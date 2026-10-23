package com.sva.launcher;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;

/**
 * A quiet Quran listening screen: one surah at a time in the middle of the screen, a choice of
 * reciter, repeat, and play/pause. The background drifts slowly between flat, calm colours.
 */
final class QuranView extends FrameLayout {
    private static final String PREF_SURAH = "quranSurah", PREF_RECITER = "quranReciter", PREF_REPEAT = "quranRepeat";
    /** Flat, low-saturation backgrounds: sage, dusk blue, sand, slate teal, plum, olive. */
    private static final int[] BACKGROUNDS = {0xFF2F3E36, 0xFF2B3646, 0xFF443A31, 0xFF26393D, 0xFF3A3140, 0xFF37392C};
    private static final int INK = 0xFFF1ECE2, SOFT = 0xCCF1ECE2, FAINT = 0x80F1ECE2, LINE = 0x26F1ECE2, GOLD = 0xFFE3B469;

    private final SharedPreferences preferences;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ArchBackground backdrop;
    private final TextView surahNumber, surahName, details, status, elapsed, remaining;
    private final FrameLayout progressTrack;
    private final View progressFill;
    private final PlayButton playButton;
    private final Button surahButton, reciterButton, repeatButton;
    private final FrameLayout picker;
    private final LinearLayout column;
    private ScrollView pickerScroll;
    private final LinearLayout pickerList;
    private final TextView pickerTitle;
    private ExoPlayer player;
    /** «بسيط ٢»: the royal pattern behind a glass card, with an arch emblem on top. */
    private boolean glass;
    private android.widget.ImageView glassBack;
    private View glassVeil, emblem;
    private boolean glassLoading;
    private int surah, reciter, colorIndex;
    private boolean repeat;

    private final Runnable progressTick = new Runnable() {
        @Override public void run() {
            updateProgress();
            handler.postDelayed(this, 500);
        }
    };
    private final Runnable colorTick = new Runnable() {
        @Override public void run() {
            int from = BACKGROUNDS[colorIndex];
            colorIndex = (colorIndex + 1) % BACKGROUNDS.length;
            ValueAnimator fade = ValueAnimator.ofObject(new ArgbEvaluator(), from, BACKGROUNDS[colorIndex]);
            fade.setDuration(6000);
            fade.addUpdateListener(a -> backdrop.setColor((int) a.getAnimatedValue()));
            fade.start();
            handler.postDelayed(this, 45_000);
        }
    };

    QuranView(Context context, SharedPreferences preferences) {
        super(context);
        this.preferences = preferences;
        surah = Math.max(1, Math.min(114, preferences.getInt(PREF_SURAH, 1)));
        reciter = Math.max(0, Math.min(QuranData.RECITERS.length - 1, preferences.getInt(PREF_RECITER, 0)));
        repeat = preferences.getBoolean(PREF_REPEAT, false);

        backdrop = new ArchBackground(context);
        backdrop.setColor(BACKGROUNDS[0]);
        addView(backdrop, new LayoutParams(-1, -1));

        column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER_HORIZONTAL);
        addView(column, new LayoutParams(dp(520), -2, Gravity.CENTER));

        surahNumber = text(context, 13, GOLD, null);
        column.addView(surahNumber);
        surahName = text(context, 54, INK, ModernTheme.typeface(context, "amiri", 400));
        surahName.setIncludeFontPadding(false);
        column.addView(surahName, spaced(4));
        details = text(context, 15, SOFT, null);
        column.addView(details, spaced(8));

        // Progress: total length on the left, elapsed on the right, filling right to left.
        LinearLayout progressRow = new LinearLayout(context);
        progressRow.setGravity(Gravity.CENTER_VERTICAL);
        remaining = text(context, 11, FAINT, null);
        elapsed = text(context, 11, FAINT, null);
        progressTrack = new FrameLayout(context);
        progressTrack.setBackground(rounded(LINE, dp(2)));
        progressFill = new View(context);
        progressFill.setBackground(rounded(GOLD, dp(2)));
        progressTrack.addView(progressFill, new LayoutParams(0, -1, Gravity.RIGHT));
        progressRow.addView(remaining, new LinearLayout.LayoutParams(dp(48), -2));
        LinearLayout.LayoutParams trackParams = new LinearLayout.LayoutParams(0, dp(3), 1);
        trackParams.setMargins(dp(10), 0, dp(10), 0);
        progressRow.addView(progressTrack, trackParams);
        progressRow.addView(elapsed, new LinearLayout.LayoutParams(dp(48), -2));
        remaining.setGravity(Gravity.LEFT);
        elapsed.setGravity(Gravity.RIGHT);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(dp(380), -2);
        progressParams.topMargin = dp(22);
        column.addView(progressRow, progressParams);

