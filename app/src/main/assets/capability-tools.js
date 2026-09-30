(() => {
'use strict';
const V=window.FFCCapabilityVocabulary,Agent=window.BANC888_FLY_AGENT;
if(!V||!Agent||window.FFC_CAPABILITIES)return;
const st=Agent.state;
const reg=(name,description,capability,sideEffect,handler)=>{
  st.capabilities.add(capability);
  st.tools.set(name,{name,description,capability,sideEffect,handler});
};
const safeJson=s=>{try{return JSON.parse(String(s||''))}catch{return{ok:false,error:String(s||'')}}};
const compose=goal=>{
  const r=Agent.execute('chat.compose',{goal:String(goal||'')});
  if(!r||!r.ok)throw new Error((r&&r.error)||'chat.compose failed');
  return String((r.value&&r.value.reply)||'');
};
const cleanCode=t=>{
  let s=String(t||'').trim();
  if(s.slice(0,3)==='\u0060\u0060\u0060'){const nl=s.indexOf('\n');if(nl>=0)s=s.slice(nl+1);if(s.slice(-3)==='\u0060\u0060\u0060')s=s.slice(0,-3)}
  return s.trim();
};
const codeExt=lang=>({python:'py',javascript:'js',typescript:'ts',kotlin:'kt',java:'java',cpp:'cpp',rust:'rs',html:'html',css:'css',sql:'sql'}[lang]||'txt');
const shareText=(text,filename,mime)=>{
  if(!window.AndroidFiles)return{ok:false,shared:false,reason:'native file bridge unavailable'};
  return safeJson(AndroidFiles.shareText(String(text||''),String(filename||'BANC888.txt'),String(mime||'text/plain')));
};
const docPrompt=(a,format)=>'資料作成ツール。形式='+format+'。タイトル='+(a.title||'資料')+'。要求: '+(a.prompt||'')+'\n読み手にそのまま渡せる完成原稿を日本語で作成してください。見出しと本文を明確にし、余計なメタ説明は省いてください。';
reg('code.generate','generate source code through the current fly conversation/code adapter','code.write','local',a=>{
  const lang=a.language||'auto';
  const text=cleanCode(compose('コード生成ツール。言語='+lang+'。要求: '+(a.prompt||'')+'\n実行可能性を優先し、コード本体を中心に返してください。'));
  let exported=null;
  if(a.export){const fn=a.filename||('BANC888_code.'+codeExt(lang));exported=shareText(text,fn,'text/plain')}
  return{language:lang,text,exported};
});
reg('code.revise','revise source code through the current fly conversation/code adapter','code.write','local',a=>{
  const lang=a.language||'auto';
  const text=cleanCode(compose('コード修正ツール。言語='+lang+'。要求: '+(a.prompt||'')+'\n既存意図を保ち、修正版コードを中心に返してください。'));
  let exported=null;if(a.export)exported=shareText(text,a.filename||('BANC888_code.'+codeExt(lang)),'text/plain');
  return{language:lang,text,exported};
});
reg('image.generate','route image creation to preserved O2/O2 Photo+ body','image.write','page',a=>{
  const r=Agent.execute('o2.generate',{prompt:String(a.prompt||'')});if(!r||!r.ok)throw new Error((r&&r.error)||'o2.generate failed');return r.value;
});
reg('voice.listen','start Android native speech recognition','voice.input','native',a=>{
  if(window.AndroidVoice){AndroidVoice.startListening(a.language||'ja-JP');return{started:true,native:true}}
  if(Agent.listen)Agent.listen();return{started:true,native:false};
});
reg('voice.speak.native','speak through Android TextToSpeech when available','human.output','native',a=>{
  let text=String(a.text||'').trim();if(!text)text=compose('次の依頼に短く自然に音声で答えてください: '+(a.prompt||''));
  if(window.AndroidVoice){AndroidVoice.speak(text,a.language||'ja-JP',1.0,1.0);return{spoken:true,native:true,text}}
  if(Agent.speak)Agent.speak(text);return{spoken:true,native:false,text};
});
reg('voice.status.native','read Android native voice bridge status','voice.input','none',()=>{
  if(window.AndroidVoice)return safeJson(AndroidVoice.status());
  return{native:false,fallback:Agent.voiceStatus?Agent.voiceStatus():null};
});
function makeDoc(format,a){
  const body=compose(docPrompt(a,format)),title=a.title||'BANC888資料';
  if(format==='docx'){
    const exported=window.AndroidFiles?safeJson(AndroidFiles.createDocx(title,body,a.filename||'BANC888_document.docx')):{ok:false,shared:false,reason:'native file bridge unavailable'};
    return{format,title,body,exported};
  }
  const ext=format==='markdown'?'md':format==='html'?'html':'txt';
  let out=body,mime='text/plain';
  if(format==='html'){out='<!doctype html><html lang="ja"><meta charset="utf-8"><title>'+title.replace(/[<>&]/g,'')+'</title><body><pre style="white-space:pre-wrap;font-family:system-ui">'+body.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;')+'</pre></body></html>';mime='text/html'}
  else if(format==='markdown')mime='text/markdown';
  const exported=shareText(out,a.filename||('BANC888_document.'+ext),mime);
  return{format,title,body:out,exported};
}
reg('document.create.docx','draft and export a real DOCX through Android native ZIP/OOXML bridge','document.write','native',a=>makeDoc('docx',a));
reg('document.create.markdown','draft and share a Markdown document','document.write','native',a=>makeDoc('markdown',a));
reg('document.create.html','draft and share an HTML document','document.write','native',a=>makeDoc('html',a));
reg('document.create.text','draft and share a text document','document.write','native',a=>makeDoc('text',a));
function summarize(tool,v){
  if(tool==='code.generate'||tool==='code.revise')return'コードを'+(tool==='code.revise'?'修正':'生成')+'しました（'+v.language+'）。'+(v.exported&&v.exported.ok?' ファイル共有を開きました。':'');
  if(tool==='image.generate')return'画像生成を実行しました。'+(v&&v.objects!=null?' objects='+v.objects:'');
  if(tool==='voice.listen')return'音声入力を開始しました。';
  if(tool==='voice.speak.native')return'音声で返答しました。';
  if(tool==='voice.status.native')return'音声状態: '+JSON.stringify(v);
  if(tool&&tool.indexOf('document.create.')===0)return String(v.format||'document').toUpperCase()+'資料を作成しました。'+(v.exported&&v.exported.ok?' Android共有を開きました。':'');
  return'ツールを実行しました。';
}
function presentToolResult(tool,v,summary){
  let detail=summary;
  if(tool==='code.generate'||tool==='code.revise')detail+='\n\n'+String(v.text||'').slice(0,12000);
  else if(tool&&tool.indexOf('document.create.')===0)detail+='\n\n'+String(v.body||'').slice(0,8000);
  try{if(Agent.present)Agent.present({finalText:detail,selected:{tool},observation:{tool,result:{ok:true,value:v}}})}catch(e){}
}
function handle(text,ctx){
  ctx=ctx||{};
  const plan=V.classify(text);
  if(!plan.handled||plan.confidence<.56)return{handled:false,plan};
  const args=Object.assign({},plan.args,{prompt:String(text||''),context:String(ctx.context||''),threadCode:ctx.threadCode||null});
  const r=Agent.execute(plan.tool,args);
  if(!r||!r.ok)return{handled:true,plan,tool:plan.tool,error:(r&&r.error)||'tool failed',finalText:plan.tool+' でエラー: '+((r&&r.error)||'unknown')};
  const finalText=summarize(plan.tool,r.value);
  presentToolResult(plan.tool,r.value,finalText);
  return{handled:true,plan,tool:plan.tool,value:r.value,finalText};
}
function ui(){
  const host=document.getElementById('ffcThreadHub')||document.getElementById('flyAgentCard');if(!host||document.getElementById('ffcCapabilityBar'))return;
  const x=document.createElement('div');x.id='ffcCapabilityBar';x.style.cssText='display:flex;gap:6px;flex-wrap:wrap;margin:8px 0;font-size:12px;opacity:.9';
  x.innerHTML='<span>🧰 Tools</span><span>CODE</span><span>IMAGE</span><span>VOICE I/O</span><span>DOCX</span><span>MD/HTML/TXT</span>';
  host.insertBefore(x,host.firstChild);
}
window.FFC_CAPABILITIES={vocabulary:V,handle,classify:V.classify,manifest:()=>[...st.tools.values()].filter(x=>/^(code|image|voice|document)\./.test(x.name)).map(({handler,...x})=>x)};
if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',ui,{once:true});else setTimeout(ui,0);
})();