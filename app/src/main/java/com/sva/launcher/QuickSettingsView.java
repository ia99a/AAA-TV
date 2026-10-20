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
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/**
 * A small Arabic settings panel opened with the remote's Menu key (or a long press on «آمر»):
 * background mood, calm colour, weather city, the home apps and their order, and the full settings.
 * Up/Down move, OK or Left/Right change a value, Back closes.
 */
final class QuickSettingsView extends FrameLayout {
    interface Listener {
        void onMoodChanged(String mood);
        void onCalmColourChanged(int index);
        void onWeatherCityChanged(PrayerTimes.City city);
        void onOpenAdvanced();
        /** {package, label} of every launchable app, sorted by name. */
        List<String[]> installedApps();
        /** Packages shown on Home, in order. */
        List<String> homeApps();
        void onHomeAppsChanged(List<String> packages);
    }

    static final String PREF_WEATHER_CITY = "weatherCityKey";
    private static final int INK = 0xFFF1ECE2, SOFT = 0x99F1ECE2, GOLD = 0xFFE3B469;
    private static final int MOOD = 0, COLOUR = 1, WEATHER = 2, APPS = 3, ADVANCED = 4;

    private final SharedPreferences preferences;
    private final Listener listener;
    private final TextView title, hint;
    private final LinearLayout list;
    private final ScrollView scroll;
    private final ArrayList<TextView> rows = new ArrayList<>();
    private int selected;
    /** Ignore key releases that belong to the press which opened the panel. */
    private boolean armed;
    private boolean appsPage;
    private final ArrayList<String[]> apps = new ArrayList<>();
    private final ArrayList<String> home = new ArrayList<>();

