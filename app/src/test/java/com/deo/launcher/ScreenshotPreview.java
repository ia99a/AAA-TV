package com.sva.launcher;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Looper;
import android.view.View;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.robolectric.Shadows.shadowOf;

/**
 * Draws the real «بسيط ٢» screens to PNG files for review, at 1920×1080 (a 960×540dp TV).
 * Runs only when the build is started with -Pscreens=true; the files land in app/build/screens.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 34, qualifiers = "w960dp-h540dp-land-xhdpi")
public final class ScreenshotPreview {
    private File dir;

    @Before public void onlyWhenAsked() {
        Assume.assumeTrue("true".equals(System.getProperty("screens")));
        dir = new File(System.getProperty("screensDir", "build/screens"));
        dir.mkdirs();
    }

    private static void prepare(SharedPreferences p, String font, String fixed) {
        SharedPreferences.Editor e = p.edit().putInt(ModernTheme.PREF_THEME, ModernTheme.SIMPLE_2)
                .putString(ModernTheme.PREF_FONT, font).putBoolean("firstRunShown", true);
        if (fixed != null) e.putString(FixedBackground.PREF_PICTURE, fixed); else e.remove(FixedBackground.PREF_PICTURE);
        e.commit();
    }

    private void shoot(String font, String fixed, int tab, String name, boolean settings) throws Exception {
        SharedPreferences p = org.robolectric.RuntimeEnvironment.getApplication()
                .getSharedPreferences("launcher", 0);
        prepare(p, font, fixed);
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();
        shadowOf(Looper.getMainLooper()).idle();
        Method select = MainActivity.class.getDeclaredMethod("selectTab", int.class, boolean.class);
        select.setAccessible(true);
        select.invoke(activity, tab, false);
        if (settings) {
            Field field = MainActivity.class.getDeclaredField("quickSettings");
            field.setAccessible(true);
            ((QuickSettingsView) field.get(activity)).open();
        }
        // Let background decoding (fixed picture, wallpapers) finish and post back.
        for (int i = 0; i < 20; i++) { Thread.sleep(150); shadowOf(Looper.getMainLooper()).idle(); }
        View root = activity.getWindow().getDecorView();
        int w = 1920, h = 1080;
        root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, w, h);
        shadowOf(Looper.getMainLooper()).idle();
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        root.draw(new Canvas(bitmap));
        try (FileOutputStream out = new FileOutputStream(new File(dir, name + ".png"))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
        activity.finish();
    }

    private static int tab(String name) throws Exception {
        Field field = MainActivity.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.getInt(null);
    }

    @Test public void prayerTajawal() throws Exception { shoot("tajawal", null, tab("TAB_PRAYER"), "prayer-tajawal", false); }
    @Test public void prayerHarmattan() throws Exception { shoot("harmattan", null, tab("TAB_PRAYER"), "prayer-harmattan", false); }
    @Test public void prayerJomhuria() throws Exception { shoot("jomhuria", null, tab("TAB_PRAYER"), "prayer-jomhuria", false); }
    @Test public void prayerRuqaaFixed() throws Exception {
        shoot("ruqaa", "gallery/nature/mountain-lake.jpg", tab("TAB_PRAYER"), "prayer-ruqaa-fixed", false);
    }
    @Test public void calendarTajawal() throws Exception { shoot("tajawal", null, tab("TAB_EVENTS"), "calendar-tajawal", false); }
    @Test public void calendarHarmattanFixed() throws Exception {
        shoot("harmattan", "gallery/nature/mountain-lake.jpg", tab("TAB_EVENTS"), "calendar-harmattan-fixed", false);
    }
    @Test public void homeTajawal() throws Exception { shoot("tajawal", null, tab("TAB_HOME"), "home-tajawal", false); }
    @Test public void homeFixed() throws Exception {
        shoot("tajawal", "gallery/nature/mountain-lake.jpg", tab("TAB_HOME"), "home-fixed", false);
    }
    @Test public void settings() throws Exception { shoot("tajawal", null, tab("TAB_HOME"), "settings", true); }
}
