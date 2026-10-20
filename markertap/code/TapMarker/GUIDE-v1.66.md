# MarkerTap v1.66 — F-52A互換版

F-52A向けの自動負荷設定と、Android 11/12の仮想画面入力を追加した軽量版です。APKのバージョン表記は **1.66**。他のARM端末でも使えます。

## インストールとF-52Aでの開始

1. v1.65軽量版がある場合は「全停止」を押して仮想画面を閉じ、`MarkerTap-v1.66-f52a-compatible.apk`を上書きインストールしてください。設定・学習内容・ペアリングを引き継げる同じパッケージ／署名です。アンインストールは不要です。
2. アプリ名は「マーカー連打 軽量版」。ユーザー補助を有効にしてください。
3. F-52Aでは負荷設定を「自動」にすると「F-52A向け軽量」が選ばれます。既に「標準」や「高速」を選んでいる場合は、その選択を保持するので「自動」に変更してください。
4. 仮想画面の自律には **Android 11以降** が必要です。F-52AではAndroid 11／12向けの互換入力を使います。端末のAndroid版は「設定」の端末情報で確認できます。
5. 「端末だけで接続」を設定し、対象アプリを選んで **仮想画面** を開きます。まず少ない画面数で始め、表示を確認してから増やしてください。
6. 仮想画面の「自動開始」を押してください。「状況」には端末名・Android版・入力方式・画像の更新時刻・入力完了数が表示されます。旧実行部と表示された場合は「端末だけで接続」から再起動してください。

v1.64以前の通常版 `jp.tapmarker` からは別アプリです。通常版を全停止してユーザー補助をOFFにしてから軽量版を使ってください。通常版からの設定・ペアリングの自動移行はありません。v1.65軽量版 `jp.tapmarker.lite` からは上書き更新です。

F-52A上で複数ゲームの画像判定を使う場合は仮想画面を選んでください。通常の分割ウインドウでの複数画面判定はAndroid 14以降の機能です。Android 10では通常のマーカー連打を利用できますが、画像判定・仮想画面自律はAndroid 11以降が必要です。

## v1.66の修正

- Android 11/12には、以前の実行部が必須としていたアプリUID指定の入力APIがありません。旧Android向けの入力APIを用意し、各入力の直前にその仮想画面で最上位に表示されているアプリを確認します。画面ID、アプリ、ユーザーが一致しない場合や情報取得に失敗した場合は入力を拒否します。Android 12では表示中アクティビティのUIDも確認します。
- Android 11の`StackInfo`、Android 12の`RootTaskInfo`という異なる取得APIに対応。端末全体のフォーカス情報ではなく、入力先の仮想画面の情報を取得します。
- Android 13以降では従来どおりOS側でUIDを指定する入力APIを優先し、入力拒否時に旧方式へ切り替えません。
- F-52Aは8GB・8コアだけで「標準」と判定せず、機種名から専用の軽量設定を選択。仮想画面は幅540pxを上限とし、画像取込・判定・プレビュー・連打の頻度を抑えます。
- F-52Aの自動設定ではゲーム起動を650msずつずらし、同時起動の負荷を抑えます。各ゲームの入力レーンと状態機械は独立したままです。停止後の遅延起動は実行しません。
- 状況画面に機種・Android版・入力方式を追加。起動時の画面にv1.66を表示します。

旧AndroidのAPIには入力と同時にUIDを検査する機能がないため、直前の表示確認と入力処理は別の処理です。新AndroidのOS内UID検査と同一の仕組みではありません。メーカー固有の挙動は実機未確認です。

## 負荷設定

下表は処理の最短間隔です。実際の速度は端末・アプリ・判定時間に依存します。

| 選択 | 仮想画面の幅上限 | 画像取込 | 判定 | プレビュー | 連打間隔の下限 |
| --- | ---: | ---: | ---: | ---: | ---: |
| 自動・F-52A | 540px | 220ms | 400ms | 500ms | 32ms |
| 軽量 | 540px | 220ms | 350ms | 500ms | 24ms |
| 標準 | 720px | 140ms | 220ms | 300ms | 12ms |
| 高速 | 720px | 70ms | 120ms | 180ms | 3ms |

他の機種の自動設定はメモリ枠・低RAM判定・総メモリ・CPU構成から軽量／標準を選びます。明示的に選んだ軽量／標準／高速は保持します。保存した連打間隔が上表より長い場合も、その間隔を優先します。

仮想画面のサイズを変えるには、既存の画面を閉じて開き直してください。F-52Aの1080×2280に対応する既定の540×1140では、判定用のARGB配列2本は1画面あたり約4.92MBです。720×1520の場合は約8.76MBです。これは配列サイズの計算で、実測の総RAM使用量ではありません。GPU、ImageReader、ゲーム本体のメモリは含みません。

