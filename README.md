# BANC888 Unified v7.14 — 4-mode cognition (experimental)

BANC888 Android の会話・コーディング・画像・動画を、同一のBANC認知ループにつなぎました。**万能AI/AGIを達成したという意味ではありません**。現在の回路は6集約ノード・16接続で、意味分類、一般知識の生成と画像モデルの代替は別の工学的機構が担います。

## Android APKを取得する

- **[v7.14 APK ビルド（署名検証合格）](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/actions/runs/42002425771)** — 画面下の Artifacts から BANC888-v7.14-unified-debug を選び、ZIP内の APK を取り出す。GitHubへのサインインが必要な場合があります。
- **[このブランチの最新APKビルド一覧](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/actions/workflows/banc888-threaded-apk.yml?query=branch%3Abanc888-unified-multimodal-v1)**
- [統合四機能の設計・検証・限界](docs/UNIFIED_MULTIMODAL_V714.md)
- [認知エンジンの設計](docs/COGNITION_ENGINE_V1.md)
- [重み空間プログラミング](docs/WEIGHT_SPACE_PROGRAMMING.md)

APKはAndroid Debug署名付きの開発用で、アプリIDは従来のBANC888 Cognitionとは異なる com.ffc.banc888.fly.unified です。Playストア公開用の署名ではありません。端末でのアップグレード時はバックアップを保管してください。

## チャットから実行する

普段の会話画面で自然言語で依頼します。

- 会話: 「この内容を説明して」
- コーディング: 「PythonでCSVを解析するコードを書いて」
- 画像: 「猫の画像を生成して」
- 動画: 「猫が走る短い動画を生成して」

複数成果物を明示した一括生成依頼も認知エンジンが計画します。実行後、チャットは空応答でないこと、コードは静的検証・レビューに通ったこと、画像はO2内部品質検証に通ったこと、動画は全フレームが完成してMP4の実バイト数があることを確認します。未完了でも「成功」と表示しないようになっています。

Chat/コードの自由生成はユーザーが設定した言語モデルAPIを利用できます。内蔵の言語モデル重みは付属しません。画像は内蔵O2手続き描画であり、写真級の拡散モデルではありません。動画は内蔵O3→MP4、またはユーザーが設定したA1111。外部サービスを使わずあらゆる内容を高品質生成できる能力は未実装です。

## 回帰検証

- node tests/cognition_core.test.cjs
- node tests/cognition_multimodal.test.cjs
- node tests/weight_space.test.cjs
- node tests/live_manifest_verify.test.cjs

端末実機による全機能の品質検証や未知課題への汎用化評価は引き続き必要です。
