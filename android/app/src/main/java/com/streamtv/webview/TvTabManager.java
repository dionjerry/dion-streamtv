package com.streamtv.webview;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Bundle;
import android.webkit.WebView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

public class TvTabManager {
    public static class Tab {
        long id; String title; String url; String screenshot; String group; boolean incognito; boolean pinned; Bundle state;
        Tab(long id, String title, String url, String screenshot) {
            this.id=id; this.title=title; this.url=url; this.screenshot=screenshot; this.group=groupFor(url);
        }
    }

    private final Context context;
    private final WebView webView;
    private final List<Tab> tabs = new ArrayList<>();
    private int current;
    private Tab lastClosed;

    public TvTabManager(Context context, WebView webView) {
        this.context=context; this.webView=webView; restoreMetadata();
        if (tabs.isEmpty()) tabs.add(new Tab(System.currentTimeMillis(), "Home", "http://localhost/", ""));
    }

    public List<Tab> all() { return new ArrayList<>(tabs); }
    public int currentIndex() { return current; }
    public boolean isPrivate() { return !tabs.isEmpty()&&tabs.get(current).incognito; }

    public void pageChanged(String url, String title) {
        if (tabs.isEmpty() || url == null) return;
        Tab tab=tabs.get(current); tab.url=url; tab.title=title == null || title.isEmpty() ? url : title; tab.group=groupFor(url);
        persist();
    }

    public void newTab() {
        saveCurrent();
        tabs.add(new Tab(System.currentTimeMillis(), "New tab", "http://localhost/", ""));
        current=tabs.size()-1; persist(); webView.loadUrl("http://localhost/");
    }
    public void newPrivateTab() { newTab(); tabs.get(current).incognito=true; tabs.get(current).title="Private tab"; persist(); }
    public void reopenClosed() { if(lastClosed==null)return; saveCurrent();tabs.add(lastClosed);current=tabs.size()-1;lastClosed=null;webView.loadUrl(tabs.get(current).url);persist(); }
    public void togglePin(int index) { if(index>=0&&index<tabs.size()){tabs.get(index).pinned=!tabs.get(index).pinned;persist();} }
    public boolean hasClosedTab(){return lastClosed!=null;}

    public void switchTo(int index) {
        if (index < 0 || index >= tabs.size() || index == current) return;
        saveCurrent(); current=index; Tab tab=tabs.get(index);
        if (tab.state != null) webView.restoreState(tab.state); else webView.loadUrl(tab.url);
        persist();
    }

    public void closeCurrent() {
        if (tabs.size() == 1) { webView.loadUrl("http://localhost/"); return; }
        lastClosed=tabs.remove(current); current=Math.max(0, Math.min(current, tabs.size()-1));
        Tab tab=tabs.get(current);
        if (tab.state != null) webView.restoreState(tab.state); else webView.loadUrl(tab.url);
        persist();
    }

    private void saveCurrent() {
        if (tabs.isEmpty()) return;
        Tab tab=tabs.get(current); tab.state=new Bundle(); webView.saveState(tab.state);
        if (webView.getWidth() <= 0 || webView.getHeight() <= 0) return;
        try {
            int width=640, height=Math.max(1, webView.getHeight()*width/webView.getWidth());
            Bitmap shot=Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
            Canvas canvas=new Canvas(shot); canvas.scale(width/(float)webView.getWidth(), height/(float)webView.getHeight());
            webView.draw(canvas);
            File file=new File(context.getCacheDir(), "tab-"+tab.id+".jpg");
            try (FileOutputStream out=new FileOutputStream(file)) { shot.compress(Bitmap.CompressFormat.JPEG, 72, out); }
            shot.recycle(); tab.screenshot=file.getAbsolutePath();
        } catch (Exception ignored) {}
    }

    private void restoreMetadata() {
        try {
            JSONArray data=new JSONArray(context.getSharedPreferences("tabs", Context.MODE_PRIVATE).getString("items", "[]"));
            for(int i=0;i<data.length();i++) { JSONObject o=data.getJSONObject(i); Tab t=new Tab(o.optLong("id"),o.optString("title"),o.optString("url"),o.optString("screenshot"));t.pinned=o.optBoolean("pinned");t.group=o.optString("group",groupFor(t.url));tabs.add(t); }
            current=Math.max(0,Math.min(context.getSharedPreferences("tabs",Context.MODE_PRIVATE).getInt("current",0),tabs.size()-1));
        } catch(Exception ignored) { tabs.clear(); current=0; }
    }

    private void persist() {
        JSONArray data=new JSONArray();
        try { for(Tab t:tabs) if(!t.incognito)data.put(new JSONObject().put("id",t.id).put("title",t.title).put("url",t.url).put("screenshot",t.screenshot).put("pinned",t.pinned).put("group",t.group)); }
        catch(Exception ignored) {}
        context.getSharedPreferences("tabs",Context.MODE_PRIVATE).edit().putString("items",data.toString()).putInt("current",current).apply();
    }

    private static String groupFor(String url) {
        if(url==null)return "Web";String u=url.toLowerCase();
        if(u.contains("streamimdb"))return "Movies + Series";if(u.contains("animepahe"))return "Anime";if(u.contains("google"))return "Google";if(u.contains("localhost"))return "Home";return "Web";
    }
}
