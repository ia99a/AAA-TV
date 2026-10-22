package com.sva.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.View;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.Duration;

/**
 * The countdown from the adhan to the iqama: when it is running, how long is left, and two ways to
 * show it — a large gold ring for the prayer screen, and a small pill for Home, the Quran and the
 * wallpapers.
 */
final class IqamaCountdown {
    private static final int GOLD = 0xFFE3B469, INK = 0xFFF1ECE2, SOFT = 0xFFA7A294, TRACK = 0x1FECE4D4;
    private static final int[] PRAYERS = {PrayerTimes.FAJR, PrayerTimes.DHUHR, PrayerTimes.ASR,
            PrayerTimes.MAGHRIB, PrayerTimes.ISHA};

    /** A running adhan-to-iqama window. */
    static final class Window {
        final int prayer;
        final LocalDateTime adhan, iqama;
        Window(int prayer, LocalDateTime adhan, LocalDateTime iqama) {
            this.prayer = prayer; this.adhan = adhan; this.iqama = iqama;
        }
        long secondsLeft(LocalDateTime now) {
            return Math.max(0, (Duration.between(now, iqama).toMillis() + 999) / 1000);
        }
        /** Share of the wait still to come: 1 at the adhan, 0 at the iqama. */
        float remaining(LocalDateTime now) {
            double total = Duration.between(adhan, iqama).toMillis();
            if (total <= 0) return 0f;
            return (float) Math.max(0, Math.min(1, Duration.between(now, iqama).toMillis() / total));
        }
    }

    private IqamaCountdown() { }

    /** The window that {@code now} falls in for the given city, or null between iqama and the next adhan. */
    static Window current(String cityKey, LocalDateTime now, ZoneId zone) {
        PrayerTimes.City city = PrayerTimes.city(cityKey);
        for (int offset = -1; offset <= 0; offset++) {
            LocalDate day = now.toLocalDate().plusDays(offset);
            double hours = zone.getRules().getOffset(day.atTime(12, 0)).getTotalSeconds() / 3600.0;
            LocalDateTime[] times = PrayerTimes.forDay(day, city.latitude, city.longitude, hours);
            for (int prayer : PRAYERS) {
                LocalDateTime adhan = times[prayer], iqama = PrayerTimes.iqama(times, prayer);
                if (!now.isBefore(adhan) && now.isBefore(iqama)) return new Window(prayer, adhan, iqama);
            }
        }
        return null;
    }

    static Window current(SharedPreferences preferences) {
        ZoneId zone = ZoneId.systemDefault();
        return current(preferences.getString(PrayerView.PREF_CITY, "riyadh"), LocalDateTime.now(zone), zone);
    }

    /** "٥:٤٨" — minutes and seconds, with no leading zero on the minutes. */
    static String clock(long seconds) {
        long minutes = seconds / 60, rest = seconds % 60;
        return ArabicFormat.digits(minutes + ":" + (rest < 10 ? "0" : "") + rest);
    }

    /** "دقيقة" while a minute or more is left, "ثانية" for the last minute. */
    static String unit(long seconds) { return seconds >= 60 ? "دقيقة" : "ثانية"; }

