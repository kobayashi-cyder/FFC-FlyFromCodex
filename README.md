# FFC — FlyFromCodex

## Sol-Zero v0.16 — 学習WSP × BANC888認知 × Android（2026-11-07）

[▶ 統合オフラインHTMLを取得](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/raw/refs/heads/main/projects/sol-zero/SolZero_Chat_Offline.html) / [統合PR #79](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/pull/79) / [詳細と操作方法](projects/sol-zero/README.md)

- **実装済み:** ゼロから訓練する数値WSP重みを `/net demo`・`/net train`・`/net run`、タスク工房の `wsp.learned` に統合。加えてBANC888の認知計画検証コアを `/cognition demo`・`/cognition {JSON}` および `cognition.plan` として移植し、最大16工程・24操作のタスク内で使用できます。既存のチャット、コード、メディア制作機能を維持。BANC計画は予測のみで、OS操作は実行しません。
- **検証:** Node **159/159**・Python **7/7**、Chromiumのモバイル表示試験、単体HTML再生成一致が通過。[Android 15実動作CI #42342318734](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/actions/runs/42342318734) ではAPK署名・WebView起動・チャット・学習WSP・認知計画・タスク工房・PNG・ローカル保存が合格しました。
- **範囲:** BANC888認知コアの一部のみを移植。旧Androidアプリ全体、万能知能、自由な高品質生成AIは未完成です。既存の大規模言語モデルやクラウドAPIは不使用です。

---

## Sol-Zero v0.16 — Androidオフライン検証APK（2026-11-07 公開）

**[▶ Android用署名済みAPKを直接ダウンロード](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/releases/download/sol-zero-v016-offline-lab/SolZero-v016-Android-offline-debug-signed.apk)** / [検証版Release](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/releases/tag/sol-zero-v016-offline-lab) / [SHA-256](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/releases/download/sol-zero-v016-offline-lab/SHA256SUMS.txt) / [仕様](projects/sol-zero/android/README.md)

- **アプリの内容:** Sol-Zeroのチャット・学習WSP・BANC888認知計画・コード生成・24種類のタスク工房・PNG/WAV/GIF/WebM制作を端末内の安全なWebViewに収録。Android 10（API 29）以上が対象。パッケージ `jp.solzero.offline`。
- **完全ローカル:** `INTERNET`権限・アクセシビリティ権限・広域ストレージ権限は不要。外部URLはWebViewで遮断し、保存はAndroid標準の `Downloads/SolZero`、取込はシステムファイル選択を利用します。
- **検査:** [Android APK CI](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/actions/workflows/sol-zero-android.yml) でAPK署名・16KB配置・内蔵HTMLのSHA-256一致・Java単体テストを実施。**Android 15エミュレーターの実動作試験に合格し、同じCIから検証版をGitHub Releaseへ公開済み**です。[検証ログ](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/actions/runs/42342318734)を確認できます。
- **署名と更新:** CIのdebug鍵で署名された**試験用APK**です。将来のAPKと証明書が異なると上書き更新できません。端末データのバックアップ後に必要に応じてアンインストールしてください。
- **未保証:** Android 17 / Pixel 10実機、長時間の安定性、WebMの全WebView互換、自由学習による万能AI。通常のAndroid操作自動化機能はありません。

---

## Sol-Zero v0.15 — 旧版仕様（2026-11-06）

**[▶ 現行版v0.16の単体HTMLを取得](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/raw/refs/heads/main/projects/sol-zero/SolZero_Chat_Offline.html)** / **[ソースと使い方](projects/sol-zero/README.md)** / [GitHub上でファイルを確認](projects/sol-zero/SolZero_Chat_Offline.html)

- **機能:** 会話、数値WSP回路の帰納・実行・Python/JavaScript書き出し、固定8アルゴリズムのコード生成、手続き的なPNG/WAV/GIF/WebM生成・作品ZIP、最大16工程・22操作のCSV/資料/計算/WSP/メディア連結タスク。
- **回帰検査:** [GitHub Actions #42155012701](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/actions/runs/42155012701) でNode **149/149**、Python **7/7**、Chrome画面幅390pxのチャット・WSP・作品生成・ZIP復元・メディア保存・タスク実行が合格。単体HTMLは生成ソースとのバイト一致を確認済み。
- **始め方:** HTMLを保存してブラウザで開き、上部「🧩 タスク工房」→「CSV集計」→「計画を検証」→「計画を実行」。チャットからは `/agent help`、`/code python fibonacci`、`/image 海`。
- **データの安全性:** チャットはJSONバックアップ、作品はZIPバックアップを別に保存。破損した作品ZIPは復元前に拒否し、IndexedDBへの作品差し替えを単一トランザクションで行います。ブラウザの保存設定により一時保存になる場合があります。
- **対象と制約:** HTMLブラウザ版であり**APKではありません**。独自学習の万能AIや自由コード生成・実写級画像動画生成の完成版ではありません。Pixel 10/Android 17での実機動作は未検証です。

---

## CIFA v1.161.8 — 開拓うさぎ98秒動画対応・2画面固定の署名済み検証版（2026-11-05）

**[▶ CIFA v1.161.8 署名済みAPKを直接ダウンロード](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/releases/download/cifa-v1.1618-fullcycle-2display-final-lab/CIFA-v1.1618-fullcycle-2display-signed.apk)**

- **今回の実修正:** 提供された98秒動画を基に、ピンク見出しの「タップ2倍」（黄色「使用する」）と「コインアップチャンス」（下段「このままはじめる」）を別々に認識。ミッション画面の最初の未達ステージ「プレイ」とオレンジ「ゲット」の判定を分離し、汎用ミッション画面判定がゲット候補を先に消していた分岐を修正。旧レイアウトの認識も残しています。
- **従来の制約を保持:** v1.161.6の最大2画面固定を統合。旧設定に3〜4画面が残っていても上限2とし、アプリ選択で3〜4画面オプションを表示せず、過剰数を保存しません。アプリ内表示は「CIFA v1.161.8 (Lab)」です。
- **署名と回帰検証:** [GitHub Actions build #41929592236](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/actions/runs/41929592236) SUCCESS。12系統・**3,413件**のホスト回帰検査に合格。APK v2/v3署名検証、16KB zipalign、ZIP/DEX整合、固有パッケージID検査を通過。
- **配布APK:** 5,897,116バイト、SHA-256 `d4a0295b0f4ed190856eda94f5bdd9347f5f5100cbce92879b4cf8e0713cb8af`。パッケージ `jp.tapmarker.cifa.getfix1618`。CIテスト鍵の別アプリであり、既存の `jp.tapmarker.lite` と以前の検証版を**上書きしません**。初回設定は必要です。異なる署名鍵を用いた将来ビルドで上書き更新できる保証はありません。
- **Android 15エミュレーター起動確認:** [公開署名APKの実インストール・起動CI #41930055312](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/actions/runs/41930055312) SUCCESS。GitHub Releaseから同じAPKをダウンロード→SHA照合→インストール→MainActivity起動→6秒間生存→クラッシュログ検査に合格。保存されたスクリーンショットに **CIFA v1.161.8 (Lab)** と **最大2画面** の表示を確認。これはゲーム自律操作の実機テストではありません。
- **未実施の実機合格条件:** Pixel 10 / Android 17での実際の入力、CMの完走・戻り、タップ効率効果、ゲット報酬の入金、および長時間連続自律稼働。現在の成功はソースの実装とCIシミュレーション・パッケージ検査までであり、完全自律を実機実証したものではありません。
- [GitHub Release](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/releases/tag/cifa-v1.1618-fullcycle-2display-final-lab) / [98秒録画修正 PR #54](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/pull/54) / [2画面安全制限統合 PR #55](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/pull/55) / [SHA256SUMS.txt](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/releases/download/cifa-v1.1618-fullcycle-2display-final-lab/SHA256SUMS.txt)

---

## CIFA v1.161.6 — 開拓うさぎ自律候補・2画面上限・正しいアプリ表示（2026-11-05）

**[▶ CIFA v1.161.6 署名済みAPKを直接ダウンロード](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/releases/download/cifa-v1.1616-two-display-lab/CIFA-v1.1616-two-display-signed.apk)**

- **今回の成果:** 前版v1.161.5の録画に基づく「タップ効率」「オート強化」「コインアップチャンス見送り」認識修正、ミッション「ゲット」候補・受領後重複防止・広告保護を維持。実際のAndroid起動画像で見つかった旧「MarkerTap v1.123」表示を**CIFA v1.161.6 (Lab)**に修正しました。
- **2画面固定上限:** `VirtualLoadPolicy.MAX_LIMIT=2` に変更。旧設定に3〜4画面が残っていても2画面に制限し、選択画面から3〜4画面オプションを削除。過剰な対象アプリ選択も保存しません。
- **CI検証:** [署名APKビルド成功 #41925196933](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/actions/runs/41925196933)。10系統・**3,374**のJavaテスト（そのうち30件がミッション→ゲット→受領確認→復帰→左右強化の一連の模擬シナリオ）に合格。v2/v3署名検証、16KB zipalign、DEX/ZIPと固有パッケージ検査も合格。
- **配布APK:** 5,893,020バイト。SHA-256 `239c13caa6624f935cd2ca64fc0f69edc071e680044fbd4a9a733bc8a15f0b59`（GitHub ReleaseとCI出力で一致）。パッケージID `jp.tapmarker.cifa.getfix1616`。元のMarkerTapや旧検証版を上書きしません。
- **導入:** APKをダウンロードしAndroid「ファイル」から開いてください。必要な場合のみインストール元の許可を設定。初回ユーザー補助・端末内接続設定が必要です。この検証APKはCIの一時鍵による署名のため将来別ビルドを上書きできる保証はありません。
- **Android 15起動実証:** [公開済みAPKをインストールして撮影したエミュレーター検査 #41925548061](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/actions/runs/41925548061) が成功。SHA確認→APKインストール→MainActivity表示→6秒プロセス生存→クラッシュログ空を確認。実画面の見出し **CIFA v1.161.6 (Lab)** と **最大2画面** の説明も撮影画像で確認しました。
- **未検証:** Pixel 10 / Android 17でのゲーム操作、広告視聴後の報酬加算、ミッション「ゲット」の実受領、長時間無人運転。ホスト検査で処理順が通っただけで**完全自律の実機成功とは主張しません**。
- [GitHub Release](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/releases/tag/cifa-v1.1616-two-display-lab) / [改修PR #52](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/pull/52) / [SHA256SUMS.txt](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/releases/download/cifa-v1.1616-two-display-lab/SHA256SUMS.txt)

---

## CIFA v1.161.5 — 開拓うさぎ動画に基づく認識修正・署名済み検証APK（2026-11-05）

**[▶ CIFA v1.161.5 署名済みAPKを直接ダウンロード](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/releases/download/cifa-v1.1615-real-video-pixel-lab/CIFA-v1.1615-real-video-pixel-signed.apk)**

- **用途:** 開拓うさぎのタップ効率・オート強化ボタンの誤検知／見逃し、および「コインアップチャンス!!」の見送り操作の誤認識を、2026-11-05提供動画の画面に合わせて修正。既存の「ゲット」受領確認・期限付き再試行、CM保護処理と統合した別パッケージ検証版です。
- **ビルド・署名:** [GitHub Actions #41921925169](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/actions/runs/41921925169) 成功。9系統・計**2,315**のJava回帰チェック成功。APK署名検証、16KB zipalign、DEX/ZIP構造検査とパッケージID検査を通過。
- **APK:** 5,893,020バイト、SHA-256 `81a0dfabcad7a588e0fe668e5c79fc38ad9599cc14793ffe89d8a89a38db1ab7`（GitHub Releaseのアセットdigestとビルドログが一致）。検証用アプリID `jp.tapmarker.cifa.getfix1615`。元の `jp.tapmarker.lite` は上書きしません。
- **インストール:** 上のAPKを保存しAndroidの「ファイル」アプリから開き、必要な場合のみダウンロード元の「不明なアプリのインストール」を許可してください。以前のCIFA検証版と共存できますが、独立アプリのため設定は自動移行しません。毎回異なるCIテスト鍵で署名されるため、将来のAPKで上書き更新できるとは限りません。
- **Android起動検証:** [Android 15（API 35）エミュレーター #41923377225](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/actions/runs/41923377225) で、**Release公開APKそのもの**のSHA照合・インストール・MainActivity起動・6秒後のプロセス生存・クラッシュログ検査・スクリーンショット保存が成功。
- **検証境界:** 動画のオフライン画像再生で左右強化パネルとコインアップ画面の候補認識を確認。ただし**Pixel 10/Android 17の実機入力、広告視聴完遂、ゲット報酬の実受領、長時間完全自律は未検証**です。完成版や本番安定版としては扱わないでください。
- [GitHub Release](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/releases/tag/cifa-v1.1615-real-video-pixel-lab) / [改修差分 PR #51](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/pull/51) / [SHA256SUMS.txt](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/releases/download/cifa-v1.1615-real-video-pixel-lab/SHA256SUMS.txt)

---

## MarkerTap v1.158 — 報酬評価・最小化UI保持（元証明書で署名済み試験版・2026-11-04）

**[▶ MarkerTap v1.158 署名済みAPKを直接ダウンロード](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/releases/download/markertap-v1.158-preserved-rc1/MarkerTap-v1.158-preserved-original-signed.apk)**

[GitHub Release（v1.158-preserved-rc1）](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/releases/tag/markertap-v1.158-preserved-rc1) / [署名・16KB整合性・versionCode確認の成功ログ](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/actions/runs/41802295093) / [検証JSON](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/releases/download/markertap-v1.158-preserved-rc1/MarkerTap-v1.158-preserved-original-signed.validation.json) / [ソース・改修PR #43](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/pull/43)

- パッケージ `jp.tapmarker.lite`、versionName **1.158**、versionCode **161**。**元のv1.157と同じ署名証明書**でv2/v3署名済み。16KB ZIP配置検査成功。APK SHA-256: `56e13b2249fb9ce8c98f498498ea14146ddf4095307707c6911214f2f5463ef8`。
- 旧v1.157のDEX **1,613クラスと全資産**を保持。**最小化後のドラッグ可能な復帰ボタン**を含むUI 13クラスを保存し、報酬確定の評価関数、物理画面HUD・仮想画面ROI読取、優先順位更新を追加。
- **試験版の制約:** Pixel 10/Android 17実機での操作、報酬の実加算、広告からの回復、長時間自動稼働は未検証。機能を実機で検証できるまで安定版扱いしません。
- 元の署名秘密鍵は公開していません。APKは原本と一致する署名ブロックの再構成からGitHub Actions上でも検証しています。v1.157は別のプレリリースとして保存しています。

---

## MarkerTap v1.157 — CM2秒継続後のHOME→対象ゲーム停止→起動Intent（署名済みプレリリース・2026-11-04）

**[▶ MarkerTap v1.157 署名済みAPKを直接ダウンロード](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/releases/download/markertap-v1.157-rc1/MarkerTap-v1.157-integrated-signed.apk)**

[GitHub Release v1.157-rc1](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/releases/tag/markertap-v1.157-rc1) / [SHA-256チェックサム](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/releases/download/markertap-v1.157-rc1/SHA256SUMS-v1.157.txt) / [DEX統合・ホスト試験](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/actions/runs/41728042799) / [署名・公開ファイル照合](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/actions/runs/41728868632) / [改修PR #41](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/pull/41)

**追加検証（2026-11-04）：** [署名済みv1.157 APKをAndroid 15 / API35エミュレーターにインストールし、MainActivityの起動・プロセス生存・クラッシュログ検査が成功](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/actions/runs/41730661486)。Android 17 / Pixel 10の実機およびCM回復と報酬加算の動作は未検証です。

v1.156の署名済みAPKのSHA-256を固定して、物理画面・仮想画面・ADBシェル実行部の3か所へDEX差分を統合しました。対象の既知ミニゲームで広告の正の観測を2枚以上、2秒以上継続して確認した場合に、HOME操作と実表示の確認を行い、認証済みADB実行部から**当該ゲームのパッケージだけ**を停止し、登録された起動Intentで再開を試みます。HOMEを確認できない、ADBが利用できない、仮想画面にHOMEが存在しない場合は**強制停止せず保留**します。回復試行の回数・間隔制限もあります。

パッケージ名 jp.tapmarker.lite、versionName **1.157**、versionCode **160**。署名済みAPK SHA-256：0108aadfa717c676dfe30b4f678a5c1526277e11b01ae16f85317d8b8dd50f7d。従来と同じ更新用証明書（SHA-256：7f42d636e4d3c251f475eb998fbe8e92b515ded8bce3ff4d30aa90b9a738cfb1）でAPK v2/v3署名を検証し、公開ファイルも照合済み。通常はアンインストールせず上書き更新できます。

**検証範囲・注意：** これは**実機試験前のプレリリース**です。Pixel 10 / Android 17での広告2秒判定、仮想2画面のHOME、ADB停止とIntent、収納時通信、報酬の実加算、クラッシュ解消は未確認です。広告視聴報酬は2秒で中断すると失う可能性があります。開拓うさぎの「ゲット」等の報酬認識ソース候補はPR #41にありますが、**今回のDEX差分APKには追加収録されていません**。今回の主な変更は広告復帰経路です。

## MarkerTap v1.156 — 仮想画面「手動操作中」の長時間残留対策・署名済み検証版（2026-11-03）

**[▶ MarkerTap v1.156 署名済みAPKを直接ダウンロード](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/releases/download/markertap-v1.156-rc1/MarkerTap-v1.156-integrated-signed.apk)**  
[GitHub Release v1.156-rc1](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/releases/tag/markertap-v1.156-rc1) / [SHA-256チェックサム](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/releases/download/markertap-v1.156-rc1/SHA256SUMS-v1.156.txt) / [ビルド・署名検証成功](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/actions/runs/41622513405) / [改修PR #40](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/pull/40)

ユーザー提供の約35秒の録画で、左「増えうさぎ」仮想画面が広告の表示中に**「手動操作中」表示のまま長く進まない**状態を確認。右の「クラシル開拓…」側でも動画広告が流れ続け「確認中」のままでした。録画中にアプリ全体のクラッシュは確認できておらず、実機のログは未受領のためクラッシュ原因の断定はしていません。

**変更：** v1.155の署名済みAPKをベースに、ユーザーが仮想画面をタッチした際の一時保留を監視。12秒後にその画面の手動ホールド期限、独立した入力ガード、実行状態を検証し、期限経過後も「手動操作中」が残っている場合に表示と認識スケジュールを更新します。ほかの画面に触れず、広告の無条件タップ・表示画面の強制再生成・権限の昇格は行いません。v1.155のWi-FiなしローカルADBおよび停止ループ抑制は引き続き維持します。

パッケージ `jp.tapmarker.lite`、versionName **1.156**、versionCode **159**、APK SHA-256 `b4bf94683710c3810a5dd193263dc555977f77f1091a5d8dff45bbafc69da8e6`。v1.155までのインストールと同じ更新用証明書でAPK v2/v3署名、12項目の追加テスト・既存回帰試験・DEX監査・公開ファイル照合成功。**v1.155をアンインストールせず上書き更新**を推奨します。

**重要な制限：** 本版は**実機未検証のプレリリース**です。動画中の広告からの自動離脱や報酬回収、Pixel 10 / Android 17での2画面連続自律、過去のクラッシュ原因の解消まで保証するものではありません。更新後も停止する場合は、MarkerTapの診断ZIPまたはAndroidクラッシュログを添付してください。前版v1.155の配布リンクは以下に残しています。

## MarkerTap v1.155 — 停止ループ抑制・署名済み安定化候補（2026-11-03）

**[▶ MarkerTap v1.155 署名済みAPKを直接ダウンロード](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/releases/download/markertap-v1.155-rc1/MarkerTap-v1.155-integrated-signed.apk)**  
[GitHub Release v1.155-rc1](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/releases/tag/markertap-v1.155-rc1) / [SHA-256チェックサム](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/releases/download/markertap-v1.155-rc1/SHA256SUMS-v1.155.txt) / [署名・ダウンロード検証成功](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/actions/runs/41618553703) / [停止ループ対策の改修PR #39](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/pull/39)

**v1.154で停止が繰り返されるというユーザー報告への緊急対処版です。** 元の署名済みv1.151 APKをベースに組み直し、v1.153から加えた高頻度の画面別復旧フック・自動画面再生成・個別隔離処理は、安全性が実機で確認できるまで**ロールバック**しました。内蔵ADBへの起動案内は残します。Wi-Fiがなくても、同一端末で認証済みの`127.0.0.1`ポートが利用可能なら接続を試みますが、オフラインの**自動**再接続は15分あたり2回までに制限します。Wi-Fiがある場合は従来のADB検出を優先します。

パッケージ `jp.tapmarker.lite`、versionName **1.155** / versionCode **158**、APK SHA-256 `574b6e0f8d733d6b6288a9c2754e26aef4d9b109b10db2c0bcc90a925741344e`。以前のMarkerTapと同じ更新用証明書でv2/v3署名済み。DEX差分監査、ホストテスト、署名証明書、公開APKバイト一致を確認しました。通常は**v1.154をアンインストールせずに上書き更新**できます（Android側の状態・ポリシーに依存）。既存のデータは更新操作で維持されますが、念のためバックアップを推奨します。

**重要：実機のクラッシュ解消は未確認です。** 端末の新しい診断ログをまだ受領していないため、原因の特定は完了していません。v1.152–v1.154の自動復旧能力の一部を削除したため、仮想画面の自動再生成頻度は抑えられます。Pixel 10 / Android 17の仮想2画面、自律動作、広告復帰、長時間安定は引き続き要実機検証。異常が再発する場合は再起動を繰り返さず、診断ZIPまたはAndroidのクラッシュログをご提供ください。

## MarkerTap v1.154 — Wi-FiなしのローカルADB再接続・署名済み検証版（2026-11-03）

**[▶ MarkerTap v1.154 署名済みAPKを直接ダウンロード](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/releases/download/markertap-v1.154-rc1/MarkerTap-v1.154-integrated-signed.apk)**  
[GitHub Release v1.154-rc1](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/releases/tag/markertap-v1.154-rc1) / [SHA-256チェックサム](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/releases/download/markertap-v1.154-rc1/SHA256SUMS-v1.154.txt) / [署名・公開URL検証成功](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/actions/runs/41615453059) / [改修PR #38](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/pull/38)

パッケージ `jp.tapmarker.lite`、versionName **1.154**、versionCode **157**、公開APK SHA-256 `1e5c95fa21fa6c6a8d935ab280b7689dc90789bb61d32a89cf1ccf0bd1cf18b7`。v1.153と同じ更新用署名証明書を検証済み。APK v2/v3署名・DEXクラスとメソッド変更監査・ZIP・16KiB zipalign・公開ファイルの再ダウンロードとバイト一致が成功しています。秘密の署名鍵はリポジトリに公開していません。

**改修：** 起動済みのADB実行部を再利用し、以前に同じ端末で認証成功した`127.0.0.1` のADBポートを保存。Wi-Fiがなくても、そのポートがAndroid側でまだ待ち受けていれば接続を試みます。利用できなければ既存のWi-Fiデバッグ検出へ戻します。未ペアリングのポートを無断使用せず、他端末への接続・ポート範囲スキャン・権限の自動昇格は行いません。

**重要な制約：** 初回のペアリングにはAndroidのデバッグ許可が必要です。アップグレード直後は、成功したローカルADBポートを記録するため、通常のワイヤレスADB接続を一度実行する必要があります。Android側が待受を閉じた場合や再起動後にポートが変わった場合、ローカルホストのみでは再起動できません。また、ゲーム自体がインターネットを要する場合は別の通信手段が必要です。

**検証段階：** 署名・公開URL・ホストテストまで完了した**プレリリース検証版**です。Pixel 10 / Android 17の実機でのWi-Fi切断・再接続、2画面自律、収納・広告復帰・報酬回収は未検証であり、完全自律の保証はありません。問題があれば下のv1.153 / v1.151に戻せます。

## MarkerTap v1.153 — 署名済み統合APK（2026-11-03）

**[▶ MarkerTap v1.153 署名済みAPKを直接ダウンロード](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/releases/download/markertap-v1.153-rc1/MarkerTap-v1.153-integrated-signed.apk)**  
[GitHub Release v1.153-rc1](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/releases/tag/markertap-v1.153-rc1) / [SHA-256チェックサム](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/releases/download/markertap-v1.153-rc1/SHA256SUMS-v1.153.txt) / [署名・外部ダウンロード検証成功](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/actions/runs/41612946538) / [DEX統合PR #37](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/pull/37) / [Shizuku不要設計PR #36](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/pull/36)

**インストール可能な署名済み検証版です。** パッケージ `jp.tapmarker.lite`、versionName **1.153** / versionCode **156**、5,655,395 bytes。APK SHA-256 `9a3fa3b4fea3e04bcff92531a8f908724edf33efab99710366cab9c793ff3824`。元の更新用署名証明書 SHA-256 `7f42d636e4d3c251f475eb998fbe8e92b515ded8bce3ff4d30aa90b9a738cfb1` と一致。APK v2/v3署名・DEX変更監査・ZIP/zipalign・公開URLからの再取得とバイト一致を検証済みです。秘密の署名鍵はGitHubに公開していません。

**今回のAPKに実際に組み込まれた機能：** 既存の内蔵ワイヤレスADBへの起動経路、画面ごとの画像再取得・段階的な再作成、ネットワーク/DNSエラー時の再起動抑止、画面単独障害の復旧判断と回数制限。**Shizuku別アプリのインストールは基本経路に不要**ですが、v1.151ベースの互換性を保つため旧Shizukuのクラス/ProviderはAPK内に残ります。コード全削除版ではなく、起動時に内蔵ADBへ振り分ける統合版です。非root端末での初回ペアリング・デバッグ許可は必要です。

**検証状況：** 署名・ビルド・公開配布まで成功。ただし Pixel 10 / Android 17 の実機、仮想2画面を最小化した際の通信状態、広告復帰、報酬回収、自律の連続成功については**未検証**です。現段階は**プレリリース候補**として扱い、安定版の自動置換・完全自律の宣言はしません。問題があれば、下のv1.151 / v1.150配布リンクへ戻せます。

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
