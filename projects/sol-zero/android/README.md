# Sol-Zero v0.16 (Android Offline Lab)

このフォルダは**Sol-Zero v0.16の統合HTMLをAndroid WebViewで動かすオフライン検証用APK**です。独自訓練の小規模数値WSP・BANC888の検証専用認知計画・24操作のタスク工房を同梱します。万能AI、自由なコード生成、実写級メディア生成を保証しません。

## 動作条件

- Android 10（API 29）以上。Android 17 / Pixel 10の実機確認は別途必要。
- Android System WebView（現行バージョンが望ましい）が必要。WebM録画等は機種/WebViewバージョンにより制限されます。
- `INTERNET`権限、一般ストレージ権限、アクセシビリティ権限は要求しません。動作中に外部の生成APIやモデルを取得しません。
- 固定パッケージID：`jp.solzero.offline`。CIFA、MarkerTap、BANC888などとは独立しています。

## 使い方

1. [検証済み署名APK（GitHub Release）](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/releases/download/sol-zero-v016-offline-lab/SolZero-v016-Android-offline-debug-signed.apk) をダウンロード。
2. APKファイルをAndroid端末で開いてインストール。検証用署名であるため、旧デバッグ版との上書きは保証されません。
3. アプリを起動すると、統合HTMLがオフラインで開きます。チャット `/net demo` で独自WSP学習、`/cognition demo` でBANC認知計画、上部からタスク工房・メディア工房が利用可能です。
4. プロジェクトZIP・画像・音声などの書き出しは `Downloads/SolZero` へ保存されます。ファイル選択はAndroid標準のシステムピッカーです。

## 再現ビルド

JDK 17、Gradle 8.9、Android SDK Platform 35、Build Tools 35.0.0を使用：

```bash
cd projects/sol-zero/android
gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug
```

ビルド時には `../SolZero_Chat_Offline.html` を `assets/index.html` として組み込みます。CIでAPK内HTMLとリポジトリ内HTMLのSHA-256一致、署名、ZIP16KB alignment、`INTERNET`権限がないことを確認します。さらにKVMを有効にしたAndroid 15エミュレーターで実際のWebView・チャット・学習・認知計画・PNG・Downloadsを実行し、[本流CI #42342318734](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/actions/runs/42342318734) で合格しています。

## 公開APKの整合性

[SHA256SUMS.txt](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/releases/download/sol-zero-v016-offline-lab/SHA256SUMS.txt) のSHA-256を照合してください。公開APKは3,261,821バイト、SHA-256 `becf872e062635d3f770f3cd7c08ca62a7f1ea0147aaed4d9ffb48ab2b88af67` です。

## 重要な制限

- APKはCI実行時に生成する**debug証明書で署名**します。配布アーティファクトは検証用で、製品用秘密鍵を公開リポジトリに置きません。同じ署名が次回以降も維持される保証はなく、上書きインストールの互換性は保証しません。
- アプリをアンインストールすると端末内ローカルデータが失われ得ます。チャット履歴JSONと作品ZIPを別々にバックアップしてください。
- WebViewが動画録画・WebAudio・Blob取り込みに対応しない場合があります。Webブラウザ版のCI成功とAndroid実機の互換性は別物です。
- 本APKは他アプリの画面取得・タップ・自動操作、ファイルの自由走査、ネット検索、万能知能を実装しません。
