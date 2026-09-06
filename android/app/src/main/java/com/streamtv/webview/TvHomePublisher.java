package com.streamtv.webview;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;

import androidx.tvprovider.media.tv.TvContractCompat;
import androidx.tvprovider.media.tv.WatchNextProgram;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Publishes unfinished videos into Android TV's system Watch Next row. */
public class TvHomePublisher {
    private final Context context;
    private final SharedPreferences preferences;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    public TvHomePublisher(Context context) {
        this.context = context.getApplicationContext();
        this.preferences = context.getSharedPreferences("tv_home", Context.MODE_PRIVATE);
    }

    public void syncAsync(List<JSONObject> snapshot) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        String serialized = new JSONArray(snapshot).toString();
        worker.execute(() -> sync(serialized));
    }

    private void sync(String serialized) {
        try {
            JSONArray items = new JSONArray(serialized);
            JSONObject ids = new JSONObject(preferences.getString("watch_next_ids", "{}"));
            Set<String> retained = new HashSet<>();
            int published = 0;
            for (int i = 0; i < items.length() && published < 30; i++) {
                JSONObject item = items.optJSONObject(i);
                if (item == null || item.optBoolean("watched")) continue;
                double position = item.optDouble("position", 0);
                double duration = item.optDouble("duration", 0);
                if (position < 2 || duration < 30 || position >= duration * .90) continue;
                String url = item.optString("url", "");
                if (url.isEmpty()) continue;
                retained.add(url);
                long currentId = ids.optLong(url, -1);
                long resultId = publish(item, currentId);
                if (resultId > 0) ids.put(url, resultId);
                published++;
            }

            ContentResolver resolver = context.getContentResolver();
            Iterator<String> keys = ids.keys();
            Set<String> stale = new HashSet<>();
            while (keys.hasNext()) {
                String url = keys.next();
                if (!retained.contains(url)) stale.add(url);
            }
            for (String url : stale) {
                long id = ids.optLong(url, -1);
                if (id > 0) resolver.delete(TvContractCompat.buildWatchNextProgramUri(id), null, null);
                ids.remove(url);
            }
            preferences.edit().putString("watch_next_ids", ids.toString()).apply();
        } catch (Exception ignored) {}
    }

    private long publish(JSONObject item, long existingId) {
        try {
            String url = item.optString("url");
            String title = item.optString("title", "Continue watching");
            String poster = item.optString("poster", "");
            int position = (int) Math.round(item.optDouble("position", 0) * 1000);
            int duration = (int) Math.round(item.optDouble("duration", 0) * 1000);
            Uri intentUri = new Uri.Builder().scheme("dionstreamtv").authority("watch")
                .appendQueryParameter("url", url).build();
            WatchNextProgram.Builder builder = new WatchNextProgram.Builder()
                .setType(url.contains("/tv/") ? TvContractCompat.WatchNextPrograms.TYPE_TV_EPISODE
                    : TvContractCompat.WatchNextPrograms.TYPE_MOVIE)
                .setWatchNextType(TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE)
                .setTitle(title)
                .setDescription("Resume in DiON streamTV")
                .setIntentUri(intentUri)
                .setInternalProviderId(url)
                .setContentId(url)
                .setLastPlaybackPositionMillis(position)
                .setDurationMillis(duration)
                .setLastEngagementTimeUtcMillis(item.optLong("updated", System.currentTimeMillis()));
            if (poster.startsWith("http")) builder.setPosterArtUri(Uri.parse(poster));
            ContentResolver resolver = context.getContentResolver();
            if (existingId > 0) {
                Uri existing = TvContractCompat.buildWatchNextProgramUri(existingId);
                if (resolver.update(existing, builder.build().toContentValues(), null, null) > 0) return existingId;
            }
            Uri inserted = resolver.insert(TvContractCompat.WatchNextPrograms.CONTENT_URI,
                builder.build().toContentValues());
            return inserted == null ? -1 : Long.parseLong(inserted.getLastPathSegment());
        } catch (Exception ignored) { return -1; }
    }
}
