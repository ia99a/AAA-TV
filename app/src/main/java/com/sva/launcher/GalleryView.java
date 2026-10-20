package com.sva.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * «الخلفيات»: framed paintings and calligraphy that fill the screen to its edges, fading slowly from
 * one to the next. The pictures ship inside the app (assets/frames, 3840×2160), so it works offline.
 * Left/Right change picture, Up/Down change how often it changes (or pin it), Back closes.
 */
final class GalleryView extends FrameLayout {
    static final String PREF_INDEX = "galleryIndex";
    /** Minutes between pictures; 0 keeps the chosen picture on screen. */
    static final String PREF_INTERVAL = "galleryInterval";
    static final int[] INTERVALS = {1, 5, 15, 30, 60, 0};
    static final int DEFAULT_INTERVAL = 1;
    private static final String FOLDER = "frames";
    private static final long FADE_MS = 2_000L;

    private final SharedPreferences preferences;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final ImageView[] slides = new ImageView[2];
    private final Bitmap[] bitmaps = new Bitmap[2];
    private final TextView label;
    private final List<String> pictures = new ArrayList<>();
    private int front, index, generation;
    private boolean running;
    private final Runnable advance = () -> show(index + 1);

    GalleryView(Context context, SharedPreferences preferences) {
        super(context);
        this.preferences = preferences;
        setBackgroundColor(0xFF000000);
        setKeepScreenOn(true);
        setFocusable(true);
        for (int i = 0; i < 2; i++) {
            slides[i] = new ImageView(context);
            slides[i].setScaleType(ImageView.ScaleType.CENTER_CROP);
            slides[i].setAlpha(0f);
            addView(slides[i], new LayoutParams(-1, -1));
        }
        label = new TextView(context);
        label.setTextColor(0xE6F1ECE2);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        label.setPadding(dp(14), dp(6), dp(14), dp(6));
        android.graphics.drawable.GradientDrawable pill = new android.graphics.drawable.GradientDrawable();
        pill.setColor(0x991B2024);
        pill.setCornerRadius(dp(14));
        label.setBackground(pill);
        label.setAlpha(0f);
        LayoutParams labelParams = new LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        labelParams.setMargins(0, 0, 0, dp(28));
        addView(label, labelParams);
        try {
            String[] names = context.getAssets().list(FOLDER);
            if (names != null) {
                Arrays.sort(names);
                for (String name : names) if (name.endsWith(".jpg")) pictures.add(FOLDER + "/" + name);
            }
        } catch (Exception ignored) { }
        index = Math.max(0, preferences.getInt(PREF_INDEX, 0));
    }

    View entryFocus() { return this; }

    @Override protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (changedView != this) return;
        if (visibility == VISIBLE) start(); else stop();
    }

    void start() {
        if (running) return;
        running = true;
        show(index);
    }

    void stop() {
        running = false;
        generation++;
        handler.removeCallbacksAndMessages(null);
    }

    boolean handleKey(KeyEvent event) {
        int code = event.getKeyCode();
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT:   // Right to left: Left is "next".
                if (down && event.getRepeatCount() == 0) { show(index + 1); flashLabel(); }
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (down && event.getRepeatCount() == 0) { show(index - 1); flashLabel(); }
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (down && event.getRepeatCount() == 0) {
                    stepInterval(code == KeyEvent.KEYCODE_DPAD_UP ? 1 : -1);
                    flashLabel();
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                if (!down) flashLabel();
                return true;
            default:
                return false;
        }
    }

    private void show(int next) {
        handler.removeCallbacks(advance);
        if (pictures.isEmpty() || !running) return;
        index = Math.floorMod(next, pictures.size());
        preferences.edit().putInt(PREF_INDEX, index).apply();
        final String asset = pictures.get(index);
        final int wanted = ++generation;
        // A 4K picture is decoded at the screen's own size: full detail without wasting memory.
        final int screenWidth = Math.max(getWidth(), getResources().getDisplayMetrics().widthPixels);
        worker.execute(() -> {
            Bitmap bitmap = decode(asset, screenWidth);
            handler.post(() -> {
                if (bitmap == null) return;
                if (!running || wanted != generation) { bitmap.recycle(); return; }
                int incoming = 1 - front, outgoing = front;
                slides[incoming].animate().cancel();
                slides[incoming].setImageBitmap(bitmap);
                if (bitmaps[incoming] != null) bitmaps[incoming].recycle();
                bitmaps[incoming] = bitmap;
                slides[incoming].animate().alpha(1f).setDuration(FADE_MS).start();
                slides[outgoing].animate().alpha(0f).setDuration(FADE_MS).start();
                front = incoming;
                schedule();
            });
        });
    }

    private Bitmap decode(String asset, int screenWidth) {
        try (InputStream bounds = getContext().getAssets().open(asset)) {
            BitmapFactory.Options probe = new BitmapFactory.Options();
            probe.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(bounds, null, probe);
            int sample = 1;
            while (probe.outWidth / (sample * 2) >= screenWidth) sample *= 2;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            try (InputStream input = getContext().getAssets().open(asset)) {
                return BitmapFactory.decodeStream(input, null, options);
            }
        } catch (Exception | OutOfMemoryError unreadable) {
            return null;
        }
    }

    static int interval(SharedPreferences preferences) {
        int minutes = preferences.getInt(PREF_INTERVAL, DEFAULT_INTERVAL);
        for (int value : INTERVALS) if (value == minutes) return minutes;
        return DEFAULT_INTERVAL;
    }

    static String intervalName(int minutes) {
        switch (minutes) {
            case 0: return "مثبّتة";
            case 1: return "كل دقيقة";
            case 60: return "كل ساعة";
            case 5: return "كل ٥ دقائق";
            default: return "كل " + ArabicFormat.digits(minutes) + " دقيقة";
        }
    }

    /** Moves through the change intervals; Up goes towards "pinned". */
    static int nextInterval(SharedPreferences preferences, int delta) {
        int at = 0;
        for (int i = 0; i < INTERVALS.length; i++) if (INTERVALS[i] == interval(preferences)) at = i;
        int minutes = INTERVALS[Math.floorMod(at + delta, INTERVALS.length)];
        preferences.edit().putInt(PREF_INTERVAL, minutes).apply();
        return minutes;
    }

    private void stepInterval(int delta) {
        nextInterval(preferences, delta);
        schedule();
    }

    /** Re-reads the interval, for example after it was changed from the quick settings. */
    void intervalChanged() { if (running) schedule(); }

    private void schedule() {
        handler.removeCallbacks(advance);
        int minutes = interval(preferences);
        if (running && minutes > 0) handler.postDelayed(advance, minutes * 60_000L);
    }

    private void flashLabel() {
        if (pictures.isEmpty()) return;
        int minutes = interval(preferences);
        label.setText(ArabicFormat.digits(String.valueOf(index + 1)) + " / "
                + ArabicFormat.digits(String.valueOf(pictures.size()))
                + "   ·   " + (minutes == 0 ? "الصورة مثبّتة" : "التبديل " + intervalName(minutes))
                + "   ·   يمين/يسار: الصورة   فوق/تحت: المدة");
        label.animate().cancel();
        label.setAlpha(1f);
        label.animate().alpha(0f).setStartDelay(4000).setDuration(800).start();
    }

    private int dp(float value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics()));
    }
}
