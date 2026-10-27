# FFC — FlyFromCodex

FFC development repository. Android APK builds are isolated on dedicated build branches before any merge to main.

## MarkerTap 最新配布：v1.143（署名済み）

[**署名済みAPKをダウンロード**](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/raw/fff7eac5f903ce687767e19f70e6e215fc8fdf5b/markertap/releases/MarkerTap-v1.143-signed.apk)

- [更新内容・インストール手順](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/blob/fff7eac5f903ce687767e19f70e6e215fc8fdf5b/markertap/v1.143-kaitaku-actions/README.md)
- [署名・ビルド・回帰テストの検証記録](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/blob/fff7eac5f903ce687767e19f70e6e215fc8fdf5b/markertap/v1.143-kaitaku-actions/validation-local.json)
- [APKのSHA-256](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/blob/fff7eac5f903ce687767e19f70e6e215fc8fdf5b/markertap/releases/SHA256SUMS-v1.143.txt)
- [最新版のソース](
https://github.com/kobayashi-cyder/FFC-FlyFromCodex/tree/markertap/kaitaku-actions-v1.143/markertap) · [開発の再開手順](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/blob/fff7eac5f903ce687767e19f70e6e215fc8fdf5b/markertap/WORK_NEXT.md)

開拓うさぎの通常連打・CM復帰に、タップ効率、オートタップ強化、左右の追加ボタン、ミッション報酬の確認ループ、名称を取得できるタップミニゲームの処理を追加しました。

パッケージは `jp.tapmarker.lite`、versionNameは `1.143`、versionCodeは `146`。以前と同じ署名証明書による更新APKです。旧版で「全停止」後に上書きインストールしてください。署名鍵は含みません。

v2/v3署名・証明書一致・版番号・16KB整列、追加操作82項目と既存の回帰テストを確認済みです。**実機入力・実際の報酬加算は未検証**です。ミッション一覧は既存の共通見本または名称で照合し、名称を取得できないミニゲームには未対応です。

CIで作る検証APKは未署名です。配布用はページ先頭の署名済みAPKです。
