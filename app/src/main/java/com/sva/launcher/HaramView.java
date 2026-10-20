package com.sva.launcher;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

/**
 * Full-screen live view of Masjid al-Haram from the Saudi Quran channel's own streams, played
 * directly (no ads), at the best quality the stream offers. If one source fails the next is tried.
 * OK shows a small panel to switch source or open the 4K live search in YouTube.
 */
@UnstableApi
final class HaramView extends FrameLayout {
    /** {label, HLS URL}. The Saudi Broadcasting Authority's Quran channel, live from Makkah. */
    static final String[][] SOURCES = {
            {"قناة القرآن الكريم", "https://cdn-globecast.akamaized.net/live/eds/saudi_quran/hls_roku/index.m3u8"},
            {"قناة القرآن الكريم (احتياطي)", "http://m.live.net.sa:1935/live/quran/playlist.m3u8"},
            {"قناة القرآن الكريم (احتياطي ٢)", "https://win.holol.com/live/quran/playlist.m3u8"},
    };
    private static final int INK = 0xFFF1ECE2, GOLD = 0xFFE3B469;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final PlayerView playerView;
    private final TextView badge, status;
    private final LinearLayout panel;
    private final TextView nextSource, youtube;
    private ExoPlayer player;
    private int source;
    private boolean panelOnNext = true;

    HaramView(Context context) {
        super(context);
        setBackgroundColor(Color.BLACK);
        playerView = new PlayerView(context);
        playerView.setUseController(false);
        playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
        playerView.setShutterBackgroundColor(Color.BLACK);
        playerView.setKeepScreenOn(true);
        addView(playerView, new LayoutParams(-1, -1));

        badge = new TextView(context);
        badge.setText("الحرم المكي  ·  مباشر");
        badge.setTextColor(INK);
        badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        badge.setPadding(dp(14), dp(7), dp(14), dp(7));
        badge.setBackground(rounded(0x991B2024, dp(14)));
        LayoutParams badgeParams = new LayoutParams(-2, -2, Gravity.TOP | Gravity.RIGHT);
        badgeParams.setMargins(0, dp(24), dp(28), 0);
        addView(badge, badgeParams);

        status = new TextView(context);
        status.setTextColor(0xCCF1ECE2);
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        status.setGravity(Gravity.CENTER);
        addView(status, new LayoutParams(-2, -2, Gravity.CENTER));

        panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.HORIZONTAL);
        panel.setGravity(Gravity.CENTER);
        panel.setPadding(dp(10), dp(8), dp(10), dp(8));
        panel.setBackground(rounded(0xE61B2024, dp(18)));
        youtube = chip(context, "مشاهدة 4K على YouTube");
        nextSource = chip(context, "المصدر التالي");
        panel.addView(youtube);
        LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(-2, -2);
        gap.leftMargin = dp(8);
        panel.addView(nextSource, gap);
        panel.setVisibility(GONE);
        LayoutParams panelParams = new LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        panelParams.bottomMargin = dp(28);
        addView(panel, panelParams);
        setFocusable(true);
        setFocusableInTouchMode(false);
    }

    View entryFocus() { return this; }

    boolean isPlaying() { return player != null && player.isPlaying(); }

    void pause() { stop(); }

    @Override protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (changedView != this) return;
        if (visibility == VISIBLE) start(); else stop();
    }

    @Override protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }

    void start() {
        if (player != null) return;
        player = new ExoPlayer.Builder(getContext()).build();
        // Always the highest quality the stream offers, up to what the TV can decode.
        player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
                .setForceHighestSupportedBitrate(true).build());
        player.addListener(new Player.Listener() {
            @Override public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_BUFFERING) status.setText("جارٍ الاتصال بالبث…");
                else if (state == Player.STATE_READY) { status.setText(""); hideBadgeSoon(); }
            }
            @Override public void onPlayerError(PlaybackException error) { tryNextSource(); }
        });
        playerView.setPlayer(player);
        play(source);
    }

    void stop() {
        handler.removeCallbacksAndMessages(null);
        if (player == null) return;
        playerView.setPlayer(null);
        player.release();
        player = null;
    }

    private void play(int index) {
        source = index;
        status.setText("جارٍ الاتصال بالبث…");
        badge.setAlpha(1f);
        player.setMediaItem(MediaItem.fromUri(SOURCES[source][1]));
        player.prepare();
        player.setPlayWhenReady(true);
    }

    private int failures;

    private void tryNextSource() {
        failures++;
        if (failures >= SOURCES.length) {
            failures = 0;
            status.setText("تعذّر تشغيل البث الآن. تحقّق من الإنترنت، أو اضغط OK للمشاهدة على YouTube.");
            return;
        }
        if (player != null) play((source + 1) % SOURCES.length);
    }

    private void hideBadgeSoon() {
        failures = 0;
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(() -> badge.animate().alpha(0f).setDuration(800).start(), 5000);
    }

    /** OK opens the small panel; Left/Right choose; OK again acts; Back closes it. */
    boolean handleKey(KeyEvent event) {
        int code = event.getKeyCode();
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        boolean ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER;
        if (panel.getVisibility() == VISIBLE) {
            if (code == KeyEvent.KEYCODE_BACK) { if (!down) panel.setVisibility(GONE); return true; }
            if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (down) { panelOnNext = code == KeyEvent.KEYCODE_DPAD_LEFT; highlight(); }
                return true;
            }
            if (ok) {
                if (!down) {
                    panel.setVisibility(GONE);
                    if (panelOnNext) { failures = 0; if (player != null) play((source + 1) % SOURCES.length); }
                    else openYouTube();
                }
                return true;
            }
            return code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN;
        }
        if (ok) {
            if (!down) { panelOnNext = true; highlight(); panel.setVisibility(VISIBLE); badge.setAlpha(1f); hideBadgeSoon(); }
            return true;
        }
        // Arrow keys do nothing while watching, so the picture is never interrupted by accident.
        return code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN
                || code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT;
    }

    private void highlight() {
        style(nextSource, panelOnNext);
        style(youtube, !panelOnNext);
    }

    private void openYouTube() {
        Context context = getContext();
        Intent search = new Intent(Intent.ACTION_SEARCH).setPackage("com.google.android.youtube.tv")
                .putExtra(android.app.SearchManager.QUERY, "مكة مباشر 4K").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { context.startActivity(search); return; } catch (RuntimeException ignored) { }
        try {
            context.startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://www.youtube.com/results?search_query=%D9%85%D9%83%D8%A9+%D9%85%D8%A8%D8%A7%D8%B4%D8%B1+4K"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (RuntimeException ignored) {
            status.setText("تطبيق YouTube غير مثبت.");
        }
    }

    private TextView chip(Context context, String text) {
        TextView chip = new TextView(context);
        chip.setText(text);
        chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        chip.setPadding(dp(16), dp(9), dp(16), dp(9));
        style(chip, false);
        return chip;
    }

    private void style(TextView chip, boolean selected) {
        GradientDrawable shape = rounded(selected ? 0x26E3B469 : Color.TRANSPARENT, dp(16));
        shape.setStroke(dp(selected ? 2 : 1), selected ? GOLD : 0x33F1ECE2);
        chip.setBackground(shape);
        chip.setTextColor(selected ? GOLD : INK);
    }

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(radius);
        return shape;
    }

    private int dp(float value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics()));
    }
}
