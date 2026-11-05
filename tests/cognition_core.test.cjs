'use strict';
const assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const CG=require('../app/src/main/assets/cognition-core.js');
const C=require('../app/src/main/assets/proxy-agent-core.js');
const {plan,infer,solve}=CG;

function task(actions,goal={result:'ready'},facts={result:'absent'},rules=[]){
 return{schema:1,facts,goal,actions,rules,maxStates:400,maxDepth:8};
}
function op(id,requires,effects,cost=1,tool='tool.run'){
 return{id,tool,requires,effects,cost,args:{label:id}};
}
const chain=task([
 op('prepare',{result:'absent'},{result:'draft'}),
 op('check',{result:'draft'},{result:'ready'})
]);
const p=plan(chain);
assert.equal(p.status,'planned');assert.deepEqual(p.steps.map(x=>x.id),['prepare','check']);
assert.equal(p.steps.length,2);
assert.equal(plan({...chain,maxDepth:1}).status,'blocked');
assert.equal(plan({...chain,maxStates:1}).status,'blocked');
assert.equal(plan({...chain,facts:{result:'ready'}}).status,'satisfied_assumed');

const logic=task([
 op('verify',{identity:'known'},{verified:true}),
 op('pay',{verified:true},{paid:true})
],{eligible:true},{identity:'known'},[
 {id:'eligibility',if:{verified:true,paid:true},then:{eligible:true}}
]);
assert.equal(plan(logic).status,'planned');
assert.deepEqual(plan(logic).steps.map(s=>s.id),['verify','pay']);
const inferred=infer({...logic,facts:{identity:'known',verified:true,paid:true}});
assert.equal(inferred.status,'inferred');assert.equal(inferred.facts.eligible,true);
assert.equal(inferred.proofs[0].rule,'eligibility');

const conflicting=task([],{eligible:true},{paid:true},[
 {if:{paid:true},then:{eligible:true}},{if:{paid:true},then:{eligible:false}}
]);
assert.equal(plan(conflicting).reason,'inference_conflict');
const unreachable=task([op('impossible',{never:true},{result:'ready'})]);
assert.equal(plan(unreachable).reason,'no_valid_plan');
assert.throws(()=>plan(task([op('duplicate',{}, {result:'draft'}),op('duplicate',{}, {result:'ready'})])),/unique action/);
assert.throws(()=>plan({schema:1,facts:JSON.parse('{"__proto__":1}'),goal:{x:1},actions:[]}),/key\/value/);
assert.throws(()=>plan({...chain,actions:[op('x',{}, {result:'ready'},-1)]}),/cost/);
assert.throws(()=>plan({...chain,actions:[{id:'x',tool:'tool.run',requires:{},effects:{result:'ready'},args:JSON.parse('{"constructor":42}') }]}),/invalid argument/);
const snapshot=JSON.stringify(chain);
plan(chain);assert.equal(JSON.stringify(chain),snapshot,'planning must not modify its input');

