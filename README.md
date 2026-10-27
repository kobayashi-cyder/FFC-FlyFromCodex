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
