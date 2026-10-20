# v1.51 仮想ディスプレイの小窓と最小表示

## 操作

1. APKを上書き更新し、アクセシビリティサービスを有効にする。
2. 展開 →「複数アプリを開いて自律」→ 対象アプリを選択。
3. まず「仮想画面（Shizukuなし）」を選ぶ。
4. 各ゲームの表示とアプリ名を確認できた場合に、アプリ別のオートマトンを開始。
5. 展開 →「仮想画面を最小表示」で見た目を小さくする。「仮想画面を拡大表示」で復元。

「全停止」は入力と自動開始の待機を停止し、仮想画面自体は残す。「仮想画面を全て閉じる」は作成した画面・Surfaceを解放する。

## 実装した標準方式

DisplayManagerでアプリごとの独立した仮想画面を作成し、SurfaceTextureへ表示する。別ゲームの起動前にisActivityStartAllowedOnDisplayでAndroidの許可を確認し、ActivityOptions.setLaunchDisplayIdで指定する。入力はAccessibilityのGestureDescription.Builder.setDisplayIdで各仮想画面へ送る。本体画面ID0は仮想入力として受け付けない。

Shizukuは標準方式に不要。ただし、仮想画面の作成成功と別ゲームの起動許可は別。Androidが別ゲームの起動を拒否した場合、その旨を小窓に表示して自律開始しない。この場合に標準方式が使えるとは保証しない。ゲームが別画面へ移動せず本体に出た場合も、アプリの表示確認が成立しない限り自律開始しない。

追加の選択肢「仮想画面（追加権限方式）」はShizukuを起動し、このアプリへの権限を許可した場合だけ動作する。UserServiceが仮想画面を作成し、登録コンポーネントを起動してその画面へ入力する。一般的なシェルコマンドの実行機能は公開しない。指定できる入力先は、このサービス自身が作成した仮想画面だけ。

## 最小表示の意味

表示用の小窓と内部解像度は分離。内部は幅720ピクセル・端末の縦横比・端末と対応する密度で保持し、表示のみタイトル＋1ピクセルまで縮小する。画像はAccessibility.takeScreenshot(displayId)で内部ディスプレイから取得し、縮小プレビューを引き延ばして判定しない。画像取得不可なら入力を保留する。

小窓を小さくしても内部のゲーム描画・ゲーム側メモリが同じなら、その負荷はほぼ残る。内部解像度を下げる設定は本版では追加していない。極小の表示中は目視や手動操作に向かないため、必要時に拡大する。

画像バッファ1枚の計算例（RGBA、ゲーム等のメモリを含まない）：720×1600で約4.39MiB、360×800で約1.10MiB。複数バッファとゲーム本体が別途必要。実メモリ・CPU・FPS・連打頻度は未実測。

## 自律処理の範囲

増えうさぎ・農園うさぎの既定オートマトンを再利用。各画面に独立した状態・監視領域・復帰回数を保持する。その他アプリは保存済みオートマトンが必要。領域中央タップ、既定2ゲームの収穫連打、待機、登録アプリ復帰、停止に対応。カスタム曲線・マーカー指定アクションは該当アプリを停止する。

参照画像が現在の状態を確認したときだけタップし、アカウント・異常表示のテキストを検出したら入力を保留する。仮想画面ではその表示を必ず自動解除できるとは保証しない。

## 確認状況

ビルド・APK署名確認・既存の遷移テストと録画ROI再生テスト・複数画面の配置計算・仮想画面ID/座標の14項目のテストを実施。Pixel 10での仮想画面生成、両ゲームの起動許可、最小表示での画面取得、入力、実際のゲーム遷移は未検証。既定画素判定が仮想画面のInsetsやレイアウトに合わない場合、タップを保留する。最小表示によって動作速度や稼働数が向上した実測結果はない。

## 参照と依存

- Android DisplayManager: https://developer.android.com/reference/android/hardware/display/DisplayManager
- Activity launch policy: https://source.android.com/docs/core/display/multi_display/activity-launch
- ActivityManager: https://developer.android.com/reference/android/app/ActivityManager
- GestureDescription.Builder: https://developer.android.com/reference/android/accessibilityservice/GestureDescription.Builder
- Shizuku API 13.1.5（任意の追加方式のみ使用）: https://github.com/RikkaApps/Shizuku-API

ShizukuのMITライセンスをlibs/SHIZUKU-LICENSE.txtとAPKのassets/third-party/Shizuku-LICENSE.txtに含めた。VirtualWindows/VirtualShell/VirtualProtocolは本プロジェクトの実装。
