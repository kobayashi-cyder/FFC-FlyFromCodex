# MarkerTap v1.102：ロケール対応

`jp.tapmarker.lite` / versionName `1.102` / versionCode `105`

## 表示言語

- 日本語と英語に対応しました。Androidの優先言語から、対応している言語のリソースを選択します。どちらも選べない場合は英語を使います。
- Android 13以降では、アプリの「設定 → 言語 → 言語設定を開く」から、このアプリの言語設定を開けます。「システムのデフォルト」を選ぶと端末の設定に従います。
- Android 12以前では、このボタンから端末の言語設定を開きます。
- ホーム・設定・作成、操作パネル、接続案内、録画解析、状態編集、主要なゲーム進行状態など、605件の表示文言をリソース化しました。
- アプリ名、独自の名前、取得した画面テキスト、Androidからのエラー本文、旧形式の文章コマンドなどには、元の表記が残る場合があります。旧文章コマンドの文法は日本語です。定型指示と図・一覧による編集には英語表示を追加しています。

## 引き継ぐ動作

保存するアプリ識別子、状態ID、画像見本、操作座標、学習内容は翻訳しません。翻訳は表示時に行い、選択肢の順番や保存する値は変えません。v1.101の復帰処理と自動操作の判定は引き継ぎます。

ユーザー補助サービスは、言語だけが変更されたConfiguration通知を受けたとき、パネルと小窓の表示を更新します。この分岐から実行の停止や再学習は要求しません。OSによるプロセス再起動やサービス切断が起こらないことを保証するものではありません。

## 更新する

1. v1.101で「全停止」を押し、仮想画面を閉じます。
2. `MarkerTap-v1.102-locales.apk`を上書きインストールします。
3. ユーザー補助と接続状態を確認して、対象アプリを選び直し開始します。

v1.101と同じ証明書で署名します。アプリの識別子と保存済み設定を引き継ぎます。

## 検証

`UiCatalogTest`は、既定・英語・日本語リソースの完備、605件の対応、構造化された状態表示、改行、識別子の保持を確認します。実際のAPKに`en`・`ja`の構成とlocaleConfigが含まれることも検査します。

実行結果は`VALIDATION-v1.102.json`と`tests-v1.102.log`を参照してください。Android実機は接続されておらず、端末での言語切替、表示の折り返し、連続入力は未確認です。

## 翻訳を追加する

- 短い表示文言：`tools/ui-translations.tsv`
- 長い説明：`tools/ui-translations-long.json`
- 生成：`python3 tools/generate-locales.py`
- Androidリソース：`res/values/strings.xml`、`res/values-en/strings.xml`、`res/values-ja/strings.xml`
- 対応言語一覧：`res/xml/locales_config.xml`

ビルド時にリソースと表示用カタログを再生成します。RクラスはAAPT2が生成します。判定用の日本語を検索置換して翻訳しないでください。

```bash
ANDROID_SDK_ROOT=/path/to/android-sdk \
TAPMARKER_KEYSTORE=/path/to/original-key.jks \
bash build.sh

ANDROID_SDK_ROOT=/path/to/android-sdk bash run-tests.sh
```

設計上の参照：[Androidのローカライズ](https://developer.android.com/guide/topics/resources/localization)、[アプリ別言語設定](https://developer.android.com/guide/topics/resources/app-languages)。
