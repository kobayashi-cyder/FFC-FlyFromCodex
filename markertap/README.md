# MarkerTap 復元用バックアップ

このブランチは MarkerTap の再開用です。既存の FFC/BANC888 の main とは別に管理します。

v1.117のソース実体を保存し、結合後のSHA-256とZIP CRCを検証済みです。code/にはその後の変更を保存します。

## 保存状態

`BACKUP_STATUS.json` が `source_backed_up` になるまでは、ソース実体の保存は未完了です。
完了後の正本は `archive/manifest.json` と `archive/source.zip.part*` です。
`code/` はコード参照用の複製で、全依存ファイルを含む復元元は分割アーカイブです。

## 復元

```sh
git clone --branch markertap/recovery --single-branch https://github.com/kobayashi-cyder/FFC-FlyFromCodex.git
cd FFC-FlyFromCodex
python3 markertap/tools/restore_source.py --extract-to ./MarkerTap-restored
```

復元ツールは各分割ファイルと結合後アーカイブの SHA-256 を検証します。
--extract-to は基礎アーカイブを展開後、code/の最新変更を重ねます。v1.117だけを復元する場合は --baseline-only を加えてください。
復元したソース内の `build.sh` と `run-tests.sh`、各版のガイドを確認してください。

既存ガイドの再ビルド要件は Java 17、Android API 35、Build Tools 35.0.0、Python 3 です。
実際の同梱スクリプトの指定を優先してください。

## 署名鍵

署名鍵・パスワード・一時取得URLをこのブランチに平文で保存しません。
既存 APK を上書きできる更新には、別途保管している `MarkerTap-lite-signing-backup.zip` が必要です。
復元した鍵のパスを `TAPMARKER_KEYSTORE` として指定します。

## 次の作業

`WORK_NEXT.md` を最初に読んでください。v1.120は通常解像度の領域取得を維持し、起動中のWi-Fi切断で接続済み実行部を終了させる経路を修正。実行中セッションの復旧と、全体画像取得の例外・OOM処理を追加しています。詳細は `ROI_MULTIPLEX_SPEC.md` を参照してください。署名済みAPKと検証記録は `releases/` に保存します。

## ダウンロード

- [MarkerTap v1.120 APK](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/releases/download/markertap-v1.120/MarkerTap-v1.120-connection-recovery.apk)
- [APK一つ入りのZIP](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/releases/download/markertap-v1.120/MarkerTap-v1.120.zip)

ログインなしのHTTP 200取得と、検証済みAPKとのSHA-256一致を確認済みです。APKを直接保存できない場合はZIPを展開してください。


## v1.121 異常終了後に残存を残さない改修（ソース反映済み）
- 新しい仕様書：[GUIDE-v1.121.md](code/TapMarker/GUIDE-v1.121.md)
- 不意のアプリ・サービス終了、仮想ディスプレイ失敗、実行部Binder切断で、MarkerTap管理下の実行処理をまとめて終了します。
- 新しい owner Binder 監視はShizukuとADB両経路に使用。自動で旧画面を再開する挙動は異常終了時には無効化され、手動での再開が必要です。
- **v1.121は現時点でソース改修段階**です。下記v1.120のAPKにはこの変更は含まれません。
- 独立したCIで一時的な検証用鍵によるソースビルド・回帰テストを行います。本番の更新APKは従来と同じ署名鍵で署名しなければ既存アプリを上書きできません。

