# MarkerTap 1.87 / MultiScreen PC 2.6 検証記録

2026-10-16 06:36提出（保存済み06:32時点）の報告を再現条件としました。実機はPixel 10 / Android17 / API37です。買い物の画像175453ms待ち、農園の入力1466回とPlay Store切替の履歴、復帰済みエラーの残留を扱っています。

| 検査 | 結果 |
|---|---|
| VirtualFrameQueueTest | 27項目成功。175453msの古い画像、画面別重複防止、正の画面ID、世代の一致、古い結果と遅い結果の破棄、通常画像の復旧優先、400ms間隔、2500ms応答期限、失敗後の待機、4画面の公平な取得順、停止後の結果拒否 |
| InputTargetRecoveryTest | 22項目成功。UID拒否の文字列、個別予算、最大3回と間隔、復帰後の画像・アプリ確認、遅延応答の拒否 |
| TargetedInputTest | 97項目成功。対象UID・表示ID・Android旧APIガードを維持し、UID拒否を無制限入力で再試行しないことを確認 |
| Android既存回帰 | run-tests.shのcoreグループ成功。各ゲームの画像判定、録画再現、状態機械、タップの押下・解放・取消し、PcPlacementRequest17、PcRunLease12、DisplayTaskGuard134、暗号・ADBスターターを含む |
| 本番Workerによるスマホ→PC模擬連携 | 17項目成功。旧版の14項目に加え、復帰中と通常処理の区別、復帰後の現エラー解除と履歴保持、1466回以上の回数保持と判定中表示を確認 |
| PC Worker全体の結合検査 | 54項目成功。表示起動、5画面、TapMakerへの委任、通信断、停止再試行、上書き更新、ユーザー変更と終了時の後片付け |
| PowerShellコア・構文 | 47項目成功。全PS1構文、旧状態ファイル互換、診断出力 |
| 旧PC処理の復旧判定 | 9項目成功。別端末・別プロセスを除外 |
| APK | versionName1.87 / versionCode90 / jp.tapmarker.lite。実行部89。4ABI、署名v2/v3、16KiB zipalign成功 |
| 実機API・Windows GUI・ゲーム反応 | 未実施。Pixel 10実機はこの環境に接続されていません |

証明書SHA-256は既存版と同じ `7f42d636e4d3c251f475eb998fbe8e92b515ded8bce3ff4d30aa90b9a738cfb1` です。

## 仕様の確認

GitHub経由でAOSPのAccessibilityServiceとAbstractAccessibilityServiceConnectionを取得し、公開仮想画面の論理IDを指定したtakeScreenshot呼出し、取得間隔、結果バッファと失敗応答を照合しました。Android CTSの仮想画面取得テストと333msの間隔テストも参照しました。

- https://github.com/aosp-mirror/platform_frameworks_base/blob/main/services/accessibility/java/com/android/server/accessibility/AbstractAccessibilityServiceConnection.java
- https://android.googlesource.com/platform/cts/+/master/tests/accessibilityservice/src/android/accessibilityservice/cts/AccessibilityTakeScreenshotTest.java

撮り直しでは実際に取得した同じ縦横の画像だけを採用します。キャッシュの取得時刻だけを更新せず、本体画面0への代替取得も行いません。復帰確認は入力成功回数を増やしません。通常画像の新しいデータを古い撮り直し結果で上書きしません。

## 再現環境と制約

API35 SDK、Java17、縮小済みJavaクラスでcoreグループを実行しました。過去4録画用のfullグループは今回の対象に含めていません。PCはLinux版PowerShell7.4.6と実行可能なADB/scrcpy模擬プログラムを使用しました。二つのPC結合検査は同じ模擬端末の排他ロックを使うため順番に実施しました。

PC全体検査の初回は、PowerShellの外部テレメトリ通信が自動審査で拒否されて中断しました。Microsoftの公式設定とソースを確認し、起動前にPOWERSHELL_TELEMETRY_OPTOUT=1を設定した条件で再実行し、54項目を完了しました。開発用テストもこの設定を継承します。

```text
ANDROID_SDK_ROOT=... TAPMARKER_KEYSTORE=... TAPMARKER_OUTPUT=... bash build.sh
ANDROID_SDK_ROOT=... bash run-tests.sh
POWERSHELL_TELEMETRY_OPTOUT=1 pwsh -NoProfile -File tests/Test-Core.ps1
POWERSHELL_TELEMETRY_OPTOUT=1 pwsh -NoProfile -File tests/Test-Recovery.ps1
python3 tests/test_pc_placement.py /absolute/path/to/pwsh
python3 tests/test_worker.py /absolute/path/to/pwsh
```

再取得キューとPC状態同期の成功は、Android17実機での画面取得成功やゲーム反応を保証しません。画面画像が提示されていないため、主画面認識が合わない原因や広告終了判定まで特定したとは扱いません。
