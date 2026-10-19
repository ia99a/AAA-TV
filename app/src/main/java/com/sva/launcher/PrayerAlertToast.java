package com.sva.launcher;

import android.app.Activity;
import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.lang.ref.WeakReference;
import java.time.LocalDateTime;

/**
 * A small card in the top-right corner with a soft chime. Inside Sva it is drawn on the launcher;
 * over other apps it uses the "display over other apps" permission when the person has allowed it.
 */
final class PrayerAlertToast {
    private static final long VISIBLE_MS = 9_000L;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    /** The launcher while it is on screen, so the card can be drawn inside it without any permission. */
    static WeakReference<Activity> foreground = new WeakReference<>(null);

    private PrayerAlertToast() { }

    static void show(Context context, int prayer, boolean iqama, Runnable done) {
        MAIN.post(() -> {
            Chime.play(iqama);
            View card = card(context, prayer, iqama);
            Activity activity = foreground.get();
            Runnable remove;
            if (activity != null && !activity.isFinishing()) {
                ViewGroup decor = (ViewGroup) activity.getWindow().getDecorView();
                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.RIGHT);
                params.setMargins(0, dp(context, 26), dp(context, 30), 0);
                decor.addView(card, params);
                remove = () -> decor.removeView(card);
            } else if (Settings.canDrawOverlays(context)) {
                WindowManager windows = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
                WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                        WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                        PixelFormat.TRANSLUCENT);
                params.gravity = Gravity.TOP | Gravity.RIGHT;
                params.x = dp(context, 30);
                params.y = dp(context, 26);
                try {
                    windows.addView(card, params);
                    remove = () -> { try { windows.removeView(card); } catch (RuntimeException ignored) { } };
                } catch (RuntimeException notAllowed) {
                    remove = null;
                }
            } else {
                remove = null; // No permission over other apps: the chime alone is the alert.
            }
            if (remove == null) { MAIN.postDelayed(done, 3_000L); return; }
            card.setAlpha(0f);
            card.setTranslationY(-dp(context, 8));
            card.animate().alpha(1f).translationY(0).setDuration(450).start();
            final Runnable removeCard = remove;
            MAIN.postDelayed(() -> card.animate().alpha(0f).setDuration(600)
                    .withEndAction(() -> { removeCard.run(); done.run(); }).start(), VISIBLE_MS);
        });
    }

    private static View card(Context context, int prayer, boolean iqama) {
        LinearLayout card = new LinearLayout(context);
        card.setGravity(Gravity.CENTER_VERTICAL);
        int pad = dp(context, 14);
        card.setPadding(pad + dp(context, 4), dp(context, 11), pad, dp(context, 11));
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xF81B2024);
        background.setCornerRadius(dp(context, 16));
        background.setStroke(dp(context, 1), 0x33E3B469);
        card.setBackground(background);
        card.setElevation(dp(context, 8));

        LinearLayout copy = new LinearLayout(context);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setGravity(Gravity.RIGHT);
        TextView title = new TextView(context);
        title.setText(PrayerAlerts.title(prayer, iqama));
        title.setTextColor(0xFFF1ECE2);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        copy.addView(title);
        TextView subtitle = new TextView(context);
        String city = PrayerTimes.city(PrayerAlerts.preferences(context).getString(PrayerAlerts.PREF_CITY, "riyadh")).name;
        subtitle.setText(ArabicFormat.clockWithPeriod(LocalDateTime.now()) + "  ·  " + city);
        subtitle.setTextColor(0x99F1ECE2);
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        copy.addView(subtitle);
        card.addView(copy, new LinearLayout.LayoutParams(-2, -2));

        // A small gold lamp to the right of the text.
        View lamp = new View(context);
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(iqama ? 0xFFF1ECE2 : 0xFFE3B469);
        dot.setStroke(dp(context, 4), iqama ? 0x33F1ECE2 : 0x40E3B469);
        lamp.setBackground(dot);
        LinearLayout.LayoutParams lampParams = new LinearLayout.LayoutParams(dp(context, 16), dp(context, 16));
        lampParams.leftMargin = dp(context, 12);
        card.addView(lamp, lampParams);
        card.setContentDescription(title.getText());
        return card;
    }

    private static int dp(Context context, float value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, context.getResources().getDisplayMetrics()));
    }

    /** A soft two-note bell for the adhan and a single quieter note for the iqama, synthesised on the device. */
    static final class Chime {
        private static final int RATE = 44100;

        static void play(boolean iqama) {
            short[] samples = iqama ? render(new double[]{880}, new double[]{0}, 0.13, 1.8)
                    : render(new double[]{659.25, 880}, new double[]{0, 0.42}, 0.16, 2.4);
            try {
                AudioTrack track = new AudioTrack.Builder()
                        .setAudioAttributes(new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                        .setAudioFormat(new AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(RATE)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                        .setTransferMode(AudioTrack.MODE_STATIC)
                        .setBufferSizeInBytes(samples.length * 2)
                        .build();
                track.write(samples, 0, samples.length);
                track.play();
                MAIN.postDelayed(track::release, (long) (samples.length * 1000L / RATE) + 300L);
            } catch (RuntimeException ignored) {
                // No audio output available; the card still appears.
            }
        }

        /** Sine bells with a soft attack, a gentle overtone and a long exponential fade. */
        static short[] render(double[] notes, double[] starts, double volume, double seconds) {
            int length = (int) (RATE * seconds);
            double[] mix = new double[length];
            for (int n = 0; n < notes.length; n++) {
                int offset = (int) (starts[n] * RATE);
                for (int i = 0; offset + i < length; i++) {
                    double t = i / (double) RATE;
                    double attack = Math.min(1.0, t / 0.012);
                    double envelope = attack * Math.exp(-t / 0.55);
                    double tone = Math.sin(2 * Math.PI * notes[n] * t)
                            + 0.22 * Math.sin(2 * Math.PI * notes[n] * 2.0 * t) * Math.exp(-t / 0.25);
                    mix[offset + i] += envelope * tone;
                }
            }
            short[] out = new short[length];
            for (int i = 0; i < length; i++) {
                double fadeOut = Math.min(1.0, (length - i) / (RATE * 0.05));
                out[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, mix[i] * volume * fadeOut * Short.MAX_VALUE));
            }
            return out;
        }
    }
}
