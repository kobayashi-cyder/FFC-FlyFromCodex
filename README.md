# FFC — FlyFromCodex

FFC本体のAndroidネイティブ可視化・解析アプリです。

## 現在の実装
- ネイティブAndroid UI（WebViewラッパーではない）
- ニューロン検索
- Cell Details
- 1-hop直接接続
- Exact 2〜6-hop
- 長さkのpath集約
- synapse count / neurotransmitter / effect confidence
- ネイティブCanvas接続グラフ
- nodes.csv / edges.csv インポート
- neurite length / glial coverage 派生指標の表示枠
- BANC/Codex/CAVE/Influence用語の学習画面

## データについて
APK同梱データはUI・アルゴリズム確認用の小さなデモです。BANC実ニューロンIDではありません。
実BANC v888由来データは端末向けに変換したCSV/SQLiteへ差し替える設計です。

### nodes.csv
```
id,cell_type,super_class,body_part,nt,neurite_um,glia_coverage_pct
```

### edges.csv
```
pre_id,post_id,synapse_count,nt,neuropil,effect,effect_confidence
```

## Android
- minSdk 26
- targetSdk 35
- Java 17 build
- package: `jp.ffc.flyfromcodex`

GitHub Actionsで `app-debug.apk` を生成します。
