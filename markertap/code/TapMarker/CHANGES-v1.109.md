# v1.109 変更記録

仮想画面の起動幅を処理負荷設定から分離し、32〜720pxの段階試験と実寸の記録を追加しました。選択した起動幅は次の画面作成から適用されます。

- 既存の自動設定を維持し、明示した幅ではDPIの72下限を外して同比率で縮小します。
- 実行部の最小幅・高さを32px、最小DPIを1へ変更し、旧実行部の混在をrevision 90で拒否します。
- 旧画面の終了処理を待ってから再作成し、古い入力と画像を世代で無効化します。
- 直近80起動、実際の幾何、アプリ別の手動結果、起動失敗、評価時の診断値を保存します。
- 全画面が手動で正常と評価された幅を復元できます。
- 日本語・英語の操作UI、ビルド・署名、対象回帰、録画画素の段階縮小比較を用意しました。

最終検証の範囲・制限はVALIDATION-v1.109.jsonとGUIDE-v1.109.mdを参照してください。

変更ファイル：

- 更新 AndroidManifest.xml
- 追加 GUIDE-v1.109.md
- 更新 README.md
- 追加 VALIDATION-v1.109.json
- 追加 build-v1.109.log
- 更新 build.sh
- 更新 res/values/strings.xml
- 更新 res/values-en/strings.xml
- 更新 res/values-ja/strings.xml
- 追加 resolution-replay-v1.109.csv
- 更新 run-tests.sh
- 更新 src/jp/tapmarker/MainActivity.java
- 更新 src/jp/tapmarker/PerformanceSettings.java
- 更新 src/jp/tapmarker/ProgressErrors.java
- 更新 src/jp/tapmarker/UiCatalog.java
- 更新 src/jp/tapmarker/VirtualProtocol.java
- 追加 src/jp/tapmarker/VirtualResolutionLab.java
- 追加 src/jp/tapmarker/VirtualResolutionPolicy.java
- 更新 src/jp/tapmarker/VirtualShell.java
- 更新 src/jp/tapmarker/VirtualWindows.java
- 追加 test-resolution.sh
- 追加 tests/.image6738-replay.bin.gz.T76OTo
- 追加 tests/.video6724-replay.bin.gz.xlNDTJ
- 追加 tests/VirtualResolutionPolicyTest.java
- 追加 tests-v1.109-final-resolution.log
- 追加 tests-v1.109.log
- 更新 tools/generate-locales.py
- 追加 tools/resolution-probe/ResolutionReplayProbe.java
- 追加 tools/resolution-probe/run.sh
- 更新 tools/ui-catalog-index.json
- 更新 tools/ui-translations.tsv
