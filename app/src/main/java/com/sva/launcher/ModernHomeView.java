package com.sva.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * «بسيط ٢»: the glass home screen. Two rows of cards (weather, verse, clock / occasion, wisdom or
 * poetry, next prayer) and a centred row of square app tiles. The launcher is laid out left to
 * right, so every row is added in visual order with the first (Arabic) item at the right.
 * Sizes follow a 960-unit-wide design and are scaled to the screen.
 */
final class ModernHomeView extends FrameLayout {
    static final class App {
        final String label, kind, tag;
        final Drawable icon;
        final Runnable click;
        App(String label, String kind, Drawable icon, String tag, Runnable click) {
            this.label = label; this.kind = kind; this.icon = icon; this.tag = tag; this.click = click;
        }
    }

    private static final String[] WEEKDAYS = {"الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت", "الأحد"};
    private static final int[] PRAYERS = {PrayerTimes.FAJR, PrayerTimes.DHUHR, PrayerTimes.ASR,
            PrayerTimes.MAGHRIB, PrayerTimes.ISHA};

    private final SharedPreferences preferences;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final float k;
    private final FrameLayout cards;
    private final LinearLayout appRow;
    private boolean day;
    private String font;
    private float sizeFactor = 1f;
    private int ink, soft, faint, glass, glass2, edge, gold, line;

    private WeatherRepository.Forecast forecast;
    private String occasionEyebrow = "المناسبة القادمة", occasionTitle = "", occasionDate = "";
    private List<App> apps = new ArrayList<>();
    private final List<View> tiles = new ArrayList<>();
    private String focusedTag;