    QuickSettingsView(Context context, SharedPreferences preferences, Listener listener) {
        super(context);
        this.preferences = preferences;
        this.listener = listener;
        setBackgroundColor(0x99000000);
        setVisibility(GONE);
        setClickable(true);

        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(20), dp(16), dp(20), dp(16));
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(0xF51B2024);
        shape.setCornerRadius(dp(18));
        panel.setBackground(shape);
        title = text(context, 15, GOLD);
        panel.addView(title, new LinearLayout.LayoutParams(-1, -2));
        scroll = new ScrollView(context);
        scroll.setVerticalScrollBarEnabled(false);
        list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list, new ScrollView.LayoutParams(-1, -2));
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(-1, -2);
        scrollParams.topMargin = dp(8);
        panel.addView(scroll, scrollParams);
        hint = text(context, 11, SOFT);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(-1, -2);
        hintParams.topMargin = dp(10);
        panel.addView(hint, hintParams);
        addView(panel, new LayoutParams(dp(440), -2, Gravity.CENTER));
    }

    boolean isOpen() { return getVisibility() == VISIBLE; }

    void open() {
        appsPage = false;
        selected = 0;
        armed = false;
        buildMain();
        setVisibility(VISIBLE);
        bringToFront();
    }

    void close() { setVisibility(GONE); }

    boolean handleKey(KeyEvent event) {
        if (!isOpen()) return false;
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        if (down && event.getRepeatCount() == 0) armed = true;
        else if (down && !armed) return true; // Held key from the long press that opened the panel.
        else if (!armed) return true; // The release of the key that opened the panel.
        int code = event.getKeyCode();
        boolean ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER;
        if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_MENU || code == KeyEvent.KEYCODE_SETTINGS) {
            if (!down) {
                if (appsPage) { appsPage = false; selected = APPS; buildMain(); }
                else close();
            }
            return true;
        }
        if (code == KeyEvent.KEYCODE_DPAD_DOWN || code == KeyEvent.KEYCODE_DPAD_UP) {
            if (down) move(code == KeyEvent.KEYCODE_DPAD_DOWN ? 1 : -1);
            return true;
        }
        if (appsPage) return handleAppsKey(code, down, ok);
        if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT) {
            if (down && selected != ADVANCED && selected != APPS) change(code == KeyEvent.KEYCODE_DPAD_LEFT ? 1 : -1);
            return true;
        }
        if (ok) {
            if (!down) {
                if (selected == ADVANCED) { close(); listener.onOpenAdvanced(); }
                else if (selected == APPS) openApps();
                else change(1);
            }
            return true;
        }
        return true; // Nothing behind the panel reacts while it is open.
    }

    private void move(int delta) {
        int count = rows.size();
        int next = selected;
        for (int i = 0; i < count; i++) {
            next = Math.max(0, Math.min(count - 1, next + delta));
            if (rows.get(next).getVisibility() == VISIBLE) break;
        }
        selected = next;
        paint();
    }

    // ---- Main page ----

    private void buildMain() {
        title.setText("الإعدادات السريعة");
        hint.setText("فوق/تحت للتنقل  ·  OK أو يمين/يسار للتغيير  ·  رجوع للإغلاق");
        list.removeAllViews();
        rows.clear();
        for (int i = 0; i <= ADVANCED; i++) addRow();
        refreshMain();
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
        refreshMain();
    }

    private boolean calm() { return WallpaperMoods.CALM.equals(WallpaperMoods.current(preferences)); }

    private void refreshMain() {
        String mood = WallpaperMoods.current(preferences);
        rows.get(MOOD).setText("‹  الخلفية: " + WallpaperMoods.NAMES[WallpaperMoods.index(mood)] + "  ›");
        rows.get(COLOUR).setText("‹  لون الهدوء: " + CalmWallpapers.NAMES[preferences.getInt(CalmWallpapers.PREF, CalmWallpapers.DEFAULT)] + "  ›");
        rows.get(COLOUR).setVisibility(calm() ? VISIBLE : GONE);
        rows.get(WEATHER).setText("‹  مدينة الطقس: " + PrayerTimes.city(preferences.getString(PREF_WEATHER_CITY, "riyadh")).name + "  ›");
        rows.get(APPS).setText("تطبيقات الشاشة الرئيسية وترتيبها");
        rows.get(ADVANCED).setText("الإعدادات المتقدمة");
        if (selected == COLOUR && !calm()) selected = MOOD;
        paint();
    }

    // ---- Apps page ----

    private void openApps() {
        appsPage = true;
        apps.clear();
        apps.addAll(listener.installedApps());
        home.clear();
        home.addAll(listener.homeApps());
        selected = 0;
        title.setText("تطبيقات الشاشة الرئيسية");
        hint.setText("OK لإظهار التطبيق أو إخفائه  ·  يمين/يسار لتغيير ترتيبه  ·  رجوع للحفظ");
        buildApps();
    }

    /** Shown apps first, in their order, then the rest by name. */
    private List<String[]> orderedApps() {
        ArrayList<String[]> ordered = new ArrayList<>();
        for (String pkg : home) for (String[] app : apps) if (app[0].equals(pkg)) ordered.add(app);
        for (String[] app : apps) if (!home.contains(app[0])) ordered.add(app);
        return ordered;
    }

    private void buildApps() {
        list.removeAllViews();
        rows.clear();
        List<String[]> ordered = orderedApps();
        for (String[] app : ordered) {
            TextView row = addRow();
            int position = home.indexOf(app[0]);
            row.setText(position >= 0 ? "✓  " + ArabicFormat.digits(position + 1) + ".  " + app[1] : "      " + app[1]);
            row.setTag(app[0]);
        }
        selected = Math.max(0, Math.min(rows.size() - 1, selected));
        paint();
    }

    private boolean handleAppsKey(int code, boolean down, boolean ok) {
        if (rows.isEmpty()) return true;
        String pkg = (String) rows.get(selected).getTag();
        if (ok) {
            if (!down) {
                if (home.contains(pkg)) home.remove(pkg); else home.add(pkg);
                listener.onHomeAppsChanged(new ArrayList<>(home));
                buildApps();
            }
            return true;
        }
        if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT) {
            int index = home.indexOf(pkg);
            if (down && index >= 0) {
                // Right to left: Left moves the app later in the row, Right earlier.
                int target = Math.max(0, Math.min(home.size() - 1, index + (code == KeyEvent.KEYCODE_DPAD_LEFT ? 1 : -1)));
                if (target != index) {
                    home.remove(index);
                    home.add(target, pkg);
                    selected = target;
                    listener.onHomeAppsChanged(new ArrayList<>(home));
                    buildApps();
                }
            }
            return true;
        }
        return true;
    }

    // ---- Shared ----

    private TextView addRow() {
        TextView row = text(getContext(), 15, SOFT);
        row.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), 0, dp(14), 0);
        row.setSingleLine(true);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(42));
        params.topMargin = dp(4);
        list.addView(row, params);
        rows.add(row);
        return row;
    }

    private void paint() {
        for (int i = 0; i < rows.size(); i++) {
            boolean on = i == selected;
            GradientDrawable shape = new GradientDrawable();
            shape.setCornerRadius(dp(10));
            shape.setColor(on ? 0x1FFFFFFF : Color.TRANSPARENT);
            if (on) shape.setStroke(dp(1), 0x66E3B469);
            rows.get(i).setBackground(shape);
            rows.get(i).setTextColor(on ? INK : SOFT);
        }
        // Keep the list no taller than most of the screen, and the selected row in view.
        int max = (int) (getResources().getDisplayMetrics().heightPixels * 0.6f);
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) scroll.getLayoutParams();
        int wanted = rows.size() * dp(46);
        int height = Math.min(max, wanted);
        if (params.height != height) { params.height = height; scroll.setLayoutParams(params); }
        if (!rows.isEmpty()) {
            final TextView row = rows.get(selected);
            scroll.post(() -> {
                int top = row.getTop(), bottom = row.getBottom(), y = scroll.getScrollY(), h = scroll.getHeight();
                if (top < y) scroll.smoothScrollTo(0, top);
                else if (bottom > y + h) scroll.smoothScrollTo(0, bottom - h);
            });
        }
    }

    private TextView text(Context context, float sp, int color) {
        TextView view = new TextView(context);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        view.setTextColor(color);
        view.setGravity(Gravity.RIGHT);
        return view;
    }

    private int dp(float value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics()));
    }
}
