# BANC888 Accessible v7.15.1 — モバイル操作改善版（開発試験）

デジタル庁デザインシステムを参考に、Androidで操作が難しかったBANC888の会話・コード・画像・動画のUIを修正した実験版です。**デジタル庁の公式製品・認定製品ではありません。** また、「万能AI（AGI）の完成」ではありません。

## APK取得

- **[検証済み v7.15.1 APK / SHA-256 / 署名レポート（GitHub Actions）](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/actions/runs/42011972127)** — ページ下部の「Artifacts」から「BANC888-v7.15.1-accessible-debug」を選び、ZIPを解凍してAPKを取り出す。GitHubへのログインが必要な場合があります。
- [このブランチのAPKビルド一覧](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/actions/workflows/banc888-threaded-apk.yml?query=branch%3Abanc888-dads-accessible-ui-v1)
- [UI改善の設計・公式指針への対応表](docs/DADS_ACCESSIBLE_UI_V715.md)
- [4機能と認知ループの仕様](docs/UNIFIED_MULTIMODAL_V714.md)
- [認知エンジン仕様](docs/COGNITION_ENGINE_V1.md)

## 使い方

1. **会話 / コード / 画像 / 動画** のどれかを上部で選ぶ。
2. 入力欄で依頼内容を入力・編集する。選択だけでは勝手に送信しない。途中まで書いた文章は切替で消さない。
3. 大きな青い **「送信」** ボタンを押す。資料を送るときは「添付」、音声入力には「音声」。
4. 会話履歴は「☰ 会話」、その他の処理設定は「設定」から開く。

[Android統合検査 42012447522](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/actions/runs/42012447522) では、ChatControlsIntegrationTest 3件がすべてPASSしました。対象は4モードの実タップ・送信、オンライン/オフラインおよび設定操作、Androidのファイル選択・添付・要約です。並列18件中17件は成功、SpeechRecognizer PCMの別テスト1件は不合格です。マイク音声入力まで正常と保証するものではありません。Pixel 10 / Android 17実機での最終試験は未実施です。

## 技術上の扱い

- バージョンは7.15.1-accessible、アプリIDは com.ffc.banc888.fly.accessible。前段のUnified v7.14とは別アプリとしてインストール。
- Android Debug署名による実験用APKであり、正式ストア公開用の署名ではありません。GitHub Actionsの実行ごとにデバッグ署名鍵が異なる場合があるため、既存の「BANC888 Accessible」へ上書きできない場合は**先に会話JSONをエクスポート**してから旧アプリをアンインストールする。削除するとローカルデータが失われるおそれがあります。
- 内蔵画面にはO2手続き描画とO3動画出力があり、自由な言語・コード生成の高品質化はユーザー設定のモデルAPIに依存。写真級画像・一般動画モデル・AGIは付属しない。
- CSSは初回描画前に読み込み、タッチ座標のレイアウト変更を抑止。最小44/48 CSS pxのタップ領域、明瞭なラベル、16px入力、フォーカス可視化、端末safe-areaを採用。

## 検査

- `node tests/dads_ui_contract.test.cjs`
- `node tests/live_manifest_verify.test.cjs`
- `node tests/cognition_core.test.cjs`
- `node tests/cognition_multimodal.test.cjs`
- [Android画面操作・添付・会話互換性検査](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/actions/runs/42011972113)

未知課題への汎用知能や画像・映像の意味理解は、別途独立評価を要します。
