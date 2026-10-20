# MarkerTap 1.85 / MultiScreen PC 2.4 検証記録

配置方式にPCを追加し、スマホ側で選んだ対象をPCへ一度だけ渡す変更です。PCによる画面表示と既存TapMakerによるゲーム別処理は1.84から引き継ぎます。

| 区分 | 確認内容 |
|---|---|
| 配置要求のJavaポリシー | 17項目成功。対象のコピー、一度だけの受付、別PC・古い要求の拒否、取消後の遅延開始拒否、置換後の旧応答拒否、2分の期限、失敗結果の保持 |
| 本番Workerによるスマホ→PC模擬連携 | 9項目成功。スマホの対象と順序、選び直し不要、重複作成防止、エラー返却、既存画面保持、受付後の取消、新しい要求への復帰、PC停止による待機取消し、終了 |
| 既存Workerの結合検証 | 54項目成功。通常起動・5画面・PC側の手動開始・TapMakerへの切替・入力回数・通信断・停止再試行・更新・ユーザー切替・後片付け |
| PC構文とコア | 46項目成功。全PS1の構文を含む |
| 復旧対象の判定 | 9項目成功。別端末・別プロセス・不明な接続先を除外 |
| Android既存回帰 | run-tests.shのcoreグループ成功。新しいPcPlacementRequestに加え、既存のPcRunLease12項目、ゲーム判定・録画再現・タップ取消し・仮想画面の独立性・暗号・ADBスターターを確認 |
| APK | versionName1.85 / versionCode88 / jp.tapmarker.lite、全4ABI、署名v2/v3と16KiB zipalign成功 |
| Windows GUI / Android UI・IPC / 実機入力 | 未実施。接続された利用者PC・スマホがないため |

証明書SHA-256は1.84と同じ `7f42d636e4d3c251f475eb998fbe8e92b515ded8bce3ff4d30aa90b9a738cfb1` です。上書き更新を使用し、アンインストールしません。

## 確認した設計

- 元の7つの配置方式の順序を維持し、8番目へ「PC（PCに各画面を表示して自律）」を追加しています。
- PCへの要求はアプリ内メモリーで管理します。再起動後に古い要求から自動起動することはありません。
- PC受付前・受付後・要求置換後の取消しを区別し、古い開始や応答を拒否します。
- 「全停止」、別の配置方式、仮想画面の終了は待機も取り消します。接続待ちは2分で終了します。
- 接続待ちは仮想画面の開始経路として扱い、端末の前面アプリで自動処理を始めません。
- 従来のPC側チェックリストからの開始は、追加のスマホ配置IDがない場合も使用できます。
- 開始要求の受付、各画面での実行状態、Androidへの入力完了、ゲームが反応したことは別です。後者は実機未確認です。

## 再現

```text
pwsh -NoProfile -File tests/Test-Core.ps1
pwsh -NoProfile -File tests/Test-Recovery.ps1
python3 tests/test_pc_placement.py /absolute/path/to/pwsh
python3 tests/test_worker.py /absolute/path/to/pwsh
```

AndroidはAPI35 SDKを指定してbuild.shでビルド後、run-tests.shのcoreグループを実行しています。full用の過去4録画テストは対象に含めていません。PC検証環境はLinux版PowerShell7.4.6で、Windows PowerShell5.1の実GUIは未検証です。模擬成功を実機の同時入力の成功とは扱いません。
