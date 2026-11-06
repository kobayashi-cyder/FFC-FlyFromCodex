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
