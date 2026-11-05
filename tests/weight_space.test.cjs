'use strict';
const assert=require('node:assert/strict');
const C=require('../app/src/main/assets/proxy-agent-core.js');
const graph={
 source:'synthetic weight-space fixture',
 nodes:['input','relay','outA','outB'],
 edges:[
  {from:'input',to:'relay',count:16,pre_top_nt:'acetylcholine'},
  {from:'relay',to:'outA',count:9,pre_top_nt:'acetylcholine'},
  {from:'input',to:'outB',count:4,pre_top_nt:'acetylcholine'}
 ],
 routes:[{id:'ra',source:'input',target:'outA'},{id:'rb',source:'input',target:'outB'}],
 toolRoutes:{a:'ra',b:'rb'}
};
const rows=[{tool:'a',excitation:1},{tool:'b',excitation:1}];
const m=new C.ConnectomeMultiplexer(graph,{maxLanes:4});
const catalog=m.weightSpaceRead('A',0,20);
assert.equal(catalog.nodeCount,4);
assert.equal(catalog.edgeCount,3);
assert.equal(catalog.rows.length,3);
assert.equal(catalog.rows[0].gain,1);
const graphIdentity=catalog.graphIdentity;
const op=(ops)=>({schema:1,graphIdentity,ops});
const initial=JSON.stringify(m.snapshot());
const preview=m.weightSpaceApply(op([{op:'set',edges:[0],value:1.5},{op:'ablate',edges:[1]}]),'A',{preview:true});
assert.equal(preview.changedEdges,2);
assert.equal(JSON.stringify(m.snapshot()),initial,'preview must not change state');
assert.throws(()=>m.weightSpaceApply(op([{op:'set',edges:[0],value:1.5},{op:'set',edges:[1],value:2.5}]),'A'));
assert.equal(m.weightSpaceRead('A').rows[0].gain,1,'invalid transaction changes nothing');
assert.throws(()=>m.weightSpaceApply({...op([{op:'reset',edges:'all'}]),graphIdentity:'wrong'},'A'));
assert.throws(()=>m.weightSpaceApply(op([{op:'set',edges:[999],value:1.1}]),'A'));
assert.throws(()=>m.weightSpaceApply(op([{op:'compute',edges:'all',value:1}]),'A'));
const applied=m.weightSpaceApply(op([{op:'set',edges:[0],value:1.5},{op:'ablate',edges:[1]}]),'A');
assert.equal(applied.changedEdges,2);
assert.equal(m.weightSpaceRead('A').rows[0].gain,1.5);
assert.equal(m.weightSpaceRead('A').rows[1].gain,0);
assert.equal(m.weightSpaceRead('B').rows[0].gain,1,'thread weight-space isolation');
assert.equal(m.select(rows,'A').tool,'b','lesion removes the A output path');
assert.throws(()=>m.weightSpaceApply(op([{op:'reset',edges:'all'}]),'A'),/pending reward credit/);
assert.ok(m.discard('A'));
const snap=m.snapshot();
const restored=new C.ConnectomeMultiplexer(graph);
restored.restore(snap);
assert.equal(restored.weightSpaceRead('A').rows[0].gain,1.5);
assert.equal(restored.weightSpaceRead('A').rows[1].gain,0);
assert.equal(restored.select(rows,'A').tool,'b','restored lesion must remain effective');
restored.discard('A');
const reset=restored.weightSpaceApply(op([{op:'reset',edges:'all'}]),'A');
assert.equal(reset.changedEdges,2);
assert.ok(restored.weightSpaceRead('A').rows.every(x=>x.gain===1));
assert.throws(()=>restored.weightSpaceApply(op([{op:'scale',edges:'all',value:-1}]),'A'));
const bulk=restored.weightSpaceApply(op([{op:'scale',edges:'all',value:1.2},{op:'add',edges:[2],value:.1}]),'A');
assert.equal(bulk.changedEdges,3);
assert.equal(restored.weightSpaceRead('A').rows[2].gain,1.3);
const others=new C.ConnectomeMultiplexer({...graph,source:'different graph'});
others.restore(snap);
assert.equal(others.weightSpaceRead('A').rows[0].gain,1,'must reject another graph state');
// User-facing APK proxy integration, not only an isolated numerical helper.
const fs=require('node:fs'),vm=require('node:vm');
const state=new Map(),calls=[],tools=new Map(['a','b'].map(name=>[name,{name,capability:'compute'}]));
const context={window:{
 FFCProxyCore:C,FFCIrPatch:{},FFCCapabilityVocabulary:{classify:()=>({handled:true,tool:'a',confidence:.9})},
 FFCFeedback:{fromTest:()=>({reward:0,learningScale:1,learnable:true}),stimuli:()=>[]},
 BANC888_FLY_AGENT:{connectome:graph,state:{tools,capabilities:new Set(['compute'])},execute:(tool)=>{calls.push(tool);return{ok:true,value:'ok'}}},
 FFC_CAPABILITIES:{}
},localStorage:{getItem:key=>state.get(key)||null,setItem:(k,v)=>state.set(k,v),removeItem:k=>state.delete(k)},
 setTimeout:()=>{},document:{getElementById:()=>null}};
vm.runInNewContext(fs.readFileSync('app/src/main/assets/proxy-agent.js','utf8'),context);
const api=context.window.FFC_PROXY_AGENT;
assert.equal(typeof api.weightSpaceRead,'function');
assert.equal(typeof api.weightSpaceApply,'function');
const read=api.weightSpaceRead({lane:'A'});
assert.equal(read.ok,true);assert.equal(read.edgeCount,3);
const edit=api.weightSpaceApply({schema:1,graphIdentity:read.graphIdentity,ops:[{op:'ablate',edges:'all'}]},{lane:'A'});
assert.equal(edit.ok,true);assert.equal(edit.changedEdges,3);
assert.equal(api.execute('test',{threadCode:'A'}).status,'blocked','zero signal must not execute a tool');
assert.equal(calls.length,0);
assert.equal(JSON.parse(state.get('FFC_PROXY_CHECKPOINT_V2')).connectome.lanes[0].factors['0'],0,'persist zero-weight program');
console.log('weight-space: PASS (atomic programming, full-edge bulk operations, preview, integrity, cross-lane isolation, persistence, APK execution gate)');
