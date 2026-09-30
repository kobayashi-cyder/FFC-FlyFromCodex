(function(root,factory){
  const api=factory();
  if(typeof module==='object'&&module.exports)module.exports=api;
  root.FFCCapabilityVocabulary=api;
})(typeof globalThis!=='undefined'?globalThis:this,function(){
'use strict';
const norm=s=>String(s||'').normalize('NFKC').toLowerCase().replace(/\s+/g,' ').trim();
const hit=(t,w)=>t.includes(norm(w));
const hits=(t,ws)=>ws.reduce((n,w)=>n+(hit(t,w)?1:0),0);
const uniq=a=>[...new Set(a.filter(Boolean))];

const PACK={
 code:{
  nouns:['コード','プログラム','実装','関数','クラス','モジュール','スクリプト','ソース','アルゴリズム','api','sdk','cli','ライブラリ','html','css','javascript','typescript','python','kotlin','java','c++','cpp','rust','sql','json','regex','正規表現','gradle','adb','powershell'],
  generate:['コード生成','実装して','書いて','作って','生成','新規','generate','implement','write','create'],
  revise:['修正','直して','編集','変更','改善','fix','edit','revise'],
  debug:['デバッグ','バグ','例外','エラー','落ちる','動かない','不具合','debug','bug','exception','crash'],
  refactor:['リファクタ','整理','分割','可読性','保守性','refactor','cleanup'],
  test:['テスト','検証コード','unit test','unittest','pytest','jest','test case'],
  convert:['変換','移植','書き換え','port','convert','rewrite'],
  optimize:['最適化','高速化','軽量化','メモリ削減','性能改善','optimize','performance']
 },
 image:{
  nouns:['画像','イラスト','絵','写真','図解','図','アイコン','サムネイル','背景','ポスター','image','picture','photo','render','canvas','png','jpg','jpeg'],
  generate:['画像生成','描いて','描画','レンダリング','作って','生成','generate','render','draw','create'],
  refine:['修正','描き直し','改善','高精細化','構図変更','明るく','暗く','refine','revise','variation']
 },
 voice:{
  nouns:['音声','声','マイク','読み上げ','発話','聞き取り','文字起こし','speech','voice','microphone','tts','stt','speechrecognizer','texttospeech'],
  listen:['聞いて','聞き取り','音声入力','録音','文字起こし','マイク開始','listen','stt','recognize'],
  speak:['読み上げ','話して','発話','音声出力','しゃべって','speak','tts'],
  status:['状態','権限','診断','使える','permission','status','diagnose'],
  configure:['ゆっくり','速く','早く','高い声','低い声','英語で','日本語で','速度','ピッチ','rate','pitch','configure']
 },
 document:{
  nouns:['資料','文書','ドキュメント','docx','word','ワード','レポート','報告書','仕様書','設計書','要件定義','議事録','提案書','手順書','マニュアル','チュートリアル','チェックリスト','台本','原稿','markdown','html文書','テキスト文書'],
  create:['資料作成','文書作成','書いて','まとめて','作って','作成','生成','draft','document','report','create'],
  revise:['修正','改訂','編集','追記','構成変更','revise','edit','update']
 },
 quality:{
  high:['高品質','丁寧','厳密','完全','細部','本番','production','実用','最良','高精度','レビューして','検証して','自己修正'],
  fast:['簡単','ざっくり','試作','最小','高速','quick','prototype','minimal']
 },
 constraints:{
  offline:['オフライン','ローカル','外部apiなし','ネット不要','offline','local only'],
  single:['単一ファイル','1ファイル','single file','standalone'],
  lowdep:['依存なし','追加依存なし','標準ライブラリ','no dependency','stdlib'],
  android:['android','apk','webview','speechrecognizer','texttospeech'],
  web:['web','ブラウザ','html','css','dom'],
  windows:['windows','powershell','adb'],
  secure:['安全','セキュリティ','権限','permission','sanitize','validation']
 }
};

const LANGS=[
 ['python',['python','py','パイソン']],
 ['javascript',['javascript',' js ','node.js','nodejs']],
 ['typescript',['typescript',' ts ']],
 ['kotlin',['kotlin','コトリン']],
 ['java',[' java ','javaで']],
 ['cpp',['c++','cpp']],
 ['rust',['rust']],
 ['html',['html']],
 ['css',['css']],
 ['sql',['sql']]
];
const FRAMEWORKS=[
 ['android',['android','apk','activity','service','intent','manifest','webview']],
 ['jetpack-compose',['jetpack compose','compose ui']],
 ['node',['node.js','nodejs','npm']],
 ['react',['react','jsx','tsx']],
 ['fastapi',['fastapi']],
 ['flask',['flask']],
 ['django',['django']],
 ['spring',['spring','spring boot']],
 ['powershell',['powershell','ps1']],
 ['adb',['adb','scrcpy']]
];

function detectCodeLanguage(t){for(const [id,ws] of LANGS)if(ws.some(w=>hit(' '+t+' ',w)))return id;return'auto'}
function detectFrameworks(t){return FRAMEWORKS.filter(([,ws])=>ws.some(w=>hit(t,w))).map(([id])=>id)}
function detectQuality(t){if(hits(t,PACK.quality.high))return'high';if(hits(t,PACK.quality.fast))return'fast';return'normal'}
function detectDocumentFormat(t){
 if(/docx|\bword\b|ワード/.test(t))return'docx';
 if(/markdown|\bmd\b/.test(t))return'markdown';
 if(/html文書|html形式/.test(t))return'html';
 if(/txt|テキスト文書/.test(t))return'text';
 return'docx';
}
function quoted(text){const m=String(text||'').match(/[「『"]([^」』"]{1,2000})[」』"]/);return m?m[1].trim():''}
function filenameHint(text,ext){const m=String(text||'').match(/(?:ファイル名|filename)\s*[:：=]\s*([^\s,、。]{1,100})/i);let n=m?m[1]:'';if(n&&!n.toLowerCase().endsWith('.'+ext))n+='.'+ext;return n}
function sizeHint(t){const m=t.match(/\b(\d{2,4})\s*[x×]\s*(\d{2,4})\b/);return m?m[1]+'x'+m[2]:null}
function aspectHint(t){
 const m=t.match(/\b(\d{1,2})\s*[:：]\s*(\d{1,2})\b/);if(m)return m[1]+':'+m[2];
 if(/縦長|ポートレート/.test(t))return'9:16';if(/横長|ワイド/.test(t))return'16:9';if(/正方形|スクエア/.test(t))return'1:1';return null;
}
function detectDocType(t){
 if(/仕様書|設計書|要件定義/.test(t))return'specification';
 if(/議事録|会議録/.test(t))return'minutes';
 if(/提案書|企画書/.test(t))return'proposal';
 if(/手順書|マニュアル/.test(t))return'manual';
 if(/チュートリアル|教材|教科書/.test(t))return'tutorial';
 if(/チェックリスト/.test(t))return'checklist';
 if(/報告書|レポート/.test(t))return'report';
 return'general';
}
function detectAudience(t){
 if(/初心者|初学者|子ども|子供/.test(t))return'beginner';
 if(/開発者|エンジニア|技術者/.test(t))return'technical';
 if(/顧客|お客様|クライアント/.test(t))return'customer';
 if(/上司|経営|役員/.test(t))return'executive';
 return'general';
}
function detectTone(t){if(/正式|フォーマル|丁寧/.test(t))return'formal';if(/やさしく|分かりやすく|平易/.test(t))return'plain';if(/技術的|専門的/.test(t))return'technical';return'balanced'}
function detectConstraints(t){return uniq(Object.entries(PACK.constraints).filter(([,ws])=>hits(t,ws)).map(([k])=>k))}
function codeAction(t){
 if(hits(t,PACK.code.debug))return'debug';
 if(hits(t,PACK.code.refactor))return'refactor';
 if(hits(t,PACK.code.test))return'test';
 if(hits(t,PACK.code.convert))return'convert';
 if(hits(t,PACK.code.optimize))return'optimize';
 if(hits(t,PACK.code.revise))return'revise';
 return'generate';
}
function codeRecipes(t){return uniq([
 /csv/.test(t)?'csv':null,/json/.test(t)?'json':null,/http|rest|api.*(?:呼|request|通信)|fetch/.test(t)?'http':null,
 /cli|コマンドライン|argparse/.test(t)?'cli':null,/ファイル|file/.test(t)?'file':null,/マイク|speechrecognizer|record_audio/.test(t)?'microphone':null,
 /webview/.test(t)?'webview':null,/権限|permission/.test(t)?'permission':null,/thread|スレッド|並列|async|非同期/.test(t)?'concurrency':null,
 /localstorage|indexeddb|保存/.test(t)?'storage':null
 ])}
function buildCodeIR(text){
 const t=norm(text),language=detectCodeLanguage(t),action=codeAction(t),frameworks=detectFrameworks(t),quality=detectQuality(t);
 const ext={python:'py',javascript:'js',typescript:'ts',kotlin:'kt',java:'java',cpp:'cpp',rust:'rs',html:'html',css:'css',sql:'sql'}[language]||'txt';
 return{kind:'CodeIR',action,language,frameworks,recipes:codeRecipes(t),constraints:detectConstraints(t),quality,export:/ファイル|保存|共有|download|export/.test(t),filename:filenameHint(text,ext),request:String(text||'').trim(),validation:['syntax','requirements','edge-cases']};
}
function buildImageIR(text){
 const t=norm(text);const lighting=uniq([
 /自然光|daylight/.test(t)?'daylight':null,/逆光|backlight/.test(t)?'backlight':null,/柔らかい光|soft light/.test(t)?'soft':null,
 /ドラマチック|dramatic/.test(t)?'dramatic':null,/夜|night/.test(t)?'night':null,/ネオン|neon/.test(t)?'neon':null
 ]);
 const composition=uniq([
 /中央|center/.test(t)?'centered':null,/接写|クローズアップ|close.?up/.test(t)?'close-up':null,/俯瞰|top.?down/.test(t)?'top-down':null,
 /広角|wide/.test(t)?'wide':null,/アイソメ|isometric/.test(t)?'isometric':null
 ]);
 const style=uniq([
 /写真|photo|photoreal/.test(t)?'photo':null,/イラスト|illustration/.test(t)?'illustration':null,/図解|diagram/.test(t)?'diagram':null,
 /アニメ|anime/.test(t)?'anime':null,/ミニマル|minimal/.test(t)?'minimal':null
 ]);
 const palette=uniq([
 /暖色|warm/.test(t)?'warm':null,/寒色|cool/.test(t)?'cool':null,/モノクロ|monochrome/.test(t)?'monochrome':null,/鮮やか|vivid/.test(t)?'vivid':null
 ]);
 return{kind:'ImageIR',action:hits(t,PACK.image.refine)?'refine':'generate',request:String(text||'').trim(),subject:quoted(text)||String(text||'').trim(),size:sizeHint(t),aspect:aspectHint(t),composition,lighting,style,palette,quality:detectQuality(t),negative:uniq([/文字なし|no text/.test(t)?'no-text':null,/透かしなし|no watermark/.test(t)?'no-watermark':null]),validation:['scene-nonempty','dimensions','request-coverage']};
}
function buildVoiceIR(text){
 const t=norm(text);let operation='speak';
 if(hits(t,PACK.voice.status)&&/マイク|音声|voice|speech/.test(t))operation='status';
 else if(hits(t,PACK.voice.listen))operation='listen';
 else if(hits(t,PACK.voice.configure)&&!/読み上げ|話して|speak/.test(t))operation='configure';
 const language=/英語|english|en-us|en-gb/.test(t)?'en-US':'ja-JP';
 let rate=1,pitch=1;if(/ゆっくり|遅く|slow/.test(t))rate=.8;if(/速く|早く|fast/.test(t))rate=1.2;if(/高い声|高め|high pitch/.test(t))pitch=1.15;if(/低い声|低め|low pitch/.test(t))pitch=.85;
 return{kind:'SpeechIR',operation,language,rate,pitch,continuous:/連続|常時|continuous/.test(t),text:quoted(text),request:String(text||'').trim(),quality:detectQuality(t),validation:['permission','engine-ready']};
}
function defaultSections(type){
 const m={
  specification:['目的','前提・制約','要件','アーキテクチャ','インターフェース','エラー処理','テスト','受入条件'],
  minutes:['日時・参加者','議題','決定事項','未決事項','アクション項目'],
  proposal:['背景','目的','提案内容','期待効果','リスクと対策','実施手順','評価指標'],
  manual:['概要','前提条件','手順','確認方法','トラブルシューティング'],
  tutorial:['学習目標','前提知識','解説','例','演習','解答・確認'],
  checklist:['目的','チェック項目','完了条件'],
  report:['要約','背景','観測・結果','分析','結論','次の行動'],
  general:['概要','目的','本文','確認事項','まとめ']
 };return m[type]||m.general;
}
function buildDocumentIR(text){
 const t=norm(text),format=detectDocumentFormat(t),type=detectDocType(t);
 const title=quoted(text)||({specification:'仕様書',minutes:'議事録',proposal:'提案書',manual:'手順書',tutorial:'教材',checklist:'チェックリスト',report:'報告書'}[type]||'BANC888資料');
 const ext=format==='markdown'?'md':format==='text'?'txt':format;
 return{kind:'DocumentIR',action:hits(t,PACK.document.revise)?'revise':'create',format,type,title,audience:detectAudience(t),tone:detectTone(t),sections:defaultSections(type),quality:detectQuality(t),constraints:detectConstraints(t),filename:filenameHint(text,ext),request:String(text||'').trim(),validation:['structure','completeness','readability','format']};
}
function classify(text){
 const raw=String(text||''),t=norm(raw),scores={code:0,image:0,voice:0,document:0};
 scores.code=hits(t,PACK.code.nouns)*1.45+hits(t,[...PACK.code.generate,...PACK.code.revise,...PACK.code.debug,...PACK.code.refactor,...PACK.code.test,...PACK.code.convert,...PACK.code.optimize])*.55;
 scores.image=hits(t,PACK.image.nouns)*1.55+hits(t,[...PACK.image.generate,...PACK.image.refine])*.65;
 scores.voice=hits(t,PACK.voice.nouns)*1.6+hits(t,[...PACK.voice.listen,...PACK.voice.speak,...PACK.voice.status,...PACK.voice.configure])*.55;
 scores.document=hits(t,PACK.document.nouns)*1.65+hits(t,[...PACK.document.create,...PACK.document.revise])*.6;
 if(/docx|\bword\b|仕様書|設計書|議事録|提案書|報告書|手順書/.test(t))scores.document+=2;
 if(/画像生成|絵を描|イラスト.*(?:作|生成)|image.*generate/.test(t))scores.image+=1.5;
 if(/マイク.*(?:状態|権限|診断)|音声入力|読み上げ|文字起こし/.test(t))scores.voice+=1.5;
 if(/コード生成|コード.*(?:書|作|修正)|実装して|デバッグ|リファクタ/.test(t))scores.code+=1.8;
 if(/(?:画像生成|音声入力|docx).*コード|コード.*(?:画像生成|音声入力|docx)|python.*docx|kotlin.*(?:マイク|音声)/.test(t))scores.code+=3;
 const sorted=Object.entries(scores).sort((a,b)=>b[1]-a[1]),domain=sorted[0][0],score=sorted[0][1],second=sorted[1][1];
 let ir=null,tool=null,action='none';
 if(domain==='code'&&score>=2.1){ir=buildCodeIR(raw);action=ir.action;tool={generate:'code.generate',revise:'code.revise',debug:'code.debug',refactor:'code.refactor',test:'code.test',convert:'code.convert',optimize:'code.optimize'}[action]||'code.generate'}
 else if(domain==='image'&&score>=2.0){ir=buildImageIR(raw);action=ir.action;tool=action==='refine'?'image.refine':'image.generate'}
 else if(domain==='voice'&&score>=1.9){ir=buildVoiceIR(raw);action=ir.operation;tool={listen:'voice.listen',speak:'voice.speak.native',status:'voice.status.native',configure:'voice.configure'}[action]}
 else if(domain==='document'&&score>=2.0){ir=buildDocumentIR(raw);action=ir.action;tool='document.create.'+ir.format}
 const confidence=tool?Math.max(.56,Math.min(1,.5+score*.07+Math.max(0,score-second)*.07)):Math.min(.5,score*.1);
 return{raw,normalized:t,domain,action,tool,args:ir?{ir}:{prompt:raw},ir,confidence,scores,handled:!!tool,packVersion:'2.0'};
}
function lexiconStats(){return{code:Object.values(PACK.code).flat().length,image:Object.values(PACK.image).flat().length,voice:Object.values(PACK.voice).flat().length,document:Object.values(PACK.document).flat().length,quality:Object.values(PACK.quality).flat().length,constraints:Object.values(PACK.constraints).flat().length}}
return{PACK,classify,buildCodeIR,buildImageIR,buildVoiceIR,buildDocumentIR,detectCodeLanguage,detectDocumentFormat,lexiconStats,quoted,filenameHint};
});