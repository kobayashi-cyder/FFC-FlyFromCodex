# MarkerTap v1.151 — 仮想画面ごとの処理分離（未公開候補）

**このブランチはソース修正・回帰テスト段階です。v1.151の署名済みAPKはまだ公開していません。**

- [今回の修正仕様・未検証項目](markertap/v1.145-physical-return/v1.151-virtual-lanes.md)
- [v1.151ホスト・DEXの検証ワークフロー](.github/workflows/markertap-v1151-isolation.yml)
- [収納時オフライン・仮想2画面の不具合記録](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/issues/33)
- [既存のv1.150署名済みAPK（修正前）](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/releases/tag/markertap-v1.150)

収納後のゲーム内オフライン表示は未解決です。今回の変更により、各仮想画面の画像取得を最大2件まで独立処理し、取得待ちの連鎖を減らす設計としました。画面を収納しても実行状態は変更せず、電話側プレビューのレイアウトを保持する修正を追加しました。完全自律・通信維持・Android実機テスト済みとは表記しません。

---

# FFC — FlyFromCodex

FFC development repository. MarkerTap builds and source history are kept on dedicated development branches.

## MarkerTap 最新配布：v1.143（署名済み）

[**署名済みAPKをダウンロード**](markertap/releases/MarkerTap-v1.143-signed.apk?raw=true)

- [更新内容とインストール方法](markertap/v1.143-kaitaku-actions/README.md)
- [署名・ビルド・回帰テストの検証記録](markertap/v1.143-kaitaku-actions/validation-local.json)
- [APKのSHA-256](markertap/releases/SHA256SUMS-v1.143.txt)
- [開発の再開手順](markertap/WORK_NEXT.md) · [再構築スクリプト](markertap/v1.143-kaitaku-actions/build_ci.py)

開拓うさぎの通常連打・CM復帰に、タップ効率、オートタップ強化、左右の追加ボタン、ミッション報酬の確認ループ、名称を取得できるタップミニゲームの処理を追加しました。パッケージは `jp.tapmarker.lite`、versionCodeは `146`。従来と同じ証明書で署名し、上書き更新できます。署名鍵は含みません。

実機入力と実際の報酬加算は未検証です。ミッション一覧の画像は既存の共通見本を照合し、名称を取得できないミニゲームには未対応です。CIで生成する検証APKは未署名なので、配布には上の署名済みAPKを使用してください。
