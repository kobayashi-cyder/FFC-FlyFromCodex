# MarkerTap v1.157 — CM2秒検出後のHOME→対象ゲーム停止→登録Intent（署名済みプレリリース）

**[▶ MarkerTap v1.157 署名済みAPKを直接ダウンロード](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/releases/download/markertap-v1.157-rc1/MarkerTap-v1.157-integrated-signed.apk)**

[公開Release](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/releases/tag/markertap-v1.157-rc1) / [SHA-256チェックサム](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/releases/download/markertap-v1.157-rc1/SHA256SUMS-v1.157.txt) / [DEX実ビルド・監査](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/actions/runs/41728042799) / [元証明書での署名と公開照合](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/actions/runs/41728868632) / [PR #41](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/pull/41)

既知ミニゲームについて、新しい画像2枚以上でCM表示が2秒続いたとき、HOME表示を確認→当該ゲームのみADBでforce-stop→登録済みIntentで再起動するDEX回復ブリッジを、実配布v1.156 APKからSHA固定で統合しました。物理画面・仮想画面・ADB実行部の元メソッドを保存した監査が通過しています。HOMEが確認できない仮想画面や、未接続ADBでは強制停止せず保留します。他ゲームの停止を避け、1時間などの無限ループにならないよう再試行を制限しています。

versionName **1.157**、versionCode **160**、パッケージ jp.tapmarker.lite。署名済みSHA-256：0108aadfa717c676dfe30b4f678a5c1526277e11b01ae16f85317d8b8dd50f7d。元のMarkerTap証明書に一致するAPK v2/v3署名済みです。通常の上書き更新が可能です。

**未確認事項：** Pixel 10 / Android 17、仮想2画面、広告離脱とゲーム再開、報酬の実加算、収納後通信や長時間安定は実機未検証です。開拓うさぎのミッション報酬UI認識はPRにソース候補を置いていますが、v1.157の署名済みDEX APKにはその追加候補は含まれません。広告視聴が報酬条件の場合、2秒後の中断で報酬が得られなくなる可能性があります。実機確認まではPRをDraftに維持し、安定版には昇格しません。


---

# MarkerTap v1.151 — 仮想画面ごとの処理分離（署名済み検証版）

## 公開済み署名APK（検証版・安定版未昇格）

**[MarkerTap v1.151-rc1 署名済みAPK直接ダウンロード](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/releases/download/markertap-v1.151-rc1/MarkerTap-v1.151-integrated-signed.apk)**

[公開Release](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/releases/tag/markertap-v1.151-rc1) / [署名・ダウンロードのCI検証](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/actions/runs/41572253002) / [チェックサム](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/releases/download/markertap-v1.151-rc1/SHA256SUMS-v1.151.txt)

署名済みAPK SHA-256 `ae2db2972780e50966f26243ab5fd8f2f6d9106056eeda233a2d75a1f9535dab`。元の証明書と一致するv2/v3署名、ZIP CRC、ホスト試験、DEX統合監査および公開後の原本一致を検証。Pixel 10 / Android17の実機・仮想2画面・収納後のオフライン解消・広告復帰は未検証。既存の1.150の配布は変更していません。



- [今回の修正仕様・未検証項目](markertap/v1.145-physical-return/v1.151-virtual-lanes.md)
- [v1.151統合APKビルド成功（未署名・実機未検証）](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/actions/runs/41562962175)
- [v1.151検証専用のCIアーティファクト（更新インストール不可）](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/actions/runs/41562962175/artifacts/13198131860)
- [v1.151ホスト・DEXの検証ワークフロー](.github/workflows/markertap-v1151-isolation.yml)
- [収納時オフライン・仮想2画面の不具合記録](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/issues/33)
- [既存のv1.150署名済みAPK（修正前）](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/releases/tag/markertap-v1.150)

収納後のゲーム内オフライン表示は未解決です。今回の変更により、各仮想画面の画像取得を最大2件まで独立処理し、取得待ちの連鎖を減らす設計としました。画面を収納しても実行状態は変更せず、電話側プレビューのレイアウトを保持する修正を追加しました。未署名APK SHA-256: `54cfccffa8bd6b5149fb2da563088b1cd3dde85037f567a33182308619d8befe`。元の署名鍵で署名したAPKは公開済みですが、実機接続がないため完全自律・通信維持・Android実機テスト済みとは表記しません。

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
