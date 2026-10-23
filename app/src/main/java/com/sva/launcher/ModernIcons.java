package com.sva.launcher;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/** Thin line icons and soft weather icons for «بسيط ٢», drawn on the device. */
final class ModernIcons {
    private ModernIcons() { }

    /** Line icon drawn in a {@code box}×{@code box} grid: yt, quran, haram, gallery, stremio, dome, calstar. */
    static final class Line extends Drawable {
        private final String kind;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int color;

        Line(String kind, int color) {
            this.kind = kind;
            this.color = color;
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
        }

        void setColor(int value) { if (color != value) { color = value; invalidateSelf(); } }

        @Override public void draw(Canvas canvas) {
            Rect b = getBounds();
            if (b.isEmpty()) return;
            float box = kind.equals("dome") || kind.equals("calstar") ? 24f : 40f;
            float size = Math.min(b.width(), b.height());
            int save = canvas.save();
            canvas.translate(b.exactCenterX() - size / 2f, b.exactCenterY() - size / 2f);
            canvas.scale(size / box, size / box);
            paint.setColor(color);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(box == 24f ? 1.6f : 1.6f);
            drawKind(canvas, kind, paint);
            canvas.restoreToCount(save);
        }

        @Override public void setAlpha(int alpha) { }
        @Override public void setColorFilter(ColorFilter filter) { }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    static void drawKind(Canvas c, String kind, Paint p) {
        Path path = new Path();
        switch (kind) {
            case "yt":
                c.drawRoundRect(new RectF(6, 10, 34, 30), 6, 6, p);
                tri(c, p, 17, 15.5f, 24, 20, 17, 24.5f);
                break;
            case "quran":
                c.drawRoundRect(new RectF(9, 7, 31, 34), 3, 3, p);
                c.drawLine(27, 8, 27, 33, p);
                star(c, p, 18, 19.5f, 4.4f, 1.9f);
                break;
            case "haram":
                path.moveTo(9, 14); path.lineTo(20, 9); path.lineTo(31, 14); path.lineTo(31, 29);
                path.lineTo(20, 34); path.lineTo(9, 29); path.close();
                path.moveTo(9, 14); path.lineTo(20, 19); path.lineTo(31, 14);
                path.moveTo(20, 19); path.lineTo(20, 34);
                path.moveTo(9, 18.5f); path.lineTo(20, 23.5f); path.lineTo(31, 18.5f);
                c.drawPath(path, p);
                break;
            case "gallery":
                c.drawRoundRect(new RectF(6, 9, 34, 31), 3, 3, p);
                c.drawRoundRect(new RectF(10, 13, 30, 27), 1.5f, 1.5f, p);
                path.moveTo(11, 26); path.lineTo(17, 20); path.lineTo(21, 24); path.lineTo(24, 21); path.lineTo(29, 26);
                c.drawPath(path, p);
                c.drawCircle(24.5f, 17, 1.6f, p);
                break;
            case "stremio":
                c.drawCircle(20, 20, 13, p);
                tri(c, p, 17, 14.5f, 25, 20, 17, 25.5f);
                break;
            case "dome":
                // A single dome with a crescent, a low base and an arched door.
                c.drawLine(3.5f, 21, 20.5f, 21, p);
                path.moveTo(5.5f, 21); path.lineTo(5.5f, 15); path.lineTo(18.5f, 15); path.lineTo(18.5f, 21);
                path.moveTo(5.5f, 15); path.arcTo(new RectF(5.5f, 8.5f, 18.5f, 21.5f), 180, 180, false);
                path.moveTo(12, 8.5f); path.lineTo(12, 6.6f);
                path.moveTo(10.5f, 21); path.lineTo(10.5f, 18.2f);
                path.arcTo(new RectF(10.5f, 16.7f, 13.5f, 19.7f), 180, 180, false); path.lineTo(13.5f, 21);
                c.drawPath(path, p);
                Path crescent = new Path(), cut = new Path();
                crescent.addCircle(12.2f, 4.25f, 1.9f, Path.Direction.CW);
                cut.addCircle(13.1f, 3.75f, 1.6f, Path.Direction.CW);
                crescent.op(cut, Path.Op.DIFFERENCE);
                Paint.Style style = p.getStyle();
                p.setStyle(Paint.Style.FILL);
                c.drawPath(crescent, p);
                p.setStyle(style);
                break;
            case "calstar":
                c.drawRoundRect(new RectF(3.5f, 5, 20.5f, 20.5f), 3, 3, p);
                c.drawLine(8, 3, 8, 7, p);
                c.drawLine(16, 3, 16, 7, p);
                c.drawLine(3.5f, 10, 20.5f, 10, p);
                star(c, p, 12, 15.6f, 3f, 1.3f);
                break;
            default:
                break;
        }
    }

    private static void tri(Canvas c, Paint p, float x1, float y1, float x2, float y2, float x3, float y3) {
        Path t = new Path();
        t.moveTo(x1, y1); t.lineTo(x2, y2); t.lineTo(x3, y3); t.close();
        c.drawPath(t, p);
    }

