const assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const C=require('../app/src/main/assets/proxy-agent-core.js'),V=require('../app/src/main/assets/capability-vocabulary-core.js'),AC=require('../app/src/main/assets/autonomy-core.js'),FB=require('../app/src/main/assets/feedback-core.js');
const html=fs.readFileSync('app/src/main/assets/index.html','utf8');
const csv=html.match(/const RAW=\{[\s\S]*?edges:`([\s\S]*?)`/)[1];
const edges=csv.trim().split('\n').slice(1).map(line=>{const [from,to,count,...nt]=line.split(',');return{from,to,count:+count,pre_top_nt:nt.join(',').replaceAll('"','')}});
const routes=vm.runInNewContext(html.match(/const O1P_SHORTCUTS=(\[[\s\S]*?\]);/)[1]);
const data={nodes:[...new Set(edges.flatMap(e=>[e.from,e.to]))],edges,routes,toolRoutes:{},source:'embedded BANC aggregate'};
function runtime(topology){
 const store=new Map(),tools=new Map(),kernel=new C.ConnectomeMultiplexer(topology);
 const Agent={state:{tools,capabilities:new Set(),connectomeSelector:kernel},execute:(tool,args)=>{
  const t=tools.get(tool);if(t){try{return{ok:true,value:t.handler(args||{})}}catch(e){return{ok:false,error:e.message}}}
  return{ok:true,value:{reply:'No generated code from this test body'}};
 }};
 const context={window:{FFCCapabilityVocabulary:V,FFCImageQuality:{},FFCFeedback:FB,FFCAutonomyCore:AC,BANC888_FLY_AGENT:Agent},localStorage:{getItem:k=>store.get(k)||null,setItem:(k,v)=>store.set(k,v)},document:{readyState:'complete',getElementById:()=>null},setTimeout:()=>{}};
 vm.runInNewContext(fs.readFileSync('app/src/main/assets/capability-tools.js','utf8'),context);
 return{Agent,kernel};
}
const live=runtime(data);
let code=live.Agent.execute('code.generate',{prompt:'PythonでCSV読み込みのコードを生成して',threadCode:'A'});
assert.equal(code.ok,true);assert.equal(code.value.validation.pass,true);assert.ok(code.value.text.includes('csv.DictReader'));assert.ok(code.value.review.connectome.trace.length===6);
let doc=live.Agent.execute('document.create.markdown',{prompt:'会議議事録をMarkdown文書として作成して',threadCode:'B',context:'会議は10月3日。参加者は田中と佐藤。開発計画を議論し、試作品を10月10日までに田中が提出することを決定。'});
assert.equal(doc.ok,true);assert.equal(doc.value.validation.pass,true);assert.ok(doc.value.body.includes('##'));assert.ok(doc.value.review.connectome);assert.ok(doc.value.body.includes('10月10日'));assert.ok(doc.value.body.includes('田中'));
let reading=live.Agent.execute('content.understand',{prompt:'要約してください。本文:会議は10月3日に開催します。議題は開発計画です。参加者は田中と佐藤です。',threadCode:'C'});
assert.equal(reading.value.validation.pass,true);assert.ok(reading.value.summary.includes('10月3日'));assert.equal(reading.value.evidence.length,3);assert.ok(reading.value.connectome.every(x=>x.trace.length===6));
const missing=live.Agent.execute('content.understand',{prompt:'要約して',threadCode:'C'});assert.equal(missing.value.validation.pass,false);
const dead=runtime({...data,edges:[]});
assert.equal(dead.Agent.execute('code.generate',{prompt:'PythonでCSV読み込みのコードを生成して'}).value.validation.pass,false);
assert.equal(dead.Agent.execute('content.understand',{prompt:'要約。本文:会議は10月3日です。'}).value.validation.pass,false);
console.log('task-review: PASS (working CSV implementation, document structure, source-grounded reading, circuit-selected output, cut/missing-source rejection)');
