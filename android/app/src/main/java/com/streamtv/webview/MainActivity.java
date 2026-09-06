package com.streamtv.webview;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.media.AudioManager;
import android.view.WindowManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Color;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.CookieManager;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;
import org.json.JSONArray;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.net.URL;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import androidx.activity.OnBackPressedCallback;

import com.getcapacitor.BridgeActivity;
import com.getcapacitor.BridgeWebChromeClient;
import androidx.core.content.FileProvider;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

public class MainActivity extends BridgeActivity {
    private TvCursorView cursorView;
    private TextView adBlockToggle;
    private AdBlockWebViewClient adBlockClient;
    private boolean adBlockEnabled;
    private boolean externalLinksAsk;
    private TextView externalLinksToggle;
    private TextView exploreButton;
    private TextView tabsButton;
    private TextView navigationModeButton;
    private TextView refreshButton;
    private LinearLayout explorePanel;
    private LinearLayout historyList;
    private ScrollView historyScroll;
    private TextView exploreHeading;
    private TextView exploreSubtitle;
    private LinearLayout zoomControls;
    private TextView zoomLevel;
    private TvMediaControllerView mediaController;
    private String currentFolder;
    private final List<View> nativeClickTargets = new ArrayList<>();
    private PlaybackStore playbackStore;
    private BrowserHistoryStore browserHistoryStore;
    private TvHomePublisher tvHomePublisher;
    private TvTabManager tabManager;
    private final ExecutorService imageLoader = Executors.newFixedThreadPool(3);
    private float pointerSpeed;
    private int pointerSmoothness;
    private float pointerSize;
    private boolean pointerAcceleration;
    private int pointerStyle;
    private View fullscreenView;
    private WebChromeClient.CustomViewCallback fullscreenCallback;
    private File pendingUpdate;
    private TvWebChromeClient tvChromeClient;
    private boolean backgroundPlayback;
    private boolean keepScreenAwake;
    private String websiteViewMode;
    private String appliedWebsiteViewMode="";
    private String defaultUserAgent;
    private final Handler inputHandler = new Handler(Looper.getMainLooper());
    private boolean centerHeld;
    private boolean cursorDragging;
    private boolean focusMode;
    private float edgeScrollVelocity;
    private boolean edgeScrollRunning;
    private long edgeScrollInputTime;
    private float libraryScrollVelocity;
    private boolean libraryScrollRunning;
    private long libraryScrollInputTime;
    private int seekSeconds;
    private long lastBackPressed;
    private String activeMediaFrame = "";
    private int activeMediaScore = -1;
    private long activeMediaSeen;
    private String configuredMediaFrame = "";
    private String pendingMediaFrame = "";
    private long pendingMediaSince;
    private boolean mediaPromptShowing;
    private boolean mediaRejectedForPage;
    private boolean mediaApproved;
    private boolean controlsAllowedForVideo=true;
    private boolean pendingPlaying, pendingWaiting;
    private String mediaPageUrl="";
    private double pendingPosition, pendingDuration;
    private int pendingScore;
    private String pendingStreamUrl="", pendingStreamType="VIDEO", pendingTracks="[]", pendingQualities="[]", pendingAudioTracks="[]";
    private final Runnable beginCursorDrag = () -> {
        if (centerHeld && fullscreenView == null) {
            cursorDragging = true;
            dispatchCursorTouch(MotionEvent.ACTION_DOWN);
            Toast.makeText(this, "Drag mode", Toast.LENGTH_SHORT).show();
        }
    };
    private final Runnable edgeScrollFrame = new Runnable() {
        @Override public void run() {
            long idle = SystemClock.uptimeMillis() - edgeScrollInputTime;
            if (idle > 70) edgeScrollVelocity *= .82f;
            if (Math.abs(edgeScrollVelocity) < .025f) {
                edgeScrollVelocity = 0;
                edgeScrollRunning = false;
                return;
            }
            dispatchMouseWheel(edgeScrollVelocity);
            inputHandler.postDelayed(this, 16);
        }
    };
    private final Runnable libraryScrollFrame = new Runnable() {
        @Override public void run() {
            if (historyScroll == null || explorePanel == null || explorePanel.getVisibility() != View.VISIBLE) {
                libraryScrollVelocity=0; libraryScrollRunning=false; return;
            }
            long idle=SystemClock.uptimeMillis()-libraryScrollInputTime;
            if(idle>70)libraryScrollVelocity*=.82f;
            if(Math.abs(libraryScrollVelocity)<.08f){libraryScrollVelocity=0;libraryScrollRunning=false;return;}
            historyScroll.scrollBy(0,Math.round(libraryScrollVelocity));
            inputHandler.postDelayed(this,16);
        }
    };
    private static final String UPDATE_URL = "https://pub-25182af2d73f4f29a7cc596f6e1f4dfa.r2.dev/app-debug.apk";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        enterImmersiveMode();
        tvHomePublisher = new TvHomePublisher(this);
        playbackStore = new PlaybackStore(this, () -> runOnUiThread(() -> {
            refreshHistory();
            tvHomePublisher.syncAsync(playbackStore.items());
        }));
        browserHistoryStore = new BrowserHistoryStore(this);
        tabManager = new TvTabManager(this, bridge.getWebView());
        bridge.getWebView().addJavascriptInterface(playbackStore, "PlaybackTracker");
        bridge.getWebView().addJavascriptInterface(browserHistoryStore, "BrowserHistory");
        bridge.getWebView().addJavascriptInterface(new MediaBridge(), "DionMedia");
        bridge.getWebView().addJavascriptInterface(new DiagnosticsBridge(), "DionDiagnostics");
        bridge.getWebView().addJavascriptInterface(new ViewportBridge(), "DionViewport");
        installDocumentStartMediaDetection();
        pointerSpeed = getPreferences(MODE_PRIVATE).getFloat("pointerSpeed", 1f);
        pointerSmoothness = getPreferences(MODE_PRIVATE).getInt("pointerSmoothness", 42);
        pointerSize = getPreferences(MODE_PRIVATE).getFloat("pointerSize", 1f);
        pointerAcceleration = getPreferences(MODE_PRIVATE).getBoolean("pointerAcceleration", true);
        pointerStyle = getPreferences(MODE_PRIVATE).getInt("pointerStyle", 0);
        focusMode = getPreferences(MODE_PRIVATE).getBoolean("focusMode", false);
        seekSeconds = getPreferences(MODE_PRIVATE).getInt("seekSeconds", 10);
        backgroundPlayback = getPreferences(MODE_PRIVATE).getBoolean("backgroundPlayback", false);
        keepScreenAwake = getPreferences(MODE_PRIVATE).getBoolean("keepScreenAwake", true);
        websiteViewMode = getPreferences(MODE_PRIVATE).getString("websiteViewMode", "desktop");
        defaultUserAgent = WebSettings.getDefaultUserAgent(this);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(bridge.getWebView(), true);
        WebSettings browserSettings=bridge.getWebView().getSettings();
        browserSettings.setDomStorageEnabled(true);
        browserSettings.setSupportZoom(true);
        browserSettings.setBuiltInZoomControls(true);
        browserSettings.setDisplayZoomControls(false);
        applyKeepAwake();
        applyWebsiteView(false);
        bridge.getWebView().addJavascriptInterface(new TvSettingsBridge(), "TvSettings");
        bridge.getWebView().addJavascriptInterface(new UpdateBridge(), "AppUpdater");
        tvChromeClient = new TvWebChromeClient();
        bridge.getWebView().setWebChromeClient(tvChromeClient);
        adBlockEnabled = getPreferences(MODE_PRIVATE).getBoolean("adBlockEnabled", true);
        externalLinksAsk = getPreferences(MODE_PRIVATE).getBoolean("externalLinksAsk", false);
        adBlockClient = new AdBlockWebViewClient(
            bridge, adBlockEnabled, this::handlePageFinished, this::handleExternalNavigation, this::recoverWebView);
        bridge.setWebViewClient(adBlockClient);
        installTvCursor();
        installAdBlockToggle();
        installExternalLinksToggle();
        installExplorePanel();
        installTabsButton();
        installNavigationButtons();
        installZoomControls();
        installMediaController();
        installBrandSplash();
        String requested=watchUrl(getIntent());
        String recovery=getPreferences(MODE_PRIVATE).getString("recoverPage","");
        if(!requested.isEmpty()) bridge.getWebView().postDelayed(()->bridge.getWebView().loadUrl(requested),450);
        else if(!recovery.isEmpty()){getPreferences(MODE_PRIVATE).edit().remove("recoverPage").apply();bridge.getWebView().postDelayed(()->bridge.getWebView().loadUrl(recovery),450);}
        tvHomePublisher.syncAsync(playbackStore.items());

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                long now=SystemClock.uptimeMillis();
                if (focusMode) {
                    setNavigationMode(false);
                    return;
                }
                if(now-lastBackPressed<750 && bridge!=null) {
                    lastBackPressed=0; bridge.getWebView().loadUrl("http://localhost/"); return;
                }
                lastBackPressed=now;
                if (fullscreenView != null) {
                    tvChromeClient.onHideCustomView();
                } else if (explorePanel != null && explorePanel.getVisibility() == View.VISIBLE) {
                    hideExplorePanel();
                } else if (bridge != null && bridge.getWebView().canGoBack()) {
                    bridge.getWebView().goBack();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
    }

    private void handlePageFinished() {
        String finishedUrl=bridge.getWebView().getUrl();
        if(finishedUrl!=null&&!finishedUrl.equals(mediaPageUrl)){mediaPageUrl=finishedUrl;resetMediaSelection();}
        playbackStore.setEnabled(!tabManager.isPrivate());
        adBlockClient.setEnabled(adBlockEnabled && siteBoolean("adblock", true));
        applyWebsiteView(false);
        injectPlaybackTracker();
        WebView webView = bridge.getWebView();
        if(!tabManager.isPrivate()) browserHistoryStore.visit(webView.getUrl(), webView.getTitle());
        tabManager.pageChanged(webView.getUrl(), webView.getTitle());
        updateTabsButton();
        getPreferences(MODE_PRIVATE).edit().putString("lastPage",webView.getUrl()).apply();
        webView.postDelayed(this::applySavedZoom, 350);
    }

