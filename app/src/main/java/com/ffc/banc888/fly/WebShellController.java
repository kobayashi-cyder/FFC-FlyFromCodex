package com.ffc.banc888.fly;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;

import androidx.webkit.WebViewAssetLoader;
import androidx.webkit.WebViewClientCompat;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

final class WebShellController {
    private final Activity activity;
    private final WebView webView;
    private final DevLiveManager devLive;
    private final NativeSession session;
    private volatile boolean trustedMainFrame = false;

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
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        if (Build.VERSION.SDK_INT >= 26) s.setSafeBrowsingEnabled(true);
        webView.setBackgroundColor(0xff050a13);

        bridges.install();

        WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .setDomain(AppConfig.APP_HOST)
                .setHttpAllowed(false)
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(activity))
                .addPathHandler("/live/", new WebViewAssetLoader.InternalStoragePathHandler(activity, devLive.root()))
                .build();

        webView.setWebViewClient(new WebViewClientCompat() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return loader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                trustedMainFrame = AppConfig.isTrustedInternalUri(Uri.parse(url));
                super.onPageStarted(view, url, favicon);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (request.isForMainFrame() && AppConfig.isTrustedInternalUri(uri)) return false;
                if ("https".equalsIgnoreCase(uri.getScheme())) {
                    try { activity.startActivity(new Intent(Intent.ACTION_VIEW, uri)); }
                    catch (Exception ignored) {}
                }
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                trustedMainFrame = AppConfig.isTrustedInternalUri(Uri.parse(url));
                if (!trustedMainFrame) return;
                String token = session.rotate();
                view.evaluateJavascript("window.__BANC_NATIVE_TOKEN=" + JSONObject.quote(token)
                        + ";window.__BANC_NATIVE_WRAPPED=false;", null);
                for (String name : AppConfig.RUNTIME_SCRIPTS) injectAsset(name);
                view.postDelayed(() -> eval("window.BANC888_NATIVE_VOICE&&window.BANC888_NATIVE_VOICE.onStatus&&window.BANC888_NATIVE_VOICE.onStatus(AndroidVoice.status())"), 250);
            }
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
            try (BufferedReader r = new BufferedReader(new InputStreamReader(devLive.openRuntimeAsset(name), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) b.append(line).append('\n');
            }
            webView.evaluateJavascript(b.toString(), null);
        } catch (Exception e) {
            eval("console.error(" + JSONObject.quote("JS injection failed: " + name + ": " + e.getMessage()) + ")");
        }
    }

    void destroy(BridgeRegistry bridges) {
        trustedMainFrame = false;
        bridges.uninstall();
        webView.stopLoading();
        webView.destroy();
    }
}
