# BANC888 認知エンジン v1（実験）

## 定義

BANC888 にオフラインで使える、**有限状態の記号的推論・目標計画・行動結果の検証・失敗時再計画**を追加する。既存 BANC コネクトームは各実行アクションの実行ゲートとして残し、既存代理人の権限検査と共有リソースの排他を通す。

これは人工汎用知能（AGI）ではなく、自然言語から任意の世界モデルを自動獲得する機能でもない。現在の Figure 3g は6集約ノード/16接続であり、全神経細胞の生物学的再現ではない。未知の課題のモデルをどう構築するかは未解決。

## 操作

APK画面下部の「🧠 BANC認知実験 — 目標推論と複数手順の計画」を開く。JSONを入力し、まず「計画を探索」を押す。事前に指定した事実・行動・論理規則から最少コスト計画を探す。プレビューはツールを実行しない。

\`BANC888_FLY_AGENT\` が認識するツールに限り、ユーザー確認付きで「観測付き実行」できる。ただし操作系の副作用があるツールは追加の実行許可を与えない限り拒否する。標準UIからはその許可を付与しない。

WebView の API:

\`\`\`js
const task = {
  schema:1,
  facts:{draft:'none',review:'pending'},
  goal:{review:'accepted'},
  actions:[
    {id:'draft',tool:'document.create.text',requires:{draft:'none'},effects:{draft:'ready'},args:{goal:'草稿作成'}},
    {id:'review',tool:'content.understand',requires:{draft:'ready'},effects:{review:'accepted'},args:{source:'草稿本文'}}
  ]
};
FFC_PROXY_AGENT.cognitionPlan(task); // 計画のみ
await FFC_PROXY_AGENT.cognitionSolve(task,{execute:true,threadCode:'A'}); // 実ツール呼出
\`\`\`

この例は**計画探索用の概念例**。既存の document/content ツールが \`observedFacts\` を返す保証はない。その場合、実行しても成功扱いせず \`blocked/missing_observation\` を返す。適用可能なツールの \`observedFacts\` 契約を拡張して初めて、実世界での複合実行が可能になる。

## 考える順序

1. ユーザーが与えた事実を検証し、if/then 規則を固定点まで推論する。競合する結論は明示的に停止する。
2. 行動の前提条件・予測効果・コストから、最大1800状態・16ステップの範囲で最少コスト経路を探索する（既定は800状態・深さ10）。
3. 実行時は権限と副作用ポリシーで拒否可能にした後、コネクトーム出力を必要とする。
4. 実ツールの結果に \`observedFacts\` があれば予測と照合する。これらは**ツールから報告された事実**であり、独立した外部監査ではない。
5. 実測値のない成功メッセージを達成とせず停止する。観測した失敗は学習履歴に数え、同じ行動を一時除外して別案があれば再計画する。
6. 学習履歴には成功/失敗件数の要約のみ保存し、生の依頼本文・観測内容は永続化しない。

\`status:done\` は「ツールの reported observations を満たした」の意味であって、現実世界で独立確認されたことやAGIの完成を意味しない。

## 追加された検証

\`node tests/cognition_core.test.cjs\`

- 2段以上の計画、前提条件・規則の多段推論、矛盾検出、計算量制限
- 不正入力・プロトタイプ汚染・重複アクションの拒否
- 誤報/観測欠落時の停止、失敗時に代替案へ再計画
- 実APIからのコネクトーム判定、ツール権限、明示的副作用許可、学習要約の保存
- 経路を切断した場合の実行停止とプレビューの副作用防止

**未検証:** 大規模な自然言語推論・世界モデル自動生成・100%端末内のLLM推論・複雑な未知課題への汎化・実機電池消費。今後は既存のオンラインローカルモデル橋渡し、端末上のモデル実行、検証可能な状態取得ツールを接続し、holdout課題で品質を実測する。

## 既存資産への影響

- \`cognition-core.js\` : 純JSの記号的推論と計画器
- \`cognition-ui.js\` : JSON操作画面
- \`proxy-agent.js\` : policy / connectome / body arbiter 経由のオーケストレーション
- \`AppConfig.java\` : APKとLive配信資産への登録
- \`live-manifest.json\` : SHA-256で内容を固定

PRは既存のweight-space開発ブランチを起点として追加する。既存安定版に自動マージしない。
