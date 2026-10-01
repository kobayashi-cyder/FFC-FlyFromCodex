(() => {
'use strict';
const V=window.FFCCapabilityVocabulary,IQ=window.FFCImageQuality,Agent=window.BANC888_FLY_AGENT;
if(!V||!IQ||!Agent||window.FFC_CAPABILITIES)return;
const st=Agent.state,EXP_KEY='FFC_CAPABILITY_EXPERIENCE_V2',LAST_KEY='FFC_CAPABILITY_LAST_V2';
const reg=(name,description,capability,sideEffect,handler)=>{st.capabilities.add(capability);st.tools.set(name,{name,description,capability,sideEffect,handler})};
const safeJson=s=>{try{return JSON.parse(String(s||''))}catch{return{ok:false,error:String(s||'')}}};
const load=(k,d)=>{try{return JSON.parse(localStorage.getItem(k)||'null')||d}catch{return d}};
const save=(k,v)=>{try{localStorage.setItem(k,JSON.stringify(v))}catch(e){}};
let EXP=load(EXP_KEY,[]),LAST=load(LAST_KEY,{});
const remember=(tool,ok,meta)=>{EXP.push({time:Date.now(),tool,ok:!!ok,meta:meta||{}});if(EXP.length>96)EXP=EXP.slice(-96);save(EXP_KEY,EXP)};
const expStats=tool=>{const a=EXP.filter(x=>!tool||x.tool===tool),ok=a.filter(x=>x.ok).length;return{count:a.length,success:ok,failure:a.length-ok,rate:a.length?ok/a.length:null,last:a.slice(-6)}};
const setLast=(thread,kind,value)=>{const k=(thread||'_global')+'|'+kind;LAST[k]=value;save(LAST_KEY,LAST)};
const getLast=(thread,kind)=>LAST[(thread||'_global')+'|'+kind]||LAST['_global|'+kind]||null;
const compose=goal=>{const r=Agent.execute('chat.compose',{goal:String(goal||'')});if(!r||!r.ok)throw new Error((r&&r.error)||'chat.compose failed');return String((r.value&&r.value.reply)||'')};
const clean=t=>String(t||'').replace(/^\s+|\s+$/g,'');
const codeExt=lang=>({python:'py',javascript:'js',typescript:'ts',kotlin:'kt',java:'java',cpp:'cpp',rust:'rs',html:'html',css:'css',sql:'sql'}[lang]||'txt');
const shareText=(text,filename,mime)=>window.AndroidFiles?safeJson(AndroidFiles.shareText(String(text||''),String(filename||'BANC888.txt'),String(mime||'text/plain'))):{ok:false,shared:false,reason:'native file bridge unavailable'};

function controlPrepare(ir){
 if(!ir||ir.quality!=='high')return null;
 try{
  const mode=ir.kind==='CodeIR'?'code':ir.kind==='ImageIR'?'image':ir.kind==='SpeechIR'?'audio':'text';
  const c=Agent.execute('f42.compile',{goal:JSON.stringify(ir).slice(0,1600),mode,n:12,fan:4,passes:2,budget:120000});
  if(!c||!c.ok)return{ok:false,error:(c&&c.error)||'compile'};
  let done=false,last=null;
  for(let i=0;i<4&&!done;i++){last=Agent.execute('f42.step',{maxOps:8192});done=!!(last&&last.ok&&last.value&&last.value.done)}
  return{ok:true,done,compile:c.value,last:last&&last.value};
 }catch(e){return{ok:false,error:String(e&&e.message||e)}}
}
function controlReward(ctrl,ok){
 if(!ctrl||!ctrl.ok||!ctrl.done)return null;
 try{const r=Agent.execute(ok?'f42.adapt':'f42.rewire',{});return r&&r.ok?r.value:{error:r&&r.error}}
 catch(e){return{error:String(e&&e.message||e)}}
}
function looksLikeCode(s,lang){
 s=String(s||'');if(s.length<60)return false;
 if(lang==='python')return /\b(def|import|from|class)\b/.test(s);
 if(lang==='javascript'||lang==='typescript')return /\b(function|const|let|class|async|=>)\b/.test(s);
 if(lang==='kotlin')return /\b(fun|class|val|var|override)\b/.test(s);
 if(lang==='java')return /\b(class|public|private|static|void)\b/.test(s);
 if(lang==='html')return /<html|<!doctype|<div|<body/i.test(s);
 if(lang==='css')return /[{][^}]+[}]/.test(s);
 if(lang==='sql')return /\b(select|create|insert|update|with)\b/i.test(s);
 return /[{}();=]|\b(def|class|function|import)\b/.test(s);
}
function balance(s,a,b){let n=0;for(const c of String(s||'')){if(c===a)n++;else if(c===b)n--;if(n<0)return false}return n===0}
function validateCode(code,ir){
 const issues=[];code=String(code||'');
 if(code.length<60)issues.push('too-short');
 if(!balance(code,'{','}'))issues.push('brace-balance');
 if(!balance(code,'(',')'))issues.push('paren-balance');
 if(ir.language==='javascript'&&issues.length===0&&!/\b(import|export)\b/.test(code)){try{new Function(code)}catch(e){issues.push('javascript-syntax:'+e.message)}}
 if(ir.language==='json'){try{JSON.parse(code)}catch(e){issues.push('json-syntax')}}
 for(const r of ir.recipes||[]){
  if(r==='csv'&&!/csv/i.test(code))issues.push('csv-requirement');
  if(r==='http'&&!/(fetch|http|request|urlopen|Http)/i.test(code))issues.push('http-requirement');
  if(r==='permission'&&!/(permission|RECORD_AUDIO|requestPermissions|ActivityCompat)/i.test(code))issues.push('permission-requirement');
 }
 return{pass:issues.length===0,issues,length:code.length};
}
function templateCode(ir,request){
 const note=String(request||'').replace(/[\r\n]+/g,' ').slice(0,180);
 if(ir.language==='python'){
  if((ir.recipes||[]).includes('csv'))return "from __future__ import annotations\nimport csv\nfrom pathlib import Path\n\n\ndef read_csv(path: str | Path) -> list[dict[str, str]]:\n    p = Path(path)\n    with p.open('r', encoding='utf-8-sig', newline='') as f:\n        return list(csv.DictReader(f))\n\n\ndef main() -> None:\n    rows = read_csv('input.csv')\n    print(f'{len(rows)} rows')\n\n\nif __name__ == '__main__':\n    main()\n";
  if((ir.recipes||[]).includes('json'))return "from __future__ import annotations\nimport json\nfrom pathlib import Path\n\n\ndef load_json(path: str | Path):\n    return json.loads(Path(path).read_text(encoding='utf-8'))\n\n\ndef save_json(path: str | Path, value) -> None:\n    Path(path).write_text(json.dumps(value, ensure_ascii=False, indent=2), encoding='utf-8')\n\n\nif __name__ == '__main__':\n    data = load_json('input.json')\n    print(data)\n";
  if((ir.recipes||[]).includes('http'))return "from urllib.request import Request, urlopen\nimport json\n\n\ndef get_json(url: str):\n    req = Request(url, headers={'User-Agent': 'BANC888/1.0'})\n    with urlopen(req, timeout=15) as r:\n        return json.loads(r.read().decode('utf-8'))\n\n\nif __name__ == '__main__':\n    print(get_json('https://example.com/api'))\n";
  return "def main() -> None:\n    # Request: "+note.replace(/#/g,'')+"\n    print('BANC888 generated scaffold')\n\n\nif __name__ == '__main__':\n    main()\n";
 }
 if(ir.language==='javascript'||ir.language==='typescript'){
  if((ir.recipes||[]).includes('http'))return "async function requestJson(url) {\n  const response = await fetch(url, {headers: {'Accept': 'application/json'}});\n  if (!response.ok) throw new Error('HTTP ' + response.status);\n  return response.json();\n}\n\nrequestJson('https://example.com/api').then(console.log).catch(console.error);\n";
  return "'use strict';\n\nfunction main() {\n  // Request: "+note.replace(/\*\//g,'')+"\n  console.log('BANC888 generated scaffold');\n}\n\nmain();\n";
 }
 if(ir.language==='html')return "<!doctype html>\n<html lang=\"ja\">\n<meta charset=\"utf-8\">\n<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">\n<title>BANC888 App</title>\n<body>\n  <main id=\"app\"><h1>BANC888</h1><button id=\"run\">実行</button><pre id=\"out\"></pre></main>\n  <script>document.getElementById('run').onclick=()=>document.getElementById('out').textContent='ready';</script>\n</body>\n</html>\n";
 if(ir.language==='kotlin'&&(ir.recipes||[]).includes('microphone'))return "import android.Manifest\nimport android.app.Activity\nimport android.content.pm.PackageManager\n\nclass MicPermission(private val activity: Activity) {\n    fun ensure(requestCode: Int = 888): Boolean {\n        if (activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) return true\n        activity.requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), requestCode)\n        return false\n    }\n}\n";
 if(ir.language==='sql')return "CREATE TABLE IF NOT EXISTS items (\n  id INTEGER PRIMARY KEY,\n  name TEXT NOT NULL,\n  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP\n);\n\nSELECT id, name, created_at FROM items ORDER BY id DESC;\n";
 return "// BANC888 generated scaffold\n// Request: "+note+"\n";
}
function generateCode(a,action){
 const ir=a.ir||V.buildCodeIR(a.prompt||''),ctrl=controlPrepare(ir),prior=getLast(a.threadCode,'code'),priorText=prior&&prior.text?prior.text:'';
 let draft='';const goal='CodeIR='+JSON.stringify(ir)+'\nThreadContext='+String(a.context||'').slice(-1800)+'\nPreviousArtifact='+priorText.slice(0,5000)+'\nRequest='+String(a.prompt||'')+'\nReturn a complete '+ir.language+' implementation. No meta commentary.';
 try{draft=clean(compose(goal))}catch(e){}
 if(!looksLikeCode(draft,ir.language))draft=templateCode(ir,a.prompt);
 let validation=validateCode(draft,ir);
 if(!validation.pass&&ir.quality==='high'){const fb=templateCode(ir,a.prompt),fv=validateCode(fb,ir);if(fv.issues.length<validation.issues.length){draft=fb;validation=fv}}
 const reward=controlReward(ctrl,validation.pass);let exported=null;if(ir.export)exported=shareText(draft,ir.filename||('BANC888_code.'+codeExt(ir.language)),'text/plain');
 const out={action,ir,text:draft,validation,control:ctrl,reward,exported};
 setLast(a.threadCode,'code',out);remember('code.'+action,validation.pass,{language:ir.language,issues:validation.issues,quality:ir.quality});return out;
}
function imagePrompt(ir){
 const p=[ir.subject];if(ir.style&&ir.style.length)p.push('style: '+ir.style.join(', '));if(ir.composition&&ir.composition.length)p.push('composition: '+ir.composition.join(', '));if(ir.lighting&&ir.lighting.length)p.push('lighting: '+ir.lighting.join(', '));if(ir.palette&&ir.palette.length)p.push('palette: '+ir.palette.join(', '));if(ir.aspect)p.push('aspect: '+ir.aspect);if(ir.size)p.push('size: '+ir.size);if(ir.quality==='high')p.push('coherent composition, clear subject hierarchy, refined detail');if(ir.negative&&ir.negative.length)p.push('avoid: '+ir.negative.join(', '));return p.filter(Boolean).join(' | ');
}
function generateImage(a,refine){
 const ir=a.ir||V.buildImageIR(a.prompt||''),last=getLast(a.threadCode,'image');
 if(refine&&last&&last.ir)ir.subject=last.ir.subject+'; refinement request: '+ir.request;
 const ctrl=controlPrepare(ir),base=imagePrompt(ir),all=[];

 function renderVariant(v){
   const fullPrompt=[base,v.prompt].filter(Boolean).join(' | ');
   let r;
   try{r=Agent.execute('o2.generate',{prompt:fullPrompt,seed:v.seed,mode:v.mode,detail:v.detail,blur:v.blur,atmosphere:v.atmosphere,layoutX:v.layoutX,layoutY:v.layoutY,scaleBias:v.scaleBias})}
   catch(e){r={ok:false,error:String(e&&e.message||e)}}
   return{id:v.id,parentId:v.parentId||null,generation:+v.generation||0,strategy:v.strategy,prompt:fullPrompt,seed:v.seed,mode:v.mode,detail:v.detail,blur:v.blur,atmosphere:v.atmosphere,layoutX:+v.layoutX||0,layoutY:+v.layoutY||0,scaleBias:+v.scaleBias||1,value:r&&r.ok?r.value:null,error:r&&r.ok?null:(r&&r.error)||'generation-failed'};
 }
 function renderBatch(list){for(const v of list)all.push(renderVariant(v))}
 function rerank(){return IQ.rank(all,ir)}

 const initial=IQ.expand(ir,ir.quality==='high'?12:9).map(v=>({...v,generation:0}));
 renderBatch(initial);
 let ranked=rerank();
 const generationTrace=[{generation:0,candidates:all.length,bestScore:ranked.selected?.quality?.score||0,passed:ranked.passed.length}];

 for(let generation=1;generation<=2;generation++){
   const children=IQ.evolve(ranked.evaluated,ir,generation,generation===1?8:6);
   if(!children.length)break;
   renderBatch(children);
   ranked=rerank();
   generationTrace.push({generation,candidates:all.length,bestScore:ranked.selected?.quality?.score||0,passed:ranked.passed.length});
 }

 if(!ranked.selected){
   const reasons=ranked.rejected.slice(0,6).map(c=>c.id+':'+[...(c.quality.hardIssues||[]),...(c.quality.issues||[])].join(',')).join(' / ');
   remember(refine?'image.refine':'image.generate',false,{quality:ir.quality,candidates:all.length,generations:generationTrace.length,reasons});
   controlReward(ctrl,false);
   throw new Error('画像候補が品質基準を通過しませんでした。'+(reasons?' '+reasons:''));
 }

 const best=ranked.selected;
 const finalRun=Agent.execute('o2.generate',{prompt:best.prompt,seed:best.seed,mode:best.mode,detail:best.detail,blur:best.blur,atmosphere:best.atmosphere,layoutX:best.layoutX,layoutY:best.layoutY,scaleBias:best.scaleBias});
 if(!finalRun||!finalRun.ok)throw new Error((finalRun&&finalRun.error)||'selected image render failed');
 const finalQuality=IQ.evaluate({...best,value:finalRun.value},ir);
 if(!finalQuality.pass)throw new Error('最終画像が再検証で品質基準を下回りました。');

 const firstBest=generationTrace[0]?.bestScore||0;
 const validation={
   pass:true,score:finalQuality.score,issues:finalQuality.issues,hardIssues:finalQuality.hardIssues,
   metrics:finalQuality.metrics,objects:finalRun.value?.objects||0,scene:finalRun.value?.scene||null,
   candidateCount:all.length,passedCount:ranked.passed.length,rejectedCount:ranked.rejected.length,
   generationCount:generationTrace.length,evolutionGain:finalQuality.score-firstBest,generationTrace
 };
 const candidatesPassed=ranked.passed.map(c=>({id:c.id,parentId:c.parentId||null,generation:c.generation||0,strategy:c.strategy,score:c.quality.score,pass:true,issues:c.quality.issues||[],seed:c.seed,mode:c.mode,layoutX:c.layoutX,layoutY:c.layoutY,scaleBias:c.scaleBias}));
 const candidatesRejected=ranked.rejected.map(c=>({id:c.id,parentId:c.parentId||null,generation:c.generation||0,strategy:c.strategy,score:c.quality.score,pass:false,issues:[...(c.quality.hardIssues||[]),...(c.quality.issues||[])],seed:c.seed,mode:c.mode,layoutX:c.layoutX,layoutY:c.layoutY,scaleBias:c.scaleBias}));
 const reward=controlReward(ctrl,true),out={
   ir,prompt:best.prompt,value:finalRun.value,validation,
   selection:{id:best.id,parentId:best.parentId||null,generation:best.generation||0,strategy:best.strategy,score:finalQuality.score},
   candidates:candidatesPassed,rejected:candidatesRejected,control:ctrl,reward
 };
 setLast(a.threadCode,'image',out);
 remember(refine?'image.refine':'image.generate',true,{objects:validation.objects,quality:ir.quality,score:validation.score,candidates:validation.candidateCount,passed:validation.passedCount,generations:validation.generationCount,evolutionGain:validation.evolutionGain});
 return out;
}
let VOICE_CFG=load('FFC_VOICE_CONFIG_V2',{language:'ja-JP',rate:1,pitch:1});
function voiceConfigure(a){const ir=a.ir||V.buildVoiceIR(a.prompt||'');VOICE_CFG={language:ir.language||VOICE_CFG.language,rate:ir.rate||VOICE_CFG.rate,pitch:ir.pitch||VOICE_CFG.pitch};save('FFC_VOICE_CONFIG_V2',VOICE_CFG);remember('voice.configure',true,VOICE_CFG);return{...VOICE_CFG,continuousRequested:!!ir.continuous}}
function voiceListen(a){const ir=a.ir||V.buildVoiceIR(a.prompt||''),lang=ir.language||VOICE_CFG.language;if(window.AndroidVoice){AndroidVoice.startListening(lang);remember('voice.listen',true,{language:lang});return{started:true,native:true,language:lang,logicalThread:a.threadCode||null}}if(Agent.listen)Agent.listen();return{started:true,native:false,language:lang}}
function voiceSpeak(a){const ir=a.ir||V.buildVoiceIR(a.prompt||'');let text=String(ir.text||a.text||'').trim();if(!text)try{text=compose('人間へ短く自然に発話する内容を作る。要求: '+String(a.prompt||''))}catch(e){text=String(a.prompt||'')}const cfg={language:ir.language||VOICE_CFG.language,rate:ir.rate||VOICE_CFG.rate,pitch:ir.pitch||VOICE_CFG.pitch};if(window.AndroidVoice){AndroidVoice.speak(text,cfg.language,cfg.rate,cfg.pitch);remember('voice.speak.native',true,cfg);return{spoken:true,native:true,text,...cfg}}if(Agent.speak)Agent.speak(text);return{spoken:true,native:false,text,...cfg}}
function docFallback(ir,context){const sections=(ir.sections||[]).map((s,i)=>'## '+(i+1)+'. '+s+'\n'+(i===0?'本資料は「'+ir.request+'」を目的として整理したものです。':i===1&&context?'関連文脈: '+String(context).slice(-900):'要件に沿って確認・記録してください。')).join('\n\n');return '# '+ir.title+'\n\n'+sections+'\n'}
function validateDoc(body,ir){const b=String(body||''),heads=(b.match(/^##?\s+/gm)||[]).length,issues=[];if(b.length<220)issues.push('too-short');if(heads<Math.min(3,(ir.sections||[]).length))issues.push('missing-sections');if(/\bTODO\b|未定です|十分な知識がない/.test(b))issues.push('placeholder');return{pass:issues.length===0,issues,length:b.length,headings:heads}}
function makeDoc(format,a){
 const ir=a.ir||V.buildDocumentIR(a.prompt||''),ctrl=controlPrepare(ir),prior=getLast(a.threadCode,'document');let body='';
 const goal='DocumentIR='+JSON.stringify(ir)+'\nThreadContext='+String(a.context||'').slice(-2200)+'\nPreviousDraft='+String(prior&&prior.body||'').slice(0,4000)+'\nCreate a finished Japanese document. Use headings beginning with # and ##. Include all required sections. No meta commentary.';
 try{body=clean(compose(goal))}catch(e){}
 if(!/^#\s+/m.test(body)||body.length<220)body=docFallback(ir,a.context);
 let validation=validateDoc(body,ir);if(!validation.pass&&ir.quality==='high'){body=docFallback(ir,a.context)+'\n## 検証メモ\n- 要求: '+ir.request+'\n- 受入条件: 各章の内容を確認し、未確定事項を明示する。\n';validation=validateDoc(body,ir)}
 const reward=controlReward(ctrl,validation.pass),title=ir.title||'BANC888資料';let exported=null,outText=body;
 if(format==='docx')exported=window.AndroidFiles?safeJson(AndroidFiles.createDocx(title,body,ir.filename||'BANC888_document.docx')):{ok:false,reason:'native file bridge unavailable'};
 else{const ext=format==='markdown'?'md':format==='html'?'html':'txt',mime=format==='markdown'?'text/markdown':format==='html'?'text/html':'text/plain';if(format==='html')outText='<!doctype html><html lang="ja"><meta charset="utf-8"><title>'+title.replace(/[<>&]/g,'')+'</title><body><pre style="white-space:pre-wrap;font-family:system-ui">'+body.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;')+'</pre></body></html>';exported=shareText(outText,ir.filename||('BANC888_document.'+ext),mime)}
 const out={format,title,body:outText,ir,validation,control:ctrl,reward,exported};setLast(a.threadCode,'document',out);remember('document.create.'+format,validation.pass,{type:ir.type,quality:ir.quality,issues:validation.issues});return out;
}

reg('code.generate','CodeIR plan -> generate -> static validate -> bounded repair/export','code.write','local',a=>generateCode(a,'generate'));
reg('code.revise','revise the last thread code artifact with CodeIR and validation','code.write','local',a=>generateCode(a,'revise'));
reg('code.debug','debug the last thread code artifact and regenerate a corrected candidate','code.write','local',a=>generateCode(a,'debug'));
reg('code.refactor','refactor the last thread code artifact preserving intent','code.write','local',a=>generateCode(a,'refactor'));
reg('code.test','generate a test-oriented code candidate from CodeIR','code.write','local',a=>generateCode(a,'test'));
reg('code.convert','convert or port the last thread code artifact according to CodeIR','code.write','local',a=>generateCode(a,'convert'));
reg('code.optimize','optimize the last thread code artifact under requested constraints','code.write','local',a=>generateCode(a,'optimize'));
reg('image.generate','ImageIR -> broad candidates -> quality gates -> evolutionary breeding -> rerank -> render best passing candidate','image.write','page',a=>generateImage(a,false));
reg('image.refine','refine prior ImageIR -> evolutionary candidate search -> quality gates -> render best passing candidate','image.write','page',a=>generateImage(a,true));
reg('voice.listen','SpeechIR -> Android native speech recognition','voice.input','native',voiceListen);
reg('voice.speak.native','SpeechIR -> Android native TTS with rate pitch language','human.output','native',voiceSpeak);
reg('voice.status.native','read Android native STT TTS permission state','voice.input','none',()=>window.AndroidVoice?safeJson(AndroidVoice.status()):{native:false,fallback:Agent.voiceStatus?Agent.voiceStatus():null});
reg('voice.configure','persist SpeechIR language rate pitch defaults','voice.input','local',voiceConfigure);
reg('document.create.docx','DocumentIR -> structured draft -> validation -> native OOXML DOCX export','document.write','native',a=>makeDoc('docx',a));
reg('document.create.markdown','DocumentIR -> structured Markdown draft -> validation export','document.write','native',a=>makeDoc('markdown',a));
reg('document.create.html','DocumentIR -> structured HTML draft -> validation export','document.write','native',a=>makeDoc('html',a));
reg('document.create.text','DocumentIR -> structured text draft -> validation export','document.write','native',a=>makeDoc('text',a));
reg('capability.experience.status','read bounded success failure experience for artifact tools','compute','none',a=>expStats(a&&a.tool));
reg('capability.lexicon.status','read specialist vocabulary pack counts','compute','none',()=>V.lexiconStats());

function summarize(tool,v){
 if(tool.indexOf('code.')===0)return'コード処理 '+tool+' 完了。validation='+(v.validation&&v.validation.pass?'PASS':'CHECK')+' / '+(v.ir&&v.ir.language||'auto')+(v.exported&&v.exported.ok?' / file shared':'');
 if(tool.indexOf('image.')===0)return'画像処理 '+tool+' 完了。候補 '+(v.validation?.candidateCount||0)+'件 → 合格 '+(v.validation?.passedCount||0)+'件 / 選択 '+(v.selection?.id||'—')+' / score '+(v.validation?.score??'—')+' / validation='+(v.validation&&v.validation.pass?'PASS':'CHECK');
 if(tool==='voice.listen')return'音声入力を開始しました。';if(tool==='voice.speak.native')return'音声出力しました。';if(tool==='voice.status.native')return'音声状態: '+JSON.stringify(v);if(tool==='voice.configure')return'音声設定を更新しました。';
 if(tool.indexOf('document.create.')===0)return String(v.format||'document').toUpperCase()+'資料を作成しました。validation='+(v.validation&&v.validation.pass?'PASS':'CHECK')+(v.exported&&v.exported.ok?' / Android共有を開きました':'');
 return'ツール処理完了。';
}
function present(tool,v,summary){let d=summary;if(tool.indexOf('code.')===0)d+='\n\n'+String(v.text||'').slice(0,14000);else if(tool.indexOf('document.create.')===0)d+='\n\n'+String(v.body||'').slice(0,9000);try{if(Agent.present)Agent.present({finalText:d,selected:{tool},observation:{tool,result:{ok:true,value:v}}})}catch(e){}}
function handle(text,ctx){ctx=ctx||{};const plan=V.classify(text);if(!plan.handled||plan.confidence<.58)return{handled:false,plan};const args={ir:plan.ir,prompt:String(text||''),context:String(ctx.context||''),threadCode:ctx.threadCode||null};const r=Agent.execute(plan.tool,args);if(!r||!r.ok){remember(plan.tool,false,{error:(r&&r.error)||'unknown'});return{handled:true,plan,tool:plan.tool,error:(r&&r.error)||'tool failed',finalText:plan.tool+' でエラー: '+((r&&r.error)||'unknown')}}const finalText=summarize(plan.tool,r.value);present(plan.tool,r.value,finalText);return{handled:true,plan,tool:plan.tool,value:r.value,finalText}}
function ui(){const host=document.getElementById('ffcThreadHub')||document.getElementById('flyAgentCard');if(!host||document.getElementById('ffcCapabilityBar'))return;const x=document.createElement('div');x.id='ffcCapabilityBar';x.style.cssText='display:flex;gap:6px;flex-wrap:wrap;margin:8px 0;font-size:12px;opacity:.9';const n=V.lexiconStats();x.innerHTML='<span>🧰 Specialist Tools v2</span><span>CODE '+n.code+'</span><span>IMAGE '+n.image+'</span><span>VOICE '+n.voice+'</span><span>DOC '+n.document+'</span><span>IR→VERIFY→REPAIR</span>';host.insertBefore(x,host.firstChild)}
window.FFC_CAPABILITIES={version:'2.0',vocabulary:V,handle,classify:V.classify,experience:expStats,last:(thread,kind)=>getLast(thread,kind),manifest:()=>[...st.tools.values()].filter(x=>/^(code|image|voice|document|capability)\./.test(x.name)).map(({handler,...x})=>x)};
if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',ui,{once:true});else setTimeout(ui,0);
})();