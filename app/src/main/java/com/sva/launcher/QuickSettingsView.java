package com.sva.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * The settings, opened with the remote's Menu key (or a long press on «آمر»), laid out like a car's
 * settings screen: categories down the right, the chosen category's options on the left as large
 * rows with switches, segmented choices and values.
 * In the categories: Up/Down choose, Left or OK enter. In the options: Up/Down move, OK or Left/Right
 * change, Back returns to the categories. Back from the categories (or Menu anywhere) closes.
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
        /** Theme, day/night, fonts, cards or tabs changed. */
        void onHomeThemeChanged();
        void onGalleryCategoryChanged();
        /** Prayer city or alerts changed; {@code alertsTurnedOn} when alerts were just switched on. */
        void onPrayerSettingsChanged(boolean alertsTurnedOn);
        void onFixedBackgroundChanged();
    }

    static final String PREF_WEATHER_CITY = "weatherCityKey";
    private static final int INK = 0xFFEEF0F3, SOFT = 0x9EEEF0F3, FAINT = 0x5CEEF0F3, GOLD = 0xFFE3B469,
            GLASS = 0xE6101419, ROW = 0x0FFFFFFF, EDGE = 0x1CFFFFFF, DARK = 0xFF14181E;

    // Categories, top to bottom.
    private static final int LOOK = 0, FONTS = 1, WALLS = 2, HOME = 3, PRAYER = 4, SYSTEM = 5;
    private static final String[] CATEGORY_NAMES = {"المظهر", "الخطوط", "الخلفيات", "الشاشة الرئيسية", "الصلاة والطقس", "النظام"};
    private static final String[] CATEGORY_ICONS = {"look", "fonts", "walls", "home", "prayer", "system"};

    // Options.
    private static final int THEME = 0, MODE = 1, FONT = 2, VERSE_FONT = 3, TEXT_SIZE = 4, FONT_SAMPLE = 5,
            MOOD = 6, COLOUR = 7, FIXED = 8, FIXED_SCREEN = 9, FIXED_CLEAR = 12, GALLERY_SET = 13, GALLERY = 14,
            CARD = 20, APPS = 40, TABS = 41, WEATHER = 42, PRAYER_CITY = 43, ALERTS = 44, KIOSK = 45, ABOUT = 46;
    private static final int SEG = 0, SWITCH = 1, VALUE = 2, GO = 3, ACTION = 4, TILES = 5, INFO = 6, PICK = 7;
    private static final int PAGE_OPTIONS = 0, PAGE_APPS = 1, PAGE_TABS = 2;

    private final SharedPreferences preferences;
    private final Listener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final LinearLayout categoryList, options;
    private final TextView title, subtitle, hint;
    private final ScrollView scroll;
    private final ArrayList<View> categoryRows = new ArrayList<>();
    /** The options of the current category: ids, kinds and their row views. */
    private final ArrayList<Integer> ids = new ArrayList<>(), kinds = new ArrayList<>();
    private final ArrayList<View> rows = new ArrayList<>();
    private int category, selected;
    private boolean inCategories = true;
    /** Ignore key releases that belong to the press which opened the panel. */
    private boolean armed;
    private int page = PAGE_OPTIONS;
    private final ArrayList<String[]> entries = new ArrayList<>();
    private final ArrayList<String> shown = new ArrayList<>();
    private String thumbAsset;
    private Bitmap thumb;

    QuickSettingsView(Context context, SharedPreferences preferences, Listener listener) {
        super(context);
        this.preferences = preferences;
        this.listener = listener;
        setBackgroundColor(0xB3050709);
        setVisibility(GONE);
        setClickable(true);

        // Right: the categories.
        LinearLayout side = new LinearLayout(context);
        side.setOrientation(LinearLayout.VERTICAL);
        side.setBackground(glass(dp(20)));
        side.setPadding(dp(12), dp(18), dp(12), dp(18));
        LinearLayout head = new LinearLayout(context);
        head.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        head.setPadding(dp(8), 0, dp(8), dp(14));
        LinearLayout headCopy = new LinearLayout(context);
        headCopy.setOrientation(LinearLayout.VERTICAL);
        headCopy.addView(text(context, 18, INK, true), new LinearLayout.LayoutParams(-1, -2));
        ((TextView) headCopy.getChildAt(0)).setText("الإعدادات");
        TextView headHint = text(context, 11, SOFT, false);
        headHint.setText("زر القائمة في الريموت");
        headCopy.addView(headHint, new LinearLayout.LayoutParams(-1, -2));
        head.addView(headCopy, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams gearParams = new LinearLayout.LayoutParams(dp(26), dp(26));
        gearParams.leftMargin = dp(10);
        head.addView(new Icon(context, "system", GOLD), gearParams);
        side.addView(head, new LinearLayout.LayoutParams(-1, -2));
        View rule = new View(context);
        rule.setBackgroundColor(0x14FFFFFF);
        LinearLayout.LayoutParams ruleParams = new LinearLayout.LayoutParams(-1, Math.max(1, dp(1)));
        ruleParams.bottomMargin = dp(10);
        side.addView(rule, ruleParams);
        categoryList = new LinearLayout(context);
        categoryList.setOrientation(LinearLayout.VERTICAL);
        side.addView(categoryList, new LinearLayout.LayoutParams(-1, -2));
        LayoutParams sideParams = new LayoutParams(dp(236), -1, Gravity.RIGHT);
        sideParams.setMargins(0, dp(24), dp(24), dp(24));
        addView(side, sideParams);

        // Left: the options of the chosen category.
        LinearLayout main = new LinearLayout(context);
        main.setOrientation(LinearLayout.VERTICAL);
        main.setBackground(glass(dp(20)));
        main.setPadding(dp(26), dp(20), dp(26), dp(12));
        title = text(context, 22, INK, true);
        main.addView(title, new LinearLayout.LayoutParams(-1, -2));
        subtitle = text(context, 12, SOFT, false);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(-1, -2);
        subtitleParams.bottomMargin = dp(12);
        main.addView(subtitle, subtitleParams);
        scroll = new ScrollView(context);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setFocusable(false);
        options = new LinearLayout(context);
        options.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(options, new ScrollView.LayoutParams(-1, -2));
        main.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        hint = text(context, 10, FAINT, false);
        hint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(-1, -2);
        hintParams.topMargin = dp(8);
        main.addView(hint, hintParams);
        LayoutParams mainParams = new LayoutParams(-1, -1);
        mainParams.setMargins(dp(24), dp(24), dp(276), dp(24));
        addView(main, mainParams);
    }

    boolean isOpen() { return getVisibility() == VISIBLE; }

    void open() {
        page = PAGE_OPTIONS;
        inCategories = true;
        selected = 0;
        armed = false;
        render();
        setVisibility(VISIBLE);
        bringToFront();
    }

    void close() { setVisibility(GONE); }

    /** Re-reads everything, for example after a fixed background was chosen in «الخلفيات». */
    void refresh() { if (isOpen()) render(); }

    boolean handleKey(KeyEvent event) {
        if (!isOpen()) return false;
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        if (down && event.getRepeatCount() == 0) armed = true;
        else if (down && !armed) return true; // Held key from the long press that opened the panel.
        else if (!armed) return true; // The release of the key that opened the panel.
        int code = event.getKeyCode();
        boolean ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER;
        boolean left = code == KeyEvent.KEYCODE_DPAD_LEFT, right = code == KeyEvent.KEYCODE_DPAD_RIGHT;
        if (code == KeyEvent.KEYCODE_MENU || code == KeyEvent.KEYCODE_SETTINGS) {
            if (!down) close();
            return true;
        }
        if (code == KeyEvent.KEYCODE_BACK) {
            if (!down) back();
            return true;
        }
        if (code == KeyEvent.KEYCODE_DPAD_DOWN || code == KeyEvent.KEYCODE_DPAD_UP) {
            if (down) move(code == KeyEvent.KEYCODE_DPAD_DOWN ? 1 : -1);
            return true;
        }
        if (inCategories) {
            if ((left && down) || (ok && !down)) enterOptions();
            return true;
        }
        if (page != PAGE_OPTIONS) return handleListKey(code, down, ok);
        if (rows.isEmpty()) return true;
        int id = ids.get(selected), kind = kinds.get(selected);
        if (left || right) {
            if (!down) return true;
            if (kind == GO || kind == ACTION) { if (right) back(); }
            else change(id, left ? 1 : -1);
            return true;
        }
        if (ok && !down) {
            if (id == APPS) openList(PAGE_APPS);
            else if (id == TABS) openList(PAGE_TABS);
            else change(id, 1);
        }
        return true;
    }

    private void back() {
        if (page != PAGE_OPTIONS) {
            int keep = page == PAGE_APPS ? APPS : TABS;
            page = PAGE_OPTIONS;
            render();
            selected = Math.max(0, ids.indexOf(keep));
            paint();
        } else if (!inCategories) {
            inCategories = true;
            paint();
        } else close();
    }

    private void enterOptions() {
        selected = firstSelectable();
        if (selected < 0) { selected = 0; return; }
        inCategories = false;
        paint();
    }

    private int firstSelectable() {
        for (int i = 0; i < kinds.size(); i++) if (kinds.get(i) != INFO) return i;
        return -1;
    }

    private void move(int delta) {
        if (inCategories) {
            int next = Math.max(0, Math.min(CATEGORY_NAMES.length - 1, category + delta));
            if (next != category) { category = next; render(); }
            return;
        }
        int at = selected;
        do { at += delta; } while (at >= 0 && at < kinds.size() && kinds.get(at) == INFO);
        if (at >= 0 && at < kinds.size()) { selected = at; paint(); }
    }

    // ---- Options ----

    private void render() {
        renderCategories();
        if (page != PAGE_OPTIONS) { renderList(); return; }
        title.setText(CATEGORY_NAMES[category]);
        ids.clear();
        kinds.clear();
        boolean modern = ModernTheme.modern(preferences);
        switch (category) {
            case LOOK:
                subtitle.setText("الثيم وطريقة العرض");
                add(THEME, SEG);
                if (modern) add(MODE, SEG);
                break;
            case FONTS:
                subtitle.setText(modern ? "يتغيّر النص في «آمر» مباشرة" : "خط الواجهة وحجمه يظهران في ثيم «بسيط ٢»");
                add(FONT, TILES);
                add(VERSE_FONT, SEG);
                add(TEXT_SIZE, SEG);
                add(FONT_SAMPLE, INFO);
                break;
            case WALLS:
                subtitle.setText("خلفية «آمر» وشاشة «الخلفيات»");
                add(MOOD, VALUE);
                if (WallpaperMoods.CALM.equals(WallpaperMoods.current(preferences))) add(COLOUR, VALUE);
                if (modern) {
                    add(FIXED, PICK);
                    if (FixedBackground.picture(preferences) != null) {
                        for (int i = 0; i < FixedBackground.SCREEN_KEYS.length; i++) add(FIXED_SCREEN + i, SWITCH);
                        add(FIXED_CLEAR, ACTION);
                    }
                }
                add(GALLERY_SET, VALUE);
                add(GALLERY, VALUE);
                break;
            case HOME:
                subtitle.setText(modern ? "البطاقات والتطبيقات والتبويبات" : "التطبيقات في الشاشة الرئيسية");
                if (modern) for (int i = 0; i < ModernTheme.CARD_KEYS.length; i++) add(CARD + i, SWITCH);
                add(APPS, GO);
                if (modern) add(TABS, GO);
                break;
            case PRAYER:
                subtitle.setText("أوقات الصلاة والتنبيه والطقس");
                add(PRAYER_CITY, VALUE);
                add(ALERTS, SWITCH);
                add(WEATHER, VALUE);
                break;
            default:
                subtitle.setText(" ");
                add(KIOSK, SWITCH);
                add(ABOUT, INFO);
                break;
        }
        options.removeAllViews();
        rows.clear();
        for (int i = 0; i < ids.size(); i++) {
            View row = buildRow(ids.get(i), kinds.get(i));
            rows.add(row);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.bottomMargin = dp(8);
            options.addView(row, params);
        }
        if (!inCategories) {
            selected = Math.max(0, Math.min(ids.size() - 1, selected));
            if (!ids.isEmpty() && kinds.get(selected) == INFO) selected = Math.max(0, firstSelectable());
        }
        paint();
    }

    private void add(int id, int kind) { ids.add(id); kinds.add(kind); }

    private void renderCategories() {
        categoryList.removeAllViews();
        categoryRows.clear();
        for (int i = 0; i < CATEGORY_NAMES.length; i++) {
            LinearLayout row = new LinearLayout(getContext());
            row.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
            row.setPadding(dp(12), 0, dp(12), 0);
            boolean on = i == category;
            TextView name = text(getContext(), 15, on ? INK : SOFT, on);
            name.setText(CATEGORY_NAMES[i]);
            row.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(22), dp(22));
            iconParams.leftMargin = dp(12);
            row.addView(new Icon(getContext(), CATEGORY_ICONS[i], on ? GOLD : SOFT), iconParams);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(50));
            params.bottomMargin = dp(4);
            categoryList.addView(row, params);
            categoryRows.add(row);
        }
    }

    private View buildRow(int id, int kind) {
        Context context = getContext();
        if (id == FONT) return fontTiles();
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(58));
        row.setPadding(dp(16), dp(8), dp(16), dp(8));
        if (id == FONT_SAMPLE) {
            TextView sample = text(context, 20, INK, false);
            sample.setTypeface(ModernTheme.typeface(context, ModernTheme.verseFont(preferences), 400));
            sample.setGravity(Gravity.CENTER);
            sample.setText("﴿ أَلَا بِذِكْرِ اللَّهِ تَطْمَئِنُّ الْقُلُوبُ ﴾");
            row.addView(sample, new LinearLayout.LayoutParams(-1, -2));
            return row;
        }
        // Left to right on screen: the control, then the label reading from the right.
        View control = control(id, kind);
        if (control != null) row.addView(control, new LinearLayout.LayoutParams(-2, -2));
        LinearLayout copy = new LinearLayout(context);
        copy.setOrientation(LinearLayout.VERTICAL);
        TextView name = text(context, 15, INK, true);
        name.setText(name(id));
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        copy.addView(name, new LinearLayout.LayoutParams(-1, -2));
        String detail = detail(id);
        if (!detail.isEmpty()) {
            TextView small = text(context, 11, SOFT, false);
            small.setText(detail);
            small.setSingleLine(true);
            small.setEllipsize(TextUtils.TruncateAt.END);
            copy.addView(small, new LinearLayout.LayoutParams(-1, -2));
        }
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(0, -2, 1);
        copyParams.leftMargin = dp(14);
        row.addView(copy, copyParams);
        return row;
    }

    private View control(int id, int kind) {
        Context context = getContext();
        switch (kind) {
            case SEG: {
                String[] items;
                int on;
                if (id == THEME) { items = ModernTheme.THEME_NAMES; on = ModernTheme.theme(preferences) - 1; }
                else if (id == MODE) { items = new String[]{"ليل", "نهار"}; on = ModernTheme.day(preferences) ? 1 : 0; }
                else if (id == VERSE_FONT) { items = ModernTheme.VERSE_FONT_NAMES; on = "naskh".equals(ModernTheme.verseFont(preferences)) ? 1 : 0; }
                else { items = ModernTheme.TEXT_SIZE_NAMES; on = ModernTheme.textSize(preferences); }
                LinearLayout seg = new LinearLayout(context);
                seg.setPadding(dp(3), dp(3), dp(3), dp(3));
                seg.setBackground(round(0x40000000, dp(12)));
                // The first choice sits on the right.
                for (int i = items.length - 1; i >= 0; i--) {
                    TextView item = text(context, 13, i == on ? DARK : SOFT, i == on);
                    item.setText(items[i]);
                    if (id == VERSE_FONT) item.setTypeface(ModernTheme.typeface(context, ModernTheme.VERSE_FONT_KEYS[i], 400));
                    item.setPadding(dp(14), dp(6), dp(14), dp(6));
                    item.setBackground(i == on ? round(GOLD, dp(9)) : null);
                    seg.addView(item);
                }
                return seg;
            }
            case SWITCH: return new Switch(context, switchOn(id));
            case VALUE: {
                TextView value = text(context, 14, INK, false);
                value.setText("‹   " + value(id) + "   ›");
                value.setSingleLine(true);
                return value;
            }
            case GO: case ACTION: {
                TextView arrow = text(context, 18, SOFT, false);
                arrow.setText(kind == GO ? "‹" : "");
                return arrow;
            }
            case PICK: {
                // ‹ picture › : Left/Right walk through every picture in «الخلفيات».
                LinearLayout pick = new LinearLayout(context);
                pick.setGravity(Gravity.CENTER_VERTICAL);
                TextView before = text(context, 16, SOFT, false);
                before.setText("‹");
                TextView after = text(context, 16, SOFT, false);
                after.setText("›");
                pick.addView(before);
                LinearLayout.LayoutParams thumbParams = new LinearLayout.LayoutParams(dp(74), dp(42));
                thumbParams.setMargins(dp(10), 0, dp(10), 0);
                pick.addView(thumbnail(), thumbParams);
                pick.addView(after);
                return pick;
            }
            case INFO:
                return null;
            default: return null;
        }
    }

    private View fontTiles() {
        Context context = getContext();
        LinearLayout tiles = new LinearLayout(context);
        String current = ModernTheme.font(preferences);
        // The first font sits on the right.
        for (int i = ModernTheme.FONT_KEYS.length - 1; i >= 0; i--) {
            String key = ModernTheme.FONT_KEYS[i];
            LinearLayout tile = new LinearLayout(context);
            tile.setOrientation(LinearLayout.VERTICAL);
            tile.setGravity(Gravity.CENTER);
            boolean on = key.equals(current);
            GradientDrawable shape = round(on ? 0x1AE3B469 : ROW, dp(14));
            if (on) shape.setStroke(dp(2), GOLD);
            tile.setBackground(shape);
            TextView sample = text(context, 22 * ModernTheme.scale(key), INK, false);
            sample.setTypeface(ModernTheme.typeface(context, key, 400));
            sample.setGravity(Gravity.CENTER);
            sample.setIncludeFontPadding(false);
            sample.setText("آمر");
            tile.addView(sample);
            TextView name = text(context, 11, on ? INK : SOFT, false);
            name.setGravity(Gravity.CENTER);
            name.setText(ModernTheme.FONT_NAMES[i]);
            tile.addView(name);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(76), 1);
            params.setMargins(dp(4), 0, dp(4), 0);
            tiles.addView(tile, params);
        }
        return tiles;
    }

    private View thumbnail() {
        ImageView image = new ImageView(getContext());
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackground(round(0x33000000, dp(8)));
        image.setClipToOutline(true);
        image.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View view, android.graphics.Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(8));
            }
        });
        String asset = FixedBackground.picture(preferences);
        if (asset == null) return image;
        if (asset.equals(thumbAsset) && thumb != null) { image.setImageBitmap(thumb); return image; }
        thumbAsset = asset;
        new Thread(() -> {
            Bitmap small = null;
            try (InputStream in = getContext().getAssets().open(asset)) {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = 16;
                small = BitmapFactory.decodeStream(in, null, options);
            } catch (Exception | OutOfMemoryError ignored) { }
            final Bitmap loaded = small;
            handler.post(() -> {
                if (loaded == null || !asset.equals(thumbAsset)) return;
                thumb = loaded;
                image.setImageBitmap(loaded);
            });
        }, "settings-thumb").start();
        return image;
    }

    private String name(int id) {
        if (id >= CARD && id < CARD + ModernTheme.CARD_KEYS.length) return ModernTheme.CARD_NAMES[id - CARD];
        if (id >= FIXED_SCREEN && id < FIXED_SCREEN + 3) return "على " + FixedBackground.SCREEN_NAMES[id - FIXED_SCREEN];
        switch (id) {
            case THEME: return "الثيم";
            case MODE: return "الوضع";
            case VERSE_FONT: return "خط الآية";
            case TEXT_SIZE: return "حجم الخط";
            case MOOD: return "خلفية «آمر»";
            case COLOUR: return "لون الهدوء";
            case FIXED: return "الخلفية الثابتة";
            case FIXED_CLEAR: return "إزالة الخلفية الثابتة";
            case GALLERY_SET: return "تصنيف «الخلفيات»";
            case GALLERY: return "تبديل الصور";
            case APPS: return "التطبيقات وترتيبها";
            case TABS: return "التبويبات وترتيبها";
            case WEATHER: return "مدينة الطقس";
            case PRAYER_CITY: return "مدينة الصلاة";
            case ALERTS: return "تنبيه الأذان والإقامة";
            case KIOSK: return "المشغّل دائم على الشاشة";
            case ABOUT: return "عن التطبيق";
            default: return "";
        }
    }

    private String detail(int id) {
        switch (id) {
            case THEME: return "بسيط ١ الواجهة الأصلية  ·  بسيط ٢ البطاقات الزجاجية";
            case MODE: return "للثيم «بسيط ٢»";
            case VERSE_FONT: return "في «آمر» وصفحة الصلاة";
            case FIXED: {
                String asset = FixedBackground.picture(preferences);
                return asset == null ? "يمين/يسار لاختيار صورة، أو OK مطوّلًا على صورة في «الخلفيات»"
                        : FixedBackground.label(asset) + "  ·  يمين/يسار للتغيير";
            }
            case FIXED_CLEAR: return "ترجع الخلفية العادية في كل الشاشات";
            case APPS: {
                List<String> apps = listener.homeApps();
                return ArabicFormat.digits(apps.size()) + " تطبيقات ظاهرة";
            }
            case TABS: {
                StringBuilder names = new StringBuilder("آمر");
                for (String key : ModernTheme.tabs(preferences)) names.append("  ·  ").append(ModernTheme.tabName(key));
                return names.toString();
            }
            case ALERTS: return "بطاقة صغيرة فوق التطبيقات عند الأذان والإقامة";
            case KIOSK: return "يرجع «آمر» بعد إطفاء الشاشة وتشغيلها";
            case ABOUT: return "AAA TV  ·  الإصدار " + version();
            default: return "";
        }
    }

    private String value(int id) {
        switch (id) {
            case MOOD: return WallpaperMoods.NAMES[WallpaperMoods.index(WallpaperMoods.current(preferences))];
            case COLOUR: return CalmWallpapers.NAMES[Math.max(0, Math.min(CalmWallpapers.NAMES.length - 1,
                    preferences.getInt(CalmWallpapers.PREF, CalmWallpapers.DEFAULT)))];
            case GALLERY_SET: return GalleryView.categoryName(preferences);
            case GALLERY: {
                int minutes = GalleryView.interval(preferences);
                return minutes == 0 ? "مثبّتة" : GalleryView.intervalName(minutes);
            }
            case WEATHER: return PrayerTimes.city(preferences.getString(PREF_WEATHER_CITY, "riyadh")).name;
            case PRAYER_CITY: return PrayerTimes.city(preferences.getString(PrayerView.PREF_CITY, "riyadh")).name;
            default: return "";
        }
    }

    private boolean switchOn(int id) {
        if (id >= CARD && id < CARD + ModernTheme.CARD_KEYS.length) return ModernTheme.card(preferences, ModernTheme.CARD_KEYS[id - CARD]);
        if (id >= FIXED_SCREEN && id < FIXED_SCREEN + 3) return FixedBackground.on(preferences, FixedBackground.SCREEN_KEYS[id - FIXED_SCREEN]);
        if (id == ALERTS) return preferences.getBoolean(PrayerAlerts.PREF_ENABLED, false);
        if (id == KIOSK) return preferences.getBoolean(LauncherOverrideService.KIOSK, true);
        return false;
    }

    private String version() {
        try {
            return ArabicFormat.digits(getContext().getPackageManager()
                    .getPackageInfo(getContext().getPackageName(), 0).versionName);
        } catch (Exception unknown) {
            return "";
        }
    }

    /** Right-to-left: Left moves forward through the values, Right goes back. */
    private void change(int id, int delta) {
        boolean structure = false; // Rows appear or disappear, so the list is rebuilt from scratch.
        if (id >= CARD && id < CARD + ModernTheme.CARD_KEYS.length) {
            String key = ModernTheme.CARD_KEYS[id - CARD];
            preferences.edit().putBoolean(ModernTheme.CARD_PREFIX + key, !ModernTheme.card(preferences, key)).apply();
            listener.onHomeThemeChanged();
        } else if (id >= FIXED_SCREEN && id < FIXED_SCREEN + 3) {
            FixedBackground.toggle(preferences, FixedBackground.SCREEN_KEYS[id - FIXED_SCREEN]);
            listener.onFixedBackgroundChanged();
        } else switch (id) {
            case THEME:
                preferences.edit().putInt(ModernTheme.PREF_THEME,
                        ModernTheme.theme(preferences) == ModernTheme.SIMPLE_1 ? ModernTheme.SIMPLE_2 : ModernTheme.SIMPLE_1).apply();
                listener.onHomeThemeChanged();
                structure = true;
                break;
            case MODE:
                preferences.edit().putString(ModernTheme.PREF_MODE, ModernTheme.day(preferences) ? "night" : "day").apply();
                listener.onHomeThemeChanged();
                break;
            case FONT:
                ModernTheme.step(preferences, ModernTheme.PREF_FONT, ModernTheme.FONT_KEYS, ModernTheme.font(preferences), delta);
                listener.onHomeThemeChanged();
                break;
            case VERSE_FONT:
                ModernTheme.step(preferences, ModernTheme.PREF_VERSE_FONT, ModernTheme.VERSE_FONT_KEYS,
                        ModernTheme.verseFont(preferences), delta);
                listener.onHomeThemeChanged();
                break;
            case TEXT_SIZE: {
                int next = ModernTheme.textSize(preferences) + delta;
                if (next < 0 || next >= ModernTheme.TEXT_SIZE_KEYS.length) next = Math.floorMod(next, ModernTheme.TEXT_SIZE_KEYS.length);
                preferences.edit().putString(ModernTheme.PREF_TEXT_SIZE, ModernTheme.TEXT_SIZE_KEYS[next]).apply();
                listener.onHomeThemeChanged();
                break;
            }
            case MOOD: {
                int index = Math.floorMod(WallpaperMoods.index(WallpaperMoods.current(preferences)) + delta, WallpaperMoods.KEYS.length);
                String mood = WallpaperMoods.KEYS[index];
                preferences.edit().putString(WallpaperMoods.PREF, mood).apply();
                listener.onMoodChanged(mood);
                structure = true; // The colour row appears only for the calm background.
                break;
            }
            case COLOUR: {
                int index = Math.floorMod(preferences.getInt(CalmWallpapers.PREF, CalmWallpapers.DEFAULT) + delta, CalmWallpapers.NAMES.length);
                preferences.edit().putInt(CalmWallpapers.PREF, index).apply();
                listener.onCalmColourChanged(index);
                break;
            }
            case FIXED: {
                List<String> all = GalleryView.picturesIn(getContext().getAssets(), "all");
                if (all.isEmpty()) return;
                int at = all.indexOf(FixedBackground.picture(preferences));
                int next = at < 0 ? (delta > 0 ? 0 : all.size() - 1) : Math.floorMod(at + delta, all.size());
                FixedBackground.set(preferences, all.get(next));
                listener.onFixedBackgroundChanged();
                structure = true; // The per-screen switches appear once a picture is set.
                break;
            }
            case FIXED_CLEAR:
                FixedBackground.clear(preferences);
                listener.onFixedBackgroundChanged();
                structure = true;
                break;
            case GALLERY_SET:
                GalleryView.nextCategory(preferences, delta);
                listener.onGalleryCategoryChanged();
                break;
            case GALLERY:
                listener.onGalleryIntervalChanged(GalleryView.nextInterval(preferences, delta));
                break;
            case WEATHER: {
                PrayerTimes.City next = stepCity(PREF_WEATHER_CITY, delta);
                listener.onWeatherCityChanged(next);
                break;
            }
            case PRAYER_CITY:
                stepCity(PrayerView.PREF_CITY, delta);
                listener.onPrayerSettingsChanged(false);
                break;
            case ALERTS: {
                boolean enabled = !preferences.getBoolean(PrayerAlerts.PREF_ENABLED, false);
                preferences.edit().putBoolean(PrayerAlerts.PREF_ENABLED, enabled).apply();
                listener.onPrayerSettingsChanged(enabled);
                break;
            }
            case KIOSK: {
                boolean enabled = !preferences.getBoolean(LauncherOverrideService.KIOSK, true);
                preferences.edit().putBoolean(LauncherOverrideService.KIOSK, enabled).apply();
                listener.onKioskChanged(enabled);
                break;
            }
            default:
                return;
        }
        int keep = id;
        int keepIndex = selected;
        render();
        int at = ids.indexOf(keep);
        selected = at >= 0 ? at : Math.max(0, Math.min(keepIndex, ids.size() - 1));
        if (!ids.isEmpty() && kinds.get(selected) == INFO) selected = Math.max(0, firstSelectable());
        if (structure) inCategories = ids.isEmpty() || inCategories;
        paint();
    }

    private PrayerTimes.City stepCity(String pref, int delta) {
        PrayerTimes.City current = PrayerTimes.city(preferences.getString(pref, "riyadh"));
        int index = 0;
        for (int i = 0; i < PrayerTimes.CITIES.length; i++) if (PrayerTimes.CITIES[i] == current) index = i;
        PrayerTimes.City next = PrayerTimes.CITIES[Math.floorMod(index + delta, PrayerTimes.CITIES.length)];
        preferences.edit().putString(pref, next.key).apply();
        return next;
    }

    // ---- List pages: home apps and bar tabs ----

    private void openList(int which) {
        page = which;
        entries.clear();
        shown.clear();
        if (which == PAGE_APPS) {
            entries.addAll(listener.installedApps());
            shown.addAll(listener.homeApps());
        } else {
            for (String key : ModernTheme.TAB_KEYS) entries.add(new String[]{key, ModernTheme.tabName(key)});
            shown.addAll(ModernTheme.tabs(preferences));
        }
        selected = 0;
        renderList();
    }

    /** Shown entries first, in their order, then the rest. */
    private List<String[]> orderedEntries() {
        ArrayList<String[]> ordered = new ArrayList<>();
        for (String key : shown) for (String[] entry : entries) if (entry[0].equals(key)) ordered.add(entry);
        for (String[] entry : entries) if (!shown.contains(entry[0])) ordered.add(entry);
        return ordered;
    }

    private void renderList() {
        title.setText(page == PAGE_APPS ? "التطبيقات وترتيبها" : "التبويبات وترتيبها");
        subtitle.setText(page == PAGE_APPS ? "الظاهرة أولًا بترتيبها" : "«آمر» ثابت أولًا، ثم هذه بترتيبها");
        options.removeAllViews();
        rows.clear();
        ids.clear();
        kinds.clear();
        for (String[] entry : orderedEntries()) {
            int position = shown.indexOf(entry[0]);
            LinearLayout row = new LinearLayout(getContext());
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(dp(50));
            row.setPadding(dp(16), dp(6), dp(16), dp(6));
            row.addView(new Switch(getContext(), position >= 0), new LinearLayout.LayoutParams(-2, -2));
            TextView name = text(getContext(), 15, position >= 0 ? INK : SOFT, position >= 0);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            name.setText(position >= 0 ? ArabicFormat.digits(position + 1) + ".  " + entry[1] : entry[1]);
            LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0, -2, 1);
            nameParams.leftMargin = dp(14);
            row.addView(name, nameParams);
            row.setTag(entry[0]);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.bottomMargin = dp(6);
            options.addView(row, params);
            rows.add(row);
            ids.add(-1);
            kinds.add(SWITCH);
        }
        selected = Math.max(0, Math.min(rows.size() - 1, selected));
        paint();
    }

    private void saveList() {
        if (page == PAGE_APPS) {
            listener.onHomeAppsChanged(new ArrayList<>(shown));
            return;
        }
        preferences.edit().putString(ModernTheme.PREF_TABS, String.join(",", shown)).apply();
        listener.onHomeThemeChanged();
    }

    private boolean handleListKey(int code, boolean down, boolean ok) {
        if (rows.isEmpty()) return true;
        String key = (String) rows.get(selected).getTag();
        if (ok) {
            if (!down) {
                if (shown.contains(key)) shown.remove(key); else shown.add(key);
                saveList();
                renderList();
            }
            return true;
        }
        if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT) {
            int index = shown.indexOf(key);
            if (down && index >= 0) {
                // Right to left: Left moves the entry later, Right earlier.
                int target = Math.max(0, Math.min(shown.size() - 1, index + (code == KeyEvent.KEYCODE_DPAD_LEFT ? 1 : -1)));
                if (target != index) {
                    shown.remove(index);
                    shown.add(target, key);
                    selected = target;
                    saveList();
                    renderList();
                }
            }
            return true;
        }
        return true;
    }

    // ---- Painting ----

    private void paint() {
        for (int i = 0; i < categoryRows.size(); i++) {
            boolean on = i == category;
            GradientDrawable shape = new GradientDrawable(GradientDrawable.Orientation.RIGHT_LEFT,
                    on ? new int[]{0x38E3B469, 0x0AE3B469} : new int[]{Color.TRANSPARENT, Color.TRANSPARENT});
            shape.setCornerRadius(dp(14));
            if (on && inCategories) shape.setStroke(dp(2), GOLD);
            categoryRows.get(i).setBackground(shape);
        }
        for (int i = 0; i < rows.size(); i++) {
            if (ids.get(i) == FONT) {
                rows.get(i).setBackground(!inCategories && i == selected ? stroke() : null);
                rows.get(i).setPadding(dp(4), dp(4), dp(4), dp(4));
                continue;
            }
            boolean on = !inCategories && i == selected;
            GradientDrawable shape = round(on ? 0x14E3B469 : ROW, dp(14));
            shape.setStroke(on ? dp(2) : Math.max(1, dp(1)), on ? GOLD : Color.TRANSPARENT);
            rows.get(i).setBackground(shape);
        }
        if (inCategories) hint.setText("فوق/تحت: التصنيف   ·   يسار أو OK: الخيارات   ·   رجوع: إغلاق");
        else if (page != PAGE_OPTIONS) hint.setText("OK: إظهار أو إخفاء   ·   يمين/يسار: الترتيب   ·   رجوع: حفظ");
        else hint.setText("فوق/تحت: الخيار   ·   OK أو يمين/يسار: التغيير   ·   رجوع: التصنيفات");
        if (!inCategories && !rows.isEmpty()) {
            final View row = rows.get(Math.max(0, Math.min(rows.size() - 1, selected)));
            scroll.post(() -> {
                int top = row.getTop(), bottom = row.getBottom(), y = scroll.getScrollY(), h = scroll.getHeight();
                if (top < y) scroll.smoothScrollTo(0, top);
                else if (bottom > y + h) scroll.smoothScrollTo(0, bottom - h + dp(8));
            });
        } else scroll.scrollTo(0, 0);
    }

    private GradientDrawable stroke() {
        GradientDrawable shape = round(Color.TRANSPARENT, dp(18));
        shape.setStroke(dp(2), 0x80E3B469);
        return shape;
    }

    private GradientDrawable glass(int radius) {
        GradientDrawable shape = round(GLASS, radius);
        shape.setStroke(Math.max(1, dp(1)), EDGE);
        return shape;
    }

    private static GradientDrawable round(int color, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(radius);
        return shape;
    }

    private TextView text(Context context, float sp, int color, boolean medium) {
        TextView view = new TextView(context);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        view.setTextColor(color);
        view.setGravity(Gravity.RIGHT);
        view.setIncludeFontPadding(false);
        view.setPadding(0, dp(2), 0, dp(2));
        view.setTypeface(ModernTheme.typeface(context, "tajawal", medium ? 500 : 400));
        return view;
    }

    private int dp(float value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics()));
    }

    /** A gold on/off switch. */
    private final class Switch extends View {
        private final boolean on;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF box = new RectF();

        Switch(Context context, boolean on) { super(context); this.on = on; }

        @Override protected void onMeasure(int w, int h) { setMeasuredDimension(dp(46), dp(26)); }

        @Override protected void onDraw(Canvas canvas) {
            float r = getHeight() / 2f;
            box.set(0, 0, getWidth(), getHeight());
            paint.setColor(on ? GOLD : 0x24FFFFFF);
            canvas.drawRoundRect(box, r, r, paint);
            paint.setColor(Color.WHITE);
            float knob = r - dp(3);
            canvas.drawCircle(on ? getWidth() - r : r, r, knob, paint);
        }
    }

    /** Thin line icons for the categories, drawn in a 24×24 grid. */
    private static final class Icon extends View {
        private final String kind;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        Icon(Context context, String kind, int color) {
            super(context);
            this.kind = kind;
            paint.setColor(color);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStrokeWidth(1.6f);
        }

        @Override protected void onDraw(Canvas c) {
            float s = Math.min(getWidth(), getHeight()) / 24f;
            c.save();
            c.translate((getWidth() - 24 * s) / 2f, (getHeight() - 24 * s) / 2f);
            c.scale(s, s);
            Path p = new Path();
            switch (kind) {
                case "look":
                    c.drawCircle(12, 12, 8.5f, paint);
                    c.drawLine(12, 3.5f, 12, 20.5f, paint);
                    Paint fill = new Paint(paint);
                    fill.setStyle(Paint.Style.FILL);
                    fill.setAlpha(64);
                    c.drawArc(new RectF(3.5f, 3.5f, 20.5f, 20.5f), -90, 180, false, fill);
                    break;
                case "fonts":
                    p.moveTo(4, 19); p.lineTo(9.5f, 5); p.lineTo(10.5f, 5); p.lineTo(16, 19);
                    p.moveTo(6.3f, 13.5f); p.lineTo(13.7f, 13.5f);
                    p.moveTo(15, 19); p.lineTo(20, 19); p.moveTo(17.5f, 9); p.lineTo(17.5f, 19);
                    c.drawPath(p, paint);
                    break;
                case "walls":
                    c.drawRoundRect(new RectF(3, 5, 21, 19), 2.5f, 2.5f, paint);
                    p.moveTo(4, 17); p.lineTo(9, 12); p.lineTo(13, 16); p.lineTo(16, 13); p.lineTo(20, 17);
                    c.drawPath(p, paint);
                    c.drawCircle(16, 9, 1.4f, paint);
                    break;
                case "home":
                    p.moveTo(3, 11); p.lineTo(12, 4); p.lineTo(21, 11);
                    p.moveTo(6, 9.5f); p.lineTo(6, 20); p.lineTo(10.5f, 20); p.lineTo(10.5f, 15); p.lineTo(13.5f, 15);
                    p.lineTo(13.5f, 20); p.lineTo(18, 20); p.lineTo(18, 9.5f);
                    c.drawPath(p, paint);
                    break;
                case "prayer":
                    ModernIcons.drawKind(c, "dome", paint);
                    break;
                default:
                    c.drawCircle(12, 12, 3, paint);
                    float[][] rays = {{12, 2.5f, 12, 5}, {12, 19, 12, 21.5f}, {2.5f, 12, 5, 12}, {19, 12, 21.5f, 12},
                            {5.3f, 5.3f, 7.1f, 7.1f}, {16.9f, 16.9f, 18.7f, 18.7f}, {5.3f, 18.7f, 7.1f, 16.9f}, {16.9f, 7.1f, 18.7f, 5.3f}};
                    for (float[] r : rays) c.drawLine(r[0], r[1], r[2], r[3], paint);
                    break;
            }
            c.restore();
        }
    }
}
