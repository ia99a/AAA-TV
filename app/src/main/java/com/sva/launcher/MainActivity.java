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

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

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
    private static final String[] TAB_TITLES = {"آمر", "Live TV", "Games", "تاريخك", "Saved", "الصلاة", "القرآن", "الحرم", "الخلفيات"};
    private static final String PREF_HIDE_LIVE_TV = "hideLiveTvNavigation";
    private static final String PREF_HIDE_GAMES = "hideGamesNavigation";
    private static final String PREF_HIDE_EVENTS = "hideEventsNavigation";
    private static final String PREF_LIVE_TV_POSITION = "liveTvNavigationPosition";
    static final String ACTION_WATCH_REMINDER = "com.sva.launcher.WATCH_REMINDER";
    static final String EXTRA_REMINDER_CHANNEL_KEY = "reminderChannelKey";
    static final String EXTRA_REMINDER_STREAM_URL = "reminderStreamUrl";
    private static final int PICK_WALLPAPER = 4107;
    private static final int PICK_WALLPAPER_FOLDER = 4108;
    private static final int PICK_BACKUP_EXPORT = 4109;
    private static final int PICK_BACKUP_IMPORT = 4110;
    private static final int REQUEST_WEATHER_LOCATION = 4111;
    private static final String GOOGLE_PHOTOS_CACHE_URL = "googlePhotosCacheUrl";
    private static final String GOOGLE_PHOTOS_CACHE_IMAGES = "googlePhotosCacheImages";
    private static final String PHOTO_FOLDER_CACHE_URI = "photoFolderCacheUri";
    private static final String PHOTO_FOLDER_CACHE_IMAGES = "photoFolderCacheImages";
    private static final int CALENDAR_CACHE_VERSION = 3;
    private static final int PANEL = 0xF21B1B1B;
    private static final int PANEL_FOCUSED = 0xFF2A2A2A;
    private static final int MUTED = 0xFFB7B7B7;
    private static final int ACCENT = 0xFF7BE7F4;
    private static final int LAVENDER = 0xFFB7B7B7;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final LifecycleWork lifecycleWork = new LifecycleWork(this, handler);
    private final List<IptvRepository.Channel> channels = new ArrayList<>();
    private final Set<String> hiddenChannelKeys = new HashSet<>();
    private final Set<String> favoriteChannelKeys = new HashSet<>();
    private final Set<String> youtubeMetadataInFlight = new HashSet<>();
    private final ExecutorService youtubeWorker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "youtube-card-metadata");
        thread.setDaemon(true);
        return thread;
    });
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
    private final LruCache<String, android.graphics.Bitmap> youtubeThumbnailCache = new LruCache<String, android.graphics.Bitmap>(12 * 1024) {
        @Override protected int sizeOf(String key, android.graphics.Bitmap bitmap) {
            return Math.max(1, bitmap.getByteCount() / 1024);
        }
    };
    private IptvRepository iptv;
    private ChannelLogoLoader logoLoader;
    private SharedPreferences preferences;
    private PrayerView prayerView;
    private QuranView quranView;
    private FrameLayout sceneRoot;
    private HaramView haramView;
    private GalleryView galleryView;
    /** «بسيط ٢»: the glass home, shown instead of the original home content when chosen. */
    private ModernHomeView modernHome;
    private ImageView dayBackdrop;
    private View heroSideShade, heroBottomFade;
    private LinearLayout navbarView;
    /** Draw the «بسيط ٢» icons (the dome) in the bottom bar. */
    private boolean modernGlyphs;
    /** Adhan-to-iqama countdown at the top left of Home, the Quran and the wallpapers. */
    private IqamaCountdown.Pill iqamaPill;
    private QuickSettingsView quickSettings;
    private LinearLayout launcherShell;
    private LinearLayout root, homeContent, liveContent, gamesContent, eventsContent, savedContent;
    private LinearLayout appRow, youtubeRow, youtubeSection;
    private GridLayout gameGrid, calendarMonthGrid;
    private TextView calendarHeaderDate, calendarHeaderEvents;
    private Button previousMonthButton, nextMonthButton;
    private TextView gamesEmpty, youtubeEmpty, todayEmpty, monthTitle;
    private TextView calendarDetailDate, calendarDetailDay, calendarDetailMoon;
    private TextView calendarDetailMoonName, calendarDetailIllumination, calendarDetailEvent;
    private LinearLayout todayHeading, todayItems;
    private LinearLayout calendarDetailsPanel;
    private YearMonth displayedMonth = YearMonth.now();
    private LocalDate selectedCalendarDay = LocalDate.now();
    private YearMonth renderedMonth;
    private LocalDate renderedToday;
    private long renderedCalendarStamp = -1L;
    private final View[] tabPages = new View[TAB_TITLES.length];
    private final Button[] tabButtons = new Button[TAB_TITLES.length];
    private final View[] tabFocus = new View[TAB_TITLES.length];
    private final View[] tabEntryFocus = new View[TAB_TITLES.length];
    private final int[] tabScroll = new int[TAB_TITLES.length];
    private final NavigationGlyph[] navigationGlyphs = new NavigationGlyph[TAB_TITLES.length];
    private int activeTab = TAB_HOME;
    private boolean tabsInitialized;
    private boolean switchingTabs;
    private boolean compactNavigation;
    private LinearLayout tabRow;
    private LinearLayout systemShortcuts;
    private View browsingShade;
    private FrameLayout heroFrame;
    /** «بسيط ١»: clock, weather and occasion above the apps; hidden under «بسيط ٢», which has its own cards. */
    private FrameLayout homeTopBar;
    private final GlassBackdrop glassBackdrop = new GlassBackdrop();
    private FrameLayout homePage;
    private boolean initialAppFocus;
    private String focusedPackage;
    private ImageView wallpaperImage, slideA, slideB;
    private TextView clock, clockPeriod, date, status;
    private LinearLayout weatherPanel;
    private TextView weatherTemperature, weatherCondition, weatherDetails, weatherLocation, weatherMetrics;
    private TextView weatherSymbol, homeCalendarEyebrow, homeCalendarTitle, homeCalendarSubtitle;
    private LinearLayout timeCard, weatherCard, homeCalendarCard;
    private View clockDivider;
    private LinearLayout nowPlayingSection, nowPlayingCard;
    private Button musicToggle;
    private ImageView musicArtwork;
    private TextView musicTitle, musicArtist;
    private ProgressBar musicProgress;
    private MediaController activeMediaController;
    private Runnable mediaTick;
    private LinearLayout channelRows, channelCategoryTabs;
    private HorizontalScrollView channelCategoryScroll;
    private TextView channelsEmpty;
    private LinearLayout guideSection;
    private TextView guideEmpty;
    private Button guideToggle;
    private String pendingReminderChannelKey, pendingReminderStreamUrl;
    private final LinkedHashMap<String, ChannelRow> channelRowMap = new LinkedHashMap<>();
    private final LinkedHashMap<String, Button> channelTabMap = new LinkedHashMap<>();
    private List<String> renderedCategories = new ArrayList<>();
    private String selectedChannelCategory;
    private Runnable clockTick;
    private Runnable guideRefreshTick;
    private Runnable calendarRefreshTick;
    private Runnable googlePhotosRefreshTick;
    private Runnable weatherRefreshTick;
    private Runnable slideshowTick;
    private final List<BackgroundPhoto> backgroundPhotos = new ArrayList<>();
    private final LruCache<String, android.graphics.Bitmap> backgroundBitmapCache = new LruCache<String, android.graphics.Bitmap>(20 * 1024) {
        @Override protected int sizeOf(String key, android.graphics.Bitmap bitmap) {
            return Math.max(1, bitmap.getByteCount() / 1024);
        }
    };
    private int backgroundIndex, backgroundFolderGeneration, backgroundImageGeneration;
    private boolean slideAActive = true;
    private boolean screenActive;
    private boolean appsLoaded;
    private boolean appsLoading;
    private boolean channelsRefreshing;
    private boolean guideRefreshing;
    private boolean calendarRefreshing;
    private String calendarRefreshingUrl = "";
    private int calendarRequestGeneration;
    private WeatherRepository.Forecast weatherForecast;
    private int weatherRequestGeneration;
    private boolean weatherRefreshing, weatherRefreshedThisLaunch, pendingDeviceWeatherLocation;
    private LocationListener weatherLocationListener;
    private CancellationSignal weatherLocationCancellation;
    private Runnable weatherLocationTimeout;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        BackNavigation.install(this, this::handleBack);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        preferences = getSharedPreferences("launcher", MODE_PRIVATE);
        if (state != null) {
            focusedPackage = state.getString("focusedPackage");
            activeTab = Math.max(TAB_HOME, Math.min(LAST_TAB, state.getInt("activeTab", TAB_HOME)));
            for (int i = 0; i < tabScroll.length; i++) tabScroll[i] = state.getInt("tabScroll" + i, 0);
            tabScroll[TAB_HOME] = 0;
            try { selectedCalendarDay = LocalDate.parse(state.getString("selectedCalendarDay", LocalDate.now().toString())); }
            catch (Exception ignored) { selectedCalendarDay = LocalDate.now(); }
            displayedMonth = YearMonth.from(selectedCalendarDay);
        }
        PrayerAlerts.schedule(this);
        iptv = new IptvRepository(this);
        acceptReminderIntent(getIntent());
        logoLoader = new ChannelLogoLoader(this);
        if (!hasWeatherLocation()) {
            // Riyadh until another city is chosen from the quick settings.
            PrayerTimes.City riyadh = PrayerTimes.city(preferences.getString(QuickSettingsView.PREF_WEATHER_CITY, "riyadh"));
            preferences.edit().putString("weatherLocationMode", "city").putString("weatherLocationLabel", riyadh.name)
                    .putLong("weatherLatitude", Double.doubleToRawLongBits(riyadh.latitude))
                    .putLong("weatherLongitude", Double.doubleToRawLongBits(riyadh.longitude)).apply();
        }
        weatherForecast = cachedWeather();
        hiddenChannelKeys.addAll(HiddenChannels.load(preferences));
        favoriteChannelKeys.addAll(FavoriteChannels.load(preferences));
        String folder = preferences.getString("wallpaperFolder", "");
        String album = preferences.getString("wallpaperAlbum", "");
        if (!folder.isEmpty()) backgroundPhotos.addAll(cachedBackgroundFolder(folder));
        else if (!album.isEmpty()) backgroundPhotos.addAll(cachedBackgroundAlbum(album));
        buildHome();
        applyHomeTheme();
        if (folder.isEmpty() && album.isEmpty()) applyMood();
        renderChannels();
        loadCachedChannels();
        if (!folder.isEmpty()) loadBackgroundFolder(Uri.parse(folder), false);
        else if (!album.isEmpty()) maybeRefreshGooglePhotos(true);
        updateClock();
        clockTick = new Runnable() {
            @Override public void run() {
                updateClock();
                refreshCachedProgrammeDisplay();
                updateCalendarEventDisplay();
                handler.postDelayed(this, 30000);
            }
        };
        handler.postDelayed(clockTick, 30000);
        guideRefreshTick = new Runnable() {
            @Override public void run() {
                maybeRefreshLiveData();
                scheduleLiveRefreshCheck();
            }
        };
        updateCalendarEventDisplay();
        maybeRefreshPublicCalendar(false);
        calendarRefreshTick = new Runnable() {
            @Override public void run() {
                maybeRefreshPublicCalendar(false);
                scheduleCalendarRefresh();
            }
        };
        googlePhotosRefreshTick = new Runnable() {
            @Override public void run() {
                maybeRefreshGooglePhotos(false);
                scheduleGooglePhotosRefresh();
            }
        };
        weatherRefreshTick = new Runnable() {
            @Override public void run() {
                maybeRefreshWeather(false);
                scheduleWeatherRefresh();
            }
        };
        if (state != null && state.getBoolean("restoreSettings", false)) {
            settingsScreen = new SettingsScreen();
            settingsScreen.section = Math.max(0, Math.min(13, state.getInt("restoreSettingsSection", SettingsScreen.APPEARANCE)));
            settingsScreen.restoreDrafts(state);
            settingsScreen.show();
        }
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        if (settingsScreen != null) {
            outState.putBoolean("restoreSettings", true);
            outState.putInt("restoreSettingsSection", settingsScreen.section);
            settingsScreen.saveDrafts(outState);
        }
        View selectedApp = appRow == null ? null : appRow.findFocus();
        if (selectedApp != null && selectedApp.getTag() instanceof String) focusedPackage = (String) selectedApp.getTag();
        outState.putString("focusedPackage", focusedPackage);
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
        acceptReminderIntent(intent);
        if (!ACTION_WATCH_REMINDER.equals(intent.getAction())) selectTab(TAB_HOME, true);
        if (!channels.isEmpty()) openPendingReminder();
    }

    // Physical TV remotes and Android <33 still dispatch the legacy callback.
    @android.annotation.SuppressLint("GestureBackNavigation")
    @Override public void onBackPressed() { handleBack(); }

    @android.annotation.SuppressLint("GestureBackNavigation") // Default task behavior after our custom Back handling.
    private void handleBack() {
        if (settingsScreen != null) { settingsScreen.requestClose(); return; }
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

    private void acceptReminderIntent(Intent intent) {
        if (intent == null || !ACTION_WATCH_REMINDER.equals(intent.getAction())) return;
        pendingReminderChannelKey = intent.getStringExtra(EXTRA_REMINDER_CHANNEL_KEY);
        pendingReminderStreamUrl = intent.getStringExtra(EXTRA_REMINDER_STREAM_URL);
    }

    private void openPendingReminder() {
        if (pendingReminderChannelKey == null) return;
        String key = pendingReminderChannelKey, url = pendingReminderStreamUrl;
        pendingReminderChannelKey = pendingReminderStreamUrl = null;
        IptvRepository.Channel fallback = null;
        int matches = 0;
        for (IptvRepository.Channel channel : channels) {
            if (!channel.stableKey().equals(key) || HiddenChannels.isHidden(hiddenChannelKeys, channel)) continue;
            if (channel.streamUrl.equals(url)) { playChannel(channel); return; }
            fallback = channel;
            matches++;
        }
        if (matches == 1) playChannel(fallback);
        else Toast.makeText(this, "That reminder channel is no longer available.", Toast.LENGTH_LONG).show();
    }

    private void openGameLibrary() {
        Intent intent = new Intent(this, GameLibraryActivity.class);
        ImageView visible = null;
        for (ImageView slide : new ImageView[]{slideA, slideB}) {
            if (slide != null && slide.getVisibility() == View.VISIBLE && slide.getDrawable() != null
                    && (visible == null || slide.getAlpha() > visible.getAlpha())) visible = slide;
        }
        if (visible != null && visible.getTag() instanceof Uri) {
            Uri uri = (Uri) visible.getTag();
            if ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())) {
                java.io.File cached = new java.io.File(new java.io.File(getCacheDir(), "google-photos"),
                        remoteWallpaperCacheName(uri.toString()));
                if (cached.isFile()) intent.putExtra(GameLibraryActivity.EXTRA_BACKGROUND_URI, Uri.fromFile(cached).toString());
            } else {
                intent.putExtra(GameLibraryActivity.EXTRA_BACKGROUND_URI, uri.toString());
            }
        }
        startActivity(intent);
    }

    private void openWallpaperFolderPicker() {
        startActivityForResult(TvFilePickerActivity.intent(this, TvFilePickerActivity.MODE_IMAGE_FOLDER), PICK_WALLPAPER_FOLDER);
    }

    private void openBackupExportPicker() {
        startActivityForResult(TvFilePickerActivity.intent(this, TvFilePickerActivity.MODE_SAVE_JSON), PICK_BACKUP_EXPORT);
    }

    private void openBackupImportPicker() {
        startActivityForResult(TvFilePickerActivity.intent(this, TvFilePickerActivity.MODE_JSON), PICK_BACKUP_IMPORT);
    }

    private void exportBackup(Uri destination) {
        Toast.makeText(this, "Creating launcher backup…", Toast.LENGTH_SHORT).show();
        Thread worker = new Thread(() -> {
            String error = null;
            try {
                JSONObject backup = LauncherBackup.create(preferences,
                        getSharedPreferences("iptv", MODE_PRIVATE), iptv);
                try (java.io.OutputStream output = getContentResolver().openOutputStream(destination, "w")) {
                    if (output == null) throw new IllegalStateException("Could not write to that file.");
                    output.write(backup.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    output.flush();
                }
            } catch (Exception e) {
                error = e.getMessage() == null ? "Could not create the backup." : e.getMessage();
            }
            String result = error;
            lifecycleWork.post(() -> Toast.makeText(this,
                    result == null ? "Backup saved." : "Backup failed: " + result,
                    result == null ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG).show());
        }, "launcher-backup-export");
        worker.setDaemon(true);
        worker.start();
    }

    private void importBackup(Uri source) {
        restoringBackup = true;
        Toast.makeText(this, "Restoring launcher backup…", Toast.LENGTH_SHORT).show();
        Thread worker = new Thread(() -> {
            String error = null;
            try (java.io.InputStream input = getContentResolver().openInputStream(source)) {
                if (input == null) throw new IllegalStateException("Could not read that file.");
                java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int count;
                int total = 0;
                final int maxBytes = 64 * 1024 * 1024;
                while ((count = input.read(buffer)) != -1) {
                    total += count;
                    if (total > maxBytes) throw new IllegalArgumentException("Backup file is larger than 64 MB.");
                    bytes.write(buffer, 0, count);
                }
                JSONObject backup = new JSONObject(new String(bytes.toByteArray(), java.nio.charset.StandardCharsets.UTF_8));
                LauncherBackup.restore(backup, preferences, getSharedPreferences("iptv", MODE_PRIVATE), iptv);
            } catch (Exception e) {
                error = e.getMessage() == null ? "Could not restore that backup." : e.getMessage();
            }
            String result = error;
            lifecycleWork.post(() -> {
                restoringBackup = false;
                if (result != null) {
                    Toast.makeText(this, "Restore failed: " + result, Toast.LENGTH_LONG).show();
                    return;
                }
                hiddenChannelKeys.clear();
                hiddenChannelKeys.addAll(HiddenChannels.load(preferences));
                favoriteChannelKeys.clear();
                favoriteChannelKeys.addAll(FavoriteChannels.load(preferences));
                renderChannels();
                if (!channels.isEmpty()) {
                    ((SvaApplication) getApplication()).guideReminders().reconcile(channels, hiddenChannelKeys);
                }
                if (settingsScreen != null) {
                    settingsScreen.resetDrafts();
                    settingsScreen.channelsChanged = true;
                    if (settingsScreen.channelAdapter != null) {
                        settingsScreen.channelAdapter.refresh();
                    }
                }
                Toast.makeText(this, "Backup restored.", Toast.LENGTH_SHORT).show();
                recreate();
            });
        }, "launcher-backup-import");
        worker.setDaemon(true);
        worker.start();
    }

    private final Runnable screensaverIdle = this::startScreensaverIfIdle;

    private void startScreensaverIfIdle() {
        boolean playing = activeMediaController != null && activeMediaController.getPlaybackState() != null
                && activeMediaController.getPlaybackState().getState() == PlaybackState.STATE_PLAYING;
        // Never interrupt a recitation with the screensaver.
        if (quranView != null && quranView.isPlaying()) playing = true;
        if (haramView != null && haramView.getVisibility() == View.VISIBLE) playing = true;
        if (galleryView != null && galleryView.getVisibility() == View.VISIBLE) playing = true;
        if (screenActive && settingsScreen == null && hasWindowFocus() && !playing) {
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
        Set<String> freshHidden = HiddenChannels.load(preferences);
        Set<String> freshFavorites = FavoriteChannels.load(preferences);
        if (!freshHidden.equals(hiddenChannelKeys) || !freshFavorites.equals(favoriteChannelKeys)) {
            hiddenChannelKeys.clear();
            hiddenChannelKeys.addAll(freshHidden);
            favoriteChannelKeys.clear();
            favoriteChannelKeys.addAll(freshFavorites);
            renderChannels();
        }
        scheduleScreensaverIdle();
        maybeRefreshLiveData();
        scheduleLiveRefreshCheck();
        maybeRefreshPublicCalendar(false);
        scheduleCalendarRefresh();
        scheduleGooglePhotosRefresh();
        maybeRefreshWeather(false);
        scheduleWeatherRefresh();
        if (settingsScreen != null) settingsScreen.refreshLauncherOverride();
        updateNowPlaying();
        if (mediaTick == null) mediaTick = new Runnable() {
            @Override public void run() {
                if (!screenActive) return;
                updateNowPlaying();
                handler.postDelayed(this, activeMediaController != null ? 1000 : 10000);
            }
        };
        handler.removeCallbacks(mediaTick);
        handler.postDelayed(mediaTick, activeMediaController != null ? 1000 : 10000);
        if (root != null && !appsLoaded) renderApps();
        if (gameGrid != null) renderGames();
        if (settingsScreen == null && !backgroundPhotos.isEmpty()) {
            if (slideA.getVisibility() != View.VISIBLE && slideB.getVisibility() != View.VISIBLE) {
                showNextBackgroundPhoto();
            } else {
                scheduleNextBackgroundPhoto();
            }
        }
    }

    @Override protected void onPause() {
        if (PrayerAlertToast.foreground.get() == this) PrayerAlertToast.foreground = new java.lang.ref.WeakReference<>(null);
        screenActive = false;
        handler.removeCallbacks(screensaverIdle);
        if (slideshowTick != null) handler.removeCallbacks(slideshowTick);
        if (guideRefreshTick != null) handler.removeCallbacks(guideRefreshTick);
        if (calendarRefreshTick != null) handler.removeCallbacks(calendarRefreshTick);
        if (googlePhotosRefreshTick != null) handler.removeCallbacks(googlePhotosRefreshTick);
        if (weatherRefreshTick != null) handler.removeCallbacks(weatherRefreshTick);
        if (mediaTick != null) handler.removeCallbacks(mediaTick);
        cancelWeatherLocation();
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
        if (clockTick != null) handler.removeCallbacks(clockTick);
        if (guideRefreshTick != null) handler.removeCallbacks(guideRefreshTick);
        if (calendarRefreshTick != null) handler.removeCallbacks(calendarRefreshTick);
        if (googlePhotosRefreshTick != null) handler.removeCallbacks(googlePhotosRefreshTick);
        if (weatherRefreshTick != null) handler.removeCallbacks(weatherRefreshTick);
        if (mediaTick != null) handler.removeCallbacks(mediaTick);
        if (slideshowTick != null) handler.removeCallbacks(slideshowTick);
        handler.removeCallbacksAndMessages(null);
        calendarRequestGeneration++;
        backgroundFolderGeneration++;
        backgroundImageGeneration++;
        weatherRequestGeneration++;
        cancelWeatherLocation();
        youtubeWorker.shutdownNow();
        calendarWorker.shutdownNow();
        ioWorker.shutdownNow();
        if (logoLoader != null) logoLoader.close();
        youtubeThumbnailCache.evictAll();
        backgroundBitmapCache.evictAll();
        glassBackdrop.close();
        Motion.cancelTree(getWindow().getDecorView());
        if (settingsScreen != null && settingsScreen.dialog != null && settingsScreen.dialog.isShowing()) {
            settingsScreen.dialog.dismiss();
        }
        super.onDestroy();
    }

    private void loadCachedChannels() {
        lifecycleWork.execute(ioWorker, () -> {
            List<IptvRepository.Channel> cached = iptv.cachedChannels();
            lifecycleWork.post(() -> {
                if (isFinishing() || isDestroyed() || !channels.isEmpty()) return;
                channels.addAll(cached);
                renderChannels();
                if (!cached.isEmpty()) ((SvaApplication) getApplication()).guideReminders()
                        .reconcile(channels, hiddenChannelKeys);
                openPendingReminder();
                if (!cached.isEmpty()) status.setVisibility(View.GONE);
                scheduleLiveRefreshCheck();
                if (settingsScreen != null && settingsScreen.channelAdapter != null) {
                    settingsScreen.channelAdapter.refresh();
                }
            });
        });
    }

    private void buildHome() {
        boolean light = LiquidTheme.isLight(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.TRANSPARENT);
        root.setClipChildren(false);
        FrameLayout page = new FrameLayout(this);
        page.setBackgroundColor(Color.TRANSPARENT);
        page.addView(root, new FrameLayout.LayoutParams(-1, -1));
        homePage = page;
        FrameLayout scene = new FrameLayout(this);
        setContentView(scene);

        heroFrame = new FrameLayout(this);
        heroFrame.setBackgroundColor(light ? LiquidTheme.background(this) : 0xFF071417);
        wallpaperImage = backgroundImage();
        wallpaperImage.setImageResource(R.drawable.mountain_wallpaper);
        wallpaperImage.setVisibility(View.VISIBLE);
        slideA = backgroundImage();
        slideB = backgroundImage();
        heroFrame.addView(wallpaperImage, new FrameLayout.LayoutParams(-1, -1));
        heroFrame.addView(slideA, new FrameLayout.LayoutParams(-1, -1));
        heroFrame.addView(slideB, new FrameLayout.LayoutParams(-1, -1));
        showCachedBackgroundImmediately();
        GradientDrawable shadow = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                light ? new int[]{0x22FFFFFF, 0x05FFFFFF, 0x18FFFFFF, 0x55E9F1EE}
                      : new int[]{0x33000000, 0x09000000, 0x23000000, 0x9B031013});
        View shade = new View(this);
        shade.setBackground(shadow);
        heroFrame.addView(shade, new FrameLayout.LayoutParams(-1, -1));
        View sideShade = new View(this);
        sideShade.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                light ? new int[]{0x55E9F1EE, 0x22E9F1EE, 0x00000000}
                      : new int[]{0xB5000D10, 0x44000D10, 0x00000000}));
        heroFrame.addView(sideShade, new FrameLayout.LayoutParams(-1, -1));
        heroSideShade = sideShade;
        View bottomFade = new View(this);
        bottomFade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                light ? new int[]{0x00E9F1EE, 0x18E9F1EE, 0xCCE9F1EE}
                      : new int[]{0x00030C0E, 0x18030C0E, 0xE5030C0E}));
        heroFrame.addView(bottomFade, new FrameLayout.LayoutParams(-1, -1));
        heroBottomFade = bottomFade;
        // «بسيط ٢» day look: a pale version of the royal pattern over everything.
        dayBackdrop = backgroundImage();
        dayBackdrop.setVisibility(View.GONE);
        heroFrame.addView(dayBackdrop, new FrameLayout.LayoutParams(-1, -1));
        scene.addView(heroFrame, new FrameLayout.LayoutParams(-1, -1));
        browsingShade = new View(this);
        browsingShade.setBackgroundColor(light ? 0xFFE9F1EE : 0xFF071417);
        browsingShade.setAlpha(light ? 0.50f : 0f);
        scene.addView(browsingShade, new FrameLayout.LayoutParams(-1, -1));
        sceneRoot = scene;
        LinearLayout tabShell = new LinearLayout(this);
        launcherShell = tabShell;
        tabShell.setOrientation(LinearLayout.VERTICAL);
        tabShell.setClipChildren(false);
        tabShell.setClipToPadding(false);
        scene.addView(tabShell, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout navigation = new LinearLayout(this);
        navigation.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        int screenWidthDp = Math.round(getResources().getDisplayMetrics().widthPixels
                / getResources().getDisplayMetrics().density);
        compactNavigation = screenWidthDp < 800;
        int outerPadding = compactNavigation ? 16 : 33;
        int dockHeight = 50;
        navigation.setPadding(dp(outerPadding), dp(8), dp(outerPadding), dp(8));
        tabShell.addView(navigation, new LinearLayout.LayoutParams(-1, dp(dockHeight)));

        navigation.setClipChildren(false);
        navigation.setClipToPadding(false);
        LinearLayout navbar = new LinearLayout(this);
        navbar.setGravity(Gravity.CENTER_VERTICAL);
        navbar.setClipChildren(false);
        navbar.setClipToPadding(false);
        navbar.setPadding(dp(10), 0, dp(14), 0);
        // A transparent bar: only the labels and the gold underline, no panel behind them.
        navbar.setBackground(null);
        navbar.setElevation(0);
        navbarView = navbar;
        navigation.addView(navbar, new LinearLayout.LayoutParams(-2, dp(34)));

        int weatherMaxWidth = compactNavigation ? 165 : 195;
        HorizontalScrollView tabScroller = new HorizontalScrollView(this);
        tabScroller.setHorizontalScrollBarEnabled(false);
        tabScroller.setOverScrollMode(View.OVER_SCROLL_NEVER);
        tabScroller.setFillViewport(false);
        tabScroller.setClipChildren(true);
        tabScroller.setClipToPadding(false);
        LinearLayout.LayoutParams tabGroupParams = new LinearLayout.LayoutParams(-2, dp(34));
        navbar.addView(tabScroller, tabGroupParams);
        tabRow = new LinearLayout(this);
        tabRow.setGravity(Gravity.CENTER_VERTICAL);
        tabRow.setClipChildren(false);
        tabRow.setClipToPadding(false);
        tabRow.setPadding(dp(4), dp(2), dp(4), dp(2));
        tabScroller.addView(tabRow, new HorizontalScrollView.LayoutParams(-2, -1));
        for (int i = 0; i < TAB_TITLES.length; i++) {
            final int tab = i;
            Button button = actionButton(TAB_TITLES[i]);
            button.setId(View.generateViewId());
            button.setMinWidth(0);
            button.setMinimumWidth(0);
            button.setTextSize(12);
            int horizontalButtonPadding = compactNavigation ? 12 : (i == TAB_HOME ? 18 : 10);
            button.setPadding(dp(horizontalButtonPadding), 0, dp(horizontalButtonPadding), 0);
            NavigationGlyph glyph = new NavigationGlyph(i);
            navigationGlyphs[i] = glyph;
            glyph.setBounds(0, 0, dp(16), dp(16));
            // Right-to-left: the icon sits to the right of the Arabic label.
            button.setCompoundDrawablesRelative(null, null, glyph, null);
            button.setCompoundDrawablePadding(dp(6));
            // No filled pill: a soft underline marks the selected tab and stretches when focused.
            button.setBackground(new TabIndicatorDrawable(light ? 0xFF9A6A1F : 0xFFE3B469,
                    light ? 0x14000000 : 0x1AFFFFFF, getResources().getDisplayMetrics().density));
            button.setSelected(i == activeTab);
            button.setTypeface(LiquidTheme.font(this));
            button.setTextColor(new android.content.res.ColorStateList(
                    new int[][]{new int[]{android.R.attr.state_selected}, new int[]{android.R.attr.state_focused}, new int[]{}},
                    new int[]{tabTextColor(true), tabTextColor(true), tabTextColor(false)}));
            glyph.setGlyphColor(tabTextColor(i == activeTab));
            button.setOnClickListener(v -> { if (tab == TAB_HARAM) openHaramLive(); else selectTab(tab, false); });
            button.setOnFocusChangeListener((view, focused) -> {
                LiquidTheme.animateContainedFocus(view, focused);
                // Full-screen tabs open with OK only, so moving along the bar never jumps into them.
                if (focused && !isFullScreenTab(tab)) selectTab(tab, false);
            });
            tabButtons[i] = button;
        }
        rebuildNavigationButtons();
        LinearLayout timeStack = new LinearLayout(this);
        timeStack.setOrientation(LinearLayout.VERTICAL);
        timeStack.setGravity(Gravity.CENTER_VERTICAL);
        timeStack.setPadding(0, 0, 0, 0);
        clock = new TextView(this);
        clock.setTextColor(light ? LiquidTheme.text(this) : Color.WHITE);
        // Keep the clock deliberately distinct from the compact Manrope UI copy.
        // The wider medium face remains easy to read from across the room.
        clock.setTextSize(compactNavigation ? 40 : 46);
        clock.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        clock.setLetterSpacing(.015f);
        clock.setIncludeFontPadding(false);
        clock.setSingleLine(true);
        LinearLayout timeLine = new LinearLayout(this);
        timeLine.setGravity(Gravity.CENTER_VERTICAL);
        timeLine.addView(clock, new LinearLayout.LayoutParams(-2, -2));
        clockDivider = new View(this);
        clockDivider.setBackgroundColor(light ? LiquidTheme.border(this) : 0x667E8588);
        LinearLayout.LayoutParams clockDividerParams = new LinearLayout.LayoutParams(dp(1), dp(32));
        clockDividerParams.setMargins(dp(10), 0, dp(10), 0);
        clockDivider.setVisibility(View.GONE);
        clockPeriod = label("", compactNavigation ? 16 : 20, light ? LiquidTheme.secondaryText(this) : 0xFFBEC4CD, false);
        clockPeriod.setPadding(0, dp(18), dp(10), 0);
        timeLine.addView(clockPeriod, new LinearLayout.LayoutParams(-2, -2));
        timeStack.addView(timeLine, new LinearLayout.LayoutParams(-2, -2));
        date = label("", compactNavigation ? 11 : 13, light ? LiquidTheme.secondaryText(this) : 0xFFF5F7F7, false);
        date.setTypeface(LiquidTheme.font(this));
        date.setLetterSpacing(0f);
        date.setSingleLine(true);
        date.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams dateParams = new LinearLayout.LayoutParams(-2, -2);
        dateParams.topMargin = dp(2);
        timeStack.addView(date, 0, dateParams);

        weatherPanel = new LinearLayout(this);
        weatherPanel.setOrientation(LinearLayout.HORIZONTAL);
        weatherPanel.setGravity(Gravity.CENTER_VERTICAL);
        weatherPanel.setFocusable(false);
        weatherPanel.setClickable(false);
        weatherPanel.setClipChildren(false);
        weatherPanel.setClipToPadding(false);
        weatherPanel.setPadding(0, 0, 0, 0);
        weatherPanel.setContentDescription("Weather unavailable");
        weatherSymbol = label("☀", compactNavigation ? 36 : 42, 0xFFFFBD38, true);
        weatherSymbol.setGravity(Gravity.CENTER);
        weatherPanel.addView(weatherSymbol, new LinearLayout.LayoutParams(dp(60), -1));
        LinearLayout weatherCopy = new LinearLayout(this);
        weatherCopy.setOrientation(LinearLayout.VERTICAL);
        weatherCopy.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams weatherCopyParams = new LinearLayout.LayoutParams(0, -2, 1);
        weatherCopyParams.leftMargin = dp(6);
        weatherPanel.addView(weatherCopy, weatherCopyParams);
        weatherTemperature = label("Add weather", 16, light ? LiquidTheme.text(this) : Color.WHITE, true);
        weatherTemperature.setTypeface(Typeface.create(LiquidTheme.font(this), Typeface.BOLD));
        weatherTemperature.setTextColor(light ? LiquidTheme.text(this) : Color.WHITE);
        weatherTemperature.setIncludeFontPadding(false);
        weatherTemperature.setSingleLine(true);
        weatherTemperature.setMaxWidth(dp(weatherMaxWidth));
        weatherCopy.addView(weatherTemperature);
        weatherCondition = label("Local conditions", 13, light ? LiquidTheme.secondaryText(this) : 0xFFCCD0D6, false);
        weatherCondition.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        weatherCondition.setIncludeFontPadding(false);
        weatherCondition.setSingleLine(true);
        weatherCondition.setMaxWidth(dp(weatherMaxWidth));
        weatherCondition.setEllipsize(TextUtils.TruncateAt.END);
        weatherCopy.addView(weatherCondition);
        LinearLayout weatherFacts = new LinearLayout(this);
        weatherFacts.setOrientation(LinearLayout.VERTICAL);
        weatherFacts.setGravity(Gravity.CENTER_VERTICAL);
        weatherFacts.setPadding(dp(16), 0, 0, 0);
        weatherDetails = label("", 11, light ? LiquidTheme.secondaryText(this) : 0xFFCCD0D6, false);
        weatherLocation = label("", 11, light ? LiquidTheme.secondaryText(this) : 0xFFCCD0D6, false);
        weatherLocation.setSingleLine(true);
        weatherLocation.setEllipsize(TextUtils.TruncateAt.END);
        weatherMetrics = label("", 11, light ? LiquidTheme.secondaryText(this) : 0xFFF0F1F3, false);
        weatherFacts.addView(weatherDetails);
        weatherFacts.addView(weatherLocation);
        weatherFacts.addView(weatherMetrics);
        weatherPanel.addView(weatherFacts, new LinearLayout.LayoutParams(0, -1, 1.15f));

        systemShortcuts = new LinearLayout(this);
        systemShortcuts.setGravity(Gravity.CENTER_VERTICAL);
        systemShortcuts.setClipChildren(false);
        systemShortcuts.setClipToPadding(false);
        systemShortcuts.setPadding(dp(3), dp(3), dp(3), dp(3));
        navbar.addView(systemShortcuts, new LinearLayout.LayoutParams(-2, dp(40)));
        addTopIcon(systemShortcuts, "Network", R.drawable.ic_network, this::openNetworkSettings);
        addTopIcon(systemShortcuts, "Android Settings", R.drawable.ic_android_settings, this::openAndroidSettings);
        addTopIcon(systemShortcuts, "Launcher Settings", R.drawable.ic_launcher_settings, this::showSettings);
        // The network, Android settings and launcher settings icons are hidden to keep the bar quiet.
        // Launcher settings open with the remote's Menu key, or a long press on «آمر».
        systemShortcuts.setVisibility(View.GONE);
        tabButtons[TAB_HOME].setOnLongClickListener(v -> {
            if (quickSettings != null) quickSettings.open(); else showSettings();
            return true;
        });
        mirrorRow(navbar);
        FrameLayout viewport = new FrameLayout(this);
        viewport.setClipChildren(false);
        viewport.setClipToPadding(false);
        tabShell.addView(viewport, new LinearLayout.LayoutParams(-1, 0, 1));
        tabPages[TAB_HOME] = page;
        viewport.addView(page, new FrameLayout.LayoutParams(-1, -1));
        modernHome = new ModernHomeView(this, preferences);
        modernHome.setVisibility(View.GONE);
        page.addView(modernHome, new FrameLayout.LayoutParams(-1, -1));
        liveContent = addTabContentPage(viewport, TAB_TV);
        gamesContent = addTabContentPage(viewport, TAB_GAMES);
        eventsContent = addTabContentPage(viewport, TAB_EVENTS);
        savedContent = addTabContentPage(viewport, TAB_SAVED);
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
        galleryView.setVisibility(View.GONE);
        tabPages[TAB_GALLERY] = galleryView;
        sceneRoot.addView(galleryView, new FrameLayout.LayoutParams(-1, -1));
        linkTabEntry(TAB_GALLERY, galleryView.entryFocus());
        iqamaPill = new IqamaCountdown.Pill(this, preferences);
        // On Home the pill takes the place of the next-occasion card while it shows.
        iqamaPill.setListener(shown -> { if (homeCalendarCard != null) homeCalendarCard.setVisibility(shown ? View.INVISIBLE : View.VISIBLE); });
        FrameLayout.LayoutParams pillParams = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.LEFT);
        pillParams.setMargins(dp(30), dp(26), 0, 0);
        sceneRoot.addView(iqamaPill, pillParams);
        quickSettings = new QuickSettingsView(this, preferences, new QuickSettingsView.Listener() {
            @Override public void onMoodChanged(String mood) { clearPhotoWallpapers(); applyMood(); }
            @Override public void onCalmColourChanged(int index) { clearPhotoWallpapers(); applyMood(); }
            @Override public void onWeatherCityChanged(PrayerTimes.City city) {
                saveWeatherLocation("city", new WeatherRepository.Location(city.name, city.latitude, city.longitude));
            }
            @Override public void onOpenAdvanced() { showSettings(); }
            @Override public void onGalleryIntervalChanged(int minutes) { if (galleryView != null) galleryView.intervalChanged(); }
            @Override public void onHomeThemeChanged() { applyHomeTheme(); }
            @Override public void onGalleryCategoryChanged() { if (galleryView != null) galleryView.categoryChanged(); }
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
                    android.widget.Toast.makeText(MainActivity.this,
                            "فعّل «AAA TV» في إمكانية الوصول، ثم ارجع", android.widget.Toast.LENGTH_LONG).show();
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
        tabShell.removeView(navigation);
        tabShell.addView(navigation, new LinearLayout.LayoutParams(-1, dp(dockHeight)));
        FrameLayout heroContent = new FrameLayout(this);
        root.addView(heroContent, new LinearLayout.LayoutParams(-1,
                dp(Math.max(180, heroHeightDp() - dockHeight - 128))));
        renderWeather();

        int topCardHeight = dp(compactNavigation ? 78 : 82);
        int weatherWidth = dp(compactNavigation ? 240 : 270);

        // Top-Left: Time
        timeCard = new LinearLayout(this);
        timeCard.setId(View.generateViewId());
        timeCard.setOrientation(LinearLayout.VERTICAL);
        timeCard.setGravity(Gravity.CENTER_VERTICAL);
        timeCard.setPadding(dp(16), dp(2), dp(16), dp(2));
        timeCard.setElevation(0);
        timeCard.setFocusable(false);
        timeCard.setClickable(false);
        timeCard.addView(timeStack, new LinearLayout.LayoutParams(-2, -2));

        // Top-Center: Event
        homeCalendarCard = new LinearLayout(this);
        homeCalendarCard.setId(View.generateViewId());
        homeCalendarCard.setGravity(Gravity.CENTER_VERTICAL);
        homeCalendarCard.setPadding(dp(12), dp(6), dp(12), dp(6));
        homeCalendarCard.setElevation(0);
        TextView calendarIcon = label("", 26, light ? LiquidTheme.accent(this) : 0xFF81B8F2, true);
        NavigationGlyph eventGlyph = new NavigationGlyph(TAB_EVENTS);
        eventGlyph.setBounds(0, 0, dp(27), dp(27));
        eventGlyph.setGlyphColor(light ? LiquidTheme.accent(this) : 0xFF81B8F2);
        calendarIcon.setCompoundDrawables(null, eventGlyph, null, null);
        calendarIcon.setGravity(Gravity.CENTER);
        homeCalendarCard.addView(calendarIcon, new LinearLayout.LayoutParams(dp(44), dp(46)));
        View calendarDivider = new View(this);
        calendarDivider.setBackgroundColor(light ? LiquidTheme.border(this) : 0x558FADB2);
        LinearLayout.LayoutParams calendarDividerParams = new LinearLayout.LayoutParams(dp(1), dp(36));
        calendarDividerParams.setMargins(dp(6), 0, dp(10), 0);

        LinearLayout calendarCopy = new LinearLayout(this);
        calendarCopy.setOrientation(LinearLayout.VERTICAL);
        calendarCopy.setGravity(Gravity.CENTER_VERTICAL);
        homeCalendarCard.addView(calendarCopy, new LinearLayout.LayoutParams(0, -2, 1));
        homeCalendarEyebrow = label("القادم", 10, light ? LiquidTheme.secondaryText(this) : 0xFFBEC4CD, false);
        calendarCopy.addView(homeCalendarEyebrow);
        homeCalendarTitle = label("لا مناسبات اليوم", 16, light ? LiquidTheme.text(this) : Color.WHITE, true);
        homeCalendarTitle.setSingleLine(true);
        homeCalendarTitle.setMaxLines(1);
        homeCalendarTitle.setEllipsize(TextUtils.TruncateAt.END);
        calendarCopy.addView(homeCalendarTitle);
        homeCalendarSubtitle = label("", 11, light ? LiquidTheme.secondaryText(this) : 0xFFBCCBD0, false);
        homeCalendarSubtitle.setSingleLine(true);
        homeCalendarSubtitle.setEllipsize(TextUtils.TruncateAt.END);
        calendarCopy.addView(homeCalendarSubtitle);
        homeCalendarCard.setFocusable(false);
        homeCalendarCard.setClickable(false);
        updateHomeCalendarWidget();

        // Top-Right: Weather
        LinearLayout weatherStack = new LinearLayout(this);
        weatherStack.setOrientation(LinearLayout.VERTICAL);
        weatherStack.setGravity(Gravity.END);
        weatherStack.setClipChildren(false);
        weatherStack.setClipToPadding(false);

        weatherCard = new LinearLayout(this);
        weatherCard.setId(View.generateViewId());
        weatherCard.setOrientation(LinearLayout.HORIZONTAL);
        weatherCard.setGravity(Gravity.CENTER_VERTICAL);
        weatherCard.setPadding(dp(12), dp(6), dp(14), dp(6));
        weatherCard.setElevation(0);
        weatherCard.setFocusable(false);
        weatherCard.setClickable(false);
        weatherCard.addView(weatherPanel, new LinearLayout.LayoutParams(-1, -1));


        nowPlayingSection = new LinearLayout(this);
        nowPlayingSection.setOrientation(LinearLayout.VERTICAL);
        nowPlayingSection.setVisibility(View.GONE);
        nowPlayingSection.setClipChildren(false);
        nowPlayingSection.setClipToPadding(false);
        TextView musicLabel = label("NOW PLAYING", 11, light ? LiquidTheme.accent(this) : 0xFF8BE4F5, true);
        musicLabel.setLetterSpacing(.18f);
        LinearLayout.LayoutParams musicLabelParams = new LinearLayout.LayoutParams(-1, -2);
        musicLabelParams.topMargin = dp(10);
        nowPlayingSection.addView(musicLabel, musicLabelParams);
        nowPlayingCard = new LinearLayout(this);
        nowPlayingCard.setGravity(Gravity.CENTER_VERTICAL);
        nowPlayingCard.setClipChildren(false);
        nowPlayingCard.setClipToPadding(false);
        nowPlayingCard.setPadding(dp(8), dp(6), dp(8), dp(6));
        nowPlayingCard.setBackground(roundedShape(
                light ? LiquidTheme.surface(this) : 0x7A091F21,
                light ? LiquidTheme.border(this) : 0x33FFFFFF, 1, 13));
        musicArtwork = new ImageView(this);
        musicArtwork.setImageResource(android.R.drawable.ic_media_play);
        musicArtwork.setScaleType(ImageView.ScaleType.CENTER_CROP);
        nowPlayingCard.addView(musicArtwork, new LinearLayout.LayoutParams(dp(44), dp(44)));
        LinearLayout musicCopy = new LinearLayout(this);
        musicCopy.setOrientation(LinearLayout.VERTICAL);
        musicCopy.setPadding(dp(8), 0, dp(4), 0);
        musicTitle = label("", 13, light ? LiquidTheme.text(this) : Color.WHITE, true);
        musicTitle.setSingleLine(true);
        musicTitle.setEllipsize(TextUtils.TruncateAt.END);
        musicCopy.addView(musicTitle);
        musicArtist = label("", 10, light ? LiquidTheme.secondaryText(this) : 0xFFB8CFCC, false);
        musicArtist.setSingleLine(true);
        musicArtist.setEllipsize(TextUtils.TruncateAt.END);
        musicCopy.addView(musicArtist);
        musicProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        musicProgress.setMax(1000);
        musicProgress.setProgressTintList(android.content.res.ColorStateList.valueOf(LiquidTheme.accent(this)));
        LinearLayout.LayoutParams musicProgressParams = new LinearLayout.LayoutParams(-1, dp(3));
        musicProgressParams.topMargin = dp(6);
        musicCopy.addView(musicProgress, musicProgressParams);
        nowPlayingCard.addView(musicCopy, new LinearLayout.LayoutParams(0, -2, 1));
        musicToggle = actionButton("Ⅱ");
        musicToggle.setTextColor(LiquidTheme.accent(this));
        musicToggle.setOnFocusChangeListener(LiquidTheme::animateContainedFocus);
        musicToggle.setContentDescription("Pause playback");
        musicToggle.setOnClickListener(v -> toggleMediaPlayback());
        LinearLayout.LayoutParams musicToggleParams = new LinearLayout.LayoutParams(dp(36), dp(40));
        musicToggleParams.setMargins(dp(4), dp(2), dp(4), dp(2));
        nowPlayingCard.addView(musicToggle, musicToggleParams);
        nowPlayingSection.addView(nowPlayingCard, new LinearLayout.LayoutParams(weatherWidth, dp(68)));
        weatherStack.addView(nowPlayingSection);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setClipChildren(false);
        header.setClipToPadding(false);
        header.setPadding(dp(14), dp(4), dp(10), dp(4));
        header.setBackgroundColor(Color.TRANSPARENT);
        LinearLayout.LayoutParams timeCardParams = new LinearLayout.LayoutParams(0, -1, 28);
        timeCardParams.setMargins(dp(2), dp(2), dp(2), dp(2));
        header.addView(timeCard, timeCardParams);
        View timeRule = new View(this);
        timeRule.setBackgroundColor(light ? LiquidTheme.border(this) : 0x55CDD0D4);
        header.addView(timeRule, new LinearLayout.LayoutParams(dp(1), dp(48)));
        LinearLayout.LayoutParams weatherCardParams = new LinearLayout.LayoutParams(0, -1, 40);
        weatherCardParams.setMargins(dp(2), dp(2), dp(2), dp(2));
        header.addView(weatherCard, weatherCardParams);
        View eventRule = new View(this);
        eventRule.setBackgroundColor(light ? LiquidTheme.border(this) : 0x55CDD0D4);
        header.addView(eventRule, new LinearLayout.LayoutParams(dp(1), dp(48)));
        LinearLayout.LayoutParams eventCardParams = new LinearLayout.LayoutParams(0, -1, 32);
        eventCardParams.setMargins(dp(2), dp(2), dp(2), dp(2));
        header.addView(homeCalendarCard, eventCardParams);
        // Right-to-left top bar: clock on the right, then weather, then the next occasion.
        mirrorRow(header);
        mirrorRow(timeLine);
        mirrorRow(weatherPanel);
        mirrorRow(homeCalendarCard);
        timeStack.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        timeCard.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        weatherCopy.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        weatherFacts.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        weatherFacts.setPadding(0, 0, dp(16), 0);
        calendarCopy.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        FrameLayout topBar = new FrameLayout(this);
        topBar.setClipChildren(false);
        topBar.setClipToPadding(false);
        topBar.setPadding(dp(20), dp(16), dp(20), 0);
        topBar.addView(header, new FrameLayout.LayoutParams(-1, topCardHeight));
        FrameLayout.LayoutParams musicParams = new FrameLayout.LayoutParams(weatherWidth, -2, Gravity.END | Gravity.TOP);
        musicParams.topMargin = topCardHeight + dp(8);
        topBar.addView(weatherStack, musicParams);
        page.setClipChildren(false);
        page.setClipToPadding(false);
        page.addView(topBar, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));
        homeTopBar = topBar;

        homeContent = new LinearLayout(this);
        homeContent.setOrientation(LinearLayout.VERTICAL);
        homeContent.setPadding(dp(40), 0, dp(20), dp(32));
        homeContent.setClipChildren(false);
        homeContent.setClipToPadding(false);
        homeContent.setBackgroundColor(Color.TRANSPARENT);
        root.addView(homeContent, new LinearLayout.LayoutParams(-1, -2));

        View appsSpacer = new View(this);
        homeContent.addView(appsSpacer, new LinearLayout.LayoutParams(-1, dp(34)));

        HorizontalScrollView appScroll = new HorizontalScrollView(this) {
            @Override protected int computeScrollDeltaToGetChildRectOnScreen(android.graphics.Rect rect) {
                android.graphics.Rect focusBounds = new android.graphics.Rect(rect);
                focusBounds.inset(-dp(12), 0);
                return super.computeScrollDeltaToGetChildRectOnScreen(focusBounds);
            }
        };
        appScroll.setHorizontalScrollBarEnabled(false);
        appScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        // Keep focus clearance inside the scroll content: the viewport clips its child bounds.
        appScroll.setClipChildren(true);
        appScroll.setClipToPadding(false);
        appRow = new LinearLayout(this);
        appRow.setOrientation(LinearLayout.HORIZONTAL);
        appRow.setPadding(dp(12), dp(14), dp(12), dp(14));
        appRow.setClipChildren(false);
        appRow.setClipToPadding(false);
        appScroll.setFillViewport(true);
        appRow.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        appScroll.addView(appRow);
        homeContent.addView(appScroll, new LinearLayout.LayoutParams(-1, dp(92)));

        LinearLayout gamesHeading = new LinearLayout(this);
        gamesHeading.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        gamesHeading.setClipChildren(false);
        gamesHeading.setClipToPadding(false);
        Button seeAllGames = actionButton("Import games");
        seeAllGames.setOnFocusChangeListener(LiquidTheme::animateContainedFocus);
        seeAllGames.setOnClickListener(v -> openGameLibrary());
        linkTabEntry(TAB_GAMES, seeAllGames);
        LinearLayout.LayoutParams seeAllParams = new LinearLayout.LayoutParams(dp(172), dp(46));
        seeAllParams.setMargins(dp(4), dp(4), dp(12), dp(4));
        gamesHeading.addView(seeAllGames, seeAllParams);
        LinearLayout.LayoutParams gamesHeadingParams = new LinearLayout.LayoutParams(-1, dp(56));
        gamesHeadingParams.topMargin = dp(14);
        gamesContent.addView(gamesHeading, gamesHeadingParams);
        gamesEmpty = label("No games yet. Import your own .nes files to get started.", 17, MUTED, false);
        gamesEmpty.setPadding(dp(8), dp(20), 0, dp(20));
        gamesContent.addView(gamesEmpty);
        gameGrid = new GridLayout(this);
        gameGrid.setColumnCount(5);
        gameGrid.setClipChildren(false);
        gameGrid.setClipToPadding(false);
        gamesContent.addView(gameGrid, new LinearLayout.LayoutParams(-1, -2));
        renderGames();

        applyWallpaper();

        if (liveContent.getChildCount() > 0) liveContent.getChildAt(0).setVisibility(View.GONE);
        LinearLayout liveHeading = new LinearLayout(this);
        liveHeading.setOrientation(LinearLayout.HORIZONTAL);
        liveHeading.setGravity(Gravity.CENTER_VERTICAL);
        liveHeading.setClipChildren(false);
        liveHeading.setClipToPadding(false);
        guideSection = liveHeading;
        LinearLayout titleGroup = new LinearLayout(this);
        titleGroup.setOrientation(LinearLayout.VERTICAL);
        titleGroup.setGravity(Gravity.CENTER_VERTICAL);
        TextView liveTitle = label("Live TV", 32, LiquidTheme.text(this), true);
        liveTitle.setTypeface(LiquidTheme.font(this));
        titleGroup.addView(liveTitle);
        guideEmpty = label("Explore the schedule, see what's on now, and set reminders.", 13, MUTED, false);
        titleGroup.addView(guideEmpty);
        liveHeading.addView(titleGroup, new LinearLayout.LayoutParams(0, -2, 1));

        guideToggle = actionButton("Open TV guide  →");
        guideToggle.setContentDescription("Open full-screen TV guide");
        guideToggle.setOnFocusChangeListener(LiquidTheme::animateContainedFocus);
        guideToggle.setOnClickListener(view -> openTvGuide());
        linkTabEntry(TAB_TV, guideToggle);
        LinearLayout.LayoutParams guideToggleParams = new LinearLayout.LayoutParams(dp(195), dp(44));
        guideToggleParams.setMargins(dp(4), dp(2), dp(4), dp(2));
        liveHeading.addView(guideToggle, guideToggleParams);

        LinearLayout.LayoutParams liveHeadingParams = new LinearLayout.LayoutParams(-1, -2);
        liveHeadingParams.bottomMargin = dp(4);
        liveContent.addView(liveHeading, liveHeadingParams);

        channelRows = new LinearLayout(this);
        channelRows.setOrientation(LinearLayout.VERTICAL);
        channelRows.setClipChildren(false);
        channelRows.setClipToPadding(false);
        liveContent.addView(channelRows, new LinearLayout.LayoutParams(-1, -2));
        channelCategoryScroll = new HorizontalScrollView(this);
        channelCategoryScroll.setHorizontalScrollBarEnabled(false);
        channelCategoryScroll.setClipChildren(false);
        channelCategoryScroll.setClipToPadding(false);
        channelCategoryScroll.setPadding(dp(4), dp(4), dp(4), dp(4));
        channelCategoryTabs = new LinearLayout(this);
        channelCategoryTabs.setOrientation(LinearLayout.HORIZONTAL);
        channelCategoryTabs.setGravity(Gravity.CENTER_VERTICAL);
        channelCategoryTabs.setClipChildren(false);
        channelCategoryTabs.setClipToPadding(false);
        channelCategoryScroll.addView(channelCategoryTabs, new HorizontalScrollView.LayoutParams(-2, -1));
        channelRows.addView(channelCategoryScroll, new LinearLayout.LayoutParams(-1, dp(50)));
        channelsEmpty = label("Add a playlist in Settings to browse live TV.", 15, MUTED, false);
        channelsEmpty.setPadding(0, dp(16), 0, dp(20));
        status = label("Add an M3U and optional XMLTV URL in Settings to get started.", 15, MUTED, false);
        status.setPadding(0, dp(12), 0, 0);
        liveContent.addView(status);

        youtubeSection = new LinearLayout(this);
        youtubeSection.setOrientation(LinearLayout.VERTICAL);
        youtubeSection.setClipChildren(false);
        youtubeSection.setClipToPadding(false);
        LinearLayout youtubeTitleRow = new LinearLayout(this);
        youtubeTitleRow.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        youtubeTitleRow.setClipChildren(false);
        youtubeTitleRow.setClipToPadding(false);
        Button addYouTube = actionButton("Add shortcut");
        addYouTube.setOnFocusChangeListener(LiquidTheme::animateContainedFocus);
        addYouTube.setOnClickListener(v -> openYouTubeSettings());
        linkTabEntry(TAB_SAVED, addYouTube);
        LinearLayout.LayoutParams addYouTubeParams = new LinearLayout.LayoutParams(dp(175), dp(46));
        addYouTubeParams.setMargins(dp(4), dp(4), dp(12), dp(4));
        youtubeTitleRow.addView(addYouTube, addYouTubeParams);

        youtubeTitleRow.setPadding(0, dp(4), 0, 0);
        LinearLayout.LayoutParams youtubeSectionParams = new LinearLayout.LayoutParams(-1, -2);
        youtubeSectionParams.topMargin = dp(16);
        youtubeSection.addView(youtubeTitleRow, new LinearLayout.LayoutParams(-1, dp(56)));
        HorizontalScrollView youtubeScroll = new HorizontalScrollView(this);
        youtubeScroll.setHorizontalScrollBarEnabled(false);
        youtubeScroll.setPadding(dp(8), dp(10), dp(8), dp(10));
        youtubeScroll.setClipChildren(false);
        youtubeScroll.setClipToPadding(false);
        youtubeRow = new LinearLayout(this);
        youtubeRow.setOrientation(LinearLayout.HORIZONTAL);
        youtubeRow.setClipChildren(false);
        youtubeRow.setClipToPadding(false);
        youtubeScroll.addView(youtubeRow);
        youtubeSection.addView(youtubeScroll, new LinearLayout.LayoutParams(-1, dp(230)));
        savedContent.addView(youtubeSection, youtubeSectionParams);
        youtubeEmpty = label("No saved YouTube shortcuts yet. Add a video or playlist in Settings.", 17, MUTED, false);
        youtubeEmpty.setPadding(dp(8), dp(16), 0, dp(16));
        savedContent.addView(youtubeEmpty);
        renderYouTubeCards();

        buildEventsTab();

        renderGuide();
        selectTab(activeTab, false);
    }

    @androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
    private void openTvGuide() {
        startActivityForResult(new Intent(this, GuideActivity.class).putExtra("category", selectedChannelCategory), 7410);
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
            if (activeTab == tab) browsingShade.setAlpha(Math.min(.94f,
                    (LiquidTheme.isLight(this) ? .88f : .65f) + (float) y / dp(600) * .2f));
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

    private void linkTopControl(int tab, View control) {
        if (control == null || tab < TAB_HOME || tab > LAST_TAB) return;
        if (control.getId() == View.NO_ID) control.setId(View.generateViewId());
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (quickSettings != null && quickSettings.isOpen()) return quickSettings.handleKey(event);
        if (activeTab == TAB_HARAM && haramView != null && haramView.getVisibility() == View.VISIBLE
                && settingsScreen == null && haramView.handleKey(event)) return true;
        if (activeTab == TAB_GALLERY && galleryView != null && galleryView.getVisibility() == View.VISIBLE
                && settingsScreen == null && galleryView.handleKey(event)) return true;
        // The Quran player handles its own remote keys, so the launcher's page navigation never steals them.
        if (activeTab == TAB_QURAN && quranView != null && quranView.getVisibility() == View.VISIBLE
                && settingsScreen == null) {
            if (quranView.handleKey(event)) return true;
            if (event.getKeyCode() != KeyEvent.KEYCODE_BACK && event.getKeyCode() != KeyEvent.KEYCODE_HOME)
                return super.dispatchKeyEvent(event);
        }
        if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_CENTER || event.getKeyCode() == KeyEvent.KEYCODE_ENTER || event.getKeyCode() == KeyEvent.KEYCODE_NUMPAD_ENTER) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                event.startTracking();
            }
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0 && settingsScreen == null
                && (event.getKeyCode() == KeyEvent.KEYCODE_MENU || event.getKeyCode() == KeyEvent.KEYCODE_SETTINGS)) {
            if (quickSettings != null) quickSettings.open(); else showSettings();
            return true;
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_STAR || event.getKeyCode() == KeyEvent.KEYCODE_BOOKMARK
                    || event.getKeyCode() == KeyEvent.KEYCODE_PROG_YELLOW || event.getKeyCode() == KeyEvent.KEYCODE_BUTTON_Y) {
                View focused = getCurrentFocus();
                if (focused != null && focused.performLongClick()) return true;
            }
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN && isDpadDirection(event.getKeyCode())
                && tabsInitialized && settingsScreen == null) {
            View focused = getCurrentFocus();
            int direction = focusDirection(event.getKeyCode());
            if (activeTab == TAB_EVENTS && focused != null
                    && handleCalendarDirection(focused, direction)) return true;
            if (focused != null && tabRow != null && isWithinPage(focused, tabRow)) {
                if (direction == View.FOCUS_UP) {
                    if (activeTab == TAB_TV) {
                        ChannelRow channelRow = channelRowMap.get(selectedChannelCategory);
                        if (channelRow != null && channelRow.requestFocus()) return true;
                        Button categoryTab = channelTabMap.get(selectedChannelCategory);
                        if (categoryTab != null && categoryTab.requestFocus()) return true;
                        if (guideToggle != null && guideToggle.requestFocus()) return true;
                    }
                    View entry = tabEntryFocus[activeTab];
                    if (entry != null && entry.isShown() && entry.isEnabled() && entry.requestFocus()) return true;
                    return true;
                }
                if (direction == View.FOCUS_LEFT || direction == View.FOCUS_RIGHT) {
                    View next = focused.focusSearch(direction);
                    boolean tabSibling = next != null && isWithinPage(next, tabRow);
                    boolean settingsEntry = direction == View.FOCUS_RIGHT && next != null
                            && isWithinPage(next, systemShortcuts);
                    if ((tabSibling || settingsEntry)
                            && next.isFocusable() && next.isEnabled() && next.requestFocus()) return true;
                }
                return true;
            }
            if (focused != null && systemShortcuts != null && isWithinPage(focused, systemShortcuts)) {
                if (direction == View.FOCUS_UP) {
                    View entry = tabEntryFocus[activeTab];
                    if (entry != null && entry.isShown() && entry.isEnabled() && entry.requestFocus()) return true;
                    return true;
                }
                if (direction == View.FOCUS_LEFT || direction == View.FOCUS_RIGHT) {
                    View next = focused.focusSearch(direction);
                    boolean shortcutSibling = next != null && next.getParent() == systemShortcuts;
                    boolean tabReturn = direction == View.FOCUS_LEFT && next != null
                            && isWithinPage(next, tabRow);
                    if ((shortcutSibling || tabReturn)
                            && next.isFocusable() && next.isEnabled() && next.requestFocus()) return true;
                }
                return true;
            }
            View page = tabPages[activeTab];
            if (focused != null && page != null && isWithinPage(focused, page)) {
                if (activeTab == TAB_TV) {
                    if (focused == guideToggle && direction == View.FOCUS_DOWN) {
                        Button categoryTab = channelTabMap.get(selectedChannelCategory);
                        if (categoryTab == null && channelCategoryTabs != null && channelCategoryTabs.getChildCount() > 0)
                            categoryTab = (Button) channelCategoryTabs.getChildAt(0);
                        if (categoryTab != null && categoryTab.requestFocus()) return true;
                    }
                    if (channelCategoryTabs != null && isWithinPage(focused, channelCategoryTabs)) {
                        if (direction == View.FOCUS_UP) {
                            if (guideToggle != null && guideToggle.requestFocus()) return true;
                        } else if (direction == View.FOCUS_DOWN) {
                            ChannelRow channelRow = channelRowMap.get(selectedChannelCategory);
                            if (channelRow != null && channelRow.requestFocus()) return true;
                        }
                    }
                    ChannelRow channelRow = channelRowMap.get(selectedChannelCategory);
                    if (channelRow != null && isWithinPage(focused, channelRow.container)) {
                        if (direction == View.FOCUS_UP) {
                            Button categoryTab = channelTabMap.get(selectedChannelCategory);
                            if (categoryTab == null && channelCategoryTabs != null && channelCategoryTabs.getChildCount() > 0)
                                categoryTab = (Button) channelCategoryTabs.getChildAt(0);
                            if (categoryTab != null && categoryTab.requestFocus()) return true;
                        } else if (direction == View.FOCUS_DOWN) {
                            Button destination = navigationButton(TAB_TV);
                            if (destination != null && destination.requestFocus()) return true;
                        }
                    }
                }
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
                && (isWithinPage(focused, tabRow) || isWithinPage(focused, systemShortcuts))) {
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
        if (dayBackdrop != null) dayBackdrop.setVisibility(selectedTab == TAB_HOME && ModernTheme.modern(preferences)
                && ModernTheme.day(preferences) ? View.VISIBLE : View.GONE);
        if (launcherShell != null) launcherShell.setDescendantFocusability(fullScreen
                ? ViewGroup.FOCUS_BLOCK_DESCENDANTS : ViewGroup.FOCUS_AFTER_DESCENDANTS);
        if (selectedTab == TAB_QURAN && quranView != null) quranView.post(() -> quranView.entryFocus().requestFocus());
        if (selectedTab == TAB_HARAM && haramView != null) haramView.post(() -> haramView.entryFocus().requestFocus());
        if (selectedTab == TAB_GALLERY && galleryView != null) galleryView.post(() -> galleryView.entryFocus().requestFocus());
        // On the prayer page the whole screen, bar included, takes the page's own calm colour.
        browsingShade.setBackgroundColor(selectedTab == TAB_PRAYER ? PrayerView.BACKGROUND
                : (LiquidTheme.isLight(this) ? 0xFFE9F1EE : 0xFF071417));
        browsingShade.setAlpha(selectedTab == TAB_PRAYER ? 1f
                : selectedTab == TAB_HOME ? (LiquidTheme.isLight(this) ? .50f : 0f)
                : (LiquidTheme.isLight(this) ? .88f : .65f));
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

    /** The adhan-to-iqama pill: on Home in «بسيط ١» (in «بسيط ٢» the prayer card counts down), the Quran and the wallpapers. */
    private boolean iqamaPillAllowed(int tab) {
        if (tab == TAB_HOME) return !ModernTheme.modern(preferences) || !ModernTheme.card(preferences, "prayer");
        return tab == TAB_QURAN || tab == TAB_GALLERY;
    }

    /** Switches between «بسيط ١» and «بسيط ٢» and applies the day/night look, font, cards and tabs. */
    private void applyHomeTheme() {
        boolean modern = ModernTheme.modern(preferences);
        boolean day = modern && ModernTheme.day(preferences);
        modernGlyphs = modern;
        if (root != null) root.setVisibility(modern ? View.GONE : View.VISIBLE);
        // The old top bar lives beside root on the same page, so it must be hidden on its own.
        if (homeTopBar != null) homeTopBar.setVisibility(modern ? View.GONE : View.VISIBLE);
        if (modernHome != null) {
            modernHome.setVisibility(modern ? View.VISIBLE : View.GONE);
            if (modern) modernHome.rebuild();
        }
        if (heroSideShade != null) heroSideShade.setVisibility(modern ? View.GONE : View.VISIBLE);
        if (heroBottomFade != null) heroBottomFade.setVisibility(modern ? View.GONE : View.VISIBLE);
        if (dayBackdrop != null) {
            if (day && dayBackdrop.getDrawable() == null) {
                int width = getResources().getDisplayMetrics().widthPixels;
                lifecycleWork.execute(ioWorker, () -> {
                    android.graphics.Bitmap bitmap = null;
                    try (java.io.InputStream in = getAssets().open("damask/day.jpg")) {
                        BitmapFactory.Options options = new BitmapFactory.Options();
                        options.inSampleSize = width <= 1920 ? 2 : 1;
                        bitmap = BitmapFactory.decodeStream(in, null, options);
                    } catch (Exception | OutOfMemoryError ignored) { }
                    final android.graphics.Bitmap loaded = bitmap;
                    lifecycleWork.post(() -> { if (loaded != null) dayBackdrop.setImageBitmap(loaded); });
                });
            }
            dayBackdrop.setVisibility(day && activeTab == TAB_HOME ? View.VISIBLE : View.GONE);
        }
        styleNavigation(modern, day);
        rebuildNavigationButtons();
        for (int i = 0; i < tabButtons.length; i++) {
            if (tabButtons[i] != null && navigationGlyphs[i] != null) {
                navigationGlyphs[i].setGlyphColor(tabTextColor(i == activeTab));
                navigationGlyphs[i].invalidateSelf();
            }
        }
        if (iqamaPill != null) iqamaPill.setAllowed(iqamaPillAllowed(activeTab));
        renderWeather();
        updateHomeCalendarWidget();
        renderApps();
    }

    /** «بسيط ٢»: icons only, larger, on a glass pill. «بسيط ١»: the labelled transparent bar. */
    private void styleNavigation(boolean modern, boolean day) {
        if (navbarView != null) {
            if (modern) {
                GradientDrawable pill = new GradientDrawable();
                pill.setColor(day ? 0x99FFFFFF : 0xC0141820);
                pill.setCornerRadius(dp(20));
                pill.setStroke(Math.max(1, dp(1)), day ? 0xCCFFFFFF : 0x22FFFFFF);
                navbarView.setBackground(pill);
            } else {
                navbarView.setBackground(null);
            }
        }
        for (int i = 0; i < tabButtons.length; i++) {
            Button button = tabButtons[i];
            if (button == null || navigationGlyphs[i] == null) continue;
            int size = modern ? dp(21) : dp(16);
            navigationGlyphs[i].setBounds(0, 0, size, size);
            button.setText(modern ? "" : TAB_TITLES[i]);
            button.setContentDescription(TAB_TITLES[i]);
            button.setCompoundDrawablePadding(modern ? 0 : dp(6));
            int pad = modern ? dp(16) : dp(compactNavigation ? 12 : (i == TAB_HOME ? 18 : 10));
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
        if (!ModernTheme.modern(preferences) || first == null) return;
        tabEntryFocus[TAB_HOME] = first;
        tabButtons[TAB_HOME].setNextFocusUpId(first.getId());
        for (int i = 0; i < tabButtons.length; i++) if (tabButtons[i] != null) tabButtons[i].setNextFocusUpId(first.getId());
        if (activeTab == TAB_HOME && settingsScreen == null && (quickSettings == null || !quickSettings.isOpen())) {
            View target = modernHome.tileFor(modernHome.focusedTag());
            View current = getCurrentFocus();
            if (target != null && (current == null || !current.isShown() || current.getParent() == appRow))
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
        boolean light = LiquidTheme.isLight(this) || (preferences != null && ModernTheme.modern(preferences) && ModernTheme.day(preferences));
        return selected ? (light ? 0xFF1E2A2A : 0xFFF4EFE6) : (light ? 0x9924403D : 0x8CFFFFFF);
    }

    private boolean isNavigationHidden(int tab) {
        if (isRemovedTab(tab)) return true;
        String key = tab == TAB_TV ? PREF_HIDE_LIVE_TV : tab == TAB_GAMES ? PREF_HIDE_GAMES
                : tab == TAB_EVENTS ? PREF_HIDE_EVENTS : null;
        return key != null && preferences.getBoolean(key, false);
    }

    private Button navigationButton(int tab) {
        return isNavigationHidden(tab) ? tabButtons[TAB_HOME] : tabButtons[tab];
    }

    private List<Integer> navigationOrder() {
        return NavigationPreferences.order(preferences.getString(NavigationPreferences.ORDER, null),
                preferences.getInt(PREF_LIVE_TV_POSITION, 0));
    }

    private void rebuildNavigationButtons() {
        if (tabRow == null) return;
        tabRow.removeAllViews();
        ArrayList<Integer> visible = new ArrayList<>();
        visible.add(TAB_HOME);
        if (ModernTheme.modern(preferences)) {
            for (String key : ModernTheme.tabs(preferences)) visible.add(tabForKey(key));
        } else {
            visible.add(TAB_PRAYER);
            for (int tab : navigationOrder()) if (!isNavigationHidden(tab)) visible.add(tab);
        }
        // Right-to-left: the first tab sits at the right end of the bar.
        for (int i = visible.size() - 1; i >= 0; i--)
            tabRow.addView(tabButtons[visible.get(i)], new LinearLayout.LayoutParams(-2, dp(32)));
    }

    private void openYouTubeSettings() {
        if (settingsScreen == null) settingsScreen = new SettingsScreen();
        settingsScreen.section = SettingsScreen.YOUTUBE;
        settingsScreen.show();
    }

    private void openCalendarSettings() {
        if (settingsScreen == null) settingsScreen = new SettingsScreen();
        settingsScreen.section = SettingsScreen.CALENDAR;
        settingsScreen.show();
    }

    private void buildEventsTab() {
        // Events is a dedicated month view, so let its own month header lead.
        if (eventsContent.getChildCount() > 0) eventsContent.getChildAt(0).setVisibility(View.GONE);
        eventsContent.setPadding(dp(28), dp(20), dp(28), dp(12));
        LinearLayout monthHeading = new LinearLayout(this);
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
        calendarHeaderDate = label("", 10, LiquidTheme.accent(this), true);
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

        calendarMonthGrid = new GridLayout(this) {
            private final Paint rulePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

            @Override protected void dispatchDraw(Canvas canvas) {
                rulePaint.setColor(LiquidTheme.isLight(MainActivity.this) ? 0x55708B88 : 0x558FAEAC);
                rulePaint.setStrokeWidth(dp(1));
                if (getChildCount() > 7) {
                    int top = getChildAt(7).getTop();
                    for (int i = 7; i < getChildCount(); i += 7) {
                        int y = getChildAt(i).getTop();
                        canvas.drawLine(0, y, getWidth(), y, rulePaint);
                    }
                    for (int i = 8; i < Math.min(14, getChildCount()); i++) {
                        int x = getChildAt(i).getLeft();
                        canvas.drawLine(x, top, x, getHeight(), rulePaint);
                    }
                }
                super.dispatchDraw(canvas);
            }
        };
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
            case SaudiCalendar.PERSONAL: return 0xDD8E1F4E;
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
            case SaudiCalendar.PERSONAL: return 0xFFC2185B;
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
        renderedMonth = displayedMonth;
        renderedToday = LocalDate.now();
        renderedCalendarStamp = preferences.getLong("publicCalendarUpdatedAt", 0L);
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
            heading.setLetterSpacing(.1f);
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
        int fittedHeight = (Math.max(300, screenHeightDp - 205) - 34) / rows - 7;
        int cellHeight = Math.max(58, Math.min(94, Math.min(cellWidth, fittedHeight)));
        for (int visual = 0; visual < cells; visual++) {
            int i = (visual / 7) * 7 + (6 - visual % 7);
            int dayNumber = i - offset + 1;
            View cell;
            if (dayNumber < 1 || dayNumber > displayedMonth.lengthOfMonth()) {
                TextView overflow = label(String.valueOf(dayNumber < 1
                        ? displayedMonth.minusMonths(1).lengthOfMonth() + dayNumber
                        : dayNumber - displayedMonth.lengthOfMonth()), 20,
                        light ? 0x554D6968 : 0xFF78818A, false);
                overflow.setGravity(Gravity.TOP | Gravity.RIGHT);
                overflow.setPadding(0, dp(8), dp(13), 0);
                overflow.setBackgroundColor(Color.TRANSPARENT);
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
            params.width = dp(cellWidth);
            params.height = dp(cellHeight);
            params.setMargins(0, 0, 0, 0);
            calendarMonthGrid.addView(cell, params);
        }
        updateCalendarDaySelection();
        updateCalendarDetails(events);
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
        updateCalendarDetails(cachedCalendarEvents());
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
        states.addState(new int[]{android.R.attr.state_focused}, calendarDayHighlight(true));
        states.addState(new int[]{android.R.attr.state_pressed}, calendarDayHighlight(true));
        states.addState(new int[]{}, selected ? calendarDayHighlight(false)
                : new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
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

    private Drawable calendarDayHighlight(boolean focused) {
        return new Drawable() {
            private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override public void draw(Canvas canvas) {
                Rect bounds = getBounds();
                paint.setColor(LiquidTheme.isLight(MainActivity.this) ? 0x22087D91 : 0x227BE7F4);
                canvas.drawRect(bounds, paint);
                paint.setColor(LiquidTheme.accent(MainActivity.this));
                canvas.drawRect(bounds.left + dp(10), bounds.bottom - dp(focused ? 3 : 2),
                        bounds.right - dp(10), bounds.bottom, paint);
            }
            @Override public void setAlpha(int alpha) { }
            @Override public void setColorFilter(android.graphics.ColorFilter filter) { }
            @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
        };
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

private void updateCalendarDetails(List<PublicCalendarRepository.Event> events) {
    if (calendarDetailDate == null || selectedCalendarDay == null) return;

    CalendarDates.MoonPhase moon =
            CalendarDates.moonPhase(selectedCalendarDay);

    calendarDetailDate.setText(
            String.valueOf(selectedCalendarDay.getDayOfMonth())
    );

    calendarDetailDay.setText(
            selectedCalendarDay.getDayOfWeek().getDisplayName(
                    TextStyle.FULL,
                    Locale.getDefault()
            )
            + "  ·  "
            + selectedCalendarDay.getMonth().getDisplayName(
                    TextStyle.FULL,
                    Locale.getDefault()
            )
    );

    String hijriDate = ArabicFormat.hijri(selectedCalendarDay);
    if (!hijriDate.isEmpty()) calendarDetailDay.append("\n" + hijriDate);

    calendarDetailMoon.setText(moon.symbol);
    calendarDetailMoonName.setText(moon.name);
    calendarDetailIllumination.setText(
            "Lunar illumination  ·  " + moon.illumination + "%"
    );

    // ---------------------------------------------------------
    // Find ALL events for selected day
    // ---------------------------------------------------------

    ArrayList<String> titles = new ArrayList<>();
    ZoneId zone = ZoneId.systemDefault();

    for (PublicCalendarRepository.Event event : events) {
        if (CalendarDates.includes(event, selectedCalendarDay, zone)) {
            titles.add(event.title);
        }
    }

    // ---------------------------------------------------------
    // Display events
    // ---------------------------------------------------------

    if (titles.isEmpty()) {

        calendarDetailEvent.setVisibility(View.GONE);

    } else {

        StringBuilder text = new StringBuilder(" \n");

        for (String title : titles) {
            text.append("\n ").append(title);
        }
         text.append("\n ");

        calendarDetailEvent.setText(text.toString());
        calendarDetailEvent.setVisibility(View.VISIBLE);
    }
}

    private void addCalendarEventCard(LinearLayout parent, PublicCalendarRepository.Event event) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(11), dp(16), dp(11));
        card.setBackground(roundedShape(LiquidTheme.surface(this), LiquidTheme.border(this), 1, 15));
        card.addView(label(event.title, 17, LiquidTheme.text(this), true));
        DateFormat format = preferences.getBoolean("24h", false)
                ? new java.text.SimpleDateFormat("HH:mm", Locale.getDefault())
                : new java.text.SimpleDateFormat("h:mm a", Locale.getDefault());
        String timing = event.allDay ? "طوال اليوم" : format.format(new Date(event.startMs))
                + " – " + format.format(new Date(event.endMs));
        card.addView(label(timing, 13, LiquidTheme.secondaryText(this), false));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(8);
        parent.addView(card, params);
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
    private boolean today;

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

        // Large date number.
        datePaint.setTextSize(sp(20));
        datePaint.setTypeface(LiquidTheme.font(MainActivity.this));

        // MON / TUE / WED...
        weekdayPaint.setTextSize(sp(9));
        weekdayPaint.setTypeface(LiquidTheme.font(MainActivity.this));

        // Moon phase.
        moonPaint.setTextSize(sp(9));
        moonPaint.setTypeface(LiquidTheme.font(MainActivity.this));

        // Holiday/event.
        eventPaint.setTextSize(sp(8));
        eventPaint.setTypeface(LiquidTheme.font(MainActivity.this));
        chipTextPaint.setTextSize(sp(9));
        chipTextPaint.setTypeface(LiquidTheme.font(MainActivity.this));
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
        datePaint.setTextAlign(Paint.Align.RIGHT);
        if (today) {
            // Today: the date sits in a gold disc, in the same gold as the bar's underline.
            float textWidth = datePaint.measureText(String.valueOf(dayNumber));
            float cx = right - textWidth / 2f, cy = row1Baseline + (dateFm.ascent + dateFm.descent) / 2f;
            float radius = Math.max(textWidth, -dateFm.ascent) / 2f + dp(6);
            todayPaint.setColor(0xFFE3B469);
            canvas.drawCircle(cx, cy, radius, todayPaint);
            datePaint.setColor(0xFF1B2024);
        }
        canvas.drawText(String.valueOf(dayNumber), right - (today ? dp(2) : 0), row1Baseline, datePaint);
        datePaint.setColor(primary);
        datePaint.setTextAlign(Paint.Align.LEFT);

        if (!hijriLabel.isEmpty()) {
            hijriPaint.setTextSize(sp(11));
            hijriPaint.setColor(secondary);
            hijriPaint.setTextAlign(Paint.Align.LEFT);
            canvas.drawText(hijriLabel, left + dp(2), row1Baseline, hijriPaint);
        }


        float barHeight = dp(4);
        float chipLimit = getHeight() - getPaddingBottom() - dp(2) - bars.size() * (barHeight + dp(2));

        // Single-day occasions as chips: dot on the right, text reading right to left.
        float chipTop = row1Baseline + dp(3);
        float chipHeight = dp(13);
        for (int i = 0; i < eventTitles.size(); i++) {
            if (chipTop + chipHeight > chipLimit) break;
            String eventTitle = eventTitles.get(i);
            chipPaint.setColor(calendarEventColor(eventTitle));
            canvas.drawRoundRect(left, chipTop, right, chipTop + chipHeight, dp(7), dp(7), chipPaint);
            todayPaint.setColor(calendarEventDotColor(eventTitle));
            canvas.drawCircle(right - dp(6), chipTop + chipHeight / 2f, dp(3), todayPaint);
            String title = ellipsize(eventTitle, chipTextPaint, right - left - dp(20));
            Paint.FontMetrics chipMetrics = chipTextPaint.getFontMetrics();
            float baseline = chipTop + (chipHeight - chipMetrics.bottom + chipMetrics.top) / 2f - chipMetrics.top;
            chipTextPaint.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(title, right - dp(12), baseline, chipTextPaint);
            chipTextPaint.setTextAlign(Paint.Align.LEFT);
            chipTop += chipHeight + dp(2);
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
            else if (color == MUTED) color = LiquidTheme.secondaryText(this);
            else if (color == ACCENT) color = LiquidTheme.accent(this);
            else if (color == LAVENDER) color = LiquidTheme.lavender(this);
        }
        v.setTextColor(color);
        v.setTypeface(LiquidTheme.font(this));
        return v;
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

    private void stylePrimaryButton(Button button) {
        StateListDrawable states = new Motion.States();
        states.addState(new int[]{android.R.attr.state_focused}, roundedShape(LiquidTheme.raisedSurface(this), LiquidTheme.accent(this), 2, 18));
        states.addState(new int[]{android.R.attr.state_pressed}, roundedShape(LiquidTheme.raisedSurface(this), LiquidTheme.accent(this), 2, 18));
        states.addState(new int[]{}, roundedShape(LiquidTheme.raisedSurface(this), LiquidTheme.accent(this), 1, 18));
        button.setBackground(states);
        button.setTextColor(LiquidTheme.text(this));
        button.setTypeface(LiquidTheme.font(this));
        button.setLetterSpacing(.035f);
        button.setOnFocusChangeListener(LiquidTheme::animateFocus);
    }

    private void updateClock() {
        if (clock == null) return;
        boolean twentyFour = preferences.getBoolean("24h", false);
        Date now = new Date();
        DateFormat time = android.text.format.DateFormat.getTimeFormat(this);
        if (twentyFour) time = new java.text.SimpleDateFormat("HH:mm", Locale.getDefault());
        else time = new java.text.SimpleDateFormat("h:mm a", Locale.getDefault());
        java.time.LocalDateTime localNow = java.time.LocalDateTime.now();
        String formattedTime = twentyFour ? ArabicFormat.digits(time.format(now)) : ArabicFormat.clock(localNow);
        String period = twentyFour ? "" : ArabicFormat.period(localNow);
        SpannableString styledTime = new SpannableString(formattedTime);
        int separator = formattedTime.indexOf(':');
        if (separator >= 0) {
            int minuteEnd = Math.min(formattedTime.length(), separator + 3);
            styledTime.setSpan(new ForegroundColorSpan(0xFFFFA94D), separator + 1, minuteEnd,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        clock.setText(formattedTime);
        clockPeriod.setText(period);
        clockPeriod.setVisibility(twentyFour ? View.GONE : View.VISIBLE);
        clockDivider.setVisibility(View.GONE);
        date.setVisibility(preferences.getBoolean("showDate", true)
                ? View.VISIBLE : View.GONE);
        date.setText(ArabicFormat.gregorian(localNow.toLocalDate()) + "  ·  " + ArabicFormat.hijri(localNow.toLocalDate()));
    }

    private void updateNowPlaying() {
        if (nowPlayingSection == null) return;
        String enabled = android.provider.Settings.Secure.getString(getContentResolver(),
                "enabled_notification_listeners");
        ComponentName listener = new ComponentName(this, PlaybackListenerService.class);
        boolean granted = enabled != null && enabled.contains(listener.flattenToString());
        activeMediaController = null;
        if (granted) {
            try {
                MediaSessionManager manager = (MediaSessionManager) getSystemService(MEDIA_SESSION_SERVICE);
                MediaController pausedController = null;
                if (manager != null) for (MediaController controller : manager.getActiveSessions(listener)) {
                    MediaMetadata metadata = controller.getMetadata();
                    PlaybackState state = controller.getPlaybackState();
                    if (metadata == null || state == null) continue;
                    if (state.getState() == PlaybackState.STATE_PLAYING) {
                        activeMediaController = controller;
                        break;
                    }
                    if (state.getState() == PlaybackState.STATE_PAUSED && pausedController == null)
                        pausedController = controller;
                }
                if (activeMediaController == null) activeMediaController = pausedController;
            } catch (SecurityException | IllegalArgumentException ignored) { }
        }
        MediaController controller = activeMediaController;
        boolean hasMedia = controller != null;
        nowPlayingSection.setVisibility(hasMedia ? View.VISIBLE : View.GONE);
        if (appRow != null) {
            int upId = (hasMedia && musicToggle != null) ? musicToggle.getId() : View.NO_ID;
            for (int i = 0; i < appRow.getChildCount(); i++) {
                appRow.getChildAt(i).setNextFocusUpId(upId);
            }
        }
        if (hasMedia && musicToggle != null && tabEntryFocus[TAB_HOME] != null) {
            musicToggle.setNextFocusDownId(tabEntryFocus[TAB_HOME].getId());
        }
        if (controller == null) return;
        MediaMetadata metadata = controller.getMetadata();
        PlaybackState state = controller.getPlaybackState();
        String title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE);
        String artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST);
        musicTitle.setText(TextUtils.isEmpty(title) ? controller.getPackageName() : title);
        musicArtist.setText(TextUtils.isEmpty(artist) ? "Playing on your TV" : artist);
        android.graphics.Bitmap artwork = metadata.getBitmap(MediaMetadata.METADATA_KEY_ART);
        if (artwork == null) artwork = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        if (artwork != null) musicArtwork.setImageBitmap(artwork);
        else musicArtwork.setImageResource(android.R.drawable.ic_media_play);
        long duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION);
        long position = state.getPosition();
        if (state.getState() == PlaybackState.STATE_PLAYING) {
            long elapsed = android.os.SystemClock.elapsedRealtime() - state.getLastPositionUpdateTime();
            position += Math.max(0, (long) (elapsed * state.getPlaybackSpeed()));
        }
        musicProgress.setProgress(duration > 0
                ? (int) Math.min(1000, Math.max(0, position * 1000 / duration)) : 0);
        boolean playing = state.getState() == PlaybackState.STATE_PLAYING;
        musicToggle.setText(playing ? "Ⅱ" : "▶");
        musicToggle.setContentDescription(playing ? "Pause playback" : "Resume playback");
    }

    private void toggleMediaPlayback() {
        if (activeMediaController == null || activeMediaController.getPlaybackState() == null) return;
        if (activeMediaController.getPlaybackState().getState() == PlaybackState.STATE_PLAYING)
            activeMediaController.getTransportControls().pause();
        else activeMediaController.getTransportControls().play();
        handler.postDelayed(this::updateNowPlaying, 250);
    }

    private void openMusicAccessSettings() {
        try {
            startActivity(new Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
        } catch (android.content.ActivityNotFoundException missing) {
            Toast.makeText(this, "Open Android Settings and allow Sva Now Playing notification access.",
                    Toast.LENGTH_LONG).show();
        }
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
        if (modernHome != null && ModernTheme.modern(preferences)) modernHome.setWeather(weatherForecast);
        if (weatherPanel == null) return;
        boolean light = LiquidTheme.isLight(this);
        if (weatherForecast == null) {
            weatherSymbol.setText("☀");
            weatherTemperature.setText(hasWeatherLocation() ? "الطقس غير متاح" : "الطقس غير محدد");
            weatherTemperature.setTextSize(compactNavigation ? 14 : 16);
            weatherTemperature.setTextColor(light ? LiquidTheme.text(this) : 0xFFF5FAF8);
            weatherCondition.setText(hasWeatherLocation() ? "حدّثه من الإعدادات" : "اختر مدينة من الإعدادات");
            weatherCondition.setVisibility(View.VISIBLE);
            weatherLocation.setText("");
            weatherMetrics.setText("");
            weatherDetails.setText("");
            weatherDetails.setVisibility(View.GONE);
            weatherPanel.setContentDescription(weatherTemperature.getText() + ". " + weatherCondition.getText());
            return;
        }
        boolean fahrenheit = preferences.getBoolean("weatherFahrenheit", false);
        weatherSymbol.setText(WeatherRepository.symbol(weatherForecast.code, weatherForecast.day));
        double temperature = fahrenheit ? WeatherRepository.fahrenheit(weatherForecast.temperature) : weatherForecast.temperature;
        weatherTemperature.setTextSize(compactNavigation ? 28 : 32);
        weatherTemperature.setTextColor(light ? LiquidTheme.text(this) : Color.WHITE);
        String temperatureText = ArabicFormat.digits(Math.round(temperature)) + "°";
        SpannableString styledTemperature = new SpannableString(temperatureText);
        int degree = temperatureText.indexOf('°');
        if (degree >= 0) styledTemperature.setSpan(new ForegroundColorSpan(0xFFFFA94D), degree,
                temperatureText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        weatherTemperature.setText(temperatureText);
        boolean mph = preferences.getBoolean("weatherMph", false);
        double wind = mph ? WeatherRepository.milesPerHour(weatherForecast.windSpeed) : weatherForecast.windSpeed;
        double apparent = fahrenheit ? WeatherRepository.fahrenheit(weatherForecast.apparentTemperature)
                : weatherForecast.apparentTemperature;
        weatherCondition.setText(WeatherRepository.conditionArabic(weatherForecast.code));
        weatherCondition.setVisibility(View.VISIBLE);
        weatherDetails.setText("المحسوسة " + ArabicFormat.digits(Math.round(apparent)) + "°");
        weatherDetails.setVisibility(View.VISIBLE);
        weatherLocation.setText(weatherForecast.location + "  ⌖");
        weatherMetrics.setText("الرياح " + ArabicFormat.digits(Math.round(wind)) + (mph ? " ميل/س" : " كم/س")
                + (weatherForecast.humidity >= 0 ? "  ·  الرطوبة " + ArabicFormat.digits(weatherForecast.humidity) + "٪" : ""));
        if (weatherForecast.rainToday >= 0) weatherDetails.append("  ·  " + (weatherForecast.rainToday >= 20
                ? "احتمال المطر " + ArabicFormat.digits(weatherForecast.rainToday) + "٪" : "لا أمطار متوقعة"));
        weatherPanel.setContentDescription(weatherTemperature.getText() + ". " + weatherCondition.getText()
                + ". " + weatherForecast.location + ". Wind " + Math.round(wind)
                + (mph ? " mph" : " km/h") + ".");
    }

    private void showWeatherForecast() {
        if (weatherForecast == null) {
            openWeatherSettings();
            return;
        }
        boolean fahrenheit = preferences.getBoolean("weatherFahrenheit", false);
        StringBuilder details = new StringBuilder();
        for (WeatherRepository.Day day : weatherForecast.days) {
            String weekday = day.date;
            try { weekday = new java.text.SimpleDateFormat("EEE", Locale.getDefault()).format(
                    new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(day.date)); } catch (Exception ignored) { }
            double low = fahrenheit ? WeatherRepository.fahrenheit(day.low) : day.low;
            double high = fahrenheit ? WeatherRepository.fahrenheit(day.high) : day.high;
            if (details.length() > 0) details.append("\n\n");
            details.append(weekday).append("  ·  ").append(Math.round(low)).append("° / ")
                    .append(Math.round(high)).append("°  ·  ").append(WeatherRepository.condition(day.code));
        }
        AlertDialog dialog = new AlertDialog.Builder(new android.view.ContextThemeWrapper(this,
                R.style.SvaDeviceAlertDialogTheme))
                .setTitle(weatherForecast.location)
                .setMessage(details.length() > 0 ? details.toString() : "Forecast unavailable")
                .setPositiveButton("Close", null)
                .create();
        Motion.dialog(dialog); dialog.show();
        styleDialog(dialog);
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
        if (settingsScreen != null) settingsScreen.setWeatherFeedback("Refreshing weather…");
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
                    if (settingsScreen != null) settingsScreen.setWeatherFeedback("Weather refreshed.");
                } else if (settingsScreen != null) settingsScreen.setWeatherFeedback("Refresh failed · " + resultError);
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

    private void openWeatherSettings() {
        if (settingsScreen == null) settingsScreen = new SettingsScreen();
        settingsScreen.section = SettingsScreen.WEATHER;
        settingsScreen.show();
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
        if (settingsScreen != null) settingsScreen.renderSection();
    }

    private void requestDeviceWeatherLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            pendingDeviceWeatherLocation = true;
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_WEATHER_LOCATION);
            return;
        }
        cancelWeatherLocation();
        LocationManager manager = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (manager == null) { if (settingsScreen != null) settingsScreen.setWeatherFeedback("Location is unavailable on this TV."); return; }
        String provider = manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ? LocationManager.NETWORK_PROVIDER
                : manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ? LocationManager.GPS_PROVIDER : null;
        if (provider == null) { if (settingsScreen != null) settingsScreen.setWeatherFeedback("Turn on Android location services first."); return; }
        if (settingsScreen != null) settingsScreen.setWeatherFeedback("Finding this TV’s location…");
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                weatherLocationCancellation = new CancellationSignal();
                manager.getCurrentLocation(provider, weatherLocationCancellation, getMainExecutor(), this::acceptWeatherLocation);
            } else {
                weatherLocationListener = location -> acceptWeatherLocation(location);
                manager.requestSingleUpdate(provider, weatherLocationListener, Looper.getMainLooper());
            }
            weatherLocationTimeout = () -> {
                cancelWeatherLocation();
                if (settingsScreen != null) settingsScreen.setWeatherFeedback("Location timed out. Try city search instead.");
            };
            handler.postDelayed(weatherLocationTimeout, 10000);
        } catch (SecurityException error) {
            if (settingsScreen != null) settingsScreen.setWeatherFeedback("Location permission was not granted.");
        }
    }

    private void acceptWeatherLocation(android.location.Location location) {
        cancelWeatherLocation();
        if (location == null) { if (settingsScreen != null) settingsScreen.setWeatherFeedback("Could not determine this TV’s location."); return; }
        double latitude = location.getLatitude(), longitude = location.getLongitude();
        lifecycleWork.execute(ioWorker, () -> {
            String label = "Current location";
            try {
                List<Address> addresses = new Geocoder(this, Locale.getDefault()).getFromLocation(latitude, longitude, 1);
                if (addresses != null && !addresses.isEmpty()) {
                    Address address = addresses.get(0);
                    String locality = address.getLocality() == null ? address.getSubAdminArea() : address.getLocality();
                    if (locality != null && !locality.trim().isEmpty()) label = locality;
                }
            } catch (Exception ignored) { }
            String result = label;
            lifecycleWork.post(() -> saveWeatherLocation("device", new WeatherRepository.Location(result, latitude, longitude)));
        });
    }

    private void cancelWeatherLocation() {
        if (weatherLocationTimeout != null) handler.removeCallbacks(weatherLocationTimeout);
        weatherLocationTimeout = null;
        if (weatherLocationCancellation != null) weatherLocationCancellation.cancel();
        weatherLocationCancellation = null;
        if (weatherLocationListener != null) {
            LocationManager manager = (LocationManager) getSystemService(LOCATION_SERVICE);
            if (manager != null) try { manager.removeUpdates(weatherLocationListener); } catch (Exception ignored) { }
        }
        weatherLocationListener = null;
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_WEATHER_LOCATION) return;
        boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        if (pendingDeviceWeatherLocation && granted) requestDeviceWeatherLocation();
        else if (!granted && settingsScreen != null) settingsScreen.setWeatherFeedback("Location denied. Search for a city instead.");
        pendingDeviceWeatherLocation = false;
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

    private void updateHomeCalendarWidget() {
        if (homeCalendarTitle == null || homeCalendarSubtitle == null) return;
        LocalDate today = LocalDate.now();
        PublicCalendarRepository.Event todayEvent = null;
        PublicCalendarRepository.Event nextEvent = null;
        for (PublicCalendarRepository.Event event : cachedCalendarEvents()) {
            if (CalendarDates.includes(event, today, ZoneId.systemDefault())) {
                todayEvent = event;
                break;
            }
            if (event.startMs > System.currentTimeMillis()) { nextEvent = event; break; }
        }
        if (todayEvent != null) {
            homeCalendarEyebrow.setText("اليوم");
            homeCalendarTitle.setText(todayEvent.title);
            homeCalendarSubtitle.setText(todayEvent.allDay ? ArabicFormat.hijri(today)
                    : new java.text.SimpleDateFormat("h:mm a", Locale.getDefault()).format(new Date(todayEvent.startMs)));
        } else if (nextEvent != null) {
            homeCalendarEyebrow.setText("القادم");
            homeCalendarTitle.setText(nextEvent.title);
            homeCalendarSubtitle.setText(nextEvent.allDay
                    ? ArabicFormat.gregorian(java.time.Instant.ofEpochMilli(nextEvent.startMs).atZone(ZoneId.systemDefault()).toLocalDate())
                    : new java.text.SimpleDateFormat("EEE · h:mm a", Locale.getDefault()).format(new Date(nextEvent.startMs)));
        } else {
            homeCalendarEyebrow.setText("TODAY");
            homeCalendarTitle.setText("لا مناسبات اليوم");
            homeCalendarSubtitle.setText(preferences.getString("publicCalendarUrl", "").isEmpty()
                    ? ArabicFormat.hijri(today) : ArabicFormat.hijri(today));
        }
        if (modernHome != null && ModernTheme.modern(preferences)) {
            modernHome.setOccasion(todayEvent != null ? "مناسبة اليوم" : "المناسبة القادمة",
                    todayEvent != null || nextEvent != null ? homeCalendarTitle.getText().toString() : "",
                    todayEvent != null || nextEvent != null ? homeCalendarSubtitle.getText().toString() : "");
        }
    }

    private String wrapHomeCalendarTitle(String value) {
        String clean = value == null ? "Calendar event" : value.trim().replaceAll("\\s+", " ");
        String[] words = clean.split(" ");
        StringBuilder result = new StringBuilder();
        StringBuilder line = new StringBuilder();
        int lineLength = 0;
        for (String word : words) {
            if (lineLength > 0 && lineLength + 1 + word.length() > 24) {
                result.append(line).append('\n');
                line.setLength(0);
                lineLength = 0;
            }
            if (lineLength > 0) { line.append(' '); lineLength++; }
            line.append(word);
            lineLength += word.length();
        }
        if (line.length() > 0) result.append(line);
        return result.toString();
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
        updateHomeCalendarWidget();
        if (calendarMonthGrid == null) return;
        List<PublicCalendarRepository.Event> cached = cachedCalendarEvents();
        LocalDate today = LocalDate.now();
        long stamp = preferences.getLong("publicCalendarUpdatedAt", 0L);
        if (today.equals(renderedToday) && displayedMonth.equals(renderedMonth)
                && stamp == renderedCalendarStamp) return;
        if (todayItems != null) todayItems.removeAllViews();
        int todayCount = 0;
        for (PublicCalendarRepository.Event event : cached) {
            if (CalendarDates.includes(event, today, ZoneId.systemDefault())) {
                if (todayItems != null) addCalendarEventCard(todayItems, event);
                todayCount++;
            }
        }
        boolean hasTodayEvents = todayCount > 0;
        if (todayHeading != null) todayHeading.setVisibility(View.VISIBLE);
        if (todayItems != null) todayItems.setVisibility(hasTodayEvents ? View.VISIBLE : View.GONE);
        if (todayEmpty != null) todayEmpty.setVisibility(hasTodayEvents ? View.GONE : View.VISIBLE);
        renderMonthCalendar(cached);
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
        calendarRefreshing = true;
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
                calendarRefreshing = false;
                calendarRefreshingUrl = "";
                if (isFinishing() || !url.equals(preferences.getString("publicCalendarUrl", ""))) return;
                if (resultError == null && resultEvents != null) {
                    saveCalendarEvents(resultEvents);
                    updateCalendarEventDisplay();
                    if (settingsScreen != null) settingsScreen.onCalendarRefreshResult(
                            "Calendar refreshed · " + resultEvents.size() + " events cached.");
                } else if (settingsScreen != null) {
                    settingsScreen.onCalendarRefreshResult("Calendar refresh failed · " + resultError);
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
        if (appRow == null || appsLoading) return;
        appsLoading = true;
        appRow.removeAllViews();
        TextView loading = label("جارٍ تحميل التطبيقات…", 15, MUTED, false);
        loading.setGravity(Gravity.CENTER_VERTICAL);
        appRow.addView(loading, new LinearLayout.LayoutParams(dp(180), dp(72)));
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
            lifecycleWork.post(() -> showApps(withIcons));
        });
    }

    private void showApps(List<AppEntry> all) {
        if (appRow == null || isFinishing()) return;
        appsLoading = false;
        appsLoaded = true;
        View previous = appRow.findFocus();
        if (previous != null && previous.getTag() instanceof String) focusedPackage = (String) previous.getTag();
        appRow.removeAllViews();
        boolean light = LiquidTheme.isLight(this);
        int tileWidth = compactNavigation ? 148 : 167;
        List<String> selected = selectedPackages(all);
        if (selected.isEmpty() && !preferences.getBoolean("customApps", false))
            selected.addAll(defaultApps(all));
        boolean quranTileAdded = false;
        int displayed = 0;
        for (String packageName : selected) {
            AppEntry entry = findApp(all, packageName);
            if (entry == null) continue;
            LinearLayout tile = new LinearLayout(this);
            tile.setId(View.generateViewId());
            tile.setGravity(Gravity.CENTER);
            tile.setOrientation(LinearLayout.HORIZONTAL);
            tile.setFocusable(true);
            tile.setClickable(true);
            StateListDrawable appTileStates = new Motion.States();
            // Calm, dark tiles for every app, with a round icon like the built-in tiles.
            boolean isYouTube = entry.packageName.startsWith("com.google.android.youtube");
            boolean isStremio = entry.packageName.equals("com.stremio.one");
            int rgb = isYouTube ? 0x2E2626 : isStremio ? 0x29263A : 0x262B30;
            int focusColor = 0xF2000000 | rgb;
            int unfocusedBg = 0xE0000000 | rgb;
            int unfocusedBorder = 0x66FFFFFF;
            int focusedBorder = GOLD_FOCUS;
            appTileStates.addState(new int[]{android.R.attr.state_focused},
                    roundedShape(focusColor, focusedBorder, 2, 16));
            appTileStates.addState(new int[]{android.R.attr.state_pressed},
                    roundedShape(focusColor, focusedBorder, 2, 16));
            appTileStates.addState(new int[]{},
                    roundedShape(unfocusedBg, unfocusedBorder, 1, 16));
            tile.setBackground(appTileStates);
            tile.setFocusableInTouchMode(false);
            tile.setElevation(dp(2));
            tile.setOnFocusChangeListener(LiquidTheme::animateFocus);
            Motion.interaction(tile, false);
            tile.setPadding(dp(8), dp(4), dp(10), dp(4));
            tile.setTag(packageName);
            View icon = new View(this);
            icon.setBackground(isYouTube ? new AppGlyph(AppGlyph.YOUTUBE) : isStremio ? new AppGlyph(AppGlyph.STREMIO)
                    : new RoundAppIcon(entry.icon));
            LinearLayout.LayoutParams appIconParams = new LinearLayout.LayoutParams(dp(38), dp(38));
            int nameColor = 0xFFF1ECE2;
            TextView name = label(entry.label, compactNavigation ? 12 : 13, nameColor, true);
            name.setTextColor(nameColor);
            name.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
            name.setMaxLines(1);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            // Right to left: the name, then the icon at the right edge.
            tile.addView(name, new LinearLayout.LayoutParams(0, -1, 1));
            appIconParams.leftMargin = dp(10);
            tile.addView(icon, appIconParams);
            tile.setOnClickListener(v -> launchApp(entry.packageName));
            if (nowPlayingSection != null && nowPlayingSection.getVisibility() == View.VISIBLE && musicToggle != null) {
                tile.setNextFocusUpId(musicToggle.getId());
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(tileWidth), dp(64));
            lp.setMargins(dp(10), 0, 0, 0);
            // Right to left: the first app sits at the right end of the row.
            appRow.addView(tile, 0, lp);
            // The Quran player sits right beside YouTube.
            if (!quranTileAdded && packageName.startsWith("com.google.android.youtube")) {
                appRow.addView(quranTile(tileWidth), 0, quranTileParams());
                appRow.addView(haramTile(tileWidth), 0, quranTileParams());
                appRow.addView(galleryTile(tileWidth), 0, quranTileParams());
                quranTileAdded = true;
            }
            if (displayed == 0) {
                tabEntryFocus[TAB_HOME] = tile;
                if (musicToggle != null) musicToggle.setNextFocusDownId(tile.getId());
            }
            displayed++;
            if (initialAppFocus && activeTab == TAB_HOME && settingsScreen == null
                    && packageName.equals(focusedPackage)) tile.requestFocus();
        }
        if (!quranTileAdded) {
            appRow.addView(quranTile(tileWidth), appRow.getChildCount(), quranTileParams());
            appRow.addView(haramTile(tileWidth), 0, quranTileParams());
            appRow.addView(galleryTile(tileWidth), 0, quranTileParams());
        }
        if (displayed > 0) {
            tabButtons[TAB_HOME].setNextFocusUpId(appRow.getChildAt(appRow.getChildCount() - 1).getId());
        }
        if (!initialAppFocus && activeTab == TAB_HOME && displayed > 0) {
            initialAppFocus = true;
            appRow.post(() -> {
                if (activeTab != TAB_HOME || settingsScreen != null) return;
                View destination = null;
                for (int i = 0; i < appRow.getChildCount(); i++) {
                    View child = appRow.getChildAt(i);
                    if (focusedPackage != null && focusedPackage.equals(child.getTag())) { destination = child; break; }
                    if (destination == null && child.isFocusable()) destination = child;
                }
                if (destination != null) destination.requestFocus();
            });
        }
        showModernApps(all, selected);
        if (selected.isEmpty()) {
            TextView empty = label("لا توجد تطبيقات مختارة", 15, MUTED, false);
            empty.setGravity(Gravity.CENTER_VERTICAL);
            appRow.addView(empty, new LinearLayout.LayoutParams(dp(240), dp(72)));
        }
    }

    private List<YouTubeCard> savedYouTubeCards() {
        ArrayList<YouTubeCard> result = new ArrayList<>();
        String raw = preferences.getString("youtubeCards", "[]");
        try {
            JSONArray values = new JSONArray(raw);
            for (int i = 0; i < values.length(); i++) {
                JSONObject value = values.optJSONObject(i);
                if (value == null) continue;
                YouTubeCard parsed = YouTubeCard.parse(value.optString("url", ""));
                if (parsed == null) continue;
                parsed.title = value.optString("title", parsed.title);
                parsed.autoTitle = value.optString("autoTitle", parsed.title);
                parsed.customTitle = value.optBoolean("customTitle", false);
                parsed.customThumbnail = value.optBoolean("customThumbnail", false);
                String savedThumbnail = value.optString("thumbnailUrl", "");
                if (!savedThumbnail.isEmpty()) parsed.thumbnailUrl = savedThumbnail;
                parsed.metadataCheckedAt = value.optLong("metadataCheckedAt", 0L);
                result.add(parsed);
            }
        } catch (Exception ignored) { }
        return result;
    }

    private void saveYouTubeCards(List<YouTubeCard> cards) {
        JSONArray values = new JSONArray();
        for (YouTubeCard card : cards) {
            JSONObject value = new JSONObject();
            try {
                value.put("url", card.url);
                value.put("title", card.title);
                value.put("autoTitle", card.autoTitle);
                value.put("customTitle", card.customTitle);
                value.put("customThumbnail", card.customThumbnail);
                value.put("thumbnailUrl", card.thumbnailUrl == null ? "" : card.thumbnailUrl);
                value.put("metadataCheckedAt", card.metadataCheckedAt);
                values.put(value);
            } catch (Exception ignored) { }
        }
        preferences.edit().putString("youtubeCards", values.toString()).apply();
    }

    private void renderYouTubeCards() {
        if (youtubeRow == null || youtubeSection == null) return;
        youtubeRow.removeAllViews();
        List<YouTubeCard> cards = savedYouTubeCards();
        youtubeSection.setVisibility(View.VISIBLE);
        youtubeSection.getChildAt(1).setVisibility(cards.isEmpty() ? View.GONE : View.VISIBLE);
        if (youtubeEmpty != null) youtubeEmpty.setVisibility(cards.isEmpty() ? View.VISIBLE : View.GONE);
        boolean retryMissingArtwork = !preferences.getBoolean("youtubeArtworkFeedFallbackTried", false);
        if (retryMissingArtwork) preferences.edit().putBoolean("youtubeArtworkFeedFallbackTried", true).apply();
        for (YouTubeCard card : cards) {
            boolean hasThumbnail = card.thumbnailUrl != null && !card.thumbnailUrl.isEmpty();
            LinearLayout tile = new LinearLayout(this);
            tile.setOrientation(LinearLayout.VERTICAL);
            tile.setGravity(Gravity.CENTER_HORIZONTAL);
            tile.setFocusable(true);
            tile.setFocusableInTouchMode(false);
            tile.setClickable(true);
            tile.setPadding(dp(8), dp(8), dp(8), dp(7));
            StateListDrawable states = new Motion.States();
            boolean light = LiquidTheme.isLight(this);
            states.addState(new int[]{android.R.attr.state_focused},
                    roundedShape(LiquidTheme.raisedSurface(this), LiquidTheme.accent(this), 3, 11));
            states.addState(new int[]{android.R.attr.state_pressed},
                    roundedShape(LiquidTheme.raisedSurface(this), LiquidTheme.accent(this), 3, 11));
            states.addState(new int[]{},
                    roundedShape(LiquidTheme.surface(this), LiquidTheme.border(this), 1, 11));
            tile.setBackground(states);
            tile.setElevation(dp(2));
            tile.setOnFocusChangeListener(LiquidTheme::animateFocus);
            Motion.interaction(tile, false);

            FrameLayout poster = new FrameLayout(this);
            GradientDrawable posterBackground = new GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    light
                            ? new int[]{0xFFE0EBF0, 0xFFC5D9E2, 0xFFB2CAD6}
                            : new int[]{0xFF46647A, 0xFF1B3044, 0xFF101B2A});
            posterBackground.setCornerRadius(dp(16));
            posterBackground.setStroke(dp(1), LiquidTheme.border(this));
            poster.setBackground(posterBackground);
            poster.setClipToOutline(true);
            ImageView thumbnail = new ImageView(this);
            thumbnail.setScaleType(ImageView.ScaleType.CENTER_CROP);
            thumbnail.setTag(card.url);
            poster.addView(thumbnail, new FrameLayout.LayoutParams(-1, -1));
            // The title becomes the artwork when YouTube has no usable thumbnail.
            TextView fallback = label(card.title, 20, light ? 0xFF1A353D : Color.WHITE, true);
            fallback.setTextColor(light ? 0xFF1A353D : Color.WHITE);
            fallback.setGravity(Gravity.BOTTOM | Gravity.LEFT);
            fallback.setMaxLines(3);
            fallback.setEllipsize(android.text.TextUtils.TruncateAt.END);
            fallback.setPadding(dp(15), dp(34), dp(15), dp(14));
            if (!light) fallback.setShadowLayer(dp(8), 0, dp(2), Color.BLACK);
            fallback.setContentDescription("Artwork: " + card.title);
            poster.addView(fallback, new FrameLayout.LayoutParams(-1, -1));
            TextView type = label(YouTubeCard.VIDEO.equals(card.type) ? "VIDEO" : "PLAYLIST",
                    9, light ? LiquidTheme.accent(this) : Color.WHITE, true);
            type.setTextColor(light ? LiquidTheme.accent(this) : Color.WHITE);
            type.setLetterSpacing(.10f);
            type.setPadding(dp(9), dp(5), dp(9), dp(5));
            type.setBackground(roundedShape(light ? 0xD0FFFFFF : 0xDB121212, LiquidTheme.accent(this), 1, 5));
            FrameLayout.LayoutParams typeParams = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.LEFT);
            typeParams.setMargins(dp(8), dp(8), 0, 0);
            poster.addView(type, typeParams);
            tile.addView(poster, new LinearLayout.LayoutParams(dp(224), dp(hasThumbnail ? 126 : 178)));
            if (hasThumbnail) {
                TextView title = label(card.title, 14, LiquidTheme.text(this), true);
                title.setMaxLines(2);
                title.setEllipsize(android.text.TextUtils.TruncateAt.END);
                title.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
                title.setPadding(dp(3), dp(6), dp(3), 0);
                tile.addView(title, new LinearLayout.LayoutParams(dp(224), dp(52)));
            }
            tile.setContentDescription((YouTubeCard.VIDEO.equals(card.type) ? "Play video: " : "Open playlist: ") + card.title);
            tile.setOnClickListener(view -> launchYouTubeCard(card));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(240), dp(198));
            params.setMargins(dp(4), dp(4), dp(14), dp(8));
            youtubeRow.addView(tile, params);
            linkTopControl(TAB_SAVED, tile);
            loadYouTubeThumbnail(card, thumbnail, fallback);
            long refreshAfter = card.thumbnailUrl == null || card.thumbnailUrl.isEmpty()
                    ? 60L * 60L * 1000L : 24L * 60L * 60L * 1000L;
            if (card.metadataCheckedAt == 0L
                    || System.currentTimeMillis() - card.metadataCheckedAt > refreshAfter
                    || (retryMissingArtwork && (card.thumbnailUrl == null || card.thumbnailUrl.isEmpty()))) {
                fetchYouTubeMetadata(card.url);
            }
        }
    }

    private void launchYouTubeCard(YouTubeCard card) {
        Uri target = Uri.parse(card.url);
        for (String packageName : new String[]{"com.google.android.youtube.tv", "com.google.android.youtube"}) {
            Intent deepLink = new Intent(Intent.ACTION_VIEW, target);
            deepLink.setPackage(packageName);
            if (deepLink.resolveActivity(getPackageManager()) != null) {
                try { startActivity(deepLink); return; } catch (Exception ignored) { }
            }
        }
        try {
            Intent browser = new Intent(Intent.ACTION_VIEW, target);
            if (browser.resolveActivity(getPackageManager()) != null) startActivity(browser);
            else Toast.makeText(this, "No app can open this YouTube link.", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Could not open this YouTube link.", Toast.LENGTH_LONG).show();
        }
    }

    private void fetchYouTubeMetadata(String url) {
        synchronized (youtubeMetadataInFlight) {
            if (!youtubeMetadataInFlight.add(url)) return;
        }
        lifecycleWork.execute(youtubeWorker, () -> {
            String title = null, thumbnail = null;
            HttpURLConnection connection = null;
            try {
                String endpoint = "https://www.youtube.com/oembed?format=json&url="
                        + URLEncoder.encode(url, "UTF-8");
                connection = (HttpURLConnection) new java.net.URL(endpoint).openConnection();
                connection.setConnectTimeout(6000);
                connection.setReadTimeout(8000);
                connection.setInstanceFollowRedirects(true);
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) AppleWebKit/537.36 Chrome/128.0.0.0 Mobile Safari/537.36");
                if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    try (java.io.InputStream input = connection.getInputStream();
                         java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream()) {
                        byte[] buffer = new byte[4096];
                        int total = 0, count;
                        while ((count = input.read(buffer)) != -1) {
                            total += count;
                            if (total > 1024 * 1024) break;
                            output.write(buffer, 0, count);
                        }
                        JSONObject response = new JSONObject(new String(output.toByteArray(), java.nio.charset.StandardCharsets.UTF_8));
                        String candidateTitle = response.optString("title", "").trim();
                        String candidateThumbnail = response.optString("thumbnail_url", "").trim();
                        if (!candidateTitle.isEmpty()) title = candidateTitle;
                        if (isSafeYouTubeThumbnail(candidateThumbnail)) thumbnail = candidateThumbnail;
                    }
                }
            } catch (Exception ignored) {
            } finally {
                if (connection != null) connection.disconnect();
            }
            YouTubeCard parsed = YouTubeCard.parse(url);
            if (thumbnail == null && parsed != null && YouTubeCard.PLAYLIST.equals(parsed.type)) {
                thumbnail = playlistFeedThumbnail(parsed.playlistId);
            }
            final String resultTitle = title, resultThumbnail = thumbnail;
            lifecycleWork.post(() -> {
                synchronized (youtubeMetadataInFlight) { youtubeMetadataInFlight.remove(url); }
                List<YouTubeCard> cards = savedYouTubeCards();
                boolean changed = false;
                for (YouTubeCard card : cards) {
                    if (!card.url.equals(url)) continue;
                    if (resultTitle != null) {
                        card.autoTitle = resultTitle;
                        if (!card.customTitle) card.title = resultTitle;
                    }
                    if (resultThumbnail != null && !card.customThumbnail) card.thumbnailUrl = resultThumbnail;
                    card.metadataCheckedAt = System.currentTimeMillis();
                    changed = true;
                    break;
                }
                if (changed) {
                    saveYouTubeCards(cards);
                    renderYouTubeCards();
                }
            });
        });
    }

    private boolean isSafeYouTubeThumbnail(String value) {
        try {
            Uri uri = Uri.parse(value);
            String host = uri.getHost();
            return "https".equalsIgnoreCase(uri.getScheme()) && host != null
                    && ("i.ytimg.com".equalsIgnoreCase(host) || "img.youtube.com".equalsIgnoreCase(host)
                    || host.endsWith(".ytimg.com"));
        } catch (Exception ignored) { return false; }
    }

    private String playlistFeedThumbnail(String playlistId) {
        if (playlistId == null || !playlistId.matches("[A-Za-z0-9_-]{8,}")) return null;
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new java.net.URL(
                    "https://www.youtube.com/feeds/videos.xml?playlist_id=" + playlistId).openConnection();
            connection.setConnectTimeout(6000);
            connection.setReadTimeout(8000);
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) return null;
            try (java.io.InputStream input = connection.getInputStream()) {
                org.xmlpull.v1.XmlPullParser parser = android.util.Xml.newPullParser();
                parser.setInput(input, "UTF-8");
                while (parser.next() != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                    if (parser.getEventType() != org.xmlpull.v1.XmlPullParser.START_TAG) continue;
                    if ("thumbnail".equals(parser.getName())) {
                        String imageUrl = parser.getAttributeValue(null, "url");
                        if (isSafeYouTubeThumbnail(imageUrl)) return imageUrl;
                    } else if ("videoId".equals(parser.getName())) {
                        String videoId = parser.nextText();
                        if (videoId.matches("[A-Za-z0-9_-]{11}"))
                            return "https://i.ytimg.com/vi/" + videoId + "/hqdefault.jpg";
                    }
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (connection != null) connection.disconnect();
        }
        return null;
    }

    private void loadYouTubeThumbnail(YouTubeCard card, ImageView image, TextView fallback) {
        if (card.thumbnailUrl == null || card.thumbnailUrl.isEmpty()) return;
        android.graphics.Bitmap cached = youtubeThumbnailCache.get(card.thumbnailUrl);
        if (cached != null && !cached.isRecycled()) {
            image.setImageBitmap(cached);
            fallback.setVisibility(View.GONE);
            return;
        }
        lifecycleWork.execute(youtubeWorker, () -> {
            String cacheName = youtubeThumbnailCacheName(card.thumbnailUrl);
            java.io.File imageFile = new java.io.File(getFilesDir(), cacheName);
            android.graphics.Bitmap bitmap = imageFile.isFile() ? decodeYouTubeImage(imageFile) : null;
            if (bitmap == null) {
                HttpURLConnection connection = null;
                try {
                    connection = (HttpURLConnection) new java.net.URL(card.thumbnailUrl).openConnection();
                    connection.setConnectTimeout(6000);
                    connection.setReadTimeout(8000);
                    connection.setInstanceFollowRedirects(true);
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) AppleWebKit/537.36 Chrome/128.0.0.0 Mobile Safari/537.36");
                    try (java.io.InputStream input = connection.getInputStream();
                         java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream()) {
                        byte[] buffer = new byte[8192];
                        int count, total = 0;
                        while ((count = input.read(buffer)) != -1) {
                            total += count;
                            if (total > 8 * 1024 * 1024) break;
                            output.write(buffer, 0, count);
                        }
                        byte[] bytes = output.toByteArray();
                        bitmap = decodeYouTubeImage(bytes);
                        if (bitmap != null) {
                            java.io.File temporary = new java.io.File(getFilesDir(), cacheName + ".tmp");
                            try (java.io.FileOutputStream file = new java.io.FileOutputStream(temporary)) {
                                file.write(bytes);
                                file.flush();
                            }
                            if (imageFile.exists()) imageFile.delete();
                            temporary.renameTo(imageFile);
                        }
                    }
                } catch (Exception ignored) {
                } finally {
                    if (connection != null) connection.disconnect();
                }
            }
            if (bitmap == null) return;
            if (!lifecycleWork.active()) return;
            youtubeThumbnailCache.put(card.thumbnailUrl, bitmap);
            android.graphics.Bitmap result = bitmap;
            lifecycleWork.post(() -> {
                if (card.url.equals(image.getTag())) {
                    image.setImageBitmap(result);
                    fallback.setVisibility(View.GONE);
                }
            });
        });
    }

    private android.graphics.Bitmap decodeYouTubeImage(java.io.File file) {
        android.graphics.BitmapFactory.Options bounds = new android.graphics.BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
        android.graphics.BitmapFactory.Options options = youtubeImageOptions(bounds.outWidth, bounds.outHeight);
        return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
    }

    private android.graphics.Bitmap decodeYouTubeImage(byte[] bytes) {
        android.graphics.BitmapFactory.Options bounds = new android.graphics.BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length,
                youtubeImageOptions(bounds.outWidth, bounds.outHeight));
    }

    private android.graphics.BitmapFactory.Options youtubeImageOptions(int width, int height) {
        android.graphics.BitmapFactory.Options options = new android.graphics.BitmapFactory.Options();
        options.inPreferredConfig = android.graphics.Bitmap.Config.RGB_565;
        options.inSampleSize = Math.max(1, Math.min(width / Math.max(1, dp(452)), height / Math.max(1, dp(232))));
        return options;
    }

    private String youtubeThumbnailCacheName(String url) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(url.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder key = new StringBuilder("youtube-");
            for (byte value : digest) key.append(String.format(Locale.ROOT, "%02x", value));
            return key.toString();
        } catch (Exception ignored) { return "youtube-" + Integer.toHexString(url.hashCode()); }
    }

    private void addTopIcon(LinearLayout container, String title, int iconResource, Runnable action) {
        ImageView icon = new ImageView(this);
        icon.setImageResource(iconResource);
        icon.setColorFilter(LiquidTheme.isLight(this) ? 0xFF24403D : 0xD4FFFFFF);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        icon.setPadding(dp(8), dp(8), dp(8), dp(8));
        icon.setContentDescription(title);
        icon.setTooltipText(title);
        boolean light = LiquidTheme.isLight(this);
        int focusFill = light ? 0x22087D91 : 0x24FFFFFF;
        int focusStroke = light ? LiquidTheme.accent(this) : 0x4DFFFFFF;
        StateListDrawable states = new Motion.States();
        states.addState(new int[]{android.R.attr.state_focused}, roundedShape(focusFill, focusStroke, 1, 22));
        states.addState(new int[]{android.R.attr.state_pressed}, roundedShape(focusFill, focusStroke, 1, 22));
        states.addState(new int[]{}, new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        icon.setBackground(states);
        icon.setFocusable(true);
        icon.setClickable(true);
        icon.setOnFocusChangeListener(LiquidTheme::animateContainedFocus);
        icon.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                dp(compactNavigation ? 30 : 32), dp(compactNavigation ? 30 : 32));
        lp.setMargins(dp(2), 0, dp(2), 0);
        container.addView(icon, lp);
    }

    private void openNetworkSettings() {
        Intent intent = new Intent(android.provider.Settings.ACTION_WIFI_SETTINGS);
        try { startActivity(intent); }
        catch (Exception ignored) {
            try { startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS)); }
            catch (Exception ignoredAgain) { }
        }
    }

    private void openDateSettings() {
        try { startActivity(new Intent(android.provider.Settings.ACTION_DATE_SETTINGS)); }
        catch (Exception ignored) { openAndroidSettings(); }
    }

    private void openAndroidSettings() {
        try { startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS)); }
        catch (Exception ignored) { }
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

    private Drawable tileShape(int color, int strokeColor, int strokeWidth) {
        return roundedShape(color, strokeColor, strokeWidth, 18);
    }

    private Drawable roundedShape(int color, int strokeColor, int strokeWidth, int radiusDp) {
        return new GlassDrawable(this, glassBackdrop, color, strokeColor, strokeWidth, Math.max(16, radiusDp));
    }

    private Drawable gradientShape(int startColor, int endColor, int strokeColor, int strokeWidth, int radiusDp) {
        return roundedShape(startColor, strokeColor, strokeWidth, radiusDp);
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

    private Drawable headerSectionBackground() {
        boolean light = LiquidTheme.isLight(this);
        StateListDrawable states = new Motion.States();
        Drawable focused = light
                ? calendarGlassShape(0x25087D91, LiquidTheme.accent(this), 14)
                : roundedShape(0x443D5B61, 0xFF7BE7F4, 1, 14);
        states.addState(new int[]{android.R.attr.state_focused}, focused);
        states.addState(new int[]{android.R.attr.state_pressed}, focused);
        states.addState(new int[]{}, new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        return states;
    }

    private Drawable topWidgetBackground() {
        StateListDrawable states = new Motion.States();
        states.addState(new int[]{android.R.attr.state_focused},
                roundedShape(0xE0173032, 0xFF7BE7F4, 2, 18));
        states.addState(new int[]{android.R.attr.state_pressed},
                roundedShape(0xE0173032, 0xFF7BE7F4, 2, 18));
        states.addState(new int[]{},
                roundedShape(0xD0151719, 0x66FFFFFF, 1, 18));
        return states;
    }

    private void launchApp(String packageName) {
        Intent launch = getPackageManager().getLeanbackLaunchIntentForPackage(packageName);
        if (launch == null) launch = getPackageManager().getLaunchIntentForPackage(packageName);
        if (launch != null) try { startActivity(launch); } catch (Exception ignored) { }
    }

    private void renderGames() {
        if (gameGrid == null) return;
        gameGrid.removeAllViews();
        List<GameRecord> games = new GameRepository(this).games();
        gamesEmpty.setVisibility(games.isEmpty() ? View.VISIBLE : View.GONE);
        int availableWidth = getResources().getDisplayMetrics().widthPixels
                - gamesContent.getPaddingLeft() - gamesContent.getPaddingRight();
        int columns = Math.max(1, Math.min(5, availableWidth / dp(162)));
        gameGrid.setColumnCount(columns);
        int cardWidth = Math.max(1, availableWidth / columns - dp(12));
        for (GameRecord game : games) {
            LinearLayout card = GameUi.card(this, game, v -> startActivity(new Intent(this, GameDetailActivity.class).putExtra("gameId", game.id)));
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = cardWidth;
            params.height = dp(224);
            params.setMargins(0, 0, dp(12), dp(12));
            gameGrid.addView(card, params);
            if (gameGrid.getChildCount() <= columns) linkTopControl(TAB_GAMES, card);
        }
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

    private LinearLayout.LayoutParams quranTileParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(compactNavigation ? 148 : 167), dp(64));
        lp.setMargins(dp(10), 0, 0, 0);
        return lp;
    }

    private View quranTile(int tileWidth) {
        return builtInTile("القرآن الكريم", new QuranView.MushafIcon(), 0x2F3E36, TAB_QURAN);
    }

    private View galleryTile(int tileWidth) {
        return builtInTile("الخلفيات", new FrameIcon(), 0x2A2620, TAB_GALLERY);
    }

    /** A small gilded picture frame, matching the other built-in icons. */
    static final class FrameIcon extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        @Override public void draw(Canvas canvas) {
            Rect b = getBounds();
            float size = Math.min(b.width(), b.height()), cx = b.exactCenterX(), cy = b.exactCenterY();
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0x26E3B469);
            canvas.drawCircle(cx, cy, size / 2f, paint);
            RectF outer = new RectF(cx - size * 0.25f, cy - size * 0.2f, cx + size * 0.25f, cy + size * 0.2f);
            paint.setColor(0xFFE3B469);
            canvas.drawRect(outer, paint);
            RectF inner = new RectF(outer);
            inner.inset(size * 0.06f, size * 0.06f);
            paint.setColor(0xFF2F3E36);
            canvas.drawRect(inner, paint);
            // A hill and a sun inside the frame.
            paint.setColor(0xFFE3B469);
            canvas.drawCircle(inner.right - inner.width() * 0.28f, inner.top + inner.height() * 0.32f, size * 0.035f, paint);
            android.graphics.Path hill = new android.graphics.Path();
            hill.moveTo(inner.left, inner.bottom);
            hill.lineTo(inner.left + inner.width() * 0.4f, inner.top + inner.height() * 0.45f);
            hill.lineTo(inner.right, inner.bottom);
            hill.close();
            canvas.drawPath(hill, paint);
        }
        @Override public void setAlpha(int alpha) { }
        @Override public void setColorFilter(android.graphics.ColorFilter filter) { }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    private View haramTile(int tileWidth) {
        return builtInTile("الحرم مباشر", new KaabaIcon(), 0x2B2A24, TAB_HARAM);
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

    /** A small gold Kaaba: a cube with its band, on the same soft round ground as the mushaf icon. */
    static final class KaabaIcon extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        @Override public void draw(Canvas canvas) {
            Rect b = getBounds();
            float size = Math.min(b.width(), b.height()), cx = b.exactCenterX(), cy = b.exactCenterY();
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0x26E3B469);
            canvas.drawCircle(cx, cy, size / 2f, paint);
            float w = size * 0.42f, h = size * 0.44f;
            RectF cube = new RectF(cx - w / 2f, cy - h / 2f + size * 0.03f, cx + w / 2f, cy + h / 2f + size * 0.03f);
            paint.setColor(0xFF15171A);
            canvas.drawRoundRect(cube, size * 0.03f, size * 0.03f, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(size * 0.04f);
            paint.setColor(0xFFE3B469);
            canvas.drawRoundRect(cube, size * 0.03f, size * 0.03f, paint);
            paint.setStyle(Paint.Style.FILL);
            float band = cube.top + h * 0.26f;
            canvas.drawRect(cube.left, band, cube.right, band + size * 0.05f, paint);
        }
        @Override public void setAlpha(int alpha) { }
        @Override public void setColorFilter(android.graphics.ColorFilter filter) { }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    /** Focus outline for the home tiles: the same gold as the icons. */
    private static final int GOLD_FOCUS = 0xFFE3B469;

    /**
     * Round, calm icons drawn on the device for YouTube and Stremio: a softened version of each app's
     * colour with a cream symbol, in the same circle as the Quran and Haram tiles.
     */
    static final class AppGlyph extends Drawable {
        static final int YOUTUBE = 0, STREMIO = 1;
        private final int kind;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.Path path = new android.graphics.Path();
        private final RectF rect = new RectF();
        AppGlyph(int kind) { this.kind = kind; }
        @Override public void draw(Canvas canvas) {
            Rect b = getBounds();
            float size = Math.min(b.width(), b.height()), cx = b.exactCenterX(), cy = b.exactCenterY(), u = size / 100f;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(kind == YOUTUBE ? 0xFF8A2F2F : 0xFF56458A);
            canvas.drawCircle(cx, cy, size / 2f, paint);
            int cream = 0xFFF1ECE2;
            paint.setColor(cream);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(5 * u);
            path.reset();
            if (kind == YOUTUBE) {
                rect.set(cx - 26 * u, cy - 19 * u, cx + 26 * u, cy + 19 * u);
                canvas.drawRoundRect(rect, 11 * u, 11 * u, paint);
                path.moveTo(cx - 6 * u, cy - 10 * u); path.lineTo(cx + 10 * u, cy); path.lineTo(cx - 6 * u, cy + 10 * u);
            } else {
                canvas.drawCircle(cx, cy, 25 * u, paint);
                paint.setStrokeWidth(2 * u);
                paint.setPathEffect(new android.graphics.DashPathEffect(new float[]{3 * u, 7 * u}, 0));
                paint.setAlpha(180);
                canvas.drawCircle(cx, cy, 33 * u, paint);
                paint.setPathEffect(null);
                paint.setAlpha(255);
                path.moveTo(cx - 5 * u, cy - 11 * u); path.lineTo(cx + 12 * u, cy); path.lineTo(cx - 5 * u, cy + 11 * u);
            }
            path.close();
            paint.setStyle(Paint.Style.FILL);
            canvas.drawPath(path, paint);
        }
        @Override public void setAlpha(int alpha) { }
        @Override public void setColorFilter(android.graphics.ColorFilter filter) { }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    /** Any other app's own icon, inside a soft circle so it matches the rest of the row. */
    static final class RoundAppIcon extends Drawable {
        private final Drawable icon;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.Path clip = new android.graphics.Path();
        RoundAppIcon(Drawable icon) { this.icon = icon; paint.setColor(0x26E3B469); }
        @Override public void draw(Canvas canvas) {
            Rect b = getBounds();
            float size = Math.min(b.width(), b.height()), cx = b.exactCenterX(), cy = b.exactCenterY();
            canvas.drawCircle(cx, cy, size / 2f, paint);
            if (icon == null) return;
            int inner = Math.round(size * 0.62f);
            int left = Math.round(cx - inner / 2f), top = Math.round(cy - inner / 2f);
            canvas.save();
            clip.reset();
            clip.addCircle(cx, cy, inner / 2f, android.graphics.Path.Direction.CW);
            canvas.clipPath(clip);
            icon.setBounds(left, top, left + inner, top + inner);
            icon.draw(canvas);
            canvas.restore();
        }
        @Override public void setAlpha(int alpha) { }
        @Override public void setColorFilter(android.graphics.ColorFilter filter) { }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    /** A home tile that opens a built-in screen, styled like the app tiles. */
    private View builtInTile(String title, Drawable iconDrawable, int rgb, int tab) {
        LinearLayout tile = new LinearLayout(this);
        tile.setId(View.generateViewId());
        tile.setGravity(Gravity.CENTER);
        tile.setFocusable(true);
        tile.setClickable(true);
        tile.setFocusableInTouchMode(false);
        StateListDrawable states = new Motion.States();
        states.addState(new int[]{android.R.attr.state_focused}, roundedShape(0xF2000000 | rgb, GOLD_FOCUS, 2, 16));
        states.addState(new int[]{android.R.attr.state_pressed}, roundedShape(0xF2000000 | rgb, GOLD_FOCUS, 2, 16));
        states.addState(new int[]{}, roundedShape(0xE0000000 | rgb, 0x66FFFFFF, 1, 16));
        tile.setBackground(states);
        tile.setElevation(dp(2));
        tile.setOnFocusChangeListener(LiquidTheme::animateFocus);
        Motion.interaction(tile, false);
        tile.setPadding(dp(8), dp(4), dp(10), dp(4));
        tile.setContentDescription(title);
        // Right to left: the icon at the right edge, the name beside it.
        TextView name = label(title, compactNavigation ? 13 : 14, 0xFFF1ECE2, true);
        name.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        name.setSingleLine(true);
        tile.addView(name, new LinearLayout.LayoutParams(0, -1, 1));
        View icon = new View(this);
        icon.setBackground(iconDrawable);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(38), dp(38));
        iconParams.leftMargin = dp(10);
        tile.addView(icon, iconParams);
        tile.setOnClickListener(v -> { if (tab == TAB_HARAM) openHaramLive(); else selectTab(tab, false); });
        return tile;
    }

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

    private void renderChannels() {
        if (channelRows == null) return;
        IptvRepository.refreshProgrammeDisplay(channels, System.currentTimeMillis());
        List<IptvRepository.Channel> favorites = FavoriteChannels.filterFavorites(channels, hiddenChannelKeys, favoriteChannelKeys);
        LinkedHashMap<String, List<IptvRepository.Channel>> grouped = new LinkedHashMap<>();
        if (!favorites.isEmpty()) {
            grouped.put(FavoriteChannels.CATEGORY_FAVORITES, favorites);
        }
        for (IptvRepository.Channel channel : channels) {
            if (HiddenChannels.isHidden(hiddenChannelKeys, channel)) continue;
            String category = channel.group.trim().isEmpty() ? "Uncategorized" : channel.group.trim();
            grouped.computeIfAbsent(category, ignored -> new ArrayList<>()).add(channel);
        }
        if (grouped.isEmpty()) {
            channelsEmpty.setText(channels.isEmpty()
                    ? "Add a playlist in Settings to browse live TV."
                    : "All channels are hidden. Restore channels in Settings.");
            if (!renderedCategories.isEmpty() || channelRows.getChildCount() != 1 || channelRows.getChildAt(0) != channelsEmpty) {
                channelRows.removeAllViews();
                channelRowMap.clear();
                channelTabMap.clear();
                channelRows.addView(channelsEmpty);
                renderedCategories.clear();
                selectedChannelCategory = null;
            }
            renderGuide();
            return;
        }

        List<String> nextCategories = new ArrayList<>(grouped.keySet());
        if (!nextCategories.equals(renderedCategories)) {
            channelRows.removeAllViews();
            channelRowMap.clear();
            channelTabMap.clear();
            channelCategoryTabs.removeAllViews();
            renderedCategories = nextCategories;
            if (!renderedCategories.contains(selectedChannelCategory)) selectedChannelCategory = renderedCategories.get(0);
            channelRows.addView(channelCategoryScroll, new LinearLayout.LayoutParams(-1, dp(50)));
            ChannelRow row = createChannelRow();
            channelRowMap.put(selectedChannelCategory, row);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.setMargins(0, dp(4), 0, dp(6));
            channelRows.addView(row.container, params);
            for (String category : renderedCategories) {
                Button tab = createChannelCategoryTab(category);
                channelTabMap.put(category, tab);
                LinearLayout.LayoutParams tabParams = new LinearLayout.LayoutParams(-2, dp(44));
                tabParams.setMargins(dp(2), dp(2), dp(8), dp(2));
                channelCategoryTabs.addView(tab, tabParams);
                linkTopControl(TAB_TV, tab);
                if (channelCategoryTabs.getChildCount() == 1) linkTabEntry(TAB_TV, tab);
            }
        }
        if (!renderedCategories.contains(selectedChannelCategory)) selectedChannelCategory = renderedCategories.get(0);
        for (Map.Entry<String, List<IptvRepository.Channel>> category : grouped.entrySet()) {
            Button tab = channelTabMap.get(category.getKey());
            String prefix = FavoriteChannels.CATEGORY_FAVORITES.equals(category.getKey()) ? "★ " : "";
            if (tab != null) tab.setText(prefix + category.getKey() + "  ·  " + category.getValue().size());
            ChannelRow row = channelRowMap.get(selectedChannelCategory);
            if (row != null && category.getKey().equals(selectedChannelCategory)) {
                queueChannelRowUpdate(row, selectedChannelCategory, category.getValue());
            }
        }
        updateChannelCategoryTabStyles();
        renderGuide();
    }

    private void refreshCachedProgrammeDisplay() {
        IptvRepository.refreshProgrammeDisplay(channels, System.currentTimeMillis());
        for (ChannelRow row : channelRowMap.values()) {
            row.list.post(() -> {
                if (channelRowMap.containsValue(row)) row.adapter.refreshVisibleProgrammeLabels(row.list);
            });
        }
    }

    private void renderGuide() {
        if (guideEmpty == null) return;
        guideEmpty.setText(channels.isEmpty() ? "Add a playlist and programme guide in Settings."
                : "Explore the schedule, see what's on now, and set reminders.");
    }

    private Button createChannelCategoryTab(String category) {
        String label = FavoriteChannels.CATEGORY_FAVORITES.equals(category) ? "★ " + category : category;
        Button tab = actionButton(label);
        tab.setTextSize(14);
        tab.setPadding(dp(18), 0, dp(18), 0);
        tab.setMinWidth(dp(72));
        tab.setOnFocusChangeListener((view, focused) -> {
            LiquidTheme.animateFocus(view, focused);
            if (focused) selectChannelCategory(category, view);
        });
        tab.setOnClickListener(view -> selectChannelCategory(category, view));
        return tab;
    }

    private void selectChannelCategory(String category, View view) {
        if (category.equals(selectedChannelCategory)) return;
        selectedChannelCategory = category;
        updateChannelCategoryTabStyles();
        ChannelRow row = channelRowMap.isEmpty() ? null : channelRowMap.values().iterator().next();
        if (row != null) {
            channelRowMap.clear();
            channelRowMap.put(selectedChannelCategory, row);
            queueChannelRowUpdate(row, category, channelsForCategory(category));
        }
        renderGuide();
        channelCategoryScroll.smoothScrollTo(Math.max(0, view.getLeft() - dp(24)), 0);
    }

    private void queueChannelRowUpdate(ChannelRow row, String category, List<IptvRepository.Channel> items) {
        int generation = ++row.pendingUpdateGeneration;
        List<IptvRepository.Channel> snapshot = new ArrayList<>(items);
        Set<String> favorites = new HashSet<>(favoriteChannelKeys);
        row.list.post(() -> {
            if (row.pendingUpdateGeneration != generation
                    || !category.equals(selectedChannelCategory)
                    || channelRowMap.get(category) != row) return;
            row.adapter.setChannels(snapshot, favorites);
        });
    }

    private List<IptvRepository.Channel> channelsForCategory(String category) {
        if (FavoriteChannels.CATEGORY_FAVORITES.equals(category)) {
            return FavoriteChannels.filterFavorites(channels, hiddenChannelKeys, favoriteChannelKeys);
        }
        ArrayList<IptvRepository.Channel> selected = new ArrayList<>();
        for (IptvRepository.Channel channel : channels) {
            if (HiddenChannels.isHidden(hiddenChannelKeys, channel)) continue;
            String channelCategory = channel.group.trim().isEmpty() ? "Uncategorized" : channel.group.trim();
            if (category.equals(channelCategory)) selected.add(channel);
        }
        return selected;
    }

    private void updateChannelCategoryTabStyles() {
        for (Map.Entry<String, Button> entry : channelTabMap.entrySet()) {
            boolean selected = entry.getKey().equals(selectedChannelCategory);
            Button tab = entry.getValue();
            android.graphics.drawable.StateListDrawable states = new Motion.States();
            states.addState(new int[]{android.R.attr.state_focused}, roundedShape(
                    LiquidTheme.raisedSurface(this),
                    LiquidTheme.accent(this), 2, 8));
            states.addState(new int[]{}, selected
                    ? roundedShape(LiquidTheme.raisedSurface(this), LiquidTheme.accent(this), 1, 24)
                    : roundedShape(LiquidTheme.surface(this), LiquidTheme.border(this), 1, 8));
            tab.setBackground(states);
            tab.setTextColor(LiquidTheme.text(this));
            tab.setTypeface(LiquidTheme.font(this));
        }
    }

    private ChannelRow createChannelRow() {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setClipChildren(false);
        container.setClipToPadding(false);
        RecyclerView list = new RecyclerView(this);
        list.setClipChildren(false);
        list.setClipToPadding(false);
        list.setPadding(dp(4), dp(4), dp(4), dp(4));
        list.setLayoutManager(new LinearLayoutManager(this, RecyclerView.HORIZONTAL, false));
        list.setHasFixedSize(true);
        list.setItemViewCacheSize(4);
        list.setOverScrollMode(View.OVER_SCROLL_NEVER);
        ChannelAdapter adapter = new ChannelAdapter(this, logoLoader, glassBackdrop, this::playChannel, this::toggleFavoriteChannel);
        list.setAdapter(adapter);
        container.addView(list, new LinearLayout.LayoutParams(-1, dp(250)));
        return new ChannelRow(container, list, adapter);
    }

    private void loadBackgroundFolder(Uri treeUri, boolean userSelected) {
        int generation = ++backgroundFolderGeneration;
        List<BackgroundPhoto> cached = cachedBackgroundFolder(treeUri.toString());
        if (!cached.isEmpty() && !sameBackgroundPhotos(cached)) setBackgroundPhotos(cached);
        else if (userSelected && cached.isEmpty()) setBackgroundPhotos(Collections.emptyList());
        lifecycleWork.execute(ioWorker, () -> {
            FolderScanResult scan = enumeratePhotos(treeUri);
            lifecycleWork.post(() -> {
                if (generation != backgroundFolderGeneration || isFinishing()) return;
                if (!scan.success) {
                    if (userSelected) Toast.makeText(this,
                            "Could not read that folder. Keeping the saved slideshow.", Toast.LENGTH_LONG).show();
                    return;
                }
                cacheBackgroundFolder(treeUri.toString(), scan.photos);
                if (!sameBackgroundPhotos(scan.photos)) setBackgroundPhotos(scan.photos);
                if (settingsScreen != null) settingsScreen.refreshAppearance();
                if (userSelected && scan.photos.isEmpty()) {
                    Toast.makeText(this, "No images were found in that folder.", Toast.LENGTH_LONG).show();
                } else if (userSelected) {
                    Toast.makeText(this, "Background slideshow updated · " + scan.photos.size() + " photos", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void loadBackgroundAlbum(String shareUrl, boolean userSelected) {
        int generation = ++backgroundFolderGeneration;
        List<BackgroundPhoto> cached = cachedBackgroundAlbum(shareUrl);
        if (!cached.isEmpty() && !sameBackgroundPhotos(cached)) setBackgroundPhotos(cached);
        preferences.edit().putLong("googlePhotosAttemptedAt", System.currentTimeMillis()).apply();
        lifecycleWork.execute(ioWorker, () -> {
            List<BackgroundPhoto> photos = new ArrayList<>();
            String failure = null;
            try {
                List<String> urls = GooglePhotosAlbumRepository.fetchImageUrls(shareUrl);
                if (urls.isEmpty()) throw new IllegalStateException("No photos were found. Check that link sharing is on.");
                for (int i = 0; i < urls.size(); i++) photos.add(new BackgroundPhoto(Uri.parse(urls.get(i)), "Google Photos " + (i + 1)));
            } catch (Exception e) {
                failure = e.getMessage() == null ? "Could not load that Google Photos album." : e.getMessage();
            }
            List<BackgroundPhoto> result = photos;
            String error = failure;
            lifecycleWork.post(() -> {
                if (generation != backgroundFolderGeneration || isFinishing()) return;
                if (error != null || result.isEmpty()) {
                    if (userSelected) Toast.makeText(this, error == null ? "No public photos were found in that album." : error, Toast.LENGTH_LONG).show();
                    return;
                }
                cacheBackgroundAlbum(shareUrl, result);
                preferences.edit().putString("wallpaperAlbum", shareUrl).remove("wallpaperFolder")
                        .putLong("googlePhotosUpdatedAt", System.currentTimeMillis()).apply();
                if (!sameBackgroundPhotos(result)) setBackgroundPhotos(result);
                if (settingsScreen != null) settingsScreen.refreshAppearance();
                if (userSelected) Toast.makeText(this, "Google Photos slideshow updated · " + result.size() + " photos", Toast.LENGTH_SHORT).show();
                scheduleGooglePhotosRefresh();
            });
        });
    }

    private void maybeRefreshGooglePhotos(boolean appLaunch) {
        String album = preferences.getString("wallpaperAlbum", "");
        if (album.isEmpty() || !preferences.getString("wallpaperFolder", "").isEmpty()) return;
        long interval = scheduleValue(SchedulePreferences.GOOGLE_PHOTOS,
                SchedulePreferences.GOOGLE_PHOTOS_VALUES, SchedulePreferences.DEFAULT_GOOGLE_PHOTOS);
        if (interval == SchedulePreferences.MANUAL) return;
        if (interval == SchedulePreferences.EVERY_LAUNCH) {
            if (appLaunch) loadBackgroundAlbum(album, false);
            return;
        }
        long updated = preferences.getLong("googlePhotosUpdatedAt", 0L);
        long attempted = preferences.getLong("googlePhotosAttemptedAt", updated);
        if (System.currentTimeMillis() - Math.max(updated, attempted) >= interval) {
            loadBackgroundAlbum(album, false);
        }
    }

    private void scheduleGooglePhotosRefresh() {
        if (googlePhotosRefreshTick == null) return;
        handler.removeCallbacks(googlePhotosRefreshTick);
        if (!screenActive || preferences.getString("wallpaperAlbum", "").isEmpty()
                || !preferences.getString("wallpaperFolder", "").isEmpty()) return;
        long interval = scheduleValue(SchedulePreferences.GOOGLE_PHOTOS,
                SchedulePreferences.GOOGLE_PHOTOS_VALUES, SchedulePreferences.DEFAULT_GOOGLE_PHOTOS);
        if (interval <= 0) return;
        long updated = preferences.getLong("googlePhotosUpdatedAt", 0L);
        long attempted = preferences.getLong("googlePhotosAttemptedAt", updated);
        long elapsed = Math.max(0L, System.currentTimeMillis() - Math.max(updated, attempted));
        handler.postDelayed(googlePhotosRefreshTick, Math.max(1000L, interval - elapsed));
    }

    private List<BackgroundPhoto> cachedBackgroundAlbum(String shareUrl) {
        ArrayList<BackgroundPhoto> photos = new ArrayList<>();
        if (!shareUrl.equals(preferences.getString(GOOGLE_PHOTOS_CACHE_URL, ""))) return photos;
        try {
            JSONArray urls = new JSONArray(preferences.getString(GOOGLE_PHOTOS_CACHE_IMAGES, "[]"));
            for (int i = 0; i < urls.length(); i++) {
                String url = urls.optString(i, "");
                if (!url.isEmpty()) photos.add(new BackgroundPhoto(Uri.parse(url), "Google Photos " + (i + 1)));
            }
        } catch (Exception ignored) { }
        return photos;
    }

    private List<BackgroundPhoto> cachedBackgroundFolder(String folderUri) {
        ArrayList<BackgroundPhoto> photos = new ArrayList<>();
        if (!folderUri.equals(preferences.getString(PHOTO_FOLDER_CACHE_URI, ""))) return photos;
        try {
            JSONArray values = new JSONArray(preferences.getString(PHOTO_FOLDER_CACHE_IMAGES, "[]"));
            for (int i = 0; i < values.length(); i++) {
                JSONObject value = values.optJSONObject(i);
                if (value == null) continue;
                String uri = value.optString("uri", "");
                if (!uri.isEmpty()) photos.add(new BackgroundPhoto(Uri.parse(uri), value.optString("name", "Photo")));
            }
        } catch (Exception ignored) { }
        return photos;
    }

    private void cacheBackgroundFolder(String folderUri, List<BackgroundPhoto> photos) {
        JSONArray values = new JSONArray();
        for (BackgroundPhoto photo : photos) {
            JSONObject value = new JSONObject();
            try {
                value.put("uri", photo.uri.toString());
                value.put("name", photo.name);
                values.put(value);
            } catch (Exception ignored) { }
        }
        preferences.edit().putString(PHOTO_FOLDER_CACHE_URI, folderUri)
                .putString(PHOTO_FOLDER_CACHE_IMAGES, values.toString()).apply();
    }

    private void cacheBackgroundAlbum(String shareUrl, List<BackgroundPhoto> photos) {
        JSONArray urls = new JSONArray();
        for (BackgroundPhoto photo : photos) urls.put(photo.uri.toString());
        preferences.edit()
                .putString(GOOGLE_PHOTOS_CACHE_URL, shareUrl)
                .putString(GOOGLE_PHOTOS_CACHE_IMAGES, urls.toString())
                .apply();
    }

    private boolean sameBackgroundPhotos(List<BackgroundPhoto> photos) {
        if (backgroundPhotos.size() != photos.size()) return false;
        for (int i = 0; i < photos.size(); i++) {
            if (!backgroundPhotos.get(i).uri.equals(photos.get(i).uri)) return false;
        }
        return true;
    }

    private void showCachedBackgroundImmediately() {
        if (backgroundPhotos.isEmpty()) return;
        slideshowTick = this::showNextBackgroundPhoto;
        Uri uri = backgroundPhotos.get(0).uri;
        String url = uri.toString();
        int targetWidth = Math.min(getResources().getDisplayMetrics().widthPixels, 1280);
        int targetHeight = Math.min(dp(heroHeightDp()), 720);
        String scheme = uri.getScheme();
        android.graphics.Bitmap bitmap;
        if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
            java.io.File file = new java.io.File(new java.io.File(getCacheDir(), "google-photos"),
                    remoteWallpaperCacheName(url));
            if (!file.isFile()) return;
            bitmap = decodeBackgroundImage(file, targetWidth, targetHeight);
        } else {
            bitmap = decodeWallpaper(uri, targetWidth, targetHeight);
        }
        if (bitmap == null) return;
        backgroundBitmapCache.put(url, bitmap);
        slideA.setImageBitmap(bitmap);
        slideA.setTag(uri);
        slideA.setVisibility(View.VISIBLE);
        slideA.setAlpha(1f);
        slideAActive = false;
        backgroundIndex = backgroundPhotos.size() > 1 ? 1 : 0;
    }

    private FolderScanResult enumeratePhotos(Uri treeUri) {
        ArrayList<BackgroundPhoto> photos = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        try {
            boolean success;
            if ("file".equals(treeUri.getScheme())) {
                success = collectLocalPhotos(new java.io.File(treeUri.getPath()), "", visited, photos, 0);
            } else {
                success = collectPhotos(treeUri, DocumentsContract.getTreeDocumentId(treeUri), "", visited, photos, 0);
            }
            Collections.sort(photos, (left, right) -> left.name.compareToIgnoreCase(right.name));
            return new FolderScanResult(photos, success);
        } catch (Exception ignored) { return new FolderScanResult(photos, false); }
    }

    private boolean collectLocalPhotos(java.io.File folder, String parentPath, Set<String> visited,
                                       List<BackgroundPhoto> photos, int depth) {
        if (depth > 16 || !visited.add(folder.getAbsolutePath())) return true;
        java.io.File[] children = folder.listFiles();
        if (children == null) return false;
        for (java.io.File child : children) {
            if (child.isDirectory()) {
                collectLocalPhotos(child, parentPath + child.getName() + "/", visited, photos, depth + 1);
            } else if (isImageDocument(null, child.getName())) {
                photos.add(new BackgroundPhoto(Uri.fromFile(child), parentPath + child.getName()));
            }
        }
        return true;
    }

    private boolean collectPhotos(Uri treeUri, String documentId, String parentPath, Set<String> visited,
                                  List<BackgroundPhoto> photos, int depth) {
        if (depth > 16 || !visited.add(documentId)) return true;
        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId);
        String[] columns = {DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE};
        try (Cursor cursor = getContentResolver().query(childrenUri, columns, null, null, null)) {
            if (cursor == null) return false;
            while (cursor.moveToNext()) {
                String childId = cursor.getString(0);
                String name = cursor.getString(1);
                String mime = cursor.getString(2);
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                    collectPhotos(treeUri, childId, parentPath + (name == null ? "" : name) + "/", visited, photos, depth + 1);
                } else if (isImageDocument(mime, name)) {
                    Uri documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId);
                    photos.add(new BackgroundPhoto(documentUri, parentPath + (name == null ? childId : name)));
                }
            }
            return true;
        } catch (Exception ignored) { return false; }
    }

    private static boolean isImageDocument(String mime, String name) {
        if (mime != null && mime.startsWith("image/")) return true;
        if (name == null) return false;
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png")
                || lower.endsWith(".webp") || lower.endsWith(".heic") || lower.endsWith(".heif")
                || lower.endsWith(".avif") || lower.endsWith(".bmp");
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
        if (screenActive && settingsScreen == null) showNextBackgroundPhoto();
    }

    private boolean moodGalleryActive;

    /** Applies the chosen background mood, unless a photo folder or album was set in the full settings. */
    private void applyMood() {
        if (!preferences.getString("wallpaperFolder", "").isEmpty()
                || !preferences.getString("wallpaperAlbum", "").isEmpty()) return;
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

    /** Choosing a mood replaces any single photo, folder or album chosen in the full settings. */
    private void clearPhotoWallpapers() {
        preferences.edit().remove("wallpaper").remove("wallpaperFolder").remove("wallpaperAlbum").apply();
    }

    private void scheduleNextBackgroundPhoto() {
        if (slideshowTick == null || !screenActive || settingsScreen != null || backgroundPhotos.size() < 2) return;
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
        if (backgroundPhotos.isEmpty() || !screenActive || settingsScreen != null || isFinishing()) return;
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

    private static final class FolderScanResult {
        final List<BackgroundPhoto> photos;
        final boolean success;
        FolderScanResult(List<BackgroundPhoto> photos, boolean success) {
            this.photos = photos;
            this.success = success;
        }
    }

    private static final class ChannelRow {
        final LinearLayout container;
        final RecyclerView list;
        final ChannelAdapter adapter;
        int pendingUpdateGeneration;
        ChannelRow(LinearLayout container, RecyclerView list, ChannelAdapter adapter) {
            this.container = container;
            this.list = list;
            this.adapter = adapter;
        }

        boolean requestFocus() {
            if (list != null) {
                View focused = list.getFocusedChild();
                if (focused != null && focused.requestFocus()) return true;
                if (list.getChildCount() > 0) {
                    View first = list.getChildAt(0);
                    if (first != null && first.requestFocus()) return true;
                }
                if (list.requestFocus()) return true;
            }
            return container != null && container.requestFocus();
        }
    }

    @androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
    private void playChannel(IptvRepository.Channel channel) {
        PlaybackLauncher.watch(this, channel, channels);
    }

    private boolean toggleFavoriteChannel(IptvRepository.Channel channel) {
        if (channel == null) return false;
        boolean added = FavoriteChannels.toggle(favoriteChannelKeys, channel);
        FavoriteChannels.save(preferences, favoriteChannelKeys);
        Toast.makeText(this, (added ? "Added to" : "Removed from") + " favourites: " + channel.name, Toast.LENGTH_SHORT).show();
        renderChannels();
        return true;
    }

    private void refreshChannels() {
        if (channelsRefreshing) return;
        channelsRefreshing = true;
        status.setVisibility(View.VISIBLE);
        status.setText("Refreshing channels…");
        if (settingsScreen != null) settingsScreen.onRefreshResult("Refreshing channels…");
        iptv.refreshChannels((loaded, message) -> lifecycleWork.post(() -> {
            channelsRefreshing = false;
            channels.clear(); channels.addAll(loaded);
            renderChannels();
            if (!loaded.isEmpty()) ((SvaApplication) getApplication()).guideReminders()
                    .reconcile(channels, hiddenChannelKeys);
            openPendingReminder();
            status.setText(message);
            if (settingsScreen != null) settingsScreen.onRefreshResult(message);
            if (!channels.isEmpty() && !iptv.guideUrl().isEmpty() && iptv.isGuideStale(channels)) refreshGuide(false);
            scheduleLiveRefreshCheck();
        }));
    }

    private void refreshGuide(boolean userRequested) {
        if (guideRefreshing) return;
        guideRefreshing = true;
        String refreshing = userRequested ? "Refreshing programme guide…" : "Updating today's programme guide…";
        status.setVisibility(View.VISIBLE);
        status.setText(refreshing);
        if (settingsScreen != null) settingsScreen.onRefreshResult(refreshing);
        iptv.refreshGuide(new ArrayList<>(channels), (loaded, message) -> lifecycleWork.post(() -> {
            guideRefreshing = false;
            channels.clear(); channels.addAll(loaded);
            renderChannels();
            if (!loaded.isEmpty()) ((SvaApplication) getApplication()).guideReminders()
                    .reconcile(channels, hiddenChannelKeys);
            status.setText(message);
            if (settingsScreen != null) settingsScreen.onRefreshResult(message);
            scheduleLiveRefreshCheck();
        }));
    }

    private void maybeRefreshLiveData() {
        if (!screenActive) return;
        if (!iptv.playlistUrl().isEmpty() && iptv.isChannelsStale()) {
            refreshChannels();
        } else if (!channels.isEmpty() && !iptv.guideUrl().isEmpty() && iptv.isGuideStale(channels)) {
            refreshGuide(false);
        }
    }

    private void scheduleLiveRefreshCheck() {
        if (guideRefreshTick == null) return;
        handler.removeCallbacks(guideRefreshTick);
        if (!screenActive) return;
        long channelDelay = iptv.playlistUrl().isEmpty() ? Long.MAX_VALUE : iptv.millisUntilChannelsRefresh();
        long guideDelay = channels.isEmpty() || iptv.guideUrl().isEmpty()
                ? Long.MAX_VALUE : iptv.millisUntilGuideRefresh();
        long delay = Math.min(channelDelay, guideDelay);
        if (delay != Long.MAX_VALUE) handler.postDelayed(guideRefreshTick, Math.max(60_000L, delay));
    }

    private volatile boolean restoringBackup;
    private SettingsScreen settingsScreen;

    private void styleDialog(AlertDialog dialog) {
        Motion.dialog(dialog);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(roundedShape(LiquidTheme.surface(this), LiquidTheme.border(this), 1, 26));
            window.setDimAmount(.35f);
        }
        for (int which : new int[]{AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL}) {
            Button button = dialog.getButton(which);
            if (button != null) {
                styleFocusableTile(button);
                button.setTextColor(LiquidTheme.text(this));
                button.setAllCaps(false);
                ViewGroup.LayoutParams lp = button.getLayoutParams();
                if (lp instanceof ViewGroup.MarginLayoutParams) {
                    ((ViewGroup.MarginLayoutParams) lp).setMargins(dp(6), dp(4), dp(6), dp(4));
                }
                if (button.getParent() instanceof ViewGroup) {
                    ViewGroup parent = (ViewGroup) button.getParent();
                    parent.setClipChildren(false);
                    parent.setClipToPadding(false);
                }
            }
        }
    }

    private void showSettings() {
        if (settingsScreen != null) return;
        settingsScreen = new SettingsScreen();
        settingsScreen.show();
    }

    private final class SettingsScreen {
        private static final int LIVE = 0, CHANNELS = 1, APPS = 2, APPEARANCE = 3, CLOCK = 4, DRM = 5, HOME = 6, BACKUP = 7, YOUTUBE = 8, CALENDAR = 9, SCHEDULES = 10, GAMES = 11, WEATHER = 12, NAVIGATION = 13;
        private final String[] sections = {"Live TV", "Channels", "Apps", "Appearance", "Clock", "Playback & DRM", "Home button", "Backup & restore", "YouTube", "Calendar", "Schedules", "Games", "Weather", "Navigation"};
        // Live TV, Channels, YouTube, Games and Playback & DRM are removed from this build.
        private final int[] NAV_ORDER = {
            // Group 0: Content (1 item)
            CALENDAR,
            // Group 1: Home & Display (5 items)
            APPS, APPEARANCE, CLOCK, WEATHER, NAVIGATION,
            // Group 2: System & Advanced (3 items)
            SCHEDULES, HOME, BACKUP
        };
        private final String[] GROUP_LABELS = {"Content", "Home & Display", "System"};
        private final String[] descriptions = {
                "Playlist and programme guide", "Choose which channels appear in Live TV",
                "Choose and order your home shortcuts", "Photo backdrop and wallpaper",
                "Time and date on Home", "Default Widevine license settings",
                "Open Sva when the TV Home button opens its built-in launcher",
                "Save or restore launcher settings and channels", "Save videos and playlists in Saved",
                "Show events from a public Google Calendar", "Choose when content updates automatically",
                "Import and manage your local NES library", "Current conditions and a three-day forecast",
                "Choose which categories appear and their order"
        };
        private final boolean compactSettings = getResources().getDisplayMetrics().widthPixels
                / getResources().getDisplayMetrics().density < 1100;
        private final Dialog dialog = new Dialog(MainActivity.this, R.style.SvaFullScreenDialogTheme);
        private final Button[] tabs = new Button[sections.length];
        private final FrameLayout detail = new FrameLayout(MainActivity.this);
        private LinearLayout rail;
        private int section = APPEARANCE;
        private int renderedSection = -1;
        private final Map<Integer, Integer> sectionScroll = new HashMap<>();
        private final Map<Integer, int[]> sectionFocus = new HashMap<>();
        private final Map<Integer, android.os.Parcelable> listState = new HashMap<>();
        private View firstControl;
        private EditText playlistField, guideField, licenseField, headersField, calendarUrlField;
        private EditText youtubeUrlField, youtubeTitleField, youtubeArtField;
        private TextView liveFeedback, cacheSummary, guideSummary, youtubeFeedback, calendarFeedback, calendarSummary, weatherFeedback;
        private LinearLayout youtubeSavedList, weatherSearchResults;
        private ChannelSettingsAdapter channelAdapter;
        private AppSettingsAdapter appAdapter;
        private RecyclerView appList;
        private List<AppEntry> availableApps;
        private boolean loadingApps, appsChanged, channelsChanged, youtubeChanged;
        private String draftPlaylist = iptv.playlistUrl();
        private String draftGuide = iptv.guideUrl();
        private String draftLicense = iptv.drmLicenseUrl();
        private String draftHeaders = iptv.drmHeaderText();
        private String draftYoutubeUrl = "";
        private String draftYoutubeTitle = "";
        private String draftYoutubeArt = "";
        private String draftCalendarUrl = preferences.getString("publicCalendarUrl", "");

        void show() {
            if (section == LIVE || section == CHANNELS || section == YOUTUBE || section == GAMES || section == DRM)
                section = APPEARANCE;
            LinearLayout shell = new LinearLayout(MainActivity.this);
            shell.setOrientation(LinearLayout.VERTICAL);
            shell.setPadding(dp(compactSettings ? 24 : 40), dp(20), dp(compactSettings ? 24 : 40), dp(24));
            // Settings use a quiet, solid surface independently of the Home wallpaper.
            shell.setBackgroundColor(SettingsUi.background(MainActivity.this));

            // ── Header ────────────────────────────────────────────────────────────
            LinearLayout heading = new LinearLayout(MainActivity.this);
            heading.setGravity(Gravity.CENTER_VERTICAL);
            shell.addView(heading, new LinearLayout.LayoutParams(-1, dp(60)));
            LinearLayout headingCopy = new LinearLayout(MainActivity.this);
            headingCopy.setOrientation(LinearLayout.VERTICAL);
            headingCopy.setGravity(Gravity.CENTER_VERTICAL);
            heading.addView(headingCopy, new LinearLayout.LayoutParams(0, -2, 1));
            TextView eyebrow = label("Sva Launcher", 14, SettingsUi.muted(MainActivity.this), false);

            LinearLayout.LayoutParams eyeParams = new LinearLayout.LayoutParams(-2, -2);
            eyeParams.bottomMargin = dp(2);
            headingCopy.addView(eyebrow, eyeParams);
            TextView title = label("Settings", 28, SettingsUi.text(MainActivity.this), true);
            headingCopy.addView(title);
            // Thin separator below header
            View accentLine = new View(MainActivity.this);
            accentLine.setBackgroundColor(LiquidTheme.border(MainActivity.this));
            LinearLayout.LayoutParams accentParams = new LinearLayout.LayoutParams(-1, dp(1));
            accentParams.bottomMargin = dp(12);
            shell.addView(accentLine, accentParams);

            // ── Body (rail + detail) ──────────────────────────────────────────────
            LinearLayout body = new LinearLayout(MainActivity.this);
            shell.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));

            // Fixed outer frame for navigation rail
            FrameLayout railFrame = new FrameLayout(MainActivity.this);
            railFrame.setBackgroundColor(SettingsUi.background(MainActivity.this));

            ScrollView railScroll = new ScrollView(MainActivity.this);
            railScroll.setFillViewport(true);
            railScroll.setSmoothScrollingEnabled(true);
            railScroll.setVerticalScrollBarEnabled(false);
            railScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
            railScroll.setClipToPadding(false);
            railScroll.setPadding(dp(8), dp(8), dp(8), dp(8));

            rail = new LinearLayout(MainActivity.this);
            rail.setOrientation(LinearLayout.VERTICAL);
            rail.setClipChildren(false);
            rail.setClipToPadding(false);
            railScroll.addView(rail, new FrameLayout.LayoutParams(-1, -2));
            railFrame.addView(railScroll, new FrameLayout.LayoutParams(-1, -1));

            LinearLayout.LayoutParams railParams = new LinearLayout.LayoutParams(dp(compactSettings ? 220 : 260), -1);
            railParams.rightMargin = dp(12);
            body.addView(railFrame, railParams);

            // Build grouped rail with NAV_ORDER
            int lastGroup = -1;
            for (int i = 0; i < NAV_ORDER.length; i++) {
                final int target = NAV_ORDER[i];
                int group = (i < 1) ? 0 : (i < 6 ? 1 : 2);
                if (group != lastGroup) {
                    if (lastGroup >= 0) {
                        View div = new View(MainActivity.this);
                        div.setBackgroundColor(LiquidTheme.border(MainActivity.this));
                        LinearLayout.LayoutParams divParams = new LinearLayout.LayoutParams(-1, dp(1));
                        divParams.setMargins(dp(6), dp(6), dp(6), dp(8));
                        rail.addView(div, divParams);
                    }
                    TextView groupLabel = label(GROUP_LABELS[group],
                            14, SettingsUi.muted(MainActivity.this), false);

                    LinearLayout.LayoutParams glParams = new LinearLayout.LayoutParams(-1, dp(36));
                    glParams.setMargins(dp(10), lastGroup < 0 ? dp(2) : 0, dp(8), dp(2));
                    rail.addView(groupLabel, glParams);
                    lastGroup = group;
                }
                Button tab = new Button(MainActivity.this);
                tab.setText(sections[target]);
                tab.setCompoundDrawablesWithIntrinsicBounds(SettingsUi.icon(MainActivity.this, target), null, null, null);
                tab.setCompoundDrawablePadding(dp(12));
                tab.setTypeface(LiquidTheme.font(MainActivity.this));
                tab.setTextSize(18);
                tab.setAllCaps(false);
                tab.setMinWidth(0);
                tab.setMinimumWidth(0);
                tab.setMinHeight(0);
                tab.setMinimumHeight(0);
                tab.setSingleLine(false);
                tab.setMaxLines(2);
                tab.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
                tab.setPadding(dp(14), 0, dp(10), 0);
                tab.setFocusable(true);
                tab.setId(View.generateViewId());
                tab.setOnFocusChangeListener(null);
                tab.setStateListAnimator(null);
                tab.setElevation(0);
                tab.setOnClickListener(v -> {
                    captureDrafts();
                    sectionScroll.put(target, 0);
                    sectionFocus.remove(target);
                    section = target;
                    renderSection();
                    if (firstControl != null) firstControl.requestFocus();
                });
                tabs[target] = tab;
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(64));
                params.setMargins(dp(6), dp(1), dp(6), dp(3));
                rail.addView(tab, params);
            }
            detail.setBackgroundColor(SettingsUi.background(MainActivity.this));
            body.addView(detail, new LinearLayout.LayoutParams(0, -1, 1));

            dialog.setContentView(shell);
            dialog.setCancelable(false);
            dialog.setOnKeyListener((ignored, keyCode, event) -> {
                View focused = dialog.getCurrentFocus();
                if (keyCode == KeyEvent.KEYCODE_BACK) {
                    if (event.getAction() == KeyEvent.ACTION_UP) {
                        if (focused instanceof EditText) {
                            android.view.inputmethod.InputMethodManager keyboard =
                                    (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                            if (keyboard != null) keyboard.hideSoftInputFromWindow(focused.getWindowToken(), 0);
                            focused.clearFocus();
                            tabs[section].requestFocus();
                        } else requestClose();
                    }
                    return true;
                }
                if (event.getAction() != KeyEvent.ACTION_DOWN || focused == null) return false;
                if (!isDpadDirection(keyCode)) return false;
                int direction = focusDirection(keyCode);
                // The input keeps horizontal arrows for cursor movement and multiline navigation.
                if (focused instanceof EditText) return false;
                if (rail != null && isWithinPage(focused, rail)) {
                    if (direction == View.FOCUS_UP && focused == tabs[NAV_ORDER[0]]) return true;
                    if (direction == View.FOCUS_RIGHT) {
                        if (focused == tabs[section] && firstControl != null && firstControl.requestFocus()) return true;
                        return true;
                    }
                    if (direction == View.FOCUS_LEFT) return true;
                    View next = focused.focusSearch(direction);
                    if (next != null && isWithinPage(next, rail)
                            && next.isFocusable() && next.isEnabled() && next.requestFocus()) return true;
                    if (next != null) return true;
                    if (canScrollWithinPage(focused, rail, direction)) return false;
                    return true;
                }
                if (isWithinPage(focused, detail)) {
                    if (direction == View.FOCUS_LEFT && !(focused instanceof EditText)) {
                        tabs[section].requestFocus(); return true;
                    }
                    View next = focused.focusSearch(direction);
                    if (next != null && isWithinPage(next, detail)) {
                        if (next.isFocusable() && next.isEnabled() && next.requestFocus()) return true;
                        return true;
                    }
                    if (direction == View.FOCUS_LEFT && tabs[section].requestFocus()) return true;
                    if (direction == View.FOCUS_UP && !canScrollWithinPage(focused, detail, direction)) {
                        tabs[section].requestFocus(); return true;
                    }
                    if (next != null) return true;
                    if (canScrollWithinPage(focused, detail, direction)) return false;
                    return true;
                }
                return false;
            });
            dialog.setOnDismissListener(ignored -> {
                if (settingsScreen == this) settingsScreen = null;
                if (appsChanged) renderApps();
                Set<String> freshHidden = HiddenChannels.load(preferences);
                Set<String> freshFavorites = FavoriteChannels.load(preferences);
                if (channelsChanged || !freshHidden.equals(hiddenChannelKeys) || !freshFavorites.equals(favoriteChannelKeys)) {
                    hiddenChannelKeys.clear();
                    hiddenChannelKeys.addAll(freshHidden);
                    favoriteChannelKeys.clear();
                    favoriteChannelKeys.addAll(freshFavorites);
                    renderChannels();
                    ((SvaApplication) getApplication()).guideReminders().reconcile(channels, hiddenChannelKeys);
                }
                if (youtubeChanged) renderYouTubeCards();
                if (screenActive && !backgroundPhotos.isEmpty()) {
                    if (slideA.getVisibility() == View.VISIBLE || slideB.getVisibility() == View.VISIBLE) {
                        scheduleNextBackgroundPhoto();
                    } else showNextBackgroundPhoto();
                }
            });
            renderSection();
            Motion.dialog(dialog); dialog.show();
            BackNavigation.install(dialog, this::requestClose);
            if (slideshowTick != null) handler.removeCallbacks(slideshowTick);
            backgroundImageGeneration++;
            Window window = dialog.getWindow();
            if (window != null) {
                window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
                window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
                window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                WindowManager.LayoutParams windowAttributes = window.getAttributes();
                windowAttributes.dimAmount = .12f;
                window.setAttributes(windowAttributes);
                window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
                window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
            }
            tabs[section].requestFocus();
        }

        private void captureDrafts() {
            rememberLayout();
            if (playlistField != null) draftPlaylist = playlistField.getText().toString();
            if (guideField != null) draftGuide = guideField.getText().toString();
            if (licenseField != null) draftLicense = licenseField.getText().toString();
            if (headersField != null) draftHeaders = headersField.getText().toString();
            if (youtubeUrlField != null) draftYoutubeUrl = youtubeUrlField.getText().toString();
            if (youtubeTitleField != null) draftYoutubeTitle = youtubeTitleField.getText().toString();
            if (youtubeArtField != null) draftYoutubeArt = youtubeArtField.getText().toString();
            if (calendarUrlField != null) draftCalendarUrl = calendarUrlField.getText().toString();
            playlistField = guideField = licenseField = headersField = null;
            youtubeUrlField = youtubeTitleField = youtubeArtField = null;
            calendarUrlField = null;
            liveFeedback = null;
        }

        private void saveDrafts(Bundle state) {
            state.putString("settingsPlaylist", playlistField == null ? draftPlaylist : playlistField.getText().toString());
            state.putString("settingsGuide", guideField == null ? draftGuide : guideField.getText().toString());
            state.putString("settingsLicense", licenseField == null ? draftLicense : licenseField.getText().toString());
            state.putString("settingsHeaders", headersField == null ? draftHeaders : headersField.getText().toString());
            state.putString("settingsCalendar", calendarUrlField == null ? draftCalendarUrl : calendarUrlField.getText().toString());
            state.putString("settingsYoutubeUrl", youtubeUrlField == null ? draftYoutubeUrl : youtubeUrlField.getText().toString());
            state.putString("settingsYoutubeTitle", youtubeTitleField == null ? draftYoutubeTitle : youtubeTitleField.getText().toString());
            state.putString("settingsYoutubeArt", youtubeArtField == null ? draftYoutubeArt : youtubeArtField.getText().toString());
            rememberLayout();
            for (int i = 0; i < sections.length; i++) {
                state.putInt("settingsScroll" + i, sectionScroll.getOrDefault(i, 0));
                state.putIntArray("settingsFocus" + i, sectionFocus.get(i));
                state.putParcelable("settingsList" + i, listState.get(i));
            }
        }

        private void restoreDrafts(Bundle state) {
            draftPlaylist = state.getString("settingsPlaylist", draftPlaylist);
            draftGuide = state.getString("settingsGuide", draftGuide);
            draftLicense = state.getString("settingsLicense", draftLicense);
            draftHeaders = state.getString("settingsHeaders", draftHeaders);
            draftCalendarUrl = state.getString("settingsCalendar", draftCalendarUrl);
            draftYoutubeUrl = state.getString("settingsYoutubeUrl", "");
            draftYoutubeTitle = state.getString("settingsYoutubeTitle", "");
            draftYoutubeArt = state.getString("settingsYoutubeArt", "");
            for (int i = 0; i < sections.length; i++) {
                sectionScroll.put(i, state.getInt("settingsScroll" + i, 0));
                if (state.getIntArray("settingsFocus" + i) != null) sectionFocus.put(i, state.getIntArray("settingsFocus" + i));
                if (state.getParcelable("settingsList" + i) != null) listState.put(i, state.getParcelable("settingsList" + i));
            }
        }

        private View findContainer(View root, Class<?> type) {
            if (type.isInstance(root)) return root;
            if (root instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) root;
                for (int i = 0; i < group.getChildCount(); i++) {
                    View found = findContainer(group.getChildAt(i), type);
                    if (found != null) return found;
                }
            }
            return null;
        }

        private void rememberLayout() {
            if (renderedSection < 0) return;
            View scroll = findContainer(detail, ScrollView.class);
            if (scroll != null) sectionScroll.put(renderedSection, scroll.getScrollY());
            View list = findContainer(detail, RecyclerView.class);
            if (list instanceof RecyclerView && ((RecyclerView) list).getLayoutManager() != null)
                listState.put(renderedSection, ((RecyclerView) list).getLayoutManager().onSaveInstanceState());
            View focused = detail.findFocus();
            if (focused == null) return;
            ArrayList<Integer> reversed = new ArrayList<>();
            for (View child = focused; child != detail && child.getParent() instanceof ViewGroup; ) {
                ViewGroup parent = (ViewGroup)child.getParent();
                reversed.add(parent.indexOfChild(child)); child = parent;
            }
            int[] path = new int[reversed.size()];
            for (int i = 0; i < path.length; i++) path[i] = reversed.get(path.length - 1 - i);
            sectionFocus.put(renderedSection, path);
        }

        private void restoreLayout(boolean restoreFocus) {
            final int target = section;
            detail.post(() -> {
                if (settingsScreen != this || renderedSection != target) return;
                View scroll = findContainer(detail, ScrollView.class);
                if (scroll != null) scroll.scrollTo(0, sectionScroll.getOrDefault(target, 0));
                View list = findContainer(detail, RecyclerView.class);
                if (list instanceof RecyclerView && ((RecyclerView)list).getLayoutManager() != null && listState.containsKey(target))
                    ((RecyclerView)list).getLayoutManager().onRestoreInstanceState(listState.get(target));
                if (!restoreFocus) return;
                View focused = detail;
                int[] path = sectionFocus.get(target);
                if (path != null) for (int index : path) {
                    if (!(focused instanceof ViewGroup) || index >= ((ViewGroup)focused).getChildCount()) { focused = null; break; }
                    focused = ((ViewGroup)focused).getChildAt(index);
                }
                if (focused == null || focused == detail || !focused.requestFocus()) {
                    if (firstControl != null) firstControl.requestFocus();
                }
            });
        }

        void resetDrafts() {
            draftPlaylist = iptv.playlistUrl();
            draftGuide = iptv.guideUrl();
            draftLicense = iptv.drmLicenseUrl();
            draftHeaders = iptv.drmHeaderText();
            draftCalendarUrl = preferences.getString("publicCalendarUrl", "");
            draftYoutubeUrl = "";
            draftYoutubeTitle = "";
            draftYoutubeArt = "";
            if (playlistField != null) playlistField.setText(draftPlaylist);
            if (guideField != null) guideField.setText(draftGuide);
            if (licenseField != null) licenseField.setText(draftLicense);
            if (headersField != null) headersField.setText(draftHeaders);
            if (calendarUrlField != null) calendarUrlField.setText(draftCalendarUrl);
            if (youtubeUrlField != null) youtubeUrlField.setText("");
            if (youtubeTitleField != null) youtubeTitleField.setText("");
            if (youtubeArtField != null) youtubeArtField.setText("");
            appsChanged = false;
            channelsChanged = false;
            youtubeChanged = false;
        }

        private boolean hasUnsavedEdits() {
            if (restoringBackup) return false;
            captureDrafts();
            return !draftPlaylist.trim().equals(iptv.playlistUrl().trim())
                    || !draftGuide.trim().equals(iptv.guideUrl().trim())
                    || !draftLicense.trim().equals(iptv.drmLicenseUrl().trim())
                    || !draftHeaders.trim().equals(iptv.drmHeaderText().trim())
                    || !draftCalendarUrl.trim().equals(preferences.getString("publicCalendarUrl", "").trim());
        }

        private void requestClose() {
            if (restoringBackup) { dialog.dismiss(); return; }
            if (!hasUnsavedEdits()) { dialog.dismiss(); return; }
            AlertDialog prompt = new AlertDialog.Builder(new android.view.ContextThemeWrapper(MainActivity.this,
                    LiquidTheme.alertDialogTheme(MainActivity.this)))
                    .setTitle("Discard unsaved edits?")
                    .setMessage("Playlist, DRM, or calendar changes have not been saved.")
                    .setPositiveButton("Discard", (ignored, which) -> dialog.dismiss())
                    .setNegativeButton("Keep editing", null).create();
            Motion.dialog(prompt); prompt.show();
            styleDialog(prompt);
        }

        private void renderSection() {
            boolean animateSection = renderedSection >= 0 && renderedSection != section;
            boolean restoreFocus = detail.findFocus() != null;
            rememberLayout();
            renderedSection = section;
            firstControl = null;
            detail.removeAllViews();
            if (section == LIVE || section == CHANNELS || section == YOUTUBE || section == GAMES || section == DRM)
                section = APPEARANCE;
            for (int i = 0; i < tabs.length; i++) {
                if (tabs[i] == null) continue;
                boolean selected = i == section;
                tabs[i].setTextColor(selected ? SettingsUi.text(MainActivity.this) : SettingsUi.muted(MainActivity.this));
                tabs[i].setSelected(selected);
                tabs[i].setBackground(SettingsUi.navigationStates(MainActivity.this, selected));
            }
            // Detail page
            LinearLayout page = new LinearLayout(MainActivity.this);
            page.setOrientation(LinearLayout.VERTICAL);
            page.setPadding(dp(16), dp(8), dp(16), dp(4));
            detail.addView(page, new FrameLayout.LayoutParams(-1, -1));

            // Section header: glyph badge + title + subtitle
            LinearLayout headerRow = new LinearLayout(MainActivity.this);
            headerRow.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams headerParams = new LinearLayout.LayoutParams(-1, -2);
            headerParams.bottomMargin = dp(10);
            page.addView(headerRow, headerParams);

            LinearLayout headerCopy = new LinearLayout(MainActivity.this);
            headerCopy.setOrientation(LinearLayout.VERTICAL);
            headerCopy.setGravity(Gravity.CENTER_VERTICAL);
            headerRow.addView(headerCopy, new LinearLayout.LayoutParams(0, -2, 1));
            TextView title = label(sections[section], 28, SettingsUi.text(MainActivity.this), true);
            title.setTypeface(LiquidTheme.font(MainActivity.this));
            headerCopy.addView(title);
            TextView subtitle = label(descriptions[section], 14, SettingsUi.muted(MainActivity.this), false);
            subtitle.setMaxLines(2);
            LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(-1, -2);
            subParams.topMargin = dp(2);
            headerCopy.addView(subtitle, subParams);

            // Thin separator below header
            View separator = new View(MainActivity.this);
            separator.setBackgroundColor(LiquidTheme.border(MainActivity.this));
            LinearLayout.LayoutParams sepParams = new LinearLayout.LayoutParams(-1, dp(1));
            sepParams.bottomMargin = dp(10);
            page.addView(separator, sepParams);

            switch (section) {
                case LIVE: renderLive(page); break;
                case CHANNELS: renderChannelSettings(page); break;
                case APPS: renderAppSettings(page); break;
                case APPEARANCE: renderAppearance(page); break;
                case CLOCK: renderClock(page); break;
                case DRM: renderDrm(page); break;
                case HOME: renderHomeOverride(page); break;
                case BACKUP: renderBackup(page); break;
                case YOUTUBE: renderYouTubeSettings(page); break;
                case CALENDAR: renderCalendarSettings(page); break;
                case SCHEDULES: renderSchedules(page); break;
                case GAMES: renderGameSettings(page); break;
                case WEATHER: renderWeatherSettings(page); break;
                case NAVIGATION: renderNavigationSettings(page); break;
                default: break;
            }
            if (firstControl != null) {
                firstControl.setId(View.generateViewId());
                tabs[section].setNextFocusRightId(firstControl.getId());
                firstControl.setNextFocusLeftId(tabs[section].getId());
                firstControl.setNextFocusUpId(tabs[section].getId());
            }
            restoreLayout(restoreFocus);
            if (animateSection) Motion.reveal(page);
        }

        private void renderGameSettings(LinearLayout page) {
            LinearLayout form = scrollForm(page);
            addFormSectionHeader(form, "Library");
            addFormHint(form, "Sva supports user-provided .nes files only. Games are copied into private app storage; original files are never changed.");
            addFormButton(form, "Open game library and import", v -> openGameLibrary());
            addFormSectionHeader(form, "Controllers");
            addFormButton(form, "Configure game controllers", v -> startActivity(new Intent(MainActivity.this, ControllerSettingsActivity.class)));
            addFormHint(form, "Nestopia UE is embedded in Sva. On a TV remote, use the arrow buttons to move, OK for A, and hold OK for Start. Gamepads are assigned to players one and two automatically. During play, Back opens Resume, Restart, and Exit.");
            addFormHint(form, "Nestopia UE · GPL-2.0-or-later · Sva combined application GPLv3. Source: github.com/libretro/nestopia");
        }

        private void renderNavigationSettings(LinearLayout page) {
            LinearLayout form = scrollForm(page);
            LinearLayout orderRows = new LinearLayout(MainActivity.this);
            orderRows.setOrientation(LinearLayout.VERTICAL);
            orderRows.setClipChildren(false);
            orderRows.setClipToPadding(false);
            addFormSectionHeader(form, "Visible categories");
            addFormHint(form, "Choose which categories appear in the navigation bar. Your games and calendar data stay saved.");
            addToggle(form, "Show Events in navigation", !preferences.getBoolean(PREF_HIDE_EVENTS, false), enabled -> {
                preferences.edit().putBoolean(PREF_HIDE_EVENTS, !enabled).apply();
                rebuildNavigationButtons();
                renderNavigationOrder(orderRows, -1);
                if (!enabled && activeTab == TAB_EVENTS) selectTab(TAB_HOME, true);
            });
            addFormSectionHeader(form, "Item order");
            addFormHint(form, "Home stays first. Select a category to change its position. Hidden categories keep their place.");
            form.addView(orderRows);
            renderNavigationOrder(orderRows, -1);
            addFormButton(form, "Reset item order", v -> {
                preferences.edit().remove(NavigationPreferences.ORDER).remove(PREF_LIVE_TV_POSITION).apply();
                rebuildNavigationButtons();
                renderNavigationOrder(orderRows, -1);
            });
        }

        private void renderNavigationOrder(LinearLayout container, int focusTab) {
            container.removeAllViews();
            List<Integer> ordered = navigationOrder();
            String[] positions = {"First after Home", "Second after Home", "Third after Home", "Last"};
            for (int position = 0; position < ordered.size(); position++) {
                final int tab = ordered.get(position);
                if (isRemovedTab(tab)) continue;
                View row = SettingsUi.choice(MainActivity.this, TAB_TITLES[tab]
                        + (isNavigationHidden(tab) ? " · Hidden" : ""), positions, position, destination -> {
                    List<Integer> updated = navigationOrder();
                    updated.remove(Integer.valueOf(tab)); updated.add(destination, tab);
                    preferences.edit().putString(NavigationPreferences.ORDER, NavigationPreferences.encode(updated)).apply();
                    rebuildNavigationButtons(); renderNavigationOrder(container, tab);
                });
                row.setTag("navigation-order-" + tab);
                SettingsUi.add(container, row);
                if (focusTab == tab) row.post(row::requestFocus);
            }
        }

        private void renderWeatherSettings(LinearLayout page) {
            LinearLayout form = scrollForm(page);
            WeatherRepository.Location saved = savedWeatherLocation();
            // — Location ——————————————————————————————————————————————————————
            addFormSectionHeader(form, "Location");
            addFormHint(form, saved == null ? "Choose a city or use this TV's location to show weather on Home."
                    : "Weather location · " + saved.label);
            EditText search = addInput(form, "Search city or postal code", "Pune, India", "", false);
            weatherSearchResults = new LinearLayout(MainActivity.this);
            weatherSearchResults.setOrientation(LinearLayout.VERTICAL);
            weatherSearchResults.setClipChildren(false);
            weatherSearchResults.setClipToPadding(false);
            addFormButton(form, "Search", v -> {
                String query = search.getText().toString().trim();
                if (query.length() < 2) { setWeatherFeedback("Enter at least two characters."); return; }
                setWeatherFeedback("Searching locations…"); weatherSearchResults.removeAllViews();
                lifecycleWork.execute(ioWorker, () -> {
                    List<WeatherRepository.Location> results = null; String error = null;
                    try { results = WeatherRepository.search(query, Locale.getDefault()); }
                    catch (Exception failure) { error = failure.getMessage() == null ? "Location search failed." : failure.getMessage(); }
                    List<WeatherRepository.Location> found = results; String failure = error;
                    lifecycleWork.post(() -> {
                        if (settingsScreen != this || section != WEATHER) return;
                        weatherSearchResults.removeAllViews();
                        if (failure != null) { setWeatherFeedback("Search failed · " + failure); return; }
                        if (found == null || found.isEmpty()) { setWeatherFeedback("No matching places found."); return; }
                        setWeatherFeedback("Choose a location.");
                        for (WeatherRepository.Location location : found) {
                            Button option = addFormButton(weatherSearchResults, location.label, null);
                            option.setOnClickListener(ignored -> saveWeatherLocation("city", location));
                        }
                    });
                });
            });
            form.addView(weatherSearchResults);
            addFormButton(form, "Use device location", v -> requestDeviceWeatherLocation());
            if (saved != null) addFormButton(form, "Remove weather", v -> {
                preferences.edit().remove("weatherLocationMode").remove("weatherLocationLabel")
                        .remove("weatherLatitude").remove("weatherLongitude").remove("weatherCache")
                        .remove("weatherUpdatedAt").remove("weatherAttemptedAt").apply();
                weatherForecast = null; weatherRequestGeneration++; weatherRefreshing = false;
                renderWeather(); renderSection();
            });
            // — Units ——————————————————————————————————————————————————————————
            addFormSectionHeader(form, "Units");
            addChoice(form, "Temperature", new String[]{"Celsius", "Fahrenheit"},
                    preferences.getBoolean("weatherFahrenheit", false) ? 1 : 0, index -> {
                        preferences.edit().putBoolean("weatherFahrenheit", index == 1).apply(); renderWeather();
                    });
            addChoice(form, "Wind speed", new String[]{"km/h", "mph"},
                    preferences.getBoolean("weatherMph", false) ? 1 : 0, index -> {
                        preferences.edit().putBoolean("weatherMph", index == 1).apply(); renderWeather();
                    });
            addFormButton(form, "Refresh now", v -> {
                if (!hasWeatherLocation()) { setWeatherFeedback("Choose a location first."); return; }
                if ("device".equals(preferences.getString("weatherLocationMode", ""))) requestDeviceWeatherLocation();
                else maybeRefreshWeather(true);
            });
            weatherFeedback = label("", 14, SettingsUi.muted(MainActivity.this), false);
            weatherFeedback.setPadding(dp(2), dp(6), dp(2), dp(6));
            form.addView(weatherFeedback);
            addFormHint(form, "Weather by Open-Meteo · Locations by GeoNames · Device location requested only on setup or manual refresh.");
        }

        private void setWeatherFeedback(String message) {
            if (weatherFeedback != null) weatherFeedback.setText(message == null ? "" : message);
        }

        private void renderHomeOverride(LinearLayout page) {
            LinearLayout form = scrollForm(page);
            addFormSectionHeader(form, "Override status");
            boolean enabled = isLauncherOverrideEnabled();
            addFormHint(form, enabled
                    ? "✓ Sva launcher override is enabled on this TV."
                    : "✕ Sva launcher override is currently disabled.");
            addFormHint(form, "Some TVs keep their built-in launcher as Home even when another launcher is selected. Enable Sva Launcher in Android Accessibility settings to reopen Sva whenever the TV opens its built-in launcher.");
            addFormSectionHeader(form, "Settings shortcuts");
            addFormButton(form, "Open Accessibility settings", v -> {
                try {
                    startActivity(new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS));
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "Could not open Accessibility settings.", Toast.LENGTH_LONG).show();
                }
            });
            addFormButton(form, "Try setting Sva as the Home app", v -> {
                try {
                    startActivity(new Intent("android.settings.HOME_SETTINGS"));
                } catch (Exception e) {
                    try { startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS)); }
                    catch (Exception ignored) { Toast.makeText(MainActivity.this, "Could not open Home settings.", Toast.LENGTH_LONG).show(); }
                }
            });
        }

        private void renderBackup(LinearLayout page) {
            LinearLayout form = scrollForm(page);
            addFormSectionHeader(form, "Create backup");
            addFormHint(form, "Create a JSON backup of launcher settings, app and channel choices, and IPTV details.");
            addFormButton(form, "Create backup file", v -> openBackupExportPicker());
            addFormSectionHeader(form, "Restore from backup");
            addFormHint(form, "Restoring replaces the current launcher settings and channel preferences. Keep backup files private.");
            addFormButton(form, "Restore from backup", v -> openBackupImportPicker());
        }

        private void renderSchedules(LinearLayout page) {
            LinearLayout form = scrollForm(page);
            addFormSectionHeader(form, "Refresh intervals");
            addFormHint(form, "Schedules run while Sva is open. Overdue content is checked when Home resumes; manual refresh buttons continue to work when automatic refresh is disabled.");
            addScheduleButton(form, "Photo slideshow", SchedulePreferences.SLIDESHOW,
                    SchedulePreferences.SLIDESHOW_VALUES, SchedulePreferences.DEFAULT_SLIDESHOW, true, false,
                    () -> {
                        if (slideshowTick != null) handler.removeCallbacks(slideshowTick);
                        scheduleNextBackgroundPhoto();
                    });
            addScheduleButton(form, "IPTV playlist", SchedulePreferences.PLAYLIST,
                    SchedulePreferences.PLAYLIST_VALUES, SchedulePreferences.DEFAULT_PLAYLIST, false, false,
                    () -> { maybeRefreshLiveData(); scheduleLiveRefreshCheck(); });
            addScheduleButton(form, "Programme guide", SchedulePreferences.GUIDE,
                    SchedulePreferences.GUIDE_VALUES, SchedulePreferences.DEFAULT_GUIDE, false, false,
                    () -> { maybeRefreshLiveData(); scheduleLiveRefreshCheck(); });
            addScheduleButton(form, "Public calendar", SchedulePreferences.CALENDAR,
                    SchedulePreferences.CALENDAR_VALUES, SchedulePreferences.DEFAULT_CALENDAR, false, false,
                    () -> { maybeRefreshPublicCalendar(false); scheduleCalendarRefresh(); });
            addScheduleButton(form, "Google Photos album", SchedulePreferences.GOOGLE_PHOTOS,
                    SchedulePreferences.GOOGLE_PHOTOS_VALUES, SchedulePreferences.DEFAULT_GOOGLE_PHOTOS, false, true,
                    () -> { maybeRefreshGooglePhotos(false); scheduleGooglePhotosRefresh(); });
            addScheduleButton(form, "Weather", SchedulePreferences.WEATHER,
                    SchedulePreferences.WEATHER_VALUES, SchedulePreferences.DEFAULT_WEATHER, false, true,
                    () -> { weatherRefreshedThisLaunch = false; maybeRefreshWeather(false); scheduleWeatherRefresh(); });
            addFormSectionHeader(form, "Defaults");
            addFormHint(form, "Paused keeps the current photo visible. Every launch refreshes Google Photos or weather once when Sva starts.");
            addFormButton(form, "Restore schedule defaults", v -> {
                preferences.edit()
                        .putLong(SchedulePreferences.SLIDESHOW, SchedulePreferences.DEFAULT_SLIDESHOW)
                        .putLong(SchedulePreferences.PLAYLIST, SchedulePreferences.DEFAULT_PLAYLIST)
                        .putLong(SchedulePreferences.GUIDE, SchedulePreferences.DEFAULT_GUIDE)
                        .putLong(SchedulePreferences.CALENDAR, SchedulePreferences.DEFAULT_CALENDAR)
                        .putLong(SchedulePreferences.GOOGLE_PHOTOS, SchedulePreferences.DEFAULT_GOOGLE_PHOTOS)
                        .putLong(SchedulePreferences.WEATHER, SchedulePreferences.DEFAULT_WEATHER)
                        .apply();
                maybeRefreshLiveData();
                scheduleLiveRefreshCheck();
                maybeRefreshPublicCalendar(false);
                scheduleCalendarRefresh();
                scheduleGooglePhotosRefresh();
                weatherRefreshedThisLaunch = false;
                maybeRefreshWeather(false);
                scheduleWeatherRefresh();
                scheduleNextBackgroundPhoto();
                renderSection();
            });
        }

        private void addScheduleButton(LinearLayout form, String title, String key, long[] values,
                                       long fallback, boolean slideshow, boolean googlePhotos,
                                       Runnable apply) {
            long current = scheduleValue(key, values, fallback);
            String[] labels = new String[values.length];
            int selected = 0;
            for (int i = 0; i < values.length; i++) {
                labels[i] = SchedulePreferences.label(values[i], slideshow, googlePhotos);
                if (values[i] == current) selected = i;
            }
            addChoice(form, title, labels, selected, index -> {
                preferences.edit().putLong(key, values[index]).apply(); apply.run();
            });
        }

        private void renderYouTubeSettings(LinearLayout page) {
            LinearLayout form = scrollForm(page);
            addFormSectionHeader(form, "Add shortcut");
            addFormHint(form, "Paste a public YouTube video or playlist link to add a card to Saved. Add an optional title; the full artwork thumbnail stays visible on the card.");
            youtubeUrlField = addInput(form, "Video or playlist link", "https://www.youtube.com/watch?v=…", draftYoutubeUrl, false);
            youtubeTitleField = addInput(form, "Card title · optional", "Use the YouTube title", draftYoutubeTitle, false);
            youtubeArtField = addInput(form, "Artwork video link · optional", "Use for playlist artwork", draftYoutubeArt, false);
            addFormButton(form, "Add to Saved", view -> {
                String value = youtubeUrlField == null ? draftYoutubeUrl : youtubeUrlField.getText().toString();
                String customTitle = youtubeTitleField == null ? draftYoutubeTitle : youtubeTitleField.getText().toString().trim();
                String artworkVideo = youtubeArtField == null ? draftYoutubeArt : youtubeArtField.getText().toString().trim();
                YouTubeCard parsed = YouTubeCard.parse(value);
                if (parsed == null) {
                    if (youtubeFeedback != null) youtubeFeedback.setText("Enter a valid YouTube video or playlist link.");
                    return;
                }
                List<YouTubeCard> cards = savedYouTubeCards();
                for (YouTubeCard card : cards) {
                    if (card.url.equals(parsed.url)) {
                        if (youtubeFeedback != null) youtubeFeedback.setText("That link is already in Saved.");
                        return;
                    }
                }
                if (!customTitle.isEmpty()) {
                    parsed.title = customTitle;
                    parsed.customTitle = true;
                }
                if (!artworkVideo.isEmpty() && !parsed.useArtworkFromVideo(artworkVideo)) {
                    if (youtubeFeedback != null) youtubeFeedback.setText("Artwork must be a YouTube video link.");
                    return;
                }
                cards.add(parsed);
                saveYouTubeCards(cards);
                youtubeChanged = true;
                draftYoutubeUrl = "";
                draftYoutubeTitle = "";
                draftYoutubeArt = "";
                if (youtubeUrlField != null) youtubeUrlField.setText("");
                if (youtubeTitleField != null) youtubeTitleField.setText("");
                if (youtubeArtField != null) youtubeArtField.setText("");
                if (youtubeFeedback != null) youtubeFeedback.setText("Added to Saved.");
                renderYouTubeSavedList();
                if (youtubeUrlField != null) youtubeUrlField.requestFocus();
            });
            youtubeFeedback = label("", 14, SettingsUi.muted(MainActivity.this), false);
            youtubeFeedback.setPadding(0, dp(4), 0, dp(8));
            form.addView(youtubeFeedback);
            TextView savedTitle = label("Saved shortcuts", 18, SettingsUi.text(MainActivity.this), true);
            savedTitle.setLetterSpacing(.12f);
            LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, dp(36));
            titleParams.topMargin = dp(10);
            form.addView(savedTitle, titleParams);
            youtubeSavedList = new LinearLayout(MainActivity.this);
            youtubeSavedList.setOrientation(LinearLayout.VERTICAL);
            youtubeSavedList.setClipChildren(false);
            youtubeSavedList.setClipToPadding(false);
            form.addView(youtubeSavedList);
            renderYouTubeSavedList();
        }

        private void renderYouTubeSavedList() {
            if (youtubeSavedList == null) return;
            youtubeSavedList.removeAllViews();
            List<YouTubeCard> cards = savedYouTubeCards();
            if (cards.isEmpty()) {
                youtubeSavedList.addView(label("No YouTube cards saved yet.", 14,
                        SettingsUi.muted(MainActivity.this), false));
                return;
            }
            for (YouTubeCard card : cards) {
                LinearLayout row = new LinearLayout(MainActivity.this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(12), dp(10), dp(12), dp(10));
                row.setOrientation(LinearLayout.VERTICAL);
                row.setBackground(roundedShape(LiquidTheme.surface(MainActivity.this),
                        LiquidTheme.border(MainActivity.this), 1, 13));
                LinearLayout copy = new LinearLayout(MainActivity.this);
                copy.setOrientation(LinearLayout.VERTICAL);
                TextView title = label(card.title, 15, SettingsUi.text(MainActivity.this), true);
                title.setSingleLine(true);
                title.setEllipsize(android.text.TextUtils.TruncateAt.END);
                copy.addView(title);
                TextView detail = label((YouTubeCard.VIDEO.equals(card.type) ? "VIDEO" : "PLAYLIST")
                        + ((card.thumbnailUrl == null || card.thumbnailUrl.isEmpty()) ? "  ·  Add art" : "")
                        + "  ·  " + card.url, 11, SettingsUi.muted(MainActivity.this), false);
                detail.setSingleLine(true);
                detail.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
                copy.addView(detail);
                row.addView(copy, new LinearLayout.LayoutParams(-1, -2));
                LinearLayout cardActions = new LinearLayout(MainActivity.this);
                cardActions.setClipChildren(false);
                cardActions.setClipToPadding(false);
                row.setClipChildren(false);
                row.setClipToPadding(false);
                LinearLayout.LayoutParams cardActionParams = new LinearLayout.LayoutParams(-1, -2);
                cardActionParams.topMargin = dp(10);
                row.addView(cardActions, cardActionParams);
                Button remove = settingsButton("Remove");
                remove.setContentDescription("Remove " + card.title + " from Saved");
                Button editTitle = settingsButton("Title");
                editTitle.setContentDescription("Edit title for " + card.title);
                Button editArt = settingsButton("Art");
                editArt.setContentDescription("Edit artwork for " + card.title);
                LinearLayout.LayoutParams editParams = new LinearLayout.LayoutParams(0, dp(46), 1);
                editParams.setMargins(dp(2), dp(2), dp(3), dp(2));
                cardActions.addView(editTitle, editParams);
                LinearLayout.LayoutParams artParams = new LinearLayout.LayoutParams(0, dp(46), 1);
                artParams.setMargins(dp(3), dp(2), dp(3), dp(2));
                cardActions.addView(editArt, artParams);
                LinearLayout.LayoutParams removeParams = new LinearLayout.LayoutParams(0, dp(46), 1);
                removeParams.setMargins(dp(3), dp(2), dp(2), dp(2));
                cardActions.addView(remove, removeParams);
                editTitle.setOnClickListener(view -> editYouTubeTitle(card));
                editArt.setOnClickListener(view -> editYouTubeArtwork(card));
                remove.setOnClickListener(view -> {
                    List<YouTubeCard> latest = savedYouTubeCards();
                    ArrayList<YouTubeCard> retained = new ArrayList<>();
                    for (YouTubeCard saved : latest) if (!saved.url.equals(card.url)) retained.add(saved);
                    saveYouTubeCards(retained);
                    youtubeChanged = true;
                    if (youtubeFeedback != null) youtubeFeedback.setText("Removed from Saved.");
                    renderYouTubeSavedList();
                });
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
                rowParams.setMargins(dp(6), dp(2), dp(6), dp(8));
                youtubeSavedList.addView(row, rowParams);
            }
        }

        private void editYouTubeArtwork(YouTubeCard selected) {
            EditText artworkInput = new EditText(MainActivity.this);
            artworkInput.setSingleLine(true);
            artworkInput.setHint("YouTube video link");
            artworkInput.setTextColor(SettingsUi.text(MainActivity.this));
            artworkInput.setHintTextColor(SettingsUi.muted(MainActivity.this));
            artworkInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
            artworkInput.setPadding(dp(24), dp(12), dp(24), dp(12));
            artworkInput.setBackground(roundedShape(LiquidTheme.surface(MainActivity.this),
                    LiquidTheme.border(MainActivity.this), 1, 12));
            AlertDialog artworkDialog = new AlertDialog.Builder(new android.view.ContextThemeWrapper(MainActivity.this,
                    LiquidTheme.alertDialogTheme(MainActivity.this)))
                    .setTitle("Card artwork")
                    .setMessage("Paste a YouTube video link to use its thumbnail. Leave empty to retry automatic artwork.")
                    .setView(artworkInput)
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Save", null)
                    .create();
            artworkDialog.setOnShowListener(ignored -> artworkDialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    .setOnClickListener(view -> {
                        String videoLink = artworkInput.getText().toString().trim();
                        YouTubeCard image = videoLink.isEmpty() ? null : YouTubeCard.parse(videoLink);
                        if (!videoLink.isEmpty() && (image == null || !YouTubeCard.VIDEO.equals(image.type))) {
                            artworkInput.setError("Enter a YouTube video link");
                            return;
                        }
                        List<YouTubeCard> cards = savedYouTubeCards();
                        for (YouTubeCard card : cards) {
                            if (!card.url.equals(selected.url)) continue;
                            card.customThumbnail = image != null;
                            card.thumbnailUrl = image == null
                                    ? (YouTubeCard.VIDEO.equals(card.type)
                                    ? "https://i.ytimg.com/vi/" + card.videoId + "/hqdefault.jpg" : null)
                                    : image.thumbnailUrl;
                            card.metadataCheckedAt = 0L;
                            break;
                        }
                        saveYouTubeCards(cards);
                        youtubeChanged = true;
                        renderYouTubeSavedList();
                        renderYouTubeCards();
                        artworkDialog.dismiss();
                    }));
            Motion.dialog(artworkDialog); artworkDialog.show();
            styleDialog(artworkDialog);
        }

        private void editYouTubeTitle(YouTubeCard selected) {
            EditText titleInput = new EditText(MainActivity.this);
            titleInput.setSingleLine(true);
            titleInput.setHint("Use the YouTube title");
            titleInput.setText(selected.customTitle ? selected.title : "");
            titleInput.setTextColor(SettingsUi.text(MainActivity.this));
            titleInput.setHintTextColor(SettingsUi.muted(MainActivity.this));
            titleInput.setSelectAllOnFocus(true);
            titleInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            int inset = dp(24);
            titleInput.setPadding(inset, dp(12), inset, dp(12));
            titleInput.setBackground(roundedShape(
                    LiquidTheme.surface(MainActivity.this),
                    LiquidTheme.border(MainActivity.this), 1, 12));
            AlertDialog prompt = new AlertDialog.Builder(new android.view.ContextThemeWrapper(MainActivity.this,
                    LiquidTheme.alertDialogTheme(MainActivity.this)))
                    .setTitle("Card title")
                    .setMessage("Clear the title to use the title provided by YouTube when available.")
                    .setView(titleInput)
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Save", (dialog, which) -> {
                        String custom = titleInput.getText().toString().trim();
                        List<YouTubeCard> latest = savedYouTubeCards();
                        for (YouTubeCard card : latest) {
                            if (!card.url.equals(selected.url)) continue;
                            card.customTitle = !custom.isEmpty();
                            if (!custom.isEmpty()) card.title = custom;
                            else card.title = card.autoTitle;
                            break;
                        }
                        saveYouTubeCards(latest);
                        youtubeChanged = true;
                        renderYouTubeSavedList();
                    }).create();
            Motion.dialog(prompt); prompt.show();
            styleDialog(prompt);
        }

        private void renderCalendarSettings(LinearLayout page) {
            LinearLayout form = scrollForm(page);
            addFormSectionHeader(form, "Calendar feed");
            addFormHint(form, "Add a public Google Calendar iCal address (Google Calendar → Settings → Integrate calendar → Public address in iCal format). Events appear in the Events tab.");
            calendarUrlField = addInput(form, "Public calendar iCal URL", "https://calendar.google.com/calendar/ical/…/public/basic.ics", draftCalendarUrl, false);
            calendarSummary = label(calendarSummaryText(), 14, SettingsUi.muted(MainActivity.this), false);
            calendarSummary.setPadding(dp(14), dp(8), dp(14), dp(8));
            calendarSummary.setBackground(roundedShape(SettingsUi.surface(MainActivity.this), LiquidTheme.border(MainActivity.this), 1, 10));
            LinearLayout.LayoutParams csParams = new LinearLayout.LayoutParams(-1, -2);
            csParams.setMargins(0, dp(4), 0, dp(6));
            form.addView(calendarSummary, csParams);
            addFormSectionHeader(form, "Actions");
            addFormButton(form, "Save calendar and refresh", view -> {
                String entered = calendarUrlField == null ? draftCalendarUrl : calendarUrlField.getText().toString().trim();
                if (entered.isEmpty()) {
                    preferences.edit().remove("publicCalendarUrl").remove("publicCalendarEvents")
                            .remove("publicCalendarUpdatedAt").remove("publicCalendarAttemptedAt")
                            .remove("publicCalendarCacheVersion").remove("publicCalendarAttemptedVersion").apply();
                    draftCalendarUrl = "";
                    if (calendarUrlField != null) calendarUrlField.setText("");
                    if (calendarFeedback != null) calendarFeedback.setText("Calendar removed.");
                    updateCalendarEventDisplay();
                    if (calendarSummary != null) calendarSummary.setText(calendarSummaryText());
                    return;
                }
                String normalized = PublicCalendarRepository.normalizeUrl(entered);
                if (normalized == null) {
                    if (calendarFeedback != null) calendarFeedback.setText("Use a public Google Calendar iCal URL from Integrate calendar.");
                    return;
                }
                String previous = preferences.getString("publicCalendarUrl", "");
                if (!normalized.equals(previous)) {
                    preferences.edit().putString("publicCalendarUrl", normalized)
                            .putString("publicCalendarEvents", "[]").remove("publicCalendarUpdatedAt")
                            .remove("publicCalendarAttemptedAt").remove("publicCalendarCacheVersion")
                            .remove("publicCalendarAttemptedVersion").apply();
                    updateCalendarEventDisplay();
                }
                draftCalendarUrl = normalized;
                if (calendarUrlField != null) calendarUrlField.setText(normalized);
                if (calendarFeedback != null) calendarFeedback.setText("Refreshing public calendar…");
                if (calendarSummary != null) calendarSummary.setText(calendarSummaryText());
                refreshPublicCalendar(normalized, true);
            });
            addFormButton(form, "Refresh saved calendar now", view -> {
                String entered = calendarUrlField == null ? draftCalendarUrl : calendarUrlField.getText().toString().trim();
                if (!entered.equals(preferences.getString("publicCalendarUrl", ""))) {
                    if (calendarFeedback != null) calendarFeedback.setText("Save the calendar URL before refreshing.");
                } else if (entered.isEmpty()) {
                    if (calendarFeedback != null) calendarFeedback.setText("Add a public calendar URL first.");
                } else {
                    if (calendarFeedback != null) calendarFeedback.setText("Refreshing public calendar…");
                    refreshPublicCalendar(entered, true);
                }
            });
            calendarFeedback = label("", 14, SettingsUi.muted(MainActivity.this), false);
            calendarFeedback.setPadding(dp(2), dp(4), dp(2), dp(4));
            form.addView(calendarFeedback);
            addFormHint(form, "The on-device schedule is checked when Sva opens. Change its automatic refresh cadence under Schedules. Events appear while active.");
        }

        private String calendarSummaryText() {
            long updatedAt = preferences.getLong("publicCalendarUpdatedAt", 0L);
            JSONArray events;
            try { events = new JSONArray(preferences.getString("publicCalendarEvents", "[]")); }
            catch (Exception ignored) { events = new JSONArray(); }
            String updated = updatedAt == 0L ? "Not refreshed yet"
                    : "Updated " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                    .format(new Date(updatedAt));
            return "Public calendar cache · " + events.length() + " events · " + updated;
        }

        void onCalendarRefreshResult(String message) {
            if (settingsScreen != this) return;
            if (calendarFeedback != null) calendarFeedback.setText(message);
            if (calendarSummary != null) calendarSummary.setText(calendarSummaryText());
        }

        void refreshLauncherOverride() {
            if (settingsScreen == this && section == HOME) renderSection();
        }

        private LinearLayout scrollForm(LinearLayout page) {
            ScrollView scroll = new ScrollView(MainActivity.this);
            scroll.setFillViewport(false);
            scroll.setPadding(dp(8), dp(4), dp(8), dp(6));
            scroll.setClipToPadding(false);
            scroll.setSmoothScrollingEnabled(true);
            scroll.setVerticalScrollBarEnabled(false);
            scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
            page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
            LinearLayout form = new LinearLayout(MainActivity.this);
            form.setOrientation(LinearLayout.VERTICAL);
            form.setClipChildren(false);
            form.setClipToPadding(false);
            form.setPadding(0, 0, 0, dp(8));
            scroll.addView(form);
            return form;
        }

        private EditText addInput(LinearLayout form, String labelText, String hintText, String value, boolean multiline) {
            TextView title = label(labelText, 14, SettingsUi.muted(MainActivity.this), false);

            title.setMinHeight(dp(24));
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(-1, -2);
            labelParams.setMargins(dp(10), dp(10), dp(10), 0);
            form.addView(title, labelParams);
            EditText field = new EditText(MainActivity.this);
            field.setText(value);
            field.setHint(hintText);
            field.setTextColor(SettingsUi.text(MainActivity.this));
            field.setHintTextColor(SettingsUi.muted(MainActivity.this));
            field.setTextSize(14);
            field.setTypeface(LiquidTheme.font(MainActivity.this));
            field.setSelectAllOnFocus(!multiline);
            field.setInputType(multiline
                    ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
            if (!multiline) {
                field.setSingleLine(true);
                field.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
                field.setOnEditorActionListener((view, actionId, event) -> {
                    if (actionId != android.view.inputmethod.EditorInfo.IME_ACTION_DONE) return false;
                    android.view.inputmethod.InputMethodManager keyboard =
                            (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                    if (keyboard != null) keyboard.hideSoftInputFromWindow(field.getWindowToken(), 0);
                    field.clearFocus();
                    return true;
                });
            }
            field.setGravity(multiline ? Gravity.TOP | Gravity.START : Gravity.CENTER_VERTICAL | Gravity.START);
            field.setPadding(dp(16), dp(10), dp(16), dp(10));
            SettingsUi.styleInput(field, multiline);
            LinearLayout.LayoutParams fieldParams = new LinearLayout.LayoutParams(-1, -2);
            fieldParams.setMargins(dp(8), dp(4), dp(8), dp(12));
            form.addView(field, fieldParams);
            if (firstControl == null) firstControl = field;
            return field;
        }

        private Button addFormButton(LinearLayout form, String text, View.OnClickListener listener) {
            Button button = settingsButton(text);
            button.setOnClickListener(listener);
            button.setMinHeight(dp(50));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.setMargins(dp(8), dp(6), dp(8), dp(6));
            form.addView(button, params);
            if (firstControl == null) firstControl = button;
            return button;
        }

        private void addFormSectionHeader(LinearLayout form, String label) {
            SettingsUi.heading(form, label);
        }

        private Button settingsButton(String text) {
            Button button = SettingsUi.action(MainActivity.this, text, null);
            if (text.equals("↑") || text.equals("↓")) {
                button.setPadding(dp(4), 0, dp(4), 0);
                button.setGravity(Gravity.CENTER);
                button.setContentDescription(text.equals("↑") ? "Move up" : "Move down");
            }
            return button;
        }

        private void addFormHint(LinearLayout form, String value) {
            SettingsUi.hint(form, value);
        }

        private void addToggle(LinearLayout form, String title, boolean checked, java.util.function.Consumer<Boolean> save) {
            View row = SettingsUi.toggle(MainActivity.this, title, checked, save);
            SettingsUi.add(form, row);
            if (firstControl == null) firstControl = row;
        }

        private void addChoice(LinearLayout form, String title, String[] options, int selected, java.util.function.IntConsumer save) {
            View row = SettingsUi.choice(MainActivity.this, title, options, selected, save);
            SettingsUi.add(form, row);
            if (firstControl == null) firstControl = row;
        }

        private void addSaveActions(LinearLayout page, View.OnClickListener save,
                                    View.OnClickListener discard, View.OnClickListener refresh) {
            LinearLayout actions = new LinearLayout(MainActivity.this);
            actions.setClipChildren(false);
            actions.setClipToPadding(false);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
            actions.setGravity(Gravity.CENTER_VERTICAL);
            rowParams.setMargins(dp(8), dp(10), dp(8), dp(4));
            page.addView(actions, rowParams);
            Button saveButton = settingsButton("Save changes");
            saveButton.setTextColor(LiquidTheme.accent(MainActivity.this));
            saveButton.setMinHeight(dp(50));
            saveButton.setOnClickListener(save);
            LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(0, -2, 1);
            saveParams.setMargins(dp(2), dp(3), dp(4), dp(3));
            actions.addView(saveButton, saveParams);
            Button discardButton = settingsButton("Discard edits");
            discardButton.setOnClickListener(discard);
            discardButton.setMinHeight(dp(50));
            LinearLayout.LayoutParams discardParams = new LinearLayout.LayoutParams(0, -2, 1);
            discardParams.setMargins(dp(6), dp(3), dp(4), dp(3));
            actions.addView(discardButton, discardParams);
            if (refresh != null) {
                Button refreshButton = settingsButton("Refresh channels");
                refreshButton.setOnClickListener(refresh);
                refreshButton.setMinHeight(dp(50));
                LinearLayout.LayoutParams refreshParams = new LinearLayout.LayoutParams(0, -2, 1);
                refreshParams.setMargins(dp(6), dp(3), dp(2), dp(3));
                actions.addView(refreshButton, refreshParams);
            }
        }

        private void renderLive(LinearLayout page) {
            LinearLayout form = scrollForm(page);
            addFormSectionHeader(form, "Playlist & Guide URLs");
            playlistField = addInput(form, "M3U playlists · one URL per line", "https://example.com/playlist.m3u", draftPlaylist, true);
            guideField = addInput(form, "Programme guides · one URL per line", "https://www.epgschedule.com/data/index-lite.json", draftGuide, true);
            addFormHint(form, "Channels from all playlists are combined. Guide schedules from all sources are merged. Duplicate source URLs are ignored.");
            addFormHint(form, "XMLTV matches by tvg-id. JSON guides match channel IDs or a unique channel name. EPGSchedule times use India Standard Time.");
            addFormSectionHeader(form, "Cache & Guide status");
            cacheSummary = label(cacheSummaryText(), 14, SettingsUi.muted(MainActivity.this), false);
            cacheSummary.setPadding(dp(14), dp(8), dp(14), dp(8));
            cacheSummary.setBackground(roundedShape(SettingsUi.surface(MainActivity.this), LiquidTheme.border(MainActivity.this), 1, 10));
            LinearLayout.LayoutParams csParams = new LinearLayout.LayoutParams(-1, -2);
            csParams.setMargins(0, dp(4), 0, dp(4));
            form.addView(cacheSummary, csParams);

            guideSummary = label(guideSummaryText(), 14, SettingsUi.muted(MainActivity.this), false);
            guideSummary.setPadding(dp(14), dp(8), dp(14), dp(8));
            guideSummary.setBackground(roundedShape(SettingsUi.surface(MainActivity.this), LiquidTheme.border(MainActivity.this), 1, 10));
            LinearLayout.LayoutParams gsParams = new LinearLayout.LayoutParams(-1, -2);
            gsParams.setMargins(0, dp(4), 0, dp(6));
            form.addView(guideSummary, gsParams);

            addFormButton(form, "Refresh programme guide now", v -> {
                String enteredGuide = guideField == null ? draftGuide : guideField.getText().toString().trim();
                if (enteredGuide.isEmpty() || !enteredGuide.equals(iptv.guideUrl())) {
                    if (liveFeedback != null) liveFeedback.setText("Save the programme guide URL before refreshing it.");
                    return;
                }
                refreshGuide(true);
            });
            addFormHint(form, "Playlist and programme-guide refresh cadences are configured independently under Schedules. You can also refresh them manually here.");
            addSaveActions(page, v -> {
                captureDrafts();
                String playlist = draftPlaylist.trim(), guide = draftGuide.trim();
                boolean changed = !playlist.equals(iptv.playlistUrl()) || !guide.equals(iptv.guideUrl());
                iptv.saveUrls(playlist, guide);
                draftPlaylist = playlist; draftGuide = guide;
                if (changed) {
                    channels.clear();
                    channels.addAll(iptv.cachedChannels());
                    renderChannels();
                }
                renderSection();
                if (changed) maybeRefreshLiveData();
                if (liveFeedback != null) liveFeedback.setText(changed
                        ? "Settings saved. Automatic refresh uses the cadences under Schedules."
                        : "No changes to save.");
            }, v -> {
                draftPlaylist = iptv.playlistUrl(); draftGuide = iptv.guideUrl(); renderSection();
            }, v -> {
                String enteredPlaylist = playlistField == null
                        ? draftPlaylist : playlistField.getText().toString().trim();
                if (enteredPlaylist.isEmpty() || !enteredPlaylist.equals(iptv.playlistUrl())) {
                    if (liveFeedback != null) liveFeedback.setText("Save the playlist URL before refreshing channels.");
                    return;
                }
                refreshChannels();
            });
            liveFeedback = label(status.getText().toString(), 14, SettingsUi.muted(MainActivity.this), false);
            liveFeedback.setPadding(dp(2), dp(4), dp(2), dp(2));
            page.addView(liveFeedback);
        }

        private void renderDrm(LinearLayout page) {
            LinearLayout form = scrollForm(page);
            addFormSectionHeader(form, "Widevine DRM");
            licenseField = addInput(form, "Widevine license URL", "Optional default license endpoint", draftLicense, false);
            headersField = addInput(form, "License request headers", "One per line · Name: Value", draftHeaders, true);
            addFormHint(form, "Channel-specific KODIPROP settings in the playlist take priority over these defaults.");
            addSaveActions(page, v -> {
                captureDrafts();
                iptv.saveDrmSettings(draftLicense, draftHeaders);
                draftLicense = iptv.drmLicenseUrl(); draftHeaders = iptv.drmHeaderText();
                renderSection();
                Toast.makeText(MainActivity.this, "Playback settings saved", Toast.LENGTH_SHORT).show();
            }, v -> {
                draftLicense = iptv.drmLicenseUrl(); draftHeaders = iptv.drmHeaderText(); renderSection();
            }, null);
        }

        private void renderClock(LinearLayout page) {
            LinearLayout form = scrollForm(page);
            addFormSectionHeader(form, "Clock display");
            addFormHint(form, "Changes appear on the Home clock immediately.");
            addToggle(form, "24-hour time", preferences.getBoolean("24h", false), enabled -> {
                preferences.edit().putBoolean("24h", enabled).apply(); updateClock();
            });
            addToggle(form, "Show date", preferences.getBoolean("showDate", true), enabled -> {
                preferences.edit().putBoolean("showDate", enabled).apply(); updateClock();
            });
        }

        private void renderAppearance(LinearLayout page) {
            LinearLayout form = scrollForm(page);
            // — Theme ——————————————————————————————————————————————————————————
            addFormSectionHeader(form, "Theme");
            addFormHint(form, "Follow your TV’s theme, or choose a light or dark appearance.");
            String mode = preferences.getString(LiquidTheme.PREF_MODE, LiquidTheme.DEFAULT_MODE);
            String[] modes = {LiquidTheme.SYSTEM, LiquidTheme.LIGHT, LiquidTheme.DARK};
            addChoice(form, "Appearance", new String[]{"System default", "Light", "Dark"},
                    LiquidTheme.LIGHT.equals(mode) ? 1 : LiquidTheme.DARK.equals(mode) ? 2 : 0,
                    index -> changeLiquidGlassMode(modes[index]));

            // — Now Playing ————————————————————————————————————————————————————
            addFormSectionHeader(form, "Now Playing");
            addFormHint(form, "Appears on Home only while media is playing. Allow notification access to show playback from other apps.");
            addFormButton(form, "Manage Now Playing access", v -> openMusicAccessSettings());
            // — Screensaver ————————————————————————————————————————————————————
            addFormSectionHeader(form, "Screensaver");
            addFormButton(form, "Sva Memories · screensaver settings", v ->
                    startActivity(new Intent(MainActivity.this, ScreensaverSettingsActivity.class)));
            // — Slideshow ——————————————————————————————————————————————————————
            addFormSectionHeader(form, "Slideshow");
            addFormHint(form, "A photo folder or Google Photos album plays as a slideshow. It takes priority over a single wallpaper.");
            boolean hasFolder = !preferences.getString("wallpaperFolder", "").isEmpty();
            addFormHint(form, hasFolder
                    ? "Photo folder · " + backgroundPhotos.size() + " images"
                    : "No photo folder selected");
            addFormButton(form, "Choose photo folder", v -> openWallpaperFolderPicker());
            if (hasFolder) addFormButton(form, "Clear photo folder", v -> {
                backgroundFolderGeneration++;
                preferences.edit().remove("wallpaperFolder")
                        .remove(PHOTO_FOLDER_CACHE_URI)
                        .remove(PHOTO_FOLDER_CACHE_IMAGES).apply();
                String album = preferences.getString("wallpaperAlbum", "");
                if (!album.isEmpty()) loadBackgroundAlbum(album, false);
                else setBackgroundPhotos(Collections.emptyList());
                renderSection();
            });
            String albumUrl = preferences.getString("wallpaperAlbum", "");
            addFormHint(form, albumUrl.isEmpty()
                    ? "No Google Photos album selected"
                    : "Google Photos album · " + backgroundPhotos.size() + " images");
            EditText albumField = addInput(form, "Google Photos public album link", "Paste a link shared with anyone", albumUrl, false);
            addFormButton(form, "Use Google Photos album", v -> {
                String link = albumField.getText().toString().trim();
                if (link.isEmpty()) {
                    Toast.makeText(MainActivity.this, "Paste a Google Photos shared album link first.", Toast.LENGTH_SHORT).show();
                    return;
                }
                Toast.makeText(MainActivity.this, "Loading public album…", Toast.LENGTH_SHORT).show();
                loadBackgroundAlbum(link, true);
            });
            if (!albumUrl.isEmpty()) addFormButton(form, "Clear Google Photos album", v -> {
                backgroundFolderGeneration++;
                preferences.edit().remove("wallpaperAlbum")
                        .remove(GOOGLE_PHOTOS_CACHE_URL)
                        .remove(GOOGLE_PHOTOS_CACHE_IMAGES)
                        .remove("googlePhotosUpdatedAt")
                        .remove("googlePhotosAttemptedAt")
                        .apply();
                String folder = preferences.getString("wallpaperFolder", "");
                if (!folder.isEmpty()) loadBackgroundFolder(Uri.parse(folder), false);
                else setBackgroundPhotos(Collections.emptyList());
                renderSection();
            });
            // — Single wallpaper ———————————————————————————————————————————————
            addFormSectionHeader(form, "Single wallpaper");
            boolean hasWallpaper = preferences.contains("wallpaper");
            addFormHint(form, hasWallpaper ? "Single wallpaper is set" : "No single wallpaper — using the default landscape");
            addFormButton(form, "Choose single wallpaper", v ->
                    startActivityForResult(TvFilePickerActivity.intent(MainActivity.this, TvFilePickerActivity.MODE_IMAGE), PICK_WALLPAPER));
            if (hasWallpaper) addFormButton(form, "Clear single wallpaper", v -> {
                preferences.edit().remove("wallpaper").apply();
                applyWallpaper();
                renderSection();
            });
        }

        private void changeLiquidGlassMode(String mode) {
            if (mode.equals(preferences.getString(LiquidTheme.PREF_MODE, LiquidTheme.DEFAULT_MODE))) return;
            preferences.edit().putString(LiquidTheme.PREF_MODE, mode).apply();
            recreate();
        }

        void refreshAppearance() {
            if (settingsScreen == this && section == APPEARANCE) renderSection();
        }

        void onRefreshResult(String message) {
            if (settingsScreen == this && liveFeedback != null) liveFeedback.setText(message);
            if (settingsScreen == this && cacheSummary != null) cacheSummary.setText(cacheSummaryText());
            if (settingsScreen == this && guideSummary != null) guideSummary.setText(guideSummaryText());
            if (settingsScreen == this && section == CHANNELS && channelAdapter != null) channelAdapter.refresh();
        }

        private String cacheSummaryText() {
            long updatedAt = iptv.lastUpdatedAt();
            String updated = updatedAt == 0 ? "Not refreshed yet"
                    : "Updated " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                    .format(new Date(updatedAt));
            return "On-device channel cache · " + channels.size()
                    + (channels.size() == 1 ? " channel" : " channels") + " · " + updated;
        }

        private String guideSummaryText() {
            long updatedAt = iptv.lastGuideUpdatedAt();
            String updated = updatedAt == 0 ? "Not refreshed yet"
                    : "Last refreshed " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                    .format(new Date(updatedAt));
            return "Programme guide · daily refresh · " + updated;
        }

        private void renderChannelSettings(LinearLayout page) {
            EditText search = addInput(page, "Find a channel", "Search by channel name", "", false);
            search.setInputType(InputType.TYPE_CLASS_TEXT);
            TextView count = label("", 14, SettingsUi.muted(MainActivity.this), false);
            LinearLayout.LayoutParams countParams = new LinearLayout.LayoutParams(-1, -2);
            countParams.bottomMargin = dp(8);
            page.addView(count, countParams);
            RecyclerView list = new RecyclerView(MainActivity.this);
            list.setLayoutManager(new LinearLayoutManager(MainActivity.this));
            list.setClipChildren(true);
            list.setClipToPadding(false);
            list.setPadding(dp(4), dp(4), dp(4), dp(4));
            list.setItemViewCacheSize(6);
            list.setOverScrollMode(View.OVER_SCROLL_NEVER);
            channelAdapter = new ChannelSettingsAdapter(count);
            list.setAdapter(channelAdapter);
            page.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
            search.addTextChangedListener(new android.text.TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) { channelAdapter.setQuery(s.toString()); }
                @Override public void afterTextChanged(android.text.Editable s) { }
            });
            channelAdapter.setQuery("");
        }

        private void renderAppSettings(LinearLayout page) {
            addFormHint(page, "Choose up to five apps for Home. Use arrows to change their order.");
            appList = new RecyclerView(MainActivity.this);
            appList.setLayoutManager(new LinearLayoutManager(MainActivity.this));
            appList.setClipChildren(true);
            appList.setClipToPadding(false);
            appList.setPadding(dp(4), dp(4), dp(4), dp(4));
            appList.setItemViewCacheSize(6);
            appList.setOverScrollMode(View.OVER_SCROLL_NEVER);
            appAdapter = new AppSettingsAdapter(appList);
            appList.setAdapter(appAdapter);
            page.addView(appList, new LinearLayout.LayoutParams(-1, 0, 1));
            if (availableApps != null) appAdapter.setApps(availableApps);
            else if (!loadingApps) {
                loadingApps = true;
                lifecycleWork.execute(ioWorker, () -> {
                    List<AppEntry> loaded = installedApps();
                    lifecycleWork.post(() -> {
                        if (settingsScreen != this) return;
                        availableApps = loaded;
                        loadingApps = false;
                        if (section == APPS && appAdapter != null) appAdapter.setApps(loaded);
                    });
                });
            }
            firstControl = appList;
        }

        private final class ChannelSettingsAdapter extends RecyclerView.Adapter<ChannelSettingsAdapter.Holder> {
            private final List<IptvRepository.Channel> visible = new ArrayList<>();
            private final TextView count;
            private String query = "";

            ChannelSettingsAdapter(TextView count) { this.count = count; }

            void setQuery(String value) {
                query = value == null ? "" : value.trim().toLowerCase(Locale.getDefault());
                refresh();
            }

            void refresh() {
                visible.clear();
                for (IptvRepository.Channel channel : channels) {
                    if (query.isEmpty() || channel.name.toLowerCase(Locale.getDefault()).contains(query)) visible.add(channel);
                }
                updateCountLabel();
                notifyDataSetChanged();
            }

            private int shownChannelCount() {
                int shown = 0;
                for (IptvRepository.Channel channel : channels) {
                    if (!HiddenChannels.isHidden(hiddenChannelKeys, channel)) shown++;
                }
                return shown;
            }

            private int favoriteChannelCount() {
                int count = 0;
                for (IptvRepository.Channel channel : channels) {
                    if (favoriteChannelKeys.contains(channel.stableKey())) count++;
                }
                return count;
            }

            private void updateCountLabel() {
                count.setText(visible.size() + " of " + channels.size() + " channels · "
                        + shownChannelCount() + " shown · " + favoriteChannelCount() + " favourites\nPress OK to toggle visibility · Long-press to toggle favourite");
            }

            @Override public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
                LinearLayout row = new LinearLayout(MainActivity.this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(18), dp(4), dp(18), dp(4));
                row.setFocusable(true); row.setClickable(true);
                row.setBackground(SettingsUi.states(MainActivity.this, false));
                Motion.interaction(row, true);
                RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(-1, dp(70));
                params.setMargins(dp(6), dp(1), dp(6), dp(7));
                row.setLayoutParams(params);
                LinearLayout text = new LinearLayout(MainActivity.this);
                text.setOrientation(LinearLayout.VERTICAL);
                row.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
                TextView name = label("", 16, SettingsUi.text(MainActivity.this), true);
                name.setSingleLine(true);
                name.setEllipsize(android.text.TextUtils.TruncateAt.END);
                text.addView(name);
                TextView group = label("", 14, SettingsUi.muted(MainActivity.this), false);
                group.setSingleLine(true);
                text.addView(group);
                TextView state = label("", 14, SettingsUi.text(MainActivity.this), true);
                row.addView(state);
                return new Holder(row, name, group, state);
            }

            @Override public void onBindViewHolder(Holder holder, int position) {
                IptvRepository.Channel channel = visible.get(position);
                boolean isFav = favoriteChannelKeys.contains(channel.stableKey());
                holder.name.setText((isFav ? "★  " : "") + channel.name);
                holder.group.setText(channel.group.isEmpty() ? "Uncategorized" : channel.group);
                boolean shown = !HiddenChannels.isHidden(hiddenChannelKeys, channel);
                holder.state.setText((isFav ? "★ " : "") + (shown ? "Shown" : "Hidden"));
                holder.state.setTextColor(shown ? SettingsUi.text(MainActivity.this)
                        : SettingsUi.muted(MainActivity.this));
                holder.itemView.setOnClickListener(v -> {
                    HiddenChannels.toggle(hiddenChannelKeys, channel);
                    HiddenChannels.save(preferences, hiddenChannelKeys);
                    channelsChanged = true;
                    int current = holder.getBindingAdapterPosition();
                    if (current != RecyclerView.NO_POSITION) notifyItemChanged(current);
                    updateCountLabel();
                });
                holder.itemView.setOnLongClickListener(v -> {
                    boolean added = FavoriteChannels.toggle(favoriteChannelKeys, channel);
                    FavoriteChannels.save(preferences, favoriteChannelKeys);
                    channelsChanged = true;
                    int current = holder.getBindingAdapterPosition();
                    if (current != RecyclerView.NO_POSITION) notifyItemChanged(current);
                    updateCountLabel();
                    Toast.makeText(MainActivity.this, (added ? "Added to" : "Removed from") + " favourites: " + channel.name, Toast.LENGTH_SHORT).show();
                    return true;
                });
            }

            @Override public int getItemCount() { return visible.size(); }

            final class Holder extends RecyclerView.ViewHolder {
                final TextView name, group, state;
                Holder(View row, TextView name, TextView group, TextView state) {
                    super(row);
                    this.name = name; this.group = group; this.state = state;
                }
            }
        }

        private final class AppSettingsAdapter extends RecyclerView.Adapter<AppSettingsAdapter.Holder> {
            private final RecyclerView list;
            private final List<AppEntry> entries = new ArrayList<>();
            private final List<AppEntry> rows = new ArrayList<>();
            private final List<String> selectedOrder = new ArrayList<>();
            private boolean initialized;

            AppSettingsAdapter(RecyclerView list) { this.list = list; }

            void setApps(List<AppEntry> apps) {
                entries.clear(); entries.addAll(apps);
                if (!initialized) {
                    selectedOrder.addAll(selectedPackages(apps));
                    if (selectedOrder.isEmpty() && !preferences.getBoolean("customApps", false)) {
                        for (AppEntry entry : apps) selectedOrder.add(entry.packageName);
                    }
                    initialized = true;
                }
                rebuild();
            }

            private void rebuild() {
                rows.clear();
                for (String pkg : selectedOrder) {
                    AppEntry entry = findApp(entries, pkg);
                    if (entry != null) rows.add(entry);
                }
                for (AppEntry entry : entries) if (!selectedOrder.contains(entry.packageName)) rows.add(entry);
                notifyDataSetChanged();
            }

            private void persistAndFocus(String pkg, int control) {
                preferences.edit().putBoolean("customApps", true)
                        .putString("selectedApps", String.join("\n", selectedOrder)).apply();
                appsChanged = true;
                rebuild();
                int index = -1;
                for (int i = 0; i < rows.size(); i++) if (rows.get(i).packageName.equals(pkg)) { index = i; break; }
                if (index < 0) return;
                final int target = index;
                list.scrollToPosition(target);
                list.post(() -> {
                    RecyclerView.ViewHolder found = list.findViewHolderForAdapterPosition(target);
                    if (!(found instanceof Holder)) return;
                    Holder holder = (Holder) found;
                    View focus = control == 1 && holder.up.isEnabled() ? holder.up
                            : control == 2 && holder.down.isEnabled() ? holder.down : holder.toggle;
                    focus.requestFocus();
                });
            }

            @Override public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
                LinearLayout row = new LinearLayout(MainActivity.this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setClipChildren(false);
                row.setClipToPadding(false);
                RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(-1, dp(70));
                params.setMargins(dp(6), dp(1), dp(6), dp(7));
                row.setLayoutParams(params);
                Button toggle = settingsButton("");
                toggle.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
                toggle.setPadding(dp(18), 0, dp(12), 0);
                LinearLayout.LayoutParams toggleParams = new LinearLayout.LayoutParams(0, dp(60), 1);
                toggleParams.setMargins(dp(2), dp(2), dp(4), dp(2));
                row.addView(toggle, toggleParams);
                Button up = settingsButton("↑");
                LinearLayout.LayoutParams upParams = new LinearLayout.LayoutParams(dp(56), dp(60));
                upParams.setMargins(dp(4), dp(2), 0, dp(2));
                row.addView(up, upParams);
                Button down = settingsButton("↓");
                LinearLayout.LayoutParams downParams = new LinearLayout.LayoutParams(dp(56), dp(60));
                downParams.setMargins(dp(6), dp(2), dp(4), dp(2));
                row.addView(down, downParams);
                return new Holder(row, toggle, up, down);
            }

            @Override public void onBindViewHolder(Holder holder, int position) {
                AppEntry entry = rows.get(position);
                int selected = selectedOrder.indexOf(entry.packageName);
                holder.toggle.setText((selected >= 0 ? "✓  " : "+  ") + entry.label);
                holder.toggle.setTextColor(SettingsUi.text(MainActivity.this));
                holder.toggle.setOnClickListener(v -> {
                    if (selectedOrder.contains(entry.packageName)) selectedOrder.remove(entry.packageName);
                    else selectedOrder.add(entry.packageName);
                    persistAndFocus(entry.packageName, 0);
                });
                holder.up.setVisibility(selected >= 0 ? View.VISIBLE : View.GONE);
                holder.down.setVisibility(selected >= 0 ? View.VISIBLE : View.GONE);
                holder.up.setEnabled(selected > 0);
                holder.down.setEnabled(selected >= 0 && selected < selectedOrder.size() - 1);
                holder.up.setOnClickListener(v -> {
                    int current = selectedOrder.indexOf(entry.packageName);
                    if (current > 0) { Collections.swap(selectedOrder, current, current - 1); persistAndFocus(entry.packageName, 1); }
                });
                holder.down.setOnClickListener(v -> {
                    int current = selectedOrder.indexOf(entry.packageName);
                    if (current >= 0 && current < selectedOrder.size() - 1) {
                        Collections.swap(selectedOrder, current, current + 1);
                        persistAndFocus(entry.packageName, 2);
                    }
                });
            }

            @Override public int getItemCount() { return rows.size(); }

            final class Holder extends RecyclerView.ViewHolder {
                final Button toggle, up, down;
                Holder(View row, Button toggle, Button up, Button down) {
                    super(row);
                    this.toggle = toggle; this.up = up; this.down = down;
                }
            }
        }
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 7410) {
            Set<String> freshHidden = HiddenChannels.load(preferences);
            Set<String> freshFavorites = FavoriteChannels.load(preferences);
            if (!freshHidden.equals(hiddenChannelKeys) || !freshFavorites.equals(favoriteChannelKeys)) {
                hiddenChannelKeys.clear();
                hiddenChannelKeys.addAll(freshHidden);
                favoriteChannelKeys.clear();
                favoriteChannelKeys.addAll(freshFavorites);
                renderChannels();
            }
            if (guideToggle != null) guideToggle.post(() -> guideToggle.requestFocus());
            return;
        }
        if (resultCode == RESULT_OK && data != null && data.getData() != null) {
            if (requestCode == PICK_BACKUP_EXPORT) {
                exportBackup(data.getData());
                return;
            }
            if (requestCode == PICK_BACKUP_IMPORT) {
                importBackup(data.getData());
                return;
            }
        }
        if (requestCode == PICK_WALLPAPER_FOLDER && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri folderUri = data.getData();
            try { getContentResolver().takePersistableUriPermission(folderUri, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (Exception ignored) { }
            preferences.edit().putString("wallpaperFolder", folderUri.toString()).remove("wallpaperAlbum").apply();
            loadBackgroundFolder(folderUri, true);
            if (settingsScreen != null) settingsScreen.refreshAppearance();
            return;
        }
        if (requestCode != PICK_WALLPAPER || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (Exception ignored) { }
        preferences.edit().putString("wallpaper", uri.toString()).apply();
        applyWallpaper();
        if (settingsScreen != null) settingsScreen.refreshAppearance();
    }

    private void applyWallpaper() {
        if (wallpaperImage == null || heroFrame == null) return;
        String uriValue = preferences.getString("wallpaper", null);
        int targetWidth = Math.min(getResources().getDisplayMetrics().widthPixels, 1920);
        int targetHeight = Math.min(dp(heroHeightDp()), 1080);
        lifecycleWork.execute(ioWorker, () -> {
            android.graphics.Bitmap decoded = uriValue == null ? null : decodeWallpaper(Uri.parse(uriValue), targetWidth, targetHeight);
            if (decoded == null) {
                // No photo chosen: one of the calm built-in backgrounds.
                decoded = CalmWallpapers.render(MainActivity.this, preferences.getInt(CalmWallpapers.PREF, CalmWallpapers.DEFAULT),
                        Math.max(640, targetWidth), Math.max(360, targetHeight));
            }
            final android.graphics.Bitmap bitmap = decoded;
            lifecycleWork.post(() -> {
                if (isFinishing() || isDestroyed() || !java.util.Objects.equals(uriValue, preferences.getString("wallpaper", null))) return;
                if (bitmap == null) return;
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
            String modern = modernGlyphs && kind == TAB_PRAYER ? "dome" : kind == TAB_GALLERY ? "gallery" : kind == TAB_HARAM ? "haram" : null;
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
    }
}
