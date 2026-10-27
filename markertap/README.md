# MarkerTap

現在の更新候補は **v1.141 / versionCode 144**、パッケージは `jp.tapmarker.lite` です。
2026-10-27時点の作業ブランチは `markertap/reward-window-v1.141`、[PR #22](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/pull/22)はドラフト・未マージです。

## 参照先

| 用途 | 参照先 |
| --- | --- |
| 変更内容・インストール・署名確認 | [v1.141 README](v1.141-reward-window/README.md) |
| 次に作業を再開する時 | [WORK_NEXT.md](WORK_NEXT.md) |
| 署名済みAPKの検証結果 | [validation-local.json](v1.141-reward-window/validation-local.json) |
| 診断画像と追加見本の来歴 | [reference-provenance.json](v1.141-reward-window/reference-provenance.json) |
| バックアップと現行版の状態 | [BACKUP_STATUS.json](BACKUP_STATUS.json) |
| コード改修時のCI | [成功した実行 #40414483942](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/actions/runs/40414483942) |

## v1.141の変更と検証

農園うさぎの「＋100」報酬画面を、見出しと「閉じる」の両方で確認し、主画面の詳細探索より先に処理します。ウインドウ範囲には画像内の可視部分との交差を使い、実画面の画像取得成功と判定完了を別々に監視します。

追加202項目、既存core/host試験、障害再生8件、APK内の追加16項目・既存復帰12項目を通過したとの検証記録があります。検証記録には従来証明書との一致、APK署名v2/v3、パッケージ・版番号・整列の確認も記載されています。Pixel 10 / F-52Aでの実機入力、実際の報酬加算、長時間無人運転は未検証です。

## APKの取得

この文書の旧記載では、署名済み更新版 `MarkerTap-v1.141-signed.apk` は「改修依頼の会話添付」から取得するよう案内していました。その記載自体は検証記録に残っていますが、**このリポジトリから取得できる添付URLやGitHub Release資産を現在確認できていません**。ファイル名・ハッシュの記録だけではAPK本体をダウンロードできるとは限りません。

- 検証記録上のAPK SHA-256：`3b35740dcdbeb76615eec7218a861a5b679299c181070587c898a549cf68321f`
- 検証記録上の証明書SHA-256：`7f42d636e4d3c251f475eb998fbe8e92b515ded8bce3ff4d30aa90b9a738cfb1`
- 今回のActions成果物：`MarkerTap-v1.141-reward-window-unsigned-evidence`（未署名の検証用。インストール用配布物ではありません）
- [公開Release一覧](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/releases)

署名済みAPK本体を実際に取得できる状態にするには、従来の非公開JKSを保護された環境で使って再生成し、SHA-256を照合したうえでRelease資産として公開する必要があります。秘密鍵・パスワードは公開リポジトリやチャットへアップロードしないでください。

## v1.141ソースの復元

`code/TapMarker` は過去のコード参照用の複製です。v1.141を再構築する場合は、専用の復元スクリプトで基礎アーカイブと各版の変更を統合してください。

```bash
git clone --depth 1 --branch markertap/reward-window-v1.141 --single-branch https://github.com/kobayashi-cyder/FFC-FlyFromCodex.git
cd FFC-FlyFromCodex
python3 markertap/v1.141-reward-window/restore_project.py /tmp/markertap-v1141-source
```

復元先には新しい空のディレクトリを指定します。v1.141の再ビルド・回帰試験・APK内部検査をまとめて実行する手順は次のとおりです。

```bash
TAPMARKER_KEYSTORE=/private/path/to/lite-development.jks \
python3 markertap/v1.141-reward-window/build_ci.py \
  --sdk /path/to/android-sdk \
  --jadx /path/to/jadx-1.5.3-all.jar \
  --output /path/to/results
```

Java 17（`java` と `javac`）、Python 3、Bash、Android API 35 / Build Tools 35.0.0、JADX 1.5.3を使用します。依存物の取得が必要な処理ではネットワーク接続も必要です。署名鍵未指定時は未署名APKが出力されます。復元だけを行ったディレクトリの `build.sh` はビルド用で、上記 `build_ci.py` が回帰試験を含む検証の入口です。

## 署名鍵

既存APKへの更新には、別途保管済みの `MarkerTap-lite-signing-backup.zip` から復元した従来のJKSを使います。そのパスを `TAPMARKER_KEYSTORE` に指定します。秘密鍵・パスワード・一時取得URLは公開リポジトリへ保存しません。

## 基礎バックアップと過去版

`BACKUP_STATUS.json` の `version_name: 1.117` は基礎バックアップの版、`current_version_name: 1.141` は今回の更新候補を表します。基礎アーカイブの正本は `archive/manifest.json` と `archive/source.zip.part*` です。各分割ファイルと結合後のSHA-256を復元ツールで検証します。

過去のv1.117系の復元には `markertap/tools/restore_source.py` を使います。この旧経路と `code/` の複製だけではv1.141の全変更は適用されません。

- [v1.121ガイド（当時の全停止処理）](code/TapMarker/GUIDE-v1.121.md)
- [v1.140の復帰ゲートと実画面診断](v1.140-recovery-progress/README.md)
- [v1.141の統合手順](v1.141-reward-window/README.md)
