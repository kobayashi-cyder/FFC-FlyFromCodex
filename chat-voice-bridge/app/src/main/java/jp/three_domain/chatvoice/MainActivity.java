package jp.three_domain.chatvoice;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int AUDIO_REQUEST = 501;
    private EditText reply;
    private TextView status;
    private TextToSpeech tts;
    private boolean ttsReady;
    private String pendingSharedText;
    private int utteranceCounter;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildView();
        tts = new TextToSpeech(this, result -> {
            ttsReady = (result == TextToSpeech.SUCCESS);
            if (ttsReady) {
                int lang = tts.setLanguage(Locale.JAPANESE);
                if (lang == TextToSpeech.LANG_MISSING_DATA || lang == TextToSpeech.LANG_NOT_SUPPORTED) {
                    status.setText("日本語の音声合成データがありません。Androidの設定から音声データを導入してください。");
                } else {
                    status.setText("Android音声合成の準備ができました。");
                }
                if (pendingSharedText != null) {
                    String text = pendingSharedText;
                    pendingSharedText = null;
                    speak(text);
                }
            } else {
                status.setText("Android音声合成を初期化できません。");
            }
        });
        processIntent(getIntent());
    }

    private void buildView() {
        ScrollView scroller = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(24));
        root.setBackgroundColor(Color.rgb(250, 250, 251));
        scroller.addView(root);
        addText(root, "Chat音声ブリッジ", 24, true);
        addText(root, "通常のChatGPTのChatを利用します。音声入力はAndroidキーボード、読み上げはAndroidの音声合成です。Work・Codex・APIキーは不要。", 15, false);
        addText(root, "① Android音声入力", 19, true);
        addButton(root, "マイクの利用を許可", () -> {
            if (Build.VERSION.SDK_INT >= 23) requestPermissions(new String[]{ Manifest.permission.RECORD_AUDIO }, AUDIO_REQUEST);
        });
        addButton(root, "音声キーボードを有効にする", () -> startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));
        addButton(root, "音声キーボードに切り替える", () -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            imm.showInputMethodPicker();
        });
        addButton(root, "ChatGPTのChatを開く", this::openChat);
        addText(root, "ChatGPTの入力欄で🎙ボタンを押し、認識した文章を確認してからChatGPT側で送信してください。", 14, false);
        addText(root, "② Android音声読み上げ", 19, true);
        addText(root, "ChatGPTの返答を選択して『共有』→『Chat音声ブリッジ』を選ぶか、コピーしてここで『クリップボードから取得』を押してください。", 14, false);
        reply = new EditText(this);
        reply.setMinLines(4);
        reply.setMaxLines(10);
        reply.setHint("読み上げたい返答テキスト");
        reply.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        root.addView(reply, new LinearLayout.LayoutParams(-1, -2));
        addButton(root, "クリップボードから取得", () -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            ClipData data = cm.getPrimaryClip();
            if (data == null || data.getItemCount() == 0) { status.setText("クリップボードにテキストがありません。"); return; }
            CharSequence value = data.getItemAt(0).coerceToText(this);
            reply.setText(value == null ? "" : value);
        });
        addButton(root, "🔊 テキストを読み上げる", () -> speak(reply.getText().toString()));
        addButton(root, "⏹ 読み上げを停止", () -> { if (tts != null) tts.stop(); });
        status = addText(root, "Android音声合成を準備しています。", 14, false);
        addText(root, "プライバシー：アプリはChatGPTの認証情報・会話履歴を取得しません。音声認識サービスがネットワークを使う場合があります。ChatGPTのテキスト利用上限は変わりません。", 13, false);
        setContentView(scroller);
    }

    private TextView addText(LinearLayout root, String content, int size, boolean bold) {
        TextView t = new TextView(this);
        t.setText(content);
        t.setTextSize(size);
        t.setTextColor(Color.rgb(26, 30, 36));
        if (bold) t.setTypeface(null, 1);
        t.setPadding(0, dp(8), 0, dp(8));
        root.addView(t, new LinearLayout.LayoutParams(-1, -2));
        return t;
    }

    private void addButton(LinearLayout root, String title, Runnable action) {
        Button b = new Button(this);
        b.setText(title);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);
        root.addView(b, new LinearLayout.LayoutParams(-1, -2));
        b.setOnClickListener(v -> action.run());
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private void openChat() {
        Intent chat = getPackageManager().getLaunchIntentForPackage("com.openai.chatgpt");
        if (chat != null) startActivity(chat);
        else startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://chatgpt.com/")));
    }

    private void processIntent(Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        CharSequence text = null;
        if (Intent.ACTION_SEND.equals(action) && "text/plain".equals(intent.getType())) {
            text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
        } else if (Intent.ACTION_PROCESS_TEXT.equals(action)) {
            text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT);
        }
        if (text != null && !text.toString().isEmpty()) {
            reply.setText(text);
            if (ttsReady) speak(text.toString());
            else pendingSharedText = text.toString();
        }
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        processIntent(intent);
    }

    private void speak(String text) {
        if (text == null || text.trim().isEmpty()) { status.setText("読み上げる文章を入力してください。"); return; }
        if (!ttsReady || tts == null) { status.setText("音声合成の準備ができていません。"); return; }
        int max = Math.min(TextToSpeech.getMaxSpeechInputLength(), 2000);
        List<String> pieces = SpeechTextFormatter.chunks(text, max);
        tts.stop();
        for (int i = 0; i < pieces.size(); ++i) {
            int code = tts.speak(pieces.get(i), i == 0 ? TextToSpeech.QUEUE_FLUSH : TextToSpeech.QUEUE_ADD,
                    null, "chat-voice-" + (++utteranceCounter));
            if (code == TextToSpeech.ERROR) {
                status.setText("読み上げ要求に失敗しました。");
                return;
            }
        }
        status.setText(pieces.size() + " 分割でAndroid音声合成へ送信しました。");
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == AUDIO_REQUEST) {
            boolean granted = results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED;
            status.setText(granted ? "マイク権限が許可されました。" : "マイク権限がないため音声入力は動作しません。");
        }
    }

    @Override protected void onDestroy() {
        if (tts != null) { tts.stop(); tts.shutdown(); }
        super.onDestroy();
    }
}
