# BANC888 v7.16.1 — ご指定の熊アイコンを採用

ユーザーが提供した白黒の熊の顔（右目が赤いマーク）を**Androidのホーム画面に表示するアプリアイコン**として設定しました。背景・輪郭・目を保持した軽量なベクター資産で、丸形を含むAndroid 8以降のアダプティブアイコンに対応しています。

- **[最新版の署名済みdebug APKビルド](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/actions/workflows/banc888-threaded-apk.yml?query=branch%3Abanc888-apk-user-icon-v1)** — Artifactsから **BANC888-v7.16-selfheal-debug** を選んでダウンロード
- **[ランチャーアイコンのAndroidエミュレータ検査](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/actions/workflows/banc888-icon-android.yml?query=branch%3Abanc888-apk-user-icon-v1)**
- [自律復旧版の機能と制約](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/blob/banc888-selfhealing-ui-v1/README.md)

変更箇所：`app/src/main/res/drawable/banc888_user_icon.xml`、`banc888_icon_foreground.xml`、`mipmap-anydpi-v26/ic_launcher.xml`、`AndroidManifest.xml`。

v7.16.1のデバッグ用アプリIDは`com.ffc.banc888.fly.selfheal`です。CIが生成するデバッグ署名は以前のビルドと一致するとは限らないため、更新時に署名不一致が出た場合は既存の会話をJSONで保存してから変更してください。元の画像の細かな陰影は軽量ベクター化のため一部簡略化されています。

なお、SolZeroの実装が入った権限付きリポジトリは確認できず、BANC888とのプログラム統合は行っていません。今回の改修は**ランチャーアイコンのみ**です。

---

# BANC888 v7.16 — 自律復旧型の操作画面（実験版）

このブランチはv7.15で発生した「上部の会話操作は見えるのに、本文入力欄・送信ボタン・会話履歴が見えない」問題を優先して改修しています。

**画面が欠けても操作できる状態への自律復旧**が目的であり、人工汎用知能（AGI）の達成を意味しません。

## 2026-11-05 無反応報告への追加修正

実画面録画（簡易操作モードのまま静止、標準画面未復旧）を受け、救済操作画面に「診断」ボタンを追加しました。実行エンジン/標準UIの接続状態と復旧エラーを表示し、バックエンドが応答しない場合は45秒（通常・コード）/60秒（画像）/120秒（動画）で未完了として終了します。失敗時の入力を保持し、依頼処理中の新しい入力を勝手に削除しません。ローカルのJS模擬動作検査（診断・送信・未接続・標準画面復帰）は通過しました。

**注意:** 以下の実行ID `42014264789` / `42014264697` の既存APK・エミュレータテストは**追加修正より前**のものです。この修正を含むAPKの署名、CI、Pixel 10実機操作は未検証です。ビルドの成功と実機での作業成功は別々に判定してください。署名が異なるデバッグAPKの上書きは失敗するため、旧版のデータを保存する前に削除しないでください。

## 最新のAPK取得

- **[v7.16 自律復旧APK（署名・パッケージ照合合格）](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/actions/runs/42014264789)** — Artifacts > BANC888-v7.16-selfheal-debug
- **[Android 34上の自律復旧操作テスト合格（1件）](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/actions/runs/42014264697)** — 入力欄消失→簡易画面から送信→標準画面復帰
- [v7.16 自律復旧APKビルド一覧（GitHub Actions）](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/actions/workflows/banc888-threaded-apk.yml?query=branch%3Abanc888-selfhealing-ui-v1)
- [v7.16 Android画面操作テスト](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/actions/workflows/banc888-selfhealing-android.yml?query=branch%3Abanc888-selfhealing-ui-v1)
- [実装と差分（PR #63）](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/pull/63)
- [四機能の能力範囲](docs/UNIFIED_MULTIMODAL_V714.md)
- [デジタル庁デザインシステムに基づくUI設計](docs/DADS_ACCESSIBLE_UI_V715.md)

**APKの取得方法**：成功したActionsの実行を開き、画面下部の Artifacts から BANC888-v7.16-selfheal-debug を選択。ZIPを展開し、APKをインストールしてください。GitHubへのサインインが必要な場合があります。

このAPKは開発用Debug署名。アプリIDは com.ffc.banc888.fly.selfheal で、v7.15とは別アプリとして共存します。既存の会話データを自動移行しません。旧アプリの会話は必要に応じてJSON保存してください。

## 操作方法

正常な場合は従来どおり上部の会話・コード・画像・動画を選択し、入力欄に依頼を書いて「送信」を押します。

通常の会話画面が欠けた場合は、**画面全体にBANC888操作画面（簡易操作モード）が自動表示**されます。上部で会話・コード・画像・動画を選び、テキスト欄に記入して「送信」。これは実際に既存の認知・ツール経路へ送信する操作です。処理できない場合は「未完了」と表示し、入力を保持します。外部モデル未設定の生成機能を成功扱いしません。

簡易操作モードの「自動復旧を再試行」は、再読込せずに画面と必要モジュールの再接続を試みます。アプリは裏側でも定期的にUIを確認します。正常な画面が復帰したら、簡易画面を自動で閉じます。

## 自律復旧の実装

1. チャット入力・送信・ログのDOMの存在と実表示サイズを検証。
2. 欠損を検知したら既存のFFC_THREADSにUI再構築を依頼。会話ログを初期化しない。
3. 必須モジュールが欠けていればAPKに同梱されたローカル資産から最大3回再読込。
4. 復旧できなければ別実装の画面を表示し、利用者の入力と簡易モードの会話履歴をローカル保存。
5. 標準UIが正常に戻ったら簡易画面を自動的に非表示。無限リロード・全データ消去を行わない。

復旧画面は既存の本来の会話履歴とは**別の一時保存領域**を使います。完全な会話統合はまだ実装していません。長期運用前には履歴の移行・共有の追加が必要です。

## テスト

- node tests/ui_recovery.test.cjs
- node tests/dads_ui_contract.test.cjs
- node tests/live_manifest_verify.test.cjs
- Android UiRecoveryIntegrationTest：入力画面を強制的に外す→簡易画面でボタン操作・送信→通常画面へ自律復帰
- 実機Pixel 10/Android 17の確認は別途必要。CIのエミュレータ成功は実機の保証ではありません。
