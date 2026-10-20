package com.sva.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Picture collections for the «الخلفيات» screen, downloaded once and then kept on the TV so they
 * play without a connection:
 *   vintage – public-domain landscape and still-life paintings (Art Institute of Chicago, CC0),
 *   nature  – Wikimedia Commons featured landscape photographs,
 *   carpets – public-domain carpets and rugs from The Met's Islamic Art collection (CC0).
 */
final class GalleryRepository {
    static final String VINTAGE = "vintage", NATURE = "nature", CARPETS = "carpets";
    static final String[] KEYS = {VINTAGE, NATURE, CARPETS};
    static final String[] NAMES = {"لوحات عتيقة", "طبيعة", "سجاد فاخر"};
    private static final int TARGET = 24, MAX_EDGE = 1920;
    private static final long MONTH = 30L * 24 * 60 * 60 * 1000;
    private static final String AGENT = "AAA-TV/1.0 (Android TV wallpapers)";
    /** Subjects left out of the paintings, to keep the collection to landscapes and still lifes. */
    private static final List<String> AVOID = Arrays.asList("nude", "nudes", "women", "men", "woman", "man",
            "portrait", "portraits", "figure", "figures", "children", "child", "girl", "boy", "religion",
            "christian", "christianity", "mythology", "saints", "saint", "bible", "angel", "angels", "jesus", "madonna",
            "virgin", "crucifixion", "deity", "deities", "gods", "bathers", "dance", "dancers");

    private GalleryRepository() { }

    static File folder(Context context, String key) {
        File dir = new File(context.getFilesDir(), "gallery/" + key);
        if (!dir.isDirectory()) dir.mkdirs();
        return dir;
    }

    /** The pictures already saved on the TV, in a stable order. */
    static List<File> cached(Context context, String key) {
        File[] files = folder(context, key).listFiles((dir, name) -> name.endsWith(".jpg"));
        ArrayList<File> list = new ArrayList<>();
        if (files != null) list.addAll(Arrays.asList(files));
        Collections.sort(list, (a, b) -> a.getName().compareTo(b.getName()));
        return list;
    }

    static boolean needsDownload(Context context, String key) {
        File stamp = new File(folder(context, key), ".stamp");
        return cached(context, key).size() < TARGET || System.currentTimeMillis() - stamp.lastModified() > MONTH;
    }

    interface Progress { void onSaved(File file); }

