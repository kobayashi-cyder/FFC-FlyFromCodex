(() => {
'use strict';
const C=window.FFCProxyCore,P=window.FFCIrPatch,V=window.FFCCapabilityVocabulary,RP=window.FFCResearchPhysics,Agent=window.BANC888_FLY_AGENT,Caps=window.FFC_CAPABILITIES;
if(!C||!P||!V||!Agent||!Caps||window.FFC_PROXY_AGENT)return;
const KEY='FFC_PROXY_CHECKPOINT_V2',BAK=KEY+'_BAK',TMP=KEY+'_TMP';
const policy=new C.ToolPolicy(new Set(['compute','read_state','write_state','human_output','code.write','image.write','voice.input','document.write','network.read']));
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
function artifactKind(domain){return domain==='code'?'code':domain==='image'?'image':domain==='document'?'document':domain==='voice'?'voice':null}
function scopeFromText(text){const t=String(text||'');if(/少し|ちょっと|だけ|微調整|一箇所|1箇所/.test(t))return'micro';if(/全面|全体|大幅|根本|作り直|大きく/.test(t))return'macro';return'meso'}
function shouldPatch(plan,text){
 if(!plan||!plan.ir)return false;
 if(['revise','refine','debug','refactor','optimize','convert','test'].includes(plan.action))return true;
 return /さっき|前の|これを|続きを|そのまま|もう少し|だけ変|修正|改善/.test(String(text||''));
}
function proposal(text,ctx){
 const rp=RP?.classify?.(text);const plan=rp?.handled?rp:V.classify(text);if(!plan.handled)return{handled:false,plan};
 let ir=plan.ir,patch=null;
 if(shouldPatch(plan,text)){
  const k=artifactKind(plan.domain),last=k&&Caps.last?Caps.last(ctx.threadCode,k):null;
  if(last&&last.ir&&last.ir.kind===ir.kind){try{patch=P.infer(last.ir,text,V,scopeFromText(text));ir=patch.ir;if(ir.action!==undefined)ir.action=plan.action}catch(e){emit('ir-patch-error',String(e&&e.message||e),{tool:plan.tool})}}
 }
 const step={tool:plan.tool,args:{ir,prompt:String(text||''),context:String(ctx.context||''),threadCode:ctx.threadCode||null}};
 return{handled:true,plan,patch,proposal:{source:'specialist-vocabulary',confidence:plan.confidence,steps:[step]}};
}
function finalText(tool,value,status,patch){
 const patchNote=patch&&patch.changed&&patch.changed.length?' / IR '+patch.scope+' patch '+patch.changed.length:'',rv=value&&value.review,reviewNote=rv?' / review='+(rv.accepted?'PASS':'REJECT')+' '+Math.round((rv.best?.score||rv.score||0)*100)+'% '+(rv.candidates?.filter?.(x=>x.pass).length||rv.passedCount||0)+'/'+(rv.candidates?.length||rv.candidateCount||0):'';
 if(status===C.Status.BLOCKED)return'代理人が実行をブロックしました。'+patchNote;
 if(status===C.Status.WAITING)return'共有身体が使用中のため待機しています。'+patchNote;
 if(status===C.Status.FAILED)return(rv&&rv.accepted===false?'候補を生成・検査・修復しましたが、合格候補がないため成果物は出力しません。':'生成または検証に失敗しました。')+reviewNote+patchNote;
 if(/^code\./.test(tool))return'コード処理を完了しました。'+(value&&value.validation?' validation='+(value.validation.pass?'PASS':'CHECK'):'')+reviewNote+patchNote;
 if(/^image\./.test(tool))return'画像処理を完了しました。'+(value&&value.validation?' validation='+(value.validation.pass?'PASS':'CHECK'):'')+reviewNote+patchNote;
 if(/^document\./.test(tool))return String(value&&value.format||'DOCUMENT').toUpperCase()+'資料を作成しました。'+(value&&value.validation?' validation='+(value.validation.pass?'PASS':'CHECK'):'')+reviewNote+patchNote;
 if(/^voice\./.test(tool))return'音声ツールを実行しました。'+patchNote;
 return'ツールを実行しました。'+reviewNote+patchNote;
}
function execute(text,ctx){
 ctx=ctx||{};const pp=proposal(text,ctx);if(!pp.handled)return{handled:false,plan:pp.plan};
 const goal={id:id(),text:String(text||''),threadId:ctx.threadCode||null,priority:+ctx.priority||50,status:C.Status.QUEUED,createdAt:now(),updatedAt:now(),attempts:0};
 state.goals.push(goal);save();
 const verdict=executive.evaluate(pp.proposal,spec);
 if(!verdict.accepted){goal.status=C.Status.BLOCKED;goal.lastError=verdict.reason;goal.updatedAt=now();emit('blocked',verdict.reason,{goalId:goal.id,tool:pp.plan.tool});return{handled:true,status:goal.status,tool:pp.plan.tool,plan:pp.plan,patch:pp.patch,finalText:'代理人が '+verdict.reason+' のため実行をブロックしました。'}}
 const step=pp.proposal.steps[0],resource=C.resourceForTool(step.tool),owner=ctx.threadCode||'__system__',lease=arbiter.acquire(resource,owner);
 if(!lease){goal.status=C.Status.WAITING;goal.lastError='resource busy: '+resource;goal.updatedAt=now();emit('waiting',goal.lastError,{goalId:goal.id});return{handled:true,status:goal.status,tool:step.tool,plan:pp.plan,patch:pp.patch,finalText:finalText(step.tool,null,goal.status,pp.patch)}}
 goal.status=C.Status.RUNNING;goal.attempts++;goal.updatedAt=now();state.lastThread=owner;save();
 let r;
 try{r=Agent.execute(step.tool,step.args)}
 catch(e){r={ok:false,error:String(e&&e.message||e)}}
 finally{try{arbiter.release(lease)}catch(e){}}
 if(!r||!r.ok){goal.status=C.Status.FAILED;goal.lastError=(r&&r.error)||'tool failed';emit('observation','tool failed',{goalId:goal.id,tool:step.tool,error:goal.lastError})}
 else{
  const val=r.value&&r.value.validation,review=r.value&&r.value.review;
  if((val&&val.pass===false)||(review&&review.accepted===false)){goal.status=C.Status.FAILED;goal.lastError=review&&review.accepted===false?'review gate rejected all candidates':'artifact validation failed';emit('observation','validation/review failed',{goalId:goal.id,tool:step.tool,issues:val?.issues||review?.best?.diagnosis||[],review:review||null})}
  else{goal.status=C.Status.DONE;goal.lastError=null;emit('observation','success',{goalId:goal.id,tool:step.tool,review:review||null,validation:val||null})}
 }
 goal.updatedAt=now();save();
 return{handled:true,status:goal.status,tool:step.tool,plan:pp.plan,patch:pp.patch,value:r&&r.value,error:r&&r.error,finalText:finalText(step.tool,r&&r.value,goal.status,pp.patch)};
}
function retry(goalId){const g=state.goals.find(x=>x.id===goalId);if(!g||![C.Status.BLOCKED,C.Status.FAILED,C.Status.WAITING].includes(g.status))return false;g.status=C.Status.QUEUED;g.lastError=null;g.updatedAt=now();save();return true}
function cancel(goalId){const g=state.goals.find(x=>x.id===goalId);if(!g||[C.Status.DONE,C.Status.CANCELLED].includes(g.status))return false;g.status=C.Status.CANCELLED;g.updatedAt=now();save();return true}
function status(){return{schema:state.schema,recovered:!!state.recovered,goals:state.goals.slice(-80),events:state.events.slice(-40),body:arbiter.snapshot(),allowed:[...policy.allowed]}}
function patchIr(ir,ops,scope){return P.apply(ir,ops,scope)}
window.FFC_PROXY_AGENT={version:'2.1-unified-autonomy',execute,proposal,status,retry,cancel,patchIr,policy,body:arbiter,executive};
setTimeout(()=>{const b=document.getElementById('ffcCapabilityBar');if(b&&!document.getElementById('ffcProxyBadge')){const x=document.createElement('span');x.id='ffcProxyBadge';x.textContent='🪰 PROXY EXEC + IR PATCH';b.appendChild(x)}},200);
if(state.recovered)emit('recovery','checkpoint restored from backup');
})();