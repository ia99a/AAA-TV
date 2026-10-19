package com.sva.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Random;

/**
 * A calm, Arabic, right-to-left prayer screen: clock and dates, the next prayer with a countdown
 * and its iqama, a verse for the day, and all of today's times. It dims itself late at night and
 * nudges its content a few pixels every ten minutes to protect the panel from burn-in.
 */
final class PrayerView extends FrameLayout {
    static final String PREF_CITY = "prayerCity";

    private static final int BG = 0xFF11161D;
    private static final int INK = 0xFFECE4D4;
    private static final int INK_SOFT = 0xFFA7A294;
    private static final int INK_FAINT = 0xFF6A717B;
    private static final int LAMP = 0xFFE3B469;
    private static final int LINE = 0x1AECE4D4;

    private final SharedPreferences preferences;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private final LinearLayout content;
    private final TextView time, period, gregorian, hijri, ramadan;
    private final TextView eyebrow, nextName, nextTime, countdown, iqamaLine, since;
    private final View progressFill;
    private final FrameLayout progressTrack;
    private final TextView verseTheme, verseText, verseRef;
    private final Button cityButton;
    private final LinearLayout[] columns = new LinearLayout[6];
    private final TextView[] columnName = new TextView[6], columnTime = new TextView[6], columnIqama = new TextView[6];