    // Live parts updated every second.
    private TextView clockText, clockPeriod;
    private TextView prayerEyebrow, prayerName, prayerTime, prayerIqama;
    private Ring prayerRing;
    private long lastMinute = -1;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            // Only while on screen: with «بسيط ١» chosen this view is hidden and the timer stops.
            if (!isShown()) return;
            updateLive();
            handler.postDelayed(this, 1000 - (System.currentTimeMillis() % 1000));
        }
    };

    ModernHomeView(Context context, SharedPreferences preferences) {
        super(context);
        this.preferences = preferences;
        this.k = context.getResources().getDisplayMetrics().widthPixels / 960f;
        setClipChildren(false);
        cards = new FrameLayout(context);
        addView(cards, new LayoutParams(-1, -1));
        appRow = new LinearLayout(context);
        appRow.setClipChildren(false);
        appRow.setClipToPadding(false);
        LayoutParams rowParams = new LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        rowParams.topMargin = u(322);
        addView(appRow, rowParams);
        rebuild();
    }

    private int u(float units) { return Math.round(units * k); }

    // ---- Public API ----

    /** Re-reads the theme settings (mode, font, cards) and redraws everything. */
    void rebuild() {
        day = ModernTheme.day(preferences);
        font = ModernTheme.font(preferences);
        sizeFactor = ModernTheme.textSizeFactor(preferences);
        if (day) {
            ink = 0xFF1F2A37; soft = 0x9E1F2A37; faint = 0x661F2A37; glass = 0x99FFFFFF; glass2 = 0x66FFFFFF;
            edge = 0xCCFFFFFF; gold = 0xFFB7832F; line = 0x1A1F2A37;
        } else {
            ink = 0xFFEEF0F3; soft = 0xA6EEF0F3; faint = 0x66EEF0F3; glass = 0xC0141820; glass2 = 0x14FFFFFF;
            edge = 0x22FFFFFF; gold = 0xFFE3B469; line = 0x1AFFFFFF;
        }
        buildCards();
        buildApps();
        lastMinute = -1;
        updateLive();
    }

    void setWeather(WeatherRepository.Forecast value) { forecast = value; buildCards(); updateLive(); }

    void setOccasion(String eyebrow, String title, String date) {
        if (TextUtils.equals(title, occasionTitle) && TextUtils.equals(date, occasionDate)
                && TextUtils.equals(eyebrow, occasionEyebrow)) return;
        occasionEyebrow = eyebrow; occasionTitle = title; occasionDate = date;
        buildCards();
        updateLive();
    }

    /** Returns the tile that should take focus first (the right-most), or null when there are no apps. */
    View setApps(List<App> value) {
        apps = new ArrayList<>(value);
        buildApps();
        return tiles.isEmpty() ? null : tiles.get(0);
    }

    View firstTile() { return tiles.isEmpty() ? null : tiles.get(0); }

    View tileFor(String tag) {
        for (View tile : tiles) if (tag != null && tag.equals(tile.getTag())) return tile;
        return firstTile();
    }

    String focusedTag() { return focusedTag; }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        handler.removeCallbacks(tick);
        handler.post(tick);
    }

    @Override protected void onDetachedFromWindow() {
        handler.removeCallbacks(tick);
        super.onDetachedFromWindow();
    }

    @Override protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        handler.removeCallbacks(tick);
        if (isShown()) handler.post(tick);
    }

    // ---- Cards ----

    private void buildCards() {
        cards.removeAllViews();
        clockText = clockPeriod = null;
        prayerEyebrow = prayerName = prayerTime = prayerIqama = null;
        prayerRing = null;
        LocalDate today = LocalDate.now();
        // Row 1 (top 24, height 120): weather | verse | clock. Row 2 (top 156, height 77): occasion | wisdom | prayer.
        if (ModernTheme.card(preferences, "weather")) place(weatherCard(), 24, 24, 280, 120);
        if (ModernTheme.card(preferences, "verse")) place(verseCard(today), 316, 24, 328, 120);
        if (ModernTheme.card(preferences, "clock")) place(clockCard(), 656, 24, 280, 120);
        if (ModernTheme.card(preferences, "occasion")) place(occasionCard(), 24, 156, 280, 77);
        View quote = quoteCard(today);
        if (quote != null) place(quote, 316, 156, 328, 77);
        if (ModernTheme.card(preferences, "prayer")) place(prayerCard(), 656, 156, 280, 77);
    }

    private void place(View card, float left, float top, float width, float height) {
        card.setBackground(glassShape(14));
        LayoutParams params = new LayoutParams(u(width), u(height));
        params.leftMargin = u(left);
        params.topMargin = u(top);
        cards.addView(card, params);
    }

    private GradientDrawable glassShape(float radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(glass);
        shape.setCornerRadius(u(radius));
        shape.setStroke(Math.max(1, u(0.8f)), edge);
        return shape;
    }

    private TextView text(float size, int color, int weight) {
        TextView view = new TextView(getContext()) {
            @Override public void setText(CharSequence value, BufferType type) {
                super.setText(ModernTheme.forFont(font, value), type);
            }
        };
        float px = size * k * ModernTheme.scale(font) * sizeFactor;
        view.setTextSize(TypedValue.COMPLEX_UNIT_PX, px);
        float[] trim = ModernTheme.trim(font);
        view.setPadding(0, -Math.round(trim[0] * px), 0, -Math.round(trim[1] * px));
        view.setTextColor(color);
        view.setTypeface(ModernTheme.typeface(getContext(), font, weight));
        view.setIncludeFontPadding(false);
        view.setSingleLine(true);
        view.setEllipsize(TextUtils.TruncateAt.END);
        view.setGravity(Gravity.RIGHT);
        return view;
    }

    private LinearLayout column(int gravity) {
        LinearLayout column = new LinearLayout(getContext());
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(gravity);
        return column;
    }

    private View weatherCard() {
        LinearLayout card = column(Gravity.TOP);
        card.setPadding(u(11), u(8), u(11), u(6));
        LinearLayout top = new LinearLayout(getContext());
        top.setGravity(Gravity.CENTER_VERTICAL);
        if (forecast == null) {
            TextView empty = text(10, soft, 400);
            empty.setText("الطقس غير متاح الآن");
            top.addView(empty, new LinearLayout.LayoutParams(-1, -2));
            card.addView(top, new LinearLayout.LayoutParams(-1, u(36)));
            return card;
        }
        // Left: city, humidity, wind. Right: temperature and condition, then the icon.
        LinearLayout side = column(Gravity.LEFT);
        TextView city = text(8, soft, 400); city.setGravity(Gravity.LEFT); city.setText(forecast.location);
        // Today's high and low, then humidity and wind on one line.
        TextView range = text(8, ink, 400); range.setGravity(Gravity.LEFT);
        String todayIso = LocalDate.now().toString();
        if (!forecast.days.isEmpty() && todayIso.equals(forecast.days.get(0).date)) {
            WeatherRepository.Day today = forecast.days.get(0);
            range.setText("العظمى " + ArabicFormat.digits(Math.round(today.high)) + "°   الصغرى "
                    + ArabicFormat.digits(Math.round(today.low)) + "°");
        }
        TextView air = text(8, soft, 400); air.setGravity(Gravity.LEFT);
        air.setText((forecast.humidity >= 0 ? "الرطوبة " + ArabicFormat.digits(forecast.humidity) + "٪  ·  " : "")
                + "الرياح " + ArabicFormat.digits(Math.round(forecast.windSpeed)) + " كم/س");
        side.addView(city); addGap(side, 2); side.addView(range); addGap(side, 2); side.addView(air);
        top.addView(side, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout temp = column(Gravity.RIGHT);
        TextView value = text(25.6f, ink, 300);
        value.setEllipsize(null);
        String t = ArabicFormat.digits(Math.round(forecast.temperature)) + "°م";
        SpannableString styled = new SpannableString(t);
        int degree = t.indexOf('°');
        styled.setSpan(new RelativeSizeSpan(0.42f), degree, t.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        styled.setSpan(new ForegroundColorSpan(soft), degree, t.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        value.setText(styled);
        TextView condition = text(9.6f, ink, 400);
        condition.setText(WeatherRepository.conditionArabic(forecast.code));
        temp.addView(value); addGap(temp, 3); temp.addView(condition);
        top.addView(temp, new LinearLayout.LayoutParams(-2, -2));
        ImageView icon = new ImageView(getContext());
        icon.setImageDrawable(new ModernIcons.Weather(ModernIcons.weatherKind(forecast.code, forecast.day)));
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(u(35), u(29));
        iconParams.leftMargin = u(8);
        top.addView(icon, iconParams);
        card.addView(top, new LinearLayout.LayoutParams(-1, -2));
        View rule = new View(getContext());
        rule.setBackgroundColor(line);
        LinearLayout.LayoutParams ruleParams = new LinearLayout.LayoutParams(-1, Math.max(1, u(0.8f)));
        ruleParams.setMargins(0, u(5), 0, u(2));
        card.addView(rule, ruleParams);
        // Five days, today at the right.
        LinearLayout days = new LinearLayout(getContext());
        int count = Math.min(5, forecast.days.size());
        for (int i = count - 1; i >= 0; i--) {
            WeatherRepository.Day d = forecast.days.get(i);
            LinearLayout cell = column(Gravity.CENTER_HORIZONTAL);
            cell.setPadding(0, u(2), 0, u(3));
            if (todayIso.equals(d.date)) {
                GradientDrawable now = new GradientDrawable();
                now.setColor(glass2); now.setCornerRadius(u(8)); now.setStroke(Math.max(1, u(0.8f)), edge);
                cell.setBackground(now);
            }
            TextView name = text(8, i == 0 ? ink : soft, 400);
            name.setGravity(Gravity.CENTER);
            name.setText(todayIso.equals(d.date) ? "اليوم" : weekday(d.date));
            ImageView dayIcon = new ImageView(getContext());
            dayIcon.setImageDrawable(new ModernIcons.Weather(ModernIcons.weatherKind(d.code, true)));
            TextView range = text(8, ink, 400);
            range.setGravity(Gravity.CENTER);
            // Low then high: in this left-to-right line the high sits at the right, as Arabic reads first.
            String low = ArabicFormat.digits(Math.round(d.low)) + "°", high = ArabicFormat.digits(Math.round(d.high)) + "°";
            SpannableString r = new SpannableString(low + " " + high);
            r.setSpan(new ForegroundColorSpan(faint), 0, low.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            range.setText(r);
            cell.addView(name, new LinearLayout.LayoutParams(-1, -2));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(u(19), u(15));
            p.topMargin = u(1); p.bottomMargin = u(1);
            cell.addView(dayIcon, p);
            cell.addView(range, new LinearLayout.LayoutParams(-1, -2));
            LinearLayout.LayoutParams cellParams = new LinearLayout.LayoutParams(0, -2, 1);
            cellParams.setMargins(u(2), 0, u(2), 0);
            days.addView(cell, cellParams);
        }
        card.addView(days, new LinearLayout.LayoutParams(-1, -2));
        return card;
    }

    private static String weekday(String isoDate) {
        try { return WEEKDAYS[LocalDate.parse(isoDate).getDayOfWeek().getValue() - 1]; }
        catch (Exception unknown) { return ""; }
    }

    private View verseCard(LocalDate today) {
        LinearLayout card = column(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        card.setPadding(u(21), u(14), u(21), u(14));
        String[] verse = DailyVerses.forEpochDay(today.toEpochDay());
        // The verse has its own Quranic font (Amiri or Naskh), whatever the interface font is.
        String verseFont = ModernTheme.verseFont(preferences);
        TextView mark = text(18, gold, 400);
        mark.setTypeface(ModernTheme.typeface(getContext(), verseFont, 400));
        mark.setTextSize(TypedValue.COMPLEX_UNIT_PX, 22 * k);
        mark.setText("\u201D");
        TextView body = text(13.6f, ink, 400);
        body.setTypeface(ModernTheme.typeface(getContext(), verseFont, 400));
        body.setTextSize(TypedValue.COMPLEX_UNIT_PX, ("naskh".equals(verseFont) ? 13.2f : 14.4f) * k * sizeFactor);
        mark.setPadding(0, 0, 0, 0);
        body.setPadding(0, 0, 0, 0);
        body.setSingleLine(false);
        body.setMaxLines(2);
        body.setLineSpacing(0, 1.25f);
        body.setText("﴿ " + verse[1] + " ﴾");
        TextView ref = text(8, soft, 400);
        ref.setText("آية اليوم · سورة " + verse[2]);
        View underline = new View(getContext());
        underline.setBackgroundColor((gold & 0x00FFFFFF) | 0x99000000);
        card.addView(mark);
        addGap(card, 6);
        card.addView(body, new LinearLayout.LayoutParams(-1, -2));
        addGap(card, 3);
        card.addView(ref, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams lineParams = new LinearLayout.LayoutParams(u(21), Math.max(2, u(1.6f)));
        lineParams.topMargin = u(6);
        lineParams.gravity = Gravity.RIGHT;
        card.addView(underline, lineParams);
        return card;
    }

    private View clockCard() {
        LinearLayout row = new LinearLayout(getContext());
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(u(14), 0, u(16), 0);
        // Left to right: the date (weekday, Gregorian, Hijri), a thin divider, then the time at the right.
        LocalDate today = LocalDate.now();
        LinearLayout date = column(Gravity.CENTER);
        TextView weekday = text(9.6f, soft, 400); weekday.setGravity(Gravity.CENTER);
        weekday.setText(WEEKDAYS[today.getDayOfWeek().getValue() - 1]);
        TextView gregorian = text(11.5f, ink, 500); gregorian.setGravity(Gravity.CENTER);
        gregorian.setText(ArabicFormat.digits(today.getDayOfMonth()) + " " + ArabicFormat.month(today.getMonthValue())
                + " " + ArabicFormat.digits(today.getYear()));
        TextView hijri = text(9.6f, gold, 400); hijri.setGravity(Gravity.CENTER);
        hijri.setText(ArabicFormat.hijri(today));
        date.addView(weekday, new LinearLayout.LayoutParams(-1, -2)); addGap(date, 5);
        date.addView(gregorian, new LinearLayout.LayoutParams(-1, -2)); addGap(date, 5);
        date.addView(hijri, new LinearLayout.LayoutParams(-1, -2));
        View divider = new View(getContext());
        divider.setBackgroundColor(line);
        clockPeriod = text(11.2f, soft, 300);
        clockText = text(36.8f, ink, 300);
        clockText.setGravity(Gravity.RIGHT);
        row.addView(date, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(Math.max(1, u(0.8f)), u(56));
        dividerParams.setMargins(u(10), 0, u(12), 0);
        row.addView(divider, dividerParams);
        // Period and time share a baseline, the period on the left of the digits.
        LinearLayout time = new LinearLayout(getContext());
        time.setBaselineAligned(true);
        LinearLayout.LayoutParams periodParams = new LinearLayout.LayoutParams(-2, -2);
        periodParams.rightMargin = u(4);
        time.addView(clockPeriod, periodParams);
        time.addView(clockText, new LinearLayout.LayoutParams(-2, -2));
        row.addView(time, new LinearLayout.LayoutParams(-2, -2));
        return row;
    }

    private View occasionCard() {
        LinearLayout card = new LinearLayout(getContext());
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(u(14), 0, u(14), 0);
        LinearLayout copy = column(Gravity.RIGHT);
        TextView eyebrow = text(8, soft, 400); eyebrow.setText(occasionEyebrow);
        TextView title = text(13.6f, ink, 400); title.setText(occasionTitle.isEmpty() ? "لا مناسبات قريبة" : occasionTitle);
        TextView date = text(8.8f, soft, 400); date.setText(occasionDate);
        copy.addView(eyebrow, new LinearLayout.LayoutParams(-1, -2)); addGap(copy, 3);
        copy.addView(title, new LinearLayout.LayoutParams(-1, -2)); addGap(copy, 3);
        copy.addView(date, new LinearLayout.LayoutParams(-1, -2));
        card.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        FrameLayout badge = new FrameLayout(getContext());
        GradientDrawable box = new GradientDrawable();
        box.setColor(glass2); box.setCornerRadius(u(11)); box.setStroke(Math.max(1, u(0.8f)), edge);
        badge.setBackground(box);
        ImageView icon = new ImageView(getContext());
        icon.setImageDrawable(new ModernIcons.Line("calstar", gold));
        badge.addView(icon, new LayoutParams(u(18), u(18), Gravity.CENTER));
        LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(u(35), u(35));
        badgeParams.leftMargin = u(11);
        card.addView(badge, badgeParams);
        return card;
    }

    /** Daily wisdom or a line of poetry; when both are on they take turns every minute. */
    private View quoteCard(LocalDate today) {
        boolean wisdom = ModernTheme.card(preferences, "wisdom"), poetry = ModernTheme.card(preferences, "poetry");
        if (!wisdom && !poetry) return null;
        boolean showPoetry = poetry && (!wisdom || (System.currentTimeMillis() / 60_000L) % 2 == 1);
        LinearLayout card = column(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        card.setPadding(u(21), u(9), u(21), u(9));
        LinearLayout head = new LinearLayout(getContext());
        head.setGravity(Gravity.CENTER_VERTICAL);
        View rule = new View(getContext());
        rule.setBackgroundColor(line);
        TextView label = text(8, gold, 400);
        label.setText(showPoetry ? "من الشعر" : "حكمة اليوم");
        head.addView(rule, new LinearLayout.LayoutParams(0, Math.max(1, u(0.8f)), 1));
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(-2, -2);
        labelParams.leftMargin = u(6);
        head.addView(label, labelParams);
        card.addView(head, new LinearLayout.LayoutParams(-1, -2));
        addGap(card, 5);
        if (showPoetry) {
            String[] line = ModernTheme.poetryFor(today);
            LinearLayout verses = new LinearLayout(getContext());
            TextView second = text(11.2f, ink, 400); second.setGravity(Gravity.LEFT); second.setText(line[1]);
            TextView first = text(11.2f, ink, 400); first.setText(line[0]);
            verses.addView(second, new LinearLayout.LayoutParams(0, -2, 1));
            LinearLayout.LayoutParams firstParams = new LinearLayout.LayoutParams(0, -2, 1);
            firstParams.leftMargin = u(12);
            verses.addView(first, firstParams);
            card.addView(verses, new LinearLayout.LayoutParams(-1, -2));
            addGap(card, 3);
            TextView by = text(8, soft, 400); by.setText(line[2]);
            card.addView(by, new LinearLayout.LayoutParams(-1, -2));
        } else {
            TextView body = text(12, ink, 400);
            body.setText(ModernTheme.wisdomFor(today));
            card.addView(body, new LinearLayout.LayoutParams(-1, -2));
            addGap(card, 3);
            TextView by = text(8, soft, 400); by.setText("مثل عربي");
            card.addView(by, new LinearLayout.LayoutParams(-1, -2));
        }
        card.setTag(showPoetry ? "poetry" : "wisdom");
        return card;
    }

    private View prayerCard() {
        LinearLayout card = new LinearLayout(getContext());
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(u(13), 0, u(13), 0);
        LinearLayout copy = column(Gravity.RIGHT);
        prayerEyebrow = text(8, soft, 400);
        prayerName = text(16, ink, 400);
        prayerTime = text(9.6f, gold, 400);
        prayerIqama = text(8, soft, 400);
        copy.addView(prayerEyebrow, new LinearLayout.LayoutParams(-1, -2)); addGap(copy, 3);
        copy.addView(prayerName, new LinearLayout.LayoutParams(-1, -2)); addGap(copy, 3);
        copy.addView(prayerTime, new LinearLayout.LayoutParams(-1, -2)); addGap(copy, 2);
        copy.addView(prayerIqama, new LinearLayout.LayoutParams(-1, -2));
        card.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        prayerRing = new Ring(getContext());
        LinearLayout.LayoutParams ringParams = new LinearLayout.LayoutParams(u(58), u(58));
        ringParams.leftMargin = u(11);
        card.addView(prayerRing, ringParams);
        return card;
    }

    private void addGap(LinearLayout parent, float units) {
        parent.addView(new View(getContext()), new LinearLayout.LayoutParams(1, u(units)));
    }

    // ---- Live clock and prayer ----

    private void updateLive() {
        LocalDateTime now = LocalDateTime.now();
        long minute = System.currentTimeMillis() / 60_000L;
        if (minute != lastMinute) {
            boolean firstTime = lastMinute == -1;
            lastMinute = minute;
            if (!firstTime) {
                // Day change, and the wisdom/poetry turn, are picked up once a minute.
                boolean both = ModernTheme.card(preferences, "wisdom") && ModernTheme.card(preferences, "poetry");
                if (both || (now.getHour() == 0 && now.getMinute() == 0)) { buildCards(); }
            }
        }
        if (clockText != null) {
            clockText.setText(ArabicFormat.clock(now));
            clockPeriod.setText(ArabicFormat.period(now));
        }
        if (prayerRing != null) updatePrayer(now);
    }

    private View findQuote() {
        for (int i = 0; i < cards.getChildCount(); i++) {
            Object tag = cards.getChildAt(i).getTag();
            if ("poetry".equals(tag) || "wisdom".equals(tag)) return cards.getChildAt(i);
        }
        return null;
    }

    private void updatePrayer(LocalDateTime now) {
        ZoneId zone = ZoneId.systemDefault();
        String cityKey = preferences.getString(PrayerView.PREF_CITY, "riyadh");
        IqamaCountdown.Window window = IqamaCountdown.current(cityKey, now, zone);
        if (window != null) {
            long seconds = window.secondsLeft(now);
            setIfChanged(prayerEyebrow, "أُذّن لصلاة");
            setIfChanged(prayerName, PrayerTimes.NAMES[window.prayer]);
            setIfChanged(prayerTime, "الإقامة " + ArabicFormat.clockWithPeriod(window.iqama));
            setIfChanged(prayerIqama, "الأذان " + ArabicFormat.clockWithPeriod(window.adhan));
            prayerRing.set(window.remaining(now), IqamaCountdown.clock(seconds), IqamaCountdown.unit(seconds));
            return;
        }
        PrayerTimes.City city = PrayerTimes.city(cityKey);
        LocalDate today = now.toLocalDate();
        LocalDateTime[] t = timesFor(today, city, zone);
        LocalDateTime next = null, previous = null;
        int nextPrayer = PrayerTimes.FAJR;
        for (int prayer : PRAYERS) if (t[prayer].isAfter(now)) { next = t[prayer]; nextPrayer = prayer; break; }
        if (next == null) next = timesFor(today.plusDays(1), city, zone)[PrayerTimes.FAJR];
        for (int i = PRAYERS.length - 1; i >= 0; i--) if (!t[PRAYERS[i]].isAfter(now)) { previous = t[PRAYERS[i]]; break; }
        if (previous == null) previous = timesFor(today.minusDays(1), city, zone)[PrayerTimes.ISHA];
        long left = Math.max(0, Duration.between(now, next).toMinutes() + 1);
        double total = Math.max(1, Duration.between(previous, next).toMinutes());
        String value = left >= 60 ? ArabicFormat.digits(left / 60 + ":" + (left % 60 < 10 ? "0" : "") + left % 60)
                : ArabicFormat.digits(left);
        setIfChanged(prayerEyebrow, "الصلاة القادمة");
        setIfChanged(prayerName, PrayerTimes.NAMES[nextPrayer]);
        setIfChanged(prayerTime, ArabicFormat.clockWithPeriod(next));
        int iqamaMinutes = PrayerTimes.IQAMA_MINUTES[nextPrayer];
        setIfChanged(prayerIqama, "الإقامة " + ArabicFormat.clockWithPeriod(next.plusMinutes(iqamaMinutes)));
        prayerRing.set((float) Math.min(1, left / total), value, left >= 60 ? "ساعة" : "دقيقة");
    }

    private static LocalDateTime[] timesFor(LocalDate day, PrayerTimes.City city, ZoneId zone) {
        double hours = zone.getRules().getOffset(day.atTime(12, 0)).getTotalSeconds() / 3600.0;
        return PrayerTimes.forDay(day, city.latitude, city.longitude, hours);
    }

    private static void setIfChanged(TextView view, String value) {
        if (view != null && !TextUtils.equals(view.getText(), value)) view.setText(value);
    }

    /** Gold ring that empties as the time runs out, with the time left in the middle. */
    private final class Ring extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF box = new RectF();
        private float fraction;
        private String value = "", unit = "";

        Ring(Context context) {
            super(context);
            textPaint.setTextAlign(Paint.Align.CENTER);
        }

        void set(float f, String v, String u) {
            if (f == fraction && v.equals(value) && u.equals(unit)) return;
            fraction = f; value = v; unit = u;
            setContentDescription(v + " " + u);
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            float size = Math.min(getWidth(), getHeight()), stroke = size * 0.065f;
            float cx = getWidth() / 2f, cy = getHeight() / 2f, r = size / 2f - stroke;
            box.set(cx - r, cy - r, cx + r, cy + r);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeWidth(stroke);
            paint.setColor(line);
            canvas.drawArc(box, 0, 360, false, paint);
            paint.setColor(gold);
            if (fraction > 0) canvas.drawArc(box, -90, -360f * fraction, false, paint);
            textPaint.setColor(ink);
            textPaint.setTypeface(ModernTheme.typeface(getContext(), font, 300));
            textPaint.setTextSize(size * 0.26f * ModernTheme.scale(font));
            canvas.drawText(value, cx, cy + size * 0.06f, textPaint);
            textPaint.setColor(soft);
            textPaint.setTypeface(ModernTheme.typeface(getContext(), font, 400));
            textPaint.setTextSize(size * 0.13f * ModernTheme.scale(font));
            canvas.drawText(unit, cx, cy + size * 0.27f, textPaint);
        }
    }

    // ---- Apps ----

    private void buildApps() {
        appRow.removeAllViews();
        tiles.clear();
        // Right to left: the first app sits at the right, so add the list in reverse.
        ArrayList<View> built = new ArrayList<>();
        for (App app : apps) built.add(tile(app));
        for (int i = built.size() - 1; i >= 0; i--) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(u(98), -2);
            params.setMargins(u(9), 0, u(9), 0);
            appRow.addView(built.get(i), params);
        }
        tiles.addAll(built);
    }

    private View tile(App app) {
        LinearLayout cell = column(Gravity.CENTER_HORIZONTAL);
        cell.setFocusable(true);
        cell.setClickable(true);
        cell.setClipChildren(false);
        cell.setId(View.generateViewId());
        cell.setTag(app.tag);
        cell.setContentDescription(app.label);
        FrameLayout square = new FrameLayout(getContext());
        square.setBackground(tileShape(false));
        ImageView icon = new ImageView(getContext());
        Drawable drawable = app.icon != null ? app.icon : new ModernIcons.Line(app.kind, day ? 0xFF2A3644 : 0xFFEEF0F3);
        icon.setImageDrawable(drawable);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int iconSize = app.icon != null ? u(46) : u(40);
        square.addView(icon, new LayoutParams(iconSize, iconSize, Gravity.CENTER));
        cell.addView(square, new LinearLayout.LayoutParams(u(86), u(86)));
        TextView label = text(12, soft, 400);
        label.setGravity(Gravity.CENTER);
        label.setText(app.label);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(-1, -2);
        labelParams.topMargin = u(8);
        cell.addView(label, labelParams);
        cell.setOnClickListener(v -> { if (app.click != null) app.click.run(); });
        cell.setOnFocusChangeListener((v, focused) -> {
            square.setBackground(tileShape(focused));
            square.animate().scaleX(focused ? 1.06f : 1f).scaleY(focused ? 1.06f : 1f).setDuration(140).start();
            label.setTextColor(focused ? ink : soft);
            if (focused) focusedTag = app.tag;
        });
        return cell;
    }

    private GradientDrawable tileShape(boolean focused) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(glass);
        shape.setCornerRadius(u(24));
        shape.setStroke(focused ? u(2) : Math.max(1, u(0.8f)), focused ? gold : edge);
        return shape;
    }
}
