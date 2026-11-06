## ゼロから学習するWSP重みネットワーク（追加開発）

数値課題に対し、固定手順の選択ではなく**教師例から入力→隠れ→出力の重み行列を更新**します。推論時は積和・Tanh活性化のみを通し、既存の学習済みモデルを使用しません。

`/net demo` で二次関数の9例から重みを学習し、訓練に含まれない4入力で評価・採用します。`/net run [1.3]` で実際の重みから推論、`/net status` で次元と重み数を確認します。`/net undo` で直前の重みへ戻し、再読み込み後も保存したモデルを使用できます。`/net train {JSON}` で新しい数値入出力例を学習できます（`inputs`, `outputs`, `train`, `holdout`、任意の `hidden`, `epochs`, `seed`, `tolerance`）。

訓練6〜64例・未使用評価3〜32例、入力次元1〜8、出力次元1〜4、隠れ4〜24。未使用評価の入力を訓練に混ぜず、全部の検証に合格しないモデルは不採用にします。

**限界：** 現在は数値関数の学習です。自由な日本語文章や高品質画像・音声・動画を生成するWSPモデルは未完成です。従来のメディア工房は手続き的生成のままで、学習済みモデルと称しません。

---

# Sol-Zero v0.13 — WSP・チャット・コード生成・メディア統合（実験版）

**品質強化パッチ：** コード生成の追加条件を勝手に無視しない厳密なコマンド判定、512文字の要求長上限、バージョン表記整合、タッチ操作領域・キーボードフォーカス・動作軽減設定、JavaScript追加回帰試験、4bit重みストアのPython試験、単一HTML再生成一致のCI検査を追加しました。品質判定と未達項目は [QUALITY_GATES.md](QUALITY_GATES.md) に記載しています。

v0.12のチャット・独立評価つきWSP帰納・画像/音声/動画工房を維持し、**Python/JavaScriptの実行可能な8種類の固定アルゴリズムコード生成**を追加します。ブラウザ内でコードを実行せず、コピー用テキストを生成します。

**試す：** `/code python fibonacci`、`/code javascript binary_search`、`/code help`。利用可能：階乗、フィボナッチ、昇順二分探索、重複除去、回文、平均、CSVフィールド引用、範囲制限。`/image 海`、`/audio 波`、`/video 星空`（GIF）、`/webm 街`（ブラウザ対応時の音声付きWebM）は継続利用可能です。映像・画像は学習済みモデルではなく手続き的生成です。

**ビルド・検証：** `node --test tests/*.test.mjs`、`python scripts/build_standalone.py`。コミット済みソースを `SolZero_Chat_Offline.html` に再生成できます。`/code` のPython/JSコードは実行テストを用意しています。GitHub Actions `sol-zero-ci.yml` ではNodeテスト、Python構文検査、単体HTMLの再構築と配布アーティファクト化を実施します。PythonのPyTorch学習系テストは別工程であり、CIの軽量チェックには含めません。

**限界：** 自由なコード生成、総合的な自然言語推論、実写・高品質画像・動画生成、一般用途の学習知能は未実装です。ゼロからのモデル開発と、WSP数値回路・手続き的生成を区別します。端末ブラウザ互換と現物動作は別途検証が必要です。

---

# Sol-Zero Chat v0.12 — オフライン・マルチメディア統合版

学習済みの外部モデル（Qwen、Llama、Gemma、GPT等）・蒸留・外部推論API・CDNは**使用しません**。v0.11のチャット/WSPに、数学的な手続きによる画像・波形・動画の生成を追加しました。高品質な生成AIモデルはまだ存在しません。

## はじめかた

`SolZero_Chat_Offline.html` をスマートフォン／PCのブラウザで開いてください（インターネット不要）。上の **「🎨 メディア工房」** から、文章の説明・乱数シード・画風・時間・音を指定します。

1. **画像 PNG**：Canvasの描画計算から静止画を作り、端末へ保存。
2. **音声 WAV**：アンビエント・波音・風音・自作電子音・簡易ロボット声を16kHz PCM波形として生成。
3. **動画 GIF**：フレームを作りLZWでGIFに変換。音声は入りません。
4. **音声付き動画 WebM**：Canvas＋WebAudio＋MediaRecorderを使い、映像・音声の2トラックをWebMへ出力（ブラウザ対応時のみ）。
5. **作品ZIP**：現在のすべての画像・音声・動画と再編集用の `project.json` を一括ダウンロード。**「作品ZIPを復元」** で再読込可能。
6. **端末の素材を取り込む**：PNG/JPEG/GIF/WAV/MP3/MP4/WebMを作品に含められます。取り込んだ画像の内容をAIが理解するわけではありません。
7. **メディアWSP**：4×3の直接プログラムした重みを編集すると、描画密度・運動速度・音程が変化します。

チャットから `/image 青い海`、`/audio 波音`、`/video 星空`、`/webm 街の夜景` と入力しても生成できます。`/video` はGIFです。音声入りWebMを希望する場合は `/webm` を使用してください。

### プロジェクトの保存

対応ブラウザでは生成素材をIndexedDB、制作条件・WSPパラメータをlocalStorageへ保存します。ただし `file://` のストレージ制約やプライベートモード等では保存できないことがあります。**作品ZIPが確実な持ち運び・バックアップの手段**です。チャット履歴のJSONバックアップとメディア作品ZIPは別に保存してください。素材は外部に送信しません。

### ソース構成

| ファイル | 用途 |
|---|---|
| `chat_web/media_studio.js` | 描画、波形、GIF、ZIP、WSP重み制御 |
| `chat_web/media_ui.js` | ブラウザ画面、IndexedDB、WebM録画、素材管理 |
| `chat_web/app.js` | チャットUI・メディアコマンド・旧版移行 |
| `scripts/build_standalone.py` | 外部依存なしの1ファイルHTMLを再生成 |
| `tools/webm_to_mp4.py` | PCでWebM→MP4変換（FFmpeg要） |
| `tests/media_studio.test.mjs` | PCM/GIF/ZIP/WSP等のテスト |
| `samples/` | ブラウザで実生成した静止画・音声・GIF・WebM/MP4 |

### 検証コマンド

```bash
node --test tests/*.test.mjs
python -m pytest -q tests/test_solzero.py tests/test_weight_store.py
python scripts/build_standalone.py
python tools/webm_to_mp4.py samples/movie.webm samples/movie.mp4
```

端末によってWebM録画と自動保存の挙動が異なります。ブラウザ実操作検証にはPlaywright/Chromiumを用意してください。学習済みモデルは不要です。

### 境界と次の研究課題

2026-11-06現在、自由な自然言語理解や、任意プロンプトを解釈する拡散画像モデル・自然なTTS・実写動画生成は未実装です。現在の `MediaWSP` は**生成パラメータの制御回路**で、生成知能そのものではありません。高品質な学習系に進むには、許諾確認済みデータ・ゼロからの訓練・独立評価・推論ランタイム・低ビット量子化が必要です。5GBは最終配布物の保存上限であり、訓練時RAM/VRAMを制限していません。
