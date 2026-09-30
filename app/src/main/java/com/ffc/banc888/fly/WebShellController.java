package com.ffc.banc888.fly;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;
import android.webkit.ConsoleMessage;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Toast;

import androidx.webkit.WebViewAssetLoader;
import androidx.webkit.WebViewClientCompat;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Locale;

final class WebShellController {
    private static final String TAG = "BANC888-WebView";

    private final Activity activity;
    private final WebView webView;
    private final DevLiveManager devLive;
    private final NativeSession session;

    private volatile boolean trustedMainFrame = false;
    private volatile boolean runtimeReady = false;
    private volatile String currentMainFrameUrl = "";
    private volatile long pageStartedAt = 0L;
    private volatile long pageFinishedAt = 0L;
    private volatile long lastHeartbeatAt = 0L;
    private volatile int jsErrorCount = 0;
    private volatile int longTaskCount = 0;
    private volatile int pageProgress = 0;

    WebShellController(Activity activity, WebView webView, DevLiveManager devLive, NativeSession session) {
        this.activity = activity;
        this.webView = webView;
        this.devLive = devLive;
        this.session = session;
    }

    boolean isTrustedMainFrame() {
        return trustedMainFrame;
    }

    void configure(BridgeRegistry bridges) {
        boolean debug = (activity.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        WebView.setWebContentsDebuggingEnabled(debug);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setSupportMultipleWindows(false);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setLoadsImagesAutomatically(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setTextZoom(100);
        s.setSaveFormData(false);
        s.setGeolocationEnabled(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        if (Build.VERSION.SDK_INT >= 26) s.setSafeBrowsingEnabled(true);

        webView.setBackgroundColor(0xff050a13);
        webView.setOverScrollMode(WebView.OVER_SCROLL_NEVER);

        bridges.install();
        installMessageBridge();

        WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .setDomain(AppConfig.APP_HOST)
                .setHttpAllowed(false)
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(activity))
                .addPathHandler("/live/", new WebViewAssetLoader.InternalStoragePathHandler(activity, devLive.root()))
                .build();

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                pageProgress = Math.max(0, Math.min(100, newProgress));
                if (pageProgress == 100) pageFinishedAt = SystemClock.elapsedRealtime();
                super.onProgressChanged(view, newProgress);
            }

            @Override
            public boolean onConsoleMessage(ConsoleMessage message) {
                if (message == null) return true;
                int priority = message.messageLevel() == ConsoleMessage.MessageLevel.ERROR ? Log.ERROR
                        : message.messageLevel() == ConsoleMessage.MessageLevel.WARNING ? Log.WARN
                        : Log.DEBUG;
                Log.println(priority, TAG,
                        "JS " + message.messageLevel() + " " + message.sourceId() + ":"
                                + message.lineNumber() + " " + message.message());
                return true;
            }
        });

        webView.setWebViewClient(new WebViewClientCompat() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (AppConfig.isTrustedInternalUri(uri)) return loader.shouldInterceptRequest(uri);
                if (!request.isForMainFrame() && isNetworkUri(uri)) {
                    Log.w(TAG, "blocked remote subresource: " + uri);
                    return blockedResponse();
                }
                return null;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                currentMainFrameUrl = url == null ? "" : url;
                trustedMainFrame = safeTrustedInternal(currentMainFrameUrl);
                runtimeReady = false;
                pageProgress = 0;
                pageStartedAt = SystemClock.elapsedRealtime();
                super.onPageStarted(view, url, favicon);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (request.isForMainFrame() && AppConfig.isTrustedInternalUri(uri)) return false;
                openExternal(uri);
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                currentMainFrameUrl = url == null ? "" : url;
                trustedMainFrame = safeTrustedInternal(currentMainFrameUrl);
                pageFinishedAt = SystemClock.elapsedRealtime();
                if (!trustedMainFrame) return;

                String token = session.rotate();
                view.evaluateJavascript("window.__BANC_NATIVE_TOKEN=" + JSONObject.quote(token)
                        + ";window.__BANC_NATIVE_WRAPPED=false;", null);
                for (String name : AppConfig.RUNTIME_SCRIPTS) injectAsset(name);

                view.postDelayed(() -> eval(
                        "window.BANC888_NATIVE_VOICE&&window.BANC888_NATIVE_VOICE.onStatus"
                                + "&&window.BANC888_NATIVE_VOICE.onStatus(AndroidVoice.status())"
                ), 180);
                view.postDelayed(() -> {
                    if (!runtimeReady && trustedMainFrame) {
                        Log.w(TAG, "web runtime did not report ready within 5s");
                        eval("window.FFC_WEBVIEW_RUNTIME&&window.FFC_WEBVIEW_RUNTIME.probe&&window.FFC_WEBVIEW_RUNTIME.probe()");
                    }
                }, 5000);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (request != null && request.isForMainFrame()) {
                    String description = error == null ? "unknown" : String.valueOf(error.getDescription());
                    requestRecovery("main-frame-error: " + description);
                }
            }

