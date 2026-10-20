# MarkerTap v1.95：コイン落とし・ポイポイうさぎの入力修正

**修正ソースとビルド・ホスト検証は完了しています。従来の署名秘密鍵が取得できず、上書きインストール用APKは未完成です。**

パッケージは`jp.tapmarker.lite`、versionNameは`1.95`、versionCodeは`98`です。旧版を削除する必要はありません。配布アーカイブの`release/MarkerTap-v1.95-unsigned.apk`は署名前のビルド成果物です。そのままインストールできません。

## 原因と修正

| 対象 | 確認できた問題・改善 | 修正内容 |
| --- | --- | --- |
| コイン落とし／6738.png | v1.94の実際の認識処理で通常画面判定は成功するものの、連打対象の座標がnullになることを再現しました。 | 添付画像のボタン表示を見本に追加。背景色を照合から除き、ボタンの本体を確認します。同じ画像で約342,1240を取得できました。 |
| ポイポイうさぎ | 既存録画の通常24場面ではv1.94でも位置を取得できました。今回の実機で無反応だった原因は特定できていません。 | 動くキャラクターを主対象の照合から外し、下部のにんじんと皿で連打位置を確認するよう改善しました。 |
| 両ゲームの入力 | Pixelの従来の自動設定では押下要求1ms・解放待ち0msでした。送信完了だけではゲームによる受付を確認できません。 | 自動・離散設定では押下と解放を各17ms以上にします。60Hzの入力読取りを想定した保守的な設定であり、ゲームのFPSを測った値ではありません。明示的な「最短入力」は従来の1ms要求を維持します。 |
| 複数・仮想画面 | 入力設定を本体で選択中のアプリから取得する経路がありました。 | 実際の入力先アプリごとに押下・解放時間を適用します。 |

入力先の変更、CM、報酬、ミッション、満腹、「使用しない」などは既存の状態判定を継続します。ボタンや皿が消えた場合は、前の座標で連打を続けず再認識します。新しい画像の有効期限は1.5秒です。通常連打に実行時間の上限は追加していません。

## 検証の範囲

- 新規`GameInputRepairTest`は236項目に合格。添付画像での再現、既存のポイポイ録画、対象消失、合成した拡縮・ウインドウ位置・アニメーション変化、模擬60Hz受信側のDOWN/UP確認を含みます。
- 既存のテストを含む101組のJava回帰と、模擬ADB8シナリオに合格しました。実行ログは`tests-v1.95.log`です。
- Java/Androidビルド、4CPU向けネイティブライブラリ、16KB配置、DEXと検証対象Java入力の対応を確認しました。署名工程のみ一時検証鍵で試験しました。一時鍵のAPKは既存版に上書きできないため配布していません。
- 6738は追加見本として使用した画像であり、未知の画像に対する認識率の測定ではありません。ポイポイ24場面は既存録画の再生検証です。Android実機での入力受付・無反応の解消・実効速度は未確認です。

## 上書き用APKを完成させるために必要なもの

従来の`signing/lite-development.jks`、またはその鍵が入っている旧版ソースZIPが必要です。APK内の署名証明書から秘密鍵は復元できません。旧版と違う鍵のAPKを更新版として使うことはできません。

従来証明書のSHA-256：

```text
7f42d636e4d3c251f475eb998fbe8e92b515ded8bce3ff4d30aa90b9a738cfb1
```

Java17、Android API35、Build Tools35.0.0を用意し、元の鍵で次を実行します。秘密鍵はこのアーカイブに含めません。

```bash
ANDROID_SDK_ROOT=/path/to/android-sdk \
TAPMARKER_KEYSTORE=/path/to/lite-development.jks \
bash sign-release.sh
```

`sign-release.sh`は同梱の署名前APKを署名し、従来証明書との一致を確認してから最終APKを出力します。違う鍵なら出力を中止します。全ソースからの再ビルドは`build.sh`、回帰検証は`run-tests.sh`です。

入力に関する公式仕様：[Android AccessibilityService](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService#dispatchGesture(android.accessibilityservice.GestureDescription,%20android.accessibilityservice.AccessibilityService.GestureResultCallback,%20android.os.Handler))。
