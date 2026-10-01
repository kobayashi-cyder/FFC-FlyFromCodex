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
    private VideoExporter videos;
    private A1111Client a1111;
    private LocalModelClient localModel;
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
        videos = new VideoExporter(this);
        a1111 = new A1111Client();
        localModel = new LocalModelClient();
        speech = new SpeechController(this, this::evalJs);

        webShell = new WebShellController(this, webView, devLive, nativeSession);
        bridges = new BridgeRegistry(
                this,
                webView,
                nativeSession,
                webShell::isTrustedMainFrame,
                webShell::diagnosticsJson,
                speech,
                documents,
                videos,
                a1111,
                localModel,
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

    SpeechController speechForTest() {
        return speech;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        // Do not serialize Chromium/WebView history into the Activity state bundle.
        // Real pages can exceed Binder's transaction limit and crash the Activity
        // with TransactionTooLargeException. The shell is local/reloadable, so
        // rebuilding it is safer than persisting megabytes of Chromium state.
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onPause() {
        if (webView != null) webView.onPause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) webView.onResume();
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_MIC || speech == null) return;
        boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        speech.onPermissionResult(granted);
        evalJs("window.BANC888_NATIVE_VOICE&&window.BANC888_NATIVE_VOICE.onStatus"
                + "&&window.BANC888_NATIVE_VOICE.onStatus(AndroidVoice.status())");
    }

    @Override
    protected void onDestroy() {
        if (webShell != null && bridges != null) {
            try { webShell.destroy(bridges); } catch (Throwable ignored) {}
        }
        if (speech != null) {
            try { speech.destroy(); } catch (Throwable ignored) {}
        }
        if (videos != null) {
            VideoExporter exporter = videos;
            new Thread(() -> {
                try { exporter.close(); } catch (Throwable ignored) {}
            }, "BANC888-video-shutdown").start();
        }
        if (a1111 != null) a1111.close();
        if (localModel != null) localModel.close();
        if (research != null) {
            try { research.shutdown(); } catch (Throwable ignored) {}
        }
        if (devLive != null) {
            try { devLive.shutdown(); } catch (Throwable ignored) {}
        }
        super.onDestroy();
    }
}
