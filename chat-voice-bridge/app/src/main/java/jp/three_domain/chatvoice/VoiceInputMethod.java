package jp.three_domain.chatvoice;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.inputmethodservice.InputMethodService;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.graphics.Color;
import android.view.View;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Locale;

/** Dictation-only IME. Does not inspect the host app or auto-submit messages. */
public class VoiceInputMethod extends InputMethodService implements RecognitionListener {
    private SpeechRecognizer recognizer;
    private TextView hint;
    private boolean recording;
    private boolean recognizerOnDevice;

    @Override public View onCreateInputView() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(10), dp(10), dp(10), dp(10));
        root.setBackgroundColor(Color.rgb(233, 238, 243));
        hint = new TextView(this);
        hint.setText("Chat音声キーボード：認識結果は入力欄に入るだけです。内容を確認して送信してください。");
        hint.setTextColor(Color.BLACK);
        hint.setTextSize(14);
        root.addView(hint);
        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(controls);
        button(controls, "🎙 音声入力", () -> startDictation());
        button(controls, "⏹ 停止", () -> stopDictation());
        button(controls, "⌫", () -> deleteOne());
        LinearLayout more = new LinearLayout(this);
        more.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(more);
        button(more, "空白", () -> insert(" "));
        button(more, "改行", () -> insert("\n"));
        button(more, "キーボード切替", () -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            imm.showInputMethodPicker();
        });
        return root;
    }

    private int dp(int d) { return Math.round(d * getResources().getDisplayMetrics().density); }

    private void button(LinearLayout parent, String title, Runnable click) {
        Button b = new Button(this);
        b.setText(title);
        b.setAllCaps(false);
        b.setTextSize(13);
        parent.addView(b, new LinearLayout.LayoutParams(0, dp(52), 1));
        b.setOnClickListener(v -> click.run());
    }

    private void setHint(String text) { if (hint != null) hint.setText(text); }

    private void insert(String text) {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) ic.commitText(text, 1);
    }

    private void deleteOne() {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) ic.deleteSurroundingTextInCodePoints(1, 0);
    }

    private void startDictation() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            setHint("アプリのマイク権限が未許可です。設定画面を開いて許可してください。");
            Intent i = new Intent(this, MainActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this) &&
            !(Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(this))) {
            setHint("Android音声認識サービスが利用できません。");
            return;
        }
        if (recording) { setHint("すでに録音中です。"); return; }
        if (recognizer != null) { recognizer.destroy(); recognizer = null; }
        recognizerOnDevice = Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(this);
        try {
            recognizer = recognizerOnDevice ? SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
                    : SpeechRecognizer.createSpeechRecognizer(this);
            recognizer.setRecognitionListener(this);
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.JAPANESE.toLanguageTag());
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
            recording = true;
            recognizer.startListening(intent);
            setHint(recognizerOnDevice ? "🎙 端末内音声認識中…" : "🎙 Android音声認識中…（音声認識サービスによっては通信を使用）");
        } catch (RuntimeException ex) {
            recording = false;
            setHint("認識を開始できませんでした：" + ex.getClass().getSimpleName());
            if (recognizer != null) { recognizer.destroy(); recognizer = null; }
        }
    }

    private void stopDictation() {
        if (recognizer != null && recording) {
            recognizer.stopListening();
            setHint("音声認識の結果を確定しています…");
        }
    }

    @Override public void onReadyForSpeech(Bundle params) { setHint("発話してください…"); }
    @Override public void onBeginningOfSpeech() { setHint("音声を認識しています…"); }
    @Override public void onRmsChanged(float rmsdB) {}
    @Override public void onBufferReceived(byte[] buffer) {}
    @Override public void onEndOfSpeech() { setHint("結果を処理しています…"); }
    @Override public void onError(int error) {
        recording = false;
        setHint("音声認識エラー：" + error + "。再度🎙を押してください。");
    }
    @Override public void onResults(Bundle results) {
        recording = false;
        ArrayList<String> words = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (words != null && !words.isEmpty()) {
            insert(words.get(0));
            setHint("入力しました。誤認識を修正し、ChatGPTの送信ボタンを押してください。");
        } else setHint("認識結果がありませんでした。");
    }
    @Override public void onPartialResults(Bundle partialResults) {
        ArrayList<String> words = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (words != null && !words.isEmpty()) setHint("認識候補（未確定）：" + words.get(0));
    }
    @Override public void onEvent(int eventType, Bundle params) {}

    @Override public void onFinishInput() {
        stopAndRelease();
        super.onFinishInput();
    }
    @Override public void onDestroy() {
        stopAndRelease();
        super.onDestroy();
    }
    private void stopAndRelease() {
        recording = false;
        if (recognizer != null) {
            recognizer.cancel();
            recognizer.destroy();
            recognizer = null;
        }
    }
}
