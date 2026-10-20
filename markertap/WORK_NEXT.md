# 再開時の状態

最新の実装コミット: e52b47c93af2fd6ed91e114896d52b4c9b7b82c8
v1.119の初期版は回帰・署名検証済みでreleases/に保存済み。最新版code/では、録画比較で見つかったタイル全体の過剰コピーをPointPixelsの要求画素だけの直接取得へ改善しています。

最新版のビルド・core回帰・コピー量上限試験を実行中です。
https://github.com/kobayashi-cyder/FFC-FlyFromCodex/actions/runs/39495111580
署名用参照はこの実行へ暗号化して送付済みです。
build-channel/result.jsonのrun_idが39495111580でcompleteになるまで、旧VALIDATION-v1.119.jsonを最新コードの検証結果と混同しないでください。

復元はrestore_source.py --extract-to。ROI_MULTIPLEX_SPEC.mdにタイル過剰コピーの対策と試験範囲を記録しています。
署名鍵の平文や一時URLはリポジトリへ保存しません。実機のImageReader・GPU・入力・多重化数は未検証です。
