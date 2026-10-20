# マーカー連打 v1.53：端末内接続の試作版

PC・Shizukuを使わず、同じ端末のワイヤレスデバッグに接続して仮想画面用の実行部を起動します。実行部は別のセッションへ移し、APKとのBinder接続を確認してからADB接続を閉じます。

**起動時にはWi-Fi接続が必要です。Wi-Fiを切った後のPixel 10での継続動作、実際のペアリング、ゲーム起動・入力は未検証です。再起動後にWi-Fiなしで仮想画面を起動できる版ではありません。** 通常画面でのユーザー補助による連打は、このADB起動処理を使いません。

## 起動の手順

1. APKを更新し、ユーザー補助で「マーカー連打」を有効にします。設定が無効な場合はアプリ情報の「制限付き設定を許可」を確認します。
2. 起動時はWi-Fiに接続し、「USBデバッグ」と「ワイヤレスデバッグ」をオンにします。USBケーブルは不要です。USBデバッグをオンに保つのは、Wi-Fi切断時にADB本体まで止まる条件を避けるためです。
3. 操作パネルの「複数アプリを開いて自律」で対象を選び、方式に「仮想画面（端末内接続・PC不要）」を選択します。「接続を開始」を押します。接続だけ先に準備する場合はメイン画面の「端末だけで接続」を使います。
4. 初回はAndroid設定の「ワイヤレスデバッグ」から「ペア設定コードを使用してデバイスをペア設定」を開き、コード画面を表示したまま待ちます。設定を開く操作とAndroidが要求する許可の承認は必要です。
5. ユーザー補助でコードとポートを読めれば、ペアリング・接続・実行部起動を自動で進めます。読めない場合は「接続情報を入力」を使用します。ペアリング用と接続用のポートは別です。初回はコードとペアリング用ポートを入力し、接続先の自動検出もできない場合は接続用ポートをコード空欄で入力します。
6. 「端末内実行部を起動 / ADB接続は切断済み」を確認します。仮想画面は各アプリの表示が確認できてから自律を開始し、起動に失敗したアプリは個別に表示します。

Wi-Fiなしの利用は、この起動準備が完了し実行部が生きている状態で試してください。Wi-Fiを切った状態での仮想画面作成、ゲーム入力、停止・再開をPixel 10で確認する必要があります。ゲーム自体がネット通信を必要とする場合、その通信は別です。

## 再起動・停止

- ペアリング鍵はAndroid Keystoreの鍵で暗号化し、アプリのバックアップ対象外領域へ保存します。コードはディスクへ保存しません。Android側の登録が有効なら、次回は保存した鍵で接続します。
- 端末再起動やアプリの強制停止、APK更新で実行部が終了した場合、Wi-Fiにつないで起動し直します。通常はペアリングコードの再入力まで必要ありませんが、Android側で登録を削除した場合などは再ペアリングが必要です。
- 「全停止」「複数画面停止」は入力と起動準備を止めます。接続準備中の取消しで、遅れて起動する実行部や画面を受け付けません。起動済みの実行部は通常の停止では残し、再開に利用します。
- ユーザー補助を終了すると、この版が起動した実行部を終了します。画面回転は既存仕様どおり停止します。
- Bluetooth接続や公開TCPポートの常時開放は追加していません。

## 確認の範囲

ビルド・署名、16KBページ用のネイティブライブラリとAPK内配置、既存の画面準備・停止・座標・状態遷移のJVMテスト、およびコード解析・証明書生成・SPAKE2/AES-GCMの相互通信を確認します。暗号テストは同じリリースのC/JNIソースをLinux向けにビルドしたものです。AndroidのTLS・ADBデーモン・設定画面との相互動作を実機で確認したことにはなりません。

## 再ビルドとライブラリの差し替え

Java、Python 3、ripgrep、Android SDK platform 35/build-tools 35.0.0が必要です。`ANDROID_SDK_ROOT`と自分の`TAPMARKER_KEYSTORE`を指定して`bash build.sh`を実行します。現行スクリプトの署名用設定はalias `tapmarker` / password `android`です。配布者の秘密鍵はソースに含まれません。自分の鍵で署名したAPKは配布APKへ上書きできず、別署名版のインストールには既存版の削除が必要となり、アプリ内設定も消えます。

`ANDROID_SDK_ROOT`を指定して`bash run-tests.sh`でJVMテストを実行します。ホストの暗号テストにはC/C++コンパイラも必要です。`toolchain/host-jni/jni.h`はAOSPのJNIインターフェース定義です。

LGPLライブラリの対応ソースは`third-party/spake2-java-2.2.1/`です。Cサブモジュールも含めています。Android NDKとCMakeを用いて、`android/src/main/cpp`をソースとし、AndroidのCMake toolchain・希望するABI・API 24以上・Releaseを指定してビルドできます。リンカには`-Wl,-z,max-page-size=16384`を指定し、生成された`libspake2.so`を`native/<ABI>/`へ配置します。Java側を変更する場合は同リポジトリの`android/src/main/java`をコンパイルしたjarで`libs/spake2-android-2.2.1.jar`を置き換え、その後APKを再ビルドします。変更したライブラリのデバッグ目的でのリバースエンジニアリングを妨げません。

## 仕様確認の参照

- [AndroidのADBドキュメント](https://developer.android.com/tools/adb)
- [Wi-Fi切断時のADB停止処理](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/core/java/com/android/server/adb/AdbDebuggingManager.java)
- [USB・Wi-Fi両方が無効の場合にADB本体を停止する処理](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/core/java/com/android/server/adb/AdbService.java)
- [Shizukuの接続から独立した実行部の起動例](https://github.com/RikkaApps/Shizuku/blob/master/manager/src/main/jni/starter.cpp)
- [ADBで起動するサービスの再起動後の制限の説明](https://shizuku.rikka.app/guide/setup/)

上記の停止条件を踏まえた設計であり、端末個別の動作保証ではありません。
