package com.streamtv.webview;

import android.net.Uri;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebResourceError;
import android.webkit.RenderProcessGoneDetail;

import com.getcapacitor.Bridge;
import com.getcapacitor.BridgeWebViewClient;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class AdBlockWebViewClient extends BridgeWebViewClient {
    public interface ExternalNavigationHandler {
        void onExternalNavigation(Uri uri);
    }
    public interface RecoveryHandler { void onRendererGone(); }

    private static final Set<String> BLOCKED_HOSTS = new HashSet<>(Arrays.asList(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com",
        "adservice.google.com", "amazon-adsystem.com", "adsrvr.org",
        "adnxs.com", "advertising.com", "taboola.com", "outbrain.com",
        "popads.net", "popcash.net", "propellerads.com", "exoclick.com",
        "trafficjunky.net", "juicyads.com", "revcontent.com", "mgid.com",
        "scorecardresearch.com", "zedo.com", "histats.com"
    ));

    private boolean enabled;
    private final Runnable pageFinishedCallback;
    private final ExternalNavigationHandler externalNavigationHandler;
    private final RecoveryHandler recoveryHandler;
    private int blockedRequests;

    public AdBlockWebViewClient(Bridge bridge, boolean enabled, Runnable pageFinishedCallback,
                                ExternalNavigationHandler externalNavigationHandler, RecoveryHandler recoveryHandler) {
        super(bridge);
        this.enabled = enabled;
        this.pageFinishedCallback = pageFinishedCallback;
        this.externalNavigationHandler = externalNavigationHandler;
        this.recoveryHandler = recoveryHandler;
    }

    @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
        super.onReceivedError(view, request, error);
        if (request.isForMainFrame()) {
            String target=request.getUrl().toString().replace("'","%27");
            String message=error.getDescription()==null?"Page could not be loaded":error.getDescription().toString();
            view.loadDataWithBaseURL(request.getUrl().toString(), "<meta name='viewport' content='width=device-width'><body style='margin:0;background:#07090e;color:white;font-family:sans-serif;display:grid;place-items:center;min-height:100vh'><main style='text-align:center;max-width:650px'><div style='font-size:55px'>⌁</div><h1>You’re offline</h1><p style='color:#94a3b8'>"+message+"</p><button onclick=\"location.href='"+target+"'\" style='padding:16px 28px;border:0;border-radius:14px;background:#245bd8;color:white;font-size:18px'>Try again</button></main></body>","text/html","UTF-8",null);
        }
    }

    @Override public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
        if(recoveryHandler!=null) recoveryHandler.onRendererGone();
        return true;
    }

    @Override
    public void onPageFinished(WebView view, String url) {
        super.onPageFinished(view, url);
        if (pageFinishedCallback != null) pageFinishedCallback.run();
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
        if (enabled && !request.isForMainFrame() && isBlocked(request.getUrl())) {
            blockedRequests++;
            return new WebResourceResponse("text/plain", "UTF-8", new ByteArrayInputStream(new byte[0]));
        }
        return super.shouldInterceptRequest(view, request);
    }
    public int getBlockedRequests(){return blockedRequests;}

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        if (request.isForMainFrame() && !isAllowedNavigation(view, request.getUrl())) {
            if (externalNavigationHandler != null) externalNavigationHandler.onExternalNavigation(request.getUrl());
            return true;
        }
        return super.shouldOverrideUrlLoading(view, request);
    }

    private boolean isStreamImdb(Uri uri) {
        String host = uri.getHost();
        return host != null && (host.equals("streamimdb.ru") || host.endsWith(".streamimdb.ru"));
    }

    private boolean isAllowedNavigation(WebView view, Uri destination) {
        String host = destination.getHost();
        if (host == null) return false;
        host = host.toLowerCase();
        if (isStreamImdb(destination) || host.equals("animepahe.com") || host.endsWith(".animepahe.com") ||
            host.equals("animepahe.org") || host.endsWith(".animepahe.org") ||
            host.equals("animepahe.pw") || host.endsWith(".animepahe.pw") ||
            host.equals("google.com") || host.endsWith(".google.com")) return true;
        try {
            Uri current = Uri.parse(view.getUrl());
            String currentHost = current.getHost();
            if (currentHost == null || currentHost.equals("localhost")) return true;
            return currentHost.equalsIgnoreCase(host);
        } catch (Exception ignored) { return false; }
    }

    private boolean isBlocked(Uri uri) {
        String host = uri.getHost();
        if (host == null) return false;
        host = host.toLowerCase();
        for (String blocked : BLOCKED_HOSTS) {
            if (host.equals(blocked) || host.endsWith("." + blocked)) return true;
        }
        return false;
    }
}
