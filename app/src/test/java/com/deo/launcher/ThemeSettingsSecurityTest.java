package com.sva.launcher;

import android.content.SharedPreferences;

import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Saved settings can come back from a restored backup, so every value the themes and the wallpapers
 * screen read must be checked against a fixed list: nothing unexpected may reach a file path or a tab.
 */
public final class ThemeSettingsSecurityTest {
    /** A small in-memory SharedPreferences for JVM tests. */
    private static final class Prefs implements SharedPreferences {
        final Map<String, Object> values = new HashMap<>();
        @Override public Map<String, ?> getAll() { return values; }
        @Override public String getString(String k, String d) { Object v = values.get(k); return v instanceof String ? (String) v : d; }
        @Override public Set<String> getStringSet(String k, Set<String> d) { return d; }
        @Override public int getInt(String k, int d) { Object v = values.get(k); return v instanceof Integer ? (Integer) v : d; }
        @Override public long getLong(String k, long d) { return d; }
        @Override public float getFloat(String k, float d) { return d; }
        @Override public boolean getBoolean(String k, boolean d) { Object v = values.get(k); return v instanceof Boolean ? (Boolean) v : d; }
        @Override public boolean contains(String k) { return values.containsKey(k); }
        @Override public Editor edit() {
            return new Editor() {
                @Override public Editor putString(String k, String v) { values.put(k, v); return this; }
                @Override public Editor putStringSet(String k, Set<String> v) { return this; }
                @Override public Editor putInt(String k, int v) { values.put(k, v); return this; }
                @Override public Editor putLong(String k, long v) { return this; }
                @Override public Editor putFloat(String k, float v) { return this; }
                @Override public Editor putBoolean(String k, boolean v) { values.put(k, v); return this; }
                @Override public Editor remove(String k) { values.remove(k); return this; }
                @Override public Editor clear() { values.clear(); return this; }
                @Override public boolean commit() { return true; }
                @Override public void apply() { }
            };
        }
        @Override public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) { }
        @Override public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) { }
    }

    @Test public void unknownFontFallsBackToABundledOne() {
        Prefs p = new Prefs();
        p.values.put(ModernTheme.PREF_FONT, "../../../../system/fonts/evil");
        assertEquals("tajawal", ModernTheme.font(p));
    }

    @Test public void verseFontAndTextSizeFallBackSafely() {
        Prefs p = new Prefs();
        p.values.put(ModernTheme.PREF_VERSE_FONT, "../evil");
        assertEquals("amiri", ModernTheme.verseFont(p));
        p.values.put(ModernTheme.PREF_TEXT_SIZE, "huge");
        assertEquals(1f, ModernTheme.textSizeFactor(p), 0f);
        ModernTheme.step(p, ModernTheme.PREF_TEXT_SIZE, ModernTheme.TEXT_SIZE_KEYS, "large", 1);
        assertEquals("small", p.values.get(ModernTheme.PREF_TEXT_SIZE));
    }

    @Test public void barTabsAcceptOnlyKnownTabs() {
        Prefs p = new Prefs();
        p.values.put(ModernTheme.PREF_TABS, "quran,../secret,prayer,quran,settings,haram");
        List<String> tabs = ModernTheme.tabs(p);
        assertEquals(java.util.Arrays.asList("quran", "prayer", "haram"), tabs);
    }

    @Test public void themeIsOneOfTheTwo() {
        Prefs p = new Prefs();
        p.values.put(ModernTheme.PREF_THEME, 99);
        assertEquals(ModernTheme.SIMPLE_2, ModernTheme.theme(p));
        p.values.put(ModernTheme.PREF_THEME, 1);
        assertEquals(ModernTheme.SIMPLE_1, ModernTheme.theme(p));
    }

    @Test public void galleryCategoryCannotPointOutsideTheBundledFolders() {
        Prefs p = new Prefs();
        p.values.put(GalleryView.PREF_CATEGORY, "../../databases");
        assertEquals(0, GalleryView.category(p));
        GalleryView.nextCategory(p, -1);
        String key = p.getString(GalleryView.PREF_CATEGORY, "");
        boolean known = false;
        for (String k : GalleryView.CATEGORY_KEYS) known |= k.equals(key);
        assertTrue(known);
        assertFalse(key.contains("/") || key.contains("."));
    }
}
