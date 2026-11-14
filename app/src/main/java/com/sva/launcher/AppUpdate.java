package com.sva.launcher;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * «تحديث التطبيق» under النظام: checks the small version file published beside the APK, and updates
 * through the Downloader app (by AFTVnews), which TV owners already use to install the launcher.
 */
final class AppUpdate {
    static final String APK_URL = "https://github.com/ia99a/AAA-TV/releases/download/tv-latest/aaa-tv.apk";
    static final String VERSION_URL = "https://github.com/ia99a/AAA-TV/releases/download/tv-latest/version.json";
    /** The Downloader short code that leads to {@link #APK_URL}. */
    static final String DOWNLOADER_CODE = "4454086";
    static final String DOWNLOADER_PACKAGE = "com.esaba.downloader";

    interface Callback { void onResult(int latestCode, String latestName); }

    private AppUpdate() { }

    /** Reads the published version off the main thread; reports -1 when it cannot be read. */
    static void checkLatest(Callback callback) {
        Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            int code = -1;
            String name = "";
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(VERSION_URL).openConnection();
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(8000);
                connection.setInstanceFollowRedirects(true);
                if (connection.getResponseCode() == 200) {
                    String body = readSmall(connection.getInputStream());
                    JSONObject json = new JSONObject(body);
                    code = json.optInt("versionCode", -1);
                    name = clean(json.optString("versionName", ""));
                }
            } catch (Exception | OutOfMemoryError unreachable) {
                code = -1;
            } finally {
                if (connection != null) connection.disconnect();
            }
            final int latest = code;
            final String latestName = name;
            main.post(() -> callback.onResult(latest, latestName));
        }, "update-check").start();
    }

    /** At most 4 KB: the file is a few dozen bytes, anything bigger is not ours. */
    private static String readSmall(InputStream in) throws Exception {
        try (InputStream input = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int n;
            while ((n = input.read(buffer)) > 0) {
                out.write(buffer, 0, n);
                if (out.size() > 4096) throw new IllegalStateException("Version file too large");
            }
            return out.toString("UTF-8");
        }
    }

    /** Version names are digits and dots only, so nothing odd from the network reaches the screen. */
    static String clean(String name) {
        return name != null && name.matches("[0-9]{1,4}(\\.[0-9]{1,6}){0,3}") ? name : "";
    }

    static boolean downloaderInstalled(Context context) {
        try {
            context.getPackageManager().getPackageInfo(DOWNLOADER_PACKAGE, 0);
            return true;
        } catch (PackageManager.NameNotFoundException missing) {
            return false;
        }
    }

    /**
     * Opens Downloader on the APK link; if it does not take the link, opens Downloader and shows the
     * code to type; if Downloader is not installed, opens its store page.
     */
    static void openDownloader(Context context) {
        if (downloaderInstalled(context)) {
            Intent direct = new Intent(Intent.ACTION_VIEW, Uri.parse(APK_URL)).setPackage(DOWNLOADER_PACKAGE)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                context.startActivity(direct);
                toast(context, "يُحمَّل التحديث في Downloader · بعد التحميل اضغط Install");
                return;
            } catch (ActivityNotFoundException | SecurityException notAccepted) {
                // Falls through to opening the app with the code.
            }
            Intent launch = context.getPackageManager().getLeanbackLaunchIntentForPackage(DOWNLOADER_PACKAGE);
            if (launch == null) launch = context.getPackageManager().getLaunchIntentForPackage(DOWNLOADER_PACKAGE);
            if (launch != null) {
                context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                toast(context, "اكتب الرمز " + DOWNLOADER_CODE + " في Downloader ثم اضغط Go");
                return;
            }
        }
        try {
            context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + DOWNLOADER_PACKAGE))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            toast(context, "ثبّت Downloader ثم ارجع إلى «تحديث التطبيق»");
        } catch (ActivityNotFoundException noStore) {
            toast(context, "ثبّت تطبيق Downloader من المتجر، ثم اكتب فيه الرمز " + DOWNLOADER_CODE);
        }
    }

    private static void toast(Context context, String message) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show();
    }
}
