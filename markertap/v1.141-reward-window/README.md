# MarkerTap v1.141：農園の報酬画面・実画面の範囲と取得監視

v1.140（PR21 / `5ed7ae752666325c1121e0140a6114da61a5b7fb`）を継承。versionNameは1.141、versionCodeは144。

添付診断の「＋100」報酬画面を従来の見本で認識できないことと、`ContentViewport.locate()` が画面外へ一部延びたウインドウ範囲を例外にする経路を確認した。診断ZIPには実際の失敗時の範囲値がなく、例外の具体的な範囲値と両者の因果順序は未確定。

- ウインドウ範囲は画像内の可視部分との交差を使う。反転・空・画像と交差しない範囲は引き続き拒否し、寸法を記録。画像の縮小や全面への推測置換は行わない。
- 実画像の「コインを獲得しました」と「閉じる」を独立した2領域の見本として追加。元のclose=.008 / start=.016の距離閾値を維持する。
- 農園の報酬確認を主画面の詳細探索より先に実行。フル画面座標で両領域が対象ウインドウ内にあることを確認するため、システムバーのInsetsがあっても座標を二重にずらさない。
- 実際の `AutoPilot$1.onSuccess()` を包み、凍結した実行トークンと撮影時の範囲を取得記録へ渡す。元の変換・解放処理を保持し、古い実行のコールバックは記録しない。
- 実画面の15秒監視を、取得成功と判定完了に分ける。約1.8秒前の結果が毎回更新されている状況を、15秒間画像が届かない状況と混同しない。操作時の1500ms鮮度制限と従来の復帰ゲートは変更しない。

## 検証

ローカルで署名済みAPKをビルドし、以下を通過しました。詳細は [validation-local.json](validation-local.json) に記録しています。

| 検証 | 結果 |
| --- | --- |
| 可視ウインドウ範囲 | 22項目合格 |
| 実画面の取得監視 | 97項目合格 |
| 実画像由来の報酬判定・状態遷移 | 83項目合格 |
| 新規の動作検証合計 | 202項目合格 |
| 既存core / host試験 | 合格 |
| 障害再生 | 8件の結果ダイジェスト一致 |
| APK内の新しい呼び出し | 16項目合格 |
| 既存の復帰処理 | 12項目合格 |
| 同一証明書・署名v2/v3・版番号・整列 | 確認済み |

コード改修コミット `cfc86e2651290e0b7df8a4c78951c3f631a704a4` の[GitHub Actions実行 #40414483942](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/actions/runs/40414483942)も成功しています。CIは鍵を使わない未署名ビルド、ローカル検証は従来鍵で署名したAPKに対する結果です。

Pixel 10 / F-52Aでの実機入力、実際の報酬加算、長時間無人運転は未検証。

## APKと署名

| 項目 | 値 |
| --- | --- |
| 署名済みAPK | `MarkerTap-v1.141-signed.apk` |
| 取得先 | この改修依頼の会話添付 |
| ファイルサイズ | 5,102,363 bytes |
| パッケージ | `jp.tapmarker.lite` |
| versionName / versionCode | `1.141` / `144` |
| 署名方式 | APK Signature Scheme v2 / v3 |
| 改修ソース | `cfc86e2651290e0b7df8a4c78951c3f631a704a4` |
| 作業ブランチ | `markertap/reward-window-v1.141` |
| PR | [#22（ドラフト・未マージ）](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/pull/22) |

APKのSHA-256：

```text
3b35740dcdbeb76615eec7218a861a5b679299c181070587c898a549cf68321f
```

従来版と一致した署名証明書SHA-256：

```text
7f42d636e4d3c251f475eb998fbe8e92b515ded8bce3ff4d30aa90b9a738cfb1
```

Actions成果物の `MarkerTap-v1.141-reward-window-unsigned-evidence` は未署名APKと検証記録です。署名済み更新版の取得には、会話添付の上記ファイルを使ってください。公開済みの過去版は[Releases](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/releases)で確認できます。

## 更新手順

1. 旧版で「全停止」を押します。
2. 会話添付の `MarkerTap-v1.141-signed.apk` を開き、既存アプリへの更新としてインストールします。
3. アプリ情報で版を確認し、ユーザー補助の状態を確認します。
4. 対象ゲームを開いて自律を開始します。今回の診断は実画面モードです。

署名・パッケージ・版番号は更新用として検証済みですが、端末上でのインストールと動作は未検証です。設定を保持するため、更新失敗時に旧版をアンインストールせず、表示されたエラーを記録してください。

## 再構築

Java 17（`java` / `javac`）、Python 3、Bash、Android API 35 / Build Tools 35.0.0、JADX 1.5.3を用意します。リポジトリの `markertap/reward-window-v1.141` をチェックアウトし、ルートで実行してください。復元先は新しい空のディレクトリを指定します。

```bash
python3 markertap/v1.141-reward-window/restore_project.py /tmp/markertap-v1141-source
TAPMARKER_KEYSTORE=/private/path/to/lite-development.jks \
python3 markertap/v1.141-reward-window/build_ci.py \
  --sdk /path/to/android-sdk \
  --jadx /path/to/jadx-1.5.3-all.jar \
  --output /path/to/results
```

署名には従来のJKSを `TAPMARKER_KEYSTORE` で指定する。未指定時の成果物は `unsigned` として明確に区別される。秘密鍵はソースに含めない。

見本の再生成は `tools/PrepareRewardReference.java` を既存runtime APIとともにコンパイルし、元の診断JPEGとこのモジュールのパスを引数に実行する。テスト用保存物は見出しとボタン周辺の2本の画像領域に限定し、残高・操作パネル・広告バナーは含めない。

`build_ci.py` は独立した一時ディレクトリに再度ソースを復元し、ビルド・回帰・APK内部検査・署名検証を実行します。署名時は指定した出力先へ `MarkerTap-v1.141-signed.apk`、`validation.json`、`SHA256SUMS.txt` とログを保存します。ネットワーク接続が必要な依存物取得も含みます。

次に確認する実機項目と診断の共有内容は [WORK_NEXT.md](../WORK_NEXT.md) にまとめています。