    private static void star(Canvas c, Paint p, float cx, float cy, float outer, float inner) {
        Path s = new Path();
        for (int i = 0; i < 10; i++) {
            double a = -Math.PI / 2 + i * Math.PI / 5;
            float r = i % 2 == 0 ? outer : inner;
            float x = cx + (float) (Math.cos(a) * r), y = cy + (float) (Math.sin(a) * r);
            if (i == 0) s.moveTo(x, y); else s.lineTo(x, y);
        }
        s.close();
        c.drawPath(s, p);
    }

    /** Weather from an Open-Meteo code: sun, part, cloud, rain, storm, fog, or moon at night. */
    static String weatherKind(int code, boolean day) {
        if (code == 0) return day ? "sun" : "moon";
        if (code <= 2) return day ? "part" : "moon";
        if (code == 45 || code == 48) return "fog";
        if (code >= 95) return "storm";
        if ((code >= 51 && code <= 67) || (code >= 80 && code <= 82)) return "rain";
        return "cloud";
    }

    /** Soft coloured weather icon drawn in a 48×40 grid. */
    static final class Weather extends Drawable {
        private final String kind;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        Weather(String kind) { this.kind = kind; }

        @Override public void draw(Canvas canvas) {
            Rect b = getBounds();
            if (b.isEmpty()) return;
            float scale = Math.min(b.width() / 48f, b.height() / 40f);
            int save = canvas.save();
            canvas.translate(b.exactCenterX() - 24 * scale, b.exactCenterY() - 20 * scale);
            canvas.scale(scale, scale);
            switch (kind) {
                case "sun": sun(canvas, 24, 20, 12); break;
                case "part": sun(canvas, 30, 14, 10); cloud(canvas, 4, 10, 0.95f); break;
                case "rain": case "storm":
                    cloud(canvas, 4, 2, 1f);
                    paint.setShader(null);
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeCap(Paint.Cap.ROUND);
                    paint.setStrokeWidth(2.2f);
                    paint.setColor(kind.equals("storm") ? 0xFFF2C14E : 0xFF7FA7D8);
                    for (int x : new int[]{16, 24, 32}) canvas.drawLine(x, 32, x - 2, 37, paint);
                    paint.setStyle(Paint.Style.FILL);
                    break;
                case "moon": moon(canvas); break;
                default: cloud(canvas, 4, 8, 1.05f); break;
            }
            canvas.restoreToCount(save);
        }

        private void sun(Canvas c, float cx, float cy, float r) {
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new RadialGradient(cx - r * 0.3f, cy - r * 0.35f, r * 1.5f,
                    new int[]{0xFFFFD66B, 0xFFF7A541, 0xFFEE7F32}, new float[]{0f, 0.65f, 1f}, Shader.TileMode.CLAMP));
            c.drawCircle(cx, cy, r, paint);
            paint.setShader(null);
        }

        private void cloud(Canvas c, float x, float y, float s) {
            Path body = new Path();
            body.addCircle(x + 10 * s, y + 18 * s, 7.5f * s, Path.Direction.CW);
            Path p = new Path(); p.addCircle(x + 20 * s, y + 12 * s, 10 * s, Path.Direction.CW); body.op(p, Path.Op.UNION);
            p = new Path(); p.addCircle(x + 30 * s, y + 17 * s, 7 * s, Path.Direction.CW); body.op(p, Path.Op.UNION);
            p = new Path(); p.addRoundRect(new RectF(x + 4 * s, y + 16 * s, x + 37 * s, y + 25.5f * s), 5 * s, 5 * s, Path.Direction.CW);
            body.op(p, Path.Op.UNION);
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new LinearGradient(0, y + 2 * s, 0, y + 26 * s, 0xFFF4F6F9, 0xFFAEB6C2, Shader.TileMode.CLAMP));
            c.drawPath(body, paint);
            paint.setShader(null);
        }

        private void moon(Canvas c) {
            Path moon = new Path(), cut = new Path();
            moon.addCircle(26, 21, 13, Path.Direction.CW);
            cut.addCircle(33, 15, 11, Path.Direction.CW);
            moon.op(cut, Path.Op.DIFFERENCE);
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new RadialGradient(20, 16, 18, 0xFFFFF4D6, 0xFFE3C27F, Shader.TileMode.CLAMP));
            c.drawPath(moon, paint);
            paint.setShader(null);
            paint.setColor(0xFFFFF4D6);
            Path sparkle = new Path();
            sparkle.moveTo(12, 9); sparkle.lineTo(13, 11.5f); sparkle.lineTo(15.5f, 12.5f); sparkle.lineTo(13, 13.5f);
            sparkle.lineTo(12, 16); sparkle.lineTo(11, 13.5f); sparkle.lineTo(8.5f, 12.5f); sparkle.lineTo(11, 11.5f); sparkle.close();
            c.drawPath(sparkle, paint);
        }

        @Override public void setAlpha(int alpha) { }
        @Override public void setColorFilter(ColorFilter filter) { }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
