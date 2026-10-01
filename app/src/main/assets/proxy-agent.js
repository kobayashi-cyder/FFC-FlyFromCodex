(() => {
'use strict';
const C=window.FFCProxyCore,P=window.FFCIrPatch,V=window.FFCCapabilityVocabulary,RP=window.FFCResearchPhysics,FB=window.FFCFeedback,Agent=window.BANC888_FLY_AGENT,Caps=window.FFC_CAPABILITIES;
if(!C||!P||!V||!FB||!Agent||!Caps||window.FFC_PROXY_AGENT)return;
const KEY='FFC_PROXY_CHECKPOINT_V2',BAK=KEY+'_BAK',TMP=KEY+'_TMP';
const policy=new C.ToolPolicy(new Set([...(Agent.state?.capabilities||[]),'compute','read_state','write_state','human_output','code.write','image.write','voice.input','document.write','network.read']));
const executive=new C.Executive({policy,maxPlanSteps:16}),arbiter=new C.BodyArbiter();
const now=()=>Date.now(),id=()=>Math.random().toString(36).slice(2,14);
function safeParse(s,d){try{const x=JSON.parse(s);return x&&typeof x==='object'?x:d}catch{return d}}
function blank(){return{schema:2,goals:[],events:[],lastThread:null,recovered:false}}
function load(){
 let s=safeParse(localStorage.getItem(KEY),null);
 if(!s){s=safeParse(localStorage.getItem(BAK),null);if(s)s.recovered=true}
 s=s||blank();s.goals=C.recoverGoals(Array.isArray(s.goals)?s.goals:[]);s.events=Array.isArray(s.events)?s.events.slice(-240):[];return s
}
let state=load();
function save(){
 try{
  const payload=JSON.stringify({...state,events:state.events.slice(-240)});
  localStorage.setItem(TMP,payload);JSON.parse(localStorage.getItem(TMP));
  const old=localStorage.getItem(KEY);if(old){try{JSON.parse(old);localStorage.setItem(BAK,old)}catch(e){}}
  localStorage.setItem(KEY,localStorage.getItem(TMP));localStorage.removeItem(TMP);
 }catch(e){}
}
function emit(kind,message,data){state.events.push({time:now(),kind,message,data:data||{}});if(state.events.length>240)state.events.splice(0,state.events.length-240);save()}
function spec(name){const s=Agent.state&&Agent.state.tools&&Agent.state.tools.get?Agent.state.tools.get(name):null;if(!s)return null;return{capability:C.classifyCapability(name,s),sideEffect:!!s.sideEffect}}
function syncAutonomy(){try{return Caps.syncAutonomy?Caps.syncAutonomy():null}catch(e){emit('autonomy-sync-error',String(e&&e.message||e));return null}}
function feedbackEvent(tool,r){
 if(/^feedback\.|^autonomy\./.test(String(tool||'')))return null;
 const val=r&&r.value&&r.value.validation;
 let ev;
 if(val&&typeof val==='object')ev=FB.fromQuality(val,{tier:'training',action:tool,source:tool});
 else ev=FB.fromTest('tool:'+tool,!!(r&&r.ok),{tier:'training',action:tool,failure:r&&r.error||null});
 try{Agent.execute('feedback.ingest',{event:{...ev,senses:FB.stimuli(ev)},targetTool:tool})}catch(e){}
 try{if(Caps.autonomy)Caps.autonomy.observeTool(tool,{ok:!!(r&&r.ok),reward:ev.reward*ev.learningScale,quality:val||null,learnable:ev.learnable});if(Caps.persistAutonomy)Caps.persistAutonomy()}catch(e){}
 return ev
}
function learnedProposal(text){
 syncAutonomy();const auto=Caps.autonomy;if(!auto||!auto.matchSkill)return null;
 try{return auto.matchSkill(String(text||''),name=>{const s=spec(name);return !!(s&&policy.allows(s.capability))})}catch(e){emit('autonomy-match-error',String(e&&e.message||e));return null}
}
function baseCandidateProposal(text){
 if(!Agent.buildCandidates)return null;
 try{
  const built=Agent.buildCandidates(String(text||''),null,[]),auto=Caps.autonomy;
  let rows=Array.isArray(built?.candidates)?built.candidates:[];
  const q=String(text||''),extra=(tool,args,exc=.9)=>{if(Agent.state?.tools?.has?.(tool))rows.push({tool,args:args||{},excitation:exc,inhibition:0,confidence:.96,source:'autonomy-diagnostic-router'})};
  if(/micro.*(?:status|状態)|(?:status|状態).*micro/i.test(q))extra('micro.status',{},1.03);
  if(/f38.*(?:status|状態)|(?:status|状態).*f38/i.test(q))extra('f38.status',{},1.03);
  if(/f42.*(?:status|状態)|(?:status|状態).*f42/i.test(q))extra('f42.status',{},1.03);
  if(/f46.*(?:status|状態)|(?:status|状態).*f46/i.test(q))extra('f46.status',{},1.03);
  if(/(?:feedback|フィードバック|報酬|reward).*(?:status|状態)|(?:学習状態|報酬状態)/i.test(q))extra('feedback.status',{},1.02);
  if(/(?:autonomy|自律|獲得スキル|学習).*(?:status|状態|一覧)|獲得スキル一覧/i.test(q))extra('autonomy.status',{},1.06);
  if(/(?:tool|ツール|機能).*(?:manifest|一覧|リスト)|できること一覧/i.test(q))extra('system.manifest',{},1.01);
  if(/(?:画面|display).*(?:表示|出して|show)/i.test(q))extra('human.display',{text:q},.97);
  if(auto&&auto.enrichCandidates)rows=auto.enrichCandidates(rows);
  rows=rows.filter(x=>{const s=spec(x.tool);return !!(s&&policy.allows(s.capability))}).map(x=>({...x,activation:(+x.excitation||0)-(+x.inhibition||0)}));
  rows.sort((a,b)=>b.activation-a.activation||(b.confidence||0)-(a.confidence||0));
  const pick=rows[0];if(!pick||pick.activation<.05)return null;
  return{handled:true,plan:{handled:true,tool:pick.tool,source:'fly-candidate-router',confidence:pick.confidence||.7},patch:null,proposal:{source:'fly-candidate-router',confidence:pick.confidence||.7,steps:[{tool:pick.tool,args:pick.args||{},description:'autonomous candidate selection'}]}}
 }catch(e){emit('candidate-router-error',String(e&&e.message||e));return null}
}
function artifactKind(domain){return domain==='code'?'code':domain==='image'?'image':domain==='document'?'document':domain==='voice'?'voice':null}
function scopeFromText(text){const t=String(text||'');if(/少し|ちょっと|だけ|微調整|一箇所|1箇所/.test(t))return'micro';if(/全面|全体|大幅|根本|作り直|大きく/.test(t))return'macro';return'meso'}
function shouldPatch(plan,text){
 if(!plan||!plan.ir)return false;
 if(['revise','refine','debug','refactor','optimize','convert','test'].includes(plan.action))return true;
 return /さっき|前の|これを|続きを|そのまま|もう少し|だけ変|修正|改善/.test(String(text||''));
}
function proposal(text,ctx){
 ctx=ctx||{};syncAutonomy();
 const learned=learnedProposal(text);
 if(learned&&learned.proposal?.steps?.length){
  const first=learned.proposal.steps[0];
  return{handled:true,plan:{handled:true,tool:first.tool,source:learned.proposal.source,confidence:learned.proposal.confidence},patch:null,learnedSkill:learned.id,proposal:learned.proposal}
 }
 const rp=RP?.classify?.(text),plan=rp?.handled?rp:V.classify(text);
 if(plan.handled){
  let ir=plan.ir,patch=null;
  if(shouldPatch(plan,text)){
   const k=artifactKind(plan.domain),last=k&&Caps.last?Caps.last(ctx.threadCode,k):null;
   if(last&&last.ir&&last.ir.kind===ir.kind){try{patch=P.infer(last.ir,text,V,scopeFromText(text));ir=patch.ir;if(ir.action!==undefined)ir.action=plan.action}catch(e){emit('ir-patch-error',String(e&&e.message||e),{tool:plan.tool})}}
  }
  const step={tool:plan.tool,args:{ir,prompt:String(text||''),context:String(ctx.context||''),threadCode:ctx.threadCode||null},description:'specialist vocabulary route'};
  return{handled:true,plan,patch,proposal:{source:'specialist-vocabulary',confidence:plan.confidence,steps:[step]}}
 }
 const base=baseCandidateProposal(text);if(base)return base;
 return{handled:false,plan}
}
function finalText(tool,value,status,patch){
 const patchNote=patch&&patch.changed&&patch.changed.length?' / IR '+patch.scope+' patch '+patch.changed.length:'';
 if(status===C.Status.BLOCKED)return'代理人が実行をブロックしました。'+patchNote;
 if(status===C.Status.WAITING)return'共有身体が使用中のため待機しています。'+patchNote;
 if(status===C.Status.FAILED)return'生成または検証に失敗しました。'+patchNote;
 if(/^code\./.test(tool))return'コード処理を完了しました。'+(value&&value.validation?' validation='+(value.validation.pass?'PASS':'CHECK'):'')+patchNote;
 if(/^image\./.test(tool))return'画像処理を完了しました。'+(value&&value.validation?' validation='+(value.validation.pass?'PASS':'CHECK'):'')+patchNote;
 if(/^document\./.test(tool))return String(value&&value.format||'DOCUMENT').toUpperCase()+'資料を作成しました。'+(value&&value.validation?' validation='+(value.validation.pass?'PASS':'CHECK'):'')+patchNote;
 if(/^voice\./.test(tool))return'音声ツールを実行しました。'+patchNote;
 return'ツールを実行しました。'+patchNote;
}
function execute(text,ctx){
 ctx=ctx||{};const pp=proposal(text,ctx);if(!pp.handled)return{handled:false,plan:pp.plan};
 const goal={id:id(),text:String(text||''),threadId:ctx.threadCode||null,priority:+ctx.priority||50,status:C.Status.QUEUED,createdAt:now(),updatedAt:now(),attempts:0};
 state.goals.push(goal);save();
 const verdict=executive.evaluate(pp.proposal,spec);
 if(!verdict.accepted){goal.status=C.Status.BLOCKED;goal.lastError=verdict.reason;goal.updatedAt=now();emit('blocked',verdict.reason,{goalId:goal.id,tool:pp.plan?.tool});return{handled:true,status:goal.status,tool:pp.plan?.tool,plan:pp.plan,patch:pp.patch,finalText:'代理人が '+verdict.reason+' のため実行をブロックしました。'}}
 goal.status=C.Status.RUNNING;goal.attempts++;goal.updatedAt=now();state.lastThread=ctx.threadCode||'__system__';save();
 const results=[],rewards=[];let failed=null,last=null;
 for(let i=0;i<pp.proposal.steps.length;i++){
  const step=pp.proposal.steps[i],resource=C.resourceForTool(step.tool),owner=ctx.threadCode||'__system__',lease=arbiter.acquire(resource,owner);
  if(!lease){failed={step,error:'resource busy: '+resource,status:C.Status.WAITING};break}
  const args={...(step.args||{})};
  if(ctx.threadCode!=null)args.threadCode=ctx.threadCode;
  if(ctx.context!=null&&args.context==null)args.context=String(ctx.context||'');
  let r;
  try{r=Agent.execute(step.tool,args)}
  catch(e){r={ok:false,error:String(e&&e.message||e)}}
  finally{try{arbiter.release(lease)}catch(e){}}
  const ev=feedbackEvent(step.tool,r),reward=ev?ev.reward*ev.learningScale:(r&&r.ok ? .2 : -.6);
  rewards.push(Number(reward)||0);results.push({tool:step.tool,result:r,reward});last={step,r};
  if(!r||!r.ok){failed={step,r,error:(r&&r.error)||'tool failed',status:C.Status.FAILED};break}
  const val=r.value&&r.value.validation;
  if(val&&val.pass===false){failed={step,r,error:'artifact validation failed',status:C.Status.FAILED};break}
 }
 const avg=rewards.length?rewards.reduce((a,b)=>a+b,0)/rewards.length:(failed?-.6:.2);
 if(Caps.autonomy&&Caps.autonomy.recordEpisode){
  try{const promoted=Caps.autonomy.recordEpisode(text,pp.proposal.steps,{success:!failed,reward:avg,learnable:true,source:pp.proposal.source,skillId:pp.learnedSkill||null});if(promoted)emit('skill-acquired','acquired '+promoted.id,{goalId:goal.id,skill:promoted});if(Caps.persistAutonomy)Caps.persistAutonomy()}catch(e){emit('autonomy-record-error',String(e&&e.message||e))}
 }
 if(failed){
  goal.status=failed.status||C.Status.FAILED;goal.lastError=failed.error;emit(goal.status===C.Status.WAITING?'waiting':'observation',failed.error,{goalId:goal.id,tool:failed.step?.tool});
 }else{goal.status=C.Status.DONE;goal.lastError=null;emit('observation','success',{goalId:goal.id,steps:results.map(x=>x.tool)})}
 goal.updatedAt=now();save();
 const tool=last?.step?.tool||failed?.step?.tool||pp.plan?.tool,value=last?.r?.value;
 return{handled:true,status:goal.status,tool,plan:pp.plan,patch:pp.patch,value,error:failed?.error||null,steps:results,learnedSkill:pp.learnedSkill||null,finalText:finalText(tool,value,goal.status,pp.patch)};
}
function retry(goalId){const g=state.goals.find(x=>x.id===goalId);if(!g||![C.Status.BLOCKED,C.Status.FAILED,C.Status.WAITING].includes(g.status))return false;g.status=C.Status.QUEUED;g.lastError=null;g.updatedAt=now();save();return true}
function cancel(goalId){const g=state.goals.find(x=>x.id===goalId);if(!g||[C.Status.DONE,C.Status.CANCELLED].includes(g.status))return false;g.status=C.Status.CANCELLED;g.updatedAt=now();save();return true}
function status(){return{schema:state.schema,recovered:!!state.recovered,goals:state.goals.slice(-80),events:state.events.slice(-40),body:arbiter.snapshot(),allowed:[...policy.allowed],autonomy:Caps.autonomyStatus?Caps.autonomyStatus():null}}
function patchIr(ir,ops,scope){return P.apply(ir,ops,scope)}
window.FFC_PROXY_AGENT={version:'2.1-autonomy',execute,proposal,status,retry,cancel,patchIr,policy,body:arbiter,executive};
setTimeout(()=>{const b=document.getElementById('ffcCapabilityBar');if(b&&!document.getElementById('ffcProxyBadge')){const x=document.createElement('span');x.id='ffcProxyBadge';x.textContent='🪰 PROXY EXEC + IR PATCH';b.appendChild(x)}},200);
if(state.recovered)emit('recovery','checkpoint restored from backup');
})();