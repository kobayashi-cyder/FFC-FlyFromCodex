const assert=require('node:assert/strict');
const V=require('../app/src/main/assets/capability-vocabulary-core.js');
const cases=[
 ['PythonでCSVを読むコードを書いて','code.generate'],
 ['このJavaScriptコードのバグを修正して','code.debug'],
 ['Kotlinのコードをリファクタして','code.refactor'],
 ['この処理のunit testを書いて','code.test'],
 ['PythonからKotlinへコードを変換して','code.convert'],
 ['Androidの音声入力を実装するKotlinコードを書いて','code.generate'],
 ['DOCXを作るPythonコードを書いて','code.generate'],
 ['1024x1024で猫の画像を生成して','image.generate'],
 ['この画像をもう少し明るく描き直して','image.refine'],
 ['俯瞰、自然光、写真風で画像を作って','image.generate'],
 ['マイクの権限状態を診断して','voice.status.native'],
 ['音声入力を開始して','voice.listen'],
 ['ゆっくり低い声に設定して','voice.configure'],
 ['これを英語で読み上げて','voice.speak.native'],
 ['Wordの仕様書をDOCXで作って','document.create.docx'],
 ['会議の議事録をDOCXでまとめて','document.create.docx'],
 ['提案書をMarkdownで作って','document.create.markdown'],
 ['HTML文書として手順書を作って','document.create.html']
];
for(const [q,tool] of cases){const x=V.classify(q);assert.equal(x.tool,tool,q+' => '+JSON.stringify(x))}
let x=V.classify('普通に雑談しよう');assert.equal(x.handled,false);
x=V.buildImageIR('16:9、接写、柔らかい光、暖色で猫の画像を生成');assert.equal(x.aspect,'16:9');assert.ok(x.composition.includes('close-up'));assert.ok(x.lighting.includes('soft'));
x=V.buildVoiceIR('英語でゆっくり低い声にして');assert.equal(x.language,'en-US');assert.equal(x.rate,.8);assert.equal(x.pitch,.85);
x=V.buildDocumentIR('初心者向けの正式な仕様書をDOCXで作る');assert.equal(x.type,'specification');assert.equal(x.audience,'beginner');assert.equal(x.tone,'formal');assert.ok(x.sections.includes('受入条件'));
console.log('specialist-capability-v2: PASS',V.lexiconStats());