        // Controls, right to left: previous surah, play/pause, next surah.
        LinearLayout controls = new LinearLayout(context);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        Button next = roundButton(context, "‹", "السورة التالية");
        next.setOnClickListener(v -> step(1));
        playButton = new PlayButton(context);
        playButton.setOnClickListener(v -> togglePlay());
        Button previous = roundButton(context, "›", "السورة السابقة");
        previous.setOnClickListener(v -> step(-1));
        controls.addView(next, new LinearLayout.LayoutParams(dp(46), dp(46)));
        LinearLayout.LayoutParams playParams = new LinearLayout.LayoutParams(dp(78), dp(78));
        playParams.setMargins(dp(26), 0, dp(26), 0);
        controls.addView(playButton, playParams);
        controls.addView(previous, new LinearLayout.LayoutParams(dp(46), dp(46)));
        column.addView(controls, spaced(18));

        status = text(context, 12, FAINT, null);
        column.addView(status, spaced(8));

        // Choices, right to left: surah, reciter, repeat.
        LinearLayout choices = new LinearLayout(context);
        choices.setGravity(Gravity.CENTER);
        repeatButton = chip(context);
        repeatButton.setOnClickListener(v -> setRepeat(!repeat));
        reciterButton = chip(context);
        reciterButton.setOnClickListener(v -> openReciterPicker());
        surahButton = chip(context);
        surahButton.setOnClickListener(v -> openSurahPicker());
        choices.addView(repeatButton, chipParams());
        choices.addView(reciterButton, chipParams());
        choices.addView(surahButton, chipParams());
        column.addView(choices, spaced(14));

        // Picker: a calm panel listing surahs or reciters, closed with Back.
        picker = new FrameLayout(context);
        picker.setBackgroundColor(0x99000000);
        picker.setVisibility(GONE);
        picker.setClickable(true);
        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(rounded(0xF21B2024, dp(18)));
        panel.setPadding(dp(18), dp(14), dp(18), dp(14));
        pickerTitle = text(context, 15, GOLD, null);
        pickerTitle.setGravity(Gravity.RIGHT);
        panel.addView(pickerTitle, new LinearLayout.LayoutParams(-1, -2));
        ScrollView scroll = new ScrollView(context);
        pickerScroll = scroll;
        scroll.setVerticalScrollBarEnabled(false);
        pickerList = new LinearLayout(context);
        pickerList.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(pickerList, new ScrollView.LayoutParams(-1, -2));
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(-1, 0, 1);
        scrollParams.topMargin = dp(8);
        panel.addView(scroll, scrollParams);
        LayoutParams panelParams = new LayoutParams(dp(360), -1, Gravity.CENTER);
        panelParams.setMargins(0, dp(24), 0, dp(24));
        picker.addView(panel, panelParams);
        addView(picker, new LayoutParams(-1, -1));

        glassBack = new android.widget.ImageView(context);
        glassBack.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        glassBack.setVisibility(GONE);
        addView(glassBack, 1, new LayoutParams(-1, -1));
        glassVeil = new View(context);
        glassVeil.setBackgroundColor(0x59000000);
        glassVeil.setVisibility(GONE);
        addView(glassVeil, 2, new LayoutParams(-1, -1));
        emblem = new Emblem(context);
        emblem.setVisibility(GONE);
        LinearLayout.LayoutParams emblemParams = new LinearLayout.LayoutParams(dp(64), dp(64));
        emblemParams.bottomMargin = dp(10);
        column.addView(emblem, 0, emblemParams);

