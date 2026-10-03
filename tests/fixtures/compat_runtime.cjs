const fs=require('node:fs'),vm=require('node:vm');
const C=require('../../app/src/main/assets/proxy-agent-core.js');
const V=require('../../app/src/main/assets/capability-vocabulary-core.js');
const A=require('../../app/src/main/assets/ai-compat-core.js');
const P=require('../../app/src/main/assets/ir-patch-core.js');
const html=fs.readFileSync('app/src/main/assets/index.html','utf8');
const csv=html.match(/const RAW=\{[\s\S]*?edges:`([\s\S]*?)`/)[1];
const edges=csv.trim().split('\n').slice(1).map(line=>{const [from,to,count,...nt]=line.split(',');return{from,to,count:+count,pre_top_nt:nt.join(',').replaceAll('"','')}});
const routes=vm.runInNewContext(html.match(/const O1P_SHORTCUTS=(\[[\s\S]*?\]);/)[1]);
const graph={nodes:[...new Set(edges.flatMap(e=>[e.from,e.to]))],edges,routes,source:'embedded BANC Figure 3g',toolRoutes:{'compute.math':'o1p1','chat.compose':'o1p3'}};
function runtime(data=graph){
 const calls=[],store=new Map();
 const names=['chat.compose','compute.math','content.understand','code.generate','code.revise','code.debug','document.create.markdown','document.create.docx','document.create.text','feedback.ingest'];
 const tools=new Map(names.map(name=>[name,{name,capability:name.startsWith('code.')?'code.write':name.startsWith('document.')?'document.write':'compute'}]));
 const Agent={connectome:data,state:{tools,capabilities:new Set(['compute','read_state'])},buildCandidates:goal=>({candidates:[{tool:/計算|calculate/i.test(goal)?'compute.math':'chat.compose',confidence:1,excitation:1,args:{goal}}]}),execute:(tool,args)=>{calls.push({tool,args});return{ok:true,value:tool==='chat.compose'?{reply:'fixture reply'}:tool==='content.understand'?{summary:args.source||args.context}:tool.startsWith('document.')?{format:tool.split('.').at(-1)}:{text:'fixture code'}}}};
 const context={window:{FFCProxyCore:C,FFCIrPatch:P,FFCAICompat:A,FFCCapabilityVocabulary:V,FFCFeedback:{fromTest:()=>({reward:0,learningScale:0,learnable:false,tier:'holdout'}),stimuli:()=>[]},BANC888_FLY_AGENT:Agent,FFC_CAPABILITIES:{}},localStorage:{getItem:k=>store.get(k)||null,setItem:(k,v)=>store.set(k,v),removeItem:k=>store.delete(k)},setTimeout:()=>{},document:{getElementById:()=>null}};
 vm.runInNewContext(fs.readFileSync('app/src/main/assets/proxy-agent.js','utf8'),context);
 return{api:context.window.FFC_PROXY_AGENT,calls,context};
}
module.exports={graph,runtime,C,A};