    private void resetMediaSelection() {
        activeMediaFrame="";activeMediaScore=-1;configuredMediaFrame="";pendingMediaFrame="";
        pendingMediaSince=0;mediaPromptShowing=false;mediaRejectedForPage=false;mediaApproved=false;controlsAllowedForVideo=true;
        if(mediaController!=null){mediaController.setFullscreenActive(false);mediaController.update(false,false,false,0,0);}
    }

    private void recoverWebView() {
        runOnUiThread(()->{
            String last=getPreferences(MODE_PRIVATE).getString("lastPage","http://localhost/");
            logDiagnostic("renderer","WebView renderer exited; restoring "+last);
            getPreferences(MODE_PRIVATE).edit().putString("recoverPage",last).apply();
            Toast.makeText(this,"Browser recovered after a WebView crash",Toast.LENGTH_LONG).show();
            recreate();
        });
    }

    private void logDiagnostic(String type,String message) {
        try {
            JSONArray old=new JSONArray(getPreferences(MODE_PRIVATE).getString("diagnostics","[]"));JSONArray next=new JSONArray();
            next.put(new JSONObject().put("time",System.currentTimeMillis()).put("type",type).put("message",message));
            for(int i=0;i<Math.min(99,old.length());i++)next.put(old.get(i));
            getPreferences(MODE_PRIVATE).edit().putString("diagnostics",next.toString()).apply();
        } catch(Exception ignored){}
    }

    private class DiagnosticsBridge {
        @JavascriptInterface public String getReport(){try{return new JSONObject().put("blocked",adBlockClient==null?0:adBlockClient.getBlockedRequests()).put("events",new JSONArray(getPreferences(MODE_PRIVATE).getString("diagnostics","[]"))).toString();}catch(Exception e){return "{\"blocked\":0,\"events\":[]}";}}
        @JavascriptInterface public void clear(){getPreferences(MODE_PRIVATE).edit().remove("diagnostics").apply();}
    }

    private void installExternalLinksToggle() {
        externalLinksToggle = makeToolbarPill(externalLinksAsk ? "LINKS · ASK" : "LINKS · BLOCKED",
            externalLinksAsk ? 0xee8a5a08 : 0xee172033);
        externalLinksToggle.setOnClickListener(v -> {
            externalLinksAsk = !externalLinksAsk;
            getPreferences(MODE_PRIVATE).edit().putBoolean("externalLinksAsk", externalLinksAsk).apply();
            externalLinksToggle.setText(externalLinksAsk ? "LINKS · ASK" : "LINKS · BLOCKED");
            styleToolbarPill(externalLinksToggle, externalLinksAsk ? 0xee8a5a08 : 0xee172033);
            Toast.makeText(this, externalLinksAsk
                ? "External links will ask before opening"
                : "External links are blocked", Toast.LENGTH_SHORT).show();
        });
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        params.setMargins(0, (int) dp(16), 0, 0);
        addContentView(externalLinksToggle, params);
        nativeClickTargets.add(externalLinksToggle);
        cursorView.bringToFront();
    }

    private void installTabsButton() {
        tabsButton = makeToolbarPill("▣  TABS", 0xee172033);
        tabsButton.setOnClickListener(v -> showTabs());
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.setMargins((int)dp(145), (int)dp(16), 0, 0);
        addContentView(tabsButton, params);
        nativeClickTargets.add(tabsButton);
        updateTabsButton();
        cursorView.bringToFront();
    }