const graph={source:'synthetic cognition fixture',nodes:['s','m'],edges:[{from:'s',to:'m',count:4,pre_top_nt:'acetylcholine'}],routes:[{id:'r',source:'s',target:'m'}],toolRoutes:{'tool.run':'r'}};
function connection(data=graph){const k=new C.ConnectomeMultiplexer(data);return a=>k.evaluate([{tool:a.tool,excitation:1}], 'A')}
const gate=connection();
assert.ok(gate({tool:'tool.run'}));
(async()=>{
 const pre=await solve(chain,{},{});assert.equal(pre.status,'planned');assert.equal(pre.executed,false);
 let calls=0;
 const dummy={authorize:()=>true,select:gate,invoke:a=>{calls++;return{ok:true,value:{observedFacts:{result:a.effects.result}}}}};
 const success=await solve(chain,dummy,{execute:true});
 assert.equal(success.status,'done');assert.equal(success.verification,'tool_reported_observations');assert.equal(calls,2);
 assert.equal(success.trace.every(x=>x.ok),true);
 assert.ok(Object.keys(success.stats).includes('tool.run|prepare'));
 const blind=await solve(chain,{...dummy,invoke:()=>({ok:true,value:{reply:'success'}})},{execute:true});
 assert.equal(blind.status,'blocked');assert.equal(blind.reason,'missing_observation');
 const forbidden=await solve(chain,{...dummy,authorize:()=>false},{execute:true});
 assert.equal(forbidden.reason,'permission_denied');
 let cuts=0;
 const cut=await solve(chain,{...dummy,select:()=>{cuts++;return null}},{execute:true});
 assert.equal(cut.reason,'no_connectome_output');assert.equal(cuts,1);
 let attempts=[];
 const alternate=task([
  op('primary',{result:'absent'},{result:'ready'},1),
  op('secondary',{result:'absent'},{result:'ready'},2)
 ]);
 const repaired=await solve(alternate,{...dummy,invoke:a=>{attempts.push(a.id);return a.id==='primary'?{ok:false,error:'failed'}:{ok:true,observedFacts:{result:'ready'}}}},{execute:true});
 assert.equal(repaired.status,'done');assert.deepEqual(attempts,['primary','secondary']);
 assert.equal(repaired.trace[0].ok,false);assert.equal(repaired.trace[1].ok,true);
 const replan=await solve(alternate,{...dummy,invoke:a=>({ok:true,observedFacts:{check:'failed'}})},{execute:true});
 assert.equal(replan.status,'blocked');assert.equal(replan.reason,'no_valid_plan');

 const store=new Map(),calls=[],names=new Map([['tool.run',{name:'tool.run',capability:'compute',sideEffect:false}],['tool.write',{name:'tool.write',capability:'compute',sideEffect:true}]]);
 const scenario={...graph,toolRoutes:{'tool.run':'r','tool.write':'r'}};
 const context={window:{
  FFCProxyCore:C,FFCIrPatch:{},FFCCapabilityVocabulary:{classify:()=>({handled:false})},
  FFCCognitionCore:CG,FFCFeedback:{fromTest:()=>({reward:0,learningScale:1,learnable:true,tier:'training'}),stimuli:()=>[]},
  BANC888_FLY_AGENT:{connectome:scenario,state:{tools:names,capabilities:new Set(['compute'])},execute:(tool,args)=>{calls.push(tool);return{ok:true,value:{observedFacts:{result:'ready'}}}}},
  FFC_CAPABILITIES:{}
 },localStorage:{getItem:k=>store.get(k)||null,setItem:(k,v)=>store.set(k,v),removeItem:k=>store.delete(k)},
 setTimeout:()=>{},document:{getElementById:()=>null}};
 vm.runInNewContext(fs.readFileSync('app/src/main/assets/proxy-agent.js','utf8'),context);
 const api=context.window.FFC_PROXY_AGENT;
 assert.equal(api.cognitionPlan(chain).status,'planned');
 assert.equal(calls.length,0,'preview must not execute');
 const once=task([op('write',{result:'absent'},{result:'ready'})]);
 const committed=await api.cognitionSolve(once,{execute:true,threadCode:'T1'});
 assert.equal(committed.status,'done');assert.equal(calls.length,1);
 assert.equal(api.status().cognition.status,'done');
 assert.ok(JSON.parse(store.get('FFC_PROXY_CHECKPOINT_V2')).cognitionStats['tool.run|write'].success>=1);
 const sideEffect=task([op('write-side',{result:'absent'},{result:'ready'},1,'tool.write')]);
 const blocked=await api.cognitionSolve(sideEffect,{execute:true,threadCode:'T2'});
 assert.equal(blocked.reason,'permission_denied');assert.equal(calls.length,1,'effectful tools require explicit approval');
 const approved=await api.cognitionSolve(sideEffect,{execute:true,threadCode:'T3',allowSideEffects:true});
 assert.equal(approved.status,'done');assert.equal(calls.length,2);
 const disconnected={...scenario,edges:[]};
 const second={...context,window:{...context.window,BANC888_FLY_AGENT:{...context.window.BANC888_FLY_AGENT,connectome:disconnected}}};
 delete second.window.FFC_PROXY_AGENT;
 vm.runInNewContext(fs.readFileSync('app/src/main/assets/proxy-agent.js','utf8'),second);
 assert.equal((await second.window.FFC_PROXY_AGENT.cognitionSolve(once,{execute:true,threadCode:'F'})).reason,'no_connectome_output');
 console.log('cognition: PASS (multi-step search, rule inference, data validation, observation/replanning, BANC selection, APK policy, persistence)');
})().catch(e=>{console.error(e);process.exitCode=1});
