# FFC — FlyFromCodex

FFC development repository. Android APK builds are isolated on dedicated build branches before merge to main.

## MarkerTap

現在のMarkerTap更新候補は **v1.141 / versionCode 144** です（2026-10-27）。

- [利用・復元・ビルドの入口](markertap/README.md)
- [v1.141の変更・更新手順・署名情報](markertap/v1.141-reward-window/README.md)
- [再開時の状態と次の確認事項](markertap/WORK_NEXT.md)
- [ローカル署名版の検証記録](markertap/v1.141-reward-window/validation-local.json)
- [PR #22](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/pull/22) / [コード改修時のCI成功記録](https://github.com/kobayashi-cyder/FFC-FlyFromCodex/actions/runs/40414483942)

作業ブランチは `markertap/reward-window-v1.141`、PR #22はドラフト・未マージです。
**署名済みAPKの検証記録はありますが、APK本体が会話添付またはGitHubのダウンロード可能な配布物として存在することは、現在確認できていません。** GitHub Actionsの成果物は未署名の検証用で、端末への更新インストールには使えません。既存の非公開署名鍵を使って再生成し、安全なRelease資産として公開する必要があります。

Pixel 10 / F-52Aでの実機入力、報酬加算、長時間の無人運転は未検証です。
