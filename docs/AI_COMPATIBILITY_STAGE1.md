# BANC888 v7.12 — AI互換処理の第一段階

現行 v7.11 の会話・資料・開発ワークスペースへ、要求の分類、会話内の情報選択、適用可能な実行手順の選択を接続した。各段階は内蔵 BANC Figure 3g 縮約回路の出力を必要とし、由来・活動・選択経路を返す。通常の会話画面でもこの処理を通る。

内蔵回路は **6集約ノード・16接続**。全脳ではない。意味候補は従来の語彙・IR・獲得スキルの工学的アダプタが作る。回路単独で語義理解や汎用推論を学習したという実装ではない。回路の更新則と神経伝達物質の符号は従来モデルの仮定を継承する。

## 三つの処理

1. 適用可能な候補を用途別にまとめ、回路出力で用途を選ぶ。
2. 現在の会話の最大48行から、要求に関係する上位の行を最大6行選ぶ。同程度の関連性の候補は回路で選択する。一致がない場合は直近4行を使い、fallback と記録する。他会話の保存状態を検索しない。明示的な提供本文は変更せず渡す。
3. 選んだ用途に属する手順候補を回路で比較する。各実行ステップの既存回路ゲートと検証も維持する。

`compatibility.instruction`、`compatibility.information`、`compatibility.plan` に完全な処理記録を返す。会話の保存メタデータには、経路・選んだ行番号等の短い監査情報だけを保存する。評価による選択は実行中ステップの報酬帰属を消費しない。

## JSON入出力

WebView 内の JavaScript API:

```javascript
const result = await FFC_PROXY_AGENT.request({
  messages: [
    {role: 'user', content: '田中さんは開発を担当します。'},
    {role: 'assistant', content: '担当を受け取りました。'},
    {role: 'user', content: '本文を要約して'}
  ],
  source: '会議は10月3日です。田中さんは開発を担当します。',
  threadCode: 'A'
});
console.log(result.choices[0]?.message.content, result.compatibility);
```

これは **ローカルJSON形式の部分互換**。外部アプリ用 HTTP サーバーや、特定プロバイダの API 全互換を提供するものではない。対応は user/assistant の文字列メッセージ1〜32件、各12,000文字、合計36,000文字、提供本文24,000文字。最後は空でない user 発話。system/tool メッセージ、画像入力、streaming、tools、tool_choice、response_format、temperature、max_tokens、model 指定は未対応として明示的に返す。提供本文の既存読解器は先頭12,000文字を扱う。

成功した場合だけ `choices[].message` を返す。失敗・ブロック・未対応を成功の文章に置き換えない。通常の画面・従来の機能・オンライン接続設定は維持する。

## 検証と限界

```sh
node tests/ai_compat_core.test.cjs
node tools/ai_compat_evaluation.cjs > ai-compat-evaluation.json
```

評価は手書きの20件のルーティング要求。実際の内蔵回路と代理人を使い、ツールの実行部分は決定的な fixture。用途の振り分け・3段階の記録・全接続切断時の停止を確認する。結果は指示理解の汎化精度、要約や生成物の品質、Android遅延、全プロセスRAM、電池使用量ではない。全切断で止まることは、ランダム回路に対する優位性も意味しない。

Android の `AICompatibilityIntegrationTest` は、実APKのJSON入力、本文要約、3段階の回路ログ、未対応streamingを検査する。既存の回帰検査も継続する。

## APK

versionCode 89 / versionName `7.12.0-compat`、asset schema 12、Live資産32件。元の署名鍵を復旧できないため、debug APK は `com.ffc.banc888.fly.compat` として既存版と並べてインストールできる。アプリ名は BANC888 Compat。既存版の会話は自動移行しない。必要な会話は既存のJSON保存機能から取り出せる。

開発ブランチ `banc888-apk-ai-compat-stage1` は `banc888-apk-connectome-mobile-v1` から分ける。main へ自動マージしない。
