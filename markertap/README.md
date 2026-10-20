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

`WORK_NEXT.md` を最初に読み、解像度修正の未完了状態を引き継いでください。