v1.65のバッファ再利用、画素タイルの一括取得、解析・プレビュー生成をUIスレッドの外で実行する処理を維持しています。解析が遅い場合は周期を延ばし、メモリ不足通知時は軽量設定を使います。小さい文字の一致判定が止まる場合は「標準」を選んで画面を開き直してください。

標準4アプリの見本、回路、ライセンスを含む全assetsとARMネイティブ部品をv1.65からバイト単位で保持しました。オートマトンの一致条件、保護表示の停止条件、矢印の操作順序は維持しています。

## 検証と確認範囲

- **47組のJavaホストテストと、ADB起動の模擬8シナリオが成功。**
- 実際の反射入力アダプターを模擬InputManagerで実行し、旧Android方式の許可・拒否、新方式の優先、本体画面の拒否、取得失敗時の拒否を確認。
- Android 11/12のAPI形状に合わせたタスク情報で、24個の独立画面、別アプリ、別ユーザー、Android 12のUID不一致、欠落情報、表示切替後の拒否を確認。
- F-52Aの機種名選択・手動設定保持、1080×2280の比率、540×1140のサイズ、起動時刻の分散、メモリ予算を確認。
- 既存の増えうさぎ・農園うさぎ録画を540px／720px幅へ縮小し、元の比率とF-52A相当の19:9比率で主画面認識・座標範囲・未知画面からの連打禁止を確認。
- 画面遷移、停止、遅れた完了通知、保護表示、独立入力レーン、学習・オートマトン、暗号交換の回帰テストを確認。
- APKのパッケージ、バージョン、v2／v3署名、v1.65との署名一致、ARM32／ARM64、ネイティブELFの16KiBページ対応を確認。

**F-52A実機へのインストール、入力、速度、電池消費、Android TLS通信、ゲームの長時間並列運転は未確認です。** ホストの模擬テストと縮小録画の成功は、実機の全状態の動作保証ではありません。大容量録画が配布ソースにないため、Video6651／6662／6666／6669の全列再生は実行していません。

詳細は`VALIDATION-v1.66.json`、`tests-v1.66.log`、`build-v1.66.log`を参照してください。

## 再ビルド

`MarkerTap-v1.66-source.zip`を展開し、`TapMarker`で実行します。Java 17、Android API 35／Build Tools 35.0.0、Python 3、rg、C／C++コンパイラが必要です。

```sh
ANDROID_SDK_ROOT=/path/to/android-sdk \
TAPMARKER_KEYSTORE="$PWD/signing/lite-development.jks" \
bash build.sh

TAPMARKER_TEST_GROUP=core \
ANDROID_SDK_ROOT=/path/to/android-sdk \
bash run-tests.sh
```

出力は`../MarkerTap-v1.66-f52a-compatible.apk`。同梱の署名鍵はv1.65から継続しています。将来の軽量版の更新に使えます。PCから接続する場合は`adb/README-JA.md`を参照してください。

## 仕様確認の参照先

- [NTTドコモ F-52Aスペック](https://www.docomo.ne.jp/support/product/f52a/spec.html)：Snapdragon 765G、8GB、Android 12の記載。
- [NTTドコモ 発売時仕様](https://www.docomo.ne.jp/info/news_release/2020/12/11_00.html)：1080×2280、発売時Android 10。
- [AOSP Android 11 InputManager](https://android.googlesource.com/platform/frameworks/base/+/android-11.0.0_r1/core/java/android/hardware/input/InputManager.java)、[Android 12 InputManager](https://android.googlesource.com/platform/frameworks/base/+/android-12.0.0_r1/core/java/android/hardware/input/InputManager.java)、[Android 13 InputManager](https://android.googlesource.com/platform/frameworks/base/+/android-13.0.0_r1/core/java/android/hardware/input/InputManager.java)：入力APIの違い。
- [AOSP Android 11 タスクAPI](https://android.googlesource.com/platform/frameworks/base/+/android-11.0.0_r1/core/java/android/app/IActivityTaskManager.aidl)、[Android 12 タスクAPI](https://android.googlesource.com/platform/frameworks/base/+/android-12.0.0_r1/core/java/android/app/IActivityTaskManager.aidl)：表示先を指定したタスク情報取得。

APK：**3033212 bytes**（約3.03MB）。SHA-256：

```text
66941a21c8d7f6bceb9dc9ac0564ba44ea7cdd9944b25a2a3fa7c043fdbf3c4d
```
