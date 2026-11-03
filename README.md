# FFC — FlyFromCodex

## MarkerTap v1.151 — 署名済み検証版APK（2026-11-03）

**[▶ MarkerTap v1.151 署名済みAPKを直接ダウンロード](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/releases/download/markertap-v1.151-rc1/MarkerTap-v1.151-integrated-signed.apk)**  
[GitHub Release v1.151-rc1](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/releases/tag/markertap-v1.151-rc1) / [SHA-256チェックサム](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/releases/download/markertap-v1.151-rc1/SHA256SUMS-v1.151.txt) / [署名・公開URL・ダウンロード検証成功](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/actions/runs/41572253002) / [修正PR #34](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/pull/34)

パッケージ `jp.tapmarker.lite`、versionName **1.151** / versionCode **154**、5,651,299 bytes。APK SHA-256 `ae2db2972780e50966f26243ab5fd8f2f6d9106056eeda233a2d75a1f9535dab`。既存のMarkerTapと同じ署名証明書（SHA-256 `7f42d636e4d3c251f475eb998fbe8e92b515ded8bce3ff4d30aa90b9a738cfb1`）を検証し、v2/v3署名、ZIP CRC、DEX監査・ホスト試験、公開APKのバイト一致を確認。

**検証版のため安定版への置き換えは未実施。** Pixel 10 / Android 17の仮想1・2画面、収納・復元後のオフライン表示、広告からの復帰、報酬回収、継続稼働は実機未検証。現行v1.150の配布リンクも引き続き利用できます。秘密の署名鍵は公開されていません。

## MarkerTap v1.150 — 署名済みAPK直接ダウンロード（2026-11-03）

**[▶ MarkerTap v1.150 署名済み統合APKをダウンロード](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/releases/download/markertap-v1.150/MarkerTap-v1.150-integrated-signed.apk)**  
[GitHub Release v1.150](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/releases/tag/markertap-v1.150) / [SHA-256チェックサム](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/releases/download/markertap-v1.150/SHA256SUMS-v1.150.txt) / [公開・ダウンロード照合成功](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/actions/runs/41548356954)

パッケージ `jp.tapmarker.lite`、versionName **1.150** / versionCode **153**、5,647,203 bytes。APK SHA-256 `58e6cd830350473dad218352b3e23f4efcb214b2624e1122823ceb1093345067`。旧版と同じ署名証明書・APK v2/v3・16KiB整列・ZIP CRC・DEX変更監査・ホスト回帰テストを確認。GitHub Releaseから**ログイン不要で実際に取得し、元の署名済みAPKとバイト一致**を確認。

**注意：** Pixel 10 / Android 17での実機自律・仮想2画面の完全動作、広告削減率、実際のゲーム報酬は未検証。現状は署名済み**プレリリース**であり、自律の保証ではありません。

## MarkerTap v1.150 — 高速自律・CM選択回避・仮想2画面改修（2026-11-03）

**[v1.150 改修コード・検証PR #32](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/pull/32)**  
[改修内容・待機時間・ビルド手順を開く](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/blob/markertap/no-redundant-taps-v1.150/markertap/v1.145-physical-return/README.md)  
[GitHub Actions ホストテスト](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/actions/workflows/markertap-v1148-video-modal.yml)  
[Android 35 / DEXパッチャ・署名Releaseパイプライン](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/actions/workflows/markertap-v1150-integrated-release.yml)

開拓うさぎの重複閉じる候補を350/750/1500/2500msに調整し、画面が変化したら即時次操作候補へ。強制CMは消去せず、ユーザー補助で確認できた「このままはじめる」「キャンセル」を優先。通常画面の余計な解析を減らし、仮想画面（最大2）の画像走査間隔を最大負荷に応じて短縮するDEXフックを追加。

**更新情報：** 当初の秘密設定待ちを解消するため、ローカルで元の署名鍵による統合ビルドを実施し、公開用バイナリをGitHub Actionsで再構成してReleaseを公開しました。**実機検証のみ未完了です。** したがって上のリンクは「改修コードを開く」ためのものです。**APK本体はこちら：** [v1.150署名済みAPK](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/releases/download/markertap-v1.150/MarkerTap-v1.150-integrated-signed.apk)。 既存のv1.137署名済みReleaseやv1.149チャット内APKにもv1.150改修は含まれていません。


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
