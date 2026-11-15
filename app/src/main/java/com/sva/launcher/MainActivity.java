package com.sva.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.Manifest;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.app.NotificationManager;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.os.Bundle;
import android.os.Build;
import android.os.CancellationSignal;
import android.util.LruCache;
import android.text.InputType;
import android.text.TextUtils;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityManager;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;


import java.text.DateFormat;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.DayOfWeek;
import java.time.format.TextStyle;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.json.JSONObject;

public final class MainActivity extends Activity {

    private static final int TAB_HOME = 0, TAB_TV = 1, TAB_GAMES = 2, TAB_EVENTS = 3, TAB_SAVED = 4, TAB_PRAYER = 5, TAB_QURAN = 6, TAB_HARAM = 7, TAB_GALLERY = 8;
    private static final int LAST_TAB = TAB_GALLERY;
    /** Indices 1, 2 and 4 were live TV, games and saved shortcuts; they stay reserved so saved tab numbers keep meaning. */
    private static final String[] TAB_TITLES = {"آمر", "", "", "تاريخك", "", "الصلاة", "القرآن", "الحرم", "الخلفيات"};
    private static final int CALENDAR_CACHE_VERSION = 3;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final LifecycleWork lifecycleWork = new LifecycleWork(this, handler);
    private final ExecutorService calendarWorker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "public-calendar-refresh");
        thread.setDaemon(true);
        return thread;
    });
    private final ExecutorService ioWorker = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "launcher-io");
        thread.setDaemon(true);
        return thread;
    });
    private SharedPreferences preferences;
    private PrayerView prayerView;
    private QuranView quranView;
    private FrameLayout sceneRoot;
    private HaramView haramView;
    private GalleryView galleryView;
    /** The glass home: cards, apps and tiles. */
    private ModernHomeView modernHome;
    private ImageView dayBackdrop;
    private LinearLayout navbarView;
    /** Adhan-to-iqama countdown at the top left of the Quran and the wallpapers (and Home without the prayer card). */
    private IqamaCountdown.Pill iqamaPill;
    private QuickSettingsView quickSettings;
    private LinearLayout launcherShell;
    private LinearLayout eventsContent;
    private GridLayout calendarMonthGrid;
    private TextView calendarHeaderDate, calendarHeaderEvents, monthTitle;
    private Button previousMonthButton, nextMonthButton;
    private YearMonth displayedMonth = YearMonth.now();
    private LocalDate selectedCalendarDay = LocalDate.now();
    private final View[] tabPages = new View[TAB_TITLES.length];
    private final Button[] tabButtons = new Button[TAB_TITLES.length];
    private final View[] tabFocus = new View[TAB_TITLES.length];
    private final View[] tabEntryFocus = new View[TAB_TITLES.length];
    private final int[] tabScroll = new int[TAB_TITLES.length];
    private final NavigationGlyph[] navigationGlyphs = new NavigationGlyph[TAB_TITLES.length];
    private int activeTab = TAB_HOME;
    private boolean tabsInitialized;
    private boolean switchingTabs;
    private LinearLayout tabRow;
    private View browsingShade;
    private FrameLayout heroFrame;
    /** «الخلفية الثابتة»: a still picture behind «آمر», prayer and «تاريخك», above the usual background. */
    private FrameLayout fixedLayer;
    private ImageView fixedImage;
    private View fixedVeil;
    private String fixedLoaded;
    private LinearLayout calendarHeading;
    private String navigationSignature;
    /** The calendar is redrawn only when its font or text size changes. */
    private String calendarGlass;
    private final GlassBackdrop glassBackdrop = new GlassBackdrop();
    private ImageView wallpaperImage, slideA, slideB;
    private Runnable clockTick;
    private Runnable calendarRefreshTick;
    private Runnable weatherRefreshTick;
    private Runnable slideshowTick;
    private final List<BackgroundPhoto> backgroundPhotos = new ArrayList<>();
    private final LruCache<String, android.graphics.Bitmap> backgroundBitmapCache = new LruCache<String, android.graphics.Bitmap>(20 * 1024) {
        @Override protected int sizeOf(String key, android.graphics.Bitmap bitmap) {
            return Math.max(1, bitmap.getByteCount() / 1024);
        }
    };
    private int backgroundIndex, backgroundImageGeneration;
    private boolean slideAActive = true;
    private boolean screenActive;
    private boolean appsLoading;
    private String calendarRefreshingUrl = "";
    private int calendarRequestGeneration;
    private WeatherRepository.Forecast weatherForecast;
    private int weatherRequestGeneration;
    private boolean weatherRefreshing, weatherRefreshedThisLaunch;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ModernIcons.init(this);
        BackNavigation.install(this, this::handleBack);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        preferences = getSharedPreferences("launcher", MODE_PRIVATE);
        // Photo folders, albums and a picked photo belonged to the retired full settings.
        preferences.edit().remove("wallpaper").remove("wallpaperFolder").remove("wallpaperAlbum").apply();
        if (state != null) {
            activeTab = Math.max(TAB_HOME, Math.min(LAST_TAB, state.getInt("activeTab", TAB_HOME)));
            for (int i = 0; i < tabScroll.length; i++) tabScroll[i] = state.getInt("tabScroll" + i, 0);
            tabScroll[TAB_HOME] = 0;
            try { selectedCalendarDay = LocalDate.parse(state.getString("selectedCalendarDay", LocalDate.now().toString())); }
            catch (Exception ignored) { selectedCalendarDay = LocalDate.now(); }
            displayedMonth = YearMonth.from(selectedCalendarDay);
        }
        PrayerAlerts.schedule(this);
        if (!hasWeatherLocation()) {
            // Riyadh until another city is chosen from the settings.
            PrayerTimes.City riyadh = PrayerTimes.city(preferences.getString(QuickSettingsView.PREF_WEATHER_CITY, "riyadh"));
            preferences.edit().putString("weatherLocationMode", "city").putString("weatherLocationLabel", riyadh.name)
                    .putLong("weatherLatitude", Double.doubleToRawLongBits(riyadh.latitude))
                    .putLong("weatherLongitude", Double.doubleToRawLongBits(riyadh.longitude)).apply();
        }
        weatherForecast = cachedWeather();
        buildHome();
        applyHomeTheme();
        applyMood();
        clockTick = new Runnable() {
            @Override public void run() {
                updateCalendarEventDisplay();
                handler.postDelayed(this, 30000);
            }
        };
        handler.postDelayed(clockTick, 30000);
        updateCalendarEventDisplay();
        maybeRefreshPublicCalendar(false);
        calendarRefreshTick = new Runnable() {
            @Override public void run() {
                maybeRefreshPublicCalendar(false);
                scheduleCalendarRefresh();
            }
        };
        weatherRefreshTick = new Runnable() {
            @Override public void run() {
                maybeRefreshWeather(false);
                scheduleWeatherRefresh();
            }
        };
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        outState.putInt("activeTab", activeTab);
        for (int i = 0; i < tabPages.length; i++)
            outState.putInt("tabScroll" + i, i == TAB_HOME ? 0
                    : tabPages[i] == null ? tabScroll[i] : tabPages[i].getScrollY());
        outState.putString("selectedCalendarDay", selectedCalendarDay.toString());
        super.onSaveInstanceState(outState);
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        selectTab(TAB_HOME, true);
    }


    // Physical TV remotes and Android <33 still dispatch the legacy callback.
    @android.annotation.SuppressLint("GestureBackNavigation")
    @Override public void onBackPressed() { handleBack(); }

    @android.annotation.SuppressLint("GestureBackNavigation") // Default task behavior after our custom Back handling.
    private void handleBack() {
        if (activeTab == TAB_QURAN && quranView != null && quranView.handleBack()) return;
        if (activeTab == TAB_HOME && preferences.getBoolean(LauncherOverrideService.KIOSK, true)) {
            countKioskBack();
            return;
        }
        if (activeTab != TAB_HOME) { selectTab(TAB_HOME, true); return; }
        super.onBackPressed();
    }

    private int kioskBackPresses;
    private long kioskBackAt;

    /** "Always on screen": Back on «آمر» does nothing, unless pressed three times in a row to leave. */
    private void countKioskBack() {
        long now = android.os.SystemClock.uptimeMillis();
        kioskBackPresses = now - kioskBackAt < 1600 ? kioskBackPresses + 1 : 1;
        kioskBackAt = now;
        if (kioskBackPresses < 3) {
            android.widget.Toast.makeText(this, kioskBackPresses == 1 ? "اضغط رجوع مرتين أخريين للخروج"
                    : "اضغط رجوع مرة أخرى للخروج", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        kioskBackPresses = 0;
        preferences.edit().putBoolean(LauncherOverrideService.KIOSK, false).apply();
        android.widget.Toast.makeText(this, "أوقفت الشاشة الدائمة. لإعادتها: زر القائمة ← المشغّل دائم على الشاشة",
                android.widget.Toast.LENGTH_LONG).show();
        openStockHome();
    }

    /** Opens the TV's own home screen. */
    private void openStockHome() {
        for (String pkg : new String[]{"com.google.android.apps.tv.launcherx", "com.google.android.tvlauncher",
                "com.google.android.leanbacklauncher"}) {
            try {
                startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(pkg)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return;
            } catch (Exception ignored) { }
        }
        try { startActivity(new Intent(android.provider.Settings.ACTION_HOME_SETTINGS)); return; }
        catch (Exception ignored) { }
        moveTaskToBack(true);
    }

    private final Runnable screensaverIdle = this::startScreensaverIfIdle;
    private void startScreensaverIfIdle() {
        // Never interrupt a recitation, the Haram live stream or the wallpapers with the screensaver.
        boolean playing = quranView != null && quranView.isPlaying();
        if (haramView != null && haramView.getVisibility() == View.VISIBLE) playing = true;
        if (galleryView != null && galleryView.getVisibility() == View.VISIBLE) playing = true;
        if (screenActive && hasWindowFocus() && !playing) {
            startActivity(new Intent(this, ScreensaverPreviewActivity.class));
        } else if (screenActive) scheduleScreensaverIdle();
    }


    private void scheduleScreensaverIdle() {
        handler.removeCallbacks(screensaverIdle);
        if (preferences == null || !screenActive) return;
        int minutes = ScreensaverPreferences.idleMinutes(preferences.getInt(ScreensaverPreferences.IDLE, 0));
        if (minutes > 0) handler.postDelayed(screensaverIdle, minutes * 60_000L);
    }

    @Override public void onUserInteraction() {
        super.onUserInteraction();
        scheduleScreensaverIdle();
    }
    @Override protected void onResume() {
        super.onResume();
        screenActive = true;
        PrayerAlertToast.foreground = new java.lang.ref.WeakReference<>(this);
        PrayerAlerts.schedule(this);
        if (activeTab == TAB_HARAM && haramView != null && haramView.getVisibility() == View.VISIBLE) haramView.start();
        if (activeTab == TAB_GALLERY && galleryView != null && galleryView.getVisibility() == View.VISIBLE) galleryView.start();
        scheduleScreensaverIdle();
        maybeRefreshPublicCalendar(false);
        scheduleCalendarRefresh();
        maybeRefreshWeather(false);
        scheduleWeatherRefresh();
        renderApps();
        if (!backgroundPhotos.isEmpty()) {
            if (slideA.getVisibility() != View.VISIBLE && slideB.getVisibility() != View.VISIBLE) showNextBackgroundPhoto();
            else scheduleNextBackgroundPhoto();
        }
    }

    @Override protected void onPause() {
        if (PrayerAlertToast.foreground.get() == this) PrayerAlertToast.foreground = new java.lang.ref.WeakReference<>(null);
        screenActive = false;
        handler.removeCallbacks(screensaverIdle);
        if (slideshowTick != null) handler.removeCallbacks(slideshowTick);
        if (calendarRefreshTick != null) handler.removeCallbacks(calendarRefreshTick);
        if (weatherRefreshTick != null) handler.removeCallbacks(weatherRefreshTick);
        backgroundImageGeneration++;
        super.onPause();
    }


    @Override protected void onStop() {
        // Leaving the launcher (opening YouTube, say) pauses the recitation.
        if (quranView != null) quranView.pause();
        if (haramView != null) haramView.pause();
        if (galleryView != null) galleryView.stop();
        super.onStop();
    }
    @Override protected void onDestroy() {
        lifecycleWork.close();
        handler.removeCallbacksAndMessages(null);
        calendarRequestGeneration++;
        backgroundImageGeneration++;
        weatherRequestGeneration++;
        calendarWorker.shutdownNow();
        ioWorker.shutdownNow();
        backgroundBitmapCache.evictAll();
        glassBackdrop.close();
        Motion.cancelTree(getWindow().getDecorView());
        super.onDestroy();
    }

    private void buildHome() {
        boolean light = LiquidTheme.isLight(this);
        FrameLayout page = new FrameLayout(this);
        page.setBackgroundColor(Color.TRANSPARENT);
        page.setClipChildren(false);
        page.setClipToPadding(false);
        FrameLayout scene = new FrameLayout(this);
        setContentView(scene);

        heroFrame = new FrameLayout(this);
        heroFrame.setBackgroundColor(light ? LiquidTheme.background(this) : 0xFF071417);
        wallpaperImage = backgroundImage();
        wallpaperImage.setVisibility(View.VISIBLE);
        slideA = backgroundImage();
        slideB = backgroundImage();
        heroFrame.addView(wallpaperImage, new FrameLayout.LayoutParams(-1, -1));
        heroFrame.addView(slideA, new FrameLayout.LayoutParams(-1, -1));
        heroFrame.addView(slideB, new FrameLayout.LayoutParams(-1, -1));
        View shade = new View(this);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                light ? new int[]{0x22FFFFFF, 0x05FFFFFF, 0x18FFFFFF, 0x55E9F1EE}
                      : new int[]{0x33000000, 0x09000000, 0x23000000, 0x9B031013}));
        heroFrame.addView(shade, new FrameLayout.LayoutParams(-1, -1));
        // Day look: a pale version of the royal pattern over everything.
        dayBackdrop = backgroundImage();
        dayBackdrop.setVisibility(View.GONE);
        heroFrame.addView(dayBackdrop, new FrameLayout.LayoutParams(-1, -1));
        scene.addView(heroFrame, new FrameLayout.LayoutParams(-1, -1));
        browsingShade = new View(this);
        browsingShade.setBackgroundColor(light ? 0xFFE9F1EE : 0xFF071417);
        browsingShade.setAlpha(light ? 0.50f : 0f);
        scene.addView(browsingShade, new FrameLayout.LayoutParams(-1, -1));
        fixedLayer = new FrameLayout(this);
        fixedLayer.setVisibility(View.GONE);
        fixedImage = new ImageView(this);
        fixedImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        fixedLayer.addView(fixedImage, new FrameLayout.LayoutParams(-1, -1));
        fixedVeil = new View(this);
        fixedVeil.setBackgroundColor(0xFF05070A);
        fixedLayer.addView(fixedVeil, new FrameLayout.LayoutParams(-1, -1));
        scene.addView(fixedLayer, new FrameLayout.LayoutParams(-1, -1));
        sceneRoot = scene;

        LinearLayout tabShell = new LinearLayout(this);
        launcherShell = tabShell;
        tabShell.setOrientation(LinearLayout.VERTICAL);
        tabShell.setClipChildren(false);
        tabShell.setClipToPadding(false);
        scene.addView(tabShell, new FrameLayout.LayoutParams(-1, -1));
        FrameLayout viewport = new FrameLayout(this);
        viewport.setClipChildren(false);
        viewport.setClipToPadding(false);
        tabShell.addView(viewport, new LinearLayout.LayoutParams(-1, 0, 1));

        // The dock at the bottom: a glass pill of icons (sized in styleNavigation).
        LinearLayout navigation = new LinearLayout(this);
        navigation.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        navigation.setClipChildren(false);
        navigation.setClipToPadding(false);
        navigation.setPadding(dp(33), dp(8), dp(33), dp(8));
        tabShell.addView(navigation, new LinearLayout.LayoutParams(-1, dp(50)));
        LinearLayout navbar = new LinearLayout(this);
        navbar.setGravity(Gravity.CENTER_VERTICAL);
        navbar.setClipChildren(false);
        navbar.setClipToPadding(false);
        navbarView = navbar;
        navigation.addView(navbar, new LinearLayout.LayoutParams(-2, dp(34)));
        HorizontalScrollView tabScroller = new HorizontalScrollView(this);
        tabScroller.setHorizontalScrollBarEnabled(false);
        tabScroller.setOverScrollMode(View.OVER_SCROLL_NEVER);
        tabScroller.setClipChildren(true);
        tabScroller.setClipToPadding(false);
        navbar.addView(tabScroller, new LinearLayout.LayoutParams(-2, -1));
        tabRow = new LinearLayout(this);
        tabRow.setGravity(Gravity.CENTER_VERTICAL);
        tabRow.setClipChildren(false);
        tabRow.setClipToPadding(false);
        tabRow.setPadding(dp(4), dp(2), dp(4), dp(2));
        tabScroller.addView(tabRow, new HorizontalScrollView.LayoutParams(-2, -1));
        for (int i = 0; i < TAB_TITLES.length; i++) {
            if (isRemovedTab(i)) continue;
            final int tab = i;
            Button button = actionButton("");
            button.setId(View.generateViewId());
            button.setMinWidth(0);
            button.setMinimumWidth(0);
            button.setContentDescription(TAB_TITLES[i]);
            NavigationGlyph glyph = new NavigationGlyph(i);
            navigationGlyphs[i] = glyph;
            button.setCompoundDrawablesRelative(null, null, glyph, null);
            // A soft gold underline marks the selected tab and stretches when focused.
            button.setBackground(new TabIndicatorDrawable(light ? 0xFF9A6A1F : 0xFFE3B469,
                    light ? 0x14000000 : 0x1AFFFFFF, getResources().getDisplayMetrics().density));
            button.setSelected(i == activeTab);
            glyph.setGlyphColor(tabTextColor(i == activeTab));
            button.setOnClickListener(v -> { if (tab == TAB_HARAM) openHaramLive(); else selectTab(tab, false); });
            button.setOnFocusChangeListener((view, focused) -> {
                LiquidTheme.animateContainedFocus(view, focused);
                // Full-screen tabs open with OK only, so moving along the bar never jumps into them.
                if (focused && !isFullScreenTab(tab)) selectTab(tab, false);
            });
            tabButtons[i] = button;
        }
        // The settings open with the remote's Menu key, or a long press on «آمر».
        tabButtons[TAB_HOME].setOnLongClickListener(v -> { quickSettings.open(); return true; });

        tabPages[TAB_HOME] = page;
        viewport.addView(page, new FrameLayout.LayoutParams(-1, -1));
        modernHome = new ModernHomeView(this, preferences);
        page.addView(modernHome, new FrameLayout.LayoutParams(-1, -1));
        eventsContent = addTabContentPage(viewport, TAB_EVENTS);
        addPrayerPage(viewport);
        // The Quran player covers the whole screen, above the wallpaper and the navigation bar.
        quranView = new QuranView(this, preferences);
        quranView.setVisibility(View.GONE);
        tabPages[TAB_QURAN] = quranView;
        sceneRoot.addView(quranView, new FrameLayout.LayoutParams(-1, -1));
        linkTabEntry(TAB_QURAN, quranView.entryFocus());
        haramView = new HaramView(this);
        haramView.setVisibility(View.GONE);
        tabPages[TAB_HARAM] = haramView;
        sceneRoot.addView(haramView, new FrameLayout.LayoutParams(-1, -1));
        linkTabEntry(TAB_HARAM, haramView.entryFocus());
        galleryView = new GalleryView(this, preferences);
        galleryView.setOnFixedChanged(() -> { updateFixedBackground(); quickSettings.refresh(); });
        galleryView.setVisibility(View.GONE);
        tabPages[TAB_GALLERY] = galleryView;
        sceneRoot.addView(galleryView, new FrameLayout.LayoutParams(-1, -1));
        linkTabEntry(TAB_GALLERY, galleryView.entryFocus());
        iqamaPill = new IqamaCountdown.Pill(this, preferences);
        FrameLayout.LayoutParams pillParams = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.LEFT);
        pillParams.setMargins(dp(30), dp(26), 0, 0);
        sceneRoot.addView(iqamaPill, pillParams);
        quickSettings = new QuickSettingsView(this, preferences, new QuickSettingsView.Listener() {
            @Override public void onMoodChanged(String mood) { applyMood(); }
            @Override public void onCalmColourChanged(int index) { applyMood(); }
            @Override public void onWeatherCityChanged(PrayerTimes.City city) {
                saveWeatherLocation("city", new WeatherRepository.Location(city.name, city.latitude, city.longitude));
            }
            @Override public void onGalleryIntervalChanged(int minutes) { galleryView.intervalChanged(); }
            @Override public void onHomeThemeChanged() { applyHomeTheme(); }
            @Override public void onFixedBackgroundChanged() { updateFixedBackground(); }
            @Override public void onPrayerSettingsChanged(boolean alertsTurnedOn) {
                PrayerAlerts.schedule(MainActivity.this);
                prayerView.settingsChanged();
                modernHome.rebuild();
                // Showing the card over other apps needs "display over other apps"; ask once when turning on.
                if (alertsTurnedOn && !android.provider.Settings.canDrawOverlays(MainActivity.this)) {
                    try {
                        startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:" + getPackageName())));
                    } catch (RuntimeException settingsMissing) {
                        Toast.makeText(MainActivity.this,
                                "فعّل «العرض فوق التطبيقات» لـ AAA TV من إعدادات التلفزيون", Toast.LENGTH_LONG).show();
                    }
                }
            }
            @Override public void onGalleryCategoryChanged() { galleryView.categoryChanged(); }
            @Override public List<String[]> installedApps() {
                ArrayList<String[]> result = new ArrayList<>();
                for (AppEntry entry : MainActivity.this.installedApps()) result.add(new String[]{entry.packageName, entry.label});
                return result;
            }
            @Override public List<String> homeApps() {
                List<AppEntry> all = MainActivity.this.installedApps();
                List<String> selected = selectedPackages(all);
                if (selected.isEmpty() && !preferences.getBoolean("customApps", false)) selected = defaultApps(all);
                return selected;
            }
            @Override public void onKioskChanged(boolean enabled) {
                if (enabled && !isLauncherOverrideEnabled()) {
                    // The helper needs Accessibility permission once to bring AAA TV back over the TV's own home.
                    Toast.makeText(MainActivity.this, "فعّل «AAA TV» في إمكانية الوصول، ثم ارجع", Toast.LENGTH_LONG).show();
                    try { startActivity(new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)); }
                    catch (Exception ignored) { }
                }
            }
            @Override public void onHomeAppsChanged(List<String> packages) {
                preferences.edit().putString("selectedApps", String.join("\n", packages))
                        .putBoolean("customApps", true).apply();
                renderApps();
            }
        });
        sceneRoot.addView(quickSettings, new FrameLayout.LayoutParams(-1, -1));
        buildEventsTab();
        applyWallpaper();
        selectTab(activeTab, false);
    }


    private LinearLayout addTabContentPage(FrameLayout viewport, int tab) {
        ScrollView page = new ScrollView(this);
        page.setFillViewport(true);
        page.setVerticalScrollBarEnabled(false);
        page.setBackgroundColor(Color.TRANSPARENT);
        page.setClipChildren(false);
        page.setClipToPadding(false);
        page.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int topPad = tab == TAB_TV ? dp(24) : dp(54);
        int bottomPad = tab == TAB_TV ? dp(20) : dp(34);
        content.setPadding(dp(72), topPad, dp(72), bottomPad);
        content.setClipChildren(false);
        content.setClipToPadding(false);
        TextView title = label(TAB_TITLES[tab], 42, LiquidTheme.text(this), true);
        title.setTypeface(LiquidTheme.font(this));
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, dp(72));
        titleParams.topMargin = dp(4);
        content.addView(title, titleParams);
        page.addView(content);
        page.setOnScrollChangeListener((v, x, y, oldX, oldY) -> {
            if (activeTab == tab) browsingShade.setAlpha(shadeAlpha(tab, y));
            glassBackdrop.invalidate();
        });
        tabPages[tab] = page;
        viewport.addView(page, new FrameLayout.LayoutParams(-1, -1));
        return content;
    }

    private void addPrayerPage(FrameLayout viewport) {
        ScrollView page = new ScrollView(this);
        page.setFillViewport(true);
        page.setVerticalScrollBarEnabled(false);
        page.setOverScrollMode(View.OVER_SCROLL_NEVER);
        prayerView = new PrayerView(this, preferences);
        page.addView(prayerView, new ScrollView.LayoutParams(-1, -1));
        page.setVisibility(View.GONE);
        tabPages[TAB_PRAYER] = page;
        viewport.addView(page, new FrameLayout.LayoutParams(-1, -1));
        linkTabEntry(TAB_PRAYER, prayerView.entryFocus());
    }

    private void linkTabEntry(int tab, View entry) {
        if (entry == null || tab < TAB_HOME || tab > LAST_TAB || tabButtons[tab] == null) return;
        if (entry.getId() == View.NO_ID) entry.setId(View.generateViewId());
        tabButtons[tab].setNextFocusUpId(entry.getId());
        tabEntryFocus[tab] = entry;
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (quickSettings != null && quickSettings.isOpen()) return quickSettings.handleKey(event);
        if (activeTab == TAB_HARAM && haramView != null && haramView.getVisibility() == View.VISIBLE
                && haramView.handleKey(event)) return true;
        if (activeTab == TAB_GALLERY && galleryView != null && galleryView.getVisibility() == View.VISIBLE
                && galleryView.handleKey(event)) return true;
        // The Quran player handles its own remote keys, so the launcher's page navigation never steals them.
        if (activeTab == TAB_QURAN && quranView != null && quranView.getVisibility() == View.VISIBLE) {
            if (quranView.handleKey(event)) return true;
            if (event.getKeyCode() != KeyEvent.KEYCODE_BACK && event.getKeyCode() != KeyEvent.KEYCODE_HOME)
                return super.dispatchKeyEvent(event);
        }
        if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_CENTER || event.getKeyCode() == KeyEvent.KEYCODE_ENTER || event.getKeyCode() == KeyEvent.KEYCODE_NUMPAD_ENTER) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) event.startTracking();
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0
                && (event.getKeyCode() == KeyEvent.KEYCODE_MENU || event.getKeyCode() == KeyEvent.KEYCODE_SETTINGS)) {
            if (quickSettings != null) quickSettings.open();
            return true;
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN && isDpadDirection(event.getKeyCode()) && tabsInitialized) {
            View focused = getCurrentFocus();
            int direction = focusDirection(event.getKeyCode());
            if (activeTab == TAB_EVENTS && focused != null
                    && handleCalendarDirection(focused, direction)) return true;
            if (focused != null && tabRow != null && isWithinPage(focused, tabRow)) {
                if (direction == View.FOCUS_UP) {
                    View entry = tabEntryFocus[activeTab];
                    if (entry != null && entry.isShown() && entry.isEnabled() && entry.requestFocus()) return true;
                    return true;
                }
                if (direction == View.FOCUS_LEFT || direction == View.FOCUS_RIGHT) {
                    View next = focused.focusSearch(direction);
                    if (next != null && isWithinPage(next, tabRow)
                            && next.isFocusable() && next.isEnabled() && next.requestFocus()) return true;
                }
                return true;
            }
            View page = tabPages[activeTab];
            if (focused != null && page != null && isWithinPage(focused, page)) {
                View next = focused.focusSearch(direction);
                if (next != null && next.isFocusable() && next.isEnabled()
                        && isInFocusDirection(focused, next, direction)) {
                    if (isWithinPage(next, page) && next.requestFocus()) return true;
                    if (direction == View.FOCUS_DOWN && isWithinPage(next, tabRow)) {
                        if (scrollPageWithinDirection(focused, page, direction)) return true;
                        Button destination = navigationButton(activeTab);
                        if (destination != null && destination.requestFocus()) return true;
                    }
                }
                if (scrollPageWithinDirection(focused, page, direction)) return true;
                if (direction == View.FOCUS_DOWN) {
                    Button destination = navigationButton(activeTab);
                    if (destination != null && destination.requestFocus()) return true;
                }
                return true;
            }
            if (focused != null && focused.getParent() instanceof ViewGroup) {
                ViewGroup siblingGroup = (ViewGroup) focused.getParent();
                View next = focused.focusSearch(direction);
                if (next != null && next.getParent() == siblingGroup
                        && next.isFocusable() && next.isEnabled() && next.requestFocus()) return true;
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }


    @Override public boolean onKeyLongPress(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
            View focused = getCurrentFocus();
            if (focused != null && focused.performLongClick()) return true;
        }
        return super.onKeyLongPress(keyCode, event);
    }

    private View calendarDateView(LocalDate day) {
        if (calendarMonthGrid == null) return null;
        for (int i = 7; i < calendarMonthGrid.getChildCount(); i++) {
            View child = calendarMonthGrid.getChildAt(i);
            if (day.equals(child.getTag())) return child;
        }
        return null;
    }

    private boolean focusCalendarDate(LocalDate day) {
        View destination = calendarDateView(day);
        return destination != null && destination.requestFocus();
    }

    private boolean handleCalendarDirection(View focused, int direction) {
        if (focused instanceof CalendarDayButton && focused.getTag() instanceof LocalDate) {
            LocalDate day = (LocalDate) focused.getTag();
            int delta = direction == View.FOCUS_LEFT ? 1 : direction == View.FOCUS_RIGHT ? -1
                    : direction == View.FOCUS_UP ? -7 : 7;
            LocalDate target = day.plusDays(delta);
            if (YearMonth.from(target).equals(displayedMonth)) {
                focusCalendarDate(target);
            } else if (direction == View.FOCUS_UP) {
                previousMonthButton.requestFocus();
            } else if (direction == View.FOCUS_DOWN) {
                navigationButton(TAB_EVENTS).requestFocus();
            } else if (CalendarDates.inRange(YearMonth.from(target), YearMonth.now())) {
                // Move naturally across month boundaries without relying on geometry.
                // Keep focus off the old grid while its date views are rebuilt.
                previousMonthButton.requestFocus();
                displayedMonth = YearMonth.from(target);
                selectedCalendarDay = target;
                renderMonthCalendar(cachedCalendarEvents());
                focusCalendarDate(target);
            }
            return true;
        }
        if (direction == View.FOCUS_UP
                && isWithinPage(focused, tabRow)) {
            focusCalendarDate(selectedCalendarDay);
            return true;
        }
        if (direction == View.FOCUS_DOWN && isWithinPage(focused, eventsContent)
                && !isWithinPage(focused, calendarMonthGrid)) {
            focusCalendarDate(selectedCalendarDay);
            return true;
        }
        return false;
    }

    private boolean isDpadDirection(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                || keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT;
    }

    private int focusDirection(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP: return View.FOCUS_UP;
            case KeyEvent.KEYCODE_DPAD_DOWN: return View.FOCUS_DOWN;
            case KeyEvent.KEYCODE_DPAD_LEFT: return View.FOCUS_LEFT;
            default: return View.FOCUS_RIGHT;
        }
    }

    private boolean isInFocusDirection(View from, View to, int direction) {
        int[] fromLocation = new int[2];
        int[] toLocation = new int[2];
        from.getLocationOnScreen(fromLocation);
        to.getLocationOnScreen(toLocation);
        float fromX = fromLocation[0] + from.getWidth() / 2f;
        float fromY = fromLocation[1] + from.getHeight() / 2f;
        float toX = toLocation[0] + to.getWidth() / 2f;
        float toY = toLocation[1] + to.getHeight() / 2f;
        switch (direction) {
            case View.FOCUS_UP: return toY < fromY;
            case View.FOCUS_DOWN: return toY > fromY;
            case View.FOCUS_LEFT: return toX < fromX;
            default: return toX > fromX;
        }
    }

    private boolean scrollPageWithinDirection(View child, View page, int direction) {
        for (View current = child; current != null; ) {
            boolean canScroll = direction == View.FOCUS_UP ? current.canScrollVertically(-1)
                    : direction == View.FOCUS_DOWN ? current.canScrollVertically(1)
                    : direction == View.FOCUS_LEFT ? current.canScrollHorizontally(-1)
                    : current.canScrollHorizontally(1);
            if (canScroll) {
                int distance = direction == View.FOCUS_UP || direction == View.FOCUS_DOWN
                        ? Math.max(1, current.getHeight() / 2) : Math.max(1, current.getWidth() / 2);
                current.scrollBy(direction == View.FOCUS_LEFT ? -distance
                                : direction == View.FOCUS_RIGHT ? distance : 0,
                        direction == View.FOCUS_UP ? -distance
                                : direction == View.FOCUS_DOWN ? distance : 0);
                return true;
            }
            if (current == page) break;
            android.view.ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        return false;
    }

    private boolean isWithinPage(View child, View page) {
        for (View current = child; current != null; ) {
            if (current == page) return true;
            android.view.ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        return false;
    }

    private boolean canScrollWithinPage(View child, View page, int direction) {
        for (View current = child; current != null; ) {
            boolean canScroll = direction == View.FOCUS_UP ? current.canScrollVertically(-1)
                    : direction == View.FOCUS_DOWN ? current.canScrollVertically(1)
                    : direction == View.FOCUS_LEFT ? current.canScrollHorizontally(-1)
                    : current.canScrollHorizontally(1);
            if (canScroll) return true;
            if (current == page) break;
            android.view.ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        return false;
    }

    private void selectTab(int tab, boolean focusTabButton) {
        // Hiding a focused page can synchronously focus a navigation button.
        // Its listener must not start another switch partway through this one.
        if (switchingTabs) return;
        if (tab < TAB_HOME || tab > LAST_TAB || tabPages[TAB_HOME] == null) return;
        final int selectedTab = isNavigationHidden(tab) ? TAB_HOME : tab;
        View currentFocus = getCurrentFocus();
        boolean navigatingTabs = false;
        for (Button button : tabButtons) {
            if (button == currentFocus) { navigatingTabs = true; break; }
        }
        final boolean keepNavigationFocus = navigatingTabs;
        boolean animatePage = tabsInitialized && activeTab != selectedTab;
        final int previousTab = activeTab;
        View oldPage = tabPages[activeTab];
        if (tabsInitialized && oldPage != null && oldPage.getVisibility() == View.VISIBLE) {
            tabScroll[activeTab] = activeTab == TAB_HOME ? 0 : oldPage.getScrollY();
            View focused = oldPage.findFocus();
            if (focused != null) tabFocus[activeTab] = focused;
        }
        activeTab = selectedTab;
        tabsInitialized = true;
        switchingTabs = true;
        try {
            for (int i = 0; i < tabPages.length; i++) {
                if (tabPages[i] != null) {
                    if (i != selectedTab) Motion.reset(tabPages[i]);
                    tabPages[i].setVisibility(i == selectedTab ? View.VISIBLE : View.GONE);
                }
                if (tabButtons[i] != null) {
                    if (i != previousTab && i != selectedTab) continue;
                    boolean selected = i == selectedTab;
                    tabButtons[i].setSelected(selected);
                    navigationGlyphs[i].setGlyphColor(tabTextColor(selected));
                }
            }
            if (!keepNavigationFocus && !focusTabButton) {
                View destination = tabFocus[selectedTab];
                if (destination == null || !destination.isAttachedToWindow() || !destination.isShown())
                    destination = tabEntryFocus[selectedTab];
                if (destination != null) destination.requestFocus();
            }
        } finally {
            switchingTabs = false;
        }
        boolean fullScreen = selectedTab == TAB_QURAN || selectedTab == TAB_HARAM || selectedTab == TAB_GALLERY;
        if (iqamaPill != null) iqamaPill.setAllowed(iqamaPillAllowed(selectedTab));
        if (dayBackdrop != null) dayBackdrop.setVisibility(selectedTab == TAB_HOME
                && ModernTheme.day(preferences) ? View.VISIBLE : View.GONE);
        if (launcherShell != null) launcherShell.setDescendantFocusability(fullScreen
                ? ViewGroup.FOCUS_BLOCK_DESCENDANTS : ViewGroup.FOCUS_AFTER_DESCENDANTS);
        if (selectedTab == TAB_QURAN && quranView != null) quranView.post(() -> quranView.entryFocus().requestFocus());
        if (selectedTab == TAB_HARAM && haramView != null) haramView.post(() -> haramView.entryFocus().requestFocus());
        if (selectedTab == TAB_GALLERY && galleryView != null) galleryView.post(() -> galleryView.entryFocus().requestFocus());
        // On the prayer page the whole screen, bar included, takes the page's own calm colour.
        browsingShade.setBackgroundColor(selectedTab == TAB_PRAYER ? PrayerView.BACKGROUND
                : (LiquidTheme.isLight(this) ? 0xFFE9F1EE : 0xFF071417));
        browsingShade.setAlpha(shadeAlpha(selectedTab, 0));
        updateFixedBackground();
        View selectedPage = tabPages[selectedTab];
        if (selectedPage != null) {
            if (animatePage) Motion.reveal(selectedPage);
            selectedPage.post(() -> {
                if (activeTab != selectedTab) return;
                if (selectedTab != TAB_HOME) selectedPage.scrollTo(0, tabScroll[selectedTab]);
                View previous = tabFocus[selectedTab];
                if (focusTabButton) navigationButton(selectedTab).requestFocus();
                else if (!keepNavigationFocus && previous != null && previous.isAttachedToWindow() && previous.isShown()) previous.requestFocus();
            });
        }
    }
    /** How much the plain colour covers the background: prayer and «تاريخك» let the background through. */
    private float shadeAlpha(int tab, int scrollY) {
        if (tab == TAB_PRAYER) return .45f;
        if (tab == TAB_HOME) return LiquidTheme.isLight(this) ? .50f : 0f;
        return Math.min(.94f, .40f + (float) scrollY / dp(600) * .2f);
    }


    /** The «بسيط ٢» font chosen in the settings, for screens outside the home cards. */
    private android.graphics.Typeface homeTypeface(int weight) {
        return ModernTheme.typeface(this, ModernTheme.font(preferences), weight);
    }

    private float homeTextScale() {
        return ModernTheme.scale(ModernTheme.font(preferences)) * ModernTheme.textSizeFactor(preferences);
    }

    private static String screenKey(int tab) {
        return tab == TAB_HOME ? "home" : tab == TAB_PRAYER ? "prayer" : tab == TAB_EVENTS ? "events" : null;
    }

    /** Shows or hides the fixed background for the current tab, loading the picture when it changed. */
    private void updateFixedBackground() {
        // The Quran player draws its own backdrop; it takes the fixed picture when that is switched on for it.
        if (quranView != null) quranView.setFixedPicture(FixedBackground.showsOn(preferences, "quran")
                ? FixedBackground.picture(preferences) : null);
        if (fixedLayer == null) return;
        String key = screenKey(activeTab);
        boolean show = FixedBackground.showsOn(preferences, key);
        if (!show) { fixedLayer.setVisibility(View.GONE); return; }
        // The cards on prayer and «تاريخك» need a little more shade than the home cards.
        fixedVeil.setAlpha(activeTab == TAB_HOME ? (ModernTheme.day(preferences) ? 0f : .18f) : .38f);
        String asset = FixedBackground.picture(preferences);
        fixedLayer.setVisibility(View.VISIBLE);
        if (asset.equals(fixedLoaded)) return;
        fixedLoaded = asset;
        int width = getResources().getDisplayMetrics().widthPixels;
        lifecycleWork.execute(ioWorker, () -> {
            android.graphics.Bitmap bitmap = null;
            try (java.io.InputStream bounds = getAssets().open(asset)) {
                BitmapFactory.Options probe = new BitmapFactory.Options();
                probe.inJustDecodeBounds = true;
                BitmapFactory.decodeStream(bounds, null, probe);
                int sample = 1;
                while (probe.outWidth / (sample * 2) >= width) sample *= 2;
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = sample;
                try (java.io.InputStream in = getAssets().open(asset)) { bitmap = BitmapFactory.decodeStream(in, null, options); }
            } catch (Exception | OutOfMemoryError ignored) { }
            final android.graphics.Bitmap loaded = bitmap;
            lifecycleWork.post(() -> {
                if (!asset.equals(fixedLoaded)) { if (loaded != null) loaded.recycle(); return; }
                if (loaded == null) { fixedLoaded = null; fixedLayer.setVisibility(View.GONE); return; }
                fixedImage.setImageBitmap(loaded);
                fixedImage.setAlpha(0f);
                fixedImage.animate().alpha(1f).setDuration(600).start();
            });
        });
    }

    /** Live TV, Games and Saved are removed from this build of the launcher. */
    private static boolean isFullScreenTab(int tab) {
        return tab == TAB_QURAN || tab == TAB_HARAM || tab == TAB_GALLERY;
    }

    private static int tabForKey(String key) {
        switch (key) {
            case "events": return TAB_EVENTS;
            case "quran": return TAB_QURAN;
            case "gallery": return TAB_GALLERY;
            case "haram": return TAB_HARAM;
            default: return TAB_PRAYER;
        }
    }
    /** The adhan-to-iqama pill: on the Quran and the wallpapers, and on Home when the prayer card is hidden. */
    private boolean iqamaPillAllowed(int tab) {
        if (tab == TAB_HOME) return !ModernTheme.card(preferences, "prayer");
        return tab == TAB_QURAN || tab == TAB_GALLERY;
    }

    /** Applies the day/night look, fonts, cards and tabs. */
    private void applyHomeTheme() {
        boolean day = ModernTheme.day(preferences);
        modernHome.rebuild();
        if (day && dayBackdrop.getDrawable() == null) {
            int width = getResources().getDisplayMetrics().widthPixels;
            lifecycleWork.execute(ioWorker, () -> {
                android.graphics.Bitmap bitmap = null;
                try (java.io.InputStream in = getAssets().open("damask/day.webp")) {
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inSampleSize = width <= 1920 ? 2 : 1;
                    bitmap = BitmapFactory.decodeStream(in, null, options);
                } catch (Exception | OutOfMemoryError ignored) { }
                final android.graphics.Bitmap loaded = bitmap;
                lifecycleWork.post(() -> { if (loaded != null) dayBackdrop.setImageBitmap(loaded); });
            });
        }
        dayBackdrop.setVisibility(day && activeTab == TAB_HOME ? View.VISIBLE : View.GONE);
        // Rebuilding the bar moves focus (and can switch the page behind the settings), so only do it
        // when the day/night look or the tabs really changed.
        String navigation = day + "|" + ModernTheme.tabs(preferences);
        if (!navigation.equals(navigationSignature)) {
            navigationSignature = navigation;
            styleNavigation(day);
            rebuildNavigationButtons();
        }
        for (int i = 0; i < tabButtons.length; i++) {
            if (tabButtons[i] != null && navigationGlyphs[i] != null) {
                navigationGlyphs[i].setGlyphColor(tabTextColor(i == activeTab));
                navigationGlyphs[i].invalidateSelf();
            }
        }
        iqamaPill.setAllowed(iqamaPillAllowed(activeTab));
        prayerView.setGlass(true);
        quranView.setGlass(true);
        if (calendarHeading != null) {
            GradientDrawable heading = new GradientDrawable();
            heading.setColor(0xC0141820);
            heading.setCornerRadius(dp(20));
            heading.setStroke(Math.max(1, dp(1)), 0x22FFFFFF);
            calendarHeading.setBackground(heading);
            calendarHeading.setPadding(dp(14), 0, dp(14), 0);
        }
        String calendarLook = ModernTheme.font(preferences) + "|" + ModernTheme.textSize(preferences);
        if (calendarMonthGrid != null && displayedMonth != null && !calendarLook.equals(calendarGlass)) {
            calendarGlass = calendarLook;
            monthTitle.setTypeface(homeTypeface(500));
            calendarHeaderDate.setTypeface(homeTypeface(400));
            calendarHeaderEvents.setTypeface(homeTypeface(500));
            calendarHeaderDate.setTextSize(13 * homeTextScale());
            calendarHeaderEvents.setTextSize(15 * homeTextScale());
            renderMonthCalendar(cachedCalendarEvents());
        }
        browsingShade.setAlpha(shadeAlpha(activeTab, 0));
        updateFixedBackground();
        renderWeather();
        updateOccasion();
        renderApps();
    }

    /** Icons only, on a glass pill sized on the cards' 960-unit grid. */
    private void styleNavigation(boolean day) {
        float unit = getResources().getDisplayMetrics().widthPixels / 960f;
        GradientDrawable pill = new GradientDrawable();
        pill.setColor(day ? 0x99FFFFFF : 0xC0141820);
        pill.setCornerRadius(Math.round(14 * unit));
        pill.setStroke(Math.max(1, Math.round(0.8f * unit)), day ? 0xCCFFFFFF : 0x22FFFFFF);
        navbarView.setBackground(pill);
        // A taller dock sitting the same distance from the bottom as the cards from the top.
        ViewGroup.LayoutParams barParams = navbarView.getLayoutParams();
        barParams.height = Math.round(unit * 46);
        navbarView.setLayoutParams(barParams);
        LinearLayout dock = (LinearLayout) navbarView.getParent();
        ViewGroup.LayoutParams dockParams = dock.getLayoutParams();
        dockParams.height = Math.round(unit * (46 + 6 + 20));
        dock.setLayoutParams(dockParams);
        dock.setPadding(dock.getPaddingLeft(), Math.round(unit * 6), dock.getPaddingRight(), Math.round(unit * 20));
        navbarView.setPadding(Math.round(unit * 10), 0, Math.round(unit * 10), 0);
        for (int i = 0; i < tabButtons.length; i++) {
            Button button = tabButtons[i];
            if (button == null || navigationGlyphs[i] == null) continue;
            int size = Math.round(unit * 22);
            navigationGlyphs[i].setBounds(0, 0, size, size);
            button.setText("");
            button.setCompoundDrawablePadding(0);
            int pad = Math.round(unit * 20);
            button.setPadding(pad, 0, pad, 0);
            button.setCompoundDrawablesRelative(null, null, navigationGlyphs[i], null);
        }
    }


    /** Fills the «بسيط ٢» tiles: the chosen apps, with the Quran, Haram and wallpapers beside YouTube. */
    private void showModernApps(List<AppEntry> all, List<String> selected) {
        if (modernHome == null) return;
        ArrayList<ModernHomeView.App> list = new ArrayList<>();
        boolean builtIns = false;
        for (String packageName : selected) {
            AppEntry entry = findApp(all, packageName);
            if (entry == null) continue;
            boolean youtube = packageName.startsWith("com.google.android.youtube");
            boolean stremio = packageName.equals("com.stremio.one");
            list.add(new ModernHomeView.App(entry.label, youtube ? "yt" : stremio ? "stremio" : "app",
                    youtube || stremio ? null : entry.icon, packageName, () -> launchApp(entry.packageName)));
            if (youtube && !builtIns) { addModernBuiltIns(list); builtIns = true; }
        }
        if (!builtIns) addModernBuiltIns(list);
        View first = modernHome.setApps(list);
        if (first == null) return;
        tabEntryFocus[TAB_HOME] = first;
        tabButtons[TAB_HOME].setNextFocusUpId(first.getId());
        for (int i = 0; i < tabButtons.length; i++) if (tabButtons[i] != null) tabButtons[i].setNextFocusUpId(first.getId());
        if (activeTab == TAB_HOME && (quickSettings == null || !quickSettings.isOpen())) {
            View target = modernHome.tileFor(modernHome.focusedTag());
            View current = getCurrentFocus();
            if (target != null && (current == null || !current.isShown()))
                modernHome.post(target::requestFocus);
        }
    }

    private void addModernBuiltIns(List<ModernHomeView.App> list) {
        list.add(new ModernHomeView.App("القرآن الكريم", "quran", null, "builtin:quran", () -> selectTab(TAB_QURAN, false)));
        list.add(new ModernHomeView.App("الحرم مباشر", "haram", null, "builtin:haram", this::openHaramLive));
        list.add(new ModernHomeView.App("الخلفيات", "gallery", null, "builtin:gallery", () -> selectTab(TAB_GALLERY, false)));
    }

    private static boolean isRemovedTab(int tab) {
        return tab == TAB_TV || tab == TAB_GAMES || tab == TAB_SAVED;
    }
    private int tabTextColor(boolean selected) {
        boolean light = LiquidTheme.isLight(this) || (preferences != null && ModernTheme.day(preferences));
        return selected ? (light ? 0xFF1E2A2A : 0xFFF4EFE6) : (light ? 0x9924403D : 0x8CFFFFFF);
    }

    private boolean isNavigationHidden(int tab) {
        return isRemovedTab(tab);
    }


    private Button navigationButton(int tab) {
        return isNavigationHidden(tab) ? tabButtons[TAB_HOME] : tabButtons[tab];
    }
    private void rebuildNavigationButtons() {
        if (tabRow == null) return;
        tabRow.removeAllViews();
        ArrayList<Integer> visible = new ArrayList<>();
        visible.add(TAB_HOME);
        for (String key : ModernTheme.tabs(preferences)) visible.add(tabForKey(key));
        int height = Math.round(getResources().getDisplayMetrics().widthPixels / 960f * 38);
        // Right-to-left: the first tab sits at the right end of the bar.
        for (int i = visible.size() - 1; i >= 0; i--)
            tabRow.addView(tabButtons[visible.get(i)], new LinearLayout.LayoutParams(-2, height));
    }


    private void buildEventsTab() {
        // Events is a dedicated month view, so let its own month header lead.
        if (eventsContent.getChildCount() > 0) eventsContent.getChildAt(0).setVisibility(View.GONE);
        eventsContent.setPadding(dp(28), dp(20), dp(28), dp(12));
        LinearLayout monthHeading = new LinearLayout(this);
        calendarHeading = monthHeading;
        monthHeading.setGravity(Gravity.CENTER_VERTICAL);
        monthHeading.setClipChildren(false);
        monthHeading.setClipToPadding(false);
        LinearLayout.LayoutParams monthParams = new LinearLayout.LayoutParams(-1, dp(80));
        eventsContent.addView(monthHeading, monthParams);
        boolean light = LiquidTheme.isLight(this);
        Button backButton = actionButton("‹");
        backButton.setTextSize(30);
        alignCalendarArrow(backButton);
        backButton.setText("›");
        backButton.setContentDescription("رجوع");
        backButton.setBackground(calendarGlassShape(
                light ? LiquidTheme.surface(this) : 0xC91A1D26,
                light ? LiquidTheme.border(this) : 0xAAFFFFFF, 28));
        backButton.setOnClickListener(v -> selectTab(TAB_HOME, true));
        linkTabEntry(TAB_EVENTS, backButton);
        LinearLayout.LayoutParams backParams = new LinearLayout.LayoutParams(dp(52), dp(52));
        backParams.setMargins(dp(2), dp(2), dp(14), dp(2));
        monthHeading.addView(backButton, backParams);
        monthTitle = label("تاريخك", 32, Color.WHITE, true);
        monthTitle.setTextColor(LiquidTheme.text(this));
        monthHeading.addView(monthTitle, new LinearLayout.LayoutParams(-2, -2));
        LinearLayout eventSummary = new LinearLayout(this);
        eventSummary.setOrientation(LinearLayout.VERTICAL);
        eventSummary.setGravity(Gravity.CENTER_VERTICAL);
        eventSummary.setPadding(dp(20), 0, dp(20), 0);
        calendarHeaderDate = label("", 10, 0xFFE3B469, true);
        calendarHeaderDate.setLetterSpacing(0f);
        calendarHeaderDate.setTextSize(12);
        calendarHeaderDate.setGravity(Gravity.RIGHT);
        eventSummary.addView(calendarHeaderDate);
        calendarHeaderEvents = label("", 14, LiquidTheme.text(this), true);
        calendarHeaderEvents.setMaxLines(3);
        calendarHeaderEvents.setEllipsize(TextUtils.TruncateAt.END);
        calendarHeaderEvents.setPadding(0, dp(4), 0, 0);
        calendarHeaderEvents.setGravity(Gravity.RIGHT);
        eventSummary.addView(calendarHeaderEvents);
        monthHeading.addView(eventSummary, new LinearLayout.LayoutParams(0, -1, 1));
        LinearLayout monthSwitcher = new LinearLayout(this);
        monthSwitcher.setGravity(Gravity.CENTER_VERTICAL);
        monthSwitcher.setClipChildren(false);
        monthSwitcher.setClipToPadding(false);
        monthSwitcher.setBackground(calendarGlassShape(
                light ? LiquidTheme.surface(this) : 0xC91A1D26,
                light ? LiquidTheme.border(this) : 0xAAFFFFFF, 30));
        monthHeading.addView(monthSwitcher, new LinearLayout.LayoutParams(dp(200), dp(52)));
        previousMonthButton = actionButton("›");
        previousMonthButton.setTextSize(28);
        alignCalendarArrow(previousMonthButton);
        previousMonthButton.setContentDescription("الشهر السابق");
        styleCalendarHeaderControl(previousMonthButton);
        LinearLayout.LayoutParams prevParams = new LinearLayout.LayoutParams(dp(46), dp(46));
        prevParams.setMargins(dp(3), dp(3), dp(1), dp(3));
        monthSwitcher.addView(previousMonthButton, prevParams);
        Button todayButton = actionButton("اليوم");
        todayButton.setTextSize(14);
        styleCalendarHeaderControl(todayButton);
        todayButton.setOnClickListener(v -> {
            displayedMonth = YearMonth.now();
            selectedCalendarDay = LocalDate.now();
            renderMonthCalendar(cachedCalendarEvents());
        });
        LinearLayout.LayoutParams todayParams = new LinearLayout.LayoutParams(0, dp(46), 1);
        todayParams.setMargins(dp(1), dp(3), dp(1), dp(3));
        monthSwitcher.addView(todayButton, todayParams);
        nextMonthButton = actionButton("‹");
        nextMonthButton.setTextSize(28);
        alignCalendarArrow(nextMonthButton);
        nextMonthButton.setContentDescription("الشهر التالي");
        styleCalendarHeaderControl(nextMonthButton);
        nextMonthButton.setId(View.generateViewId());
        LinearLayout.LayoutParams nextParams = new LinearLayout.LayoutParams(dp(46), dp(46));
        nextParams.setMargins(dp(1), dp(3), dp(3), dp(3));
        monthSwitcher.addView(nextMonthButton, nextParams);
        previousMonthButton.setOnClickListener(v -> {
            YearMonth candidate = displayedMonth.minusMonths(1);
            if (!CalendarDates.inRange(candidate, YearMonth.now())) return;
            displayedMonth = candidate;
            selectedCalendarDay = displayedMonth.atDay(1);
            renderMonthCalendar(cachedCalendarEvents());
        });
        nextMonthButton.setOnClickListener(v -> {
            YearMonth candidate = displayedMonth.plusMonths(1);
            if (!CalendarDates.inRange(candidate, YearMonth.now())) return;
            displayedMonth = candidate;
            selectedCalendarDay = displayedMonth.atDay(1);
            renderMonthCalendar(cachedCalendarEvents());
        });

        calendarMonthGrid = new GridLayout(this);
        // Right-to-left: the month reads from the right, as in an Arabic calendar.
        mirrorRow(monthSwitcher);
        mirrorRow(monthHeading);
        calendarMonthGrid.setColumnCount(7);
        calendarMonthGrid.setClipChildren(false);
        calendarMonthGrid.setClipToPadding(false);
        eventsContent.addView(calendarMonthGrid, new LinearLayout.LayoutParams(-1, -2));

    }

    /** Reverses a horizontal row and swaps its children's side margins, for right-to-left layouts. */
    private void mirrorRow(LinearLayout row) {
        ArrayList<View> children = new ArrayList<>();
        for (int i = 0; i < row.getChildCount(); i++) children.add(row.getChildAt(i));
        row.removeAllViews();
        for (int i = children.size() - 1; i >= 0; i--) {
            View child = children.get(i);
            ViewGroup.LayoutParams params = child.getLayoutParams();
            if (params instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams) params;
                int left = margins.leftMargin;
                margins.leftMargin = margins.rightMargin;
                margins.rightMargin = left;
            }
            row.addView(child, params);
        }
    }

    private static final String[] ARABIC_WEEKDAYS = {"الأحد", "الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت"};

    private int calendarEventColor(String title) {
        switch (SaudiCalendar.kindOf(title)) {
            case SaudiCalendar.ISLAMIC: return 0xAA1B6B4A;
            case SaudiCalendar.WHITE_DAYS: return 0x88707C8A;
            case SaudiCalendar.NATIONAL: return 0xAA0F5A35;
            case SaudiCalendar.SCHOOL: return 0xAA7A5A1C;
            default: break;
        }
        String name = title.toLowerCase(Locale.ROOT);
        if (name.contains("ekadashi")) return 0xBB1252AE;
        if (name.contains("purnima")) return 0xAA166E4B;
        if (name.contains("lunch") || name.contains("holiday")) return 0xAA795A1C;
        if (name.contains("navratri") || name.contains("dusshera")) return 0xAA84244F;
        return 0xAA45418D;
    }

    private int calendarEventDotColor(String title) {
        switch (SaudiCalendar.kindOf(title)) {
            case SaudiCalendar.ISLAMIC: return 0xFF5FD39A;
            case SaudiCalendar.WHITE_DAYS: return 0xFFF2F2EC;
            case SaudiCalendar.NATIONAL: return 0xFF3CCB7F;
            case SaudiCalendar.SCHOOL: return 0xFFFFC857;
            default: break;
        }
        String name = title.toLowerCase(Locale.ROOT);
        if (name.contains("ekadashi")) return 0xFF38AAFF;
        if (name.contains("purnima")) return 0xFF27DF7F;
        if (name.contains("lunch") || name.contains("holiday")) return 0xFFFFD438;
        if (name.contains("navratri") || name.contains("dusshera")) return 0xFFFF69AE;
        return 0xFFAD83FF;
    }

    /**
     * Ellipsizes text according to actual Paint measurements.
     *
     * Example:
     *
     * "Waning gibbous" -> "Waning gib…"
     * "Mahatma Gandhi Jayanti" -> "Mahatma Gand…"
     */

    /** A multi-day holiday drawn as one continuous bar under the days it covers. */
    static final class CalendarBar {
        final String label;
        final int color;
        final boolean starts, ends;
        CalendarBar(String label, int color, boolean starts, boolean ends) {
            this.label = label; this.color = color; this.starts = starts; this.ends = ends;
        }
    }

    private void renderMonthCalendar(List<PublicCalendarRepository.Event> events) {
        if (calendarMonthGrid == null) return;
        boolean light = LiquidTheme.isLight(this);
        String monthName = ArabicFormat.month(displayedMonth.getMonthValue());
        SpannableString headingText = new SpannableString(monthName + " " + displayedMonth.getYear());
        headingText.setSpan(new ForegroundColorSpan(light ? LiquidTheme.secondaryText(this) : 0xFFAFC6D2), monthName.length() + 1,
                headingText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        monthTitle.setText(headingText);
        previousMonthButton.setEnabled(CalendarDates.inRange(displayedMonth.minusMonths(1), YearMonth.now()));
        nextMonthButton.setEnabled(CalendarDates.inRange(displayedMonth.plusMonths(1), YearMonth.now()));
        calendarMonthGrid.removeAllViews();
        int widthDp = Math.round(getResources().getDisplayMetrics().widthPixels /
                getResources().getDisplayMetrics().density);
        int cellWidth = Math.max(58, (widthDp - 56) / 7);
        for (int i = 6; i >= 0; i--) {
            TextView heading = label(ARABIC_WEEKDAYS[i],
                    13, light ? LiquidTheme.secondaryText(this) : 0xFFC6D6DB, false);
            heading.setGravity(Gravity.CENTER);
            heading.setLetterSpacing(0f);
            heading.setTypeface(homeTypeface(400));
            heading.setTextSize(14 * homeTextScale());
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = dp(cellWidth);
            params.height = dp(34);
            calendarMonthGrid.addView(heading, params);
        }
        int offset = displayedMonth.atDay(1).getDayOfWeek().getValue() % 7;
        int cells = ((offset + displayedMonth.lengthOfMonth() + 6) / 7) * 7;
        ZoneId zone = ZoneId.systemDefault();
        int screenHeightDp = Math.round(getResources().getDisplayMetrics().heightPixels /
                getResources().getDisplayMetrics().density);
        int rows = cells / 7;
        // «بسيط ٢» has a taller dock, so the grid leaves it more room at the bottom.
        int reserved = 205;
        int fittedHeight = (Math.max(300, screenHeightDp - reserved) - 34) / rows - 7;
        int cellHeight = Math.max(58, Math.min(94, Math.min(cellWidth, fittedHeight)));
        // «بسيط ٢»: a six-week month is a touch shorter per row so its last row clears the taller dock.
        if (rows == 6) cellHeight = 55;
        for (int visual = 0; visual < cells; visual++) {
            int i = (visual / 7) * 7 + (6 - visual % 7);
            int dayNumber = i - offset + 1;
            View cell;
            if (dayNumber < 1 || dayNumber > displayedMonth.lengthOfMonth()) {
                LocalDate outside = displayedMonth.atDay(1).plusDays(dayNumber - 1);
                CalendarDayButton overflow = new CalendarDayButton(this);
                overflow.setContent(outside.getDayOfMonth(), null, false);
                overflow.setHijri(SaudiCalendar.hijriCellLabel(outside));
                overflow.setMuted(true);
                overflow.setFocusable(false);
                overflow.setClickable(false);
                overflow.setBackground(glassTile(false, true));
                cell = overflow;
            } else {
                LocalDate day = displayedMonth.atDay(dayNumber);
                ArrayList<PublicCalendarRepository.Event> dayEvents = new ArrayList<>();
                for (PublicCalendarRepository.Event event : events)
                    if (CalendarDates.includes(event, day, zone)) dayEvents.add(event);
                ArrayList<String> eventTitles = new ArrayList<>();
                ArrayList<CalendarBar> bars = new ArrayList<>();
                for (PublicCalendarRepository.Event event : dayEvents) {
                    int kind = SaudiCalendar.kindOf(event.title);
                    LocalDate first = java.time.Instant.ofEpochMilli(event.startMs).atZone(zone).toLocalDate();
                    LocalDate last = java.time.Instant.ofEpochMilli(event.endMs - 1).atZone(zone).toLocalDate();
                    long span = java.time.temporal.ChronoUnit.DAYS.between(first, last) + 1;
                    boolean asBar = kind == SaudiCalendar.SCHOOL ? span > 1 : kind >= 0 && span > 3;
                    if (!asBar) { eventTitles.add(event.title); continue; }
                    // The name appears once, as a chip on the first day; the rest of the span is a thin line.
                    if (day.equals(first)) eventTitles.add(event.title);
                    bars.add(new CalendarBar("", calendarEventDotColor(event.title),
                            day.equals(first), day.equals(last)));
                }
                CalendarDayButton dateButton = new CalendarDayButton(this);
                dateButton.setContent(dayNumber, eventTitles, day.equals(LocalDate.now()));
                dateButton.setHijri(SaudiCalendar.hijriCellLabel(day));
                dateButton.setBars(bars);
                dateButton.setId(View.generateViewId());
                dateButton.setTag(day);
                StringBuilder dayDescription = new StringBuilder(ArabicFormat.gregorian(day))
                        .append(". ").append(ArabicFormat.hijri(day));
                for (PublicCalendarRepository.Event event : dayEvents)
                    dayDescription.append(". ").append(event.title);
                dateButton.setContentDescription(dayDescription);
                styleCalendarDateButton(dateButton, day.equals(selectedCalendarDay), day.equals(LocalDate.now()),
                        day.getDayOfWeek() == DayOfWeek.FRIDAY || day.getDayOfWeek() == DayOfWeek.SATURDAY);
                dateButton.setOnFocusChangeListener((view, focused) -> {
                    if (focused) selectCalendarDay(day);
                });
                dateButton.setOnHoverListener((view, event) -> {
                    if (event.getActionMasked() == android.view.MotionEvent.ACTION_HOVER_ENTER)
                        updateCalendarHeader(day);
                    else if (event.getActionMasked() == android.view.MotionEvent.ACTION_HOVER_EXIT)
                        updateCalendarHeader(selectedCalendarDay);
                    return false;
                });
                dateButton.setOnClickListener(v -> {
                    selectCalendarDay(day);
                    v.requestFocus();
                });
                cell = dateButton;
            }
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            // «بسيط ٢»: each day is its own glass tile with a small gap around it.
            int gap = dp(3);
            params.width = dp(cellWidth) - 2 * gap;
            params.height = dp(cellHeight) - 2 * gap;
            params.setMargins(gap, gap, gap, gap);
            params.rowSpec = GridLayout.spec(GridLayout.UNDEFINED, GridLayout.TOP);
            calendarMonthGrid.addView(cell, params);
        }
        updateCalendarDaySelection();
        updateCalendarHeader(selectedCalendarDay);
    }

    private void updateCalendarHeader(LocalDate day) {
        if (calendarHeaderDate == null) return;
        calendarHeaderDate.setText(ArabicFormat.gregorian(day) + "  ·  " + ArabicFormat.hijri(day));
        ArrayList<String> titles = new ArrayList<>();
        ZoneId zone = ZoneId.systemDefault();
        for (PublicCalendarRepository.Event event : cachedCalendarEvents()) {
            if (CalendarDates.includes(event, day, zone)) titles.add(event.title);
        }
        calendarHeaderEvents.setText(titles.isEmpty() ? "لا توجد مناسبات" : TextUtils.join(" · ", titles));
    }

    private void selectCalendarDay(LocalDate day) {
        if (!day.equals(selectedCalendarDay)) {
            selectedCalendarDay = day;
            updateCalendarDaySelection();
        }
        updateCalendarHeader(day);
    }

    private void updateCalendarDaySelection() {
        if (calendarMonthGrid == null) return;
        for (int i = 0; i < calendarMonthGrid.getChildCount(); i++) {
            View child = calendarMonthGrid.getChildAt(i);
            if (child instanceof Button && child.getTag() instanceof LocalDate) {
                LocalDate day = (LocalDate) child.getTag();
                styleCalendarDateButton((Button) child, selectedCalendarDay.equals(day), day.equals(LocalDate.now()),
                        day.getDayOfWeek() == DayOfWeek.FRIDAY || day.getDayOfWeek() == DayOfWeek.SATURDAY);
            }
        }
    }

    private void styleCalendarDateButton(Button button, boolean selected, boolean today, boolean weekend) {
        StateListDrawable states = new Motion.States();
        states.addState(new int[]{android.R.attr.state_focused}, glassTile(true, false));
        states.addState(new int[]{android.R.attr.state_pressed}, glassTile(true, false));
        states.addState(new int[]{}, selected || today ? glassTile(true, false) : glassTile(false, false));
        if (!selected && today) states = todayStates();
        button.setBackground(states);
        if (button instanceof CalendarDayButton) ((CalendarDayButton) button).setToday(today);
        button.animate().cancel();
        button.animate()
                .scaleX(1f)
                .scaleY(1f)
                .translationZ(selected ? dp(14) : 0)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .setDuration(android.animation.ValueAnimator.areAnimatorsEnabled() ? 150 : 0)
                .start();
    }

    /** A day in the «بسيط ٢» calendar: dark glass, gold edge when chosen, fainter outside the month. */
    private GradientDrawable glassTile(boolean gold, boolean faint) {
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(dp(12));
        shape.setColor(faint ? 0x66141820 : 0xB3141820);
        shape.setStroke(gold ? dp(2) : Math.max(1, dp(1)), gold ? 0xFFE3B469 : 0x1FFFFFFF);
        return shape;
    }

    private StateListDrawable todayStates() {
        StateListDrawable states = new Motion.States();
        states.addState(new int[]{android.R.attr.state_focused}, glassTile(true, false));
        GradientDrawable today = glassTile(false, false);
        today.setStroke(Math.max(1, dp(1)), 0x99FFFFFF);
        states.addState(new int[]{}, today);
        return states;
    }

    private Drawable calendarGlassShape(int fill, int border, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fill);
        shape.setCornerRadius(dp(radius));
        if (border != Color.TRANSPARENT) shape.setStroke(dp(1), border);
        return shape;
    }

    private void alignCalendarArrow(Button arrow) {
        arrow.setGravity(Gravity.CENTER);
        arrow.setIncludeFontPadding(false);
        // The chevron glyph sits below its font's visual center on Android TV.
        arrow.setPadding(0, 0, 0, dp(5));
    }

    private void styleCalendarHeaderControl(Button button) {
        boolean light = LiquidTheme.isLight(this);
        StateListDrawable states = new Motion.States();
        states.addState(new int[]{-android.R.attr.state_enabled},
                calendarGlassShape(Color.TRANSPARENT, Color.TRANSPARENT, 24));
        states.addState(new int[]{android.R.attr.state_pressed},
                calendarGlassShape(light ? 0xFF087D91 : 0xFFB6FFF0, light ? 0x887C9E9A : 0xFFFFFFFF, 24));
        states.addState(new int[]{android.R.attr.state_focused},
                calendarGlassShape(light ? 0xFF087D91 : 0xFF7BE7F4, light ? 0x887C9E9A : 0xCCFFFFFF, 24));
        states.addState(new int[]{},
                calendarGlassShape(Color.TRANSPARENT, Color.TRANSPARENT, 24));
        button.setBackground(states);
        button.setTextColor(new ColorStateList(new int[][]{
                new int[]{-android.R.attr.state_enabled},
                new int[]{android.R.attr.state_pressed},
                new int[]{android.R.attr.state_focused}, new int[]{}
        }, light ? new int[]{0x55132B2B, Color.WHITE, Color.WHITE, LiquidTheme.text(this)}
                 : new int[]{0x55FFFFFF, 0xFF071417, 0xFF071417, Color.WHITE}));
        button.setOnFocusChangeListener(LiquidTheme::animateContainedFocus);
    }

    private int heroHeightDp() {
        float density = getResources().getDisplayMetrics().density;
        int screenHeightDp = Math.round(getResources().getDisplayMetrics().heightPixels / density);
        return screenHeightDp;
    }

    private ImageView backgroundImage() {
        ImageView image = new BackdropImageView(this);
        image.setVisibility(View.GONE);
        return image;
    }
    private final class CalendarDayButton extends Button {

    private int dayNumber;
    private String hijriLabel = "";
    private final ArrayList<CalendarBar> bars = new ArrayList<>();
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hijriPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ArrayList<String> eventTitles = new ArrayList<>();
    private boolean today, muted;

    private final Paint datePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint weekdayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint moonPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint eventPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint chipPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint chipTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint todayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    CalendarDayButton(Context context) {
        super(context);

        setText("");
        setAllCaps(false);

        setFocusable(true);
        setFocusableInTouchMode(false);
        setClickable(true);

        setMinWidth(0);
        setMinimumWidth(0);
        setMinHeight(0);
        setMinimumHeight(0);

        // Compact TV calendar cell.
        setPadding(dp(12), dp(6), dp(10), dp(2));

        configurePaints();
    }

    private void configurePaints() {

        // «بسيط ٢» draws the days in the home font, a little larger; «بسيط ١» keeps its own look.
        boolean glass = true;
        float scale = glass ? homeTextScale() : 1f;
        android.graphics.Typeface regular = glass ? homeTypeface(400) : LiquidTheme.font(MainActivity.this);
        android.graphics.Typeface number = glass ? homeTypeface(300) : LiquidTheme.font(MainActivity.this);

        // Large date number.
        datePaint.setTextSize(sp(glass ? 21 * Math.min(scale, 1.15f) : 20));
        datePaint.setTypeface(number);

        // MON / TUE / WED...
        weekdayPaint.setTextSize(sp(glass ? 10.5f * scale : 9));
        weekdayPaint.setTypeface(regular);

        // Moon phase (the Hijri day).
        moonPaint.setTextSize(sp(glass ? 10.5f * scale : 9));
        moonPaint.setTypeface(regular);

        // Holiday/event.
        eventPaint.setTextSize(sp(glass ? 10 * scale : 8));
        eventPaint.setTypeface(regular);
        chipTextPaint.setTextSize(sp(glass ? 10.5f * scale : 9));
        chipTextPaint.setTypeface(regular);
        chipTextPaint.setColor(Color.WHITE);

        todayPaint.setStyle(Paint.Style.FILL);
    }

    public void setContent(int dayNumber, List<String> events, boolean today) {
        this.dayNumber = dayNumber;
        this.eventTitles.clear();
        if (events != null) for (String event : events) {
            if (event != null && !event.trim().isEmpty() && this.eventTitles.size() < 3)
                this.eventTitles.add(event.trim());
        }
        this.today = today;

        invalidate();
    }

    /** A day of the month before or after, shown faintly to fill the week. */
    public void setMuted(boolean value) { muted = value; invalidate(); }

    public void setBars(List<CalendarBar> values) {
        bars.clear();
        if (values != null) bars.addAll(values);
        invalidate();
    }

    public void setHijri(String label) {
        hijriLabel = label == null ? "" : label;
        invalidate();
    }

    public void setToday(boolean today) {
        if (this.today == today) {
            return;
        }

        this.today = today;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {

        /*
         * Important:
         * Don't call super.onDraw().
         *
         * Button has no normal text. We draw all content ourselves.
         */

        int save = canvas.save();

        /*
         * Absolutely prevent event/moon text from escaping
         * outside the rounded calendar tile.
         */
        canvas.clipRect(
                getPaddingLeft(),
                getPaddingTop(),
                getWidth() - getPaddingRight(),
                getHeight() - getPaddingBottom()
        );

        final int primary = LiquidTheme.text(MainActivity.this);
        final int secondary = LiquidTheme.secondaryText(MainActivity.this);
        datePaint.setColor(primary);

        final float left = getPaddingLeft();
        final float right = getWidth() - getPaddingRight();

        // Right-to-left cell: Gregorian day on the right, Hijri day on the left.
        Paint.FontMetrics dateFm = datePaint.getFontMetrics();
        float row1Baseline = getPaddingTop() - dateFm.ascent;
        String number = String.valueOf(dayNumber);
        float numberWidth = datePaint.measureText(number);
        if (muted) datePaint.setColor(LiquidTheme.isLight(MainActivity.this) ? 0x664D6968 : 0x8078818A);
        if (today) {
            // Today: the date sits centred in a gold disc that stays wholly inside the tile.
            float radius = Math.max(numberWidth, -dateFm.ascent * 0.72f) / 2f + dp(5);
            float cy = row1Baseline + (dateFm.ascent * 0.72f) / 2f;
            float shift = Math.max(0, getPaddingTop() + dp(2) - (cy - radius));
            cy += shift;
            row1Baseline += shift;
            float cx = right - dp(2) - radius;
            todayPaint.setColor(0xFFE3B469);
            canvas.drawCircle(cx, cy, radius, todayPaint);
            datePaint.setColor(0xFF1B2024);
            datePaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(number, cx, cy - (dateFm.ascent * 0.72f) / 2f, datePaint);
            numberWidth = 2 * radius - dp(2);
        } else {
            datePaint.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(number, right, row1Baseline, datePaint);
        }
        datePaint.setColor(primary);
        datePaint.setTextAlign(Paint.Align.LEFT);

        boolean glass = true;
        if (!hijriLabel.isEmpty()) {
            // The first day of a Hijri month carries the month's name and a small gold crescent.
            boolean monthStart = hijriLabel.indexOf(' ') > 0;
            hijriPaint.setTextSize(glass ? sp(10.5f * Math.min(homeTextScale(), 1.2f)) : sp(11));
            if (glass) hijriPaint.setTypeface(homeTypeface(400));
            hijriPaint.setColor(muted ? (secondary & 0x00FFFFFF) | 0x66000000 : monthStart ? 0xFFE3B469 : secondary);
            hijriPaint.setTextAlign(Paint.Align.LEFT);
            float textLeft = left + dp(2);
            if (monthStart) {
                float size = hijriPaint.getTextSize() * 0.95f;
                drawCrescent(canvas, textLeft + size / 2f, row1Baseline - size * 0.4f, size / 2f, 0xFFE3B469);
                textLeft += size + dp(3);
            }
            canvas.drawText(ellipsize(hijriLabel, hijriPaint, Math.max(0, right - textLeft
                    - numberWidth - dp(8))), textLeft, row1Baseline, hijriPaint);
        }


        float barHeight = dp(glass ? 3 : 4);
        float chipLimit = getHeight() - getPaddingBottom() - dp(2) - bars.size() * (barHeight + dp(2));

        // Single-day occasions as chips: dot on the right, text reading right to left.
        float chipTop = row1Baseline + dp(3);
        // In «بسيط ٢» the chip grows with the chosen font so its text stays inside it.
        float chipHeight = glass ? Math.max(dp(13), chipTextPaint.getTextSize() * 1.3f + dp(2)) : dp(13);
        int fits = (int) Math.max(0, Math.floor((chipLimit - chipTop + dp(2)) / (chipHeight + dp(2))));
        // When not all occasions fit, the last chip that fits keeps its title and carries a small "+N"
        // count on its left, so no occasion is silently lost and the first ones stay readable.
        int shownChips = Math.min(fits, eventTitles.size());
        int hiddenChips = eventTitles.size() - shownChips;
        for (int i = 0; i < eventTitles.size(); i++) {
            if (chipTop + chipHeight > chipLimit) break;
            if (i >= shownChips) break;
            String eventTitle = eventTitles.get(i);
            boolean carriesCount = glass && hiddenChips > 0 && i == shownChips - 1;
            chipPaint.setColor(calendarEventColor(eventTitle));
            canvas.drawRoundRect(left, chipTop, right, chipTop + chipHeight, dp(7), dp(7), chipPaint);
            todayPaint.setColor(calendarEventDotColor(eventTitle));
            canvas.drawCircle(right - dp(6), chipTop + chipHeight / 2f, dp(3), todayPaint);
            float countWidth = 0;
            if (carriesCount) {
                // A small round "+N" badge at the chip's left end.
                String more = "+" + ArabicFormat.digits(hiddenChips);
                countWidth = chipTextPaint.measureText(more) + dp(8);
                todayPaint.setColor(0xE6FFFFFF);
                canvas.drawRoundRect(left + dp(2), chipTop + dp(2), left + dp(2) + countWidth, chipTop + chipHeight - dp(2),
                        dp(6), dp(6), todayPaint);
                int keep = chipTextPaint.getColor();
                chipTextPaint.setColor(0xFF14181E);
                chipTextPaint.setTextAlign(Paint.Align.CENTER);
                canvas.drawText(more, left + dp(2) + countWidth / 2f, chipTop + chipHeight / 2f
                        + chipTextPaint.getTextSize() * 0.35f, chipTextPaint);
                chipTextPaint.setTextAlign(Paint.Align.LEFT);
                chipTextPaint.setColor(keep);
                countWidth += dp(4);
            }
            String title = ellipsize(eventTitle, chipTextPaint, right - left - dp(20) - countWidth);
            Paint.FontMetrics chipMetrics = chipTextPaint.getFontMetrics();
            // Glass: centre on the letters themselves, since some fonts reserve a lot of space above them.
            float baseline = glass ? chipTop + chipHeight / 2f + chipTextPaint.getTextSize() * 0.35f
                    : chipTop + (chipHeight - chipMetrics.bottom + chipMetrics.top) / 2f - chipMetrics.top;
            chipTextPaint.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(title, right - dp(12), baseline, chipTextPaint);
            chipTextPaint.setTextAlign(Paint.Align.LEFT);
            chipTop += chipHeight + dp(2);
        }

        if (glass) {
            // «بسيط ٢»: each day is its own tile, so a multi-day line stays inside the tile, with rounded ends.
            float barBottom = getHeight() - getPaddingBottom() - dp(2);
            for (int i = bars.size() - 1; i >= 0; i--) {
                CalendarBar bar = bars.get(i);
                barPaint.setColor(bar.color);
                canvas.drawRoundRect(left, barBottom - barHeight, right, barBottom, barHeight / 2f, barHeight / 2f, barPaint);
                barBottom -= barHeight + dp(2);
            }
            canvas.restoreToCount(save);
            return;
        }
        canvas.restoreToCount(save);

        // Bars are drawn outside the padding clip so neighbouring days join into one line.
        // Multi-day holidays: one bar per holiday along the bottom, labelled where it starts.
        float barBottom = getHeight() - getPaddingBottom() - dp(2);
        for (int i = bars.size() - 1; i >= 0; i--) {
            CalendarBar bar = bars.get(i);
            float top = barBottom - barHeight;
            // Bars run edge to edge so neighbouring days join up; ends are rounded.
            float barRight = bar.starts ? right : getWidth();
            float barLeft = bar.ends ? left : 0;
            barPaint.setColor(bar.color);
            float r = dp(2), startR = bar.starts ? r : 0, endR = bar.ends ? r : 0;
            // Corner radii: top-left, top-right, bottom-right, bottom-left (x, y pairs).
            float[] radii = {endR, endR, startR, startR, startR, startR, endR, endR};
            Path barPath = new Path();
            barPath.addRoundRect(new RectF(barLeft, top, barRight, barBottom), radii, Path.Direction.CW);
            canvas.drawPath(barPath, barPaint);
            if (!bar.label.isEmpty()) {
                chipTextPaint.setTextAlign(Paint.Align.RIGHT);
                String title = ellipsize(bar.label, chipTextPaint, right - left - dp(8));
                Paint.FontMetrics metrics = chipTextPaint.getFontMetrics();
                float baseline = top + (barHeight - metrics.bottom + metrics.top) / 2f - metrics.top;
                canvas.drawText(title, right - dp(6), baseline, chipTextPaint);
                chipTextPaint.setTextAlign(Paint.Align.LEFT);
            }
            barBottom = top - dp(2);
        }

    }

    private void drawCrescent(Canvas canvas, float cx, float cy, float radius, int color) {
        Path moon = new Path(), cut = new Path();
        moon.addCircle(cx, cy, radius, Path.Direction.CW);
        cut.addCircle(cx + radius * 0.45f, cy - radius * 0.3f, radius * 0.85f, Path.Direction.CW);
        moon.op(cut, Path.Op.DIFFERENCE);
        todayPaint.setColor(color);
        canvas.drawPath(moon, todayPaint);
    }

    private String ellipsize(
            String text,
            Paint paint,
            float availableWidth) {

        if (text == null || text.isEmpty()) {
            return "";
        }

        if (availableWidth <= 0) {
            return "";
        }

        if (paint.measureText(text) <= availableWidth) {
            return text;
        }

        final String ellipsis = "…";

        float ellipsisWidth =
                paint.measureText(ellipsis);

        int end = text.length();

        while (end > 0) {

            float width =
                    paint.measureText(
                            text,
                            0,
                            end
                    );

            if (width + ellipsisWidth <= availableWidth) {
                break;
            }

            end--;
        }

        if (end <= 0) {
            return ellipsis;
        }

        return text
                .substring(0, end)
                .trim()
                + ellipsis;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private float sp(float value) {
        return value *
                getResources()
                        .getDisplayMetrics()
                        .scaledDensity;
    }
}

    private static final class BackdropImageView extends ImageView {
        private final Matrix cropMatrix = new Matrix();

        BackdropImageView(Context context) {
            super(context);
            setScaleType(ImageView.ScaleType.MATRIX);
        }

        @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
            super.onSizeChanged(width, height, oldWidth, oldHeight);
            updateTopCrop();
        }

        @Override public void setImageDrawable(Drawable drawable) {
            super.setImageDrawable(drawable);
            updateTopCrop();
        }

        @Override public void setImageBitmap(android.graphics.Bitmap bitmap) {
            super.setImageBitmap(bitmap);
            updateTopCrop();
        }

        @Override public void setImageResource(int resource) {
            super.setImageResource(resource);
            updateTopCrop();
        }

        private void updateTopCrop() {
            if (cropMatrix == null) return;
            Drawable drawable = getDrawable();
            if (drawable == null || getWidth() <= 0 || getHeight() <= 0
                    || drawable.getIntrinsicWidth() <= 0 || drawable.getIntrinsicHeight() <= 0) return;
            float scale = Math.max((float) getWidth() / drawable.getIntrinsicWidth(),
                    (float) getHeight() / drawable.getIntrinsicHeight());
            float horizontalOffset = (getWidth() - drawable.getIntrinsicWidth() * scale) / 2f;
            float verticalOffset = (getHeight() - drawable.getIntrinsicHeight() * scale) * 0.30f;
            cropMatrix.setScale(scale, scale);
            cropMatrix.postTranslate(horizontalOffset, verticalOffset);
            setImageMatrix(cropMatrix);
        }
    }

    private TextView label(String value, float size, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(size);
        if (LiquidTheme.isLight(this)) {
            if (color == Color.WHITE || color == 0xFFF4F6FA) color = LiquidTheme.text(this);
        }
        v.setTextColor(color);
        v.setTypeface(LiquidTheme.font(this));
        return v;
    }

    private Drawable tileShape(int color, int strokeColor, int strokeWidth) {
        return roundedShape(color, strokeColor, strokeWidth, 18);
    }

    private void styleFocusableTile(View tile) {
        StateListDrawable states = new Motion.States();
        states.addState(new int[]{android.R.attr.state_focused}, tileShape(LiquidTheme.raisedSurface(this), LiquidTheme.accent(this), 2));
        states.addState(new int[]{android.R.attr.state_pressed}, tileShape(LiquidTheme.raisedSurface(this), LiquidTheme.accent(this), 2));
        states.addState(new int[]{}, tileShape(LiquidTheme.surface(this), LiquidTheme.border(this), 1));
        tile.setBackground(states);
        tile.setFocusable(true);
        tile.setFocusableInTouchMode(false);
        tile.setClickable(true);
        tile.setOnFocusChangeListener(LiquidTheme::animateFocus);
        Motion.interaction(tile, false);
    }


    private Button actionButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTypeface(LiquidTheme.font(this));
        b.setTextSize(14);
        b.setTextColor(LiquidTheme.text(this));
        b.setAllCaps(false);
        b.setFocusable(true);
        styleFocusableTile(b);
        return b;
    }

    private boolean hasWeatherLocation() {
        return WeatherRepository.validCoordinates(Double.longBitsToDouble(preferences.getLong("weatherLatitude", Double.doubleToRawLongBits(Double.NaN))),
                Double.longBitsToDouble(preferences.getLong("weatherLongitude", Double.doubleToRawLongBits(Double.NaN))));
    }

    private WeatherRepository.Location savedWeatherLocation() {
        double latitude = Double.longBitsToDouble(preferences.getLong("weatherLatitude", Double.doubleToRawLongBits(Double.NaN)));
        double longitude = Double.longBitsToDouble(preferences.getLong("weatherLongitude", Double.doubleToRawLongBits(Double.NaN)));
        if (!WeatherRepository.validCoordinates(latitude, longitude)) return null;
        return new WeatherRepository.Location(preferences.getString("weatherLocationLabel", "Weather"), latitude, longitude);
    }

    private WeatherRepository.Forecast cachedWeather() {
        try { return WeatherRepository.Forecast.fromJson(new JSONObject(preferences.getString("weatherCache", ""))); }
        catch (Exception ignored) { return null; }
    }
    private void renderWeather() {
        if (modernHome != null) modernHome.setWeather(weatherForecast);
    }


    private void maybeRefreshWeather(boolean manual) {
        WeatherRepository.Location location = savedWeatherLocation();
        if (location == null || weatherRefreshing) return;
        long interval = scheduleValue(SchedulePreferences.WEATHER, SchedulePreferences.WEATHER_VALUES,
                SchedulePreferences.DEFAULT_WEATHER);
        if (!manual) {
            if (interval == SchedulePreferences.MANUAL) return;
            if (interval == SchedulePreferences.EVERY_LAUNCH) {
                if (weatherRefreshedThisLaunch) return;
                weatherRefreshedThisLaunch = true;
            } else {
                long last = Math.max(preferences.getLong("weatherUpdatedAt", 0), preferences.getLong("weatherAttemptedAt", 0));
                if (System.currentTimeMillis() - last < interval) return;
            }
        }
        refreshWeather(location);
    }

    private void refreshWeather(WeatherRepository.Location location) {
        weatherRefreshing = true;
        preferences.edit().putLong("weatherAttemptedAt", System.currentTimeMillis()).apply();
        int generation = ++weatherRequestGeneration;
        lifecycleWork.execute(ioWorker, () -> {
            WeatherRepository.Forecast forecast = null; String error = null;
            try { forecast = WeatherRepository.fetch(location, System.currentTimeMillis()); }
            catch (Exception failure) { error = failure.getMessage() == null ? "Could not refresh weather." : failure.getMessage(); }
            WeatherRepository.Forecast result = forecast; String resultError = error;
            lifecycleWork.post(() -> {
                if (generation != weatherRequestGeneration || isFinishing() || isDestroyed()) return;
                weatherRefreshing = false;
                if (result != null) {
                    weatherForecast = result;
                    try { preferences.edit().putString("weatherCache", result.toJson().toString())
                            .putLong("weatherUpdatedAt", result.fetchedAt).apply(); } catch (Exception ignored) { }
                    renderWeather();
                }
                scheduleWeatherRefresh();
            });
        });
    }

    private void scheduleWeatherRefresh() {
        if (weatherRefreshTick == null) return;
        handler.removeCallbacks(weatherRefreshTick);
        if (!screenActive || !hasWeatherLocation()) return;
        long interval = scheduleValue(SchedulePreferences.WEATHER, SchedulePreferences.WEATHER_VALUES,
                SchedulePreferences.DEFAULT_WEATHER);
        if (interval <= 0) return;
        long last = Math.max(preferences.getLong("weatherUpdatedAt", 0), preferences.getLong("weatherAttemptedAt", 0));
        handler.postDelayed(weatherRefreshTick, Math.max(1000L, interval - Math.max(0L, System.currentTimeMillis() - last)));
    }

    private void saveWeatherLocation(String mode, WeatherRepository.Location location) {
        weatherRequestGeneration++;
        weatherRefreshing = false;
        WeatherRepository.Location previous = savedWeatherLocation();
        boolean changed = previous == null || Math.abs(previous.latitude - location.latitude) > .0001
                || Math.abs(previous.longitude - location.longitude) > .0001;
        SharedPreferences.Editor editor = preferences.edit().putString("weatherLocationMode", mode).putString("weatherLocationLabel", location.label)
                .putLong("weatherLatitude", Double.doubleToRawLongBits(location.latitude))
                .putLong("weatherLongitude", Double.doubleToRawLongBits(location.longitude));
        if (changed) editor.remove("weatherCache").remove("weatherUpdatedAt");
        editor.apply();
        if (changed) weatherForecast = null;
        weatherRefreshedThisLaunch = true; renderWeather(); refreshWeather(location);
    }

    private List<PublicCalendarRepository.Event> cachedCalendarEvents() {
        ArrayList<PublicCalendarRepository.Event> events = new ArrayList<>();
        try {
            JSONArray values = new JSONArray(preferences.getString("publicCalendarEvents", "[]"));
            for (int i = 0; i < values.length(); i++) {
                JSONObject value = values.optJSONObject(i);
                if (value == null) continue;
                long start = value.optLong("start", 0L), end = value.optLong("end", 0L);
                if (end > start) events.add(new PublicCalendarRepository.Event(
                        value.optString("title", "Calendar event"), start, end,
                        value.optBoolean("allDay", false)));
            }
        } catch (Exception ignored) { }
        events.addAll(saudiCalendarEvents());
        Collections.sort(events, (a, b) -> Long.compare(a.startMs, b.startMs));
        return events;
    }

    private LocalDate saudiEventsDay;
    private List<PublicCalendarRepository.Event> saudiEvents = new ArrayList<>();

    /** Islamic occasions, white days, national days and school holidays, rebuilt once a day. */
    private List<PublicCalendarRepository.Event> saudiCalendarEvents() {
        LocalDate today = LocalDate.now();
        if (today.equals(saudiEventsDay)) return saudiEvents;
        ZoneId zone = ZoneId.systemDefault();
        ArrayList<PublicCalendarRepository.Event> generated = new ArrayList<>();
        for (SaudiCalendar.Item item : SaudiCalendar.between(today.minusMonths(13), today.plusMonths(13))) {
            long start = item.start.atStartOfDay(zone).toInstant().toEpochMilli();
            long end = item.endInclusive.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
            generated.add(new PublicCalendarRepository.Event(item.title, start, end, true));
        }
        saudiEvents = generated;
        saudiEventsDay = today;
        return saudiEvents;
    }

    private void saveCalendarEvents(List<PublicCalendarRepository.Event> events) {
        JSONArray values = new JSONArray();
        for (PublicCalendarRepository.Event event : events) {
            JSONObject value = new JSONObject();
            try {
                value.put("title", event.title);
                value.put("start", event.startMs);
                value.put("end", event.endMs);
                value.put("allDay", event.allDay);
                values.put(value);
            } catch (Exception ignored) { }
        }
        preferences.edit().putString("publicCalendarEvents", values.toString())
                .putLong("publicCalendarUpdatedAt", System.currentTimeMillis())
                .putInt("publicCalendarCacheVersion", CALENDAR_CACHE_VERSION).apply();
    }
    private void updateCalendarEventDisplay() {
        updateOccasion();
        if (calendarMonthGrid != null) renderMonthCalendar(cachedCalendarEvents());
    }

    /** Today's occasion, or the next one, for the home card. */
    private void updateOccasion() {
        if (modernHome == null) return;
        LocalDate today = LocalDate.now();
        PublicCalendarRepository.Event todayEvent = null, nextEvent = null;
        for (PublicCalendarRepository.Event event : cachedCalendarEvents()) {
            if (CalendarDates.includes(event, today, ZoneId.systemDefault())) { todayEvent = event; break; }
            if (event.startMs > System.currentTimeMillis()) { nextEvent = event; break; }
        }
        if (todayEvent != null) {
            modernHome.setOccasion("مناسبة اليوم", todayEvent.title, todayEvent.allDay ? ArabicFormat.hijri(today)
                    : new java.text.SimpleDateFormat("h:mm a", Locale.getDefault()).format(new Date(todayEvent.startMs)));
        } else if (nextEvent != null) {
            modernHome.setOccasion("المناسبة القادمة", nextEvent.title, nextEvent.allDay
                    ? ArabicFormat.gregorian(java.time.Instant.ofEpochMilli(nextEvent.startMs).atZone(ZoneId.systemDefault()).toLocalDate())
                    : new java.text.SimpleDateFormat("EEE · h:mm a", Locale.getDefault()).format(new Date(nextEvent.startMs)));
        } else {
            modernHome.setOccasion("المناسبة القادمة", "", "");
        }
    }


    private void maybeRefreshPublicCalendar(boolean manual) {
        String url = preferences.getString("publicCalendarUrl", "");
        if (url.isEmpty() || url.equals(calendarRefreshingUrl)) return;
        long interval = scheduleValue(SchedulePreferences.CALENDAR, SchedulePreferences.CALENDAR_VALUES,
                SchedulePreferences.DEFAULT_CALENDAR);
        if (!manual && interval <= 0) return;
        long lastUpdated = preferences.getLong("publicCalendarUpdatedAt", 0L);
        long lastAttempted = preferences.getLong("publicCalendarAttemptedAt", lastUpdated);
        boolean cacheNeedsUpgrade = preferences.getInt("publicCalendarCacheVersion", 0) < CALENDAR_CACHE_VERSION
                && preferences.getInt("publicCalendarAttemptedVersion", 0) < CALENDAR_CACHE_VERSION;
        if (!manual && !cacheNeedsUpgrade
                && System.currentTimeMillis() - Math.max(lastUpdated, lastAttempted) < interval) return;
        refreshPublicCalendar(url, manual);
    }

    private void refreshPublicCalendar(String url, boolean manual) {
        if (url.equals(calendarRefreshingUrl)) return;
        calendarRefreshingUrl = url;
        preferences.edit().putLong("publicCalendarAttemptedAt", System.currentTimeMillis())
                .putInt("publicCalendarAttemptedVersion", CALENDAR_CACHE_VERSION).apply();
        int generation = ++calendarRequestGeneration;
        lifecycleWork.execute(calendarWorker, () -> {
            String error = null;
            List<PublicCalendarRepository.Event> events = null;
            try {
                String feed = PublicCalendarRepository.download(url);
                events = PublicCalendarRepository.parse(feed, System.currentTimeMillis());
            } catch (Exception failure) {
                error = failure.getMessage() == null ? "Could not refresh this calendar." : failure.getMessage();
            }
            String resultError = error;
            List<PublicCalendarRepository.Event> resultEvents = events;
            lifecycleWork.post(() -> {
                if (generation != calendarRequestGeneration) return;
                calendarRefreshingUrl = "";
                if (isFinishing() || !url.equals(preferences.getString("publicCalendarUrl", ""))) return;
                if (resultError == null && resultEvents != null) {
                    saveCalendarEvents(resultEvents);
                    updateCalendarEventDisplay();
                }
                if (manual && resultError != null) {
                    Toast.makeText(this, "Calendar refresh failed: " + resultError, Toast.LENGTH_LONG).show();
                }
                scheduleCalendarRefresh();
            });
        });
    }

    private void scheduleCalendarRefresh() {
        if (calendarRefreshTick == null) return;
        handler.removeCallbacks(calendarRefreshTick);
        if (!screenActive || preferences.getString("publicCalendarUrl", "").isEmpty()) return;
        long interval = scheduleValue(SchedulePreferences.CALENDAR, SchedulePreferences.CALENDAR_VALUES,
                SchedulePreferences.DEFAULT_CALENDAR);
        if (interval <= 0) return;
        long updated = preferences.getLong("publicCalendarUpdatedAt", 0L);
        long attempted = preferences.getLong("publicCalendarAttemptedAt", updated);
        long elapsed = Math.max(0L, System.currentTimeMillis() - Math.max(updated, attempted));
        handler.postDelayed(calendarRefreshTick, Math.max(1000L, interval - elapsed));
    }
    private void renderApps() {
        if (modernHome == null || appsLoading) return;
        appsLoading = true;
        lifecycleWork.execute(ioWorker, () -> {
            List<AppEntry> loaded = installedApps();
            List<String> selected = selectedPackages(loaded);
            if (selected.isEmpty() && !preferences.getBoolean("customApps", false))
                selected.addAll(defaultApps(loaded));
            Set<String> selectedSet = new HashSet<>(selected);
            PackageManager packageManager = getPackageManager();
            ArrayList<AppEntry> withIcons = new ArrayList<>(loaded.size());
            for (AppEntry entry : loaded) {
                Drawable icon = null;
                if (selectedSet.contains(entry.packageName)) {
                    try { icon = packageManager.getApplicationIcon(entry.info); } catch (Exception ignored) { }
                }
                withIcons.add(new AppEntry(entry.info, entry.packageName, entry.label, icon));
            }
            lifecycleWork.post(() -> {
                appsLoading = false;
                if (!isFinishing()) showModernApps(withIcons, selected);
            });
        });
    }


    private boolean isLauncherOverrideEnabled() {
        AccessibilityManager manager = (AccessibilityManager) getSystemService(ACCESSIBILITY_SERVICE);
        if (manager == null) return false;
        String expectedPackage = getPackageName();
        String expectedClass = LauncherOverrideService.class.getName();
        for (AccessibilityServiceInfo info : manager.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_ALL_MASK)) {
            if (info.getResolveInfo() == null || info.getResolveInfo().serviceInfo == null) continue;
            if (expectedPackage.equals(info.getResolveInfo().serviceInfo.packageName)
                    && expectedClass.equals(info.getResolveInfo().serviceInfo.name)) return true;
        }
        return false;
    }

    private Drawable roundedShape(int color, int strokeColor, int strokeWidth, int radiusDp) {
        return new GlassDrawable(this, glassBackdrop, color, strokeColor, strokeWidth, Math.max(16, radiusDp));
    }

    private void launchApp(String packageName) {
        Intent launch = getPackageManager().getLeanbackLaunchIntentForPackage(packageName);
        if (launch == null) launch = getPackageManager().getLaunchIntentForPackage(packageName);
        if (launch != null) try { startActivity(launch); } catch (Exception ignored) { }
    }

    private static final class AppEntry {
        final ApplicationInfo info;
        final String packageName, label;
        final Drawable icon;
        AppEntry(ApplicationInfo info, String packageName, String label, Drawable icon) { this.info = info; this.packageName = packageName; this.label = label; this.icon = icon; }
    }

    private List<AppEntry> installedApps() {
        ArrayList<AppEntry> entries = new ArrayList<>();
        PackageManager pm = getPackageManager();
        for (ApplicationInfo info : pm.getInstalledApplications(PackageManager.GET_META_DATA)) {
            if (info.packageName.equals(getPackageName())) continue;
            Intent launch = pm.getLeanbackLaunchIntentForPackage(info.packageName);
            if (launch == null) launch = pm.getLaunchIntentForPackage(info.packageName);
            if (launch == null) continue;
            String label = String.valueOf(pm.getApplicationLabel(info));
            entries.add(new AppEntry(info, info.packageName, label, null));
        }
        Collections.sort(entries, (a, b) -> a.label.compareToIgnoreCase(b.label));
        return entries;
    }

    private List<String> selectedPackages(List<AppEntry> available) {
        String raw = preferences.getString("selectedApps", "");
        ArrayList<String> selected = new ArrayList<>();
        if (!raw.isEmpty()) for (String pkg : raw.split("\\n")) if (findApp(available, pkg) != null && !selected.contains(pkg)) selected.add(pkg);
        return selected;
    }

    /** The Saudi Quran channel's official YouTube channel, live from Masjid al-Haram. */
    private static final String HARAM_CHANNEL = "UCos52azQNBgW63_9uDJoPDA";

    /**
     * Opens the official live broadcast in the YouTube app, which plays it in Full HD or 4K.
     * If YouTube or the network is unavailable, the built-in player is used instead.
     */
    private void openHaramLive() {
        lifecycleWork.execute(ioWorker, () -> {
            String videoId = null;
            try {
                java.net.HttpURLConnection connection = (java.net.HttpURLConnection)
                        new java.net.URL("https://www.youtube.com/channel/" + HARAM_CHANNEL + "/live").openConnection();
                connection.setConnectTimeout(6000);
                connection.setReadTimeout(8000);
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/128.0 Mobile Safari/537.36");
                connection.setRequestProperty("Accept-Language", "ar,en;q=0.8");
                try (java.io.InputStream input = connection.getInputStream()) {
                    java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                    byte[] buffer = new byte[16384];
                    int read;
                    while ((read = input.read(buffer)) != -1 && out.size() < 2_000_000) out.write(buffer, 0, read);
                    String html = out.toString("UTF-8");
                    java.util.regex.Matcher m = java.util.regex.Pattern
                            .compile("rel=\"canonical\" href=\"https://www\\.youtube\\.com/watch\\?v=([A-Za-z0-9_-]{11})").matcher(html);
                    // Only a live broadcast page has a watch URL as its canonical link.
                    if (m.find()) videoId = m.group(1);
                } finally {
                    connection.disconnect();
                }
            } catch (Exception ignored) { }
            final String id = videoId;
            lifecycleWork.post(() -> {
                if (id != null && launchYouTubeVideo(id)) return;
                selectTab(TAB_HARAM, false);
            });
        });
    }

    private boolean launchYouTubeVideo(String id) {
        Uri uri = Uri.parse("https://www.youtube.com/watch?v=" + id);
        for (String pkg : new String[]{"com.google.android.youtube.tv", "com.google.android.youtube", null}) {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                if (pkg != null) intent.setPackage(pkg);
                startActivity(intent);
                return true;
            } catch (Exception ignored) { }
        }
        return false;
    }

    /** Focus outline for the home tiles: the same gold as the icons. */
    private static final int GOLD_FOCUS = 0xFFE3B469;

    /** Until apps are chosen in Settings, Home shows only YouTube and Stremio. */
    private static final String[] DEFAULT_APPS = {
            "com.google.android.youtube.tv", "com.google.android.youtube", "com.stremio.one"};

    private List<String> defaultApps(List<AppEntry> available) {
        ArrayList<String> result = new ArrayList<>();
        boolean youtube = false;
        for (String pkg : DEFAULT_APPS) {
            if (findApp(available, pkg) == null) continue;
            // Show one YouTube: the TV app when it is installed.
            if (pkg.startsWith("com.google.android.youtube")) { if (youtube) continue; youtube = true; }
            result.add(pkg);
        }
        return result;
    }

    private AppEntry findApp(List<AppEntry> all, String pkg) {
        for (AppEntry entry : all) if (entry.packageName.equals(pkg)) return entry;
        return null;
    }

    private boolean sameBackgroundPhotos(List<BackgroundPhoto> photos) {
        if (backgroundPhotos.size() != photos.size()) return false;
        for (int i = 0; i < photos.size(); i++) {
            if (!backgroundPhotos.get(i).uri.equals(photos.get(i).uri)) return false;
        }
        return true;
    }

    private void setBackgroundPhotos(List<BackgroundPhoto> photos) {
        backgroundBitmapCache.evictAll();
        backgroundPhotos.clear();
        backgroundPhotos.addAll(photos);
        backgroundIndex = 0;
        backgroundImageGeneration++;
        slideA.animate().cancel();
        slideB.animate().cancel();
        slideA.setImageDrawable(null);
        slideB.setImageDrawable(null);
        slideA.setVisibility(View.GONE);
        slideB.setVisibility(View.GONE);
        slideAActive = true;
        wallpaperImage.setVisibility(backgroundPhotos.isEmpty() ? View.VISIBLE : View.GONE);
        if (backgroundPhotos.isEmpty()) applyWallpaper();
        if (slideshowTick != null) handler.removeCallbacks(slideshowTick);
        if (backgroundPhotos.isEmpty()) { slideshowTick = null; return; }
        slideshowTick = this::showNextBackgroundPhoto;
        if (screenActive) showNextBackgroundPhoto();
    }

    private boolean moodGalleryActive;

    /** Applies the chosen background mood. */
    private void applyMood() {
        String mood = WallpaperMoods.current(preferences);
        if (WallpaperMoods.CALM.equals(mood)) {
            moodGalleryActive = false;
            setBackgroundPhotos(new ArrayList<>()); // Shows the calm background.
            return;
        }
        moodGalleryActive = true;
        List<String> cached = WallpaperMoods.cached(preferences, mood);
        if (!cached.isEmpty()) setBackgroundPhotos(moodPhotos(cached));
        if (!WallpaperMoods.stale(preferences, mood)) return;
        lifecycleWork.execute(ioWorker, () -> {
            try {
                List<String> fresh = WallpaperMoods.fetch(preferences, mood);
                lifecycleWork.post(() -> {
                    if (!mood.equals(WallpaperMoods.current(preferences)) || isFinishing()) return;
                    List<BackgroundPhoto> photos = moodPhotos(fresh);
                    if (!sameBackgroundPhotos(photos)) setBackgroundPhotos(photos);
                });
            } catch (Exception ignored) {
                // Offline: keep whatever is showing, the calm background included.
            }
        });
    }


    private List<BackgroundPhoto> moodPhotos(List<String> urls) {
        ArrayList<BackgroundPhoto> photos = new ArrayList<>();
        for (String url : urls) photos.add(new BackgroundPhoto(Uri.parse(url), ""));
        return photos;
    }

    private void scheduleNextBackgroundPhoto() {
        if (slideshowTick == null || !screenActive || backgroundPhotos.size() < 2) return;
        handler.removeCallbacks(slideshowTick);
        // Gallery moods change picture once a minute; a photo folder keeps its own setting.
        long interval = moodGalleryActive ? 60_000L : scheduleValue(SchedulePreferences.SLIDESHOW,
                SchedulePreferences.SLIDESHOW_VALUES, SchedulePreferences.DEFAULT_SLIDESHOW);
        if (interval > 0) handler.postDelayed(slideshowTick, interval);
    }

    private long scheduleValue(String key, long[] values, long fallback) {
        return SchedulePreferences.get(preferences, key, values, fallback);
    }

    private void showNextBackgroundPhoto() {
        if (backgroundPhotos.isEmpty() || !screenActive || isFinishing()) return;
        if (slideshowTick != null) handler.removeCallbacks(slideshowTick);
        int index = backgroundIndex % backgroundPhotos.size();
        BackgroundPhoto photo = backgroundPhotos.get(index);
        int generation = ++backgroundImageGeneration;
        ImageView incoming = slideAActive ? slideA : slideB;
        ImageView outgoing = slideAActive ? slideB : slideA;
        lifecycleWork.execute(ioWorker, () -> {
            android.graphics.Bitmap bitmap = decodeWallpaper(photo.uri,
                    Math.min(getResources().getDisplayMetrics().widthPixels, 1280),
                    Math.min(dp(heroHeightDp()), 720));
            lifecycleWork.post(() -> {
                if (generation != backgroundImageGeneration || !screenActive || isFinishing()) return;
                backgroundIndex = (index + 1) % backgroundPhotos.size();
                if (bitmap == null) {
                    scheduleNextBackgroundPhoto();
                    return;
                }
                glassBackdrop.prepare(bitmap, true, () -> {
                if (generation != backgroundImageGeneration || !screenActive || isFinishing()) return;
                incoming.setImageBitmap(bitmap);
                incoming.setTag(photo.uri);
                incoming.setVisibility(View.VISIBLE);
                incoming.setAlpha(0f);
                incoming.animate().alpha(1f).setDuration(android.animation.ValueAnimator.areAnimatorsEnabled() ? 900 : 0).start();
                outgoing.animate().alpha(0f).setDuration(android.animation.ValueAnimator.areAnimatorsEnabled() ? 900 : 0).withEndAction(() -> {
                    outgoing.setVisibility(View.GONE);
                    outgoing.setImageDrawable(null);
                }).start();
                slideAActive = !slideAActive;
                scheduleNextBackgroundPhoto();
                });
            });
        });
    }

    private static final class BackgroundPhoto {
        final Uri uri;
        final String name;
        BackgroundPhoto(Uri uri, String name) { this.uri = uri; this.name = name; }
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    /** The calm built-in background (a drawn field or the royal pattern) behind everything. */
    private void applyWallpaper() {
        if (wallpaperImage == null || heroFrame == null) return;
        int targetWidth = Math.min(getResources().getDisplayMetrics().widthPixels, 1920);
        int targetHeight = Math.min(dp(heroHeightDp()), 1080);
        lifecycleWork.execute(ioWorker, () -> {
            android.graphics.Bitmap bitmap = CalmWallpapers.render(MainActivity.this,
                    preferences.getInt(CalmWallpapers.PREF, CalmWallpapers.DEFAULT),
                    Math.max(640, targetWidth), Math.max(360, targetHeight));
            lifecycleWork.post(() -> {
                if (isFinishing() || isDestroyed() || bitmap == null) return;
                if (!backgroundPhotos.isEmpty()) {
                    wallpaperImage.setImageBitmap(bitmap);
                    wallpaperImage.setVisibility(View.GONE);
                    return;
                }
                glassBackdrop.prepare(bitmap, false, () -> {
                    wallpaperImage.setImageBitmap(bitmap);
                    wallpaperImage.setVisibility(View.VISIBLE);
                });
            });
        });
    }


    private android.graphics.Bitmap decodeWallpaper(Uri uri, int targetWidth, int targetHeight) {
        try {
            String key = uri.toString();
            android.graphics.Bitmap cached = backgroundBitmapCache.get(key);
            if (cached != null && !cached.isRecycled()) return cached;
            String scheme = uri.getScheme();
            if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
                return decodeRemoteWallpaper(uri, targetWidth, targetHeight);
            }
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (java.io.InputStream input = getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(input, null, bounds);
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
            long targetPixels = Math.max(1L, (long) targetWidth * targetHeight);
            int sample = 1;
            while (((long) bounds.outWidth / (sample * 2L)) * ((long) bounds.outHeight / (sample * 2L)) > targetPixels * 2L
                    && sample < (1 << 29)) sample *= 2;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            options.inPreferredConfig = android.graphics.Bitmap.Config.RGB_565;
            try (java.io.InputStream input = getContentResolver().openInputStream(uri)) {
                android.graphics.Bitmap bitmap = BitmapFactory.decodeStream(input, null, options);
                if (bitmap != null) backgroundBitmapCache.put(key, bitmap);
                return bitmap;
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    private android.graphics.Bitmap decodeRemoteWallpaper(Uri uri, int targetWidth, int targetHeight) {
        String key = uri.toString();
        android.graphics.Bitmap cached = backgroundBitmapCache.get(key);
        if (cached != null && !cached.isRecycled()) return cached;
        java.io.File cacheDirectory = new java.io.File(getCacheDir(), "google-photos");
        java.io.File cacheFile = new java.io.File(cacheDirectory, remoteWallpaperCacheName(key));
        if (cacheFile.isFile()) {
            android.graphics.Bitmap diskCached = decodeBackgroundImage(cacheFile, targetWidth, targetHeight);
            if (diskCached != null) {
                cacheFile.setLastModified(System.currentTimeMillis());
                backgroundBitmapCache.put(key, diskCached);
                return diskCached;
            }
            cacheFile.delete();
        }
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new java.net.URL(key).openConnection();
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(20000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) AppleWebKit/537.36 Chrome/128.0.0.0 Mobile Safari/537.36");
            connection.setRequestProperty("Accept", "image/avif,image/webp,image/*,*/*;q=0.8");
            try (java.io.InputStream input = connection.getInputStream(); java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int total = 0;
                int count;
                while ((count = input.read(buffer)) != -1) {
                    total += count;
                    if (total > 12 * 1024 * 1024) return null;
                    output.write(buffer, 0, count);
                }
                byte[] bytes = output.toByteArray();
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
                long targetPixels = Math.max(1L, (long) targetWidth * targetHeight);
                int sample = 1;
                while (((long) bounds.outWidth / (sample * 2L)) * ((long) bounds.outHeight / (sample * 2L)) > targetPixels * 2L
                        && sample < (1 << 29)) sample *= 2;
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = sample;
                options.inPreferredConfig = android.graphics.Bitmap.Config.RGB_565;
                android.graphics.Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
                if (bitmap != null) {
                    backgroundBitmapCache.put(key, bitmap);
                    cacheDirectory.mkdirs();
                    java.io.File temporary = new java.io.File(cacheDirectory, cacheFile.getName() + ".tmp");
                    try (java.io.FileOutputStream file = new java.io.FileOutputStream(temporary)) {
                        file.write(bytes);
                        file.flush();
                    }
                    if (cacheFile.exists()) cacheFile.delete();
                    temporary.renameTo(cacheFile);
                    trimGooglePhotosCache(cacheDirectory);
                }
                return bitmap;
            }
        } catch (Exception ignored) {
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private android.graphics.Bitmap decodeBackgroundImage(java.io.File file, int targetWidth, int targetHeight) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
        long targetPixels = Math.max(1L, (long) targetWidth * targetHeight);
        int sample = 1;
        while (((long) bounds.outWidth / (sample * 2L)) * ((long) bounds.outHeight / (sample * 2L)) > targetPixels * 2L
                && sample < (1 << 29)) sample *= 2;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        options.inPreferredConfig = android.graphics.Bitmap.Config.RGB_565;
        return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
    }

    private String remoteWallpaperCacheName(String url) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(url.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder key = new StringBuilder();
            for (byte value : digest) key.append(String.format(Locale.ROOT, "%02x", value));
            return key + ".img";
        } catch (Exception ignored) { return Integer.toHexString(url.hashCode()) + ".img"; }
    }

    private void trimGooglePhotosCache(java.io.File directory) {
        java.io.File[] files = directory.listFiles(file -> file.isFile() && !file.getName().endsWith(".tmp"));
        if (files == null || files.length <= 100) return;
        java.util.Arrays.sort(files, (left, right) -> Long.compare(left.lastModified(), right.lastModified()));
        for (int i = 0; i < files.length - 100; i++) files[i].delete();
    }

    private final class NavigationGlyph extends Drawable {
        private final int kind;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path strokePath = new Path();
        private final Path fillPath = new Path();
        private int glyphColor = 0xFFEAF4F5;

        NavigationGlyph(int kind) {
            this.kind = kind;
            paint.setStrokeWidth(2.1f);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            initPaths();
        }

        private void initPaths() {
            switch (kind) {
                case TAB_HOME:
                    strokePath.moveTo(2, 11); strokePath.lineTo(12, 3); strokePath.lineTo(22, 11);
                    strokePath.moveTo(5, 10); strokePath.lineTo(5, 21); strokePath.lineTo(10, 21); strokePath.lineTo(10, 15);
                    strokePath.lineTo(14, 15); strokePath.lineTo(14, 21); strokePath.lineTo(19, 21); strokePath.lineTo(19, 10);
                    break;
                case TAB_TV:
                    strokePath.addRoundRect(new RectF(3, 8, 21, 19), 2, 2, Path.Direction.CW);
                    strokePath.moveTo(8, 4); strokePath.lineTo(12, 8); strokePath.lineTo(16, 4);
                    strokePath.moveTo(9, 22); strokePath.lineTo(15, 22); strokePath.moveTo(12, 19); strokePath.lineTo(12, 22);
                    break;
                case TAB_GAMES:
                    strokePath.moveTo(7, 8); strokePath.lineTo(17, 8);
                    strokePath.cubicTo(20, 8, 21, 10, 22, 16); strokePath.cubicTo(23, 20, 20, 21, 17, 18);
                    strokePath.lineTo(15, 16); strokePath.lineTo(9, 16); strokePath.lineTo(7, 18);
                    strokePath.cubicTo(4, 21, 1, 20, 2, 16); strokePath.cubicTo(3, 10, 4, 8, 7, 8);
                    strokePath.moveTo(6, 11); strokePath.lineTo(6, 15); strokePath.moveTo(4, 13); strokePath.lineTo(8, 13);
                    fillPath.addCircle(17, 12, 1, Path.Direction.CW);
                    fillPath.addCircle(19, 14, 1, Path.Direction.CW);
                    break;
                case TAB_QURAN:
                    // An open book.
                    strokePath.moveTo(12, 6);
                    strokePath.cubicTo(9.5f, 4.2f, 6, 4, 3, 4.6f);
                    strokePath.lineTo(3, 19);
                    strokePath.cubicTo(6, 18.4f, 9.5f, 18.6f, 12, 20.4f);
                    strokePath.cubicTo(14.5f, 18.6f, 18, 18.4f, 21, 19);
                    strokePath.lineTo(21, 4.6f);
                    strokePath.cubicTo(18, 4, 14.5f, 4.2f, 12, 6);
                    strokePath.lineTo(12, 20.4f);
                    break;
                case TAB_PRAYER:
                    // Crescent above a small dome.
                    strokePath.moveTo(15.5f, 2.5f);
                    strokePath.cubicTo(12.5f, 3.2f, 12.3f, 7.8f, 15.5f, 8.5f);
                    strokePath.cubicTo(13.2f, 9.6f, 10.2f, 7.6f, 10.6f, 5.2f);
                    strokePath.cubicTo(11.0f, 3.2f, 13.4f, 1.9f, 15.5f, 2.5f);
                    strokePath.moveTo(4, 21); strokePath.lineTo(4, 16);
                    strokePath.cubicTo(4, 12.5f, 8, 11, 12, 10);
                    strokePath.cubicTo(16, 11, 20, 12.5f, 20, 16);
                    strokePath.lineTo(20, 21); strokePath.close();
                    strokePath.moveTo(10, 21); strokePath.lineTo(10, 17.5f);
                    strokePath.cubicTo(10, 16, 14, 16, 14, 17.5f); strokePath.lineTo(14, 21);
                    break;
                case TAB_EVENTS:
                    strokePath.addRoundRect(new RectF(3, 5, 21, 21), 2, 2, Path.Direction.CW);
                    strokePath.moveTo(7, 3); strokePath.lineTo(7, 8); strokePath.moveTo(17, 3); strokePath.lineTo(17, 8);
                    strokePath.moveTo(3, 10); strokePath.lineTo(21, 10);
                    fillPath.addCircle(8, 14, 1, Path.Direction.CW);
                    fillPath.addCircle(12, 14, 1, Path.Direction.CW);
                    fillPath.addCircle(16, 14, 1, Path.Direction.CW);
                    fillPath.addCircle(8, 18, 1, Path.Direction.CW);
                    break;
                default:
                    strokePath.moveTo(6, 3); strokePath.lineTo(18, 3); strokePath.lineTo(18, 21); strokePath.lineTo(12, 17);
                    strokePath.lineTo(6, 21); strokePath.close();
                    break;
            }
        }

        void setGlyphColor(int color) {
            if (glyphColor == color) return;
            glyphColor = color;
            invalidateSelf();
        }

        @Override public void draw(Canvas canvas) {
            Rect bounds = getBounds();
            if (bounds.isEmpty()) return;
            int save = canvas.save();
            canvas.translate(bounds.left, bounds.top);
            canvas.scale(bounds.width() / 24f, bounds.height() / 24f);
            paint.setColor(glyphColor);
            String modern = kind == TAB_PRAYER ? "dome" : kind == TAB_GALLERY ? "gallery"
                    : kind == TAB_HARAM ? "haram" : kind == TAB_QURAN ? "quran" : null;
            if (modern != null) {
                paint.setStyle(Paint.Style.STROKE);
                float stroke = paint.getStrokeWidth();
                if (!"dome".equals(modern)) { canvas.scale(24f / 40f, 24f / 40f); paint.setStrokeWidth(2.4f); }
                else paint.setStrokeWidth(1.7f);
                ModernIcons.drawKind(canvas, modern, paint);
                paint.setStrokeWidth(stroke);
                canvas.restoreToCount(save);
                return;
            }
            if (!strokePath.isEmpty()) {
                paint.setStyle(Paint.Style.STROKE);
                canvas.drawPath(strokePath, paint);
            }
            if (!fillPath.isEmpty()) {
                paint.setStyle(Paint.Style.FILL);
                canvas.drawPath(fillPath, paint);
            }
            canvas.restoreToCount(save);
        }

        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); invalidateSelf(); }
        @Override public void setColorFilter(android.graphics.ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }}
