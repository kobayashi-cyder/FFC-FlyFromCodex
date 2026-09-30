package com.ffc.banc888.fly;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private SpeechRecognizer recognizer;
    private TextToSpeech tts;
    private boolean ttsReady = false;
    private boolean onDeviceRecognizer = false;
    private boolean recognizerFallbackAttempted = false;
    private long listenGeneration = 0L;
    private long recognizerReadyAtMs = 0L;
    private long speechBeganAtMs = 0L;
    private float maxRmsDb = -120f;
    private int recognitionResultCount = 0;
    private int lastRecognitionErrorCode = 0;
    private String lastRecognitionError = "";
    private String pendingLanguage = "ja-JP";
    private String pendingSpeechText = null;
    private String pendingSpeechLanguage = "ja-JP";
    private double pendingSpeechRate = 1.0;
    private double pendingSpeechPitch = 1.0;
    private String lastSpeechText = "";
    private String lastSpeechLanguage = "ja-JP";
    private double lastSpeechRate = 1.0;
    private double lastSpeechPitch = 1.0;
    private Voice ttsDefaultVoice = null;
    private boolean ttsFallbackAttempted = false;
    private int ttsLanguageStatus = TextToSpeech.LANG_NOT_SUPPORTED;
    private int lastTtsErrorCode = 0;
    private String lastTtsError = "";
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
        // Prefer the platform/default recognition service. "On-device available"
        // only means an engine exists; it does not guarantee the requested
        // language model (for example ja-JP) is installed.
        initRecognizer(false);
    }

    private void initRecognizer(boolean preferOnDevice) {
        destroyRecognizer();
        try {
            if (preferOnDevice
                    && Build.VERSION.SDK_INT >= 31
                    && SpeechRecognizer.isOnDeviceRecognitionAvailable(activity)) {
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
            recognizerReadyAtMs = System.currentTimeMillis();
            setState(State.LISTENING);
            js.eval("window.BANC888_NATIVE_VOICE&&window.BANC888_NATIVE_VOICE.onListening&&window.BANC888_NATIVE_VOICE.onListening()");
        }
        @Override public void onBeginningOfSpeech() {
            speechBeganAtMs = System.currentTimeMillis();
        }
        @Override public void onRmsChanged(float rmsdB) {
            if (Float.isFinite(rmsdB)) maxRmsDb = Math.max(maxRmsDb, rmsdB);
        }
        @Override public void onBufferReceived(byte[] buffer) {}
        @Override public void onEndOfSpeech() { setState(State.WAIT_RESULT); }
        @Override public void onEvent(int eventType, Bundle params) {}

        @Override public void onError(int error) {
            lastRecognitionErrorCode = error;
            lastRecognitionError = recognitionErrorText(error);
            if (shouldFallbackRecognizer(error)) {
                fallbackToSystemRecognizer("error-" + error);
                return;
            }
            setState(State.ERROR);
            js.eval("window.BANC888_NATIVE_VOICE&&window.BANC888_NATIVE_VOICE.onError&&window.BANC888_NATIVE_VOICE.onError("
                    + JSONObject.quote(String.valueOf(error)) + "," + JSONObject.quote(lastRecognitionError) + ")");
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
            recognitionResultCount++;
            lastRecognitionErrorCode = 0;
            lastRecognitionError = "";
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
        recognizerFallbackAttempted = false;
        return startListeningInternal(language);
    }

    private synchronized boolean startListeningInternal(String language) {
        if (state != State.IDLE && state != State.ERROR) return false;
        pendingLanguage = language == null ? "ja-JP" : language;
        if (activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestMicPermission();
            return false;
        }
        if (recognizer == null) initRecognizer(!recognizerFallbackAttempted);
        if (recognizer == null) {
            setState(State.ERROR);
            lastRecognitionErrorCode = -1;
            lastRecognitionError = "Android SpeechRecognizer is unavailable";
            js.eval("window.BANC888_NATIVE_VOICE&&window.BANC888_NATIVE_VOICE.onError&&window.BANC888_NATIVE_VOICE.onError('unavailable','Android SpeechRecognizer is unavailable')");
            return false;
        }

        if (tts != null) tts.stop();
        setState(State.STARTING);
        recognizerReadyAtMs = 0L;
        speechBeganAtMs = 0L;
        maxRmsDb = -120f;
        lastRecognitionErrorCode = 0;
        lastRecognitionError = "";

        Intent intent = recognitionIntent(pendingLanguage);
        final long generation = ++listenGeneration;
        try {
            recognizer.startListening(intent);
            mainHandler.postDelayed(() -> {
                synchronized (SpeechController.this) {
                    if (generation != listenGeneration || state != State.STARTING) return;
                    if (onDeviceRecognizer && !recognizerFallbackAttempted) {
                        fallbackToSystemRecognizer("ready-timeout");
                    } else {
                        lastRecognitionErrorCode = -2;
                        lastRecognitionError = "recognizer did not reach onReadyForSpeech";
                        setState(State.IDLE);
                        js.eval("window.BANC888_NATIVE_VOICE&&window.BANC888_NATIVE_VOICE.onError&&window.BANC888_NATIVE_VOICE.onError('ready-timeout','recognizer did not reach onReadyForSpeech')");
                    }
                }
            }, 3500L);
            return true;
        } catch (Throwable e) {
            lastRecognitionErrorCode = -3;
            lastRecognitionError = e.getMessage() == null ? e.toString() : e.getMessage();
            if (onDeviceRecognizer && !recognizerFallbackAttempted) {
                fallbackToSystemRecognizer("start-exception");
                return true;
            }
            setState(State.ERROR);
            js.eval("window.BANC888_NATIVE_VOICE&&window.BANC888_NATIVE_VOICE.onError&&window.BANC888_NATIVE_VOICE.onError('start',"
                    + JSONObject.quote(lastRecognitionError) + ")");
            setState(State.IDLE);
            return false;
        }
    }

    private Intent recognitionIntent(String language) {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, language == null ? "ja-JP" : language);
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, activity.getPackageName());
        return intent;
    }

    private boolean shouldFallbackRecognizer(int error) {
        if (!onDeviceRecognizer || recognizerFallbackAttempted) return false;
        if (Build.VERSION.SDK_INT < 31) return false;
        return error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED
                || error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE
                || error == SpeechRecognizer.ERROR_SERVER
                || error == SpeechRecognizer.ERROR_SERVER_DISCONNECTED
                || error == SpeechRecognizer.ERROR_CLIENT
                || error == SpeechRecognizer.ERROR_NO_MATCH
                || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT;
    }

    private synchronized void fallbackToSystemRecognizer(String reason) {
        if (recognizerFallbackAttempted) return;
        recognizerFallbackAttempted = true;
        ++listenGeneration;
        try { if (recognizer != null) recognizer.cancel(); } catch (Throwable ignored) {}
        destroyRecognizer();
        setState(State.IDLE);
        initRecognizer(false);
        js.eval("window.BANC888_NATIVE_VOICE&&window.BANC888_NATIVE_VOICE.onStatus"
                + "&&window.BANC888_NATIVE_VOICE.onStatus(" + JSONObject.quote(statusJson()) + ")");
        startListeningInternal(pendingLanguage);
    }

    synchronized void stopListening() {
        ++listenGeneration;
        try { if (recognizer != null) recognizer.cancel(); } catch (Throwable ignored) {}
        if (state != State.SPEAKING) setState(State.IDLE);
    }

    String probeMicrophoneJson() {
        JSONObject o = new JSONObject();
        AudioRecord record = null;
        try {
            if (activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                o.put("ok", false);
                o.put("permission", "denied");
                return o.toString();
            }
            synchronized (this) {
                if (state == State.LISTENING || state == State.STARTING || state == State.WAIT_RESULT) {
                    o.put("ok", false);
                    o.put("error", "recognizer-active");
                    return o.toString();
                }
            }

            final int rate = 16000;
            int min = AudioRecord.getMinBufferSize(
                    rate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
            );
            int bufferSize = Math.max(min > 0 ? min : 0, rate / 2);
            record = new AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    rate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
            );
            boolean initialized = record.getState() == AudioRecord.STATE_INITIALIZED;
            o.put("initialized", initialized);
            o.put("sampleRate", record.getSampleRate());
            o.put("bufferSize", bufferSize);
            if (!initialized) {
                o.put("ok", false);
                o.put("error", "AudioRecord not initialized");
                return o.toString();
            }

            short[] pcm = new short[Math.max(1600, bufferSize / 2)];
            record.startRecording();
            long startedAt = System.currentTimeMillis();
            long deadline = startedAt + 1500L;
            long sumSq = 0L;
            int totalRead = 0;
            int nonZero = 0;
            int peak = 0;
            while (System.currentTimeMillis() < deadline && nonZero < 64) {
                int read = record.read(pcm, 0, pcm.length, AudioRecord.READ_BLOCKING);
                if (read <= 0) continue;
                totalRead += read;
                for (int i = 0; i < read; i++) {
                    int v = pcm[i];
                    if (v != 0) nonZero++;
                    peak = Math.max(peak, Math.abs(v));
                    sumSq += (long)v * (long)v;
                }
            }
            double rms = totalRead > 0 ? Math.sqrt((double)sumSq / totalRead) : 0.0;
            o.put("ok", totalRead > 0);
            o.put("readSamples", totalRead);
            o.put("nonZeroSamples", nonZero);
            o.put("peakAbs", peak);
            o.put("rms", rms);
            o.put("captureMs", System.currentTimeMillis() - startedAt);
        } catch (Throwable e) {
            try {
                o.put("ok", false);
                o.put("error", e.getMessage() == null ? e.toString() : e.getMessage());
            } catch (Exception ignored) {}
        } finally {
            if (record != null) {
                try { record.stop(); } catch (Throwable ignored) {}
                try { record.release(); } catch (Throwable ignored) {}
            }
        }
        return o.toString();
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
            try { ttsDefaultVoice = tts.getDefaultVoice(); } catch (Throwable ignored) {}
            ttsLanguageStatus = tts.setLanguage(Locale.JAPAN);
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
                    handleTtsFailure(utteranceId, TextToSpeech.ERROR);
                }

                @Override public void onError(String utteranceId, int errorCode) {
                    handleTtsFailure(utteranceId, errorCode);
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
            notifyTtsError("TextToSpeech unavailable");
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

        lastSpeechText = text == null ? "" : text;
        lastSpeechLanguage = lang == null ? "ja-JP" : lang;
        lastSpeechRate = rate;
        lastSpeechPitch = pitch;
        ttsFallbackAttempted = false;
        lastTtsErrorCode = 0;
        lastTtsError = "";
        return speakInternal(lastSpeechText, lastSpeechLanguage, lastSpeechRate, lastSpeechPitch, false);
    }

    private synchronized boolean speakInternal(
            String text,
            String lang,
            double rate,
            double pitch,
            boolean fallback
    ) {
        if (tts == null || !ttsReady) return false;
        try {
            if (recognizer != null) recognizer.cancel();

            String clean = text == null ? "" : text.trim();
            List<String> chunks = splitForTts(clean);
            if (chunks.isEmpty()) {
                state = State.IDLE;
                return false;
            }

            Locale locale = Locale.forLanguageTag(lang == null ? "ja-JP" : lang);
            ttsLanguageStatus = tts.setLanguage(locale);

            if (fallback || ttsLanguageStatus < TextToSpeech.LANG_AVAILABLE) {
                Voice fallbackVoice = findFallbackVoice(locale);
                if (fallbackVoice != null) {
                    tts.setVoice(fallbackVoice);
                } else if (ttsDefaultVoice != null) {
                    tts.setVoice(ttsDefaultVoice);
                }
            }

            tts.setSpeechRate((float)Math.max(0.5, Math.min(2.0, rate)));
            tts.setPitch((float)Math.max(0.5, Math.min(2.0, pitch)));
            state = State.SPEAKING;
            ttsRemaining = chunks.size();

            for (int i = 0; i < chunks.size(); i++) {
                String id = (fallback ? "fly-fallback-" : "fly-") + UUID.randomUUID();
                int result = tts.speak(
                        chunks.get(i),
                        i == 0 ? TextToSpeech.QUEUE_FLUSH : TextToSpeech.QUEUE_ADD,
                        null,
                        id
                );
                if (result == TextToSpeech.ERROR) {
                    handleTtsFailure(id, TextToSpeech.ERROR);
                    return false;
                }
            }
            return true;
        } catch (Throwable e) {
            ttsRemaining = 0;
            state = State.IDLE;
            lastTtsError = e.getMessage() == null ? e.toString() : e.getMessage();
            notifyTtsError(lastTtsError);
            return false;
        }
    }

    private Voice findFallbackVoice(Locale locale) {
        try {
            Set<Voice> voices = tts.getVoices();
            if (voices == null || voices.isEmpty()) return null;

            Voice current = null;
            try { current = tts.getVoice(); } catch (Throwable ignored) {}

            Voice sameLanguageNetwork = null;
            for (Voice v : voices) {
                if (v == null || v.getLocale() == null) continue;
                if (!v.getLocale().getLanguage().equals(locale.getLanguage())) continue;
                if (current != null && v.getName().equals(current.getName())) continue;
                if (!v.isNetworkConnectionRequired()) return v;
                if (sameLanguageNetwork == null) sameLanguageNetwork = v;
            }
            return sameLanguageNetwork;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void handleTtsFailure(String utteranceId, int errorCode) {
        String retryText;
        String retryLanguage;
        double retryRate;
        double retryPitch;
        boolean retry;

        synchronized (this) {
            ttsRemaining = 0;
            state = State.IDLE;
            lastTtsErrorCode = errorCode;
            lastTtsError = "TextToSpeech error " + errorCode;
            retry = !ttsFallbackAttempted && lastSpeechText != null && !lastSpeechText.trim().isEmpty();
            if (retry) ttsFallbackAttempted = true;
            retryText = lastSpeechText;
            retryLanguage = lastSpeechLanguage;
            retryRate = lastSpeechRate;
            retryPitch = lastSpeechPitch;
        }

        if (retry) {
            activity.runOnUiThread(() -> {
                try { if (tts != null) tts.stop(); } catch (Throwable ignored) {}
                boolean accepted = speakInternal(retryText, retryLanguage, retryRate, retryPitch, true);
                if (!accepted && lastTtsErrorCode == errorCode) {
                    notifyTtsError(ttsErrorDetails(errorCode));
                }
            });
            return;
        }

        notifyTtsError(ttsErrorDetails(errorCode));
    }

    private String ttsErrorDetails(int errorCode) {
        String defaultVoice = "";
        String voice = "";
        try {
            Voice v = ttsDefaultVoice;
            defaultVoice = v == null ? "" : v.getName();
        } catch (Throwable ignored) {}
        try {
            Voice v = tts == null ? null : tts.getVoice();
            voice = v == null ? "" : v.getName();
        } catch (Throwable ignored) {}
        return "TextToSpeech error code=" + errorCode
                + " / langStatus=" + ttsLanguageStatus
                + (defaultVoice.isEmpty() ? "" : " / defaultVoice=" + defaultVoice)
                + (voice.isEmpty() ? "" : " / voice=" + voice);
    }

    private void notifyTtsError(String message) {
        js.eval("window.BANC888_NATIVE_VOICE&&window.BANC888_NATIVE_VOICE.onTtsError"
                + "&&window.BANC888_NATIVE_VOICE.onTtsError("
                + JSONObject.quote(message == null ? "TextToSpeech error" : message) + ")");
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
            o.put("recognizerFallbackAttempted", recognizerFallbackAttempted);
            o.put("recognizerReadyAtMs", recognizerReadyAtMs);
            o.put("speechBeganAtMs", speechBeganAtMs);
            o.put("maxRmsDb", maxRmsDb);
            o.put("recognitionResultCount", recognitionResultCount);
            o.put("lastRecognitionErrorCode", lastRecognitionErrorCode);
            o.put("lastRecognitionError", lastRecognitionError);
            o.put("state", state.name());
            o.put("ttsReady", ttsReady);
            o.put("ttsPending", pendingSpeechText != null);
            o.put("ttsLanguageStatus", ttsLanguageStatus);
            o.put("ttsFallbackAttempted", ttsFallbackAttempted);
            o.put("lastTtsErrorCode", lastTtsErrorCode);
            o.put("lastTtsError", lastTtsError);
            o.put("ttsDefaultVoice", ttsDefaultVoice == null ? JSONObject.NULL : ttsDefaultVoice.getName());
            Voice v = ttsReady && tts != null ? tts.getVoice() : null;
            o.put("ttsVoice", v == null ? JSONObject.NULL : v.getName());
            o.put("ttsVoiceNetworkRequired", v == null ? JSONObject.NULL : v.isNetworkConnectionRequired());
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
            case SpeechRecognizer.ERROR_SERVER_DISCONNECTED: return "recognizer server disconnected";
            case SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED: return "language not supported by recognizer";
            case SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE: return "language supported but model unavailable";
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