        refreshLabels();
        setRepeat(repeat);
    }

    /** Switches between the calm colour fields («بسيط ١») and the glass card over the pattern («بسيط ٢»). */
    void setGlass(boolean value) {
        if (glass == value) return;
        glass = value;
        backdrop.setVisibility(glass ? GONE : VISIBLE);
        glassBack.setVisibility(glass ? VISIBLE : GONE);
        glassVeil.setVisibility(glass ? VISIBLE : GONE);
        emblem.setVisibility(glass ? VISIBLE : GONE);
        surahName.setTextColor(glass ? GOLD : INK);
        if (glass) {
            GradientDrawable card = rounded(0xC0141820, dp(24));
            card.setStroke(Math.max(1, dp(1)), 0x22FFFFFF);
            column.setBackground(card);
            column.setPadding(dp(36), dp(22), dp(36), dp(24));
            loadPattern();
        } else {
            column.setBackground(null);
            column.setPadding(0, 0, 0, 0);
        }
        LayoutParams params = (LayoutParams) column.getLayoutParams();
        params.width = dp(glass ? 560 : 520);
        column.setLayoutParams(params);
    }

    private void loadPattern() {
        if (glassBack.getDrawable() != null || glassLoading) return;
        glassLoading = true;
        final int width = Math.max(1, getResources().getDisplayMetrics().widthPixels / 2);
        final int height = Math.max(1, getResources().getDisplayMetrics().heightPixels / 2);
        int chosen = preferences.getInt(CalmWallpapers.PREF, CalmWallpapers.DEFAULT);
        final int index = CalmWallpapers.isDamask(chosen) ? chosen : CalmWallpapers.DEFAULT;
        new Thread(() -> {
            android.graphics.Bitmap bitmap = null;
            try { bitmap = CalmWallpapers.render(getContext(), index, width, height); }
            catch (RuntimeException | OutOfMemoryError ignored) { }
            final android.graphics.Bitmap loaded = bitmap;
            handler.post(() -> {
                glassLoading = false;
                if (loaded != null) glassBack.setImageBitmap(loaded);
            });
        }, "quran-pattern").start();
    }

    /** A thin gold ring holding a pointed arch, at the top of the glass card. */
    private final class Emblem extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path arch = new Path();

        Emblem(Context context) {
            super(context);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setColor(GOLD);
        }

        @Override protected void onDraw(Canvas canvas) {
            float w = getWidth(), h = getHeight(), cx = w / 2f, cy = h / 2f, r = Math.min(w, h) / 2f - dp(1.5f);
            paint.setStrokeWidth(dp(1.2f));
            paint.setAlpha(150);
            canvas.drawCircle(cx, cy, r, paint);
            paint.setAlpha(255);
            // The dome over the Quran stand, from the app's own icon artwork, in gold inside the ring.
            float side = r * 1.12f;
            canvas.save();
            canvas.translate(cx - side / 2f, cy - side / 2f + r * 0.02f);
            canvas.scale(side / 40f, side / 40f);
            ModernIcons.drawKind(canvas, "quran", paint);
            canvas.restore();
        }
    }

    /** The control the remote lands on when entering this tab. */
    View entryFocus() { return playButton; }

    /** Closes an open picker; returns true when Back was used for that. */
    boolean handleBack() {
        if (picker.getVisibility() != VISIBLE) return false;
        closePicker(surahButton);
        return true;
    }

    /**
     * Remote keys for the Quran screen. With a list open, Up/Down walk the rows and OK picks one;
     * nothing behind the list can take focus. Returns true when the key was used.
     */
    boolean handleKey(android.view.KeyEvent event) {
        int code = event.getKeyCode();
        boolean down = event.getAction() == android.view.KeyEvent.ACTION_DOWN;
        if (picker.getVisibility() == VISIBLE) {
            if (code == android.view.KeyEvent.KEYCODE_BACK) {
                if (!down) closePicker(surahButton);
                return true;
            }
            int count = pickerList.getChildCount();
            if (count == 0) return false;
            int index = 0;
            View focused = findFocus();
            for (int i = 0; i < count; i++) if (pickerList.getChildAt(i) == focused) index = i;
            int target = -1;
            if (code == android.view.KeyEvent.KEYCODE_DPAD_DOWN) target = Math.min(count - 1, index + 1);
            else if (code == android.view.KeyEvent.KEYCODE_DPAD_UP) target = Math.max(0, index - 1);
            else if (code == android.view.KeyEvent.KEYCODE_PAGE_DOWN || code == android.view.KeyEvent.KEYCODE_CHANNEL_DOWN)
                target = Math.min(count - 1, index + 8);
            else if (code == android.view.KeyEvent.KEYCODE_PAGE_UP || code == android.view.KeyEvent.KEYCODE_CHANNEL_UP)
                target = Math.max(0, index - 8);
            if (target >= 0) {
                if (down) focusRow(target);
                return true;
            }
            if (code == android.view.KeyEvent.KEYCODE_DPAD_LEFT || code == android.view.KeyEvent.KEYCODE_DPAD_RIGHT) return true;
            if (code == android.view.KeyEvent.KEYCODE_DPAD_CENTER || code == android.view.KeyEvent.KEYCODE_ENTER) {
                if (!down && focused != null && focused.getParent() == pickerList) focused.performClick();
                return true;
            }
            return false;
        }
        if (code == android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || code == android.view.KeyEvent.KEYCODE_MEDIA_PLAY
                || code == android.view.KeyEvent.KEYCODE_MEDIA_PAUSE) {
            if (down && event.getRepeatCount() == 0) togglePlay();
            return true;
        }
        if (code == android.view.KeyEvent.KEYCODE_MEDIA_NEXT) { if (down) step(1); return true; }
        if (code == android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS) { if (down) step(-1); return true; }
        return false;
    }

    private void focusRow(int index) {
        View row = pickerList.getChildAt(index);
        row.requestFocus();
        int top = row.getTop(), bottom = row.getBottom(), height = pickerScroll.getHeight();
        int y = pickerScroll.getScrollY();
        if (top < y) pickerScroll.smoothScrollTo(0, Math.max(0, top - dp(8)));
        else if (bottom > y + height) pickerScroll.smoothScrollTo(0, bottom - height + dp(8));
    }

    private void closePicker(View focusAfter) {
        picker.setVisibility(GONE);
        column.setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);
        focusAfter.requestFocus();
    }

    /** Pauses when the launcher goes to the background, so recitation never plays over another app. */
    void pause() {
        if (player != null) player.pause();
    }

    boolean isPlaying() { return player != null && player.isPlaying(); }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        handler.post(progressTick);
        handler.postDelayed(colorTick, 45_000);
    }

    @Override protected void onDetachedFromWindow() {
        handler.removeCallbacksAndMessages(null);
        if (player != null) { player.release(); player = null; }
        super.onDetachedFromWindow();
    }

    private void ensurePlayer() {
        if (player != null) return;
        player = new ExoPlayer.Builder(getContext()).build();
        player.addListener(new Player.Listener() {
            @Override public void onIsPlayingChanged(boolean isPlaying) {
                playButton.setPlaying(isPlaying);
                setKeepScreenOn(isPlaying);
                if (isPlaying) status.setText("");
            }
            @Override public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_BUFFERING) status.setText("جارٍ التحميل…");
                else if (state == Player.STATE_READY) status.setText("");
                else if (state == Player.STATE_ENDED && !repeat) step(1);
            }
            @Override public void onPlayerError(PlaybackException error) {
                status.setText("تعذّر التشغيل. تحقّق من الاتصال بالإنترنت.");
                playButton.setPlaying(false);
            }
        });
        applyRepeat();
    }

    private void load(boolean autoplay) {
        ensurePlayer();
        player.setMediaItem(MediaItem.fromUri(QuranData.url(reciter, surah)));
        player.prepare();
        player.setPlayWhenReady(autoplay);
    }

    private void togglePlay() {
        if (player == null || player.getMediaItemCount() == 0) { load(true); return; }
        if (player.getPlaybackState() == Player.STATE_IDLE) player.prepare();
        if (player.getPlaybackState() == Player.STATE_ENDED) player.seekTo(0);
        if (player.isPlaying()) player.pause(); else player.play();
    }

    private void step(int delta) {
        surah = surah + delta;
        if (surah > 114) surah = 1;
        if (surah < 1) surah = 114;
        selectSurah(surah, player != null && player.getPlayWhenReady());
    }

    private void selectSurah(int value, boolean autoplay) {
        surah = value;
        preferences.edit().putInt(PREF_SURAH, surah).apply();
        refreshLabels();
        if (autoplay || player != null) load(autoplay);
    }

    private void selectReciter(int value) {
        boolean wasPlaying = player != null && player.getPlayWhenReady();
        reciter = value;
        preferences.edit().putInt(PREF_RECITER, reciter).apply();
        refreshLabels();
        if (player != null) load(wasPlaying);
    }

    private void setRepeat(boolean value) {
        repeat = value;
        preferences.edit().putBoolean(PREF_REPEAT, repeat).apply();
        repeatButton.setText(repeat ? "إعادة السورة: تعمل" : "إعادة السورة: متوقفة");
        repeatButton.setSelected(repeat);
        applyRepeat();
    }

    private void applyRepeat() {
        if (player != null) player.setRepeatMode(repeat ? Player.REPEAT_MODE_ONE : Player.REPEAT_MODE_OFF);
    }

    private void refreshLabels() {
        surahNumber.setText("سورة رقم " + ArabicFormat.digits(surah));
        surahName.setText(QuranData.SURAHS[surah - 1]);
        details.setText(QuranData.ayahCount(surah) + "  ·  " + QuranData.RECITERS[reciter][0]);
        surahButton.setText("السورة: " + QuranData.SURAHS[surah - 1]);
        reciterButton.setText("القارئ: " + QuranData.RECITERS[reciter][0]);
    }

    private void updateProgress() {
        long position = player == null ? 0 : Math.max(0, player.getCurrentPosition());
        long duration = player == null ? 0 : player.getDuration();
        boolean known = duration > 0;
        elapsed.setText(known ? time(position) : "");
        remaining.setText(known ? time(duration) : "");
        int width = progressTrack.getWidth();
        int fill = known && width > 0 ? Math.round(width * Math.min(1f, position / (float) duration)) : 0;
        LayoutParams params = (LayoutParams) progressFill.getLayoutParams();
        if (params.width != fill) { params.width = fill; progressFill.setLayoutParams(params); }
    }

    private static String time(long millis) {
        long seconds = Math.max(0, millis / 1000);
        long h = seconds / 3600, m = (seconds % 3600) / 60, s = seconds % 60;
        String text = (h > 0 ? h + ":" + (m < 10 ? "0" : "") : "") + m + ":" + (s < 10 ? "0" : "") + s;
        return ArabicFormat.digits(text);
    }

    private void openSurahPicker() {
        pickerTitle.setText("اختر السورة");
        pickerList.removeAllViews();
        View current = null;
        for (int i = 1; i <= 114; i++) {
            final int number = i;
            Button row = pickerRow(ArabicFormat.digits(i) + ".  " + QuranData.SURAHS[i - 1], QuranData.ayahCount(i), i == surah);
            row.setOnClickListener(v -> { closePicker(surahButton); selectSurah(number, true); });
            pickerList.addView(row, rowParams());
            if (i == surah) current = row;
        }
        showPicker(current);
    }

    private void openReciterPicker() {
        pickerTitle.setText("اختر القارئ");
        pickerList.removeAllViews();
        View current = null;
        for (int i = 0; i < QuranData.RECITERS.length; i++) {
            final int index = i;
            Button row = pickerRow(QuranData.RECITERS[i][0], "", i == reciter);
            row.setOnClickListener(v -> { closePicker(reciterButton); selectReciter(index); });
            pickerList.addView(row, rowParams());
            if (i == reciter) current = row;
        }
        showPicker(current);
    }

    private void showPicker(View current) {
        // Nothing behind the list may take focus while it is open.
        column.setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
        picker.setVisibility(VISIBLE);
        pickerScroll.scrollTo(0, 0);
        final int index = current == null ? 0 : pickerList.indexOfChild(current);
        pickerList.post(() -> {
            View row = pickerList.getChildAt(Math.max(0, index));
            if (row == null) return;
            row.requestFocus();
            pickerScroll.scrollTo(0, Math.max(0, row.getTop() - pickerScroll.getHeight() / 2 + row.getHeight() / 2));
        });
    }

    private Button pickerRow(String title, String note, boolean selected) {
        Button row = new Button(getContext());
        row.setAllCaps(false);
        row.setText(note.isEmpty() ? title : title + "    " + note);
        row.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        row.setTextColor(selected ? GOLD : INK);
        row.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), 0, dp(14), 0);
        row.setMinHeight(0);
        row.setMinimumHeight(0);
        row.setBackground(focusBackground(dp(10)));
        row.setFocusable(true);
        row.setFocusableInTouchMode(false);
        return row;
    }

    private LinearLayout.LayoutParams rowParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(40));
        params.topMargin = dp(2);
        return params;
    }

    private Button chip(Context context) {
        Button chip = new Button(context);
        chip.setAllCaps(false);
        chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        chip.setTextColor(new android.content.res.ColorStateList(
                new int[][]{new int[]{android.R.attr.state_selected}, new int[]{}}, new int[]{GOLD, SOFT}));
        chip.setMinWidth(0);
        chip.setMinimumWidth(0);
        chip.setMinHeight(0);
        chip.setMinimumHeight(0);
        chip.setSingleLine(true);
        chip.setEllipsize(TextUtils.TruncateAt.END);
        chip.setMaxWidth(dp(190));
        chip.setPadding(dp(14), 0, dp(14), 0);
        GradientDrawable idle = rounded(Color.TRANSPARENT, dp(16));
        idle.setStroke(dp(1), LINE);
        GradientDrawable focused = rounded(0x1FFFFFFF, dp(16));
        focused.setStroke(dp(1), 0x66F1ECE2);
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused}, focused);
        states.addState(new int[]{}, idle);
        chip.setBackground(states);
        return chip;
    }

    private LinearLayout.LayoutParams chipParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, dp(34));
        params.setMargins(dp(5), 0, dp(5), 0);
        return params;
    }

    private Button roundButton(Context context, String glyph, String description) {
        Button button = new Button(context);
        button.setText(glyph);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        button.setTextColor(SOFT);
        button.setPadding(0, 0, 0, dp(3));
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setContentDescription(description);
        button.setBackground(focusBackground(dp(23)));
        return button;
    }

    private StateListDrawable focusBackground(int radius) {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused}, rounded(0x24FFFFFF, radius));
        states.addState(new int[]{}, rounded(Color.TRANSPARENT, radius));
        return states;
    }

    private TextView text(Context context, float sp, int color, Typeface typeface) {
        TextView view = new TextView(context);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER);
        if (typeface != null) view.setTypeface(typeface);
        return view;
    }

    private LinearLayout.LayoutParams spaced(int topDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
        params.topMargin = dp(topDp);
        return params;
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

    /**
     * The Quran tile's icon: a mushaf cover in gold outline with an eight-pointed star
     * (two overlapping squares) at its centre, on a soft round ground.
     */
    static final class MushafIcon extends android.graphics.drawable.Drawable {
        private final Paint ground = new Paint(Paint.ANTI_ALIAS_FLAG), stroke = new Paint(Paint.ANTI_ALIAS_FLAG),
                fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path star = new Path();
        private final RectF rect = new RectF();

        MushafIcon() {
            ground.setColor(0x26E3B469);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeCap(Paint.Cap.ROUND);
            stroke.setStrokeJoin(Paint.Join.ROUND);
            stroke.setColor(GOLD);
            fill.setColor(GOLD);
        }

        @Override public void draw(Canvas canvas) {
            android.graphics.Rect b = getBounds();
            float size = Math.min(b.width(), b.height()), cx = b.exactCenterX(), cy = b.exactCenterY();
            canvas.drawCircle(cx, cy, size / 2f, ground);
            stroke.setStrokeWidth(size * 0.045f);
            // Cover with a spine on the right, as an Arabic book opens.
            float w = size * 0.44f, h = size * 0.56f;
            rect.set(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f);
            canvas.drawRoundRect(rect, size * 0.05f, size * 0.05f, stroke);
            canvas.drawLine(rect.right - size * 0.07f, rect.top + size * 0.02f, rect.right - size * 0.07f, rect.bottom - size * 0.02f, stroke);
            // Eight-pointed star.
            float r = size * 0.12f, sx = cx - size * 0.03f;
            star.reset();
            for (int square = 0; square < 2; square++) {
                double base = square * Math.PI / 4;
                for (int i = 0; i < 4; i++) {
                    double a = base + i * Math.PI / 2;
                    float x = sx + (float) (Math.cos(a) * r), y = cy + (float) (Math.sin(a) * r);
                    if (i == 0) star.moveTo(x, y); else star.lineTo(x, y);
                }
                star.close();
            }
            canvas.drawPath(star, fill);
            canvas.drawCircle(sx, cy, r * 0.32f, ground);
        }

        @Override public void setAlpha(int alpha) { }
        @Override public void setColorFilter(android.graphics.ColorFilter filter) { }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }

    /** Flat colour with one faint arch outline, like a mihrab, behind the content. */
    private final class ArchBackground extends View {
        private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path arch = new Path();
        private int color;

        ArchBackground(Context context) {
            super(context);
            line.setStyle(Paint.Style.STROKE);
            line.setStrokeWidth(dp(1.5f));
            line.setColor(0x14FFFFFF);
        }

        void setColor(int value) { color = value; invalidate(); }

        @Override protected void onDraw(Canvas canvas) {
            canvas.drawColor(color);
            float w = getWidth(), h = getHeight();
            float archWidth = Math.min(w * 0.42f, h * 0.9f), left = (w - archWidth) / 2f, right = left + archWidth;
            float bottom = h + dp(4), top = h * 0.08f, shoulder = top + archWidth * 0.55f;
            arch.reset();
            arch.moveTo(left, bottom);
            arch.lineTo(left, shoulder);
            arch.cubicTo(left, top + archWidth * 0.12f, w / 2f - archWidth * 0.08f, top + archWidth * 0.04f, w / 2f, top);
            arch.cubicTo(w / 2f + archWidth * 0.08f, top + archWidth * 0.04f, right, top + archWidth * 0.12f, right, shoulder);
            arch.lineTo(right, bottom);
            canvas.drawPath(arch, line);
        }
    }

    /** A round play/pause button drawn as shapes, so it looks the same on every TV. */
    private final class PlayButton extends View {
        private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG), glyph = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path triangle = new Path();
        private final RectF bar = new RectF();
        private boolean playing;

        PlayButton(Context context) {
            super(context);
            setFocusable(true);
            setClickable(true);
            setContentDescription("تشغيل");
            ring.setStyle(Paint.Style.FILL);
            glyph.setStyle(Paint.Style.FILL);
            glyph.setColor(0xFF1E2224);
            // Focus shows a soft ring drawn inside the view's own bounds, so nothing is ever clipped.
            setOnFocusChangeListener((v, focused) -> invalidate());
        }

        void setPlaying(boolean value) {
            playing = value;
            setContentDescription(playing ? "إيقاف مؤقت" : "تشغيل");
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            float cx = getWidth() / 2f, cy = getHeight() / 2f, r = Math.min(cx, cy) - dp(1);
            if (isFocused()) {
                ring.setColor(0x33F1ECE2);
                canvas.drawCircle(cx, cy, r, ring);
            }
            ring.setColor(INK);
            canvas.drawCircle(cx, cy, r - dp(7), ring);
            float s = (r - dp(7)) * 0.36f;
            if (playing) {
                float gap = s * 0.35f, width = s * 0.42f;
                bar.set(cx - gap - width, cy - s, cx - gap, cy + s);
                canvas.drawRoundRect(bar, dp(2), dp(2), glyph);
                bar.set(cx + gap, cy - s, cx + gap + width, cy + s);
                canvas.drawRoundRect(bar, dp(2), dp(2), glyph);
            } else {
                triangle.reset();
                triangle.moveTo(cx - s * 0.7f, cy - s);
                triangle.lineTo(cx + s, cy);
                triangle.lineTo(cx - s * 0.7f, cy + s);
                triangle.close();
                canvas.drawPath(triangle, glyph);
            }
        }
    }
}
