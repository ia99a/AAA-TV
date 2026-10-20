package com.sva.launcher;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * «الخلفيات»: a full-screen, slowly moving picture show that works offline once the pictures are saved.
 * Vintage paintings hang in a gilded frame on a dark wall with a dhikr or dua on a brass plaque;
 * nature photographs and carpets fill the screen with a gentle, continuous drift.
 * Left/Right change picture, Up/Down change collection, Back closes.
 */
final class GalleryView extends FrameLayout {
    static final String PREF_COLLECTION = "galleryCollection";
    private static final long SLIDE_MS = 45_000L;
    /** Short adhkar and duas from the Quran and authentic Sunnah. */
    static final String[] ADHKAR = {
            "سبحان الله وبحمده، سبحان الله العظيم",
            "لا إله إلا الله وحده لا شريك له، له الملك وله الحمد، وهو على كل شيء قدير",
            "اللهم صلِّ وسلِّم على نبينا محمد",
            "أستغفر الله وأتوب إليه",
            "لا حول ولا قوة إلا بالله",
            "حسبي الله لا إله إلا هو، عليه توكلت وهو رب العرش العظيم",
            "ربِّ اغفر لي ولوالديّ",
            "اللهم إني أسألك العفو والعافية في الدنيا والآخرة",
            "يا مقلّب القلوب ثبّت قلبي على دينك",
            "اللهم أعنّي على ذكرك وشكرك وحسن عبادتك",
            "ربنا آتنا في الدنيا حسنة وفي الآخرة حسنة وقنا عذاب النار",
            "رضيت بالله ربًّا، وبالإسلام دينًا، وبمحمد ﷺ نبيًّا",
            "سبحان الله، والحمد لله، ولا إله إلا الله، والله أكبر",
            "اللهم إنك عفوّ تحب العفو فاعفُ عني",
            "اللهم اهدني وسدّدني",
            "ربِّ اشرح لي صدري ويسّر لي أمري",
    };

    private final SharedPreferences preferences;
    private final Handler handler = new Handler(Looper.getMainLooper());
    /** Decodes saved pictures; kept separate from downloads so pictures show while new ones arrive. */
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private static final ExecutorService DOWNLOADS = Executors.newSingleThreadExecutor();
    private static final java.util.Set<String> DOWNLOADING = java.util.Collections.synchronizedSet(new java.util.HashSet<>());
    private final Slide[] slides = new Slide[2];
    private final TextView label, status;
    private int front, collection, index, dhikr;
    private List<File> files = new ArrayList<>();
    private boolean running;
    private final Runnable advance = () -> show(index + 1);

    GalleryView(Context context, SharedPreferences preferences) {
        super(context);
        this.preferences = preferences;
        setBackgroundColor(0xFF101411);
        setKeepScreenOn(true);
        setFocusable(true);
        for (int i = 0; i < 2; i++) {
            slides[i] = new Slide(context);
            slides[i].setAlpha(0f);
            addView(slides[i], new LayoutParams(-1, -1));
        }
        label = new TextView(context);
        label.setTextColor(0xE6F1ECE2);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        label.setPadding(dp(14), dp(6), dp(14), dp(6));
        android.graphics.drawable.GradientDrawable pill = new android.graphics.drawable.GradientDrawable();
        pill.setColor(0x881B2024);
        pill.setCornerRadius(dp(14));
        label.setBackground(pill);
        LayoutParams labelParams = new LayoutParams(-2, -2, Gravity.TOP | Gravity.RIGHT);
        labelParams.setMargins(0, dp(24), dp(28), 0);
        addView(label, labelParams);
        status = new TextView(context);
        status.setTextColor(0xCCF1ECE2);
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        status.setGravity(Gravity.CENTER);
        addView(status, new LayoutParams(-2, -2, Gravity.CENTER));
        String saved = preferences.getString(PREF_COLLECTION, GalleryRepository.VINTAGE);
        for (int i = 0; i < GalleryRepository.KEYS.length; i++) if (GalleryRepository.KEYS[i].equals(saved)) collection = i;
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
        openCollection(collection);
    }

    void stop() {
        running = false;
        handler.removeCallbacksAndMessages(null);
        for (Slide slide : slides) slide.stopDrift();
    }

