package com.sva.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;

/**
 * Quiet, formal backgrounds drawn on the device: deep flat colours with a soft light and,
 * for some, a faint Islamic pattern. Used when no photo wallpaper is chosen.
 */
final class CalmWallpapers {
    static final String PREF = "calmWallpaper";
    /** Damask backgrounds: the same soft pattern in twelve dark colours, 3840×2160, in assets/damask. */
    static final String[] DAMASK = {"original", "night", "warmgrey", "coffee", "bronze", "burgundy",
            "plum", "navy", "royal", "teal", "emerald", "olive"};
    private static final int FIRST_DAMASK = 6;
    static final String[] NAMES = {"ليل كحلي", "رملي", "زيتوني", "حجري", "زمردي", "فجر",
            "ملكي · الأصلي", "ملكي · أسود ليلي", "ملكي · رمادي دافئ", "ملكي · بني قهوة", "ملكي · برونزي",
            "ملكي · عنابي", "ملكي · بنفسجي", "ملكي · كحلي", "ملكي · أزرق", "ملكي · فيروزي",
            "ملكي · زمردي", "ملكي · زيتوني"};
    static final int DEFAULT = FIRST_DAMASK;

    private static final int[][] GRADIENTS = {
            {0xFF172033, 0xFF0E1422},
            {0xFF3A332B, 0xFF231F1A},
            {0xFF2E3529, 0xFF1A1E17},
            {0xFF2A2E33, 0xFF17191C},
            {0xFF133029, 0xFF0B1C18},
            {0xFF1D2638, 0xFF3A2F3B},
    };

    private CalmWallpapers() { }

    /** The chosen background at the given size: a damask picture, or one of the drawn ones. */
    static Bitmap render(Context context, int index, int width, int height) {
        index = Math.max(0, Math.min(NAMES.length - 1, index));
        if (index >= FIRST_DAMASK) {
            Bitmap damask = damask(context, DAMASK[index - FIRST_DAMASK], width, height);
            if (damask != null) return damask;
            index = DEFAULT_DRAWN;
        }
        return render(index, width, height);
    }

    private static final int DEFAULT_DRAWN = 0;

    static boolean isDamask(int index) { return index >= FIRST_DAMASK && index < NAMES.length; }

    /** Decodes a 4K damask picture at about the screen's size and crops it to fill width × height. */
    private static Bitmap damask(Context context, String key, int width, int height) {
        String asset = "damask/" + key + ".jpg";
        try (java.io.InputStream probe = context.getAssets().open(asset)) {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(probe, null, bounds);
            int sample = 1;
            while (bounds.outWidth / (sample * 2) >= width && bounds.outHeight / (sample * 2) >= height) sample *= 2;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            Bitmap source;
            try (java.io.InputStream input = context.getAssets().open(asset)) {
                source = BitmapFactory.decodeStream(input, null, options);
            }
            if (source == null) return null;
            Bitmap out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(out);
            float scale = Math.max(width / (float) source.getWidth(), height / (float) source.getHeight());
            float dx = (width - source.getWidth() * scale) / 2f, dy = (height - source.getHeight() * scale) / 2f;
            android.graphics.Matrix matrix = new android.graphics.Matrix();
            matrix.setScale(scale, scale);
            matrix.postTranslate(dx, dy);
            canvas.drawBitmap(source, matrix, new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG));
            source.recycle();
            return out;
        } catch (Exception | OutOfMemoryError unavailable) {
            return null;
        }
    }

    static Bitmap render(int index, int width, int height) {
        index = Math.max(0, Math.min(FIRST_DAMASK - 1, index));
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setShader(new LinearGradient(0, 0, 0, height, GRADIENTS[index][0], GRADIENTS[index][1], Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, width, height, paint);

        // One soft light, placed differently per background.
        float gx = width * (index == 1 ? 0.22f : 0.78f), gy = index == 5 ? height * 1.05f : height * 0.22f;
        float gr = height * (index == 5 ? 0.9f : 0.65f);
        int glow = index == 2 ? 0x14E8E4D0 : index == 4 ? 0x10BFE3D0 : 0x16E3B469;
        paint.setShader(new RadialGradient(gx, gy, gr, glow, 0x00000000, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, width, height, paint);
        paint.setShader(null);

        if (index == 3) drawStars(canvas, width, height);
        if (index == 4) drawArch(canvas, width, height);
        return bitmap;
    }

    /** Faint eight-pointed stars on a grid. */
    private static void drawStars(Canvas canvas, int width, int height) {
        Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(Math.max(1f, width / 1400f));
        line.setColor(0x10FFFFFF);
        float step = width / 12f, r = step * 0.32f;
        Path star = new Path();
        for (float y = step / 2f; y < height + step; y += step) {
            for (float x = step / 2f; x < width + step; x += step) {
                star.reset();
                for (int square = 0; square < 2; square++) {
                    double base = square * Math.PI / 4;
                    for (int i = 0; i < 4; i++) {
                        double a = base + i * Math.PI / 2;
                        float px = x + (float) (Math.cos(a) * r), py = y + (float) (Math.sin(a) * r);
                        if (i == 0) star.moveTo(px, py); else star.lineTo(px, py);
                    }
                    star.close();
                }
                canvas.drawPath(star, line);
            }
        }
    }

    /** One faint pointed arch, like a mihrab, centred on the screen. */
    private static void drawArch(Canvas canvas, int width, int height) {
        Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(Math.max(1.5f, width / 900f));
        line.setColor(0x12FFFFFF);
        float archWidth = Math.min(width * 0.36f, height * 0.8f), left = (width - archWidth) / 2f, right = left + archWidth;
        float top = height * 0.12f, shoulder = top + archWidth * 0.55f, bottom = height + 4;
        Path arch = new Path();
        arch.moveTo(left, bottom);
        arch.lineTo(left, shoulder);
        arch.cubicTo(left, top + archWidth * 0.12f, width / 2f - archWidth * 0.08f, top + archWidth * 0.04f, width / 2f, top);
        arch.cubicTo(width / 2f + archWidth * 0.08f, top + archWidth * 0.04f, right, top + archWidth * 0.12f, right, shoulder);
        arch.lineTo(right, bottom);
        canvas.drawPath(arch, line);
    }
}
