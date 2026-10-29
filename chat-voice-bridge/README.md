# Chat音声ブリッジ（Android） — v0.1

**対象はChatGPTアプリの通常のChatです。Work・Codex・OpenAI APIは使いません。**

このアプリは2種類の機能を提供します。

1. **音声入力**: Androidの `SpeechRecognizer` を使用した「Chat音声キーボード」（IME）。ChatGPTのテキスト入力欄に認識結果を `InputConnection.commitText` で挿入します。最終的な送信はユーザーがChatGPT側で行います。
2. **音声出力**: ChatGPTの返答を選択・共有またはコピーしてアプリで `TextToSpeech` に読み上げさせます。長文は最大2000 UTF-16 code unitsで分割し、キュー再生します。

**ChatGPT Liveの音声時間を消費しない独立したOS入出力**ですが、ChatGPT通常Chatのメッセージ制限を解除しません。ChatGPTのプランや既存会話履歴には手を加えません。

## セットアップ（Android 8以降、Android 17想定）

1. APKをインストールして `Chat音声ブリッジ` を開く。
2. `マイクの利用を許可` を押す。
3. `音声キーボードを有効にする` → Android設定で `Chat音声キーボード` を有効化。
4. `音声キーボードに切り替える` → `Chat音声キーボード` を選択。
5. `ChatGPTのChatを開く` → 入力欄をタップし、キーボードの `🎙音声入力` を押す。
6. テキストを確認・訂正してChatGPTの送信ボタンをタップ。
7. 読み上げたい返答を選択してAndroidの共有メニューから `Chat音声ブリッジ` を選ぶ。共有不可ならコピーし、アプリに戻って `クリップボードから取得` → `読み上げる`。

> ChatGPTアプリのUI/Androidの選択メニューにより、返答テキストを直接共有できない場合があります。この場合はコピー経由を利用してください。

## プライバシーと制約

- ChatGPTの内部API、AccessibilityService、ログイン資格情報、トークン、画面の自動読み取りを使いません。
- ローカル端末認識 `createOnDeviceSpeechRecognizer` が提供される端末ではそれを使用し、なければ標準認識サービスにフォールバックします。標準認識サービスはネットワークへ音声を送信する場合があります。
- Androidの音声合成エンジンの日本語音声データが必要な場合があります。
- AndroidのIMEの有効化は個人情報入力に関する警告が表示されます。このキーボードは入力された内容を送信・保存するコードを持ちません（音声認識サービスの通信は別です）。
- 音声はタップ開始・認識終了の単発入力です。常時録音はしません。
- AndroidのTextToSpeechの音声出力は端末のTTS設定に従います。
- ChatGPT音声チャットと同じ遅延・割り込み機能はありません。自動読み上げや自動送信はv0.1には含まれません。
- 現在の会話履歴を保持するのはChatGPTアプリ自体であり、本アプリはChatGPT内部の履歴を読みません。

## ビルド

Android SDK 36、JDK 17、Gradle 8.13が必要です。

```bash
bash scripts/test-core.sh
gradle :app:assembleDebug
```

デバッグビルドはAndroid SDKの開発用鍵で自動署名されます。配布用署名とは異なります。

GitHub Actionsの `Chat Voice Android` ワークフローはテスト→debug APK生成→Artifacts添付を行います。
本番用署名ビルドにはGitHub Actions secretsに以下が必要です。

- `CHAT_VOICE_KEYSTORE_BASE64`
- `CHAT_VOICE_STORE_PASSWORD`
- `CHAT_VOICE_KEY_ALIAS`
- `CHAT_VOICE_KEY_PASSWORD`

これらが揃っている場合のみ署名済みrelease APKを作成し、署名検証し、Artifactsとして提供します。鍵をソース/ログ/APKに書き込みません。秘密鍵をコミットしないでください。

## 実装構造

```
app/src/main/java/jp/three_domain/chatvoice/
  MainActivity.java       Android TTS / share and clipboard text reader
  VoiceInputMethod.java   Android speech-to-text IME
  SpeechTextFormatter.java  Pure Java long-reply chunker
app/src/main/res/xml/method.xml
.github/workflows/chat-voice-android.yml
scripts/test-core.sh
```

## 検証条件

- `scripts/test-core.sh`: Java標準ライブラリのみの分割テスト、長文、絵文字サロゲートペア、空文字等。
- APKビルド・Pixel 10実機マイク・ChatGPT入力欄へのIME挿入・TTS動作は別途実機/Actions検証が必要。