    boolean handleKey(KeyEvent event) {
        int code = event.getKeyCode();
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT:   // Right to left: Left is "next".
                if (down) show(index + 1);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (down) show(index - 1);
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (down) openCollection(Math.floorMod(collection + (code == KeyEvent.KEYCODE_DPAD_DOWN ? 1 : -1),
                        GalleryRepository.KEYS.length));
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                if (!down) flashLabel();
                return true;
            default:
                return false;
        }
    }

    private void openCollection(int which) {
        collection = which;
        String key = GalleryRepository.KEYS[which];
        preferences.edit().putString(PREF_COLLECTION, key).apply();
        files = GalleryRepository.cached(getContext(), key);
        index = (int) (System.currentTimeMillis() / SLIDE_MS) % Math.max(1, files.size());
        flashLabel();
        if (files.isEmpty()) {
            for (Slide slide : slides) slide.animate().alpha(0f).setDuration(400).start();
            status.setText("جارٍ تنزيل الخلفيات لأول مرة…\nبعدها تعمل بدون إنترنت.");
        } else {
            status.setText("");
            show(index);
        }
        final Context app = getContext().getApplicationContext();
        if (GalleryRepository.needsDownload(app, key) && DOWNLOADING.add(key)) {
            DOWNLOADS.execute(() -> {
                try {
                    GalleryRepository.download(app, key, file -> handler.post(() -> {
                        if (!key.equals(GalleryRepository.KEYS[collection])) return;
                        boolean wasEmpty = files.isEmpty();
                        files = GalleryRepository.cached(app, key);
                        if (wasEmpty && running) { status.setText(""); show(0); }
                    }));
                } finally {
                    DOWNLOADING.remove(key);
                    handler.post(() -> {
                        // Nothing could be saved (no internet): say so instead of waiting forever.
                        if (key.equals(GalleryRepository.KEYS[collection]) && files.isEmpty())
                            status.setText("تعذّر التنزيل. تأكد من اتصال الإنترنت ثم افتح «الخلفيات» مرة أخرى.");
                    });
                }
            });
        }
    }

    private void show(int next) {
        handler.removeCallbacks(advance);
        if (files.isEmpty() || !running) return;
        index = Math.floorMod(next, files.size());
        dhikr = (dhikr + 1) % ADHKAR.length;
        final File file = files.get(index);
        final boolean framed = GalleryRepository.VINTAGE.equals(GalleryRepository.KEYS[collection]);
        final int width = Math.max(1, getWidth()), height = Math.max(1, getHeight());
        worker.execute(() -> {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
            handler.post(() -> {
                if (bitmap == null || !running) return;
                Slide incoming = slides[1 - front], outgoing = slides[front];
                incoming.set(bitmap, framed, ADHKAR[dhikr]);
                incoming.animate().alpha(1f).setDuration(1500).start();
                outgoing.animate().alpha(0f).setDuration(1500).withEndAction(outgoing::clear).start();
                front = 1 - front;
                handler.postDelayed(advance, SLIDE_MS);
            });
        });
    }

    private void flashLabel() {
        label.setText(GalleryRepository.NAMES[collection] + "  ·  فوق/تحت للتبديل");
        label.animate().cancel();
        label.setAlpha(1f);
        label.animate().alpha(0f).setStartDelay(4000).setDuration(800).start();
    }

    private int dp(float value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics()));
    }

    /** One picture: full-screen with a slow drift, or framed on a wall with a plaque. */
    private final class Slide extends View {
        private Bitmap bitmap;
        private boolean framed;
        private String text = "";
        private float drift;
        private ValueAnimator driftAnimator;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final Matrix matrix = new Matrix();
        private final RectF rect = new RectF();
        private StaticLayout layout;
        private float layoutSize;
        private int layoutWidth, wallHeight;
        private LinearGradient wall;
        private RadialGradient glow;

        Slide(Context context) {
            super(context);
            textPaint.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD));
            textPaint.setColor(0xFF2A2116);
        }

        void set(Bitmap value, boolean isFramed, String dhikrText) {
            clear();
            bitmap = value;
            framed = isFramed;
            text = dhikrText;
            layout = null;
            drift = 0f;
            driftAnimator = ValueAnimator.ofFloat(0f, 1f);
            driftAnimator.setDuration(SLIDE_MS + 3000);
            driftAnimator.setInterpolator(new LinearInterpolator());
            driftAnimator.addUpdateListener(a -> { drift = (float) a.getAnimatedValue(); invalidate(); });
            driftAnimator.start();
        }

        void stopDrift() { if (driftAnimator != null) driftAnimator.cancel(); }

        void clear() {
            stopDrift();
            if (bitmap != null) { bitmap.recycle(); bitmap = null; }
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            if (bitmap == null || bitmap.isRecycled()) return;
            float w = getWidth(), h = getHeight();
            if (!framed) {
                // Cover the screen, then drift: zoom 1.00 → 1.08 with a slight sideways pan.
                float scale = Math.max(w / bitmap.getWidth(), h / bitmap.getHeight()) * (1f + 0.08f * drift);
                float dx = (w - bitmap.getWidth() * scale) / 2f + (drift - 0.5f) * w * 0.03f;
                float dy = (h - bitmap.getHeight() * scale) / 2f;
                matrix.setScale(scale, scale);
                matrix.postTranslate(dx, dy);
                canvas.drawBitmap(bitmap, matrix, paint);
                return;
            }
            // Wall: deep green with a soft light from above.
            if (wall == null || wallHeight != (int) h) {
                wallHeight = (int) h;
                wall = new LinearGradient(0, 0, 0, h, 0xFF26352C, 0xFF151D18, Shader.TileMode.CLAMP);
                glow = new RadialGradient(w / 2f, h * 0.05f, h * 0.9f, 0x22F3E3C0, 0x00000000, Shader.TileMode.CLAMP);
            }
            paint.setShader(wall);
            canvas.drawRect(0, 0, w, h, paint);
            paint.setShader(glow);
            canvas.drawRect(0, 0, w, h, paint);
            paint.setShader(null);
            // Painting size: fit within the wall, leaving room for the frame and plaque.
            float frame = h * 0.045f, maxW = w * 0.62f, maxH = h * 0.62f;
            float scale = Math.min(maxW / bitmap.getWidth(), maxH / bitmap.getHeight());
            float pw = bitmap.getWidth() * scale, ph = bitmap.getHeight() * scale;
            float left = (w - pw) / 2f, top = h * 0.40f - ph / 2f;
            // Shadow, then the gilded frame in three bands, then the painting with a slow breath.
            paint.setColor(0x66000000);
            canvas.drawRect(left - frame + h * 0.012f, top - frame + h * 0.02f, left + pw + frame + h * 0.012f, top + ph + frame + h * 0.02f, paint);
            rect.set(left - frame, top - frame, left + pw + frame, top + ph + frame);
            paint.setShader(new LinearGradient(rect.left, rect.top, rect.right, rect.bottom,
                    new int[]{0xFFB98A3E, 0xFFF0D58E, 0xFF9C6F2C, 0xFFE2BE6E, 0xFF8A6024}, null, Shader.TileMode.CLAMP));
            canvas.drawRect(rect, paint);
            paint.setShader(null);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(frame * 0.12f);
            paint.setColor(0x995A3D12);
            rect.inset(frame * 0.28f, frame * 0.28f);
            canvas.drawRect(rect, paint);
            paint.setColor(0xCCFFF1C4);
            rect.inset(frame * 0.30f, frame * 0.30f);
            canvas.drawRect(rect, paint);
            paint.setStyle(Paint.Style.FILL);
            canvas.save();
            canvas.clipRect(left, top, left + pw, top + ph);
            float breathe = 1f + 0.03f * drift;
            matrix.setScale(scale * breathe, scale * breathe);
            matrix.postTranslate(left - pw * (breathe - 1f) / 2f, top - ph * (breathe - 1f) / 2f);
            canvas.drawBitmap(bitmap, matrix, paint);
            canvas.restore();
            // Brass plaque with the dhikr.
            float plaqueW = Math.min(w * 0.5f, Math.max(pw * 0.8f, w * 0.32f)), plaqueH = h * 0.085f;
            float plaqueTop = top + ph + frame + h * 0.05f;
            rect.set((w - plaqueW) / 2f, plaqueTop, (w + plaqueW) / 2f, plaqueTop + plaqueH);
            paint.setShader(new LinearGradient(rect.left, rect.top, rect.right, rect.bottom,
                    new int[]{0xFFC9A55E, 0xFFEFD9A0, 0xFFB58E45}, null, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(rect, plaqueH * 0.12f, plaqueH * 0.12f, paint);
            paint.setShader(null);
            if (layout == null || layoutSize != plaqueH * 0.34f || layoutWidth != (int) (plaqueW * 0.9f)) {
                layoutSize = plaqueH * 0.34f;
                layoutWidth = (int) (plaqueW * 0.9f);
                textPaint.setTextSize(layoutSize);
                layout = StaticLayout.Builder.obtain(text, 0, text.length(), textPaint, layoutWidth)
                        .setAlignment(Layout.Alignment.ALIGN_CENTER).setMaxLines(2).build();
            }
            canvas.save();
            canvas.translate((w - layoutWidth) / 2f, rect.centerY() - layout.getHeight() / 2f);
            layout.draw(canvas);
            canvas.restore();
        }
    }
}
