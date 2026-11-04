# MarkerTap v1.158 — v1.157の全クラス・最小化UIを保持した評価関数付きDEX候補

このブランチは**署名済みv1.157を唯一の入力APK**として、他の機能を取り除かないようDEXへ追加コードを統合します。旧ソース一式から再ビルドすると324クラスが消える問題を避けています。

## 実装済み
- 元のv1.157 APK SHA-256: `0108aadfa717c676dfe30b4f678a5c1526277e11b01ae16f85317d8b8dd50f7d`
- 元1,613クラス → 候補1,637クラス。元クラス欠落0、元アセット削除0・変更0。差分は`AdV157DexBridge`と新規評価クラスのみ。
- `PanelModeController`等を保持：最小化するとドラッグ可能な小さなフロートが残り、タップで直前の固定／フロート表示に戻る。最小化しても自律・仮想2画面処理を停止しない。
- `RewardEvaluationConnectome`: `J = 100*C + 20*ln(1+Q) - 4*T_CM`。
- 誤加点防止：登録ゲームの**完了したゲット操作**、報酬画面の独立した2フレーム証拠、実際のpt残高の別々の新鮮な2フレーム増加、同時獲得のゲーム間帰属競合抑制。農園うさぎは事前プレイ中残高を使う自動付与型を別に検査。
- 物理：AccessibilityServiceの対象ウィンドウスクリーンショットからHUD領域のみを読取（実行系の既存キャプチャとは独立、低頻度）。
- 仮想：元`RegionImage`から**必要なHUD領域だけ**読み、元画面のタッチ・フレーム・プレビューを変更しない。
- 評価による次ゲームの選択：実績未確定時は元の巡回処理を維持。確定受領がある場合だけ`RewardV158Scheduler`で次走査レーンの順序を選択。片方に偏りすぎないよう制限し、停止・手動介入中のレーンを選ばない。並列2画面は独立処理を維持。
- CM復帰／アプリ強制停止が開始されれば未確定受領は破棄。

## ホスト・構造テスト
[元DEXの保存とJava8回帰を検証するCI](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/actions/runs/41794569467):
- 新規実評価テスト9件、仮想ROIテスト10件、実評価スケジューラー8件を含む
- 元1,613クラス保存・全assets保存・最小化13クラス保存
- 未署名エンジニアリング候補生成

## 署名と実機の制約
[原署名鍵のCI確認](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/actions/runs/41794831087)は**元の署名キーストア／パスワードの保護済みGitHub Secretsが未設定で停止**しています。元証明書と異なるテスト鍵では既存APKの上書き更新ができません。暗号鍵・パスワードをソースや会話に貼り付けてはいけません。

[Android 15エミュレーターの基本起動・ユーザー補助サービス有効化操作後のプロセス生存確認は成功](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/actions/runs/41795017451)。ただしサービスの実処理実行・最小化操作・ゲーム操作は検証対象外です。Android 17 / Pixel 10 の実機で、最小化のドラッグ移動と戻り、2仮想画面の別々の報酬受領、CMからの再起動、クラッシュ、電池負荷、OCR誤読をまだ検証していません。

**このブランチのAPKは未署名の工学検証物であり、現行v1.157の安定配布・READMEリンクを置き換えません。** 既存端末へ上書きできる元証明書署名と実機受入が終わるまで正式版と表示しないこと。

## 対応するGitHub Actions
- [署名済みv1.157への差分DEX結合・旧機能監査](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/actions/workflows/markertap-v1158-preserved-dex-candidate.yml)
- [元証明書のテスト署名ゲート（鍵がないと停止）](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/actions/workflows/markertap-v1158-preserved-original-sign.yml)
- [Android15エミュレーター起動・ユーザー補助サービス検査](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/actions/workflows/markertap-v1158-preserved-emulator-smoke.yml)
