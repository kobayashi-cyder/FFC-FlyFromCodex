# MarkerTap v1.150 — 署名済み統合APK完成（2026-11-03）

**ローカルの完全DEX統合ビルドと元の署名証明書による署名に成功。** パッケージ `jp.tapmarker.lite` / versionName `1.150` / versionCode `153`、5,647,203 bytes。APK SHA-256 `58e6cd830350473dad218352b3e23f4efcb214b2624e1122823ceb1093345067`。

**証明書一致** `7f42d636e4d3c251f475eb998fbe8e92b515ded8bce3ff4d30aa90b9a738cfb1` / **v2・v3署名確認** / **16KiB zipalign成功** / **ZIP CRC正常** / **元DEXの保全監査合格** / ホスト試験合格。

[署名済みAPKの検証JSON](v1.150-signed-validation.json) / [SHA256SUMS](v1.150-SHA256SUMS.txt) / [GitHub Actionsのツール環境検証](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/actions/runs/41545774987)

**[▶ GitHubからv1.150署名済みAPKを直接ダウンロード](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/releases/download/markertap-v1.150/MarkerTap-v1.150-integrated-signed.apk)** / [Releaseページ](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/releases/tag/markertap-v1.150) / [公開ダウンロード照合成功](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/actions/runs/41548356954)

**配布状況：** GitHub ReleaseへのAPK実ファイル掲載・匿名HTTPダウンロード・ローカル署名済みAPKとのバイト一致まで検証済み。Pixel 10 / Android 17の実機操作・仮想2画面の自律・広告の実測は未検証です。

---

# MarkerTap v1.150 — 高速な自律動作・広告回避・仮想2画面の待機短縮（開発候補）

**2026-11-03 旧時点の記録：** ソース・ホスト回帰テストをGitHubに登録。その後、上記のとおり署名済みAPKのローカルビルドは完了しました。
[このブランチの実装](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/tree/markertap/no-redundant-taps-v1.150/markertap/v1.145-physical-return)
／[CI実行一覧](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/actions/workflows/markertap-v1148-video-modal.yml)

## v1.150で改修した内容

- **CM回避:** 開拓うさぎで「コインアップチャンス→このままはじめる」「タップ2倍→キャンセル」の**ユーザー補助ツリーに現れた安全なボタン領域だけ**を閉じる候補にする。報酬広告・CM視聴ボタンは勝手に選ばない。ゲームが強制的に出す広告を削除するものではない。
- **待機時間:** 同一ボタンへの重複操作候補の最短間隔を750→350ms、次を1600→750ms、3000→1500ms、4500→2500msへ短縮。**画面変化や対象ボタンの変更時は直ちに次候補を許可**。同じ未変化の画面への候補は初回と再試行4回で打ち切り、次の変化・復帰確認に委ねる。新鮮な観測を省略して連打する仕組みではない。元の入力確認ガードは維持。
- **解析の無駄削減:** 通常画面でモーダル文字が存在しない場合のコントロール一覧生成と本文正規化を回避。正規化用の正規表現を事前コンパイル。
- **仮想画面（最大2画面）:** 出荷用DEXの`SplitPilot.nextVirtual`で呼ぶ解析間隔を、`VirtualFastWait.scanInterval`に置換。標準プロファイルでは1画面の最低220→110ms、2画面の最低220→120msを目標とし、**前回解析時間が長ければ自動的に間隔を広げる**。低メモリ軽量プロファイルは元の間隔のまま。仮想画面数は古い4画面設定でも2画面に制限。これは解析のポーリング間隔であり実測タップ/フレームレートではない。
- **従来の安全条件:** 仮想ディスプレイID、操作対象アプリ、画像鮮度、入力レーン、画面保護、表示復帰の判定を変更しない。端末画面の自律処理と仮想2画面の処理は分離。

## 署名APK生成のための検証済みパイプライン

- [GitHub Actions: DEX編成ツールの事前検査と署名済みRelease](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/actions/workflows/markertap-v1150-integrated-release.yml)
- [Android 35・JADXを取得してDEXパッチャをコンパイルした成功記録](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/actions/runs/41545774987)
- [1コマンドのビルド・署名オーケストレーター](release-local.py)

