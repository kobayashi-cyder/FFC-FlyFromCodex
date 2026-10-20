# MarkerTap 1.86 / MultiScreen PC 2.5 検証記録

2026-10-16 06:08の実機報告をもとに、UID拒否後の画面単位の復帰、PCプレビューの非表示、削除・停止状態の同期を修正しました。GitHubのAOSP InputManagerService実装と照合し、TARGET_MISMATCHが指定UIDの入力先不一致を表すことを確認しました。実際の別ウィンドウ所有者は報告に含まれないため断定していません。

| 区分 | 結果 |
|---|---|
| 入力先変更の復帰 | InputTargetRecoveryTest 22項目成功。実際のエラー文字列、画面ごとの予算、最大3回・10秒間隔、復帰前の画像拒否、別パッケージ拒否、古い画像拒否、停止後の遅延応答拒否、新しい入力完了による解除 |
| UIDを限定した入力 | TargetedInputTest 97項目成功。実際のAOSP例外を反射呼出しから再現し、画面ID・UIDを含む復帰理由へ変換。UID変更・無制限オーバーロードへの再試行なし。旧Androidのガードも維持 |
| Android既存回帰 | run-tests.shのcoreグループ成功。PcPlacementRequest17、PcRunLease12、DisplayTaskGuard134を含む。各ゲームの画像判定、録画再現、入力中断・解放、仮想画面、暗号とADBスターターを確認 |
| 本番Workerによるスマホ→PC模擬連携 | 14項目成功。従来9項目に加え、削除済み画面の除去、残る画面プロセスの保持、652回とUID拒否の保持、全体停止の優先、新しい状態への同期を確認 |
| 既存Workerの結合検査 | 54項目成功。通常起動、5画面、TapMakerへの切替、通信断、停止再試行、上書き更新、ユーザー切替と後片付け |
| PowerShell構文・コア | 47項目成功。全PS1構文、旧状態ファイル互換、Android機種・API・実行部の診断出力 |
| 以前のPC処理の復旧 | 9項目成功。別端末・無関係なプロセスを除外 |
| APK | jp.tapmarker.lite、versionName1.86、versionCode89、実行部89。4ABI同梱、署名v2/v3、16KiB zipalign成功 |
| Windows GUI・実Android IPC・ゲームへの入力 | 未実施。利用者のPCとスマホはこの環境に接続されていません |

証明書SHA-256: `7f42d636e4d3c251f475eb998fbe8e92b515ded8bce3ff4d30aa90b9a738cfb1`。1.85と一致し、同じパッケージへの上書き更新です。

## 実行した検証

```text
ANDROID_SDK_ROOT=... TAPMARKER_KEYSTORE=... TAPMARKER_OUTPUT=... bash build.sh
ANDROID_SDK_ROOT=... bash run-tests.sh
pwsh -NoProfile -File tests/Test-Core.ps1
pwsh -NoProfile -File tests/Test-Recovery.ps1
python3 tests/test_pc_placement.py /absolute/path/to/pwsh
python3 tests/test_worker.py /absolute/path/to/pwsh
apksigner verify --verbose --print-certs MarkerTap-v1.86-pc.apk
```

AndroidはAPI35 SDKとJava17を使用し、APKへ変換する縮小済みJavaクラスで検証しました。過去4録画用のfullグループは今回の対象に含めていません。PCはLinux版PowerShell7.4.6と実行可能なADB/scrcpy模擬プログラムを使用しました。二つのPC結合検査は同じ模擬端末の名前付きロックを使うため、順番に実行しました。

「隠す」の実GUI操作、実機上のウィンドウ切替と復帰、ゲームが反応することは未検証です。農園の画像停止や入力0回の原因を、この報告だけで確定したとは扱いません。
