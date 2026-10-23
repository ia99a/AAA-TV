package com.sva.launcher;

import android.content.SharedPreferences;

import java.util.regex.Pattern;

/**
 * «الخلفية الثابتة» in «بسيط ٢»: one picture from «الخلفيات», chosen with a long press on OK, shown
 * still behind «آمر», the prayer page and «تاريخك», each of which can be switched off on its own.
 */
final class FixedBackground {
    static final String PREF_PICTURE = "fixedPicture", PREF_ON_PREFIX = "fixedOn_";
    static final String[] SCREEN_KEYS = {"home", "prayer", "events"};
    static final String[] SCREEN_NAMES = {"«آمر»", "الصلاة", "تاريخك"};
    /** Only pictures bundled in the app: a known folder and a plain file name, nothing else. */
    private static final Pattern ASSET = Pattern.compile("(frames|gallery/(nature|drawn|aerial|space|aviation))/[a-z0-9][a-z0-9-]{0,60}\\.jpg");

    private FixedBackground() { }

    static boolean valid(String asset) { return asset != null && ASSET.matcher(asset).matches(); }

    /** The chosen picture's asset path, or null when none is set (or the saved value is not one of ours). */
    static String picture(SharedPreferences p) {
        String asset = p.getString(PREF_PICTURE, null);
        return valid(asset) ? asset : null;
    }

    static boolean set(SharedPreferences p, String asset) {
        if (!valid(asset)) return false;
        p.edit().putString(PREF_PICTURE, asset).apply();
        return true;
    }

    static void clear(SharedPreferences p) { p.edit().remove(PREF_PICTURE).apply(); }

    static boolean on(SharedPreferences p, String screen) { return p.getBoolean(PREF_ON_PREFIX + screen, true); }

    static void toggle(SharedPreferences p, String screen) {
        p.edit().putBoolean(PREF_ON_PREFIX + screen, !on(p, screen)).apply();
    }

    /** Whether the picture shows on this screen right now: «بسيط ٢», a picture, and the screen switched on. */
    static boolean showsOn(SharedPreferences p, String screen) {
        return screen != null && ModernTheme.modern(p) && picture(p) != null && on(p, screen);
    }

    /** "طبيعة · mountain lake" style label for the settings. */
    static String label(String asset) {
        if (asset == null) return "لم تُختر بعد";
        String folder = asset.startsWith("frames/") ? "frames" : asset.substring(8, asset.indexOf('/', 8));
        String category = "";
        for (int i = 0; i < GalleryView.CATEGORY_KEYS.length; i++)
            if (GalleryView.CATEGORY_KEYS[i].equals(folder)) category = GalleryView.CATEGORY_NAMES[i];
        String name = asset.substring(asset.lastIndexOf('/') + 1, asset.length() - 4).replace('-', ' ');
        return category + "  ·  " + name;
    }
}
