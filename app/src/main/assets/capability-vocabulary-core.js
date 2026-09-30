(function(root,factory){
  const api=factory();
  if(typeof module==='object'&&module.exports)module.exports=api;
  root.FFCCapabilityVocabulary=api;
})(typeof globalThis!=='undefined'?globalThis:this,function(){
  'use strict';
  const norm=s=>String(s||'').normalize('NFKC').toLowerCase().replace(/\s+/g,' ').trim();
  const has=(t,w)=>t.includes(norm(w));
  const hits=(t,ws)=>ws.reduce((n,w)=>n+(has(t,w)?1:0),0);
  const CODE_N=['コード','プログラム','実装','関数','クラス','スクリプト','html','css','javascript','typescript','python','kotlin','java','c++','cpp','rust','json','sql','regex','正規表現','api'];
  const IMAGE_N=['画像','イラスト','絵','写真','図解','図','アイコン','サムネ','image','picture','photo','render','canvas','png','jpg'];
  const VOICE_N=['音声','声','マイク','読み上げ','発話','聞き取り','聞いて','話して','speech','voice','microphone','tts','stt'];
  const DOC_N=['資料','文書','ドキュメント','docx','word','レポート','報告書','仕様書','議事録','提案書','手順書','マニュアル','台本','原稿','markdown','md','html文書','テキスト文書'];
  const CREATE=['生成','作って','作成','書いて','作る','つくって','新規','generate','create','write','draft','compose'];
  const REVISE=['修正','直して','変更','編集','改善','リファクタ','デバッグ','バグ修正','fix','edit','revise','refactor','debug'];
  function detectCodeLanguage(t){
    const map=[['python',['python','py']],['javascript',['javascript','js']],['typescript',['typescript','ts']],['kotlin',['kotlin']],['java',[' java','javaで']],['cpp',['c++','cpp']],['rust',['rust']],['html',['html']],['css',['css']],['sql',['sql']]];
    for(const [id,ws] of map)if(ws.some(w=>has(' '+t,w)))return id;
    return 'auto';
  }
  function detectDocumentFormat(t){
    if(/docx|\bword\b|ワード/.test(t))return'docx';
    if(/markdown|\bmd\b/.test(t))return'markdown';
    if(/html/.test(t))return'html';
    if(/txt|テキスト/.test(t))return'text';
    return'docx';
  }
  function quoted(text){
    const s=String(text||'');
    const m=s.match(/[「『"]([^」』"]{1,1200})[」』"]/);
    return m?m[1].trim():'';
  }
  function filenameHint(text,ext){
    const s=String(text||'');
    const m=s.match(/(?:ファイル名|filename)\s*[:：=]\s*([^\s,、。]{1,80})/i);
    let n=m?m[1]:'';
    if(n&&!n.toLowerCase().endsWith('.'+ext))n+='.'+ext;
    return n;
  }
  function classify(text){
    const raw=String(text||''),t=norm(raw);
    const scores={code:0,image:0,voice:0,document:0};
    const actionCreate=hits(t,CREATE),actionRevise=hits(t,REVISE);
    scores.code=hits(t,CODE_N)*1.65+Math.min(2,actionCreate+actionRevise)*.85;
    scores.image=hits(t,IMAGE_N)*1.7+Math.min(2,actionCreate)*.9;
    scores.voice=hits(t,VOICE_N)*1.7;
    scores.document=hits(t,DOC_N)*1.7+Math.min(2,actionCreate+actionRevise)*.8;
    if(/docx|word|仕様書|議事録|提案書|報告書/.test(t))scores.document+=1.5;
    if(/画像生成|絵を描|イラスト.*(?:作|生成)|image.*generate/.test(t))scores.image+=1.4;
    if(/コード生成|コード.*(?:書|作|実装)|実装して|プログラム.*(?:書|作)/.test(t))scores.code+=1.4;
    if(/マイク.*(?:状態|権限|診断)|音声.*(?:状態|権限)|voice.*status/.test(t))scores.voice+=1.7;
    let domain=Object.entries(scores).sort((a,b)=>b[1]-a[1])[0][0],score=scores[domain];
    let tool=null,action='none',args={prompt:raw};
    if(domain==='code'&&score>=2.0){
      action=actionRevise>actionCreate?'revise':'generate';
      tool=action==='revise'?'code.revise':'code.generate';
      args.language=detectCodeLanguage(t);
      args.export=/ファイル|保存|共有|download|export/.test(t);
      args.filename=filenameHint(raw,args.language==='python'?'py':args.language==='javascript'?'js':args.language==='typescript'?'ts':args.language==='kotlin'?'kt':args.language==='java'?'java':args.language==='cpp'?'cpp':args.language==='rust'?'rs':args.language==='html'?'html':args.language==='css'?'css':args.language==='sql'?'sql':'txt');
    }else if(domain==='image'&&score>=2.0){
      action='generate';tool='image.generate';args.size=(t.match(/\b(\d{2,4})\s*[x×]\s*(\d{2,4})\b/)||[]).slice(1,3).join('x')||null;
    }else if(domain==='voice'&&score>=1.7){
      if(/マイク.*(?:状態|権限|診断)|音声.*(?:状態|権限)|voice.*status/.test(t)){action='status';tool='voice.status.native'}
      else if(/聞いて|聞き取り|音声入力|マイク.*(?:開始|オン)|listen|stt/.test(t)){action='listen';tool='voice.listen'}
      else {action='speak';tool='voice.speak.native';args.text=quoted(raw)}
    }else if(domain==='document'&&score>=2.0){
      action=actionRevise>actionCreate?'revise':'create';
      const format=detectDocumentFormat(t);args.format=format;
      tool='document.create.'+format;
      args.title=quoted(raw)||(/仕様書/.test(t)?'仕様書':/議事録/.test(t)?'議事録':/提案書/.test(t)?'提案書':/報告書|レポート/.test(t)?'報告書':'BANC888資料');
      args.filename=filenameHint(raw,format==='markdown'?'md':format==='text'?'txt':format);
    }
    const second=Object.values(scores).sort((a,b)=>b-a)[1]||0;
    const confidence=tool?Math.max(.45,Math.min(1,.48+score*.09+Math.max(0,score-second)*.06)):Math.min(.44,score*.1);
    return{raw,normalized:t,domain,action,tool,args,confidence,scores,handled:!!tool};
  }
  return{classify,detectCodeLanguage,detectDocumentFormat,quoted,filenameHint};
});