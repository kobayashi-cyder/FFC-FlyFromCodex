(() => {
'use strict';
const C=window.FFCProxyCore,P=window.FFCIrPatch,V=window.FFCCapabilityVocabulary,RP=window.FFCResearchPhysics,FB=window.FFCFeedback,CG=window.FFCCognitionCore,MM=window.FFCCognitionMultimodal,Agent=window.BANC888_FLY_AGENT,Caps=window.FFC_CAPABILITIES;
if(!C||!P||!V||!FB||!Agent||!Caps||window.FFC_PROXY_AGENT)return;
const KEY='FFC_PROXY_CHECKPOINT_V2',BAK=KEY+'_BAK',TMP=KEY+'_TMP';
const policy=new C.ToolPolicy(new Set([...(Agent.state?.capabilities||[]),'compute','read_state','write_state','human_output','code.write','image.write','voice.input','document.write','network.read']));
const executive=new C.Executive({policy,maxPlanSteps:16}),arbiter=new C.BodyArbiter();
const now=()=>Date.now(),id=()=>Math.random().toString(36).slice(2,14);
let lastCompatibility=null;
function safeParse(s,d){try{const x=JSON.parse(s);return x&&typeof x==='object'?x:d}catch{return d}}
function blank(){return{schema:2,goals:[],events:[],lastThread:null,recovered:false}}
function trimGoals(goals){
 let remove=Math.max(0,goals.length-80);
 return goals.filter(g=>{if(remove>0&&[C.Status.DONE,C.Status.CANCELLED].includes(g.status)){remove--;return false}return true});
}
function load(){
 let s=safeParse(localStorage.getItem(KEY),null);
 if(!s){s=safeParse(localStorage.getItem(BAK),null);if(s)s.recovered=true}
 s=s||blank();s.goals=trimGoals(C.recoverGoals(Array.isArray(s.goals)?s.goals:[]));s.events=Array.isArray(s.events)?s.events.slice(-240):[];return s
}
let state=load();
let connectome=null;
try{connectome=new C.ConnectomeMultiplexer(Agent.connectome,{maxLanes:8});connectome.restore(state.connectome);Agent.state.connectomeSelector=connectome}catch(e){state.connectomeError=String(e.message||e)}
function selectThroughConnectome(rows,ctx={},credit=false){
 if(!connectome)return null;
 const allowed=rows.filter(x=>{const s=spec(x.tool);return !!(s&&policy.allows(s.capability))});
 const lane=ctx.threadCode||'__system__';
 try{
  const pick=credit?connectome.select(allowed,lane):connectome.evaluate(allowed,lane);
  if(pick)state.connectomeError=null;
  return pick;
 }catch(e){state.connectomeError=String(e.message||e);return null}
}
function save(){
 try{
  const payload=JSON.stringify({...state,connectome:connectome?connectome.snapshot():null,events:state.events.slice(-240)});
  localStorage.setItem(TMP,payload);JSON.parse(localStorage.getItem(TMP));
  const old=localStorage.getItem(KEY);if(old){try{JSON.parse(old);localStorage.setItem(BAK,old)}catch(e){}}
  localStorage.setItem(KEY,localStorage.getItem(TMP));localStorage.removeItem(TMP);
 }catch(e){}
}
function emit(kind,message,data){state.events.push({time:now(),kind,message,data:data||{}});if(state.events.length>240)state.events.splice(0,state.events.length-240);save()}
function spec(name){const s=Agent.state&&Agent.state.tools&&Agent.state.tools.get?Agent.state.tools.get(name):null;if(!s)return null;return{capability:C.classifyCapability(name,s),sideEffect:!!s.sideEffect}}
function syncAutonomy(){try{return Caps.syncAutonomy?Caps.syncAutonomy():null}catch(e){emit('autonomy-sync-error',String(e&&e.message||e));return null}}
function feedbackEvent(tool,r,ctx={}){
 if(/^feedback\.|^autonomy\./.test(String(tool||'')))return null;
 const val=r&&r.value&&r.value.validation;
 let ev;
 if(val&&typeof val==='object')ev=FB.fromQuality(val,{tier:'training',action:tool,source:tool});
 else ev=FB.fromTest('tool:'+tool,!!(r&&r.ok),{tier:'training',action:tool,failure:r&&r.error||null});
 try{Agent.execute('feedback.ingest',{event:{...ev,senses:FB.stimuli(ev)},targetTool:tool})}catch(e){}
 try{if(Caps.autonomy)Caps.autonomy.observeTool(tool,{ok:!!(r&&r.ok),reward:ev.reward*ev.learningScale,quality:val||null,learnable:ev.learnable});if(Caps.persistAutonomy)Caps.persistAutonomy()}catch(e){}
 if(connectome)connectome.reinforce(tool,ev.reward*ev.learningScale,{learnable:ev.learnable,tier:ev.tier,lane:ctx.threadCode||'__system__'});
 return ev
}
function learnedProposal(text){
 syncAutonomy();const auto=Caps.autonomy;if(!auto||!auto.matchSkill)return null;
 try{return auto.matchSkill(String(text||''),name=>{const s=spec(name);return !!(s&&policy.allows(s.capability))})}catch(e){emit('autonomy-match-error',String(e&&e.message||e));return null}
}
function baseCandidateProposal(text,ctx={}){
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
  const pick=selectThroughConnectome(rows,ctx);if(!pick)return null;
  return{handled:true,plan:{handled:true,tool:pick.tool,source:'fly-candidate-router',confidence:pick.confidence||.7,connectome:pick.connectome},patch:null,proposal:{source:'fly-candidate-router',confidence:pick.confidence||.7,steps:[{tool:pick.tool,args:pick.args||{},description:'autonomous candidate selection'}]}}
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
 ctx=ctx||{};syncAutonomy();const options=[];
 const learned=learnedProposal(text);
 if(learned&&learned.proposal?.steps?.length){
  const first=learned.proposal.steps[0];
  options.push({handled:true,plan:{handled:true,tool:first.tool,source:learned.proposal.source,confidence:learned.proposal.confidence},patch:null,learnedSkill:learned.id,proposal:learned.proposal});
 }
 const video=/動画|video|アニメ|映像/i.test(String(text))&&/生成|作って|作成|generate|create/i.test(String(text))&&!/コード|code|実装|python|javascript|kotlin/i.test(String(text));
 const reading=(!!ctx.attachedNow&&!V.classify(text).handled||/要約|summari[sz]e|内容.*(?:理解|読)|本文.*(?:質問|答)|添付.*(?:資料|内容|読|教|答)/i.test(String(text)))&&!/コード|code|画像|image|動画|video|docx|文書作成/i.test(String(text));
 const rp=RP?.classify?.(text),chat=!reading&&!video&&!rp?.handled&&!V.classify(text).handled&&!/(生成|作成|作って|書いて|実装|修正|改善|変換|要約|計算|足し算|引き算|検索|状態|一覧|診断|表示|読み上げ|音声入力|コンパイル|回路|connectome|banc|micro|f38|f42|f46|feedback|manifest|calculate|generate|create|compile|status|search|debug|refactor|\btest\b)/i.test(String(text)),plan=chat?{handled:true,tool:'chat.compose',domain:'chat',confidence:1}:reading?{handled:true,tool:'content.understand',domain:'content',confidence:.96}:video?{handled:true,tool:'o3.generate',domain:'video',confidence:.96}:rp?.handled?rp:V.classify(text);
 if(chat)options.length=0;
 if(plan.handled){
  let ir=plan.ir,patch=null;
  if(shouldPatch(plan,text)){
   const k=artifactKind(plan.domain),last=k&&Caps.last?Caps.last(ctx.threadCode,k):null;
   if(last&&last.ir&&last.ir.kind===ir.kind){try{patch=P.infer(last.ir,text,V,scopeFromText(text));ir=patch.ir;if(ir.action!==undefined)ir.action=plan.action}catch(e){emit('ir-patch-error',String(e&&e.message||e),{tool:plan.tool})}}
  }
  const step={tool:plan.tool,args:{ir,goal:String(text||''),prompt:String(text||''),context:String(ctx.context||''),source:String(ctx.source||''),threadCode:ctx.threadCode||null},description:'specialist vocabulary route'};
  options.push({handled:true,plan,patch,proposal:{source:'specialist-vocabulary',confidence:plan.confidence,steps:[step]}});
 }else{
  const base=baseCandidateProposal(text,ctx);if(base)options.push(base);
 }
 if(!options.length)return{handled:false,plan};
 // Equivalent action sequences reuse the acquired skill. Distinct applicable
 // plans compete through circuit output rather than a fixed learned-first rule.
 const unique=[];
 for(const option of options){
  const signature=JSON.stringify(option.proposal.steps.map(x=>x.tool));
  if(!unique.some(x=>x.signature===signature))unique.push({signature,option});
 }
 const valid=unique.map(x=>x.option).filter(x=>executive.evaluate(x.proposal,spec).accepted);
 if(!valid.length)return options[0]; // Preserve the executive's explicit denial.
 if(window.FFCAICompat){
  const prepared=window.FFCAICompat.prepare(text,ctx,valid,rows=>selectThroughConnectome(rows,ctx));
  if(prepared?.blocked)return{...valid[0],compatBlocked:prepared.reason,compatibility:prepared.compatibility||null};
  if(prepared){lastCompatibility=window.FFCAICompat.audit(prepared.compatibility);return prepared;}
 }
 if(valid.length===1)return{...valid[0],plan:{...valid[0].plan,connectomeCandidates:1}};
 const rows=valid.map((option,i)=>({tool:option.proposal.steps[0].tool,excitation:1,confidence:option.proposal.confidence??1,proposalIndex:i}));
 const pick=selectThroughConnectome(rows,ctx);
 if(!pick)return valid[0]; // Execution below still requires an active output.
 const selected=valid[pick.proposalIndex];
 return{...selected,plan:{...selected.plan,connectome:pick.connectome,connectomeCandidates:rows.length}};
}

function finalText(tool,value,status,patch){
 const patchNote=patch&&patch.changed&&patch.changed.length?' / IR '+patch.scope+' patch '+patch.changed.length:'';
 if(status===C.Status.BLOCKED)return'代理人が実行をブロックしました。'+patchNote;
 if(status===C.Status.WAITING)return'共有身体が使用中のため待機しています。'+patchNote;
 if(status===C.Status.FAILED)return'生成または検証に失敗しました。'+patchNote;
 if(tool==='content.understand')return String(value?.summary||'本文から回答を抽出できませんでした。');
 if(tool==='chat.compose')return String(value?.reply||'');
 if(tool==='compute.math')return '計算結果: '+String(value);
 if(tool==='o3.generate')return value?.exported?.ok?'MP4を保存しています。動画パネルで進捗と共有を確認できます。':'MP4生成を開始できませんでした: '+String(value?.exported?.error||'利用できません');
 if(/^code\./.test(tool))return'コード処理を完了しました。'+(value&&value.validation?' validation='+(value.validation.pass?'PASS':'CHECK'):'')+patchNote;
 if(/^image\./.test(tool))return'画像処理を完了しました。'+(value&&value.validation?' validation='+(value.validation.pass?'PASS':'CHECK'):'')+patchNote;
 if(/^document\./.test(tool))return String(value&&value.format||'DOCUMENT').toUpperCase()+'資料を作成しました。'+(value&&value.validation?' validation='+(value.validation.pass?'PASS':'CHECK'):'')+patchNote;
 if(/^voice\./.test(tool))return'音声ツールを実行しました。'+patchNote;
 return'ツールを実行しました。'+patchNote;
}
function execute(text,ctx){
 ctx=ctx||{};const pp=proposal(text,ctx);if(!pp.handled)return{handled:false,plan:pp.plan};
 if(pp.compatBlocked)return{handled:true,status:C.Status.BLOCKED,error:pp.compatBlocked,plan:pp.plan,compatibility:pp.compatibility,finalText:'コネクトームの処理経路を選択できませんでした。'};
 const goal={id:id(),text:String(text||''),threadId:ctx.threadCode||null,priority:+ctx.priority||50,status:C.Status.QUEUED,createdAt:now(),updatedAt:now(),attempts:0};
 state.goals.push(goal);save();
 const verdict=executive.evaluate(pp.proposal,spec);
 if(!verdict.accepted){goal.status=C.Status.BLOCKED;goal.lastError=verdict.reason;goal.updatedAt=now();emit('blocked',verdict.reason,{goalId:goal.id,tool:pp.plan?.tool});return{handled:true,status:goal.status,tool:pp.plan?.tool,plan:pp.plan,patch:pp.patch,finalText:'代理人が '+verdict.reason+' のため実行をブロックしました。'}}
 goal.status=C.Status.RUNNING;goal.attempts++;goal.updatedAt=now();state.lastThread=ctx.threadCode||'__system__';save();
 const results=[],rewards=[];let failed=null,last=null;
 for(let i=0;i<pp.proposal.steps.length;i++){
  const step=pp.proposal.steps[i],resource=C.resourceForTool(step.tool),owner=ctx.threadCode||'__system__',lease=arbiter.acquire(resource,owner);
  if(!lease){failed={step,error:'resource busy: '+resource,status:C.Status.WAITING};break}
  const selected=selectThroughConnectome([{tool:step.tool,excitation:1,confidence:pp.proposal.confidence??1}],ctx,true);
  if(!selected){arbiter.release(lease);failed={step,error:state.connectomeError||'no active connectome output',status:C.Status.BLOCKED};break}
  const args={...(step.args||{})};
  if(ctx.threadCode!=null)args.threadCode=ctx.threadCode;
  if(ctx.context!=null&&args.context==null)args.context=String(ctx.context||'');
  let r;
  try{r=Agent.execute(step.tool,args)}
  catch(e){r={ok:false,error:String(e&&e.message||e)}}
  finally{try{arbiter.release(lease)}catch(e){}}
  const ev=feedbackEvent(step.tool,r,ctx),reward=ev?ev.reward*ev.learningScale:(r&&r.ok ? .2 : -.6);
  rewards.push(Number(reward)||0);results.push({tool:step.tool,result:r,reward,connectome:selected.connectome});last={step,r};
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
 goal.updatedAt=now();state.goals=trimGoals(state.goals);save();
 const tool=last?.step?.tool||failed?.step?.tool||pp.plan?.tool,value=last?.r?.value;
 return{handled:true,status:goal.status,tool,plan:pp.plan,patch:pp.patch,value,error:failed?.error||null,steps:results,learnedSkill:pp.learnedSkill||null,compatibility:pp.compatibility||null,finalText:finalText(tool,value,goal.status,pp.patch)};
}
async function executeAsync(text,ctx){
 ctx=ctx||{};const pp=proposal(text,ctx);if(!pp.handled)return{handled:false,plan:pp.plan};
 if(pp.compatBlocked)return{handled:true,status:C.Status.BLOCKED,error:pp.compatBlocked,plan:pp.plan,compatibility:pp.compatibility,finalText:'コネクトームの処理経路を選択できませんでした。'};
 const goal={id:id(),text:String(text||''),threadId:ctx.threadCode||null,priority:+ctx.priority||50,status:C.Status.QUEUED,createdAt:now(),updatedAt:now(),attempts:0};
 state.goals.push(goal);save();
 const verdict=executive.evaluate(pp.proposal,spec);
 if(!verdict.accepted){goal.status=C.Status.BLOCKED;goal.lastError=verdict.reason;goal.updatedAt=now();emit('blocked',verdict.reason,{goalId:goal.id,tool:pp.plan?.tool});return{handled:true,status:goal.status,tool:pp.plan?.tool,plan:pp.plan,patch:pp.patch,finalText:'代理人が '+verdict.reason+' のため実行をブロックしました。'}}
 goal.status=C.Status.RUNNING;goal.attempts++;goal.updatedAt=now();state.lastThread=ctx.threadCode||'__system__';save();
 const results=[],rewards=[];let failed=null,last=null;
 for(let i=0;i<pp.proposal.steps.length;i++){
  const step=pp.proposal.steps[i],resource=(window.FFCFlyParallel?.supported(step.tool)||step.tool==='chat.compose')?'compute:'+String(ctx.threadCode||'__system__'):C.resourceForTool(step.tool),owner=ctx.threadCode||'__system__',lease=arbiter.acquire(resource,owner);
  if(!lease){failed={step,error:'resource busy: '+resource,status:C.Status.WAITING};break}
  const selected=selectThroughConnectome([{tool:step.tool,excitation:1,confidence:pp.proposal.confidence??1}],ctx,true);
  if(!selected){arbiter.release(lease);failed={step,error:state.connectomeError||'no active connectome output',status:C.Status.BLOCKED};break}
  const args={...(step.args||{})};
  if(ctx.threadCode!=null)args.threadCode=ctx.threadCode;
  if(ctx.context!=null&&args.context==null)args.context=String(ctx.context||'');
  let r;
  try{r=await (window.FFCFlyParallel?FFCFlyParallel.execute(step.tool,args):Agent.execute(step.tool,args))}
  catch(e){r={ok:false,error:String(e&&e.message||e)}}
  finally{try{arbiter.release(lease)}catch(e){}}
  const ev=feedbackEvent(step.tool,r,ctx),reward=ev?ev.reward*ev.learningScale:(r&&r.ok ? .2 : -.6);
  rewards.push(Number(reward)||0);results.push({tool:step.tool,result:r,reward,connectome:selected.connectome});last={step,r};
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
 goal.updatedAt=now();state.goals=trimGoals(state.goals);save();
 const tool=last?.step?.tool||failed?.step?.tool||pp.plan?.tool,value=last?.r?.value;
 return{handled:true,status:goal.status,tool,plan:pp.plan,patch:pp.patch,value,error:failed?.error||null,steps:results,learnedSkill:pp.learnedSkill||null,compatibility:pp.compatibility||null,finalText:finalText(tool,value,goal.status,pp.patch)};
}
async function request(input){
 const api=window.FFCAICompat;if(!api)return{schema:1,status:'unsupported',choices:[],error:{code:'compatibility_unavailable',message:'compatibility module unavailable'}};
 let parsed;try{parsed=api.normalizeRequest(input)}catch(e){return{object:'banc888.compat.response',schema:1,status:'unsupported',choices:[],error:{code:e.code||'invalid_request',message:String(e.message||e)}}}
 try{return api.response(await executeAsync(parsed.text,parsed.ctx))}catch(e){return{object:'banc888.compat.response',schema:1,status:'failed',choices:[],error:{code:'execution_failed',message:String(e.message||e)}}}
}
function retry(goalId){const g=state.goals.find(x=>x.id===goalId);if(!g||![C.Status.BLOCKED,C.Status.FAILED,C.Status.WAITING].includes(g.status))return false;g.status=C.Status.QUEUED;g.lastError=null;g.updatedAt=now();save();return true}
function cancel(goalId){const g=state.goals.find(x=>x.id===goalId);if(!g||[C.Status.DONE,C.Status.CANCELLED].includes(g.status))return false;g.status=C.Status.CANCELLED;g.updatedAt=now();save();return true}
function status(){return{schema:state.schema,connectome:connectome?connectome.snapshot():null,connectomeError:state.connectomeError||null,connectomeRuntime:connectome?connectome.stats():null,compatibility:lastCompatibility,recovered:!!state.recovered,goals:state.goals.slice(-80),events:state.events.slice(-40),body:arbiter.snapshot(),allowed:[...policy.allowed],cognition:state.lastCognition||null,autonomy:Caps.autonomyStatus?Caps.autonomyStatus():null}}

function weightSpaceRead(opts={}){
 if(!connectome)return{ok:false,error:state.connectomeError||'connectome unavailable'};
 try{return{ok:true,...connectome.weightSpaceRead(opts.lane||'__system__',opts.start??0,opts.limit??64)}}
 catch(e){return{ok:false,error:String(e.message||e)}}
}
function weightSpaceApply(program,opts={}){
 if(!connectome)return{ok:false,error:state.connectomeError||'connectome unavailable'};
 try{
  const result=connectome.weightSpaceApply(program,opts.lane||'__system__',{preview:!!opts.preview});
  if(!opts.preview&&result.changedEdges)save();
  return{ok:true,...result};
 }catch(e){return{ok:false,error:String(e.message||e)}}
}

function cognitionPlan(task,opts={}){
 if(!CG)return{status:'unsupported',reason:'cognition_core_unavailable'};
 try{return CG.plan(task,{stats:state.cognitionStats||{}})}
 catch(e){return{status:'blocked',reason:e.code||'invalid_task',detail:String(e.message||e)}}
}
async function cognitionSolve(task,opts={}){
 if(!CG)return{status:'unsupported',reason:'cognition_core_unavailable'};
 const lane=String(opts.threadCode||'COGNITION').slice(0,256),execution=opts.execute===true,allowEffects=opts.allowSideEffects===true;
 if(!execution)return cognitionPlan(task,opts);
 const adapters={
  authorize:action=>{
   const s=spec(action.tool);
   return !!s&&policy.allows(s.capability)&&(!s.sideEffect||allowEffects);
  },
  select:action=>selectThroughConnectome([{tool:action.tool,excitation:1,confidence:1}],{threadCode:lane},true),
  invoke:async action=>{
   const lease=arbiter.acquire(C.resourceForTool(action.tool),lane);
   if(!lease)return{ok:false,error:'body resource busy'};
   try{
    const args={...action.args,threadCode:lane};
    return await Promise.resolve(Agent.execute(action.tool,args));
   }catch(e){return{ok:false,error:String(e&&e.message||e)}}
   finally{arbiter.release(lease)}
  },
  onOutcome:(action,verified)=>{
   if(!connectome)return;
   try{
    if(verified==null)connectome.discard(lane);
    else connectome.reinforce(action.tool,verified?.4:-.6,{learnable:true,tier:'training',lane});
   }catch(e){state.connectomeError=String(e.message||e)}
  }
 };
 try{
  const result=await CG.solve(task,adapters,{execute:true,stats:state.cognitionStats||{},maxSteps:opts.maxSteps});
  // Store only aggregate operation statistics; no raw user task or observations.
  state.cognitionStats=Object.fromEntries(Object.entries(result.stats||{}).slice(-128));
  state.lastCognition={status:result.status,reason:result.reason||null,steps:result.trace?.length||0,verification:result.verification||null,time:now()};
  save();
  return result;
 }catch(e){return{status:'blocked',reason:e.code||'cognition_error',detail:String(e.message||e),executed:false}}
}

// Unified four-domain interface for the ordinary conversation surface.
// Use the same BANC circuit, tool permission and verified-observation planner.
async function cognitionRequest(text,ctx={}){
 if(!CG||!MM)return executeAsync(text,ctx);
 const requestText=String(text||'').trim();
 if(!requestText||requestText.length>12000)return{handled:true,status:C.Status.BLOCKED,error:'invalid request',finalText:'入力が空、または長すぎます。'};
 let selected,original=null;
 const multi=MM.composite(requestText);
 if(multi.length)selected={modes:multi,source:'explicit_multipart_request'};
 else{
  original=proposal(requestText,ctx);
  if(original?.compatBlocked)return{handled:true,status:C.Status.BLOCKED,error:original.compatBlocked,plan:original.plan,compatibility:original.compatibility,finalText:'コネクトームの処理経路を選択できませんでした。'};
  selected=MM.classify(requestText,original?.proposal?.steps?.[0]?.tool||original?.plan?.tool);
 }
 if(!selected.modes.length)return executeAsync(requestText,ctx);
 let task;
 try{task=MM.task(requestText,selected,{ctx,ir:original?.plan?.ir||original?.proposal?.steps?.[0]?.args?.ir||null})}
 catch(e){return{handled:true,status:C.Status.BLOCKED,error:String(e.message||e),finalText:'認知計画の入力が不正です。'};}
 const preliminary=CG.plan(task,{stats:state.cognitionStats||{}});
 if(preliminary.status!=='planned'&&preliminary.status!=='satisfied_assumed')
  return{handled:true,status:C.Status.BLOCKED,error:preliminary.reason||'planning failed',finalText:'生成計画を作成できませんでした。'};
 const lane=String(ctx.threadCode||'__system__').slice(0,256);
 const produced=[],traceOutput=[];
 const adapters={
  authorize:action=>{
   const s=spec(action.tool);
   // Scope to the four explicitly requested generators only. No other
   // device/network commands can be introduced through a text prompt.
   return !!s&&policy.allows(s.capability)&&MM.kind(action.tool)!==null;
  },
  select:action=>selectThroughConnectome([{tool:action.tool,excitation:1,confidence:1}],{threadCode:lane},true),
  invoke:async action=>{
   const resource=(window.FFCFlyParallel?.supported?.(action.tool)||action.tool==='chat.compose')?'compute:'+lane:C.resourceForTool(action.tool);
   const lease=arbiter.acquire(resource,lane);
   if(!lease)return{ok:false,error:'resource busy: '+resource};
   let raw;
   try{
    const args={...action.args,threadCode:lane};
    raw=await (window.FFCFlyParallel?.execute?window.FFCFlyParallel.execute(action.tool,args):Promise.resolve(Agent.execute(action.tool,args)));
    const checked=await MM.finish(action.tool,raw,{videoExport:window.FFCVideoExport,a1111Video:window.FFCA1111Video});
    const found={mode:MM.kind(action.tool),tool:action.tool,verification:checked.audit,value:checked.result?.value||null};
    produced.push(found);
    if(!checked.audit.pass)return{ok:false,error:checked.audit.code+': '+checked.audit.reason};
    return{ok:true,observedFacts:{[found.mode]:'done'},value:checked.result?.value};
   }catch(e){produced.push({mode:MM.kind(action.tool),tool:action.tool,verification:{pass:false,reason:String(e.message||e)}});return{ok:false,error:String(e.message||e)}}
   finally{arbiter.release(lease)}
  },
  onOutcome:(action,verified)=>{
   try{
    if(!connectome)return;
    if(verified==null)connectome.discard(lane);
    else connectome.reinforce(action.tool,verified?.4:-.6,{learnable:true,tier:'training',lane});
    const s=Caps.autonomy;
    if(verified!==null&&s?.observeTool)s.observeTool(action.tool,{ok:!!verified,reward:verified?.4:-.6,learnable:true});
    if(verified!==null&&Caps.persistAutonomy)Caps.persistAutonomy();
   }catch(e){state.connectomeError=String(e.message||e)}
  }
 };
 let solved;
 try{solved=await CG.solve(task,adapters,{execute:true,stats:state.cognitionStats||{},maxSteps:Math.max(4,selected.modes.length*2)})}
 catch(e){return{handled:true,status:C.Status.FAILED,error:String(e.message||e),finalText:'認知実行に失敗しました。'};}
 state.cognitionStats=Object.fromEntries(Object.entries(solved.stats||{}).slice(-128));
 const allVerified=selected.modes.every(m=>produced.some(x=>x.mode===m&&x.verification?.pass));
 const status=solved.status==='done'&&allVerified?C.Status.DONE:C.Status.FAILED;
 state.lastCognition={status,reason:solved.reason||null,modes:[...selected.modes],steps:solved.trace?.length||0,verification:'tool_reported_artifact_checks',time:now()};
 save();
 const last=produced[produced.length-1]||null;
 const report=MM.report(produced,solved.trace||[]);
 return{handled:true,status,tool:last?.tool||null,plan:original?.plan||{domain:selected.modes.join('+'),source:selected.source},value:last?.value||null,
   outputs:produced,steps:produced.map(p=>({tool:p.tool,result:{ok:!!p.verification?.pass,value:p.value},audit:p.verification})),
   evidence:{status:solved.status,reason:solved.reason||null,trace:solved.trace,verification:'tool_reported_artifact_checks'},
   error:status===C.Status.DONE?null:solved.reason||produced.find(x=>!x.verification?.pass)?.verification?.reason||'unverified outcome',
   finalText:report};
}
function cognitionCapabilities(){
 return{schema:1,domains:['chat','code','image','video'].map(mode=>({mode,tool:MM?.TOOL?.[mode],available:!!spec(MM?.TOOL?.[mode]),verification:mode==='chat'?'nonempty-reply':mode==='code'?'static-review':mode==='image'?'procedural-scene-quality':'encoded-MP4'})),engine:CG?.VERSION||null,scope:'tool-reported evidence; no AGI or independent truth checking'};
}
function patchIr(ir,ops,scope){return P.apply(ir,ops,scope)}
window.FFC_PROXY_AGENT={version:'2.2-compat',execute,executeAsync,request,proposal,status,retry,cancel,patchIr,weightSpaceRead,weightSpaceApply,cognitionPlan,cognitionSolve,cognitionRequest,cognitionCapabilities,policy,body:arbiter,executive};
setTimeout(()=>{const b=document.getElementById('ffcCapabilityBar');if(b&&!document.getElementById('ffcProxyBadge')){const x=document.createElement('span');x.id='ffcProxyBadge';x.textContent='🪰 PROXY EXEC + IR PATCH';b.appendChild(x)}},200);
if(state.recovered)emit('recovery','checkpoint restored from backup');
})();