    private PrayerTimes.City city;
    private LocalDate cachedDay;
    private LocalDateTime[] today, tomorrow, yesterday;
    private long lastShift;
    private boolean running;
    private Boolean dimmed;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            update();
            if (running) handler.postDelayed(this, 1000 - (System.currentTimeMillis() % 1000));
        }
    };

    PrayerView(Context context, SharedPreferences preferences) {
        super(context);
        this.preferences = preferences;
        city = PrayerTimes.city(preferences.getString(PREF_CITY, "riyadh"));
        setBackgroundColor(BG);
        setLayoutDirection(LAYOUT_DIRECTION_RTL);
        setClipChildren(false);

        content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setLayoutDirection(LAYOUT_DIRECTION_RTL);
        content.setPadding(dp(56), dp(20), dp(56), dp(14));
        addView(content, new LayoutParams(-1, -1));

        // Top: clock (start = right in RTL) and the next prayer.
        LinearLayout top = new LinearLayout(context);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        content.addView(top, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout clockColumn = column(context);
        LinearLayout timeLine = new LinearLayout(context);
        timeLine.setGravity(Gravity.BOTTOM);
        time = text(context, 72, INK, Typeface.create("sans-serif-light", Typeface.NORMAL));
        time.setIncludeFontPadding(false);
        period = text(context, 22, INK_SOFT, Typeface.create("sans-serif-light", Typeface.NORMAL));
        period.setPadding(dp(10), 0, dp(10), dp(10));
        timeLine.addView(time);
        timeLine.addView(period);
        clockColumn.addView(timeLine);
        gregorian = text(context, 18, INK, null);
        hijri = text(context, 16, INK_SOFT, null);
        ramadan = text(context, 13, INK_FAINT, null);
        addSpaced(clockColumn, gregorian, 6);
        addSpaced(clockColumn, hijri, 2);
        addSpaced(clockColumn, ramadan, 4);
        top.addView(clockColumn, new LinearLayout.LayoutParams(0, -2, 1.25f));

        View divider = new View(context);
        divider.setBackgroundColor(LINE);
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(dp(1), dp(120));
        dividerParams.setMarginStart(dp(24));
        dividerParams.setMarginEnd(dp(32));
        top.addView(divider, dividerParams);

        LinearLayout next = column(context);
        LinearLayout eyebrowRow = new LinearLayout(context);
        eyebrowRow.setGravity(Gravity.CENTER_VERTICAL);
        eyebrow = text(context, 13, INK_FAINT, null);
        eyebrowRow.addView(eyebrow, new LinearLayout.LayoutParams(0, -2, 1));
        cityButton = new Button(context);
        cityButton.setAllCaps(false);
        cityButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        cityButton.setTextColor(INK_SOFT);
        cityButton.setMinWidth(0);
        cityButton.setMinimumWidth(0);
        cityButton.setMinHeight(0);
        cityButton.setMinimumHeight(0);
        cityButton.setPadding(dp(14), dp(6), dp(14), dp(6));
        cityButton.setBackground(chipBackground());
        cityButton.setFocusable(true);
        cityButton.setContentDescription("تغيير المدينة");
        cityButton.setOnClickListener(v -> nextCity());
        eyebrowRow.addView(cityButton, new LinearLayout.LayoutParams(-2, -2));
        next.addView(eyebrowRow, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout nameLine = new LinearLayout(context);
        nameLine.setGravity(Gravity.BOTTOM);
        nextName = text(context, 30, INK, Typeface.create("sans-serif", Typeface.NORMAL));
        nextTime = text(context, 17, INK_SOFT, null);
        nextTime.setPadding(dp(12), 0, dp(12), dp(6));
        nameLine.addView(nextName);
        nameLine.addView(nextTime);
        addSpaced(next, nameLine, 4);
        countdown = text(context, 20, LAMP, Typeface.create("sans-serif-light", Typeface.NORMAL));
        addSpaced(next, countdown, 2);
        iqamaLine = text(context, 14, INK_SOFT, null);
        addSpaced(next, iqamaLine, 2);
        progressTrack = new FrameLayout(context);
        progressTrack.setBackground(rounded(LINE, dp(2)));
        progressFill = new View(context);
        progressFill.setBackground(rounded(0xB3E3B469, dp(2)));
        progressTrack.addView(progressFill, new LayoutParams(0, -1, Gravity.START));
        LinearLayout.LayoutParams trackParams = new LinearLayout.LayoutParams(-1, dp(3));
        trackParams.topMargin = dp(10);
        next.addView(progressTrack, trackParams);
        since = text(context, 12, INK_FAINT, null);
        addSpaced(next, since, 8);
        top.addView(next, new LinearLayout.LayoutParams(0, -2, 1));

        // Middle: the verse for today.
        LinearLayout verse = column(context);
        verseTheme = text(context, 13, LAMP, null);
        verseText = text(context, 22, INK, Typeface.create(Typeface.SERIF, Typeface.NORMAL));
        verseText.setLineSpacing(0, 1.15f);
        verseText.setMaxLines(2);
        verseText.setEllipsize(TextUtils.TruncateAt.END);
        verseRef = text(context, 12, INK_FAINT, null);
        verse.addView(verseTheme);
        addSpaced(verse, verseText, 4);
        addSpaced(verse, verseRef, 2);
        LinearLayout.LayoutParams verseParams = new LinearLayout.LayoutParams(-1, -2);
        verseParams.topMargin = dp(10);
        content.addView(verse, verseParams);

        // Bottom: all of today's times.
        View rule = new View(context);
        rule.setBackgroundColor(LINE);
        LinearLayout.LayoutParams ruleParams = new LinearLayout.LayoutParams(-1, dp(1));
        ruleParams.topMargin = dp(12);
        content.addView(rule, ruleParams);
        LinearLayout band = new LinearLayout(context);
        band.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < 6; i++) {
            LinearLayout col = column(context);
            col.setPadding(dp(10), dp(10), dp(10), 0);
            columnName[i] = text(context, 14, INK_SOFT, null);
            columnName[i].setText(PrayerTimes.NAMES[i]);
            columnTime[i] = text(context, i == PrayerTimes.SUNRISE ? 18 : 22, INK, Typeface.create("sans-serif-light", Typeface.NORMAL));
            columnIqama[i] = text(context, 12, INK_FAINT, null);
            col.addView(columnName[i]);
            addSpaced(col, columnTime[i], 2);
            addSpaced(col, columnIqama[i], 2);
            columns[i] = col;
            band.addView(col, new LinearLayout.LayoutParams(0, -2, 1));
        }
        content.addView(band, new LinearLayout.LayoutParams(-1, -2));
        update();
    }

    /** The control the remote should land on when entering this tab. */
    View entryFocus() { return cityButton; }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        start();
    }

    @Override protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }

    @Override protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (isAttachedToWindow() && isShown()) start(); else stop();
    }

    private void start() {
        if (running) return;
        running = true;
        handler.removeCallbacks(tick);
        handler.post(tick);
    }

    private void stop() {
        running = false;
        handler.removeCallbacks(tick);
    }

    private void nextCity() {
        int index = 0;
        for (int i = 0; i < PrayerTimes.CITIES.length; i++) if (PrayerTimes.CITIES[i] == city) index = i;
        city = PrayerTimes.CITIES[(index + 1) % PrayerTimes.CITIES.length];
        preferences.edit().putString(PREF_CITY, city.key).apply();
        cachedDay = null;
        update();
    }

    private LocalDateTime[] timesFor(LocalDate day) {
        ZoneId zone = ZoneId.systemDefault();
        double offsetHours = zone.getRules().getOffset(day.atTime(12, 0)).getTotalSeconds() / 3600.0;
        return PrayerTimes.forDay(day, city.latitude, city.longitude, offsetHours);
    }

    private void update() {
        LocalDateTime now = LocalDateTime.now();
        LocalDate day = now.toLocalDate();
        if (!day.equals(cachedDay)) {
            cachedDay = day;
            today = timesFor(day);
            tomorrow = timesFor(day.plusDays(1));
            yesterday = timesFor(day.minusDays(1));
            gregorian.setText(ArabicFormat.gregorian(day));
            hijri.setText(ArabicFormat.hijri(day));
            String ramadanText = ArabicFormat.ramadan(day);
            ramadan.setText(ramadanText);
            ramadan.setVisibility(ramadanText.isEmpty() ? GONE : VISIBLE);
            String[] verse = DailyVerses.forEpochDay(day.toEpochDay());
            verseTheme.setText("آية اليوم · " + verse[0]);
            verseText.setText("﴿ " + verse[1] + " ﴾");
            verseRef.setText("سورة " + verse[2]);
            cityButton.setText(city.name);
            for (int i = 0; i < 6; i++) {
                columnTime[i].setText(ArabicFormat.clockWithPeriod(today[i]));
                columnIqama[i].setText(PrayerTimes.IQAMA_MINUTES[i] > 0
                        ? "الإقامة " + ArabicFormat.clock(PrayerTimes.iqama(today, i)) : " ");
            }
        }
        setIfChanged(time, ArabicFormat.clock(now));
        setIfChanged(period, ArabicFormat.period(now));

        // Work out which prayer comes next and which one came before it.
        int[] prayers = {PrayerTimes.FAJR, PrayerTimes.DHUHR, PrayerTimes.ASR, PrayerTimes.MAGHRIB, PrayerTimes.ISHA};
        int nextIndex = -1;
        for (int i = 0; i < prayers.length; i++) if (today[prayers[i]].isAfter(now)) { nextIndex = i; break; }
        int nextPrayer, previousPrayer;
        LocalDateTime nextAt, previousAt, previousIqama;
        if (nextIndex == -1) {
            nextPrayer = PrayerTimes.FAJR; nextAt = tomorrow[PrayerTimes.FAJR];
            previousPrayer = PrayerTimes.ISHA; previousAt = today[PrayerTimes.ISHA];
            previousIqama = PrayerTimes.iqama(today, PrayerTimes.ISHA);
        } else if (nextIndex == 0) {
            nextPrayer = PrayerTimes.FAJR; nextAt = today[PrayerTimes.FAJR];
            previousPrayer = PrayerTimes.ISHA; previousAt = yesterday[PrayerTimes.ISHA];
            previousIqama = PrayerTimes.iqama(yesterday, PrayerTimes.ISHA);
        } else {
            nextPrayer = prayers[nextIndex]; nextAt = today[nextPrayer];
            previousPrayer = prayers[nextIndex - 1]; previousAt = today[previousPrayer];
            previousIqama = PrayerTimes.iqama(today, previousPrayer);
        }

        float progress;
        if (!now.isBefore(previousAt) && now.isBefore(previousIqama)) {
            // Between the adhan and the iqama.
            setIfChanged(eyebrow, "أُذّن لصلاة");
            setIfChanged(nextName, PrayerTimes.NAMES[previousPrayer]);
            setIfChanged(nextTime, "الأذان " + ArabicFormat.clockWithPeriod(previousAt));
            setIfChanged(countdown, "الإقامة بعد " + ArabicFormat.duration(millis(now, previousIqama)));
            setIfChanged(iqamaLine, "الإقامة " + ArabicFormat.clockWithPeriod(previousIqama));
            setIfChanged(since, "");
            countdown.setTextColor(INK);
            progress = fraction(previousAt, previousIqama, now);
        } else {
            LocalDateTime nextIqama = nextAt.plusMinutes(PrayerTimes.IQAMA_MINUTES[nextPrayer]);
            setIfChanged(eyebrow, "الصلاة القادمة");
            setIfChanged(nextName, PrayerTimes.NAMES[nextPrayer]);
            setIfChanged(nextTime, ArabicFormat.clockWithPeriod(nextAt));
            setIfChanged(countdown, "بعد " + ArabicFormat.duration(millis(now, nextAt)));
            setIfChanged(iqamaLine, "الإقامة " + ArabicFormat.clockWithPeriod(nextIqama));
            setIfChanged(since, "مضى على " + PrayerTimes.NAMES[previousPrayer] + " " + ArabicFormat.duration(millis(previousAt, now)));
            countdown.setTextColor(LAMP);
            progress = fraction(previousIqama, nextAt, now);
        }
        int trackWidth = progressTrack.getWidth();
        if (trackWidth > 0) {
            LayoutParams fill = (LayoutParams) progressFill.getLayoutParams();
            int width = Math.round(trackWidth * progress);
            if (fill.width != width) { fill.width = width; progressFill.setLayoutParams(fill); }
        }

        // Highlight the next prayer and fade the ones already passed.
        boolean nextIsToday = nextIndex != -1;
        for (int i = 0; i < 6; i++) {
            boolean isNext = nextIsToday && i == nextPrayer;
            boolean past = !isNext && !today[i].isAfter(now);
            columnName[i].setTextColor(isNext ? LAMP : past ? INK_FAINT : INK_SOFT);
            columnTime[i].setTextColor(isNext ? INK : past ? INK_FAINT : INK);
            columnIqama[i].setTextColor(isNext ? LAMP : INK_FAINT);
        }

        // Quiet hours: from an hour after Isha until half an hour before Fajr.
        LocalDateTime nightStart = today[PrayerTimes.ISHA].plusHours(1);
        LocalDateTime nightEnd = today[PrayerTimes.FAJR].minusMinutes(30);
        boolean night = !now.isBefore(nightStart) || now.isBefore(nightEnd);
        if (dimmed == null || dimmed != night) {
            dimmed = night;
            content.animate().alpha(night ? .45f : 1f).setDuration(2000).start();
        }

        // Burn-in protection: shift a few pixels every ten minutes.
        long nowMillis = System.currentTimeMillis();
        if (nowMillis - lastShift > 10 * 60_000L) {
            lastShift = nowMillis;
            content.setTranslationX((random.nextFloat() * 2 - 1) * dp(6));
            content.setTranslationY((random.nextFloat() * 2 - 1) * dp(5));
        }
    }

    private static long millis(LocalDateTime from, LocalDateTime to) {
        return Duration.between(from, to).toMillis();
    }

    private static float fraction(LocalDateTime start, LocalDateTime end, LocalDateTime now) {
        long total = millis(start, end);
        if (total <= 0) return 0f;
        return Math.max(0f, Math.min(1f, millis(start, now) / (float) total));
    }

    private static void setIfChanged(TextView view, String value) {
        if (!TextUtils.equals(view.getText(), value)) view.setText(value);
    }

    private LinearLayout column(Context context) {
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.START);
        return column;
    }

    private TextView text(Context context, float sp, int color, Typeface typeface) {
        TextView view = new TextView(context);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        view.setTextColor(color);
        view.setTextDirection(TEXT_DIRECTION_RTL);
        view.setGravity(Gravity.START);
        if (typeface != null) view.setTypeface(typeface);
        return view;
    }

    private void addSpaced(LinearLayout parent, View child, int topDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
        params.topMargin = dp(topDp);
        parent.addView(child, params);
    }

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(radius);
        return shape;
    }

    private StateListDrawable chipBackground() {
        GradientDrawable idle = rounded(Color.TRANSPARENT, dp(16));
        idle.setStroke(dp(1), LINE);
        GradientDrawable focused = rounded(0x22E3B469, dp(16));
        focused.setStroke(dp(2), LAMP);
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused}, focused);
        states.addState(new int[]{}, idle);
        return states;
    }

    private int dp(float value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics()));
    }
}
