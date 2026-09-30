package com.ffc.banc888.fly;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

final class SpeechController {
    interface JsSink {
        void eval(String script);
    }

    enum State {
        IDLE, STARTING, LISTENING, WAIT_RESULT, SPEAKING, ERROR
    }

    private final Activity activity;
    private final JsSink js;
    private SpeechRecognizer recognizer;
    private TextToSpeech tts;
    private boolean ttsReady = false;
    private boolean onDeviceRecognizer = false;
    private String pendingLanguage = "ja-JP";
    private String pendingSpeechText = null;
    private String pendingSpeechLanguage = "ja-JP";
    private double pendingSpeechRate = 1.0;
    private double pendingSpeechPitch = 1.0;
    private State state = State.IDLE;
    private int ttsRemaining = 0;

    SpeechController(Activity activity, JsSink js) {
        this.activity = activity;
        this.js = js;
        initRecognizer();
        initTts();
    }

    synchronized State state() {
        return state;
    }

    private synchronized void setState(State next) {
        state = next;
    }

    void initRecognizer() {
        destroyRecognizer();
        try {
            if (Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(activity)) {
                recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(activity);
                onDeviceRecognizer = true;
            } else if (SpeechRecognizer.isRecognitionAvailable(activity)) {
                recognizer = SpeechRecognizer.createSpeechRecognizer(activity);
                onDeviceRecognizer = false;
            }
        } catch (Throwable first) {
            try {
                recognizer = SpeechRecognizer.createSpeechRecognizer(activity);
                onDeviceRecognizer = false;
            } catch (Throwable ignored) {
                recognizer = null;
            }
        }
        if (recognizer != null) recognizer.setRecognitionListener(listener);
    }

    private final RecognitionListener listener = new RecognitionListener() {
        @Override public void onReadyForSpeech(Bundle params) {
            setState(State.LISTENING);
            js.eval("window.BANC888_NATIVE_VOICE&&window.BANC888_NATIVE_VOICE.onListening&&window.BANC888_NATIVE_VOICE.onListening()");
        }
        @Override public void onBeginningOfSpeech() {}
        @Override public void onRmsChanged(float rmsdB) {}
        @Override public void onBufferReceived(byte[] buffer) {}
        @Override public void onEndOfSpeech() { setState(State.WAIT_RESULT); }
        @Override public void onEvent(int eventType, Bundle params) {}

        @Override public void onError(int error) {
            setState(State.ERROR);
            String message = recognitionErrorText(error);
            js.eval("window.BANC888_NATIVE_VOICE&&window.BANC888_NATIVE_VOICE.onError&&window.BANC888_NATIVE_VOICE.onError("
                    + JSONObject.quote(String.valueOf(error)) + "," + JSONObject.quote(message) + ")");
            setState(State.IDLE);
        }

        @Override public void onResults(Bundle results) {
            ArrayList<String> list = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            float[] conf = results.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES);
            JSONArray alternatives = new JSONArray();
            if (list != null) {
                for (int i = 0; i < Math.min(3, list.size()); i++) {
                    JSONObject x = new JSONObject();
                    try {
                        x.put("text", list.get(i));
                        x.put("confidence", conf != null && i < conf.length ? conf[i] : -1f);
                        alternatives.put(x);
                    } catch (Exception ignored) {}
                }
            }
            String best = list != null && !list.isEmpty() ? list.get(0) : "";
            float bestConfidence = conf != null && conf.length > 0 ? conf[0] : -1f;
            setState(State.IDLE);
            js.eval("(function(){var n=window.BANC888_NATIVE_VOICE;if(!n)return;"
                    + "n.onAlternatives&&n.onAlternatives(" + alternatives.toString() + ");"
                    + "n.onResult&&n.onResult(" + JSONObject.quote(best) + "," + bestConfidence + ");})()");
        }

