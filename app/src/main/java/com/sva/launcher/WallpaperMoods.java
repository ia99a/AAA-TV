package com.sva.launcher;

import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Background moods for the home screen:
 *   calm   – the flat built-in backgrounds (CalmWallpapers),
 *   floral – public-domain flower paintings from the Art Institute of Chicago (CC0),
 *   nature – Wikimedia Commons featured landscape photographs.
 * Image lists are fetched on the TV and cached for a week.
 */
final class WallpaperMoods {
    static final String PREF = "wallpaperMood";
    static final String CALM = "calm", FLORAL = "floral", NATURE = "nature";
    static final String[] KEYS = {CALM, FLORAL, NATURE};
    static final String[] NAMES = {"هادئ", "معرض الزهور", "طبيعة"};
    private static final long WEEK = 7L * 24 * 60 * 60 * 1000;
    private static final String AGENT = "SvaLauncher/4.1 (Android TV home screen)";

    private WallpaperMoods() { }

    static String current(SharedPreferences preferences) {
        String mood = preferences.getString(PREF, CALM);
        for (String key : KEYS) if (key.equals(mood)) return mood;
        return CALM;
    }

    static int index(String mood) {
        for (int i = 0; i < KEYS.length; i++) if (KEYS[i].equals(mood)) return i;
        return 0;
    }

    static List<String> cached(SharedPreferences preferences, String mood) {
        ArrayList<String> urls = new ArrayList<>();
        try {
            JSONArray values = new JSONArray(preferences.getString("moodImages_" + mood, "[]"));
            for (int i = 0; i < values.length(); i++) urls.add(values.getString(i));
        } catch (Exception ignored) { }
        return urls;
    }

    static boolean stale(SharedPreferences preferences, String mood) {
        return cached(preferences, mood).isEmpty()
                || System.currentTimeMillis() - preferences.getLong("moodImagesAt_" + mood, 0L) > WEEK;
    }

    /** Fetches a fresh, shuffled list of image URLs. Runs on a background thread. */
    static List<String> fetch(SharedPreferences preferences, String mood) throws Exception {
        List<String> urls = FLORAL.equals(mood) ? artInstitute("flowers") : nature();
        if (urls.isEmpty()) throw new IllegalStateException("No images found");
        Collections.shuffle(urls);
        preferences.edit().putString("moodImages_" + mood, new JSONArray(urls).toString())
                .putLong("moodImagesAt_" + mood, System.currentTimeMillis()).apply();
        return urls;
    }

    /** Public-domain paintings only, so every image may be shown freely. */
    private static List<String> artInstitute(String query) throws Exception {
        String url = "https://api.artic.edu/api/v1/artworks/search?q=" + java.net.URLEncoder.encode(query, "UTF-8")
                + "&query%5Bterm%5D%5Bis_public_domain%5D=true&fields=id,title,image_id,artwork_type_title&limit=100";
        JSONObject root = new JSONObject(download(url));
        String iiif = root.optJSONObject("config") == null ? "https://www.artic.edu/iiif/2"
                : root.getJSONObject("config").optString("iiif_url", "https://www.artic.edu/iiif/2");
        JSONArray data = root.optJSONArray("data");
        ArrayList<String> urls = new ArrayList<>();
        if (data == null) return urls;
        for (int i = 0; i < data.length(); i++) {
            JSONObject item = data.optJSONObject(i);
            if (item == null) continue;
            String image = item.optString("image_id", "");
            String type = item.optString("artwork_type_title", "");
            if (image.isEmpty() || image.equals("null") || !"Painting".equalsIgnoreCase(type)) continue;
            urls.add(iiif + "/" + image + "/full/1686,/0/default.jpg");
        }
        return urls;
    }

    private static List<String> nature() throws Exception {
        ArrayList<String> urls = new ArrayList<>();
        try {
            String url = "https://commons.wikimedia.org/w/api.php?action=query&format=json&generator=categorymembers"
                    + "&gcmtitle=Category:Featured_pictures_of_landscapes&gcmtype=file&gcmlimit=80"
                    + "&prop=imageinfo&iiprop=url&iiurlwidth=1920";
            JSONObject pages = new JSONObject(download(url)).getJSONObject("query").getJSONObject("pages");
            JSONArray names = pages.names();
            for (int i = 0; names != null && i < names.length(); i++) {
                JSONObject page = pages.getJSONObject(names.getString(i));
                JSONArray info = page.optJSONArray("imageinfo");
                if (info == null || info.length() == 0) continue;
                String thumb = info.getJSONObject(0).optString("thumburl", "");
                String lower = thumb.toLowerCase(java.util.Locale.ROOT);
                if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) urls.add(thumb);
            }
        } catch (Exception ignored) { }
        // Fall back to public-domain landscape paintings if Commons is unreachable.
        if (urls.isEmpty()) urls.addAll(artInstitute("landscape"));
        return urls;
    }

    private static String download(String address) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(12000);
        connection.setRequestProperty("User-Agent", AGENT);
        connection.setRequestProperty("AIC-User-Agent", AGENT);
        try (InputStream input = connection.getInputStream()) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[16384];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            return output.toString("UTF-8");
        } finally {
            connection.disconnect();
        }
    }
}