            @Override
            public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                String reason = detail != null && detail.didCrash() ? "renderer-crash" : "renderer-killed";
                if (detail != null) reason += " priority=" + detail.rendererPriorityAtExit();
                requestRecovery(reason);
                return true;
            }
        });
    }

    private boolean safeTrustedInternal(String url) {
        try {
            return url != null && !url.isEmpty() && AppConfig.isTrustedInternalUri(Uri.parse(url));
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean isNetworkUri(Uri uri) {
        if (uri == null || uri.getScheme() == null) return false;
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        return "http".equals(scheme) || "https".equals(scheme);
    }

    private void openExternal(Uri uri) {
        if (uri == null || uri.getScheme() == null) return;
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        if (!("https".equals(scheme) || "http".equals(scheme)
                || "mailto".equals(scheme) || "tel".equals(scheme))) {
            Toast.makeText(activity, "外部URLをブロックしました", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            activity.startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (Exception e) {
            Toast.makeText(activity, "外部リンクを開けません", Toast.LENGTH_SHORT).show();
        }
    }

    private WebResourceResponse blockedResponse() {
        return new WebResourceResponse(
                "text/plain",
                "UTF-8",
                403,
                "Blocked by BANC888 WebView policy",
                Collections.singletonMap("Cache-Control", "no-store"),
                new ByteArrayInputStream(new byte[0])
        );
    }

    private void installMessageBridge() {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            Log.w(TAG, "WEB_MESSAGE_LISTENER unavailable; token-gated legacy bridges remain");
            return;
        }

        WebViewCompat.addWebMessageListener(
                webView,
                "BancNative",
                Collections.singleton(AppConfig.APP_ORIGIN),
                (view, message, sourceOrigin, isMainFrame, replyProxy) -> {
                    if (!isMainFrame || !AppConfig.isTrustedOrigin(sourceOrigin)) return;
                    String raw;
                    try { raw = message.getData(); }
                    catch (Throwable ignored) { return; }
                    if (raw == null || raw.length() > 65_536) return;

                    JSONObject reply = new JSONObject();
                    try {
                        JSONObject req = new JSONObject(raw);
                        String id = req.optString("id", "");
                        String type = req.optString("type", "");
                        if (!id.isEmpty()) reply.put("id", id);
                        reply.put("type", type);
                        reply.put("ok", true);

                        switch (type) {
                            case "ping":
                            case "runtime.status":
                                reply.put("status", new JSONObject(diagnosticsJson()));
                                break;
                            case "runtime.ready":
                                runtimeReady = true;
                                lastHeartbeatAt = SystemClock.elapsedRealtime();
                                WebRecoveryGuard.markHealthy();
                                reply.put("status", new JSONObject(diagnosticsJson()));
                                break;
                            case "runtime.heartbeat": {
                                lastHeartbeatAt = SystemClock.elapsedRealtime();
                                JSONObject data = req.optJSONObject("data");
                                if (data != null) {
                                    jsErrorCount = Math.max(jsErrorCount, data.optInt("jsErrors", 0));
                                    longTaskCount = Math.max(longTaskCount, data.optInt("longTasks", 0));
                                }
                                reply.put("status", new JSONObject(diagnosticsJson()));
                                break;
                            }
                            case "runtime.error":
                                jsErrorCount++;
                                Log.e(TAG, "runtime.error " + req.optJSONObject("data"));
                                break;
                            case "runtime.reload":
                                activity.runOnUiThread(this::loadCurrentPage);
                                break;
                            case "runtime.useBundled":
                                devLive.useBundled();
                                activity.runOnUiThread(this::loadCurrentPage);
                                break;
                            case "runtime.rollback":
                                boolean rolled = devLive.rollback();
                                reply.put("rolledBack", rolled);
                                if (rolled) activity.runOnUiThread(this::loadCurrentPage);
                                break;
                            default:
                                reply.put("echo", true);
                        }
                    } catch (Exception e) {
                        try {
                            reply.put("ok", false);
                            reply.put("error", e.getMessage() == null ? e.toString() : e.getMessage());
                        } catch (Exception ignored) {}
                    }

                    try { replyProxy.postMessage(reply.toString()); }
                    catch (Throwable e) { Log.w(TAG, "WebMessage reply failed", e); }
                }
        );
    }

    private void requestRecovery(String reason) {
        trustedMainFrame = false;
        runtimeReady = false;
        Log.e(TAG, "requestRecovery: " + reason);

        if (!WebRecoveryGuard.tryAcquire(reason)) {
            activity.runOnUiThread(() -> Toast.makeText(
                    activity,
                    "WebView自動復旧の上限に達しました。アプリを再起動してください。",
                    Toast.LENGTH_LONG
            ).show());
            return;
        }

        activity.runOnUiThread(() -> {
            if (activity.isFinishing() || (Build.VERSION.SDK_INT >= 17 && activity.isDestroyed())) return;
            Toast.makeText(activity, "WebViewを自動復旧します", Toast.LENGTH_SHORT).show();
            activity.recreate();
        });
    }

    void loadCurrentPage() {
        activity.runOnUiThread(() -> webView.loadUrl(devLive.currentPageUrl()));
    }

    void eval(String script) {
        activity.runOnUiThread(() -> {
            if (trustedMainFrame) webView.evaluateJavascript(script, null);
        });
    }

    private void injectAsset(String name) {
        try {
            StringBuilder b = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(
                    devLive.openRuntimeAsset(name), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) b.append(line).append('\n');
            }
            webView.evaluateJavascript(b.toString(), null);
        } catch (Exception e) {
            eval("console.error(" + JSONObject.quote("JS injection failed: " + name + ": " + e.getMessage()) + ")");
        }
    }

    String diagnosticsJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("ok", true);
            o.put("shellVersion", AppConfig.SHELL_VERSION);
            o.put("runtimeVersion", AppConfig.WEB_RUNTIME_VERSION);
            o.put("bridgeSchema", AppConfig.BRIDGE_SCHEMA);
            o.put("assetSchema", AppConfig.ASSET_SCHEMA);
            o.put("trustedMainFrame", trustedMainFrame);
            o.put("runtimeReady", runtimeReady);
            o.put("url", currentMainFrameUrl);
            o.put("pageProgress", pageProgress);
            o.put("pageStartedAtElapsedMs", pageStartedAt);
            o.put("pageFinishedAtElapsedMs", pageFinishedAt);
            o.put("lastHeartbeatAtElapsedMs", lastHeartbeatAt);
            o.put("heartbeatAgeMs", lastHeartbeatAt <= 0 ? JSONObject.NULL
                    : Math.max(0L, SystemClock.elapsedRealtime() - lastHeartbeatAt));
            o.put("jsErrorCount", jsErrorCount);
            o.put("longTaskCount", longTaskCount);
            o.put("recovery", new JSONObject(WebRecoveryGuard.statusJson()));

            PackageInfo pkg = WebView.getCurrentWebViewPackage();
            o.put("webViewPackage", pkg == null ? JSONObject.NULL : pkg.packageName);
            o.put("webViewVersion", pkg == null ? JSONObject.NULL : pkg.versionName);
        } catch (Exception e) {
            try {
                o.put("ok", false);
                o.put("error", e.getMessage() == null ? e.toString() : e.getMessage());
            } catch (Exception ignored) {}
        }
        return o.toString();
    }

    void destroy(BridgeRegistry bridges) {
        trustedMainFrame = false;
        runtimeReady = false;
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            try { WebViewCompat.removeWebMessageListener(webView, "BancNative"); }
            catch (Throwable ignored) {}
        }
        bridges.uninstall();
        webView.stopLoading();
        webView.destroy();
    }
}
