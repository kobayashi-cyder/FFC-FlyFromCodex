package com.ffc.banc888.fly;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
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

import androidx.webkit.WebViewAssetLoader;
import androidx.webkit.WebViewClientCompat;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;
import java.util.UUID;

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
            webView.destroy();
        }
        super.onDestroy();
    }
}
