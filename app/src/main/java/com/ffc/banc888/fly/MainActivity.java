package com.ffc.banc888.fly;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.webkit.WebView;

public class MainActivity extends Activity {
    static final int REQ_MIC = 888;

    private WebView webView;
    private NativeSession nativeSession;
    private DevLiveManager devLive;
    private SpeechController speech;
    private ResearchClient research;
    private DocumentExporter documents;
    private WebShellController webShell;
    private BridgeRegistry bridges;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        webView = new WebView(this);
        setContentView(webView);

        nativeSession = new NativeSession();
        devLive = new DevLiveManager(this);
        research = new ResearchClient();
        documents = new DocumentExporter(this);
        speech = new SpeechController(this, this::evalJs);

        webShell = new WebShellController(this, webView, devLive, nativeSession);
        bridges = new BridgeRegistry(
                this,
                webView,
                nativeSession,
                webShell::isTrustedMainFrame,
                speech,
                documents,
                research,
                devLive,
                () -> webShell.loadCurrentPage()
        );

        webShell.configure(bridges);
        webShell.loadCurrentPage();
    }

    private void evalJs(String script) {
        WebShellController shell = webShell;
        if (shell != null) shell.eval(script);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_MIC || speech == null) return;
        boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        speech.onPermissionResult(granted);
        evalJs("window.BANC888_NATIVE_VOICE&&window.BANC888_NATIVE_VOICE.onStatus&&window.BANC888_NATIVE_VOICE.onStatus(AndroidVoice.status())");
    }

    @Override
    protected void onDestroy() {
        if (webShell != null && bridges != null) {
            try { webShell.destroy(bridges); } catch (Throwable ignored) {}
        }
        if (speech != null) {
            try { speech.destroy(); } catch (Throwable ignored) {}
        }
        if (research != null) {
            try { research.shutdown(); } catch (Throwable ignored) {}
        }
        if (devLive != null) {
            try { devLive.shutdown(); } catch (Throwable ignored) {}
        }
        super.onDestroy();
    }
}
