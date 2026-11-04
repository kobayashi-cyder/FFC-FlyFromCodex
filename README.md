## CIFA v1.161.3 — インストール衝突対策版「ゲット」自動回収候補（2026-11-05）

**[▶ APKを直接ダウンロード（GitHub Release）](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/releases/download/cifa-v1.1613-dailyget-installfix-lab/CIFA-v1.1613-daily-get-installfix-signed.apk)**

- **解決する問題：** 過去のCIFA検証版 `jp.tapmarker.cifa.kaitaku` はCI実行ごとに署名鍵が変わり、インストール済み検証版への上書き時に署名不一致を起こし得ます。今回のAPKは新しい **`jp.tapmarker.cifa.getfix1613`** を使用し、以前の検証版・正式MarkerTapと同居できます。端末に出た実際のインストールエラーは未取得のため、原因を確定したものではありません。
- **ビルド・署名：** [GitHub Actions成功](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/actions/runs/41915385940)。APK v2/v3署名、ZIP CRC、16KB zipalign、ホスト回帰テストに合格。サイズ5,893,020バイト、SHA-256: `e80d39743f033edc1eeed28b596293f0bca03e9ef008c3129a3fb5419f135486`。生成APKのバイト列とGitHub ReleaseのSHA-256は一致。
- **機能：** ミッション画面の達成済み黄色ゲージとオレンジ「ゲット」を確認し、既存の状態遷移に受取候補を渡します。未達成の黄色「プレイ」を報酬と誤認しないようにしています。
- **インストール：** Androidの「ファイル」アプリ→ダウンロード→APKを開き、必要なら設定→アプリ→特別なアプリアクセス→不明なアプリのインストールで使用したアプリを許可。前のCIFA検証版や元のMarkerTapを削除する必要はありません。
- **未検証：** Pixel 10/Android 17での実際のAPKインストール結果、ジェスチャー、広告復帰、報酬受領、長時間運転。安全なテスト専用別パッケージとしての公開であり本番安定版ではありません。CI署名鍵は保存しないため、このパッケージ名でも今後の別ビルドがそのまま更新できるとは限りません。
- [試験リリース](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/releases/tag/cifa-v1.1613-dailyget-installfix-lab) / [改修PR #49](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/pull/49) / [テスト結果・ハッシュ一覧](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/actions/runs/41915385940)


---

## MarkerTap / CIFA v1.161.2 — 開拓うさぎCM検知・タップ効率の修正検証版（2026-11-05）

**[▶ GitHub Actionsで署名済み検証APKのZIPを取得](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/actions/runs/41854514439)**（ページの Artifacts → `CIFA-v1.1612-kaitaku-ad-upgrade-isolated-lab-signed`）

- 検証APK：`CIFA-v1.1612-kaitaku-ad-upgrade-lab-signed.apk` / パッケージ `jp.tapmarker.cifa.kaitaku`。既存の`jp.tapmarker.lite`、`jp.tapmarker.cifa.lab`とは**別アプリ**です。
- 強化操作を通常連打から分離し、送信後は新しい画面で変化を再確認。開拓うさぎのCM終了マーカー認識も有効化し、`-ad`状態への移行を待たずに独立した視覚的CM証拠を扱います。
- Pixel 10/Android 17の実機テスト、強化のゲーム内成立、広告復帰、`FRAME_STALE`の根本解消は**未確認**。広告視聴報酬が必要な場合、途中中断で得られない可能性があります。
- CIテスト：CIFA判断・遷移・UI・開拓うさぎの追加24チェック、署名v2/v3、16KB整列を確認。SHA-256：`ad3d4e2cb64060d2ec4a688c1cdb87c8c1d0b1d4b77cda72854a20576f0f2fb3`。
- このテスト鍵はCIの実行ごとに変わるため、次の検証APKで上書きできない場合があります。メインアプリのデータを移行・上書きしません。
- [改修PR #48](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/pull/48) / [CI実行と検証ログ](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/actions/runs/41854514439)

---

## MarkerTap v1.158 — 報酬評価・最小化UI保持（元証明書で署名済み試験版・2026-11-04）

**[▶ MarkerTap v1.158 署名済みAPKを直接ダウンロード](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/releases/download/markertap-v1.158-preserved-rc1/MarkerTap-v1.158-preserved-original-signed.apk)**

[GitHub Release（v1.158-preserved-rc1）](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/releases/tag/markertap-v1.158-preserved-rc1) / [署名・16KB整合性・versionCode確認の成功ログ](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/actions/runs/41802295093) / [検証JSON](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/releases/download/markertap-v1.158-preserved-rc1/MarkerTap-v1.158-preserved-original-signed.validation.json) / [ソース・改修PR #43](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/pull/43)

- パッケージ `jp.tapmarker.lite`、versionName **1.158**、versionCode **161**。**元のv1.157と同じ署名証明書**でv2/v3署名済み。16KB ZIP配置検査成功。APK SHA-256: `56e13b2249fb9ce8c98f498498ea14146ddf4095307707c6911214f2f5463ef8`。
- 旧v1.157のDEX **1,613クラスと全資産**を保持。**最小化後のドラッグ可能な復帰ボタン**を含むUI 13クラスを保存し、報酬確定の評価関数、物理画面HUD・仮想画面ROI読取、優先順位更新を追加。
- **試験版の制約:** Pixel 10/Android 17実機での操作、報酬の実加算、広告からの回復、長時間自動稼働は未検証。機能を実機で検証できるまで安定版扱いしません。
- 元の署名秘密鍵は公開していません。APKは原本と一致する署名ブロックの再構成からGitHub Actions上でも検証しています。v1.157は別のプレリリースとして保存しています。

---

# MarkerTap v1.157 — CM2秒検出後のHOME→対象ゲーム停止→登録Intent（署名済みプレリリース）

**[▶ MarkerTap v1.157 署名済みAPKを直接ダウンロード](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/releases/download/markertap-v1.157-rc1/MarkerTap-v1.157-integrated-signed.apk)**

[公開Release](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/releases/tag/markertap-v1.157-rc1) / [SHA-256チェックサム](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/releases/download/markertap-v1.157-rc1/SHA256SUMS-v1.157.txt) / [DEX実ビルド・監査](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/actions/runs/41728042799) / [元証明書での署名と公開照合](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/actions/runs/41728868632) / [PR #41](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/pull/41)

**追加検証（2026-11-04）：** [署名済みv1.157 APKをAndroid 15 / API35エミュレーターにインストールし、MainActivityの起動・プロセス生存・クラッシュログ検査が成功](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/actions/runs/41730661486)。Android 17 / Pixel 10の実機およびCM回復と報酬加算の動作は未検証です。

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
