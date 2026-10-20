package com.sva.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * A small Arabic settings panel opened with the remote's Menu key: background mood, calm colour,
 * weather city, and a way into the full settings. Up/Down move, OK or Left/Right change a value.
 */
final class QuickSettingsView extends FrameLayout {
    interface Listener {
        void onMoodChanged(String mood);
        void onCalmColourChanged(int index);
        void onWeatherCityChanged(PrayerTimes.City city);
        void onOpenAdvanced();
    }

    static final String PREF_WEATHER_CITY = "weatherCityKey";
    private static final int INK = 0xFFF1ECE2, SOFT = 0x99F1ECE2, GOLD = 0xFFE3B469;
    private static final int MOOD = 0, COLOUR = 1, WEATHER = 2, ADVANCED = 3;

    private final SharedPreferences preferences;
    private final Listener listener;
    private final LinearLayout list;
    private final TextView[] rows = new TextView[4];
    private int selected;

    QuickSettingsView(Context context, SharedPreferences preferences, Listener listener) {
        super(context);
        this.preferences = preferences;
        this.listener = listener;
        setBackgroundColor(0x99000000);
        setVisibility(GONE);
        setClickable(true);

        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(20), dp(16), dp(20), dp(18));
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(0xF51B2024);
        shape.setCornerRadius(dp(18));
        panel.setBackground(shape);
        TextView title = new TextView(context);
        title.setText("الإعدادات السريعة");
        title.setTextColor(GOLD);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        title.setGravity(Gravity.RIGHT);
        panel.addView(title, new LinearLayout.LayoutParams(-1, -2));
        list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < rows.length; i++) {
            TextView row = new TextView(context);
            row.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            row.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
            row.setPadding(dp(14), 0, dp(14), 0);
            rows[i] = row;
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(44));
            params.topMargin = dp(i == 0 ? 10 : 4);
            list.addView(row, params);
        }
        panel.addView(list);
        TextView hint = new TextView(context);
        hint.setText("فوق/تحت للتنقل  ·  OK أو يمين/يسار للتغيير  ·  رجوع للإغلاق");
        hint.setTextColor(SOFT);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        hint.setGravity(Gravity.RIGHT);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(-1, -2);
        hintParams.topMargin = dp(12);
        panel.addView(hint, hintParams);
        addView(panel, new LayoutParams(dp(420), -2, Gravity.CENTER));
    }

    boolean isOpen() { return getVisibility() == VISIBLE; }

    void open() {
        selected = 0;
        refresh();
        setVisibility(VISIBLE);
        bringToFront();
    }

    void close() { setVisibility(GONE); }

    boolean handleKey(KeyEvent event) {
        if (!isOpen()) return false;
        int code = event.getKeyCode();
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        switch (code) {
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_MENU:
            case KeyEvent.KEYCODE_SETTINGS:
                if (!down) close();
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (down) { selected = Math.min(rows.length - 1, selected + 1); if (selected == COLOUR && !calm()) selected++; refresh(); }
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                if (down) { selected = Math.max(0, selected - 1); if (selected == COLOUR && !calm()) selected--; refresh(); }
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (down && selected != ADVANCED) change(1);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (down && selected != ADVANCED) change(-1);
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                if (!down) {
                    if (selected == ADVANCED) { close(); listener.onOpenAdvanced(); }
                    else change(1);
                }
                return true;
            default:
                return true; // Nothing behind the panel reacts while it is open.
        }
    }

    /** Right-to-left: Left moves forward through the values, Right goes back. */
    private void change(int delta) {
        if (selected == MOOD) {
            int index = Math.floorMod(WallpaperMoods.index(WallpaperMoods.current(preferences)) + delta, WallpaperMoods.KEYS.length);
            String mood = WallpaperMoods.KEYS[index];
            preferences.edit().putString(WallpaperMoods.PREF, mood).apply();
            listener.onMoodChanged(mood);
        } else if (selected == COLOUR) {
            int index = Math.floorMod(preferences.getInt(CalmWallpapers.PREF, CalmWallpapers.DEFAULT) + delta, CalmWallpapers.NAMES.length);
            preferences.edit().putInt(CalmWallpapers.PREF, index).apply();
            listener.onCalmColourChanged(index);
        } else if (selected == WEATHER) {
            PrayerTimes.City current = PrayerTimes.city(preferences.getString(PREF_WEATHER_CITY, "riyadh"));
            int index = 0;
            for (int i = 0; i < PrayerTimes.CITIES.length; i++) if (PrayerTimes.CITIES[i] == current) index = i;
            PrayerTimes.City next = PrayerTimes.CITIES[Math.floorMod(index + delta, PrayerTimes.CITIES.length)];
            preferences.edit().putString(PREF_WEATHER_CITY, next.key).apply();
            listener.onWeatherCityChanged(next);
        }
        refresh();
    }

    private boolean calm() { return WallpaperMoods.CALM.equals(WallpaperMoods.current(preferences)); }

    private void refresh() {
        String mood = WallpaperMoods.current(preferences);
        rows[MOOD].setText("‹  الخلفية: " + WallpaperMoods.NAMES[WallpaperMoods.index(mood)] + "  ›");
        rows[COLOUR].setText("‹  لون الهدوء: " + CalmWallpapers.NAMES[preferences.getInt(CalmWallpapers.PREF, CalmWallpapers.DEFAULT)] + "  ›");
        rows[COLOUR].setVisibility(calm() ? VISIBLE : GONE);
        rows[WEATHER].setText("‹  مدينة الطقس: " + PrayerTimes.city(preferences.getString(PREF_WEATHER_CITY, "riyadh")).name + "  ›");
        rows[ADVANCED].setText("الإعدادات المتقدمة");
        for (int i = 0; i < rows.length; i++) {
            boolean on = i == selected;
            GradientDrawable shape = new GradientDrawable();
            shape.setCornerRadius(dp(10));
            shape.setColor(on ? 0x1FFFFFFF : Color.TRANSPARENT);
            if (on) shape.setStroke(dp(1), 0x66E3B469);
            rows[i].setBackground(shape);
            rows[i].setTextColor(on ? INK : SOFT);
        }
    }

    private int dp(float value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics()));
    }
}