このワークフローの**preflight**は成功（Javaホスト回帰／Android 35・JADX取得／DEXパッチャコンパイル）。旧secret-gated publishジョブはスキップされたものの、ローカルで署名し、別の公開用ワークフローからGitHub Releaseへv1.150を掲載して匿名HTTP取得・ハッシュ一致まで確認しました。

リポジトリのSecretsに `MARKERTAP_V1144_BASE_URL`（オリジナルv1.144の限定アクセスURL）、`MARKERTAP_SIGNING_JKS_BASE64`（元証明書の秘密JKS）、`MARKERTAP_STORE_PASSWORD`（元のストアパスワード）、必要な場合のみ`MARKERTAP_KEY_PASSWORD`を設定し、Variable `MARKERTAP_V1150_PUBLISH=true`を設定したときにのみ、署名付きRelease作成を試みます。**これらをコミット、Issue、PR、README、workflow_dispatchの平文入力へ記入しないでください。**

ローカル実行も `python3 release-local.py --baseline /private/v1.144.apk --keystore /private/original.jks --sdk tooling/sdk --jadx tooling/jadx/lib/jadx-1.5.3-all.jar` で一括検証できます。オリジナルAPK SHA、元証明書、APK v2/v3、ZIPと16KiB整列が一致しなければ配布しません。

## ビルド・配布の状態

基底は既存の**統合版 v1.144**（SHA-256 `f62e8d6020a3cacb2ea9a9085be235ea48da12f03c5de2f4e3ae80b801b6e970`）に固定。予定値はパッケージ `jp.tapmarker.lite`、`versionName=1.150`、`versionCode=153`。新規JavaクラスをDEXに含め、既存の`KaitakuUiEvidence`と`SplitPilot`の**それぞれ1か所**だけをフックするよう整合性監査を追加。ビルド時に一致しなければ停止する。

`python3 run-tests.py` がJavaホスト試験・ソース監査を実行。**このテストだけでは、上記フックのAPK内統合や実機の自律成功は確定しない。**
`python3 build.py --baseline ... --sdk ... --jadx ...` と `sign-release.py` の再構築・元証明書照合・v2/v3検証は別途必要。
**v1.150の署名済みAPKはGitHub Releaseに掲載済みです。** [直接ダウンロード](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/releases/download/markertap-v1.150/MarkerTap-v1.150-integrated-signed.apk)。 不存在のReleaseリンクを表示しない。旧v1.149の開発APKはv1.150の変更を含まない。

## 実機での未検証事項

Pixel 10/Android 17での1画面・仮想2画面の画面取得、入力レーンの混線、広告を見ないボタンの自律選択、入力後の画面変化・ミッション報酬確認、待機短縮の前後実測、長時間安定動作。強制広告をスキップする処理や外部ゲームの広告配信制御は行わない。

---

# MarkerTap v1.149：開拓うさぎ「このままはじめる」判定改修

**2026-11-02 検証状況**：元の署名証明書を使用した **v1.149 開発用統合APK** をChatGPT会話内で生成し、ファイルライブラリ `MarkerTap-v1.149-integrated-signed.apk` に保存済み。SHA-256 `0e9d621a2243d967aef4117369edd94f74c848c41c41a4150096d33e1646b500`、5,640,586バイト、versionCode 152、証明書SHA-256 `7f42d636e4d3c251f475eb998fbe8e92b515ded8bce3ff4d30aa90b9a738cfb1`。APK v2/v3署名・内容ダイジェスト・ZIP CRCを独立検証しました。詳細は [検証記録](v1.149-signed-validation.json)。

**重要**：会話内APKはv1.148の固定DEXに最小限のバイトコード差分を適用した開発候補であり、当ブランチの全Javaソースをフル再ビルドしたバイナリではありません。説明文欠落・背景ミッションガードの修正は含みますが、Javaソースで追加した「このまま始める」表記ゆれの対応は未収録です。Android実機・報酬回収・ゲームがボタン領域を非公開にする場合の画像認識は未検証です。

