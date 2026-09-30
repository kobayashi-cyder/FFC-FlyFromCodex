package com.ffc.banc888agent;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

public final class MainActivity extends Activity {
    private WebView webView;
    private TextView agentStatus;
    private AgentCommandEngine agentEngine;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        agentEngine = new AgentCommandEngine();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        LinearLayout commandRow = new LinearLayout(this);
        commandRow.setOrientation(LinearLayout.HORIZONTAL);
        EditText command = new EditText(this);
        command.setSingleLine(true);
        command.setHint("例: 「OK」を押す / タップ 500 800 / 画面取得");
        commandRow.addView(command, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button run = new Button(this);
        run.setText("実行");
        commandRow.addView(run);
        root.addView(commandRow);

        LinearLayout controlRow = new LinearLayout(this);
        controlRow.setOrientation(LinearLayout.HORIZONTAL);
        Button permission = new Button(this);
        permission.setText("操作権限");
        Button snapshot = new Button(this);
        snapshot.setText("画面取得");
        Button back = new Button(this);
        back.setText("戻る");
        controlRow.addView(permission);
        controlRow.addView(snapshot);
        controlRow.addView(back);
        root.addView(controlRow);

        agentStatus = new TextView(this);
        agentStatus.setText("Agent: Accessibility未確認。APK内のCDL/IR/HTML生成はオフライン動作。");
        agentStatus.setMaxLines(4);
        root.addView(agentStatus);

        webView = new WebView(this);
        root.addView(webView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);

        configureWebView();

        run.setOnClickListener(v -> showAgentResult(agentEngine.run(command.getText().toString())));
        permission.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        snapshot.setOnClickListener(v -> showAgentResult(agentEngine.run("画面取得")));
        back.setOnClickListener(v -> showAgentResult(agentEngine.run("戻る")));
    }

    private void configureWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowContentAccess(false);
        s.setAllowFileAccess(true);
        s.setAllowFileAccessFromFileURLs(false);
        s.setAllowUniversalAccessFromFileURLs(false);
        s.setBlockNetworkLoads(true);
        s.setCacheMode(WebSettings.LOAD_NO_CACHE);

        webView.addJavascriptInterface(new FileBridge(), "AndroidBridge");
        webView.addJavascriptInterface(new AgentBridge(), "AndroidAgent");
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                return !("file".equalsIgnoreCase(u.getScheme())
                        && "/android_asset/index.html".equals(u.getPath()));
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String scheme = request.getUrl().getScheme();
                if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
                    return new WebResourceResponse("text/plain", "UTF-8", null);
                }
                return super.shouldInterceptRequest(view, request);
            }
        });
        webView.loadUrl("file:///android_asset/index.html");
    }

    private void showAgentResult(String result) {
        if (result == null) result = "";
        agentStatus.setText(result.length() > 1200 ? result.substring(0, 1200) + "…" : result);
    }

    private final class FileBridge {
        @JavascriptInterface
        public void saveText(String fileName, String content, String mimeType) {
            String safeName = sanitizeFileName(fileName);
            String type = (mimeType == null || mimeType.isBlank()) ? "text/plain" : mimeType;
            try {
                if (android.os.Build.VERSION.SDK_INT >= 29) {
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.Downloads.DISPLAY_NAME, safeName);
                    values.put(MediaStore.Downloads.MIME_TYPE, type);
                    values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/BANC888");
                    Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                    if (uri == null) throw new IllegalStateException("MediaStore insert failed");
                    try (OutputStream out = getContentResolver().openOutputStream(uri, "w")) {
                        if (out == null) throw new IllegalStateException("openOutputStream failed");
                        out.write(content.getBytes(StandardCharsets.UTF_8));
                    }
                    toast("保存: Downloads/BANC888/" + safeName);
                } else {
                    File dir = new File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "BANC888");
                    if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("mkdir failed");
                    File target = new File(dir, safeName);
                    try (FileOutputStream out = new FileOutputStream(target)) {
                        out.write(content.getBytes(StandardCharsets.UTF_8));
                    }
                    toast("保存: " + target.getAbsolutePath());
                }
            } catch (Exception e) {
                toast("保存失敗: " + e.getClass().getSimpleName());
            }
        }

        private String sanitizeFileName(String name) {
            String n = (name == null || name.isBlank()) ? "banc888_output.txt" : name;
            return n.replaceAll("[\\\\/:*?\"<>|\\r\\n]+", "_");
        }
    }

    private final class AgentBridge {
        @JavascriptInterface
        public String runCommand(String command) {
            return onUiThread(() -> agentEngine.run(command));
        }

        @JavascriptInterface
        public String getScreenSnapshot() {
            return onUiThread(() -> agentEngine.run("画面取得"));
        }

        @JavascriptInterface
        public boolean isAccessibilityEnabled() {
            return BancAccessibilityService.getInstance() != null;
        }

        @JavascriptInterface
        public void openAccessibilitySettings() {
            runOnUiThread(() -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        }

        private String onUiThread(java.util.concurrent.Callable<String> call) {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                try { return call.call(); } catch (Exception e) { return "{\"ok\":false,\"error\":\"agent_error\"}"; }
            }
            FutureTask<String> task = new FutureTask<>(call);
            runOnUiThread(task);
            try {
                return task.get(3, TimeUnit.SECONDS);
            } catch (Exception e) {
                return "{\"ok\":false,\"error\":\"agent_timeout\"}";
            }
        }
    }

    private void toast(String message) {
        runOnUiThread(() -> Toast.makeText(this, message, Toast.LENGTH_LONG).show());
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) webView.destroy();
        super.onDestroy();
    }
}