    private static float dp(Context context, float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, context.getResources().getDisplayMetrics());
    }

    /** Draws the gold arc for the part of the wait still to come, shrinking towards the top. */
    private static void drawArc(Canvas canvas, RectF box, float stroke, float remaining, Paint paint, boolean glow) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(stroke);
        paint.setColor(TRACK);
        canvas.drawArc(box, 0, 360, false, paint);
        if (remaining <= 0f) return;
        float sweep = -360f * remaining;  // Counter-clockwise from the top, reading right to left.
        if (glow) {
            paint.setStrokeWidth(stroke * 2.6f);
            paint.setColor(0x26E3B469);
            canvas.drawArc(box, -90, sweep, false, paint);
            paint.setStrokeWidth(stroke);
        }
        paint.setColor(GOLD);
        canvas.drawArc(box, -90, sweep, false, paint);
    }

    /** The large ring on the prayer screen; the screen feeds it each second. */
    static final class Ring extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Typeface light = Typeface.create("sans-serif-light", Typeface.NORMAL);
        private final RectF box = new RectF();
        private float remaining;
        private long seconds;

        Ring(Context context) {
            super(context);
            text.setTextAlign(Paint.Align.CENTER);
        }

        void set(Window window, LocalDateTime now) {
            float r = window.remaining(now);
            long s = window.secondsLeft(now);
            if (r == remaining && s == seconds) return;
            remaining = r;
            seconds = s;
            setContentDescription("الإقامة بعد " + clock(s) + " " + unit(s));
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            float size = Math.min(getWidth(), getHeight()), stroke = dp(getContext(), 3.5f);
            float cx = getWidth() / 2f, cy = getHeight() / 2f, r = size / 2f - stroke * 2.4f;
            box.set(cx - r, cy - r, cx + r, cy + r);
            drawArc(canvas, box, stroke, remaining, paint, true);
            text.setTypeface(null);
            text.setColor(SOFT);
            text.setTextSize(size * 0.075f);
            canvas.drawText("الإقامة بعد", cx, cy - size * 0.17f, text);
            text.setTypeface(light);
            text.setColor(INK);
            text.setTextSize(size * 0.25f);
            canvas.drawText(clock(seconds), cx, cy + size * 0.08f, text);
            text.setTypeface(null);
            text.setColor(GOLD);
            text.setTextSize(size * 0.075f);
            canvas.drawText(unit(seconds), cx, cy + size * 0.22f, text);
        }
    }

    /**
     * The small pill shown at the top left of Home, the Quran and the wallpapers. It keeps itself up
     * to date and shows only while a countdown is running and the screen behind it allows it.
     */
    static final class Pill extends View {
        interface Listener { void onShownChanged(boolean shown); }

        private final SharedPreferences preferences;
        private final Handler handler = new Handler(Looper.getMainLooper());
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Typeface light = Typeface.create("sans-serif-light", Typeface.NORMAL);
        private final RectF box = new RectF();
        private Listener listener;
        private boolean allowed, shown, attached;
        private Window window;
        private String title = "";
        private long seconds;
        private float remaining;

        private final Runnable tick = new Runnable() {
            @Override public void run() {
                refresh();
                if (attached) handler.postDelayed(this, 1000 - (System.currentTimeMillis() % 1000));
            }
        };

        Pill(Context context, SharedPreferences preferences) {
            super(context);
            this.preferences = preferences;
            setVisibility(GONE);
            setFocusable(false);
            text.setTextAlign(Paint.Align.RIGHT);
        }

        void setListener(Listener value) { listener = value; }

        /** Whether the screen behind may show the pill (Home, the Quran, the wallpapers). */
        void setAllowed(boolean value) {
            if (allowed == value) return;
            allowed = value;
            refresh();
        }

        @Override protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            attached = true;
            handler.removeCallbacks(tick);
            handler.post(tick);
        }

        @Override protected void onDetachedFromWindow() {
            attached = false;
            handler.removeCallbacks(tick);
            super.onDetachedFromWindow();
        }

        private void refresh() {
            // Only look up the prayer times when the pill could appear.
            if (allowed) {
                LocalDateTime now = LocalDateTime.now();
                if (window == null || !now.isBefore(window.iqama)) window = current(preferences);
                if (window != null) {
                    title = "إقامة صلاة " + PrayerTimes.NAMES[window.prayer] + " بعد";
                    long s = window.secondsLeft(now);
                    float r = window.remaining(now);
                    if (s != seconds || r != remaining) { seconds = s; remaining = r; invalidate(); }
                    setContentDescription(title + " " + clock(seconds) + " " + unit(seconds));
                }
            }
            boolean show = allowed && window != null;
            if (show != shown) {
                shown = show;
                setVisibility(show ? VISIBLE : GONE);
                if (listener != null) listener.onShownChanged(show);
            }
        }

        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            setMeasuredDimension(Math.round(dp(getContext(), 218)), Math.round(dp(getContext(), 64)));
        }

        @Override protected void onDraw(Canvas canvas) {
            Context c = getContext();
            float w = getWidth(), h = getHeight(), pad = dp(c, 8);
            box.set(dp(c, 1), dp(c, 1), w - dp(c, 1), h - dp(c, 1));
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xED1B2024);
            canvas.drawRoundRect(box, h / 2f, h / 2f, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(c, 1));
            paint.setColor(0x4DE3B469);
            canvas.drawRoundRect(box, h / 2f, h / 2f, paint);
            // The ring sits at the right end, as Arabic reads.
            float ring = h - pad * 2, stroke = dp(c, 3);
            float cx = w - pad - ring / 2f, cy = h / 2f, r = ring / 2f - stroke;
            RectF arc = new RectF(cx - r, cy - r, cx + r, cy + r);
            drawArc(canvas, arc, stroke, remaining, paint, false);
            float right = cx - ring / 2f - dp(c, 12);
            text.setTypeface(null);
            text.setColor(SOFT);
            text.setTextSize(dp(c, 11));
            canvas.drawText(title, right, h * 0.38f, text);
            text.setTypeface(light);
            text.setColor(INK);
            text.setTextSize(dp(c, 24));
            String digits = clock(seconds);
            canvas.drawText(digits, right, h * 0.80f, text);
            float digitsWidth = text.measureText(digits);
            text.setTypeface(null);
            text.setColor(GOLD);
            text.setTextSize(dp(c, 12));
            canvas.drawText(unit(seconds), right - digitsWidth - dp(c, 8), h * 0.78f, text);
        }
    }
}
