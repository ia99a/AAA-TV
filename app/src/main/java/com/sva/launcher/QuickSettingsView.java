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
 * the home theme («بسيط ١» / «بسيط ٢») and, for «بسيط ٢», day/night, font, cards and bar tabs;
 * background, weather city, home apps, always-on-screen and the wallpapers screen.
 * Up/Down move, OK or Left/Right change a value, Back closes (or returns from a list page).
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
        void onKioskChanged(boolean enabled);
        void onGalleryIntervalChanged(int minutes);
        /** Theme, day/night, font, cards or tabs changed. */
        void onHomeThemeChanged();
        void onGalleryCategoryChanged();
    }

    static final String PREF_WEATHER_CITY = "weatherCityKey";
    private static final int INK = 0xFFF1ECE2, SOFT = 0x99F1ECE2, GOLD = 0xFFE3B469;
    private static final int THEME = 0, MODE = 1, FONT = 2, MOOD = 3, COLOUR = 4, WEATHER = 5, CARDS = 6, APPS = 7,
            TABS = 8, KIOSK = 9, GALLERY_SET = 10, GALLERY = 11;
    private static final int PAGE_MAIN = 0, PAGE_APPS = 1, PAGE_TABS = 2, PAGE_CARDS = 3;

    private final SharedPreferences preferences;
    private final Listener listener;
    private final TextView title, hint;
    private final LinearLayout list;
    private final ScrollView scroll;
    private final ArrayList<TextView> rows = new ArrayList<>();
    /** The setting behind each row on the main page. */
    private final ArrayList<Integer> rowIds = new ArrayList<>();
    private int selected;
    /** Ignore key releases that belong to the press which opened the panel. */
    private boolean armed;
    private int page = PAGE_MAIN;
    /** List pages: {key, label} for every entry, and the shown keys in order. */
    private final ArrayList<String[]> entries = new ArrayList<>();
    private final ArrayList<String> shown = new ArrayList<>();

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
        shape.setStroke(dp(1), 0x22FFFFFF);
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
        addView(panel, new LayoutParams(dp(460), -2, Gravity.CENTER));
    }

    boolean isOpen() { return getVisibility() == VISIBLE; }

    void open() {
        page = PAGE_MAIN;
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
                if (page != PAGE_MAIN) {
                    int back = page == PAGE_APPS ? APPS : page == PAGE_TABS ? TABS : CARDS;
                    page = PAGE_MAIN;
                    buildMain();
                    selected = Math.max(0, rowIds.indexOf(back));
                    paint();
                } else close();
            }
            return true;
        }
        if (code == KeyEvent.KEYCODE_DPAD_DOWN || code == KeyEvent.KEYCODE_DPAD_UP) {
            if (down) move(code == KeyEvent.KEYCODE_DPAD_DOWN ? 1 : -1);
            return true;
        }
        if (page != PAGE_MAIN) return handleListKey(code, down, ok);
        int id = rows.isEmpty() ? -1 : rowIds.get(selected);
        if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT) {
            if (down && !isPage(id)) change(id, code == KeyEvent.KEYCODE_DPAD_LEFT ? 1 : -1);
            return true;
        }
        if (ok) {
            if (!down) {
                if (id == APPS) openList(PAGE_APPS);
                else if (id == TABS) openList(PAGE_TABS);
                else if (id == CARDS) openList(PAGE_CARDS);
                else change(id, 1);
            }
            return true;
        }
        return true; // Nothing behind the panel reacts while it is open.
    }

    private static boolean isPage(int id) { return id == APPS || id == TABS || id == CARDS; }

    private void move(int delta) {
        int count = rows.size();
        if (count == 0) return;
        selected = Math.max(0, Math.min(count - 1, selected + delta));
        paint();
    }

    // ---- Main page ----

    private void buildMain() {
        title.setText("الإعدادات السريعة");
        hint.setText("فوق/تحت للتنقل  ·  OK أو يمين/يسار للتغيير  ·  رجوع للإغلاق");
        list.removeAllViews();
        rows.clear();
        rowIds.clear();
        boolean modern = ModernTheme.modern(preferences);
        addMain(THEME);
        if (modern) { addMain(MODE); addMain(FONT); }
        addMain(MOOD);
        if (calm()) addMain(COLOUR);
        addMain(WEATHER);
        if (modern) addMain(CARDS);
        addMain(APPS);
        if (modern) addMain(TABS);
        addMain(KIOSK);
        addMain(GALLERY_SET);
        addMain(GALLERY);
        selected = Math.max(0, Math.min(rows.size() - 1, selected));
        refreshMain();
    }

    private void addMain(int id) { addRow(); rowIds.add(id); }

    /** Right-to-left: Left moves forward through the values, Right goes back. */
    private void change(int id, int delta) {
        boolean rebuild = false;
        if (id == THEME) {
            int theme = ModernTheme.theme(preferences) == ModernTheme.SIMPLE_1 ? ModernTheme.SIMPLE_2 : ModernTheme.SIMPLE_1;
            preferences.edit().putInt(ModernTheme.PREF_THEME, theme).apply();
            listener.onHomeThemeChanged();
            rebuild = true;
        } else if (id == MODE) {
            preferences.edit().putString(ModernTheme.PREF_MODE, ModernTheme.day(preferences) ? "night" : "day").apply();
            listener.onHomeThemeChanged();
        } else if (id == FONT) {
            String current = ModernTheme.font(preferences);
            int index = 0;
            for (int i = 0; i < ModernTheme.FONT_KEYS.length; i++) if (ModernTheme.FONT_KEYS[i].equals(current)) index = i;
            String next = ModernTheme.FONT_KEYS[Math.floorMod(index + delta, ModernTheme.FONT_KEYS.length)];
            preferences.edit().putString(ModernTheme.PREF_FONT, next).apply();
            listener.onHomeThemeChanged();
        } else if (id == MOOD) {
            int index = Math.floorMod(WallpaperMoods.index(WallpaperMoods.current(preferences)) + delta, WallpaperMoods.KEYS.length);
            String mood = WallpaperMoods.KEYS[index];
            preferences.edit().putString(WallpaperMoods.PREF, mood).apply();
            listener.onMoodChanged(mood);
            rebuild = true; // The colour row appears only for the calm background.
        } else if (id == COLOUR) {
            int index = Math.floorMod(preferences.getInt(CalmWallpapers.PREF, CalmWallpapers.DEFAULT) + delta, CalmWallpapers.NAMES.length);
            preferences.edit().putInt(CalmWallpapers.PREF, index).apply();
            listener.onCalmColourChanged(index);
        } else if (id == WEATHER) {
            PrayerTimes.City current = PrayerTimes.city(preferences.getString(PREF_WEATHER_CITY, "riyadh"));
            int index = 0;
            for (int i = 0; i < PrayerTimes.CITIES.length; i++) if (PrayerTimes.CITIES[i] == current) index = i;
            PrayerTimes.City next = PrayerTimes.CITIES[Math.floorMod(index + delta, PrayerTimes.CITIES.length)];
            preferences.edit().putString(PREF_WEATHER_CITY, next.key).apply();
            listener.onWeatherCityChanged(next);
        } else if (id == GALLERY_SET) {
            GalleryView.nextCategory(preferences, delta);
            listener.onGalleryCategoryChanged();
        } else if (id == GALLERY) {
            listener.onGalleryIntervalChanged(GalleryView.nextInterval(preferences, delta));
        } else if (id == KIOSK) {
            boolean enabled = !preferences.getBoolean(LauncherOverrideService.KIOSK, true);
            preferences.edit().putBoolean(LauncherOverrideService.KIOSK, enabled).apply();
            listener.onKioskChanged(enabled);
        }
        if (rebuild) {
            int keep = id;
            buildMain();
            selected = Math.max(0, rowIds.indexOf(keep));
            paint();
        } else refreshMain();
    }

    private boolean calm() { return WallpaperMoods.CALM.equals(WallpaperMoods.current(preferences)); }

    private void refreshMain() {
        for (int i = 0; i < rows.size(); i++) rows.get(i).setText(label(rowIds.get(i)));
        paint();
    }

    private String label(int id) {
        switch (id) {
            case THEME: return "‹  الثيم: " + ModernTheme.THEME_NAMES[ModernTheme.theme(preferences) - 1] + "  ›";
            case MODE: return "‹  الوضع: " + (ModernTheme.day(preferences) ? "نهار" : "ليل") + "  ›";
            case FONT: return "‹  الخط: " + ModernTheme.fontName(ModernTheme.font(preferences)) + "  ›";
            case MOOD: return "‹  الخلفية: " + WallpaperMoods.NAMES[WallpaperMoods.index(WallpaperMoods.current(preferences))] + "  ›";
            case COLOUR: return "‹  لون الهدوء: " + CalmWallpapers.NAMES[preferences.getInt(CalmWallpapers.PREF, CalmWallpapers.DEFAULT)] + "  ›";
            case WEATHER: return "‹  مدينة الطقس: " + PrayerTimes.city(preferences.getString(PREF_WEATHER_CITY, "riyadh")).name + "  ›";
            case CARDS: return "البطاقات في «آمر»";
            case APPS: return "تطبيقات الشاشة الرئيسية وترتيبها";
            case TABS: return "التبويبات وترتيبها";
            case KIOSK: return "‹  المشغّل دائم على الشاشة: "
                    + (preferences.getBoolean(LauncherOverrideService.KIOSK, true) ? "يعمل" : "متوقف") + "  ›";
            case GALLERY_SET: return "‹  تصنيف «الخلفيات»: " + GalleryView.categoryName(preferences) + "  ›";
            case GALLERY: {
                int minutes = GalleryView.interval(preferences);
                return "‹  لوحات «الخلفيات»: " + (minutes == 0 ? "مثبّتة على الصورة الحالية" : "تتبدل " + GalleryView.intervalName(minutes)) + "  ›";
            }
            default: return "";
        }
    }

    // ---- List pages: home apps, bar tabs, home cards ----

    private void openList(int which) {
        page = which;
        entries.clear();
        shown.clear();
        if (which == PAGE_APPS) {
            entries.addAll(listener.installedApps());
            shown.addAll(listener.homeApps());
            title.setText("تطبيقات الشاشة الرئيسية");
            hint.setText("OK لإظهار التطبيق أو إخفائه  ·  يمين/يسار لتغيير ترتيبه  ·  رجوع للحفظ");
        } else if (which == PAGE_TABS) {
            for (String key : ModernTheme.TAB_KEYS) entries.add(new String[]{key, ModernTheme.tabName(key)});
            shown.addAll(ModernTheme.tabs(preferences));
            title.setText("التبويبات وترتيبها");
            hint.setText("«آمر» ثابت أولًا  ·  OK لإظهار التبويب أو إخفائه  ·  يمين/يسار لتغيير ترتيبه  ·  رجوع للحفظ");
        } else {
            for (String key : ModernTheme.CARD_KEYS) {
                entries.add(new String[]{key, ModernTheme.cardName(key)});
                if (ModernTheme.card(preferences, key)) shown.add(key);
            }
            title.setText("البطاقات في «آمر»");
            hint.setText("OK للإظهار أو الإخفاء  ·  إذا ظهرت الحكمة والشعر معًا يتبادلان كل دقيقة  ·  رجوع للحفظ");
        }
        selected = 0;
        buildList();
    }

    /** Shown entries first, in their order (cards keep their fixed order), then the rest. */
    private List<String[]> orderedEntries() {
        ArrayList<String[]> ordered = new ArrayList<>();
        if (page == PAGE_CARDS) { ordered.addAll(entries); return ordered; }
        for (String key : shown) for (String[] entry : entries) if (entry[0].equals(key)) ordered.add(entry);
        for (String[] entry : entries) if (!shown.contains(entry[0])) ordered.add(entry);
        return ordered;
    }

    private void buildList() {
        list.removeAllViews();
        rows.clear();
        rowIds.clear();
        for (String[] entry : orderedEntries()) {
            TextView row = addRow();
            int position = shown.indexOf(entry[0]);
            if (page == PAGE_CARDS) row.setText((position >= 0 ? "✓  " : "      ") + entry[1] + (position >= 0 ? "   ·   ظاهر" : "   ·   مخفي"));
            else row.setText(position >= 0 ? "✓  " + ArabicFormat.digits(position + 1) + ".  " + entry[1] : "      " + entry[1] + "   ·   مخفي");
            row.setTag(entry[0]);
        }
        selected = Math.max(0, Math.min(rows.size() - 1, selected));
        paint();
    }

    private void saveList() {
        if (page == PAGE_APPS) {
            listener.onHomeAppsChanged(new ArrayList<>(shown));
            return;
        }
        SharedPreferences.Editor editor = preferences.edit();
        if (page == PAGE_TABS) editor.putString(ModernTheme.PREF_TABS, String.join(",", shown));
        else for (String key : ModernTheme.CARD_KEYS) editor.putBoolean(ModernTheme.CARD_PREFIX + key, shown.contains(key));
        editor.apply();
        listener.onHomeThemeChanged();
    }

    private boolean handleListKey(int code, boolean down, boolean ok) {
        if (rows.isEmpty()) return true;
        String key = (String) rows.get(selected).getTag();
        if (ok) {
            if (!down) {
                if (shown.contains(key)) shown.remove(key); else shown.add(key);
                saveList();
                buildList();
            }
            return true;
        }
        if ((code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT) && page != PAGE_CARDS) {
            int index = shown.indexOf(key);
            if (down && index >= 0) {
                // Right to left: Left moves the entry later, Right earlier.
                int target = Math.max(0, Math.min(shown.size() - 1, index + (code == KeyEvent.KEYCODE_DPAD_LEFT ? 1 : -1)));
                if (target != index) {
                    shown.remove(index);
                    shown.add(target, key);
                    selected = target;
                    saveList();
                    buildList();
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
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(40));
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
            if (on) shape.setStroke(dp(1), 0x8CE3B469);
            rows.get(i).setBackground(shape);
            rows.get(i).setTextColor(on ? INK : SOFT);
        }
        // Keep the list no taller than most of the screen, and the selected row in view.
        int max = (int) (getResources().getDisplayMetrics().heightPixels * 0.66f);
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) scroll.getLayoutParams();
        int wanted = rows.size() * dp(44);
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
