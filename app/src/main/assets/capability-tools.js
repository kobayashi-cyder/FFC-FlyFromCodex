(() => {
'use strict';
const V=window.FFCCapabilityVocabulary,IQ=window.FFCImageQuality,FB=window.FFCFeedback,AC=window.FFCAutonomyCore,Agent=window.BANC888_FLY_AGENT;
if(!V||!IQ||!FB||!AC||!Agent||window.FFC_CAPABILITIES)return;
const st=Agent.state,EXP_KEY='FFC_CAPABILITY_EXPERIENCE_V2',LAST_KEY='FFC_CAPABILITY_LAST_V2',AUTO_KEY='FFC_AUTONOMY_STATE_V1';
let AUTO=null;
const reg=(name,description,capability,sideEffect,handler)=>{st.capabilities.add(capability);st.tools.set(name,{name,description,capability,sideEffect,handler});if(AUTO)AUTO.syncTools([{name,description,capability,sideEffect,executable:st.capabilities.has(capability)}])};
const safeJson=s=>{try{return JSON.parse(String(s||''))}catch{return{ok:false,error:String(s||'')}}};
const load=(k,d)=>{try{return JSON.parse(localStorage.getItem(k)||'null')||d}catch{return d}};
const save=(k,v)=>{try{localStorage.setItem(k,JSON.stringify(v))}catch(e){}};
AUTO=new AC.Autonomy(load(AUTO_KEY,{}),{minSuccesses:2,minReward:.05,minReliability:.66,matchThreshold:.58});
const autoSave=()=>{if(AUTO)save(AUTO_KEY,AUTO.snapshot())};
const autoSync=()=>{if(!AUTO)return null;const m=[...st.tools.values()].map(x=>({name:x.name,description:x.description,capability:x.capability,sideEffect:x.sideEffect,executable:st.capabilities.has(x.capability)}));const r=AUTO.syncTools(m);autoSave();return r};
const autoReward=(ok,value,meta)=>{
 const v=value&&value.validation&&typeof value.validation==='object'?value.validation:(meta&&meta.validation&&typeof meta.validation==='object'?meta.validation:null);
 if(v){
  if(v.pass===false)return -Math.max(.35,Math.min(1,((72-(+v.score||0))/72)+((v.hardIssues||[]).length ? .35 : 0)));
  if(v.score!=null)return Math.max(.1,Math.min(1,((+v.score||72)-60)/40));
 }
 return ok ? .28 : -.65;
};
const autoEpisode=(goal,steps,success,reward,source='capability')=>{if(!AUTO)return null;const p=AUTO.recordEpisode(String(goal||''),steps||[],{success:!!success,reward,learnable:true,source});autoSave();return p};
const autoMatch=goal=>AUTO?AUTO.matchSkill(String(goal||''),name=>st.tools.has(name)&&st.capabilities.has(st.tools.get(name).capability)):null;
autoSync();
const feedbackIngest=(event,targetTool)=>{
  const ev={...event,senses:FB.stimuli(event)};
  try{
    const r=Agent.execute('feedback.ingest',{event:ev,targetTool:String(targetTool||event.action||'delegate')});
    return r&&r.ok?r.value:{ok:false,error:(r&&r.error)||'feedback.ingest failed',event:ev};
  }catch(e){return{ok:false,error:String(e&&e.message||e),event:ev}}
};
const feedbackTest=(name,passed,tier='training',failure=null,targetTool='chat.compose')=>
  feedbackIngest(FB.fromTest(String(name||'test'),!!passed,{tier,action:String(targetTool||'delegate'),failure}),targetTool);
let EXP=load(EXP_KEY,[]),LAST=load(LAST_KEY,{});
const remember=(tool,ok,meta)=>{EXP.push({time:Date.now(),tool,ok:!!ok,meta:meta||{}});if(EXP.length>96)EXP=EXP.slice(-96);save(EXP_KEY,EXP);if(AUTO){const reward=meta&&meta.reward!=null?Number(meta.reward):autoReward(!!ok,null,meta);AUTO.observeTool(tool,{ok:!!ok,reward,quality:meta&&meta.validation,learnable:true});autoSave()}};
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

const REVIEW_KEY='FFC_REVIEW_EPISODES_V4';
let REVIEW=load(REVIEW_KEY,[]);
const clamp01=x=>Math.max(0,Math.min(1,Number(x)||0));
const reviewRemember=e=>{REVIEW.push({...e,time:Date.now()});if(REVIEW.length>160)REVIEW=REVIEW.slice(-160);save(REVIEW_KEY,REVIEW)};
const reviewStatus=()=>{const a=REVIEW.slice(-160),pass=a.filter(x=>x.accepted).length;return{version:'4.0',policy:'GENERATE→TEST→EVALUATE→DIAGNOSE→REPAIR→RETEST→LEARN',episodes:a.length,accepted:pass,rejected:a.length-pass,acceptRate:a.length?pass/a.length:null,last:a.slice(-16)}};
// Retain strings/renderer genes only, never canvases, pixels or review traces.
// The byte limit counts UTF-16 key/payload storage; it is not a RAM measurement.
const QUALITY_CACHE=new Map(),CACHE_LIMIT=12,CACHE_STRING_BYTES=96*1024;
let cacheStringBytes=0;
function cacheRead(key){const row=QUALITY_CACHE.get(key);if(!row)return null;QUALITY_CACHE.delete(key);QUALITY_CACHE.set(key,row);return JSON.parse(row.payload)}
function cacheWrite(key,value){const payload=JSON.stringify(value),bytes=2*(key.length+payload.length);if(bytes>CACHE_STRING_BYTES)return;const old=QUALITY_CACHE.get(key);if(old){cacheStringBytes-=old.bytes;QUALITY_CACHE.delete(key)}while(QUALITY_CACHE.size>=CACHE_LIMIT||cacheStringBytes+bytes>CACHE_STRING_BYTES){const first=QUALITY_CACHE.keys().next().value;cacheStringBytes-=QUALITY_CACHE.get(first).bytes;QUALITY_CACHE.delete(first)}QUALITY_CACHE.set(key,{payload,bytes});cacheStringBytes+=bytes}
const cacheStatus=()=>({entries:QUALITY_CACHE.size,maxEntries:CACHE_LIMIT,retainedStringBytes:cacheStringBytes,maxStringBytes:CACHE_STRING_BYTES});
function adaptiveText({key,ir,field,terms,validate,initial,repair,fallback,localRepair}){
 const candidates=[],seen=new Set(),maxAttempts=ir.quality==='fast'?1:ir.quality==='high'?3:2;
 const search={policy:'validate-deduplicate-repair',maxAttempts,composeCalls:0,assessmentCalls:0,duplicateCount:0,cacheHit:false,stopReason:'budget',repairs:[]};
 const submit=(value,stage,strategy,scope)=>{const text=clean(value);if(!text)return null;if(seen.has(text)){search.duplicateCount++;return null}seen.add(text);const validation=validate(text);search.assessmentCalls++;const row={[field]:text,validation,score:baseScore(validation,text,terms),stage,strategy,scope};candidates.push(row);return row};
 const cached=cacheRead(key);let current=cached&&submit(cached.text,'cached',cached.strategy,cached.scope);
 if(current&&current.validation.pass){search.cacheHit=true;search.stopReason='cached-revalidated';return{candidates,search}}
 const call=fn=>{search.composeCalls++;try{return clean(fn())}catch(e){return''}};
 current=submit(call(initial),'generated','complete factual implementation','composed');
 while(current&&!current.validation.pass&&search.composeCalls<maxAttempts){
  const defects=[...current.validation.issues];search.repairs.push(defects);
  const local=localRepair&&submit(localRepair(current[field],defects),'repair','local defect repair','local-repair');
  if(local){current=local;if(current.validation.pass)break}
  const next=submit(call(()=>repair(current[field],current.validation.issues)),'repair','diagnostic-repair','composed');
  if(!next){search.stopReason='duplicate-or-empty-repair';break}
  if(!next.validation.pass&&next.validation.issues.length>=current.validation.issues.length){current=next;search.stopReason='repair-plateau';break}
  current=next;
 }
 if(!candidates.some(x=>x.validation.pass)){submit(fallback(),'repair','supported local fallback','local-fallback');search.stopReason=candidates.some(x=>x.validation.pass)?'validated-local-fallback':search.stopReason}
 else if(!search.cacheHit)search.stopReason='defects-resolved';
 return{candidates,search};
}
function tokenize(s){return [...new Set(String(s||'').toLowerCase().replace(/[^\p{L}\p{N}_-]+/gu,' ').split(/\s+/).filter(x=>x.length>1))].slice(0,48)}
function coverage(text,terms){const s=String(text||'').toLowerCase(),t=(terms||[]).filter(Boolean);if(!t.length)return 1;let hit=0;for(const x of t)if(s.includes(String(x).toLowerCase()))hit++;return hit/t.length}
function issuePenalty(issues){return Math.min(.72,(issues||[]).length*.16)}
function baseScore(validation,content,terms){const ok=validation&&validation.pass?1:0,len=Math.min(1,String(content||'').length/900),cov=coverage(content,terms);return clamp01(.44*ok+.28*cov+.18*len+.10*(1-issuePenalty(validation&&validation.issues)))}
function diagnose(validation,extra=[]){return [...new Set([...(validation&&validation.issues||[]),...(extra||[])].filter(Boolean))]}
function connectomeLearn(ctrl,accepted,score,issues,domain){
 const events=[];
 if(ctrl&&ctrl.ok&&ctrl.done){
  try{const r=Agent.execute(accepted?'f42.adapt':'f42.rewire',{});events.push({tool:accepted?'f42.adapt':'f42.rewire',ok:!!(r&&r.ok),value:r&&r.value,error:r&&r.error})}catch(e){events.push({tool:accepted?'f42.adapt':'f42.rewire',ok:false,error:String(e&&e.message||e)})}
  if(!accepted||score<.72){try{const r=Agent.execute('f46.evolve',{reason:'review-feedback',score,issues:(issues||[]).slice(0,12)});events.push({tool:'f46.evolve',ok:!!(r&&r.ok),value:r&&r.value,error:r&&r.error})}catch(e){}}
 }
 try{events.push({tool:'feedback.ingest',ok:true,value:feedbackTest(String(domain||'artifact')+'.review',accepted,'training',accepted?null:(issues||[]).join(','),String(domain||'artifact'))})}catch(e){events.push({tool:'feedback.ingest',ok:false,error:String(e&&e.message||e)})}
 return events;
}
function reviewSelect(domain,candidates,ctrl,meta={}){
 const tested=(candidates||[]).map((x,i)=>({...x,index:i,score:clamp01(x.score),diagnosis:diagnose(x.validation,x.diagnosis)})).sort((a,b)=>b.score-a.score);
 const passed=tested.filter(x=>x.validation&&x.validation.pass);
 let best=null,circuit=null;
 const kernel=Agent.state.connectomeSelector;
 if(passed.length&&kernel?.evaluate){
  // Quality is a hard admissibility condition; topology breaks ties between
  // equally scoring acceptable outputs. Readout roles are engineered adapters.
  const top=passed[0].score,eligible=passed.filter(x=>x.score===top);
  const rows=eligible.map(x=>({tool:'888.output.'+x.index,routeId:x.stage==='repair'?'o1p2':/concise|minimal|simple/i.test(x.strategy||'')?'o1p3':/robust|defensive|factual|risk/i.test(x.strategy||'')?'o1p1':'o1p0',excitation:.5+.5*x.score,outputIndex:x.index}));
  circuit=kernel.evaluate(rows,meta.threadCode||'__system__',{reward:meta.skipLearning?undefined:.15});
  if(circuit)best=eligible.find(x=>x.index===circuit.outputIndex)||null;
 }
 const accepted=!!best;
 const episode={domain,accepted,connectome:circuit?.connectome||null,score:best?best.score:0,candidateCount:tested.length,passedCount:passed.length,issues:best?best.diagnosis:['no-candidate'],selected:best?best.index:null,candidates:tested.map(x=>({index:x.index,score:x.score,pass:!!(x.validation&&x.validation.pass),stage:x.stage||'candidate',tests:(x.validation&&x.validation.tests||[]).map(t=>({name:t.name,pass:!!t.pass}))})),meta};
 const learning=meta.skipLearning?[]:connectomeLearn(ctrl,accepted,episode.score,episode.issues,domain);episode.learning=learning.map(x=>({tool:x.tool,ok:x.ok}));reviewRemember(episode);
 return{version:'4.0',policy:'GENERATE→TEST→EVALUATE→DIAGNOSE→REPAIR→RETEST→LEARN',accepted,best,connectome:circuit?.connectome||null,candidates:tested.map(x=>({index:x.index,score:x.score,pass:!!(x.validation&&x.validation.pass),issues:x.diagnosis,stage:x.stage||'candidate'})),learning};
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
 const issues=[],tests=[];code=String(code||'');
 const check=(name,pass,detail)=>{tests.push({name,pass:!!pass,detail:detail||''});if(!pass)issues.push(name)};
 check('minimum-length',code.length>=60,'length='+code.length);
 check('code-shape',looksLikeCode(code,ir.language));
 check('implemented-request',!/BANC888 generated scaffold/.test(code),'A scaffold does not implement an arbitrary request.');
 check('brace-balance',balance(code,'{','}'));
 check('paren-balance',balance(code,'(',')'));
 if(ir.language==='javascript'&&!/\b(import|export)\b/.test(code)){let ok=true,msg='';try{new Function(code)}catch(e){ok=false;msg=e.message}check('javascript-syntax',ok,msg)}
 if(ir.language==='json'){let ok=true;try{JSON.parse(code)}catch(e){ok=false}check('json-syntax',ok)}
 for(const r of ir.recipes||[]){
  if(r==='csv')check('requirement-csv',/csv/i.test(code));
  if(r==='json')check('requirement-json',/json/i.test(code));
  if(r==='http')check('requirement-http',/(fetch|http|request|urlopen|Http)/i.test(code));
  if(r==='permission')check('requirement-permission',/(permission|RECORD_AUDIO|requestPermissions|ActivityCompat)/i.test(code));
 }
 return{pass:issues.length===0,issues,tests,length:code.length,level:'static-syntax-and-requirements'};
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
 const ir=a.ir||V.buildCodeIR(a.prompt||''),prior=getLast(a.threadCode,'code'),priorText=action==='generate'?'':String(prior&&prior.text||'').slice(0,5000),context=String(a.context||'').slice(-1800);
 const key=JSON.stringify(['code',action,ir,context,priorText]),ctrl=QUALITY_CACHE.has(key)?null:controlPrepare(ir),terms=[...tokenize(a.prompt),...(ir.recipes||[]),ir.language].filter(Boolean);
 const plainCode=text=>clean(text).replace(/^```[^\n]*\n([\s\S]*?)\n```\s*$/,'$1');
 const recipeSupported=ir.language==='python'&&(ir.recipes||[]).some(x=>['csv','json','http'].includes(x))||['javascript','typescript'].includes(ir.language)&&(ir.recipes||[]).includes('http')||ir.language==='kotlin'&&(ir.recipes||[]).includes('microphone');
 const localCode=()=>recipeSupported?templateCode(ir,a.prompt):'// BANC888 generated scaffold: request implementation unavailable\n'+templateCode(ir,a.prompt);
 const {candidates,search}=adaptiveText({key,ir,field:'text',terms,validate:text=>validateCode(text,ir),
  initial:()=>plainCode(compose('CodeIR='+JSON.stringify(ir)+'\nThreadContext='+context+'\nPreviousArtifact='+priorText+'\nRequest='+String(a.prompt||'')+'\nReturn only a complete '+ir.language+' implementation. No meta commentary.')),
  repair:(text,issues)=>plainCode(compose('Repair this '+ir.language+' candidate. Preserve the request and remove every listed defect.\nRequest='+String(a.prompt||'')+'\nIssues='+JSON.stringify(issues)+'\nCandidate:\n'+text.slice(0,9000)+'\nReturn code only.')),
  localRepair:(text,issues)=>issues.some(x=>x==='code-shape'||x==='implemented-request')?localCode():null,
  fallback:localCode});
 const review=reviewSelect('code.'+action,candidates,ctrl,{language:ir.language,quality:ir.quality,threadCode:a.threadCode,search}),best=review.best||{text:templateCode(ir,a.prompt),validation:{pass:false,issues:['no-candidate'],tests:[]},score:0,scope:'unimplemented-scaffold'};
 if(review.accepted)cacheWrite(key,{text:best.text,strategy:best.strategy,scope:best.scope});
 const draft=best.text,validation=best.validation,reward=review.learning;let exported=null;if(ir.export&&review.accepted)exported=shareText(draft,ir.filename||('BANC888_code.'+codeExt(ir.language)),'text/plain');
 const out={action,ir,text:draft,validation,review,search,implementationScope:best.scope,control:ctrl,reward,exported};
 setLast(a.threadCode,'code',out);remember('code.'+action,review.accepted,{language:ir.language,issues:review.candidates.filter(x=>!x.pass).flatMap(x=>x.issues).slice(0,12),quality:ir.quality,score:review.best&&review.best.score,candidates:review.candidates.length});return out;
}
function imagePrompt(ir){
 const p=[ir.subject];if(ir.style&&ir.style.length)p.push('style: '+ir.style.join(', '));if(ir.composition&&ir.composition.length)p.push('composition: '+ir.composition.join(', '));if(ir.lighting&&ir.lighting.length)p.push('lighting: '+ir.lighting.join(', '));if(ir.palette&&ir.palette.length)p.push('palette: '+ir.palette.join(', '));if(ir.aspect)p.push('aspect: '+ir.aspect);if(ir.size)p.push('size: '+ir.size);if(ir.quality==='high')p.push('coherent composition, clear subject hierarchy, refined detail');if(ir.negative&&ir.negative.length)p.push('avoid: '+ir.negative.join(', '));return p.filter(Boolean).join(' | ');
}
function generateImage(a,refine){
 const ir=a.ir||V.buildImageIR(a.prompt||''),last=getLast(a.threadCode,'image');
 if(refine&&last&&last.ir)ir.subject=last.ir.subject+'; refinement request: '+ir.request;
 const key=JSON.stringify(['image',ir]),cached=cacheRead(key),ctrl=cached?null:controlPrepare(ir),base=imagePrompt(ir),all=[],seen=new Set();
 const maxCandidates=ir.quality==='fast'?6:ir.quality==='high'?14:10,qualityTarget=ir.quality==='fast'?80:ir.quality==='high'?92:86;
 const search={policy:'quality-target-defect-repair-plateau',maxCandidates,qualityTarget,stopReason:'budget',renderCalls:0,assessmentCalls:0,duplicateCount:0,cacheHit:!!cached};
 function renderVariant(v){
   const fullPrompt=[base,v.prompt].filter(Boolean).join(' | ');
   const identity=JSON.stringify([fullPrompt,v.seed,v.mode,v.detail,v.blur,v.atmosphere,v.layoutX,v.layoutY,v.scaleBias]);
   if(seen.has(identity)){search.duplicateCount++;return null}seen.add(identity);
   let r;search.renderCalls++;
   try{r=Agent.execute('o2.generate',{prompt:fullPrompt,seed:v.seed,mode:v.mode,detail:v.detail,blur:v.blur,atmosphere:v.atmosphere,layoutX:v.layoutX,layoutY:v.layoutY,scaleBias:v.scaleBias})}
   catch(e){r={ok:false,error:String(e&&e.message||e)}}
   const row={id:v.id,parentId:v.parentId||null,generation:+v.generation||0,strategy:v.strategy,prompt:fullPrompt,variantPrompt:v.prompt,seed:v.seed,mode:v.mode,detail:v.detail,blur:v.blur,atmosphere:v.atmosphere,layoutX:+v.layoutX||0,layoutY:+v.layoutY||0,scaleBias:+v.scaleBias||1,value:r&&r.ok?r.value:null,error:r&&r.ok?null:(r&&r.error)||'generation-failed'};
   row.quality=IQ.evaluate(row,ir);search.assessmentCalls++;return row;
 }
 function renderBatch(list){for(const v of list){if(all.length>=maxCandidates)break;const row=renderVariant(v);if(row)all.push(row)}}
 function rerank(){const passed=all.filter(x=>x.quality.pass).sort((x,y)=>y.quality.score-x.quality.score||String(x.id).localeCompare(String(y.id))),rejected=all.filter(x=>!x.quality.pass).sort((x,y)=>y.quality.score-x.quality.score);return{selected:passed[0]||null,passed,rejected,evaluated:all}}
 renderBatch(cached?[{...cached,generation:0}]:IQ.expand(ir,4).map(v=>({...v,generation:0})));
 let ranked=rerank(),plateaus=0;
 const generationTrace=[{generation:0,candidates:all.length,bestScore:ranked.selected?.quality?.score||0,passed:ranked.passed.length}];
 for(let generation=1;generation<=3;generation++){
   if(ranked.selected?.quality?.score>=qualityTarget){search.stopReason='quality-target';break}
   if(ranked.rejected.length&&ranked.rejected.every(x=>x.quality.hardIssues?.includes('unsupported-subject'))){search.stopReason='unsupported-subject';break}
   if(all.length>=maxCandidates)break;
   const previous=Math.max(0,...all.map(x=>x.quality.score));
   const children=IQ.evolve(ranked.evaluated.map(x=>({...x,prompt:x.variantPrompt})),ir,generation,Math.min(4,maxCandidates-all.length)).slice(0,maxCandidates-all.length);
   const before=all.length;renderBatch(children);
   if(all.length===before){search.stopReason='duplicate-or-empty-repair';break}
   ranked=rerank();
   const score=Math.max(0,...all.map(x=>x.quality.score));
   generationTrace.push({generation,candidates:all.length,bestScore:ranked.selected?.quality?.score||0,passed:ranked.passed.length,scoreGain:score-previous});
   plateaus=score-previous<1?plateaus+1:0;
   if(ranked.selected?.quality?.score>=qualityTarget){search.stopReason='quality-target';break}
   if(plateaus>=2){search.stopReason='quality-plateau';break}
 }

 if(!ranked.selected){
   const reasons=ranked.rejected.slice(0,6).map(c=>c.id+':'+[...(c.quality.hardIssues||[]),...(c.quality.issues||[])].join(',')).join(' / ');
   const failQuality=ranked.rejected[0]?.quality||{pass:false,score:0,hardIssues:['no-passing-candidate'],issues:['candidate-exhausted'],metrics:{}};
   const failEvent=FB.fromQuality({...failQuality,evolutionGain:(failQuality.score||0)-(generationTrace[0]?.bestScore||0)},{
     action:refine?'image.refine':'image.generate',source:'image-quality',tier:FB.TIERS.TRAINING,previousScore:generationTrace[0]?.bestScore||0
   });
   const feedback=feedbackIngest(failEvent,'o2.generate');
   remember(refine?'image.refine':'image.generate',false,{quality:ir.quality,candidates:all.length,generations:generationTrace.length,reasons,feedback});
   controlReward(ctrl,false);
   throw new Error('画像候補が品質基準を通過しませんでした。'+(reasons?' '+reasons:''));
 }

 const review=reviewSelect(refine?'image.refine':'image.generate',ranked.evaluated.map(x=>({prompt:x.prompt,value:x.value,validation:x.quality,score:(x.quality?.score||0)/100,stage:x.generation?'evolved':'generated',strategy:x.strategy,diagnosis:[...(x.quality?.hardIssues||[]),...(x.quality?.issues||[])]})),ctrl,{quality:ir.quality,subject:ir.subject,skipLearning:true,evolutionary:true,threadCode:a.threadCode});
 if(!review.accepted)throw new Error('画像候補に対するコネクトーム出力がありません。');
 const best=ranked.evaluated[review.best.index];
 let finalRun,finalQuality;
 if(best===all[all.length-1]){finalRun={ok:true,value:best.value};finalQuality=best.quality}
 else{search.renderCalls++;finalRun=Agent.execute('o2.generate',{prompt:best.prompt,seed:best.seed,mode:best.mode,detail:best.detail,blur:best.blur,atmosphere:best.atmosphere,layoutX:best.layoutX,layoutY:best.layoutY,scaleBias:best.scaleBias});
  if(!finalRun||!finalRun.ok)throw new Error((finalRun&&finalRun.error)||'selected image render failed');
  finalQuality=IQ.evaluate({...best,value:finalRun.value},ir);search.assessmentCalls++}
 if(!finalQuality.pass)throw new Error('最終画像が再検証で品質基準を下回りました。');

 const firstBest=generationTrace[0]?.bestScore||0;
 const feedbackEvent=FB.fromQuality({...finalQuality,evolutionGain:finalQuality.score-firstBest},{
   action:refine?'image.refine':'image.generate',source:'image-quality',tier:FB.TIERS.TRAINING,previousScore:firstBest
 });
 const feedback=feedbackIngest(feedbackEvent,'o2.generate');
 const validation={
   pass:true,score:finalQuality.score,issues:finalQuality.issues,hardIssues:finalQuality.hardIssues,
   metrics:finalQuality.metrics,objects:finalRun.value?.objects||0,scene:finalRun.value?.scene||null,
   candidateCount:all.length,passedCount:ranked.passed.length,rejectedCount:ranked.rejected.length,
   generationCount:generationTrace.length,evolutionGain:finalQuality.score-firstBest,generationTrace,search
 };
 const candidatesPassed=ranked.passed.map(c=>({id:c.id,parentId:c.parentId||null,generation:c.generation||0,strategy:c.strategy,score:c.quality.score,pass:true,issues:c.quality.issues||[],seed:c.seed,mode:c.mode,layoutX:c.layoutX,layoutY:c.layoutY,scaleBias:c.scaleBias}));
 const candidatesRejected=ranked.rejected.map(c=>({id:c.id,parentId:c.parentId||null,generation:c.generation||0,strategy:c.strategy,score:c.quality.score,pass:false,issues:[...(c.quality.hardIssues||[]),...(c.quality.issues||[])],seed:c.seed,mode:c.mode,layoutX:c.layoutX,layoutY:c.layoutY,scaleBias:c.scaleBias}));

 cacheWrite(key,{id:best.id,parentId:best.parentId,strategy:best.strategy,prompt:best.variantPrompt,seed:best.seed,mode:best.mode,detail:best.detail,blur:best.blur,atmosphere:best.atmosphere,layoutX:best.layoutX,layoutY:best.layoutY,scaleBias:best.scaleBias});
 const reward=controlReward(ctrl,true),out={
   ir,prompt:best.prompt,value:finalRun.value,validation,review,search,
   selection:{id:best.id,parentId:best.parentId||null,generation:best.generation||0,strategy:best.strategy,score:finalQuality.score},
   candidates:candidatesPassed,rejected:candidatesRejected,control:ctrl,reward,feedback
 };
 setLast(a.threadCode,'image',out);
 remember(refine?'image.refine':'image.generate',true,{objects:validation.objects,quality:ir.quality,score:validation.score,candidates:validation.candidateCount,passed:validation.passedCount,generations:validation.generationCount,evolutionGain:validation.evolutionGain});
 return out;
}
let VOICE_CFG=load('FFC_VOICE_CONFIG_V2',{language:'ja-JP',rate:1,pitch:1});
function voiceConfigure(a){const ir=a.ir||V.buildVoiceIR(a.prompt||'');VOICE_CFG={language:ir.language||VOICE_CFG.language,rate:ir.rate||VOICE_CFG.rate,pitch:ir.pitch||VOICE_CFG.pitch};save('FFC_VOICE_CONFIG_V2',VOICE_CFG);remember('voice.configure',true,VOICE_CFG);return{...VOICE_CFG,continuousRequested:!!ir.continuous}}
function voiceListen(a){const ir=a.ir||V.buildVoiceIR(a.prompt||''),lang=ir.language||VOICE_CFG.language;if(window.AndroidVoice){AndroidVoice.startListening(lang);remember('voice.listen',true,{language:lang});return{started:true,native:true,language:lang,logicalThread:a.threadCode||null}}if(Agent.listen)Agent.listen();return{started:true,native:false,language:lang}}
function voiceSpeak(a){const ir=a.ir||V.buildVoiceIR(a.prompt||'');let text=String(ir.text||a.text||'').trim();if(!text)try{text=compose('人間へ短く自然に発話する内容を作る。要求: '+String(a.prompt||''))}catch(e){text=String(a.prompt||'')}const cfg={language:ir.language||VOICE_CFG.language,rate:ir.rate||VOICE_CFG.rate,pitch:ir.pitch||VOICE_CFG.pitch};if(window.AndroidVoice){AndroidVoice.speak(text,cfg.language,cfg.rate,cfg.pitch);remember('voice.speak.native',true,cfg);return{spoken:true,native:true,text,...cfg}}if(Agent.speak)Agent.speak(text);return{spoken:true,native:false,text,...cfg}}
function docFallback(ir,context){
 const source=String(context||'').trim().slice(-2200),sections=ir.sections||[];
 const evidence=source?'提供された記録を以下に保持します。記録にない日時、参加者、決定事項は推測で補いません。\n\n'+source:'元の会議記録や資料は提供されていません。この文書は編集用の構成案です。実際の日時、参加者、発言、決定事項を確認してから共有してください。';
 const body=sections.map((name,i)=>'## '+(i+1)+'. '+name+'\n'+(i===0?'目的: '+ir.request+'\n対象: 提供された記録の整理と共有。事実と確認事項を分けて記載します。':i===1?evidence:'確認事項: この節に対応する事実を元の記録と照合します。担当者、期限、合意事項が記録にある場合は原文に沿って記載し、記録がない項目は追加確認が必要と明記します。')).join('\n\n');
 return '# '+ir.title+'\n\n'+body+'\n\n## 出典と確認\n'+(source?'出典: この依頼で提供された文脈。内容の正確性と公開範囲は元の記録で確認してください。':'出典資料なし。具体的な事実を記入するまでは完成した議事録として扱わないでください。')+'\n';
}
function validateDoc(body,ir,source=''){
 const s=String(body||''),heads=(s.match(/^##?\s+/gm)||[]).length,tests=[];const check=(name,pass,detail)=>tests.push({name,pass:!!pass,detail:detail||''});
 check('minimum-length',s.length>=220,'length='+s.length);check('required-sections',heads>=Math.min(3,(ir.sections||[]).length),'headings='+heads);check('no-placeholder',!/\bTODO\b|未定です|十分な知識がない/.test(s));
 if(source)check('source-preserved',s.includes(source),'Supplied text must remain available for checking facts.');
 const issues=tests.filter(x=>!x.pass).map(x=>x.name);return{pass:issues.length===0,issues,tests,length:s.length,headings:heads,level:'structure-and-source-preservation'};
}
function makeDoc(format,a){
 const ir=a.ir||V.buildDocumentIR(a.prompt||''),prior=getLast(a.threadCode,'document'),priorBody=ir.action==='revise'?String(prior&&prior.body||'').slice(0,4000):'',source=String(a.context||'').trim().slice(-2200);
 const key=JSON.stringify(['document',ir,source,priorBody]),ctrl=QUALITY_CACHE.has(key)?null:controlPrepare(ir),terms=[...tokenize(ir.request),...(ir.sections||[])];
 const {candidates,search}=adaptiveText({key,ir,field:'body',terms,validate:body=>validateDoc(body,ir,source),
  initial:()=>compose('DocumentIR='+JSON.stringify(ir)+'\nThreadContext='+source+'\nPreviousDraft='+priorBody+'\nCreate a Japanese document using # and ## headings and all required sections. Preserve supplied facts. Mark absent facts as requiring confirmation. No meta commentary.'),
  repair:(body,issues)=>compose('Repair the following Japanese document. Fix only the listed defects, preserve supplied facts, and return the full document.\nDocumentIR='+JSON.stringify(ir)+'\nSource='+source+'\nIssues='+JSON.stringify(issues)+'\nDraft:\n'+body.slice(0,9000)),
  localRepair:(body,issues)=>{if(issues.some(x=>x==='minimum-length'||x==='required-sections'||x==='no-placeholder'))return docFallback(ir,source);if(issues.includes('source-preserved'))return body+'\n\n## 提供された記録（出典）\n'+source+'\n';return body},
  fallback:()=>docFallback(ir,source)});
 const review=reviewSelect('document.create.'+format,candidates,ctrl,{type:ir.type,quality:ir.quality,threadCode:a.threadCode,search}),best=review.best||candidates[0]||{body:docFallback(ir,source),validation:{pass:false,issues:['no-candidate'],tests:[]},score:0};
 if(review.accepted)cacheWrite(key,{text:best.body,strategy:best.strategy,scope:best.scope});
 const body=best.body,validation=review.accepted?best.validation:{...best.validation,pass:false,issues:[...best.validation.issues,'connectome-output-missing']},title=ir.title||'BANC888資料';let exported=null,outText=body;
 if(review.accepted){if(format==='docx')exported=window.AndroidFiles?safeJson(AndroidFiles.createDocx(title,body,ir.filename||'BANC888_document.docx')):{ok:false,reason:'native file bridge unavailable'};else{const ext=format==='markdown'?'md':format==='html'?'html':'txt',mime=format==='markdown'?'text/markdown':format==='html'?'text/html':'text/plain';if(format==='html')outText='<!doctype html><html lang="ja"><meta charset="utf-8"><title>'+title.replace(/[<>&]/g,'')+'</title><body><pre style="white-space:pre-wrap;font-family:system-ui">'+body.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;')+'</pre></body></html>';exported=shareText(outText,ir.filename||('BANC888_document.'+ext),mime)}}
 const out={format,title,body:outText,ir,validation,review,search,sourceStatus:source?'supplied-source-preserved':'outline-with-missing-facts',control:ctrl,reward:review.learning,exported};setLast(a.threadCode,'document',out);remember('document.create.'+format,review.accepted,{type:ir.type,quality:ir.quality,issues:best.diagnosis,score:best.score,candidates:review.candidates.length});return out;
}

function understandContent(a){
 const prompt=String(a.prompt||''),quoted=[...prompt.matchAll(/[「『]([^」』]+)[」』]/g)].map(x=>x[1]);
 const source=String(a.source||prompt.split(/本文[:：]|source[:：]/i).slice(1).join(' ')||quoted.join(' ')||a.context||'').slice(0,12000);
 const question=prompt.split(/本文[:：]|source[:：]/i)[0],latin=question.toLowerCase().match(/[a-z0-9]{2,}/g)||[];
 const summaryMode=/要約|summari[sz]e|summary|内容.*理解/i.test(question);
 const jp=question.replace(/要約|内容|理解|教えて|ください|本文|質問|答えて|何ですか|ですか|ますか|とは|何|[\s\p{P}]/gu,'');
 const terms=[...new Set([...latin,...Array.from({length:Math.max(0,jp.length-1)},(_,i)=>jp.slice(i,i+2))])].slice(0,48);
 const sentences=source.split(/(?<=[。！？.!?])\s*|\n+/).map(x=>x.trim()).filter(Boolean).slice(0,48);
 const seen=new Set(),remaining=sentences.map((text,index)=>({text,index,relevance:terms.filter(t=>text.toLowerCase().includes(t)).length+(!summaryMode&&/いつ|日付|when/i.test(question)&&/\d+[年月日]|\d+[:/]\d+|開催日/.test(text)?2:0)})).filter(x=>{const key=x.text.normalize('NFKC').replace(/\s+/g,' ');if(seen.has(key))return false;seen.add(key);return true});
 const evidence=[],traces=[],kernel=Agent.state.connectomeSelector;
 let noRelevantEvidence=false;
 while(remaining.length&&evidence.length<3&&kernel?.evaluate){
  const top=Math.max(...remaining.map(x=>x.relevance));if(!summaryMode&&top<=0){noRelevantEvidence=!evidence.length;break}
  const eligible=top>0?remaining.filter(x=>x.relevance===top):remaining;
  const rows=eligible.map(x=>({tool:'888.content.'+x.index,routeId:/とは|です|is |means /i.test(x.text)?'o1p1':/ため|ので|because/i.test(x.text)?'o1p2':'o1p3',excitation:1,contentIndex:x.index}));
  const pick=kernel.evaluate(rows,a.threadCode||'__system__');if(!pick)break;
  const sentence=remaining.find(x=>x.index===pick.contentIndex);evidence.push(sentence);traces.push(pick.connectome);
  remaining.splice(remaining.indexOf(sentence),1);
 }
 evidence.sort((x,y)=>x.index-y.index);
 return{mode:'extractive-source-reading',questionMode:summaryMode?'summary':'source-question',summary:evidence.length?evidence.map(x=>x.text).join(' '):noRelevantEvidence?'提供された資料に、この質問に答える根拠が見つかりませんでした。':'',evidence:evidence.map(x=>({sourceIndex:x.index,text:x.text})),sourceLength:source.length,connectome:traces,validation:{pass:evidence.length>0,issues:evidence.length?[]:[noRelevantEvidence?'no-relevant-evidence':'source-or-connectome-output-missing']}};
}
reg('content.understand','read supplied text and select source evidence through the connectome','read_state','none',understandContent);

reg('code.generate','CodeIR plan -> generate -> static validate -> bounded repair/export','code.write','local',a=>generateCode(a,'generate'));
reg('code.revise','revise the last thread code artifact with CodeIR and validation','code.write','local',a=>generateCode(a,'revise'));
reg('code.debug','debug the last thread code artifact and regenerate a corrected candidate','code.write','local',a=>generateCode(a,'debug'));
reg('code.refactor','refactor the last thread code artifact preserving intent','code.write','local',a=>generateCode(a,'refactor'));
reg('code.test','generate a test-oriented code candidate from CodeIR','code.write','local',a=>generateCode(a,'test'));
reg('code.convert','convert or port the last thread code artifact according to CodeIR','code.write','local',a=>generateCode(a,'convert'));
reg('code.optimize','optimize the last thread code artifact under requested constraints','code.write','local',a=>generateCode(a,'optimize'));
reg('image.generate','ImageIR -> bounded quality target search -> defect repair -> cached assessment -> circuit-gated render','image.write','page',a=>generateImage(a,false));
reg('image.refine','refine prior ImageIR -> bounded defect repair -> quality and circuit gates','image.write','page',a=>generateImage(a,true));
reg('voice.listen','SpeechIR -> Android native speech recognition','voice.input','native',voiceListen);
reg('voice.speak.native','SpeechIR -> Android native TTS with rate pitch language','human.output','native',voiceSpeak);
reg('voice.status.native','read Android native STT TTS permission state','voice.input','none',()=>window.AndroidVoice?safeJson(AndroidVoice.status()):{native:false,fallback:Agent.voiceStatus?Agent.voiceStatus():null});
reg('voice.configure','persist SpeechIR language rate pitch defaults','voice.input','local',voiceConfigure);
reg('document.create.docx','DocumentIR -> structured draft -> validation -> native OOXML DOCX export','document.write','native',a=>makeDoc('docx',a));
reg('document.create.markdown','DocumentIR -> structured Markdown draft -> validation export','document.write','native',a=>makeDoc('markdown',a));
reg('document.create.html','DocumentIR -> structured HTML draft -> validation export','document.write','native',a=>makeDoc('html',a));
reg('document.create.text','DocumentIR -> structured text draft -> validation export','document.write','native',a=>makeDoc('text',a));
reg('capability.experience.status','read bounded success failure experience for artifact tools','compute','none',a=>expStats(a&&a.tool));
reg('capability.quality.status','read bounded quality search cache storage','compute','none',cacheStatus);
reg('capability.review.status','read multi-candidate review and repair history','compute','none',()=>reviewStatus());
reg('feedback.test','ingest a training regression or holdout test result into the Fly feedback path','compute','local',a=>feedbackTest(a?.name,a?.passed,a?.tier||'training',a?.failure||null,a?.targetTool||'chat.compose'));
reg('feedback.status','read learned feedback route bias and recent reward vectors','compute','none',()=>{try{const r=Agent.execute('feedback.status',{});return r&&r.ok?r.value:{error:r&&r.error}}catch(e){return{error:String(e&&e.message||e)}}});
reg('capability.lexicon.status','read specialist vocabulary pack counts','compute','none',()=>V.lexiconStats());
reg('autonomy.status','read dynamically discovered tools learned composite skills and reward statistics','compute','none',()=>AUTO?AUTO.status():{disabled:true});
reg('autonomy.skills','read promoted autonomous composite skills','compute','none',()=>AUTO?AUTO.status().skills:[]);
autoSync();

function summarize(tool,v){
 if(tool.indexOf('code.')===0)return'コード処理 '+tool+' 完了。validation='+(v.validation&&v.validation.pass?'PASS':'CHECK')+' / review='+(v.review?.accepted?'PASS':'REJECT')+' / candidates='+(v.review?.candidates?.length||0)+' / '+(v.ir&&v.ir.language||'auto')+(v.exported&&v.exported.ok?' / file shared':'');
 if(tool.indexOf('image.')===0)return'画像処理 '+tool+' 完了。候補 '+(v.validation?.candidateCount||0)+'件 → 合格 '+(v.validation?.passedCount||0)+'件 / 選択 '+(v.selection?.id||'—')+' / score '+(v.validation?.score??'—')+' / validation='+(v.validation&&v.validation.pass?'PASS':'CHECK');
 if(tool==='voice.listen')return'音声入力を開始しました。';if(tool==='voice.speak.native')return'音声出力しました。';if(tool==='voice.status.native')return'音声状態: '+JSON.stringify(v);if(tool==='voice.configure')return'音声設定を更新しました。';
 if(tool.indexOf('document.create.')===0)return String(v.format||'document').toUpperCase()+'資料を作成しました。validation='+(v.validation&&v.validation.pass?'PASS':'CHECK')+' / review='+(v.review?.accepted?'PASS':'REJECT')+' / candidates='+(v.review?.candidates?.length||0)+(v.exported&&v.exported.ok?' / Android共有を開きました':'');
 return'ツール処理完了。';
}
function present(tool,v,summary){let d=summary;if(tool.indexOf('code.')===0)d+='\n\n'+String(v.text||'').slice(0,14000);else if(tool.indexOf('document.create.')===0)d+='\n\n'+String(v.body||'').slice(0,9000);try{if(Agent.present)Agent.present({finalText:d,selected:{tool},observation:{tool,result:{ok:true,value:v}}})}catch(e){}}
function handle(text,ctx){
 ctx=ctx||{};autoSync();
 const learned=autoMatch(text);
 if(learned&&learned.proposal?.steps?.length){
  let last=null,failed=null,rewards=[];
  for(const step of learned.proposal.steps){
   const args={...(step.args||{})};if(ctx.threadCode!=null)args.threadCode=ctx.threadCode;if(ctx.context!=null)args.context=String(ctx.context||'');
   let r;try{r=Agent.execute(step.tool,args)}catch(e){r={ok:false,error:String(e&&e.message||e)}}
   const reward=autoReward(!!(r&&r.ok),r&&r.value,null);remember(step.tool,!!(r&&r.ok),{reward,autonomySkill:learned.id,validation:r?.value?.validation});rewards.push(reward);
   last={step,r};if(!r||!r.ok){failed=last;break}
  }
  const success=!failed&&!!last,avg=rewards.length?rewards.reduce((a,b)=>a+b,0)/rewards.length:(success ? .2 : -.65);autoEpisode(text,learned.proposal.steps,success,avg,learned.proposal.source);autoSave();
  if(!success)return{handled:true,plan:{handled:true,tool:failed?.step?.tool,source:learned.proposal.source},tool:failed?.step?.tool,error:failed?.r?.error||'learned skill failed',finalText:'獲得スキル実行エラー: '+(failed?.r?.error||'unknown')};
  const finalText=summarize(last.step.tool,last.r.value);present(last.step.tool,last.r.value,finalText);return{handled:true,plan:{handled:true,tool:last.step.tool,source:learned.proposal.source},tool:last.step.tool,value:last.r.value,finalText,learnedSkill:learned.id}
 }
 const plan=V.classify(text);if(!plan.handled||plan.confidence<.58)return{handled:false,plan};
 const args={ir:plan.ir,prompt:String(text||''),context:String(ctx.context||''),threadCode:ctx.threadCode||null},steps=[{tool:plan.tool,args,description:'specialist capability'}];
 const r=Agent.execute(plan.tool,args),reward=autoReward(!!(r&&r.ok),r&&r.value,null);
 if(!r||!r.ok){remember(plan.tool,false,{error:(r&&r.error)||'unknown',reward});autoEpisode(text,steps,false,reward,'specialist-vocabulary');return{handled:true,plan,tool:plan.tool,error:(r&&r.error)||'tool failed',finalText:plan.tool+' でエラー: '+((r&&r.error)||'unknown')}}
 remember(plan.tool,true,{reward,validation:r.value?.validation});autoEpisode(text,steps,true,reward,'specialist-vocabulary');const finalText=summarize(plan.tool,r.value);present(plan.tool,r.value,finalText);return{handled:true,plan,tool:plan.tool,value:r.value,finalText}
}
function ui(){const host=document.getElementById('ffcThreadHub')||document.getElementById('flyAgentCard');if(!host||document.getElementById('ffcCapabilityBar'))return;const x=document.createElement('div');x.id='ffcCapabilityBar';x.style.cssText='display:flex;gap:6px;flex-wrap:wrap;margin:8px 0;font-size:12px;opacity:.9';const n=V.lexiconStats();x.innerHTML='<span>🧰 Specialist Tools v2</span><span>CODE '+n.code+'</span><span>IMAGE '+n.image+'</span><span>VOICE '+n.voice+'</span><span>DOC '+n.document+'</span><span>IR→VERIFY→REPAIR</span>';host.insertBefore(x,host.firstChild)}
window.FFC_CAPABILITIES={version:'2.1-autonomy-review',vocabulary:V,handle,classify:V.classify,experience:expStats,last:(thread,kind)=>getLast(thread,kind),autonomy:AUTO,autonomyStatus:()=>AUTO?AUTO.status():null,persistAutonomy:autoSave,syncAutonomy:autoSync,manifest:()=>[...st.tools.values()].map(({handler,...x})=>x)};
if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',ui,{once:true});else setTimeout(ui,0);
})();