**GitHub配布の状態**：GitHub ReleaseにこのAPK本体はまだ公開できていません。READMEから直接APKを取得できると偽らず、現在はソース・検証記録へのリンクのみ掲載します。旧v1.148のAPKには今回の修正が含まれません。

---

## 過去バージョンのビルドと検証記録


**現在の状態：2026-11-02、元の署名鍵でv1.146署名済みAPKを生成し、パッケージ/版番号/旧証明書一致/署名v2・v3/16KiB整列まで検証済み。端末でのインストールや自律動作は未確認。** 既存v1.145の検証記録・SHA-256は新しいv1.146に適用できません。

## デジタル庁デザインシステムを参考にしたUI改善

参考：デジタル庁デザインシステムβ版の[ボタン・概要](https://design.digital.go.jp/dads/components/button/)、[アクセシビリティ](https://design.digital.go.jp/dads/components/button/accessibility/)（2026-11-02参照）。

- フロートの「画面下部に固定」「フロートに戻す」を操作行に設置。通常画面からも同じ切替が可能。**非表示ではなく、同じ操作パネルを画面下部に固定表示**する。さらに「最小化」は同一パネルを小さな「MarkerTapに戻す」ボタン1個へ縮め、いつでも直前の表示方法へ復帰できる。
- 変更は `panelUi.displayMode` に保存し、最小化直前の方式は `panelUi.lastExpandedMode` へ保存。固定時は画面下部の横幅いっぱいに配置し、角丸・影を外す。ドラッグは無効。フロートに戻した時は以前のドラッグ位置を復元する。ロック中は安全のため一時非表示になり、解除時に選択した表示方法へ戻る。
- **表示と自律処理を分離。** フロート⇄固定を切り替えても `stopAll`・`stopMarkers`・`pilot.stop` を呼ばない。全停止は独立して使う。通常画面の「操作パネル」ボタンでもフロートへ戻せる。
- ボタンのタップ領域48dp、文字による状態表示、青い主操作・アウトラインの副操作、説明文、診断項目の可読性を改善。既存のフロートと通常画面の大部分は互換性を維持。
- 通常画面に「自律操作の診断」を追加。対象、稼働表示、状態、画像経過、入力保留理由を示す。「実行中」と「ゲーム側の進捗確認」を混同しない。
- 固定モードでもパネルは画面上で操作できる。ゲームの表示を妨げる場合は「最小化」で右下の「MarkerTapに戻す」だけを残す。復元時は直前のフロートまたは固定へ戻る。

## 実装・検査

統合版1.144（固定SHA-256）を基底とするDEXパッチに `TapService.onServiceConnected`、`TapService.showPanel`、`TapService.refresh`、`MainActivity.home` の接続を追加。`PanelModeController` と `PanelModePolicy` を梱包し、既存データ/署名秘密鍵は変更しない。ビルド上の予定値は `versionName=1.146` / `versionCode=149`。

GitHub Actions：[v1.146ホスト検証の成功](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/actions/runs/41355059709)。物理復帰171項目、表示モード13項目、UIソース接続19項目が成功。**DEX統合ビルド、APK署名、端末のユーザー補助サービス、実ゲームでの入力・UI復帰操作はテストしていない。** APKの新しいハッシュ値も未発行。

## オリジナル証明書による署名（2026-11-02 完了・ローカル成果物）

ユーザーの保存ライブラリから **v1.144統合版の正本APK**（SHA-256 `f62e8d6020a3cacb2ea9a9085be235ea48da12f03c5de2f4e3ae80b801b6e970`）と **MarkerTap-lite-signing-backup(1).zip** を確認し、ユーザー所有の鍵を隔離した作業領域内でのみ復元しました。正本からv1.146 APKを実際に構築し、旧証明書SHA-256 `7f42d636e4d3c251f475eb998fbe8e92b515ded8bce3ff4d30aa90b9a738cfb1` を使って署名。**APK Signature Scheme v2/v3を両方検証し、16KiB zipalign、ZIP CRC、versionName 1.146 / versionCode 149 を確認しました。** 署名用の作業用鍵コピーは検証完了後に消去し、GitHubへコミットしていません。

実際に署名できたAPKのSHA-256は `234712319aec900a0e188df7a822e66e6cf2ae74493046909ed2741314f2f3f6`、サイズ5,647,203 bytes。ホスト検証は実画面復帰171項目、表示モード30項目、ソース接続監査37項目に合格。ソース基準コミット `279da83b7dc75d6f68b0c181dcf1079ac7c108c2`。**署名済みAPKはChatGPT会話内の成果物として提供済みですが、GitHub ReleasesやREADMEから直接ダウンロードできる形では未公開です。** 実機へのインストール・Pixel 10での動作・開拓うさぎの報酬回収は未検証。

元の署名鍵で同じ成果物を再構築する場合のコマンドは次のとおりです。ここに鍵やパスワードをコミットしないでください。

```bash
export MARKERTAP_STORE_PASSWORD='<local secret>'
# MARKERTAP_KEY_PASSWORD is required only if the key uses a different password
python3 sign-release.py \\
  --unsigned output/MarkerTap-v1.146-integrated-unsigned.apk \\
  --keystore /private/path/lite-development.jks \\
  --sdk tooling/sdk \\
  --out output/MarkerTap-v1.146-integrated-signed.apk
unset MARKERTAP_STORE_PASSWORD MARKERTAP_KEY_PASSWORD
```

署名結果の受入条件は従来証明書SHA-256 `7f42d636e4d3c251f475eb998fbe8e92b515ded8bce3ff4d30aa90b9a738cfb1` の一致、APK v2/v3、16KiB zipalign、パッケージ `jp.tapmarker.lite`、versionCode149。条件を満たさないAPKは破棄し、配布しない設計です。

## 既知のリスクと実機受入条件

実機では、フロート表示→画面下部固定→最小化→直前の固定バーへ復元→フロート復帰、通常画面からの自律開始・全停止、Androidサービス再起動・画面ロック解除・縦横変化、開拓うさぎの広告復帰とゲーム進行、ミッション受取、報酬加算を確認すること。更新失敗や旧署名との不一致を避けるため、元の非公開署名鍵で署名し、以前の証明書との一致が確認できるまで配布しない。

---

# MarkerTap v1.145 統合版：実画面の前面復帰待ち

開拓うさぎが外部画面にいる間、広告復帰状態への再入場と復帰要求の作り直しが続き、30秒の再起動待ちの途中で15秒の `FRAME_STALE` が報告される経路を修正します。

更新対象は `jp.tapmarker.lite` / versionName **1.145** / versionCode **148**。統合版1.144を継承し、target SDKは**35**です。

## 動作

- 同じ外部画面の復帰待ちでは状態へ入り直しません。外部アプリの安定を1.2秒以上確認してから対象ゲームの起動Intentを送ります。不明・System UI・MarkerTap自身の表示では送信しません。
- 既存の通常復帰3回・再起動2回のカウンタと、再起動後30秒の待機を共有します。Intent送信や短い前面復帰だけで試行枠を回復しません。起動例外も試行として数えます。
- 外部画面にいる間は撮影を期待せず、前面復帰を別の90秒の期限で監視します。試行枠を使い切って最後の再起動待ちが終わった場合も、`FOREGROUND_RETURN_FAILED` を保存して停止します。
- 対象が前面に戻ったら古い判定画像を捨て、取得・判定に最大15秒の再取得猶予を与えます。新しい結果が得られれば通常監視へ戻り、実際の取得停止は `FRAME_STALE` として検出します。
- 主画面の確認は、同じウインドウの新鮮で異なる2枚を必要とします。**前面へ戻っただけでは自律再開とみなさず**、90秒以内に主画面を再確認できなければ `HOME_REACQUIRE_FAILED` を保存して停止します。タップの鮮度・ウインドウ・実行トークンの検証は元の実装を継承します。
- 診断に前面パッケージ、撮影保留、未確認時間、起動要求ID・送信結果を追加します。また開拓うさぎの入力許可判定材料として、状態、主画面判定、フレーム経過、対象ウインドウ一致、操作保留、復帰中フラグを保存します。**タップAPIの受理やゲーム進捗を実測したログではありません。**

農園・増えうさぎ、編集済みオートマトン、仮想画面は元の入口へ委譲します。既存1.144に含まれる入力・画像処理を全体的に再生成しない構成です。

## 正本と再構築

基底は実際に配布した `MarkerTap-v1.144-integrated-signed.apk` です。SHA-256を必ず照合します。

```text
f62e8d6020a3cacb2ea9a9085be235ea48da12f03c5de2f4e3ae80b801b6e970
```

他の1.144 APKやAndroid 12系列へ置き換えないでください。`markertap/android12-v1.144` の復元スクリプトは本ビルドの正本ではありません。

JDK 17、Python 3を使用します。

```bash
python3 setup-toolchain.py
python3 run-tests.py
python3 build.py \
  --baseline /path/to/MarkerTap-v1.144-integrated-signed.apk \
  --sdk "$PWD/tooling/sdk" \
  --jadx "$PWD/tooling/jadx/lib/jadx-1.5.3-all.jar"
```

`setup-toolchain.py` はハッシュを固定したAndroid 35とJADX 1.5.3を用意します。`build.py` は基底APKからコンパイル用の型定義を作り、`src/` の新規2クラスをコンパイルします。型定義のスタブはAPKへ含めません。

DEXへの接続は `tools/PatchPhysicalReturn.java` が担当します。

1. `AutoPilot.phaseCycle(int)` の入口を新しい前面復帰処理へ接続。
2. `ProgressErrors.observe` の監視呼出しを状態別の監視へ接続し、診断本文へ前面情報を追加。
3. `RecoveryEscalation` に既存試行数・待機期限を読む3つのメソッドを追加。
4. 画面上の版表記を1.145へ更新。

基底の既存クラスはすべて残します。変更予定箇所以外は、クラス単位の正規化DEXを比較します。画像・設定・ネイティブライブラリを含む34個の既存ZIPエントリはバイト単位で同一です。Manifestは版名・版番号のみ変更します。

ビルドの出力は未署名です。更新インストール用には既存の非公開署名鍵を使用し、従来証明書と一致することを検証してください。署名鍵・パスワードはこのディレクトリに含めません。

## 検証と限界

既存の12組・164項目のホスト回帰は追加変更前に通過しています。追加した「前面へ戻ったが主画面未確認の90秒終了」「2枚の新鮮な主画面で猶予解除」「入力判定の診断出力」「同じ対象アプリの別セッションへの診断混入防止」を含む**ホスト回帰14組・171項目をGitHub Actionsで通過**しました（[実行記録](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/actions/runs/41294069839)）。**この結果はAndroid実機と更新署名APKの動作検証ではありません。**今回の「通常3/3・再起動1/2・残り15秒・画像15秒超」の再生、状態再入場の抑制、5回の試行上限、90秒期限、実際の取得停止、実行切替、画面保護、起動例外、前面の短い切替を確認しています。

APKのDEX保存検査、版番号・target SDK、従来証明書一致、v2/v3署名、16KB整列を検証済みです。結果は `validation.json` と `evidence/` を参照してください。

Pixel 10 / Android 17での実際の前面復帰・入力、長時間稼働、報酬加算は未検証です。端末でゲームが戻らなかった最初の原因を確定したものではありません。

## 2026-11-01 追加検証と配布の状態

上記ホストテスト通過後のソースは、以前の `validation.json` が記録したAPKバイト列とは異なります。`validation.json` に載る署名証明書一致・ハッシュは**追加変更前**の成果物にのみ対応します。追加変更後の統合APKは再生成・署名・証明書一致・DEX差分監査・実機テストを実施できていません。特に基底のv1.144署名済みAPKと非公開署名鍵をGitHubへ置かないこと。署名済みの新しい更新APKとして配布済みとは表現しないでください。

診断の `sampleAgeMs`、`home`、`windowValid` 等は入力可否の**一部の条件**であり、実際にジェスチャが送られた証拠ではありません。新しい停止例では実機の操作イベントIDと画面変化も照合してください。