        @Override public void onPartialResults(Bundle partialResults) {
            ArrayList<String> list = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            String text = list != null && !list.isEmpty() ? list.get(0) : "";
            js.eval("window.BANC888_NATIVE_VOICE&&window.BANC888_NATIVE_VOICE.onPartial&&window.BANC888_NATIVE_VOICE.onPartial("
                    + JSONObject.quote(text) + ")");
        }
    };

    void requestMicPermission() {
        activity.runOnUiThread(() ->
                activity.requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, MainActivity.REQ_MIC));
    }

    synchronized boolean startListening(String language) {
        if (state != State.IDLE && state != State.ERROR) return false;
        if (activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingLanguage = language == null ? "ja-JP" : language;
            requestMicPermission();
            return false;
        }
        if (recognizer == null) initRecognizer();
        if (recognizer == null) {
            setState(State.ERROR);
            js.eval("window.BANC888_NATIVE_VOICE&&window.BANC888_NATIVE_VOICE.onError&&window.BANC888_NATIVE_VOICE.onError('unavailable','Android SpeechRecognizer is unavailable')");
            return false;
        }
        if (tts != null) tts.stop();
        setState(State.STARTING);
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, language == null ? "ja-JP" : language);
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, activity.getPackageName());
        try {
            recognizer.startListening(intent);
            return true;
        } catch (Throwable e) {
            setState(State.ERROR);
            js.eval("window.BANC888_NATIVE_VOICE&&window.BANC888_NATIVE_VOICE.onError&&window.BANC888_NATIVE_VOICE.onError('start',"
                    + JSONObject.quote(e.getMessage() == null ? e.toString() : e.getMessage()) + ")");
            setState(State.IDLE);
            return false;
        }
    }

    synchronized void stopListening() {
        try { if (recognizer != null) recognizer.cancel(); } catch (Throwable ignored) {}
        if (state != State.SPEAKING) setState(State.IDLE);
    }

    void onPermissionResult(boolean granted) {
        if (granted) startListening(pendingLanguage);
        else {
            setState(State.IDLE);
            js.eval("window.BANC888_NATIVE_VOICE&&window.BANC888_NATIVE_VOICE.onError&&window.BANC888_NATIVE_VOICE.onError('permission','microphone permission denied')");
        }
    }

    private void initTts() {
        tts = new TextToSpeech(activity, status -> {
            ttsReady = status == TextToSpeech.SUCCESS;
            if (!ttsReady) {
                String dropped;
                synchronized (SpeechController.this) {
                    dropped = pendingSpeechText;
                    pendingSpeechText = null;
                    state = State.IDLE;
                }
                if (dropped != null) {
                    js.eval("window.BANC888_NATIVE_VOICE&&window.BANC888_NATIVE_VOICE.onTtsError"
                            + "&&window.BANC888_NATIVE_VOICE.onTtsError('TextToSpeech initialization failed')");
                }
                return;
            }
            tts.setLanguage(Locale.JAPAN);
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String utteranceId) {
                    setState(State.SPEAKING);
                    js.eval("window.BANC888_NATIVE_VOICE&&window.BANC888_NATIVE_VOICE.onTtsStart&&window.BANC888_NATIVE_VOICE.onTtsStart()");
                }
                @Override public void onDone(String utteranceId) {
                    boolean done;
                    synchronized (SpeechController.this) {
                        ttsRemaining = Math.max(0, ttsRemaining - 1);
                        done = ttsRemaining == 0;
                        if (done) state = State.IDLE;
                    }
                    if (done) js.eval("window.BANC888_NATIVE_VOICE&&window.BANC888_NATIVE_VOICE.onTtsDone&&window.BANC888_NATIVE_VOICE.onTtsDone()");
                }
                @Override public void onError(String utteranceId) {
                    synchronized (SpeechController.this) {
                        ttsRemaining = 0;
                        state = State.IDLE;
                    }
                    js.eval("window.BANC888_NATIVE_VOICE&&window.BANC888_NATIVE_VOICE.onTtsError&&window.BANC888_NATIVE_VOICE.onTtsError('TextToSpeech error')");
                }
            });
            js.eval("window.BANC888_NATIVE_VOICE&&window.BANC888_NATIVE_VOICE.onStatus&&window.BANC888_NATIVE_VOICE.onStatus("
                    + JSONObject.quote(statusJson()) + ")");

            String queuedText;
            String queuedLanguage;
            double queuedRate;
            double queuedPitch;
            synchronized (SpeechController.this) {
                queuedText = pendingSpeechText;
                queuedLanguage = pendingSpeechLanguage;
                queuedRate = pendingSpeechRate;
                queuedPitch = pendingSpeechPitch;
                pendingSpeechText = null;
            }
            if (queuedText != null && !queuedText.trim().isEmpty()) {
                speak(queuedText, queuedLanguage, queuedRate, queuedPitch);
            }
        });
    }

    synchronized boolean speak(String text, String lang, double rate, double pitch) {
        if (tts == null) {
            js.eval("window.BANC888_NATIVE_VOICE&&window.BANC888_NATIVE_VOICE.onTtsError"
                    + "&&window.BANC888_NATIVE_VOICE.onTtsError('TextToSpeech unavailable')");
            return false;
        }
        if (!ttsReady) {
            pendingSpeechText = text == null ? "" : text;
            pendingSpeechLanguage = lang == null ? "ja-JP" : lang;
            pendingSpeechRate = rate;
            pendingSpeechPitch = pitch;
            state = State.STARTING;
            return true;
        }
        try {
            if (recognizer != null) recognizer.cancel();
            state = State.SPEAKING;
            Locale locale = Locale.forLanguageTag(lang == null ? "ja-JP" : lang);
            tts.setLanguage(locale);
            chooseLocalVoice(locale);
            tts.setSpeechRate((float)Math.max(0.5, Math.min(2.0, rate)));
            tts.setPitch((float)Math.max(0.5, Math.min(2.0, pitch)));
            List<String> chunks = splitForTts(text == null ? "" : text);
            ttsRemaining = chunks.size();
            if (chunks.isEmpty()) {
                state = State.IDLE;
                return false;
            }
            for (int i = 0; i < chunks.size(); i++) {
                String id = "fly-" + UUID.randomUUID();
                tts.speak(chunks.get(i), i == 0 ? TextToSpeech.QUEUE_FLUSH : TextToSpeech.QUEUE_ADD, null, id);
            }
            return true;
        } catch (Throwable e) {
            ttsRemaining = 0;
            state = State.IDLE;
            js.eval("window.BANC888_NATIVE_VOICE&&window.BANC888_NATIVE_VOICE.onTtsError&&window.BANC888_NATIVE_VOICE.onTtsError("
                    + JSONObject.quote(e.getMessage() == null ? e.toString() : e.getMessage()) + ")");
            return false;
        }
    }

    private void chooseLocalVoice(Locale locale) {
        try {
            Set<Voice> voices = tts.getVoices();
            if (voices == null) return;
            Voice preferred = null;
            for (Voice v : voices) {
                if (!v.getLocale().getLanguage().equals(locale.getLanguage())) continue;
                if (!v.isNetworkConnectionRequired()) {
                    preferred = v;
                    break;
                }
                if (preferred == null) preferred = v;
            }
            if (preferred != null) tts.setVoice(preferred);
        } catch (Throwable ignored) {}
    }

    private List<String> splitForTts(String raw) {
        String text = raw.trim();
        if (text.isEmpty()) return Collections.emptyList();
        int max = Math.max(400, Math.min(TextToSpeech.getMaxSpeechInputLength() - 32, 3000));
        List<String> out = new ArrayList<>();
        int pos = 0;
        while (pos < text.length()) {
            int end = Math.min(text.length(), pos + max);
            if (end < text.length()) {
                int cut = Math.max(text.lastIndexOf('。', end), text.lastIndexOf('、', end));
                cut = Math.max(cut, text.lastIndexOf('\n', end));
                if (cut > pos + max / 3) end = cut + 1;
            }
            out.add(text.substring(pos, end));
            pos = end;
        }
        return out;
    }

    String statusJson() {
        JSONObject o = new JSONObject();
        try {
            boolean granted = activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
            o.put("permission", granted ? "granted" : "prompt");
            o.put("speechRecognizerAvailable", recognizer != null);
            o.put("onDeviceRecognitionAvailable", Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(activity));
            o.put("recognizerBackend", onDeviceRecognizer ? "on-device" : "system");
            o.put("state", state.name());
            o.put("ttsReady", ttsReady);
            o.put("ttsPending", pendingSpeechText != null);
            Voice v = ttsReady && tts != null ? tts.getVoice() : null;
            o.put("ttsVoice", v == null ? JSONObject.NULL : v.getName());
            o.put("sdkInt", Build.VERSION.SDK_INT);
            o.put("threadRouter", true);
        } catch (Throwable e) {
            try { o.put("error", e.toString()); } catch (Exception ignored) {}
        }
        return o.toString();
    }

    void simulateRecognition(String text, float confidence) {
        String safe = text == null ? "" : text;
        JSONArray alternatives = new JSONArray();
        JSONObject x = new JSONObject();
        try {
            x.put("text", safe);
            x.put("confidence", confidence);
            alternatives.put(x);
        } catch (Exception ignored) {}
        js.eval("(function(){var n=window.BANC888_NATIVE_VOICE;if(!n)return;"
                + "n.onAlternatives&&n.onAlternatives(" + alternatives.toString() + ");"
                + "n.onResult&&n.onResult(" + JSONObject.quote(safe) + "," + confidence + ");})()");
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

    private void destroyRecognizer() {
        try { if (recognizer != null) recognizer.destroy(); } catch (Throwable ignored) {}
        recognizer = null;
    }

    void destroy() {
        destroyRecognizer();
        if (tts != null) {
            try { tts.stop(); } catch (Throwable ignored) {}
            try { tts.shutdown(); } catch (Throwable ignored) {}
            tts = null;
        }
    }
}