    /** Downloads up to TARGET new pictures. Runs on a background thread; safe to call when offline. */
    static void download(Context context, String key, Progress progress) {
        List<String> urls;
        try {
            urls = VINTAGE.equals(key) ? paintings() : CARPETS.equals(key) ? carpets() : nature();
        } catch (Exception offline) {
            return;
        }
        Collections.shuffle(urls);
        File dir = folder(context, key);
        int have = cached(context, key).size();
        for (String url : urls) {
            if (have >= TARGET) break;
            File file = new File(dir, Integer.toHexString(url.hashCode()) + ".jpg");
            if (file.exists()) continue;
            try {
                Bitmap bitmap = decode(fetch(url, 25_000_000));
                if (bitmap == null) continue;
                File temp = new File(dir, file.getName() + ".part");
                try (FileOutputStream out = new FileOutputStream(temp)) {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out);
                }
                bitmap.recycle();
                if (temp.renameTo(file)) { have++; if (progress != null) progress.onSaved(file); }
            } catch (Exception ignored) { }
        }
        try { new File(dir, ".stamp").createNewFile(); new File(dir, ".stamp").setLastModified(System.currentTimeMillis()); }
        catch (Exception ignored) { }
    }

    private static Bitmap decode(byte[] data) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
        if (bounds.outWidth <= 0) return null;
        int sample = 1;
        while (Math.max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_EDGE) sample *= 2;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        return BitmapFactory.decodeByteArray(data, 0, data.length, options);
    }

    private static List<String> paintings() throws Exception {
        ArrayList<String> urls = new ArrayList<>();
        for (String query : new String[]{"landscape", "still life", "flowers", "seascape"}) {
            String url = "https://api.artic.edu/api/v1/artworks/search?q=" + URLEncoder.encode(query, "UTF-8")
                    + "&query%5Bterm%5D%5Bis_public_domain%5D=true"
                    + "&fields=id,image_id,artwork_type_title,subject_titles,title&limit=60";
            JSONObject root = new JSONObject(new String(fetch(url, 4_000_000), "UTF-8"));
            String iiif = root.optJSONObject("config") == null ? "https://www.artic.edu/iiif/2"
                    : root.getJSONObject("config").optString("iiif_url", "https://www.artic.edu/iiif/2");
            JSONArray data = root.optJSONArray("data");
            for (int i = 0; data != null && i < data.length(); i++) {
                JSONObject item = data.optJSONObject(i);
                if (item == null || !"Painting".equalsIgnoreCase(item.optString("artwork_type_title"))) continue;
                String image = item.optString("image_id", "");
                if (image.isEmpty() || image.equals("null") || mentionsAvoided(item)) continue;
                urls.add(iiif + "/" + image + "/full/1686,/0/default.jpg");
            }
        }
        return urls;
    }

    private static boolean mentionsAvoided(JSONObject item) {
        StringBuilder text = new StringBuilder(item.optString("title", "")).append(' ');
        JSONArray subjects = item.optJSONArray("subject_titles");
        for (int i = 0; subjects != null && i < subjects.length(); i++) text.append(subjects.optString(i)).append(' ');
        for (String word : text.toString().toLowerCase(Locale.ROOT).split("[^a-z]+")) if (AVOID.contains(word)) return true;
        return false;
    }

    private static List<String> carpets() throws Exception {
        String search = "https://collectionapi.metmuseum.org/public/collection/v1/search?hasImages=true&departmentId=14&q=carpet";
        JSONArray ids = new JSONObject(new String(fetch(search, 4_000_000), "UTF-8")).optJSONArray("objectIDs");
        ArrayList<String> urls = new ArrayList<>();
        for (int i = 0; ids != null && i < ids.length() && urls.size() < 40 && i < 120; i++) {
            try {
                JSONObject object = new JSONObject(new String(fetch(
                        "https://collectionapi.metmuseum.org/public/collection/v1/objects/" + ids.getInt(i), 1_000_000), "UTF-8"));
                String name = object.optString("objectName", "").toLowerCase(Locale.ROOT);
                if (!object.optBoolean("isPublicDomain") || !(name.contains("carpet") || name.contains("rug"))) continue;
                String image = object.optString("primaryImage", "");
                if (image.isEmpty()) continue;
                urls.add(image.replace("/original/", "/web-large/"));
            } catch (Exception ignored) { }
        }
        return urls;
    }

    private static List<String> nature() throws Exception {
        String url = "https://commons.wikimedia.org/w/api.php?action=query&format=json&generator=categorymembers"
                + "&gcmtitle=Category:Featured_pictures_of_landscapes&gcmtype=file&gcmlimit=80"
                + "&prop=imageinfo&iiprop=url&iiurlwidth=1920";
        JSONObject pages = new JSONObject(new String(fetch(url, 4_000_000), "UTF-8")).getJSONObject("query").getJSONObject("pages");
        JSONArray names = pages.names();
        ArrayList<String> urls = new ArrayList<>();
        for (int i = 0; names != null && i < names.length(); i++) {
            JSONArray info = pages.getJSONObject(names.getString(i)).optJSONArray("imageinfo");
            if (info == null || info.length() == 0) continue;
            String thumb = info.getJSONObject(0).optString("thumburl", "");
            String lower = thumb.toLowerCase(Locale.ROOT);
            if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) urls.add(thumb);
        }
        return urls;
    }

    private static byte[] fetch(String address, int limit) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(20000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", AGENT);
        connection.setRequestProperty("AIC-User-Agent", AGENT);
        try (InputStream input = connection.getInputStream()) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[32768];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
                if (output.size() > limit) throw new IllegalStateException("Too large");
            }
            return output.toByteArray();
        } finally {
            connection.disconnect();
        }
    }
}
