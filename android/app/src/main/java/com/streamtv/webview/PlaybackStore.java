package com.streamtv.webview;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.webkit.JavascriptInterface;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.security.MessageDigest;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PlaybackStore {
    private static final String PREFS = "playback_history";
    private static final String KEY = "items";
    private final SharedPreferences preferences;
    private final Runnable onChanged;
    private final File posterDirectory;
    private final ExecutorService posterLoader = Executors.newSingleThreadExecutor();
    private volatile boolean enabled=true;

    public PlaybackStore(Context context, Runnable onChanged) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        posterDirectory = new File(context.getFilesDir(), "poster_cache");
        posterDirectory.mkdirs();
        this.onChanged = onChanged;
    }

    @JavascriptInterface
    public void save(String url, String title, String poster, double position, double duration) {
        if(!enabled)return;
        if (!isAllowedUrl(url) || duration < 30 || position < 2) return;
        url = normalize(url);
        try {
            JSONArray existing = readArray();
            JSONArray updated = new JSONArray();
            JSONObject item = new JSONObject();
            item.put("url", url);
            item.put("title", title == null || title.isEmpty() ? "Untitled video" : title);
            item.put("poster", poster == null ? "" : poster);
            item.put("position", position);
            item.put("duration", duration);
            item.put("watched", position / duration >= 0.90);
            for (int i = 0; i < existing.length(); i++) {
                JSONObject old = existing.optJSONObject(i);
                if (old != null && url.equals(old.optString("url"))) {
                    item.put("nextUrl", old.optString("nextUrl"));
                    break;
                }
            }
            item.put("updated", System.currentTimeMillis());
            updated.put(item);
            for (int i = 0; i < existing.length(); i++) {
                JSONObject old = existing.optJSONObject(i);
                if (old != null && !url.equals(old.optString("url"))) updated.put(old);
            }
            preferences.edit().putString(KEY, updated.toString()).apply();
            cachePoster(url, poster);
            if (onChanged != null) onChanged.run();
        } catch (JSONException ignored) {}
    }

    @JavascriptInterface
    public void visit(String url, String title, String poster, String nextUrl) {
        if(!enabled)return;
        if (!isAllowedUrl(url)) return;
        url = normalize(url);
        try {
            JSONArray existing = readArray();
            JSONArray updated = new JSONArray();
            JSONObject item = null;
            for (int i = 0; i < existing.length(); i++) {
                JSONObject candidate = existing.optJSONObject(i);
                if (candidate != null && url.equals(candidate.optString("url"))) {
                    item = candidate;
                    break;
                }
            }
            if (item == null) item = new JSONObject();
            item.put("url", url);
            item.put("title", title == null || title.isEmpty() ? "Last visited page" : title);
            item.put("poster", poster == null ? "" : poster);
            item.put("nextUrl", nextUrl == null ? "" : normalize(nextUrl));
            if (!item.has("position")) item.put("position", 0);
            if (!item.has("duration")) item.put("duration", 0);
            item.put("updated", System.currentTimeMillis());
            updated.put(item);
            for (int i = 0; i < existing.length(); i++) {
                JSONObject old = existing.optJSONObject(i);
                if (old != null && !url.equals(old.optString("url"))) updated.put(old);
            }
            preferences.edit().putString(KEY, updated.toString()).apply();
            cachePoster(url, poster);
            if (onChanged != null) onChanged.run();
        } catch (JSONException ignored) {}
    }
    public void setEnabled(boolean value){enabled=value;}

    public double positionFor(String url) {
        url = normalize(url);
        for (JSONObject item : items()) {
            if (url.equals(item.optString("url"))) return item.optDouble("position", 0);
        }
        return 0;
    }

    public String nextFor(String url) {
        url=normalize(url);
        for(JSONObject item:items()) if(url.equals(item.optString("url"))) return item.optString("nextUrl","");
        return "";
    }

    public void saveStream(String pageUrl,String streamUrl,String streamType,String cookieHeader,float speed) {
        if(!enabled)return;
        String target=normalize(pageUrl);JSONArray source=readArray();
        for(int i=0;i<source.length();i++) { JSONObject item=source.optJSONObject(i);if(item!=null&&target.equals(item.optString("url"))) {
            try { item.put("streamUrl",streamUrl==null?"":streamUrl).put("streamType",streamType==null?"VIDEO":streamType)
                .put("requestHeaders",cookieHeader==null?"":cookieHeader).put("playbackSpeed",speed); } catch(JSONException ignored){} break;
        }}
        preferences.edit().putString(KEY,source.toString()).apply();
    }

    public List<JSONObject> items() {
        List<JSONObject> result = new ArrayList<>();
        JSONArray array = readArray();
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item != null) result.add(item);
        }
        Collections.sort(result, (a, b) -> Long.compare(b.optLong("updated"), a.optLong("updated")));
        return result;
    }

    public File cachedPoster(String pageUrl) {
        File file = new File(posterDirectory, digest(normalize(pageUrl)) + ".jpg");
        return file.isFile() && file.length() > 0 ? file : null;
    }

    private void cachePoster(String pageUrl, String posterUrl) {
        if (posterUrl == null || !posterUrl.startsWith("https://")) return;
        File target = new File(posterDirectory, digest(normalize(pageUrl)) + ".jpg");
        if (target.isFile() && target.length() > 0) return;
        posterLoader.execute(() -> {
            try (InputStream input = new URL(posterUrl).openStream(); FileOutputStream output = new FileOutputStream(target)) {
                byte[] buffer = new byte[8192]; int count;
                while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                if (onChanged != null) onChanged.run();
            } catch (Exception ignored) { target.delete(); }
        });
    }

    private String digest(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes("UTF-8"));
            StringBuilder result = new StringBuilder();
            for (byte item : bytes) result.append(String.format("%02x", item));
            return result.toString();
        } catch (Exception ignored) { return Integer.toHexString(value.hashCode()); }
    }

    public void remove(String url) {
        String target = normalize(url);
        JSONArray source = readArray();
        JSONArray result = new JSONArray();
        for (int i = 0; i < source.length(); i++) {
            JSONObject item = source.optJSONObject(i);
            if (item != null && !target.equals(item.optString("url"))) result.put(item);
        }
        write(result);
    }

    public void removeFolder(String folderPrefix) {
        JSONArray source = readArray();
        JSONArray result = new JSONArray();
        for (int i = 0; i < source.length(); i++) {
            JSONObject item = source.optJSONObject(i);
            if (item != null && !item.optString("url").contains(folderPrefix)) result.put(item);
        }
        write(result);
    }

    public void clear() {
        write(new JSONArray());
    }

    public void toggleWatched(String url) {
        String target = normalize(url);
        JSONArray source = readArray();
        for (int i = 0; i < source.length(); i++) {
            JSONObject item = source.optJSONObject(i);
            if (item != null && target.equals(item.optString("url"))) {
                try { item.put("watched", !item.optBoolean("watched")); }
                catch (JSONException ignored) {}
                break;
            }
        }
        write(source);
    }

    private void write(JSONArray items) {
        preferences.edit().putString(KEY, items.toString()).apply();
        if (onChanged != null) onChanged.run();
    }

    private JSONArray readArray() {
        try { return new JSONArray(preferences.getString(KEY, "[]")); }
        catch (JSONException ignored) { return new JSONArray(); }
    }

    private String normalize(String url) {
        if (url == null || url.isEmpty()) return "";
        try {
            String normalized = Uri.parse(url).buildUpon().clearQuery().fragment(null).build().toString();
            return normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
        } catch (Exception ignored) {
            return url;
        }
    }

    private boolean isAllowedUrl(String url) {
        if (url == null || url.isEmpty()) return false;
        try {
            String host = Uri.parse(url).getHost();
            return host != null && (host.equals("streamimdb.ru") || host.endsWith(".streamimdb.ru") ||
                host.equals("animepahe.com") || host.endsWith(".animepahe.com") ||
                host.equals("animepahe.org") || host.endsWith(".animepahe.org") ||
                host.equals("animepahe.pw") || host.endsWith(".animepahe.pw") ||
                host.equals("dulo.gd") || host.endsWith(".dulo.gd"));
        } catch (Exception ignored) {
            return false;
        }
    }
}
