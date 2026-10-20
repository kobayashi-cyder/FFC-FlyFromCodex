# MultiScreen PC 2.3 / MarkerTap 1.84 検証記録

対象はWindows PowerShell 5.1のPCツールとAndroidアプリです。検証環境はLinux、PowerShell 7.4.6、OpenJDK 17、Android API 35ビルドツールです。

| 区分 | 結果 | 確認内容 |
|---|---|---|
| PC構文・コア | 46項目成功 | 全PS1の構文、画面ID・アプリ・Androidユーザー解析、未知オプション拒否、ネイティブ引数、Unicode、ライブログ、JSON |
| 本番Workerと模擬ADB/scrcpy | 54項目成功 | 5つの別画面、TapMakerへの切替、既存実行部の起動、画面ID返却、表示専用scrcpy、入力完了回数、停止・再開、切断、停止再試行、電話側所有権、更新、ユーザー切替、後片付け |
| ネイティブ出力取得 | 8項目成功 | 実際の子プロセスで、起動中のstdout/stderr・Unicode・直ちに終了する例外・停止・冪等な後片付け |
| 復旧対象の判定 | 9項目成功 | 前のWorkerの終了キュー特定、同一端末確認、自己・他端末・別実行ファイル・PID不一致・不明な接続先の除外、Windows形式のパス |
| APK | ビルド・検証成功 | versionName1.84 / versionCode87 / jp.tapmarker.lite / 全4ABI、v2/v3署名、16KiB zipalign |
| Androidの既存ロジック | coreグループ成功 | 同じ縮小ランタイムでゲーム判定、画像再現、入力・取消し、画面別レーン、仮想画面準備、ゲーム設定等。新しいPcRunLeaseは12項目成功。暗号相互接続と既存ADBスターターの8シナリオも成功 |
| Windows GUI / CIM列挙 | 未実施 | Windowsマシンが接続されていないため。復旧の実際のプロセス列挙はWindowsで確認が必要 |
| AndroidのPC向けProvider / Binder接続 | 実機未実施 | APIはコンパイルと所有権ポリシー・PC側模擬応答を確認。実際のAndroid IPCは未検証 |
| 実際のスマホ・5ゲームへの同時入力 | 未実施 | ユーザーのPC・Pixel 10・F-52Aへの接続がないため |

既存1.83と新1.84の証明書SHA-256は、どちらも以下です。

`7f42d636e4d3c251f475eb998fbe8e92b515ded8bce3ff4d30aa90b9a738cfb1`

同じ署名による上書き更新を用います。保存データの削除・アンインストールは行いません。

## この変更で確認したこと

- 不正な `--activity-new-task` は2.2同様に送信しません。模擬ADBも未知のオプションを拒否します。
- 画面のみ起動では従来のscrcpy方式を使います。TapMakerでの自動処理では既存のVirtualStartを起動し、TapMaker自身が画面作成・表示確認・ゲーム別処理を担当します。
- 自動処理用のPC表示は返却された `--display-id` と `--no-control` を使います。独立したPC連打やPCからの `adb shell input` はありません。
- TapMakerの画像・ゲーム確認を待って既存SplitPilotが各画面を並行処理します。受理された開始要求、PCプロセスの起動、入力完了回数、ゲームの実際の反応を区別します。
- 設定のないゲームやスマホ側で使用中の画面は、既存PC画面を閉じる前に拒否します。
- PC通信が途絶した場合はアプリ側で20秒後に停止します。停止要求中の切断は、復帰後に停止を優先して再試行します。
- スマホで開始し直した別世代の画面を、前のPCの終了要求で停止しません。
- 重複起動の復旧は前のWorkerの終了キューを使います。無関係なプロセスの一括終了やADBサーバーの一括停止はありません。
- PCスクリーン単体の終了はTapMaker停止と同義ではありません。停止ボタンまたはPC操作画面の終了を使います。

## 再現コマンド

PC側の開発用テスト（Windowsユーザーの実行にPythonは不要）:

```text
pwsh -NoProfile -File tests/Test-Core.ps1
pwsh -NoProfile -File tests/Test-Capture.ps1
pwsh -NoProfile -File tests/Test-Recovery.ps1
python3 tests/test_worker.py /absolute/path/to/pwsh
```

Androidソースにはビルドスクリプト、同一署名の開発用キーストア、依存jarとネイティブライブラリーを維持しています。API35 SDKを指定してビルドし、`run-tests.sh` のcoreグループを実行しました。full用の過去4録画テストは今回の実行対象に含めていません。

模擬環境の成功は、実際のゲームの実行成功率や同時入力の実機確認を示しません。
