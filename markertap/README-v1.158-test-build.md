# MarkerTap — 通常配布版への復帰

**通常利用するAPK：v1.157署名済み統合版**

**[▶ 通常版 MarkerTap v1.157 APK（READMEの現行リンクと同じ）](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/releases/download/markertap-v1.157-rc1/MarkerTap-v1.157-integrated-signed.apk)**

このバージョンは従来のMarkerTapと同じ更新用署名証明書で署名されています。v1.158のテスト署名APKは通常配布版から外し、開発用の記録としてのみ残します。**テスト署名版をv1.157の正式署名版へそのまま上書きすることはできません**。テスト署名版をインストール済みなら、設定・診断データを退避したうえで再インストールが必要になる場合があります。

---

# MarkerTap v1.158 — test build only (2026-11-04)

**正式更新APKではありません。**

GitHub Actionsで完全ソースから作成し、使い捨ての検証用鍵でv2/v3署名したAPKと、元の正規署名鍵で署名するためのaligned未署名APKを保存しました。

## ダウンロード（GitHubへのログインが必要な場合があります）

- **[v1.158テストAPK＋未署名APK＋SHA256検証ZIP](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/actions/runs/41756480804/artifacts/13288276839)**
- [成功したGitHub Actionsのログ](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/actions/runs/41756480804)
- [124項目の報酬評価/JVM単体テスト成功](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/actions/runs/41756487915)
- [Android 15エミュレーター起動成功（前回ソース版）](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/actions/runs/41746583393)

アーティファクトZIPの中身：

- `MarkerTap-v1.158-TEST-SIGNED-NOT-UPGRADEABLE.apk` — v1.158, versionCode161。**検証専用署名**。既存インストールへの上書き不可。
- `MarkerTap-v1.158-UNSIGNED-ORIGINAL-SIGNING-INPUT.apk` — 元の署名証明書で署名する前のaligned APK。**そのままインストール不可**。
- `SHA256SUMS.txt` — 両APKのSHA-256。
- `signing-validation.txt` — 検証専用署名のv2/v3検査ログ。
- `INSTALLATION-NOTICE.txt` — 使用時の注意事項。

**注意：テストAPKと既存のv1.157は同じパッケージ名 `jp.tapmarker.lite` ですが、署名証明書が異なります。上書き更新は拒否されます。動作中のv1.157を消してテスト版を試すと、設定・診断が失われる可能性があります。既存版を消すことは勧めません。** 利用する場合は初回インストール用の隔離した端末/環境でのみ検証してください。

ZIPはGitHub Actionsの保存期限により失効する可能性があります（生成時の保持期間14日）。永久URL・正式リリースではありません。

## 何が検証できているか

- Androidソースがコンパイル・パッケージ化可能、署名方式v2/v3成功。
- APKのパッケージ・バージョンは `jp.tapmarker.lite / 1.158 / 161`。
- 共通pt残高認識、報酬受領前のゼロ加点、農園うさぎの受領ゲート、共通残高の二重計上対策、持続する報酬ダイアログの確認時刻を含む **124件の独立JVM試験成功**、静的接続検査成功。
- Android15エミュレーターでアプリ起動が成功（ただしこの実行より前のソース）。最新のAPKでのAndroid17/Pixel10実機検証・仮想2画面・実際のpt回収は未実施。

## 正式アップデートに残る条件

署名済みv1.157を上書きするには、**正規の元の秘密鍵**が不可欠です。元の署名証明書は SHA-256 `7f42d636e4d3c251f475eb998fbe8e92b515ded8bce3ff4d30aa90b9a738cfb1`。v1.158の `sign-v158-release.py` は正規鍵、正しいAPKのSHA、証明書一致、署名v2/v3を満たさない限り公開しません。

正規鍵はリポジトリに保存せず、GitHub ActionsのRSA-OAEP暗号化ワンショット受け渡しに対応していますが、[前回の正式署名実行](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/actions/runs/41746151588) は暗号化データ未到着で停止しました。

**現時点で通常更新に使える署名済みバージョンは[既存のv1.157](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/releases/download/markertap-v1.157-rc1/MarkerTap-v1.157-integrated-signed.apk)です。** 最終リリースのREADMEリンクは実機検証後に更新します。
