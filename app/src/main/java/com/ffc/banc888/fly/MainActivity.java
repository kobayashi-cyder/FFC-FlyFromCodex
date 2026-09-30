package com.ffc.banc888.fly;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.ClipData;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Toast;

import androidx.core.content.FileProvider;
import androidx.webkit.WebViewAssetLoader;
import androidx.webkit.WebViewClientCompat;

import org.json.JSONObject;
import org.json.JSONArray;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class MainActivity extends Activity {
    private static final int REQ_MIC = 888;
    private static final String APP_ORIGIN = "https://appassets.androidplatform.net";
    private WebView webView;
    private SpeechRecognizer recognizer;
    private TextToSpeech tts;
    private boolean ttsReady = false;
    private String pendingLanguage = "ja-JP";

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        webView = new WebView(this);
        setContentView(webView);
        configureWebView();
        initTts();
        initRecognizer();
        webView.loadUrl(APP_ORIGIN + "/assets/index.html");
    }

    private void configureWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        webView.setBackgroundColor(0xff050a13);
        webView.addJavascriptInterface(new AndroidVoiceBridge(), "AndroidVoice");
        webView.addJavascriptInterface(new AndroidFilesBridge(), "AndroidFiles");
        webView.addJavascriptInterface(new AndroidResearchBridge(), "AndroidResearch");

        WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        webView.setWebViewClient(new WebViewClientCompat() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return loader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if ("appassets.androidplatform.net".equalsIgnoreCase(uri.getHost())) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); }
                catch (Exception ignored) { }
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                injectAsset("thread-router-core.js");
                injectAsset("capability-vocabulary-core.js");
                injectAsset("research-physics-core.js");
                injectAsset("ir-patch-core.js");
                injectAsset("proxy-agent-core.js");
                injectAsset("capability-tools.js");
                injectAsset("research-physics-tools.js");
                injectAsset("proxy-agent.js");
                injectAsset("conversation-output-core.js");
                injectAsset("thread-router.js");
                view.postDelayed(() -> callJsStatus(), 250);
            }
        });
    }

    private void injectAsset(String name) {
        try {
            StringBuilder b = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(getAssets().open(name), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) b.append(line).append('\n');
            }
            webView.evaluateJavascript(b.toString(), null);
        } catch (Exception e) {
            Toast.makeText(this, "JS injection failed: " + name, Toast.LENGTH_LONG).show();
        }
    }

    private void initRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return;
        recognizer = SpeechRecognizer.createSpeechRecognizer(this);
        recognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) { callJs("onListening"); }
            @Override public void onBeginningOfSpeech() { }
            @Override public void onRmsChanged(float rmsdB) { }
            @Override public void onBufferReceived(byte[] buffer) { }
            @Override public void onEndOfSpeech() { }
            @Override public void onEvent(int eventType, Bundle params) { }

            @Override
            public void onError(int error) {
                callJsArgs("onError", String.valueOf(error), recognitionErrorText(error));
            }

            @Override
            public void onResults(Bundle results) {
                ArrayList<String> list = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                float confidence = -1f;
                float[] conf = results.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES);
                if (conf != null && conf.length > 0) confidence = conf[0];
                String text = list != null && !list.isEmpty() ? list.get(0) : "";
                callJsResult(text, confidence);
            }

            @Override
            public void onPartialResults(Bundle partialResults) {
                ArrayList<String> list = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                String text = list != null && !list.isEmpty() ? list.get(0) : "";
                callJsArgs("onPartial", text);
            }
        });
    }

    private void startRecognition(String language) {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingLanguage = language == null ? "ja-JP" : language;
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
            return;
        }
        if (recognizer == null) initRecognizer();
        if (recognizer == null) {
            callJsArgs("onError", "unavailable", "Android SpeechRecognizer is unavailable");
            return;
        }
        if (tts != null) tts.stop();
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, language == null ? "ja-JP" : language);
        i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        i.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
        try { recognizer.startListening(i); }
        catch (Exception e) { callJsArgs("onError", "start", e.getMessage() == null ? e.toString() : e.getMessage()); }
    }

    private void initTts() {
        tts = new TextToSpeech(this, status -> {
            ttsReady = status == TextToSpeech.SUCCESS;
            if (!ttsReady) return;
            tts.setLanguage(Locale.JAPAN);
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String utteranceId) { callJs("onTtsStart"); }
                @Override public void onDone(String utteranceId) { callJs("onTtsDone"); }
                @Override public void onError(String utteranceId) { callJsArgs("onTtsError", "TextToSpeech error"); }
            });
            callJsStatus();
        });
    }

    private void speakNative(String text, String lang, double rate, double pitch) {
        if (!ttsReady || tts == null) { callJsArgs("onTtsError", "TextToSpeech is not ready"); return; }
        try {
            if (recognizer != null) recognizer.cancel();
            Locale locale = Locale.forLanguageTag(lang == null ? "ja-JP" : lang);
            tts.setLanguage(locale);
            tts.setSpeechRate((float)Math.max(0.5, Math.min(2.0, rate)));
            tts.setPitch((float)Math.max(0.5, Math.min(2.0, pitch)));
            String id = "fly-" + UUID.randomUUID();
            tts.speak(text == null ? "" : text, TextToSpeech.QUEUE_FLUSH, null, id);
        } catch (Exception e) { callJsArgs("onTtsError", e.getMessage() == null ? e.toString() : e.getMessage()); }
    }

    private String recognitionErrorText(int error) {
        switch (error) {
            case SpeechRecognizer.ERROR_AUDIO: return "audio error";
            case SpeechRecognizer.ERROR_CLIENT: return "client error";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS: return "microphone permission denied";
            case SpeechRecognizer.ERROR_NETWORK: return "network error";
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT: return "network timeout";
            case SpeechRecognizer.ERROR_NO_MATCH: return "no speech match";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY: return "recognizer busy";
            case SpeechRecognizer.ERROR_SERVER: return "recognizer server error";
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT: return "speech timeout";
            default: return "speech recognizer error " + error;
        }
    }

    private String statusJson() {
        try {
            JSONObject o = new JSONObject();
            boolean granted = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
            o.put("permission", granted ? "granted" : "prompt");
            o.put("speechRecognizerAvailable", SpeechRecognizer.isRecognitionAvailable(this));
            o.put("ttsReady", ttsReady);
            o.put("sdkInt", android.os.Build.VERSION.SDK_INT);
            o.put("origin", APP_ORIGIN);
            o.put("threadRouter", true);
            return o.toString();
        } catch (Exception e) { return "{\"error\":\"status\"}"; }
    }

    private void callJsStatus() {
        runOnUiThread(() -> webView.evaluateJavascript(
                "window.BANC888_NATIVE_VOICE&&window.BANC888_NATIVE_VOICE.onStatus(" + JSONObject.quote(statusJson()) + ")",
                null));
    }

    private void callJs(String method) {
        runOnUiThread(() -> webView.evaluateJavascript(
                "window.BANC888_NATIVE_VOICE&&window.BANC888_NATIVE_VOICE." + method + "()",
                null));
    }

    private void callJsArgs(String method, String... args) {
        StringBuilder b = new StringBuilder("window.BANC888_NATIVE_VOICE&&window.BANC888_NATIVE_VOICE.")
                .append(method).append('(');
        for (int i=0;i<args.length;i++) {
            if (i>0) b.append(',');
            b.append(JSONObject.quote(args[i] == null ? "" : args[i]));
        }
        b.append(')');
        runOnUiThread(() -> webView.evaluateJavascript(b.toString(), null));
    }

    private void callJsResult(String text, float confidence) {
        String js = "window.BANC888_NATIVE_VOICE&&window.BANC888_NATIVE_VOICE.onResult("
                + JSONObject.quote(text == null ? "" : text) + "," + confidence + ")";
        runOnUiThread(() -> webView.evaluateJavascript(js, null));
    }

    public class AndroidVoiceBridge {
        @JavascriptInterface public String status() { return statusJson(); }
        @JavascriptInterface public void requestMicPermission() {
            runOnUiThread(() -> requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC));
        }
        @JavascriptInterface public void startListening(String language) {
            runOnUiThread(() -> startRecognition(language));
        }
        @JavascriptInterface public void stopListening() {
            runOnUiThread(() -> { if (recognizer != null) recognizer.cancel(); });
        }
        @JavascriptInterface public void speak(String text, String language, double rate, double pitch) {
            runOnUiThread(() -> speakNative(text, language, rate, pitch));
        }
    }


    private static String xmlEscape(String s) {
        return (s == null ? "" : s)
                .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }

    private static String safeFileName(String name, String fallback, String ext) {
        String n = (name == null || name.trim().isEmpty()) ? fallback : name.trim();
        n = n.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").replace("..", "_");
        if (!n.toLowerCase(Locale.ROOT).endsWith("." + ext)) n += "." + ext;
        return n;
    }

    private static void zipText(ZipOutputStream zip, String path, String text) throws Exception {
        ZipEntry e = new ZipEntry(path);
        zip.putNextEntry(e);
        zip.write(text.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private void writeDocx(File file, String title, String body) throws Exception {
        String types = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                + "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>"
                + "</Types>";
        String rels = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/>"
                + "</Relationships>";
        StringBuilder doc = new StringBuilder();
        doc.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
                .append("<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>");
        if (title != null && !title.trim().isEmpty()) {
            doc.append("<w:p><w:r><w:rPr><w:b/><w:sz w:val=\"32\"/></w:rPr><w:t xml:space=\"preserve\">")
                    .append(xmlEscape(title.trim())).append("</w:t></w:r></w:p>");
        }
        String[] lines = (body == null ? "" : body).replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        boolean codeBlock = false;
        for (String raw : lines) {
            String line = raw == null ? "" : raw;
            String trimmed = line.trim();
            if (trimmed.length() >= 3 && trimmed.charAt(0) == 96 && trimmed.charAt(1) == 96 && trimmed.charAt(2) == 96) { codeBlock = !codeBlock; continue; }
            if (line.isEmpty()) { doc.append("<w:p/>"); continue; }
            int heading = 0;
            if (line.startsWith("### ")) heading = 3;
            else if (line.startsWith("## ")) heading = 2;
            else if (line.startsWith("# ")) heading = 1;
            if (heading > 0) {
                String text = line.substring(heading + 1).trim();
                int size = heading == 1 ? 30 : heading == 2 ? 26 : 23;
                doc.append("<w:p><w:pPr><w:spacing w:before=\"180\" w:after=\"80\"/></w:pPr><w:r><w:rPr><w:b/><w:sz w:val=\"")
                        .append(size).append("\"/></w:rPr><w:t xml:space=\"preserve\">").append(xmlEscape(text)).append("</w:t></w:r></w:p>");
                continue;
            }
            boolean bullet = line.startsWith("- ") || line.startsWith("* ");
            boolean numbered = line.matches("^\\d+[.)]\\s+.*");
            String text = bullet ? "• " + line.substring(2).trim() : line;
            doc.append("<w:p><w:pPr>");
            if (bullet || numbered) doc.append("<w:ind w:left=\"480\" w:hanging=\"240\"/>");
            doc.append("</w:pPr><w:r><w:rPr>");
            if (codeBlock) doc.append("<w:rFonts w:ascii=\"Consolas\" w:hAnsi=\"Consolas\"/><w:sz w:val=\"19\"/>");
            doc.append("</w:rPr><w:t xml:space=\"preserve\">").append(xmlEscape(text)).append("</w:t></w:r></w:p>");
        }
        doc.append("<w:sectPr><w:pgSz w:w=\"11906\" w:h=\"16838\"/><w:pgMar w:top=\"1440\" w:right=\"1440\" w:bottom=\"1440\" w:left=\"1440\"/></w:sectPr>")
                .append("</w:body></w:document>");
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(file))) {
            zipText(zip, "[Content_Types].xml", types);
            zipText(zip, "_rels/.rels", rels);
            zipText(zip, "word/document.xml", doc.toString());
        }
    }

    private void shareFile(File file, String mime) {
        runOnUiThread(() -> {
            try {
                Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".files", file);
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType(mime);
                send.putExtra(Intent.EXTRA_STREAM, uri);
                send.setClipData(ClipData.newRawUri(file.getName(), uri));
                send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(send, "BANC888 から共有"));
            } catch (Exception e) {
                Toast.makeText(this, "Share failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    public class AndroidFilesBridge {
        @JavascriptInterface
        public String createDocx(String title, String body, String filename) {
            JSONObject o = new JSONObject();
            try {
                File dir = new File(getCacheDir(), "exports");
                if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("exports directory");
                File f = new File(dir, safeFileName(filename, "BANC888_document", "docx"));
                writeDocx(f, title, body);
                o.put("ok", true); o.put("name", f.getName()); o.put("bytes", f.length()); o.put("shared", true);
                shareFile(f, "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            } catch (Exception e) {
                try { o.put("ok", false); o.put("error", e.getMessage() == null ? e.toString() : e.getMessage()); } catch (Exception ignored) {}
            }
            return o.toString();
        }

        @JavascriptInterface
        public String shareText(String text, String filename, String mime) {
            JSONObject o = new JSONObject();
            try {
                File dir = new File(getCacheDir(), "exports");
                if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("exports directory");
                String ext = "txt";
                int dot = filename == null ? -1 : filename.lastIndexOf('.');
                if (dot >= 0 && dot < filename.length() - 1) ext = filename.substring(dot + 1).replaceAll("[^A-Za-z0-9]", "");
                File f = new File(dir, safeFileName(filename, "BANC888_export", ext.isEmpty() ? "txt" : ext));
                try (FileOutputStream out = new FileOutputStream(f)) {
                    out.write((text == null ? "" : text).getBytes(StandardCharsets.UTF_8));
                }
                o.put("ok", true); o.put("name", f.getName()); o.put("bytes", f.length()); o.put("shared", true);
                shareFile(f, mime == null || mime.isEmpty() ? "text/plain" : mime);
            } catch (Exception e) {
                try { o.put("ok", false); o.put("error", e.getMessage() == null ? e.toString() : e.getMessage()); } catch (Exception ignored) {}
            }
            return o.toString();
        }
    }


    private static String httpGetFixed(String rawUrl, int maxChars) throws Exception {
        URL u = new URL(rawUrl);
        String host = u.getHost() == null ? "" : u.getHost().toLowerCase(Locale.ROOT);
        boolean allowed = host.endsWith(".wikipedia.org") || host.equals("api.crossref.org");
        if (!"https".equalsIgnoreCase(u.getProtocol()) || !allowed) throw new SecurityException("research host blocked");
        HttpURLConnection con = (HttpURLConnection)u.openConnection();
        con.setConnectTimeout(8000);
        con.setReadTimeout(12000);
        con.setInstanceFollowRedirects(true);
        con.setRequestProperty("User-Agent","BANC888-FlyResearch/1.0");
        int code = con.getResponseCode();
        if (code < 200 || code >= 300) throw new IllegalStateException("HTTP " + code);
        int cap = Math.max(1000, Math.min(80000, maxChars));
        StringBuilder b = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8))) {
            char[] buf = new char[4096]; int n;
            while ((n = r.read(buf)) >= 0 && b.length() < cap) b.append(buf,0,Math.min(n,cap-b.length()));
        } finally { con.disconnect(); }
        return b.toString();
    }

    public class AndroidResearchBridge {
        @JavascriptInterface
        public String searchWikipedia(String query, int limit) {
            JSONObject out = new JSONObject();
            try {
                int n = Math.max(1, Math.min(5, limit));
                String q = URLEncoder.encode(query == null ? "" : query, "UTF-8");
                String raw = httpGetFixed("https://ja.wikipedia.org/w/api.php?action=query&generator=search&gsrsearch="+q+"&gsrlimit="+n+"&prop=extracts|info&exintro=1&explaintext=1&inprop=url&format=json&utf8=1", 80000);
                JSONObject j = new JSONObject(raw), pages = j.optJSONObject("query") == null ? null : j.optJSONObject("query").optJSONObject("pages");
                JSONArray arr = new JSONArray();
                if (pages != null) {
                    java.util.Iterator<String> it = pages.keys();
                    while (it.hasNext()) {
                        JSONObject p = pages.optJSONObject(it.next()); if (p == null) continue;
                        JSONObject x = new JSONObject();
                        x.put("title", p.optString("title",""));
                        x.put("url", p.optString("fullurl",""));
                        x.put("extract", p.optString("extract",""));
                        arr.put(x);
                    }
                }
                out.put("ok", true); out.put("results", arr); out.put("provider", "wikipedia");
            } catch (Exception e) {
                try { out.put("ok", false); out.put("error", e.getMessage()==null?e.toString():e.getMessage()); } catch (Exception ignored) {}
            }
            return out.toString();
        }

        @JavascriptInterface
        public String searchCrossref(String query, int limit) {
            JSONObject out = new JSONObject();
            try {
                int n = Math.max(1, Math.min(5, limit));
                String q = URLEncoder.encode(query == null ? "" : query, "UTF-8");
                String raw = httpGetFixed("https://api.crossref.org/works?rows="+n+"&select=DOI,title,URL,author,published,abstract&query="+q, 80000);
                JSONObject j = new JSONObject(raw);
                JSONArray items = j.optJSONObject("message") == null ? new JSONArray() : j.optJSONObject("message").optJSONArray("items");
                JSONArray arr = new JSONArray();
                if (items != null) for (int i=0;i<items.length();i++) {
                    JSONObject p = items.optJSONObject(i); if (p == null) continue;
                    JSONObject x = new JSONObject();
                    JSONArray tt = p.optJSONArray("title");
                    x.put("title", tt != null && tt.length()>0 ? tt.optString(0) : "");
                    x.put("url", p.optString("URL",""));
                    x.put("abstract", p.optString("abstract","").replaceAll("<[^>]+>"," "));
                    x.put("published", p.optJSONObject("published") == null ? JSONObject.NULL : p.optJSONObject("published"));
                    arr.put(x);
                }
                out.put("ok", true); out.put("results", arr); out.put("provider", "crossref");
            } catch (Exception e) {
                try { out.put("ok", false); out.put("error", e.getMessage()==null?e.toString():e.getMessage()); } catch (Exception ignored) {}
            }
            return out.toString();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_MIC) return;
        boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        callJsStatus();
        if (granted) startRecognition(pendingLanguage);
        else callJsArgs("onError", "permission", "microphone permission denied");
    }

    @Override
    protected void onDestroy() {
        if (recognizer != null) recognizer.destroy();
        if (tts != null) { tts.stop(); tts.shutdown(); }
        if (webView != null) {
            webView.removeJavascriptInterface("AndroidVoice");
            webView.removeJavascriptInterface("AndroidFiles");
            webView.removeJavascriptInterface("AndroidResearch");
            webView.destroy();
        }
        super.onDestroy();
    }
}
