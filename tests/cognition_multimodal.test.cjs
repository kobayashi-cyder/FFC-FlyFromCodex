'use strict';
const assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const MM=require('../app/src/main/assets/cognition-multimodal-core.js');
const CG=require('../app/src/main/assets/cognition-core.js');
const C=require('../app/src/main/assets/proxy-agent-core.js');
assert.equal(MM.kind('chat.compose'),'chat');
assert.equal(MM.kind('code.debug'),'code');
assert.equal(MM.kind('image.refine'),'image');
assert.equal(MM.kind('o3.generate'),'video');
assert.equal(MM.kind('unknown.tool'),null);
assert.deepEqual(MM.composite('会話、コード、画像、動画を全部生成して'),['chat','code','image','video']);
assert.deepEqual(MM.composite('コードと画像の違いを教えて'),[],'read-only questions must never trigger batch generation');
const task=MM.task('会話、コード、画像、動画を全部生成して',{modes:['chat','code','image','video']});
assert.equal(CG.plan(task).status,'planned');
assert.equal(CG.plan(task).steps.length,4);
assert.throws(()=>MM.task('',{modes:['chat']}),/empty/);
assert.throws(()=>MM.task('x',{modes:['not-allowed']}),/unsupported/);

const chat={ok:true,value:{reply:'こんにちは'}};
const code={ok:true,value:{text:'print("ok")',validation:{pass:true},review:{accepted:true},implementationScope:'completed'}};
const image={ok:true,value:{validation:{pass:true,score:93,scene:{objects:3}},selection:{id:'v1'}}};
const videoInitial={ok:true,value:{exported:{ok:true,status:'encoding',provider:'o3',jobId:'x'},tracks:4}};
const complete={ok:true,status:'complete',bytes:10101,frameCount:12,framesWritten:12};
assert.equal(MM.audit('chat.compose',chat).pass,true);
assert.equal(MM.audit('chat.compose',{ok:true,value:{reply:''}}).pass,false);
assert.equal(MM.audit('code.generate',code).pass,true);
assert.equal(MM.audit('code.generate',{ok:true,value:{...code.value,implementationScope:'unimplemented-scaffold'}}).pass,false);
assert.equal(MM.audit('code.generate',{ok:true,value:{...code.value,text:'// BANC888 generated scaffold: request implementation unavailable'}}).pass,false);
assert.equal(MM.audit('code.generate',{ok:true,value:{...code.value,review:{accepted:false}}}).pass,false);
assert.equal(MM.audit('image.generate',image).pass,true);
assert.equal(MM.audit('image.generate',{ok:true,value:{validation:{pass:true}}}).pass,false);
assert.equal(MM.audit('o3.generate',videoInitial).code,'mp4_not_complete');
(async()=>{
 let waited=0;
 const finalized=await MM.finish('o3.generate',videoInitial,{videoExport:{whenComplete:async()=>{waited++;return complete}}});
 assert.equal(finalized.audit.pass,true);assert.equal(waited,1);
 assert.equal(finalized.result.value.exported.bytes,10101);
 assert.equal((await MM.finish('o3.generate',videoInitial,{videoExport:{whenComplete:async()=>({...complete,framesWritten:9})}})).audit.code,'incomplete_mp4');
 assert.equal((await MM.finish('o3.generate',videoInitial,{})).audit.code,'missing_video_exporter');
 const failure=await MM.finish('o3.generate',videoInitial,{videoExport:{whenComplete:async()=>{throw Error('no encoder')}}});
 assert.equal(failure.audit.code,'video_export_failed');

 const graph={source:'synthetic circuit',nodes:['s','t'],edges:[{from:'s',to:'t',count:4,pre_top_nt:'acetylcholine'}],routes:[{id:'o1p0',source:'s',target:'t'},{id:'o1p3',source:'s',target:'t'}]};
 const tools=new Map([
  ['chat.compose',{capability:'compute',sideEffect:false}],
  ['code.generate',{capability:'code.write',sideEffect:true}],
  ['image.generate',{capability:'image.write',sideEffect:true}],
  ['o3.generate',{capability:'o3.write',sideEffect:true}]
 ]);
 const store=new Map(),calls=[],results={'chat.compose':chat,'code.generate':code,'image.generate':image,'o3.generate':videoInitial};
 const v={classify:input=>{
  const type=/コード/.test(input)?'code':/画像/.test(input)?'image':/動画/.test(input)?'video':'chat';
  const tool=MM.TOOL[type];
  return{handled:type!=='chat',tool,domain:type,confidence:.95,ir:{kind:type==='code'?'CodeIR':type==='image'?'ImageIR':'none',quality:'normal',action:'generate'}};
 }};
 const env={window:{
  FFCProxyCore:C,FFCIrPatch:{},FFCCapabilityVocabulary:v,FFCCognitionCore:CG,FFCCognitionMultimodal:MM,
  FFCFeedback:{fromTest:()=>({reward:0,learningScale:0,learnable:false,tier:'holdout'}),stimuli:()=>[]},
  BANC888_FLY_AGENT:{connectome:graph,state:{tools,capabilities:new Set(['compute','code.write','image.write','o3.write'])},execute:(tool)=>{calls.push(tool);return results[tool]||{ok:false,error:'not found'}}},
  FFC_CAPABILITIES:{},
  FFCVideoExport:{whenComplete:async()=>complete}
 },localStorage:{getItem:k=>store.get(k)||null,setItem:(k,v)=>store.set(k,v),removeItem:k=>store.delete(k)},
 setTimeout:()=>{},document:{getElementById:()=>null}};
 vm.runInNewContext(fs.readFileSync('app/src/main/assets/proxy-agent.js','utf8'),env);
 const api=env.window.FFC_PROXY_AGENT;
 assert.equal(typeof api.cognitionRequest,'function');
 assert.equal(api.cognitionCapabilities().domains.length,4);
 let r=await api.cognitionRequest('こんにちは',{threadCode:'A'});
 assert.equal(r.status,'done');assert.ok(r.finalText.includes('こんにちは'));assert.equal(r.outputs[0].verification.level,'nonempty_reply_only');
 r=await api.cognitionRequest('Pythonコードを生成して',{threadCode:'B'});
 assert.equal(r.status,'done');assert.ok(r.finalText.includes('print("ok")'));
 r=await api.cognitionRequest('猫の画像を生成して',{threadCode:'C'});
 assert.equal(r.status,'done');assert.equal(r.outputs[0].verification.level,'procedural_render_quality');
 r=await api.cognitionRequest('猫の動画を生成して',{threadCode:'D'});
 assert.equal(r.status,'done');assert.equal(r.outputs[0].value.exported.status,'complete');
 r=await api.cognitionRequest('会話とコードと画像と動画を全部生成して',{threadCode:'E'});
 assert.equal(r.status,'done');assert.equal(r.outputs.length,4);
 assert.deepEqual(calls,['chat.compose','code.generate','image.generate','o3.generate','chat.compose','code.generate','image.generate','o3.generate']);
 assert.ok(api.status().cognition.modes.length===4);
 assert.ok(JSON.parse(store.get('FFC_PROXY_CHECKPOINT_V2')).cognitionStats['chat.compose|deliver-chat']);
 results['code.generate']={ok:true,value:{text:'// BANC888 generated scaffold: request implementation unavailable',validation:{pass:true},review:{accepted:true},implementationScope:'scaffold'}};
 const before=calls.length;
 r=await api.cognitionRequest('Pythonコードを生成して',{threadCode:'F'});
 assert.equal(r.status,'failed');
 assert.ok(r.finalText.includes('未完了'));
 assert.equal(calls.length,before+1,'no fake retry with same unsupported output');
 results['o3.generate']={ok:true,value:{exported:{ok:false,status:'failed',error:'encoder unavailable'}}};
 r=await api.cognitionRequest('犬の動画を生成して',{threadCode:'G'});
 assert.equal(r.status,'failed');assert.ok(r.finalText.includes('未完了'));
 const fresh={...env.window,FFCProxyCore:C,BANC888_FLY_AGENT:{...env.window.BANC888_FLY_AGENT,connectome:{...graph,edges:[]}}};
 delete fresh.FFC_PROXY_AGENT;
 const cutEnv={...env,window:fresh,localStorage:{getItem:()=>null,setItem:()=>{},removeItem:()=>{}}};
 vm.runInNewContext(fs.readFileSync('app/src/main/assets/proxy-agent.js','utf8'),cutEnv);
 r=await cutEnv.window.FFC_PROXY_AGENT.cognitionRequest('こんにちは',{threadCode:'Z'});
 assert.notEqual(r.status,'done');assert.equal(calls.at(-1),'o3.generate','disconnect prevents actual tool invocation');
 console.log('cognition-multimodal: PASS (four modes, verified render completion, no scaffold success, no false MP4, lesion, planner integration)');
})().catch(e=>{console.error(e);process.exitCode=1});
