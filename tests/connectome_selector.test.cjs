const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');
const C=require('../app/src/main/assets/proxy-agent-core.js');
const html=fs.readFileSync('app/src/main/assets/index.html','utf8');
for(const script of html.matchAll(/<script\b[^>]*>([\s\S]*?)<\/script>/gi))if(script[1].trim())new vm.Script(script[1]);
const csv=html.match(/const RAW=\{[\s\S]*?edges:`([\s\S]*?)`/)[1];
const edges=csv.trim().split('\n').slice(1).map(line=>{const [from,to,count,...nt]=line.split(',');return{from,to,count:+count,pre_top_nt:nt.join(',').replaceAll('"','')}});
const routes=vm.runInNewContext(html.match(/const O1P_SHORTCUTS=(\[[\s\S]*?\]);/)[1]);
const data={nodes:[...new Set(edges.flatMap(e=>[e.from,e.to]))],edges,routes,source:'embedded BANC Figure 3g',toolRoutes:{'compute.math':'o1p1','chat.compose':'o1p3','micro.compile':'o1p0'}};
const kernel=new C.ConnectomeSelector(data);
const candidates=[{tool:'chat.compose',excitation:1.1},{tool:'micro.compile',excitation:.8}];
const pick=kernel.select(candidates);
assert.equal(pick.tool,'micro.compile'); // Lower input score, stronger actual graph output.
assert.equal(pick.connectome.trace.length,6);
assert.equal(pick.connectome.granularity,'display_name aggregate');
assert.ok(pick.connectome.signedOutput<0); // Inhibitory output is retained in trace.
assert.equal(new C.ConnectomeSelector({...data,edges:[]}).select(candidates),null);
const cut=new C.ConnectomeSelector({...data,edges:edges.filter(e=>e.to!=='MNad21')});
assert.equal(cut.select(candidates).tool,'chat.compose');
assert.deepEqual(new C.ConnectomeSelector(data).select(candidates),pick);
assert.equal(kernel.select([{tool:'micro.compile',excitation:NaN}]),null);
assert.throws(()=>new C.ConnectomeSelector({...data,edges:[{from:'missing',to:'MNad21',count:1}]}));
kernel.select(candidates);const before=kernel.snapshot();
assert.equal(kernel.reinforce('micro.compile',1,{tier:'holdout'}),false);
assert.deepEqual(kernel.snapshot(),before);
assert.equal(kernel.reinforce('chat.compose',1),false);
assert.equal(kernel.reinforce('micro.compile',1),true);
assert.ok(Object.keys(kernel.snapshot().factors).length);
assert.equal(kernel.reinforce('micro.compile',1),false); // Consume credit once.
const restored=new C.ConnectomeSelector(data);restored.restore(kernel.snapshot());
assert.deepEqual(restored.snapshot(),kernel.snapshot());
assert.deepEqual(restored.select(candidates),kernel.select(candidates));
for(let i=0;i<150;i++){kernel.select(candidates);kernel.reinforce('micro.compile',100)}
assert.ok(Object.values(kernel.snapshot().factors).every(f=>f>=.75&&f<=1.25));

// Exercise the actual APK proxy, including learned/specialist paths and policy.
function proxy(topology,kind='specialist'){
 const calls=[],store=new Map();
 const tools=new Map(['code.generate','chat.compose','micro.compile'].map(name=>[name,{name,capability:name==='code.generate'?'code.write':'compute'}]));
 const context={window:{FFCProxyCore:C,FFCIrPatch:{},FFCCapabilityVocabulary:{classify:()=>kind==='specialist'?{handled:true,tool:'code.generate',domain:'code',confidence:.9}:{handled:false}},FFCFeedback:{fromTest:()=>({reward:.2,learningScale:1,learnable:true,tier:'training'}),stimuli:()=>[]},BANC888_FLY_AGENT:{connectome:topology,state:{tools,capabilities:new Set(['compute'])},buildCandidates:()=>({candidates}),execute:(tool,args)=>{calls.push(tool);return{ok:true,value:{}}}},FFC_CAPABILITIES:{autonomy:kind==='learned'?{matchSkill:()=>({id:'skill',proposal:{confidence:.9,steps:[{tool:'code.generate'}]}})}:null}},localStorage:{getItem:k=>store.get(k)||null,setItem:(k,v)=>store.set(k,v),removeItem:k=>store.delete(k)},setTimeout:()=>{},document:{getElementById:()=>null}};
 vm.runInNewContext(fs.readFileSync('app/src/main/assets/proxy-agent.js','utf8'),context);
 return{api:context.window.FFC_PROXY_AGENT,calls};
}
for(const kind of ['specialist','learned','base']){
 const good=proxy(data,kind),result=good.api.execute('test',{threadCode:'A'});
 assert.equal(result.status,C.Status.DONE,kind);assert.ok(result.steps[0].connectome.trace.length);
 const dead=proxy({...data,edges:[]},kind),blocked=dead.api.execute('test',{threadCode:'A'});
 if(kind==='base')assert.equal(blocked.handled,false);else assert.equal(blocked.status,C.Status.BLOCKED);
 assert.ok(!dead.calls.some(x=>x==='code.generate'||x==='micro.compile'));
}
const missing=proxy(null);assert.equal(missing.api.execute('test').status,C.Status.BLOCKED);
console.log('connectome-selector: PASS (real aggregate topology, ablation, plasticity, APK execution)');