    private void installNavigationButtons() {
        navigationModeButton=makeToolbarPill(focusMode?"FOCUS":"POINTER",0xee172033);
        navigationModeButton.setOnClickListener(v->toggleNavigationMode());
        FrameLayout.LayoutParams modeParams=new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT,FrameLayout.LayoutParams.WRAP_CONTENT);
        modeParams.gravity=Gravity.TOP|Gravity.START; modeParams.setMargins((int)dp(270),(int)dp(16),0,0);
        addContentView(navigationModeButton,modeParams); nativeClickTargets.add(navigationModeButton);
        refreshButton=makeToolbarPill("↻  REFRESH",0xee172033);
        refreshButton.setOnClickListener(v->bridge.getWebView().reload());
        FrameLayout.LayoutParams refreshParams=new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT,FrameLayout.LayoutParams.WRAP_CONTENT);
        refreshParams.gravity=Gravity.BOTTOM|Gravity.START; refreshParams.setMargins((int)dp(20),0,0,(int)dp(18));
        addContentView(refreshButton,refreshParams); nativeClickTargets.add(refreshButton); cursorView.bringToFront();
    }

    private void toggleNavigationMode() {
        setNavigationMode(!focusMode);
    }

    private void setNavigationMode(boolean useFocus) {
        focusMode=useFocus; getPreferences(MODE_PRIVATE).edit().putBoolean("focusMode",focusMode).apply();
        navigationModeButton.setText(focusMode?"FOCUS":"POINTER");
        cursorView.setVisibility(focusMode?View.INVISIBLE:View.VISIBLE);
        if (!focusMode) { cursorView.center(); cursorView.bringToFront(); }
        Toast.makeText(this,focusMode?"D-pad Focus mode":"Pointer mode",Toast.LENGTH_SHORT).show();
    }

    private void updateTabsButton() {
        if (tabsButton != null && tabManager != null) tabsButton.setText("▣  TABS · " + tabManager.all().size());
    }

    private void showTabs() {
        List<TvTabManager.Tab> tabs = tabManager.all();
        LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding((int)dp(18),(int)dp(10),(int)dp(18),(int)dp(18));
        LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.HORIZONTAL);
        TextView add=makePill("＋ NEW",0xff2457d6);add.setOnClickListener(v->{tabManager.newTab();updateTabsButton();});actions.addView(add);
        TextView privacy=makePill("◉ PRIVATE",0xff273244);privacy.setOnClickListener(v->{tabManager.newPrivateTab();updateTabsButton();});actions.addView(privacy,horizontalSpacing());
        TextView reopen=makePill("↶ REOPEN",0xff273244);reopen.setEnabled(tabManager.hasClosedTab());reopen.setAlpha(tabManager.hasClosedTab()?1f:.4f);reopen.setOnClickListener(v->{tabManager.reopenClosed();updateTabsButton();});actions.addView(reopen,horizontalSpacing());content.addView(actions);
        ScrollView scroll=new ScrollView(this);LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);scroll.addView(list);
        for (int i=0;i<tabs.size();i++) {
            final int index=i; TvTabManager.Tab tab=tabs.get(i);
            LinearLayout card=new LinearLayout(this);card.setGravity(Gravity.CENTER_VERTICAL);card.setPadding((int)dp(10),(int)dp(10),(int)dp(10),(int)dp(10));
            GradientDrawable cardBg=new GradientDrawable();cardBg.setColor(i==tabManager.currentIndex()?0xff172b55:0xff111827);cardBg.setCornerRadius(dp(14));cardBg.setStroke((int)dp(1),i==tabManager.currentIndex()?0xff5d8cff:0xff344159);card.setBackground(cardBg);
            ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.CENTER_CROP);File shot=new File(tab.screenshot==null?"":tab.screenshot);if(shot.isFile())image.setImageBitmap(BitmapFactory.decodeFile(shot.getAbsolutePath()));else image.setBackgroundColor(0xff202b3d);card.addView(image,new LinearLayout.LayoutParams((int)dp(150),(int)dp(84)));
            TextView copy=new TextView(this);copy.setText((tab.incognito?"PRIVATE · ":tab.group+" · ")+(tab.pinned?"★\n":"\n")+tab.title+"\n"+tab.url);copy.setTextColor(Color.WHITE);copy.setTextSize(14);copy.setMaxLines(4);copy.setPadding((int)dp(14),0,(int)dp(10),0);card.addView(copy,new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1));
            TextView pin=makePill(tab.pinned?"★":"☆",0xff273244);pin.setOnClickListener(v->{tabManager.togglePin(index);pin.setText("★");});card.addView(pin);
            card.setFocusable(true);card.setOnClickListener(v->tabManager.switchTo(index));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT);cp.setMargins(0,(int)dp(10),0,0);list.addView(card,cp);
        }
        content.addView(scroll,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,(int)dp(430)));
        new AlertDialog.Builder(this).setTitle("Visual tabs").setView(content)
            .setNegativeButton("Close current", (dialog, which) -> { tabManager.closeCurrent(); updateTabsButton(); })
            .setPositiveButton("Done", null).show();
    }

    private void handleExternalNavigation(Uri uri) {
        runOnUiThread(() -> {
            String host = uri.getHost();
            if (host == null || !("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))) return;
            if (siteBoolean("popups", false)) {
                bridge.getWebView().loadUrl(uri.toString());
                return;
            }
            Set<String> allowed = getPreferences(MODE_PRIVATE)
                .getStringSet("allowedExternalHosts", new HashSet<>());
            if (allowed.contains(host)) {
                openExternal(uri);
                return;
            }
            if (!externalLinksAsk) {
                Toast.makeText(this, "Blocked external site: " + host, Toast.LENGTH_SHORT).show();
                return;
            }
            new AlertDialog.Builder(this)
                .setTitle("Open external website?")
                .setMessage(host + " wants to leave Stream TV and open another app.")
                .setNegativeButton("Block", null)
                .setPositiveButton("Open once", (dialog, which) -> openExternal(uri))
                .setNeutralButton("Always allow site", (dialog, which) -> {
                    Set<String> updated = new HashSet<>(getPreferences(MODE_PRIVATE)
                        .getStringSet("allowedExternalHosts", new HashSet<>()));
                    updated.add(host);
                    getPreferences(MODE_PRIVATE).edit()
                        .putStringSet("allowedExternalHosts", updated).apply();
                    openExternal(uri);
                })
                .show();
        });
    }

    private void openExternal(Uri uri) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); }
        catch (Exception exception) {
            Toast.makeText(this, "No app can open this link", Toast.LENGTH_SHORT).show();
        }
    }

    private void installTvCursor() {
        cursorView = new TvCursorView(this);
        cursorView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        addContentView(cursorView, new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ));
        cursorView.setPointerScale(pointerSize);
        cursorView.setSmoothness(pointerSmoothness);
        cursorView.setStyle(pointerStyle);
        cursorView.setPositionListener(this::sendHover);
        cursorView.setIdleListener(this::sendHoverExit);
        Toast.makeText(this, "D-pad moves pointer • OK clicks", Toast.LENGTH_LONG).show();
    }

    private void installMediaController() {
        mediaController = new TvMediaControllerView(this, this::sendMediaCommand);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.BOTTOM;
        addContentView(mediaController, params);
        cursorView.bringToFront();
    }

    private class MediaBridge {
        @JavascriptInterface public void state(boolean available, boolean playing, boolean waiting,
                                               double position, double duration) {
            runOnUiThread(() -> {
                if (mediaController != null)
                    mediaController.update(available, playing, waiting, position, duration);
            });
        }
        @JavascriptInterface public synchronized void candidate(String frame, int score, boolean available, boolean playing,
                                                   boolean waiting, double position, double duration, String streamUrl,
                                                   String streamType, String tracks, String qualities, String audioTracks) {
            if (!siteBoolean("controls", true)) return;
            String pageUrl=bridge.getWebView().getUrl();
            if(pageUrl!=null&&!pageUrl.equals(mediaPageUrl)) {
                mediaPageUrl=pageUrl;activeMediaFrame="";activeMediaScore=-1;configuredMediaFrame="";pendingMediaFrame="";
                pendingMediaSince=0;mediaPromptShowing=false;mediaRejectedForPage=false;mediaApproved=false;controlsAllowedForVideo=true;
                runOnUiThread(()->{if(mediaController!=null){mediaController.setFullscreenActive(false);mediaController.update(false,false,false,0,0);}});
                return;
            }
            long now=System.currentTimeMillis();
            if(mediaApproved) {
                if(!frame.equals(activeMediaFrame)) return;
                activeMediaSeen=now;
                runOnUiThread(()->updateApprovedMedia(playing,waiting,position,duration,streamType,tracks,qualities,audioTracks));
                return;
            }
            if(!available || mediaRejectedForPage || duration>0&&duration<30 || score<180) return;
            if(!frame.equals(pendingMediaFrame)) {
                if(!pendingMediaFrame.isEmpty()&&score<pendingScore+100&&now-pendingMediaSince<3000)return;
                pendingMediaFrame=frame;pendingMediaSince=now;
            }
            pendingScore=score;pendingPlaying=playing;pendingWaiting=waiting;pendingPosition=position;pendingDuration=duration;
            pendingStreamUrl=streamUrl;pendingStreamType=streamType;pendingTracks=tracks;pendingQualities=qualities;pendingAudioTracks=audioTracks;
            if(now-pendingMediaSince>=1500 && !mediaPromptShowing) {
                mediaPromptShowing=true;
                runOnUiThread(MainActivity.this::showVideoDetectedPrompt);
            }
        }
        @JavascriptInterface public void stalled(String frame, String streamUrl) {
            if (!frame.equals(activeMediaFrame)) return;
            logDiagnostic("player","Playback stalled: "+streamUrl);
            runOnUiThread(() -> new AlertDialog.Builder(MainActivity.this).setTitle("Playback appears stalled")
                .setMessage("The video has not buffered for 12 seconds.").setNegativeButton("Keep waiting",null)
                .setPositiveButton("Reload page",(d,w)->bridge.getWebView().reload()).show());
        }
        @JavascriptInterface public void skipAvailable(String frame, boolean available, String label) {
            if(frame.equals(activeMediaFrame)) runOnUiThread(()->mediaController.setSkipAvailable(available,label));
        }
    }

    private void showVideoDetectedPrompt() {
        if(mediaApproved||mediaRejectedForPage||pendingMediaFrame.isEmpty()){mediaPromptShowing=false;return;}
        AlertDialog dialog=new AlertDialog.Builder(this)
            .setTitle("Video detected")
            .setMessage("DiON found a likely "+pendingStreamType+" video. Play it fullscreen with TV controls?")
            .setNegativeButton("Not now",(d,w)->{mediaRejectedForPage=true;mediaPromptShowing=false;})
            .setNeutralButton("Fullscreen only",(d,w)->{mediaPromptShowing=false;approvePendingVideo(true,false);})
            .setPositiveButton("Play + controls",(d,w)->{mediaPromptShowing=false;approvePendingVideo(true,true);})
            .create();
        dialog.setOnCancelListener(d->{mediaRejectedForPage=true;mediaPromptShowing=false;});
        dialog.show();
    }

    private void approvePendingVideo(boolean requestFullscreen,boolean showControls) {
        if(pendingMediaFrame.isEmpty()) return;
        mediaApproved=true;controlsAllowedForVideo=showControls;activeMediaFrame=pendingMediaFrame;activeMediaScore=pendingScore;activeMediaSeen=System.currentTimeMillis();
        updateApprovedMedia(pendingPlaying,pendingWaiting,pendingPosition,pendingDuration,pendingStreamType,pendingTracks,pendingQualities,pendingAudioTracks);
        if(fullscreenView!=null&&mediaController!=null)mediaController.setFullscreenActive(showControls);
        configuredMediaFrame=activeMediaFrame;
        float speed=getPreferences(MODE_PRIVATE).getFloat("media_speed",1f);
        String page=bridge.getWebView().getUrl();
        String cookies=CookieManager.getInstance().getCookie(pendingStreamUrl==null||pendingStreamUrl.isEmpty()?page:pendingStreamUrl);
        playbackStore.saveStream(page,pendingStreamUrl,pendingStreamType,"Cookie: "+(cookies==null?"":cookies)+"\nReferer: "+page,speed);
        sendMediaCommand("speed",speed);
        int subtitle=getPreferences(MODE_PRIVATE).getInt("media_subtitle",-1),audio=getPreferences(MODE_PRIVATE).getInt("media_audio",-1);
        if(subtitle>=0)sendMediaCommand("subtitle",subtitle);
        if(audio>=0)sendMediaCommand("audio",audio);
        if(requestFullscreen){sendMediaCommand("play",0);bridge.getWebView().postDelayed(()->sendMediaCommand("fullscreen",0),180);}
    }

    private void updateApprovedMedia(boolean playing,boolean waiting,double position,double duration,String type,String tracks,String qualities,String audio) {
        if(mediaController==null)return;
        mediaController.setMediaDetails(type,tracks,qualities,audio);
        mediaController.setNextAvailable(!playbackStore.nextFor(bridge.getWebView().getUrl()).isEmpty(),position,duration);
        mediaController.update(true,playing,waiting,position,duration);
    }

    private void installDocumentStartMediaDetection() {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return;
        try (InputStream input=getAssets().open("dion-media.js"); ByteArrayOutputStream output=new ByteArrayOutputStream()) {
            byte[] buffer=new byte[8192]; int count;
            while((count=input.read(buffer))!=-1) output.write(buffer,0,count);
            WebViewCompat.addDocumentStartJavaScript(bridge.getWebView(), output.toString("UTF-8"),
                java.util.Collections.singleton("*"));
        } catch(Exception ignored) {}
    }

    private void sendMediaCommand(String command, double value) {
        if("hideControls".equals(command)){if(mediaController!=null)mediaController.hideNow();return;}
        if("next".equals(command)) {
            String next=playbackStore.nextFor(bridge.getWebView().getUrl());
            if(!next.isEmpty()) bridge.getWebView().loadUrl(next);
            return;
        }
        String safe = command.replace("'", "");
        if ("speed".equals(command)) getPreferences(MODE_PRIVATE).edit().putFloat("media_speed",(float)value).apply();
        else if ("subtitle".equals(command)) getPreferences(MODE_PRIVATE).edit().putInt("media_subtitle",(int)value).apply();
        else if ("audio".equals(command)) getPreferences(MODE_PRIVATE).edit().putInt("media_audio",(int)value).apply();
        bridge.getWebView().evaluateJavascript(
            "window.__dionMediaCommand&&window.__dionMediaCommand('" + safe + "'," + value + ",'" + activeMediaFrame + "')", null);
    }

    private class TvSettingsBridge {
        @JavascriptInterface public float getPointerSize() { return pointerSize; }
        @JavascriptInterface public float getPointerSpeed() { return pointerSpeed; }
        @JavascriptInterface public int getSmoothness() { return pointerSmoothness; }
        @JavascriptInterface public boolean getPointerAcceleration() { return pointerAcceleration; }
        @JavascriptInterface public int getPointerStyle() { return pointerStyle; }
        @JavascriptInterface public boolean getFocusMode() { return focusMode; }
        @JavascriptInterface public int getSeekSeconds() { return seekSeconds; }
        @JavascriptInterface public boolean getBackgroundPlayback() { return backgroundPlayback; }
        @JavascriptInterface public boolean getKeepScreenAwake() { return keepScreenAwake; }
        @JavascriptInterface public String getWebsiteViewMode() { return websiteViewMode; }
        @JavascriptInterface public void setPointerSize(float value) {
            pointerSize = Math.max(.65f, Math.min(1.8f, value));
            getPreferences(MODE_PRIVATE).edit().putFloat("pointerSize", pointerSize).apply();
            runOnUiThread(() -> cursorView.setPointerScale(pointerSize));
        }
        @JavascriptInterface public void setPointerSpeed(float value) {
            pointerSpeed = Math.max(.5f, Math.min(2.2f, value));
            getPreferences(MODE_PRIVATE).edit().putFloat("pointerSpeed", pointerSpeed).apply();
        }
        @JavascriptInterface public void setSmoothness(int value) {
            pointerSmoothness = Math.max(0, Math.min(100, value));
            getPreferences(MODE_PRIVATE).edit().putInt("pointerSmoothness", pointerSmoothness).apply();
            runOnUiThread(() -> cursorView.setSmoothness(pointerSmoothness));
        }
        @JavascriptInterface public void setPointerAcceleration(boolean value) {
            pointerAcceleration=value;
            getPreferences(MODE_PRIVATE).edit().putBoolean("pointerAcceleration",value).apply();
        }
        @JavascriptInterface public void setPointerStyle(int value) {
            pointerStyle=Math.max(0,Math.min(2,value));
            getPreferences(MODE_PRIVATE).edit().putInt("pointerStyle",pointerStyle).apply();
            runOnUiThread(() -> cursorView.setStyle(pointerStyle));
        }
        @JavascriptInterface public void centerPointer() { runOnUiThread(() -> cursorView.center()); }
        @JavascriptInterface public void setFocusMode(boolean value) { runOnUiThread(()->{if(focusMode!=value)toggleNavigationMode();}); }
        @JavascriptInterface public void setSeekSeconds(int value) { seekSeconds=Math.max(5,Math.min(60,value));getPreferences(MODE_PRIVATE).edit().putInt("seekSeconds",seekSeconds).apply(); }
        @JavascriptInterface public void setBackgroundPlayback(boolean value) {
            backgroundPlayback = value;
            getPreferences(MODE_PRIVATE).edit().putBoolean("backgroundPlayback", value).apply();
        }
        @JavascriptInterface public void setKeepScreenAwake(boolean value) {
            keepScreenAwake = value;
            getPreferences(MODE_PRIVATE).edit().putBoolean("keepScreenAwake", value).apply();
            runOnUiThread(MainActivity.this::applyKeepAwake);
        }
        @JavascriptInterface public void setWebsiteViewMode(String value) {
            if (!"mobile".equals(value) && !"desktop".equals(value)) return;
            websiteViewMode = value;
            android.content.SharedPreferences preferences = getPreferences(MODE_PRIVATE);
            android.content.SharedPreferences.Editor editor = preferences.edit().putString("websiteViewMode", value);
            // The main layout selector is authoritative. Clear older per-site layout
            // overrides so they cannot make this button appear to do nothing.
            for (String key : preferences.getAll().keySet()) {
                if (key.startsWith("site_") && key.endsWith("_view")) editor.remove(key);
            }
            editor.apply();
            runOnUiThread(() -> applyWebsiteView(true));
        }
        @JavascriptInterface public void clearBrowserCache() {
            runOnUiThread(() -> {
                bridge.getWebView().clearCache(true);
                Toast.makeText(MainActivity.this, "Browser cache cleared", Toast.LENGTH_SHORT).show();
            });
        }
        @JavascriptInterface public void resetPointer() {
            pointerSize = 1f; pointerSpeed = 1f; pointerSmoothness = 42; pointerAcceleration = true; pointerStyle = 0;
            getPreferences(MODE_PRIVATE).edit().putFloat("pointerSize", 1f).putFloat("pointerSpeed", 1f)
                .putInt("pointerSmoothness", 42).putBoolean("pointerAcceleration", true).putInt("pointerStyle", 0)
                .putBoolean("focusMode", false).apply();
            runOnUiThread(() -> {
                cursorView.setPointerScale(1f); cursorView.setSmoothness(42); cursorView.setStyle(0);
                setNavigationMode(false);
            });
        }
    }

    private void applyKeepAwake() {
        if (keepScreenAwake) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void applyWebsiteView(boolean reload) {
        WebView webView = bridge.getWebView();
        String host = currentHost();
        String mode = host.isEmpty() ? websiteViewMode : getPreferences(MODE_PRIVATE)
            .getString("site_" + host + "_view", websiteViewMode);
        if ("desktop".equals(mode)) {
            webView.getSettings().setUserAgentString(desktopUserAgent());
            webView.getSettings().setUseWideViewPort(true);
            webView.getSettings().setLoadWithOverviewMode(true);
        } else {
            webView.getSettings().setUserAgentString(defaultUserAgent);
            // Keep wide viewport support enabled so Chromium honours the injected
            // fixed phone viewport instead of treating the TV panel as device-width.
            webView.getSettings().setUseWideViewPort(true);
            webView.getSettings().setLoadWithOverviewMode(false);
        }
        applyDocumentViewport(mode);
        boolean changed=!mode.equals(appliedWebsiteViewMode);
        appliedWebsiteViewMode=mode;
        if (reload || (changed&&!host.isEmpty())) webView.reload();
    }

    private String desktopUserAgent() {
        String version="131.0.0.0";
        int start=defaultUserAgent.indexOf("Chrome/");
        if(start>=0){int end=defaultUserAgent.indexOf(' ',start);version=defaultUserAgent.substring(start+7,end>start?end:defaultUserAgent.length());}
        return "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/"+version+" Safari/537.36";
    }

    private void applyDocumentViewport(String mode) {
        String content="desktop".equals(mode)
            ? "width=1280, initial-scale=1.0, user-scalable=yes"
            : "width=412, initial-scale=1.0, user-scalable=yes";
        bridge.getWebView().evaluateJavascript("(()=>{let m=document.querySelector('meta[name=viewport]');if(!m){m=document.createElement('meta');m.name='viewport';(document.head||document.documentElement).appendChild(m)}m.content='"+content+"';window.__dionApplyDisplayMode&&window.__dionApplyDisplayMode();window.dispatchEvent(new Event('resize'))})()",null);
    }

    private class ViewportBridge {
        @JavascriptInterface public String mode(String host) {
            String clean=host==null?"":host.toLowerCase();
            return clean.isEmpty()?websiteViewMode:getPreferences(MODE_PRIVATE).getString("site_"+clean+"_view",websiteViewMode);
        }
        @JavascriptInterface public int zoom(String host) {
            String clean=host==null?"":host.toLowerCase();
            String key=clean.isEmpty()?"zoom_default":"site_"+clean+"_zoom";
            return getPreferences(MODE_PRIVATE).getInt(key,100);
        }
    }

    private String currentHost() {
        try {
            String host = Uri.parse(bridge.getWebView().getUrl()).getHost();
            return host == null || "localhost".equals(host) ? "" : host.toLowerCase();
        } catch (Exception ignored) { return ""; }
    }

    private boolean siteBoolean(String name, boolean fallback) {
        String host = currentHost();
        return host.isEmpty() ? fallback : getPreferences(MODE_PRIVATE)
            .getBoolean("site_" + host + "_" + name, fallback);
    }

    private void showSiteSettings() {
        String host = currentHost();
        if (host.isEmpty()) {
            bridge.getWebView().loadUrl("http://localhost/#settings");
            return;
        }
        String prefix = "site_" + host + "_";
        boolean[] values = {
            getPreferences(MODE_PRIVATE).getBoolean(prefix + "adblock", true),
            getPreferences(MODE_PRIVATE).getBoolean(prefix + "popups", false),
            getPreferences(MODE_PRIVATE).getBoolean(prefix + "controls", true),
            "desktop".equals(getPreferences(MODE_PRIVATE).getString(prefix + "view", websiteViewMode))
        };
        String[] labels = {"Block ads on this site", "Allow popups/new pages in app",
            "Use DiON TV video controls", "Use desktop view"};
        new AlertDialog.Builder(this)
            .setTitle("Site settings · " + host)
            .setMultiChoiceItems(labels, values, (dialog, which, checked) -> values[which] = checked)
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Reset", (dialog, which) -> {
                getPreferences(MODE_PRIVATE).edit().remove(prefix + "adblock").remove(prefix + "popups")
                    .remove(prefix + "controls").remove(prefix + "view").remove(prefix + "zoom").apply();
                bridge.getWebView().reload();
            })
            .setPositiveButton("Save + reload", (dialog, which) -> {
                getPreferences(MODE_PRIVATE).edit()
                    .putBoolean(prefix + "adblock", values[0]).putBoolean(prefix + "popups", values[1])
                    .putBoolean(prefix + "controls", values[2])
                    .putString(prefix + "view", values[3] ? "desktop" : "mobile").apply();
                adBlockClient.setEnabled(adBlockEnabled && values[0]);
                applyWebsiteView(true);
            }).show();
    }

    private void dispatchMediaKey(int keyCode) {
        AudioManager audio = (AudioManager) getSystemService(AUDIO_SERVICE);
        audio.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, keyCode));
        audio.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, keyCode));
    }

    private class TvWebChromeClient extends BridgeWebChromeClient {
        TvWebChromeClient() { super(bridge); }
        @Override public void onShowCustomView(View view, CustomViewCallback callback) {
            if (fullscreenView != null) { callback.onCustomViewHidden(); return; }
            fullscreenView = view;
            fullscreenCallback = callback;
            if(!mediaApproved&&!pendingMediaFrame.isEmpty())approvePendingVideo(false,true);
            setChromeVisible(false);
            addContentView(view, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            view.setBackgroundColor(Color.BLACK);
            view.bringToFront();
            cursorView.setVisibility(View.VISIBLE);
            cursorView.reveal();
            if (mediaController != null) { mediaController.setFullscreenActive(mediaApproved&&controlsAllowedForVideo); mediaController.bringToFront(); }
            cursorView.bringToFront();
            enterImmersiveMode();
        }
        @Override public void onHideCustomView() {
            if (fullscreenView == null) return;
            ViewGroup parent = (ViewGroup) fullscreenView.getParent();
            if (parent != null) parent.removeView(fullscreenView);
            fullscreenView = null;
            if(mediaController!=null)mediaController.setFullscreenActive(false);
            if (fullscreenCallback != null) fullscreenCallback.onCustomViewHidden();
            fullscreenCallback = null;
            setChromeVisible(true);
            enterImmersiveMode();
        }
    }

    private void setChromeVisible(boolean visible) {
        if (!visible && explorePanel.getVisibility() == View.VISIBLE) hideExplorePanel();
        int state = visible ? View.VISIBLE : View.GONE;
        exploreButton.setVisibility(state);
        tabsButton.setVisibility(state);
        navigationModeButton.setVisibility(state);
        refreshButton.setVisibility(state);
        externalLinksToggle.setVisibility(state);
        adBlockToggle.setVisibility(state);
        zoomControls.setVisibility(state);
        cursorView.setVisibility(View.VISIBLE);
    }

    private class UpdateBridge {
        @JavascriptInterface public void downloadLatest() {
            runOnUiThread(() -> Toast.makeText(MainActivity.this, "Downloading latest DiON streamTV…", Toast.LENGTH_LONG).show());
            imageLoader.execute(() -> {
                File directory = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                if (directory == null) return;
                File target = new File(directory, "DiON-streamTV-latest.apk");
                try (InputStream input = new URL(UPDATE_URL).openStream(); FileOutputStream output = new FileOutputStream(target)) {
                    byte[] buffer = new byte[16384]; int count;
                    while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                    pendingUpdate = target;
                    runOnUiThread(() -> requestInstall(target));
                } catch (Exception error) {
                    target.delete();
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Update download failed", Toast.LENGTH_LONG).show());
                }
            });
        }
    }

    private void requestInstall(File apk) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !getPackageManager().canRequestPackageInstalls()) {
            Toast.makeText(this, "Allow DiON streamTV to install updates, then return", Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName())));
            return;
        }
        pendingUpdate = null;
        Uri apkUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", apk);
        Intent install = new Intent(Intent.ACTION_VIEW).setDataAndType(apkUri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(install);
    }

    @Override public void onResume() {
        super.onResume();
        if (!backgroundPlayback && bridge != null) {
            bridge.getWebView().onResume();
            bridge.getWebView().resumeTimers();
        }
        if (pendingUpdate != null && pendingUpdate.isFile() &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || getPackageManager().canRequestPackageInstalls())) requestInstall(pendingUpdate);
    }

    @Override public void onPause() {
        if (!backgroundPlayback && bridge != null) {
            bridge.getWebView().evaluateJavascript("document.querySelectorAll('video,audio').forEach(m=>m.pause())", null);
            dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE);
            bridge.getWebView().onPause();
            bridge.getWebView().pauseTimers();
        }
        super.onPause();
    }

    private void installBrandSplash() {
        LinearLayout splash = new LinearLayout(this);
        splash.setOrientation(LinearLayout.VERTICAL);
        splash.setGravity(Gravity.CENTER);
        splash.setBackgroundColor(0xff07090e);
        TextView logo = new TextView(this);
        logo.setText(""); logo.setGravity(Gravity.CENTER);
        logo.setBackgroundResource(com.streamtv.webview.R.drawable.dion_logo);
        splash.addView(logo, new LinearLayout.LayoutParams((int) dp(112), (int) dp(112)));
        TextView name = new TextView(this);
        name.setText("DiON streamTV"); name.setTextColor(Color.WHITE); name.setTextSize(34); name.setGravity(Gravity.CENTER);
        name.setPadding(0, (int) dp(20), 0, 0); splash.addView(name);
        TextView follow = new TextView(this);
        follow.setText("BY DIONJERRY  •  FOLLOW @DIONDEVS"); follow.setTextColor(0xff77a0ff); follow.setTextSize(15); follow.setGravity(Gravity.CENTER);
        follow.setPadding(0, (int) dp(12), 0, 0); splash.addView(follow);
        addContentView(splash, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        splash.bringToFront();
        splash.postDelayed(() -> splash.animate().alpha(0f).setDuration(450).withEndAction(() -> ((ViewGroup) splash.getParent()).removeView(splash)).start(), 1300);
    }

    private void installAdBlockToggle() {
        adBlockToggle = new TextView(this);
        adBlockToggle.setTextColor(Color.WHITE);
        adBlockToggle.setTextSize(14);
        adBlockToggle.setGravity(Gravity.CENTER);
        adBlockToggle.setPadding((int) dp(16), (int) dp(9), (int) dp(16), (int) dp(9));
        adBlockToggle.setClickable(false);
        adBlockToggle.setFocusable(false);
        adBlockToggle.setOnClickListener(v -> toggleAdBlock());
        updateAdBlockToggle();

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        );
        params.gravity = Gravity.TOP | Gravity.END;
        params.setMargins(0, (int) dp(16), (int) dp(20), 0);
        addContentView(adBlockToggle, params);
        nativeClickTargets.add(adBlockToggle);
        adBlockToggle.bringToFront();
        cursorView.bringToFront();
    }

    private void installZoomControls() {
        zoomControls = new LinearLayout(this);
        zoomControls.setOrientation(LinearLayout.HORIZONTAL);
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xee111827);
        background.setCornerRadius(dp(12));
        background.setStroke((int) dp(1), 0xff526075);
        zoomControls.setBackground(background);

        TextView zoomOut = makeZoomButton("−");
        zoomOut.setOnClickListener(v -> adjustZoom(-10));
        zoomLevel = makeZoomButton("100%");
        zoomLevel.setTextSize(13);
        zoomLevel.setOnClickListener(v -> setZoom(100, true));
        TextView zoomIn = makeZoomButton("+");
        zoomIn.setOnClickListener(v -> adjustZoom(10));
        zoomControls.addView(zoomOut);
        zoomControls.addView(zoomLevel);
        zoomControls.addView(zoomIn);
        nativeClickTargets.add(zoomOut);
        nativeClickTargets.add(zoomLevel);
        nativeClickTargets.add(zoomIn);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.BOTTOM | Gravity.END;
        params.setMargins(0, 0, (int) dp(20), (int) dp(18));
        addContentView(zoomControls, params);
        cursorView.bringToFront();
    }

    private String zoomPreferenceKey() {
        String host = currentHost();
        return host.isEmpty() ? "zoom_default" : "site_" + host + "_zoom";
    }

    private int savedZoom() {
        return getPreferences(MODE_PRIVATE).getInt(zoomPreferenceKey(),100);
    }

    private void adjustZoom(int delta) {
        setZoom(Math.max(50, Math.min(200, savedZoom() + delta)), true);
    }

    private void applySavedZoom() {
        setZoom(savedZoom(),false);
    }

    private void setZoom(int percent, boolean save) {
        percent = Math.max(50, Math.min(200, percent));
        WebView webView = bridge.getWebView();
        if (save) getPreferences(MODE_PRIVATE).edit().putInt(zoomPreferenceKey(), percent).apply();
        float scale=percent/100f;
        webView.evaluateJavascript("(()=>{document.documentElement.style.zoom='"+scale+"';window.dispatchEvent(new Event('resize'))})()",null);
        if (zoomLevel != null) zoomLevel.setText(percent + "%");
        if (save) Toast.makeText(this, "Zoom " + percent + "% · saved for this site", Toast.LENGTH_SHORT).show();
    }

    private TextView makeZoomButton(String label) {
        TextView button = new TextView(this);
        button.setText(label);
        button.setTextColor(Color.WHITE);
        button.setTextSize(22);
        button.setGravity(Gravity.CENTER);
        button.setPadding((int) dp(16), (int) dp(7), (int) dp(16), (int) dp(7));
        return button;
    }

    private void installExplorePanel() {
        exploreButton = makeToolbarPill("☰  LIBRARY", 0xee172033);
        FrameLayout.LayoutParams buttonParams = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        buttonParams.gravity = Gravity.TOP | Gravity.START;
        buttonParams.setMargins((int) dp(20), (int) dp(16), 0, 0);
        addContentView(exploreButton, buttonParams);
        exploreButton.setOnClickListener(v -> showExplorePanel());
        nativeClickTargets.add(exploreButton);

        explorePanel = new LinearLayout(this);
        explorePanel.setOrientation(LinearLayout.VERTICAL);
        explorePanel.setPadding((int) dp(32), (int) dp(28), (int) dp(32), (int) dp(28));
        GradientDrawable panelBackground = new GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0xff080b11, 0xff0d121c});
        panelBackground.setStroke((int) dp(1), 0xff263244);
        explorePanel.setBackground(panelBackground);
        explorePanel.setVisibility(View.GONE);

        exploreHeading = new TextView(this);
        exploreHeading.setText("My Library");
        exploreHeading.setTextColor(Color.WHITE);
        exploreHeading.setTextSize(30);
        exploreHeading.setPadding(0, 0, 0, (int) dp(4));
        explorePanel.addView(exploreHeading);

        exploreSubtitle = new TextView(this);
        exploreSubtitle.setText("Movies and episodes you have opened");
        exploreSubtitle.setTextColor(0xff8f9bad);
        exploreSubtitle.setTextSize(15);
        exploreSubtitle.setPadding(0, 0, 0, (int) dp(22));
        explorePanel.addView(exploreSubtitle);

        LinearLayout headerActions = new LinearLayout(this);
        headerActions.setOrientation(LinearLayout.HORIZONTAL);
        headerActions.setGravity(Gravity.START);

        TextView home = makePill("⌂  APP HOME", 0xff2457d6);
        home.setOnClickListener(v -> {
            hideExplorePanel();
            bridge.getWebView().loadUrl("http://localhost/");
        });
        headerActions.addView(home);
        nativeClickTargets.add(home);

        TextView refresh = makePill("↻  RELOAD", 0xff273244);
        refresh.setOnClickListener(v -> {
            hideExplorePanel();
            bridge.getWebView().reload();
        });
        LinearLayout.LayoutParams actionSpacing = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        actionSpacing.setMargins((int) dp(12), 0, 0, 0);
        headerActions.addView(refresh, actionSpacing);
        nativeClickTargets.add(refresh);

        TextView settings = makePill("⚙  SETTINGS", 0xff273244);
        settings.setOnClickListener(v -> {
            hideExplorePanel();
            bridge.getWebView().loadUrl("http://localhost/#settings");
        });
        headerActions.addView(settings, actionSpacing);
        nativeClickTargets.add(settings);

        TextView close = makePill("✕  CLOSE", 0xff273244);
        close.setOnClickListener(v -> hideExplorePanel());
        headerActions.addView(close, actionSpacing);
        nativeClickTargets.add(close);
        explorePanel.addView(headerActions);

        historyScroll = new ScrollView(this);
        historyScroll.setSmoothScrollingEnabled(true);
        historyList = new LinearLayout(this);
        historyList.setOrientation(LinearLayout.VERTICAL);
        historyScroll.addView(historyList);
        explorePanel.addView(historyScroll, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
            (int) dp(660), FrameLayout.LayoutParams.MATCH_PARENT);
        panelParams.gravity = Gravity.START;
        addContentView(explorePanel, panelParams);
        refreshHistory();
        cursorView.bringToFront();
    }

    private TextView makePill(String text, int color) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(Color.WHITE);
        view.setTextSize(14);
        view.setGravity(Gravity.CENTER);
        view.setPadding((int) dp(16), (int) dp(10), (int) dp(16), (int) dp(10));
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(dp(12));
        background.setStroke((int) dp(1), 0xff3c485a);
        view.setBackground(background);
        return view;
    }

    private TextView makeToolbarPill(String text, int color) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(0xfff8fafc);
        view.setTextSize(14);
        view.setGravity(Gravity.CENTER);
        view.setPadding((int) dp(16), (int) dp(9), (int) dp(16), (int) dp(9));
        styleToolbarPill(view, color);
        return view;
    }

    private void styleToolbarPill(TextView view, int color) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(dp(12));
        background.setStroke((int) dp(1), 0xff526075);
        view.setBackground(background);
    }

    private void showExplorePanel() {
        if (focusMode) setNavigationMode(false);
        currentFolder = null;
        refreshHistory();
        exploreButton.setVisibility(View.INVISIBLE);
        externalLinksToggle.setVisibility(View.INVISIBLE);
        adBlockToggle.setVisibility(View.INVISIBLE);
        zoomControls.setVisibility(View.INVISIBLE);
        explorePanel.setVisibility(View.VISIBLE);
        explorePanel.bringToFront();
        cursorView.bringToFront();
    }

    private void hideExplorePanel() {
        libraryScrollVelocity=0;
        libraryScrollRunning=false;
        inputHandler.removeCallbacks(libraryScrollFrame);
        explorePanel.setVisibility(View.GONE);
        exploreButton.setVisibility(View.VISIBLE);
        externalLinksToggle.setVisibility(View.VISIBLE);
        adBlockToggle.setVisibility(View.VISIBLE);
        zoomControls.setVisibility(View.VISIBLE);
    }

    private void refreshHistory() {
        if (historyList == null) return;
        for (int i = nativeClickTargets.size() - 1; i >= 0; i--) {
            View view = nativeClickTargets.get(i);
            if (view.getTag() != null && "history".equals(view.getTag())) nativeClickTargets.remove(i);
        }
        historyList.removeAllViews();
        List<JSONObject> items = playbackStore.items();
        if (items.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Videos you start watching will appear here.");
            empty.setTextColor(0xffaab1c0);
            empty.setTextSize(18);
            empty.setPadding(0, (int) dp(30), 0, 0);
            historyList.addView(empty);
            addRecentSites();
            return;
        }
        Map<String, List<JSONObject>> folders = groupIntoFolders(items);
        if (currentFolder == null) {
            exploreHeading.setText("My Library");
            exploreSubtitle.setText(items.size() + (items.size() == 1 ? " saved video" : " saved videos") + " · grouped by title");
            int continuing=0;
            for(JSONObject item:items) if(!item.optBoolean("watched")&&item.optDouble("position")>2) continuing++;
            TextView continueWatching=makePill("▶  CONTINUE WATCHING\n     "+continuing+" in progress",0xff1d4ed8);
            continueWatching.setGravity(Gravity.START|Gravity.CENTER_VERTICAL); continueWatching.setTag("history");
            continueWatching.setOnClickListener(v->{currentFolder="__continue";refreshHistory();}); addHistoryRow(continueWatching);
            TextView watchHistory=makePill("◷  WATCH HISTORY\n     "+items.size()+" opened videos",0xff273244);
            watchHistory.setGravity(Gravity.START|Gravity.CENTER_VERTICAL); watchHistory.setTag("history");
            watchHistory.setOnClickListener(v->{currentFolder="__history";refreshHistory();}); addHistoryRow(watchHistory);
            addRecentSites();
            TextView clearAll = makePill("CLEAR HISTORY", 0xff3a1c24);
            clearAll.setTag("history");
            clearAll.setOnClickListener(v -> confirmClearAll());
            addHistoryRow(clearAll);
            for (Map.Entry<String, List<JSONObject>> entry : folders.entrySet()) {
                String folderName = displayFolderName(entry.getKey(), entry.getValue());
                int count = entry.getValue().size();
                TextView folder = makePill("▰  " + folderName + "\n     " + count + (count == 1 ? " saved video" : " saved videos"), 0xff172238);
                folder.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
                folder.setTag("history");
                String folderKey = entry.getKey();
                folder.setOnClickListener(v -> {
                    currentFolder = folderKey;
                    refreshHistory();
                });
                addHistoryRow(folder);
            }
            return;
        }

        exploreHeading.setText(currentFolder.equals("__movies") ? "Movies" : currentFolder.equals("__continue") ? "Continue Watching" : currentFolder.equals("__history") ? "Watch History" : seriesName(currentFolder));
        List<JSONObject> selectedItems;
        if(currentFolder.equals("__history")) selectedItems=items;
        else if(currentFolder.equals("__continue")) { selectedItems=new ArrayList<>(); for(JSONObject item:items) if(!item.optBoolean("watched")&&item.optDouble("position")>2) selectedItems.add(item); }
        else selectedItems = folders.get(currentFolder);
        int selectedCount = selectedItems == null ? 0 : selectedItems.size();
        exploreSubtitle.setText(selectedCount + (selectedCount == 1 ? " saved video" : " saved videos"));
        TextView backToFolders = makePill("←  ALL FOLDERS", 0xff374151);
        backToFolders.setTag("history");
        backToFolders.setOnClickListener(v -> {
            currentFolder = null;
            refreshHistory();
        });
        addHistoryRow(backToFolders);

        if(!currentFolder.startsWith("__")) {
            TextView clearFolder = makePill("REMOVE FOLDER", 0xff3a1c24);
            clearFolder.setTag("history");
            clearFolder.setOnClickListener(v -> confirmRemoveFolder(currentFolder));
            addHistoryRow(clearFolder);
        }

        List<JSONObject> folderItems = selectedItems;
        if (folderItems == null) return;
        for (JSONObject item : folderItems) {
            String title = item.optString("title", "Untitled video");
            double position = item.optDouble("position", 0);
            double duration = item.optDouble("duration", 0);
            int percent = duration > 0 ? (int) Math.min(100, position * 100 / duration) : 0;
            String status = position > 1
                ? "Resume at " + formatTime(position) + "  •  " + percent + "%"
                : "Last visited  •  Open page";
            boolean watched = item.optBoolean("watched", percent >= 90);
            LinearLayout card = makeMediaCard(item.optString("url"), item.optString("poster"),
                (watched ? "✓  " : "▶  ") + title,
                (watched ? "Watched  •  " : "") + status,
                watched ? 0xff12382f : 0xff171e2a);
            card.setTag("history");
            card.setOnClickListener(v -> {
                hideExplorePanel();
                bridge.getWebView().loadUrl(item.optString("url"));
            });
            addHistoryView(card);
            nativeClickTargets.add(card);

            LinearLayout actions = new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            actions.setTag("history");
            TextView watchedButton = makePill(watched ? "UNWATCH" : "✓  WATCHED", 0xff273244);
            watchedButton.setTag("history");
            watchedButton.setOnClickListener(v -> playbackStore.toggleWatched(item.optString("url")));
            actions.addView(watchedButton);
            nativeClickTargets.add(watchedButton);

            String nextUrl = item.optString("nextUrl");
            if (!nextUrl.isEmpty()) {
                TextView next = makePill("NEXT EPISODE  →", 0xff2457d6);
                next.setTag("history");
                next.setOnClickListener(v -> {
                    hideExplorePanel();
                    bridge.getWebView().loadUrl(nextUrl);
                });
                actions.addView(next, horizontalSpacing());
                nativeClickTargets.add(next);
            }

            TextView remove = makePill("REMOVE", 0xff3a1c24);
            remove.setTag("history");
            remove.setOnClickListener(v -> playbackStore.remove(item.optString("url")));
            actions.addView(remove, horizontalSpacing());
            nativeClickTargets.add(remove);
            addHistoryView(actions);
        }
    }

    private void addRecentSites() {
        if (browserHistoryStore == null || historyList == null) return;
        JSONArray recent = browserHistoryStore.items();
        if (recent.length() == 0) return;
        TextView label = new TextView(this);
        label.setText("RECENTLY VISITED");
        label.setTextColor(0xff7f91ad);
        label.setTextSize(12);
        label.setPadding((int)dp(4),(int)dp(22),0,(int)dp(6));
        historyList.addView(label);
        Set<String> shownHosts = new HashSet<>();
        for (int i=0; i<recent.length() && shownHosts.size()<8; i++) {
            JSONObject entry=recent.optJSONObject(i);
            if(entry==null)continue;
            String host=entry.optString("host","");
            String url=entry.optString("url","");
            if(host.isEmpty()||url.isEmpty()||!shownHosts.add(host))continue;
            String title=entry.optString("title",host);
            TextView site=makePill("⌖  "+title+"\n     "+host,0xff142034);
            site.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
            site.setMaxLines(2);
            site.setTag("history");
            site.setOnClickListener(v->{hideExplorePanel();bridge.getWebView().loadUrl(url);});
            addHistoryRow(site);
        }
    }

    private String watchUrl(Intent intent) {
        try {
            Uri data=intent==null?null:intent.getData();
            if(data!=null&&"dionstreamtv".equals(data.getScheme())&&"watch".equals(data.getHost())) {
                String url=data.getQueryParameter("url");
                if(url!=null&&(url.startsWith("https://")||url.startsWith("http://")))return url;
            }
        } catch(Exception ignored){}
        return "";
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String url=watchUrl(intent);
        if(!url.isEmpty()&&bridge!=null)bridge.getWebView().loadUrl(url);
    }

    private LinearLayout makeMediaCard(String pageUrl, String posterUrl, String title, String status, int color) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding((int) dp(12), (int) dp(12), (int) dp(18), (int) dp(12));
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(dp(18));
        background.setStroke((int) dp(1), 0xff475569);
        card.setBackground(background);

        ImageView poster = new ImageView(this);
        poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
        GradientDrawable placeholder = new GradientDrawable();
        placeholder.setColor(0xff0f172a);
        placeholder.setCornerRadius(dp(12));
        poster.setBackground(placeholder);
        card.addView(poster, new LinearLayout.LayoutParams((int) dp(92), (int) dp(132)));
        loadPoster(poster, pageUrl, posterUrl);

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding((int) dp(18), 0, 0, 0);
        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextColor(Color.WHITE);
        titleView.setTextSize(20);
        titleView.setMaxLines(2);
        copy.addView(titleView);
        TextView statusView = new TextView(this);
        statusView.setText(status);
        statusView.setTextColor(0xffcbd5e1);
        statusView.setTextSize(15);
        statusView.setPadding(0, (int) dp(9), 0, 0);
        copy.addView(statusView);
        card.addView(copy, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        return card;
    }

    private void loadPoster(ImageView view, String pageUrl, String posterUrl) {
        java.io.File cached = playbackStore.cachedPoster(pageUrl);
        if (cached != null) {
            android.graphics.Bitmap bitmap = BitmapFactory.decodeFile(cached.getAbsolutePath());
            if (bitmap != null) { view.setImageBitmap(bitmap); return; }
        }
        if (posterUrl == null || !posterUrl.startsWith("https://")) return;
        imageLoader.execute(() -> {
            try {
                java.net.URLConnection connection = new URL(posterUrl).openConnection();
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                connection.setRequestProperty("User-Agent", "StreamTV/1.0");
                android.graphics.Bitmap bitmap = BitmapFactory.decodeStream(connection.getInputStream());
                if (bitmap != null) runOnUiThread(() -> view.setImageBitmap(bitmap));
            } catch (Exception ignored) {}
        });
    }

    @Override
    public void onDestroy() {
        imageLoader.shutdownNow();
        super.onDestroy();
    }

    private LinearLayout.LayoutParams horizontalSpacing() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins((int) dp(12), 0, 0, 0);
        return params;
    }

    private void confirmClearAll() {
        new AlertDialog.Builder(this)
            .setTitle("Clear all viewing history?")
            .setMessage("This removes every saved movie, series and playback position.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Clear all", (dialog, which) -> {
                playbackStore.clear();
                currentFolder = null;
            }).show();
    }

    private void confirmRemoveFolder(String folder) {
        String label = folder.equals("__movies") ? "all movies" : seriesName(folder);
        new AlertDialog.Builder(this)
            .setTitle("Remove " + label + "?")
            .setMessage("Saved entries and playback positions in this folder will be deleted.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Remove", (dialog, which) -> {
                if (folder.equals("__movies")) playbackStore.removeFolder("/movie/");
                else if (folder.startsWith("anime/")) {
                    String id = folder.substring(6);
                    playbackStore.removeFolder("/play/" + id + "/");
                    playbackStore.removeFolder("/anime/" + id);
                } else playbackStore.removeFolder("/" + folder + "/");
                currentFolder = null;
            }).show();
    }

    private void addHistoryRow(TextView row) {
        addHistoryView(row);
    }

    private void addHistoryView(View row) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, (int) dp(14), 0, 0);
        historyList.addView(row, params);
        if (row instanceof TextView) nativeClickTargets.add(row);
    }

    private Map<String, List<JSONObject>> groupIntoFolders(List<JSONObject> items) {
        Map<String, List<JSONObject>> folders = new LinkedHashMap<>();
        for (JSONObject item : items) {
            String url = item.optString("url");
            String key = folderKey(url);
            if (!folders.containsKey(key)) folders.put(key, new ArrayList<>());
            folders.get(key).add(item);
        }
        return folders;
    }

    private String folderKey(String url) {
        try {
            Uri parsed = Uri.parse(url);
            List<String> segments = parsed.getPathSegments();
            String host = parsed.getHost();
            if (host != null && host.contains("animepahe") && segments.size() >= 2) {
                if ("play".equals(segments.get(0)) || "anime".equals(segments.get(0)))
                    return "anime/" + segments.get(1);
            }
            if (segments.size() >= 2 && "tv".equals(segments.get(0))) {
                return "tv/" + segments.get(1);
            }
        } catch (Exception ignored) {}
        return "__movies";
    }

    private String seriesName(String folderKey) {
        String slug = folderKey.startsWith("tv/") ? folderKey.substring(3) :
            (folderKey.startsWith("anime/") ? folderKey.substring(6) : folderKey);
        slug = slug.replaceFirst("^[a-zA-Z0-9]+-", "").replace('-', ' ');
        StringBuilder name = new StringBuilder();
        for (String word : slug.split(" ")) {
            if (word.isEmpty()) continue;
            if (name.length() > 0) name.append(' ');
            name.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return name.length() == 0 ? "TV Series" : name.toString();
    }

    private String displayFolderName(String key, List<JSONObject> entries) {
        if (key.equals("__movies")) return "Movies";
        if (key.startsWith("anime/") && entries != null && !entries.isEmpty()) {
            String title = entries.get(0).optString("title", "");
            title = title.replaceFirst("(?i)\\s*[-–|:]?\\s*(episode|ep)\\.?\\s*\\d+.*$", "")
                .replaceFirst("(?i)\\s*[-|]\\s*animepahe.*$", "").trim();
            if (!title.isEmpty() && !title.equalsIgnoreCase("animepahe")) return title;
        }
        return seriesName(key);
    }

    private String formatTime(double seconds) {
        int total = (int) seconds;
        return String.format("%d:%02d", total / 60, total % 60);
    }

    private void injectPlaybackTracker() {
        WebView webView = bridge.getWebView();
        String url = webView.getUrl();
        if (url == null) return;
        double resumeAt = playbackStore.positionFor(url);
        boolean customControls = false;
        String script = "(() => {" +
            "if(window.__tvTrackerInstalled)return;window.__tvTrackerInstalled=true;" +
            "const title=()=>document.querySelector('meta[property=\\\"og:title\\\"]')?.content||document.title||'Untitled video';" +
            "const poster=()=>document.querySelector('meta[property=\\\"og:image\\\"]')?.content||document.querySelector('video')?.poster||'';" +
            "const pageUrl=()=>{const isEpisode=/\\/tv\\/[^/]+\\/season\\/\\d+\\/episode\\/\\d+/.test(location.pathname)||location.pathname.startsWith('/play/');const c=isEpisode?location.href:document.querySelector('link[rel=\\\"canonical\\\"]')?.href;const u=new URL(c||location.href);u.search='';u.hash='';return u.href.replace(/\\/$/,'')};" +
            "const nextUrl=()=>Array.from(document.querySelectorAll('a[href*=\\\"/episode/\\\"]')).find(a=>/^\\s*Next/i.test(a.textContent||''))?.href||'';" +
            "if(location.pathname.startsWith('/movie/')||location.pathname.startsWith('/tv/')||location.pathname.startsWith('/anime/')||location.pathname.startsWith('/play/'))PlaybackTracker.visit(pageUrl(),title(),poster(),nextUrl());" +
            "const bind=v=>{if(v.__tvBound)return;v.__tvBound=true;" +
            "const save=()=>{if(isFinite(v.duration))PlaybackTracker.save(pageUrl(),title(),poster(),v.currentTime,v.duration)};" +
            "v.addEventListener('timeupdate',()=>{if(!v.__lastSave||Date.now()-v.__lastSave>5000){v.__lastSave=Date.now();save()}});" +
            "v.addEventListener('pause',save);v.addEventListener('ended',save);" +
            "v.addEventListener('loadedmetadata',()=>{const t=" + resumeAt + ";if(t>5&&t<v.duration-10&&v.currentTime<3)v.currentTime=t},{once:true});};" +
            "const scan=()=>document.querySelectorAll('video').forEach(bind);scan();" +
            "new MutationObserver(scan).observe(document.documentElement,{childList:true,subtree:true});" +
            (customControls ? "let active=null,lastRate=1;" +
            "const videos=()=>{const out=[...document.querySelectorAll('video')];document.querySelectorAll('iframe').forEach(f=>{try{out.push(...f.contentDocument.querySelectorAll('video'))}catch(e){}});return out};" +
            "const pick=()=>videos().find(v=>!v.paused&&!v.ended)||videos().find(v=>v.currentTime>0)||videos()[0]||null;" +
            "const report=()=>{active=pick();if(!active){DionMedia.state(false,false,false,0,0);return}DionMedia.state(true,!active.paused&&!active.ended,active.readyState<3&&!active.paused,active.currentTime||0,isFinite(active.duration)?active.duration:0)};" +
            "window.__dionMediaCommand=(cmd,val)=>{active=pick();if(!active){document.querySelectorAll('iframe').forEach(f=>{try{f.contentWindow.postMessage({__dionMedia:true,cmd,val},'*')}catch(e){}});return;}" +
            "if(cmd==='toggle')active.paused?active.play():active.pause();else if(cmd==='rewind')active.currentTime=Math.max(0,active.currentTime-val);else if(cmd==='forward')active.currentTime=Math.min(active.duration||Infinity,active.currentTime+val);else if(cmd==='mute')active.muted=!active.muted;else if(cmd==='speed'){lastRate=lastRate>=2?.75:lastRate+.25;active.playbackRate=lastRate}else if(cmd==='fullscreen'){(active.requestFullscreen||active.webkitRequestFullscreen)?.call(active)}};" +
            "addEventListener('message',e=>{if(e.data&&e.data.__dionMedia)window.__dionMediaCommand(e.data.cmd,e.data.val)});" +
            "['play','playing','pause','waiting','seeking','seeked','loadedmetadata','durationchange','ended'].forEach(n=>document.addEventListener(n,report,true));" +
            "setInterval(report,750);report();" : "") +
            "})();";
        webView.evaluateJavascript(script, null);
    }

    private void toggleAdBlock() {
        adBlockEnabled = !adBlockEnabled;
        adBlockClient.setEnabled(adBlockEnabled);
        getPreferences(MODE_PRIVATE).edit().putBoolean("adBlockEnabled", adBlockEnabled).apply();
        updateAdBlockToggle();
        Toast.makeText(this, adBlockEnabled ? "Ad blocker enabled" : "Ad blocker disabled", Toast.LENGTH_SHORT).show();
        bridge.getWebView().reload();
    }

    private void updateAdBlockToggle() {
        adBlockToggle.setText(adBlockEnabled ? "ADS · BLOCKED" : "ADS · ALLOWED");
        styleToolbarPill(adBlockToggle, adBlockEnabled ? 0xee12643a : 0xee374151);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (cursorView == null) {
            return super.dispatchKeyEvent(event);
        }

        boolean centerKey = event.getKeyCode() == KeyEvent.KEYCODE_DPAD_CENTER ||
            event.getKeyCode() == KeyEvent.KEYCODE_ENTER || event.getKeyCode() == KeyEvent.KEYCODE_NUMPAD_ENTER;
        if (centerKey && event.getAction() == KeyEvent.ACTION_UP) {
            inputHandler.removeCallbacks(beginCursorDrag);
            centerHeld = false;
            if (cursorDragging) {
                dispatchCursorTouch(MotionEvent.ACTION_UP);
                cursorDragging = false;
            } else if (fullscreenView != null) {
                toggleFullscreenPlayback();
            } else if(focusMode) bridge.getWebView().evaluateJavascript("document.activeElement&&document.activeElement.click()",null);
            else clickAtCursor();
            return true;
        }
        if (event.getAction() != KeyEvent.ACTION_DOWN) {
            // Never leak a D-pad key-up into WebView after handling its key-down.
            // WebView's built-in navigation/scrolling would otherwise fight the
            // native pointer and can make the page bounce in both directions.
            int releasedKey = event.getKeyCode();
            if (releasedKey == KeyEvent.KEYCODE_DPAD_UP || releasedKey == KeyEvent.KEYCODE_DPAD_DOWN ||
                releasedKey == KeyEvent.KEYCODE_DPAD_LEFT || releasedKey == KeyEvent.KEYCODE_DPAD_RIGHT) return true;
            return super.dispatchKeyEvent(event);
        }

        float acceleration = pointerAcceleration ? 1f + Math.min(1.25f, event.getRepeatCount() * .09f) : 1f;
        float step = dp(25 * pointerSpeed * acceleration);

        if(focusMode && event.getKeyCode()>=KeyEvent.KEYCODE_DPAD_UP&&event.getKeyCode()<=KeyEvent.KEYCODE_DPAD_RIGHT) {
            String dir=event.getKeyCode()==KeyEvent.KEYCODE_DPAD_UP?"up":event.getKeyCode()==KeyEvent.KEYCODE_DPAD_DOWN?"down":event.getKeyCode()==KeyEvent.KEYCODE_DPAD_LEFT?"left":"right";
            bridge.getWebView().evaluateJavascript("window.__dionFocusMove&&window.__dionFocusMove('"+dir+"')",null); return true;
        }

        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if(fullscreenView!=null&&event.getRepeatCount()>=5&&event.getRepeatCount()%5==0){sendMediaCommand("rewind",seekSeconds*3);return true;}
                cursorView.smoothMoveBy(-step, 0);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if(fullscreenView!=null&&event.getRepeatCount()>=5&&event.getRepeatCount()%5==0){sendMediaCommand("forward",seekSeconds*3);return true;}
                cursorView.smoothMoveBy(step, 0);
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                cursorView.smoothMoveBy(0, -step);
                if (explorePanel.getVisibility() == View.VISIBLE) {
                    scrollLibraryIfPointerAtEdge(-1);
                    return true;
                }
                if (cursorView.getCursorY() <= dp(42)) beginEdgeScroll(-1);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                cursorView.smoothMoveBy(0, step);
                if (explorePanel.getVisibility() == View.VISIBLE) {
                    scrollLibraryIfPointerAtEdge(1);
                    return true;
                }
                if (cursorView.getCursorY() >= cursorView.getHeight() - dp(42)) beginEdgeScroll(1);
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                if (event.getRepeatCount() == 0) {
                    centerHeld = true;
                    inputHandler.postDelayed(beginCursorDrag, 520);
                }
                return true;
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                sendMediaCommand("rewind", seekSeconds);
                return true;
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                sendMediaCommand("forward", seekSeconds);
                return true;
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                if (fullscreenView != null) toggleFullscreenPlayback();
                else sendMediaCommand("toggle", 0);
                return true;
            case KeyEvent.KEYCODE_MENU:
                toggleAdBlock();
                return true;
            case KeyEvent.KEYCODE_SETTINGS:
                showSiteSettings();
                return true;
            case KeyEvent.KEYCODE_PROG_BLUE:
                toggleNavigationMode(); return true;
            case KeyEvent.KEYCODE_PROG_GREEN:
                bridge.getWebView().reload(); return true;
            default:
                return super.dispatchKeyEvent(event);
        }
    }

    private void sendHover() {
        if (cursorDragging) {
            dispatchCursorTouch(MotionEvent.ACTION_MOVE);
            return;
        }
        if(fullscreenView!=null&&mediaController!=null)mediaController.reveal();
        WebView webView = bridge.getWebView();
        float[] point = webViewPoint(webView);
        long now = SystemClock.uptimeMillis();
        MotionEvent hover = MotionEvent.obtain(now, now, MotionEvent.ACTION_HOVER_MOVE, point[0], point[1], 0);
        hover.setSource(android.view.InputDevice.SOURCE_MOUSE);
        if (fullscreenView != null) fullscreenView.dispatchGenericMotionEvent(hover);
        else webView.dispatchGenericMotionEvent(hover);
        hover.recycle();
    }

    private void toggleFullscreenPlayback() {
        if (mediaController == null || !mediaController.hasMedia()) {
            dispatchFullscreenMediaKey();
            return;
        }
        final boolean wasPlaying = mediaController.isPlaying();
        sendMediaCommand(wasPlaying ? "pause" : "play", 0);
        // Cross-origin or native fullscreen players may not receive the injected
        // command. Fall back only when the reported state did not change, which
        // prevents a successful pause from immediately being toggled back on.
        bridge.getWebView().postDelayed(() -> {
            if (fullscreenView != null && mediaController != null &&
                mediaController.hasMedia() && mediaController.isPlaying() == wasPlaying) {
                dispatchFullscreenMediaKey();
            }
        }, 320);
    }

    private void dispatchFullscreenMediaKey() {
        long now=SystemClock.uptimeMillis();
        KeyEvent down=new KeyEvent(now,now,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,0);
        KeyEvent up=new KeyEvent(now,now,KeyEvent.ACTION_UP,KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,0);
        boolean handled=fullscreenView!=null&&fullscreenView.dispatchKeyEvent(down);
        if(fullscreenView!=null)fullscreenView.dispatchKeyEvent(up);
        if(!handled)dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
    }

    private void dispatchCursorTouch(int action) {
        WebView webView = bridge.getWebView();
        float[] point = webViewPoint(webView);
        long now = SystemClock.uptimeMillis();
        MotionEvent touch = MotionEvent.obtain(now, now, action, point[0], point[1], 0);
        webView.dispatchTouchEvent(touch);
        touch.recycle();
    }

    private void sendHoverExit() {
        WebView webView = bridge.getWebView();
        float[] point = webViewPoint(webView);
        long now = SystemClock.uptimeMillis();
        MotionEvent exit = MotionEvent.obtain(now, now, MotionEvent.ACTION_HOVER_EXIT, point[0], point[1], 0);
        exit.setSource(android.view.InputDevice.SOURCE_MOUSE);
        if (fullscreenView != null) fullscreenView.dispatchGenericMotionEvent(exit);
        else webView.dispatchGenericMotionEvent(exit);
        exit.recycle();
        webView.evaluateJavascript(
            "document.querySelectorAll('video').forEach(v=>v.dispatchEvent(new MouseEvent('mouseleave',{bubbles:true})))",
            null
        );
    }

    private void beginEdgeScroll(int direction) {
        if (edgeScrollVelocity != 0 && Math.signum(edgeScrollVelocity) != direction) edgeScrollVelocity = 0;
        edgeScrollVelocity = Math.max(-1.15f, Math.min(1.15f, edgeScrollVelocity + direction * .34f));
        edgeScrollInputTime = SystemClock.uptimeMillis();
        if (!edgeScrollRunning) {
            edgeScrollRunning = true;
            inputHandler.post(edgeScrollFrame);
        }
    }

    private void scrollLibraryIfPointerAtEdge(int direction) {
        if(historyScroll==null||historyScroll.getVisibility()!=View.VISIBLE)return;
        int[] cursorLocation=new int[2];
        cursorView.getLocationOnScreen(cursorLocation);
        float screenX=cursorView.getCursorX()+cursorLocation[0];
        float screenY=cursorView.getCursorY()+cursorLocation[1];
        Rect bounds=new Rect();
        if(!historyScroll.getGlobalVisibleRect(bounds)||screenX<bounds.left||screenX>bounds.right)return;
        boolean atEdge=direction>0 ? screenY>=bounds.bottom-dp(48) : screenY<=bounds.top+dp(48);
        if(!atEdge)return;
        if(libraryScrollVelocity!=0&&Math.signum(libraryScrollVelocity)!=direction)libraryScrollVelocity=0;
        libraryScrollVelocity=Math.max(-dp(12),Math.min(dp(12),libraryScrollVelocity+direction*dp(2.6f)));
        libraryScrollInputTime=SystemClock.uptimeMillis();
        if(!libraryScrollRunning){libraryScrollRunning=true;inputHandler.post(libraryScrollFrame);}
    }

    private void dispatchMouseWheel(float velocity) {
        WebView webView = bridge.getWebView();
        float[] point = webViewPoint(webView);
        MotionEvent.PointerProperties properties = new MotionEvent.PointerProperties();
        properties.id = 0;
        properties.toolType = MotionEvent.TOOL_TYPE_MOUSE;
        MotionEvent.PointerCoords coordinates = new MotionEvent.PointerCoords();
        coordinates.x = point[0];
        coordinates.y = point[1];
        // Positive AXIS_VSCROLL means wheel-up, so down uses a negative value.
        coordinates.setAxisValue(MotionEvent.AXIS_VSCROLL, -velocity);
        long now = SystemClock.uptimeMillis();
        MotionEvent wheel = MotionEvent.obtain(now, now, MotionEvent.ACTION_SCROLL, 1,
            new MotionEvent.PointerProperties[]{properties}, new MotionEvent.PointerCoords[]{coordinates},
            0, 0, 1f, 1f, 0, 0, android.view.InputDevice.SOURCE_MOUSE, 0);
        if (fullscreenView != null) fullscreenView.dispatchGenericMotionEvent(wheel);
        else webView.dispatchGenericMotionEvent(wheel);
        wheel.recycle();
    }

    private void clickAtCursor() {
        int[] cursorLocation = new int[2];
        cursorView.getLocationOnScreen(cursorLocation);
        int screenX = Math.round(cursorView.getCursorX() + cursorLocation[0]);
        int screenY = Math.round(cursorView.getCursorY() + cursorLocation[1]);
        if (mediaController != null && mediaController.clickAt(screenX, screenY)) return;
        for (int i = nativeClickTargets.size() - 1; i >= 0; i--) {
            View target = nativeClickTargets.get(i);
            if (target.getVisibility() != View.VISIBLE || !target.isShown()) continue;
            Rect bounds = new Rect();
            target.getGlobalVisibleRect(bounds);
            if (bounds.contains(screenX, screenY)) {
                target.performClick();
                return;
            }
        }
        WebView webView = bridge.getWebView();
        float[] point = webViewPoint(webView);
        long now = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, point[0], point[1], 0);
        MotionEvent up = MotionEvent.obtain(now, now + 60, MotionEvent.ACTION_UP, point[0], point[1], 0);
        webView.dispatchTouchEvent(down);
        webView.dispatchTouchEvent(up);
        down.recycle();
        up.recycle();
    }

    private float[] webViewPoint(WebView webView) {
        int[] webLocation = new int[2];
        int[] cursorLocation = new int[2];
        webView.getLocationOnScreen(webLocation);
        cursorView.getLocationOnScreen(cursorLocation);
        return new float[] {
            cursorView.getCursorX() + cursorLocation[0] - webLocation[0],
            cursorView.getCursorY() + cursorLocation[1] - webLocation[1]
        };
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) enterImmersiveMode();
    }

    private void enterImmersiveMode() {
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
    }
}
