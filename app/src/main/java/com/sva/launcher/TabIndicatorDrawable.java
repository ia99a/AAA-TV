package com.sva.launcher;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.animation.DecelerateInterpolator;

/**
 * Navigation tab background: no filled pill. The selected tab gets a short, soft underline
 * beneath its label; when the remote focuses a tab the underline stretches to the label's
 * width and a faint glass fill appears behind it.
 */
final class TabIndicatorDrawable extends Drawable {
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final int accent, focusFill;
    private final float density;
    private float selected, focused;
    private ValueAnimator animator;

    TabIndicatorDrawable(int accent, int focusFill, float density) {
        this.accent = accent;
        this.focusFill = focusFill;
        this.density = density;
        line.setStyle(Paint.Style.FILL);
        glow.setStyle(Paint.Style.FILL);
        fill.setStyle(Paint.Style.FILL);
    }

    @Override public boolean isStateful() { return true; }

    @Override protected boolean onStateChange(int[] state) {
        boolean isSelected = false, isFocused = false;
        for (int value : state) {
            if (value == android.R.attr.state_selected) isSelected = true;
            if (value == android.R.attr.state_focused) isFocused = true;
        }
        final float targetSelected = isSelected ? 1f : 0f, targetFocused = isFocused ? 1f : 0f;
        if (targetSelected == selected && targetFocused == focused) return false;
        if (animator != null) animator.cancel();
        final float fromSelected = selected, fromFocused = focused;
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(220);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(a -> {
            float t = (float) a.getAnimatedValue();
            selected = fromSelected + (targetSelected - fromSelected) * t;
            focused = fromFocused + (targetFocused - fromFocused) * t;
            invalidateSelf();
        });
        animator.start();
        return true;
    }

    @Override public void jumpToCurrentState() {
        if (animator != null) animator.end();
    }

    @Override public void draw(Canvas canvas) {
        Rect b = getBounds();
        if (b.isEmpty()) return;
        // Faint glass behind the focused tab, so the remote's position is always clear.
        if (focused > 0.01f) {
            fill.setColor(withAlpha(focusFill, focused));
            rect.set(b.left + dp(2), b.top + dp(3), b.right - dp(2), b.bottom - dp(3));
            canvas.drawRoundRect(rect, rect.height() / 2f, rect.height() / 2f, fill);
        }
        float strength = Math.max(selected, focused);
        if (strength < 0.01f) return;
        float shortWidth = dp(16), longWidth = Math.max(shortWidth, b.width() - dp(28));
        float width = shortWidth + (longWidth - shortWidth) * focused;
        float cx = b.exactCenterX(), y = b.bottom - dp(6.5f), thickness = dp(2.5f);
        // A wider, faint stroke under the line reads as a soft glow on a TV.
        glow.setColor(withAlpha(accent, strength * 0.28f));
        rect.set(cx - width / 2f - dp(2), y - thickness, cx + width / 2f + dp(2), y + thickness * 1.6f);
        canvas.drawRoundRect(rect, rect.height() / 2f, rect.height() / 2f, glow);
        line.setColor(withAlpha(accent, strength));
        rect.set(cx - width / 2f, y - thickness / 2f, cx + width / 2f, y + thickness / 2f);
        canvas.drawRoundRect(rect, thickness / 2f, thickness / 2f, line);
    }

    private static int withAlpha(int color, float factor) {
        int alpha = Math.round(((color >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, factor)));
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    private float dp(float value) { return value * density; }

    @Override public void setAlpha(int alpha) { }
    @Override public void setColorFilter(ColorFilter colorFilter) { }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
