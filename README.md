# FFC — FlyFromCodex


## MarkerTap：開拓うさぎの「このままはじめる」修正（2026-11-02）

**[v1.149候補・改修PR #30（ソースと試験）](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/pull/30)**  
[修正内容と未解決点](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/blob/markertap/dialog-resume-v1.149/markertap/v1.145-physical-return/v1.149-dialog-resume.md)

「タップ２倍／キャンセル」および「コインアップチャンス／このままはじめる」の取り逃がしを調査。背景のデイリーミッション文言による誤判定、説明文が取得できない場合のコインアップ分類、表記ゆれについてソース修正とホスト試験を追加。

**v1.149署名済み開発候補はChatGPT会話内で作成済み、ファイルライブラリに保存済みです。** APK SHA-256 `0e9d621a2243d967aef4117369edd94f74c848c41c41a4150096d33e1646b500`（5,640,586 bytes）。元証明書一致・署名v2/v3・ZIP CRCは確認しました。[検証記録](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/blob/markertap/dialog-resume-v1.149/markertap/v1.145-physical-return/v1.149-signed-validation.json)。**ただしGitHub ReleaseへのAPK本体登録は未完了で、READMEからの直接APK取得はできません。** ソース側の表記ゆれ改修はこの候補APKに未収録です。ボタン矩形が非公開の場合の画像認識・実機完全自律・報酬反映も未検証です。ユーザーのChatGPT会話内APKを取得してください。旧v1.148のAPKには今回の修正は含まれません。

MarkerTap、CIFAなどの開発リポジトリ。

## MarkerTap v1.148 — 開拓うさぎ動画対応（2026-11-02）

**[v1.148のソース・改修PR #29](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/pull/29)**  
**[v1.148ビルド・署名検証記録](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/blob/markertap/kaitaku-video-modal-v1.148/markertap/v1.145-physical-return/v1.148-signed-validation.json)**  
**[v1.148 APKのSHA-256](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/blob/markertap/kaitaku-video-modal-v1.148/markertap/v1.145-physical-return/v1.148-SHA256SUMS.txt)**

v1.148の**署名済み統合APK**はユーザー本人のChatGPT会話内の添付として作成・提供済みで、ChatGPTファイルライブラリにも `MarkerTap-v1.148-integrated-signed.apk` として保存済み。パッケージは `jp.tapmarker.lite`、versionCode 151、サイズ5,647,203 bytes。元の証明書と一致し、APK v2/v3署名、16KiB整列、ZIP整合性とホスト回帰を検証。**Android実機とゲームの完全自律は未検証。**

**配布上の制限:** このGitHubリポジトリにはv1.148のAPKバイナリ自体をまだアップロードできていません。ここに存在しないGitHub ReleaseのURLや、未署名のCI成果物をダウンロードリンクとして掲示しません。したがって **READMEからv1.148 APK本体を直接ダウンロードする要件は未完了**。上のリンクからはソース・検証結果を開けます。APK本体はChatGPT会話内で取得してください。GitHub Releasesへ実ファイルを公開しHTTPダウンロード/ハッシュ照合でき次第、正式な直接リンクへ切替対象とします。

## GitHubで現在ダウンロード可能な旧版

[MarkerTap v1.137 署名済みAPK（GitHub Release）](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/releases/download/markertap-v1.137/MarkerTap-v1.137-signed.apk)（**v1.148ではありません**）。最新の開拓うさぎ・最小化フロート変更を含まないため、今回の更新目的では上記v1.148を使用してください。

[過去のRelease一覧](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/releases)。
