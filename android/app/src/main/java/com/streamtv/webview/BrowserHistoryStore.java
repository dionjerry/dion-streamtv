package com.streamtv.webview;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.webkit.JavascriptInterface;
import org.json.JSONArray;
import org.json.JSONObject;

public class BrowserHistoryStore {
    private final SharedPreferences preferences;

    public BrowserHistoryStore(Context context) {
        preferences = context.getSharedPreferences("browser_history", Context.MODE_PRIVATE);
    }

    public synchronized void visit(String url, String title) {
        try {
            Uri uri = Uri.parse(url);
            String host = uri.getHost();
            if (host == null || host.equals("localhost") || !("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))) return;
            JSONArray old = read();
            JSONArray next = new JSONArray();
            JSONObject item = new JSONObject();
            item.put("url", url);
            item.put("title", title == null || title.trim().isEmpty() ? host : title.trim());
            item.put("host", host);
            item.put("visited", System.currentTimeMillis());
            next.put(item);
            for (int i = 0; i < old.length() && next.length() < 500; i++) {
                JSONObject entry = old.optJSONObject(i);
                if (entry != null && !url.equals(entry.optString("url"))) next.put(entry);
            }
            preferences.edit().putString("items", next.toString()).apply();
        } catch (Exception ignored) {}
    }

    @JavascriptInterface public synchronized String getHistory() { return read().toString(); }
    @JavascriptInterface public synchronized void clear() { preferences.edit().remove("items").apply(); }
    public synchronized JSONArray items() { return read(); }

    private JSONArray read() {
        try { return new JSONArray(preferences.getString("items", "[]")); }
        catch (Exception ignored) { return new JSONArray(); }
    }
}
