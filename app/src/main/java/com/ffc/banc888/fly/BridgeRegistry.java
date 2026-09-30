package com.ffc.banc888.fly;

import android.app.Activity;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import org.json.JSONObject;

import java.util.function.BooleanSupplier;

final class BridgeRegistry {
    private final Activity activity;
    private final WebView webView;
    private final NativeSession session;
    private final BooleanSupplier trustedMainFrame;
    private final SpeechController speech;
    private final DocumentExporter documents;
    private final ResearchClient research;
    private final DevLiveManager devLive;
    private final Runnable reload;

    BridgeRegistry(
            Activity activity,
            WebView webView,
            NativeSession session,
            BooleanSupplier trustedMainFrame,
            SpeechController speech,
            DocumentExporter documents,
            ResearchClient research,
            DevLiveManager devLive,
            Runnable reload
    ) {
        this.activity = activity;
        this.webView = webView;
        this.session = session;
        this.trustedMainFrame = trustedMainFrame;
        this.speech = speech;
        this.documents = documents;
        this.research = research;
        this.devLive = devLive;
        this.reload = reload;
    }

    void install() {
        webView.addJavascriptInterface(new VoiceBridge(), "__BancVoice");
        webView.addJavascriptInterface(new FilesBridge(), "__BancFiles");
        webView.addJavascriptInterface(new ResearchBridge(), "__BancResearch");
        webView.addJavascriptInterface(new DevBridge(), "__BancDev");
        webView.addJavascriptInterface(new DiagnosticsBridge(), "__BancDiagnostics");
    }

    void uninstall() {
        webView.removeJavascriptInterface("__BancVoice");
        webView.removeJavascriptInterface("__BancFiles");
        webView.removeJavascriptInterface("__BancResearch");
        webView.removeJavascriptInterface("__BancDev");
        webView.removeJavascriptInterface("__BancDiagnostics");
    }

    private boolean allow(String token) {
        return trustedMainFrame.getAsBoolean() && session.valid(token);
    }

    private String denied() {
        return "{\"ok\":false,\"error\":\"native bridge denied\"}";
    }

    public final class VoiceBridge {
        @JavascriptInterface public String status(String token) {
            return allow(token) ? speech.statusJson() : denied();
        }
        @JavascriptInterface public boolean requestMicPermission(String token) {
            if (!allow(token)) return false;
            speech.requestMicPermission();
            return true;
        }
        @JavascriptInterface public boolean startListening(String token, String language) {
            if (!allow(token)) return false;
            return speech.startListening(language);
        }
        @JavascriptInterface public boolean stopListening(String token) {
            if (!allow(token)) return false;
            speech.stopListening();
            return true;
        }
        @JavascriptInterface public boolean speak(String token, String text, String language, double rate, double pitch) {
            if (!allow(token)) return false;
            return speech.speak(text, language, rate, pitch);
        }
    }

    public final class FilesBridge {
        @JavascriptInterface public String createDocx(String token, String title, String body, String filename) {
            return allow(token) ? documents.createDocx(title, body, filename) : denied();
        }
        @JavascriptInterface public String shareText(String token, String text, String filename, String mime) {
            return allow(token) ? documents.shareText(text, filename, mime) : denied();
        }
    }

    public final class ResearchBridge {
        @JavascriptInterface public String searchWikipedia(String token, String query, int limit) {
            return allow(token) ? research.searchWikipedia(query, limit) : denied();
        }
        @JavascriptInterface public String searchCrossref(String token, String query, int limit) {
            return allow(token) ? research.searchCrossref(query, limit) : denied();
        }
    }

    public final class DevBridge {
        @JavascriptInterface public String status(String token) {
            return allow(token) ? devLive.statusJson() : denied();
        }
        @JavascriptInterface public boolean syncAndReload(String token) {
            if (!allow(token)) return false;
            devLive.syncAndReload(reload);
            return true;
        }
        @JavascriptInterface public boolean reload(String token) {
            if (!allow(token)) return false;
            activity.runOnUiThread(reload);
            return true;
        }
        @JavascriptInterface public boolean useBundledAndReload(String token) {
            if (!allow(token)) return false;
            devLive.useBundled();
            activity.runOnUiThread(reload);
            return true;
        }
        @JavascriptInterface public boolean rollbackAndReload(String token) {
            if (!allow(token)) return false;
            boolean ok = devLive.rollback();
            if (ok) activity.runOnUiThread(reload);
            return ok;
        }
    }

    public final class DiagnosticsBridge {
        @JavascriptInterface public boolean simulateVoiceResult(String token, String text, double confidence) {
            if (!allow(token)) return false;
            boolean debug = (activity.getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0;
            if (!debug) return false;
            speech.simulateRecognition(text, (float)confidence);
            return true;
        }

        @JavascriptInterface public String status(String token) {
            if (!allow(token)) return denied();
            JSONObject o = new JSONObject();
            try {
                o.put("ok", true);
                o.put("shellVersion", AppConfig.SHELL_VERSION);
                o.put("bridgeSchema", AppConfig.BRIDGE_SCHEMA);
                o.put("assetSchema", AppConfig.ASSET_SCHEMA);
                o.put("trustedMainFrame", trustedMainFrame.getAsBoolean());
                o.put("sdkInt", Build.VERSION.SDK_INT);
                PackageInfo pkg = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
                o.put("versionName", pkg.versionName == null ? "" : pkg.versionName);
                if (Build.VERSION.SDK_INT >= 28) o.put("versionCode", pkg.getLongVersionCode());
                else o.put("versionCode", pkg.versionCode);
                android.content.pm.PackageInfo webPkg = WebView.getCurrentWebViewPackage();
                o.put("webViewPackage", webPkg == null ? JSONObject.NULL : webPkg.packageName);
                o.put("webViewVersion", webPkg == null ? JSONObject.NULL : webPkg.versionName);
                o.put("speech", new JSONObject(speech.statusJson()));
                o.put("research", new JSONObject(research.diagnosticsJson()));
                o.put("documents", new JSONObject(documents.diagnosticsJson()));
                o.put("devLive", new JSONObject(devLive.statusJson()));
            } catch (Exception e) {
                try {
                    o.put("ok", false);
                    o.put("error", e.getMessage() == null ? e.toString() : e.getMessage());
                } catch (Exception ignored) {}
            }
            return o.toString();
        }
    }
}
