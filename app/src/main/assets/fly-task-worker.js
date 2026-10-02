'use strict';
self.window=self;
// Worker bodies have no network, native bridge or DOM renderer access.
self.fetch=()=>Promise.reject(new Error('worker network disabled'));
self.document={readyState:'loading',addEventListener(){},getElementById:()=>null};
importScripts('capability-vocabulary-core.js','image-quality-core.js','feedback-core.js','autonomy-core.js','proxy-agent-core.js');
let graph=null;
self.onmessage=event=>{
 const m=event.data;
 if(m.type==='init'){graph=m.graph;return}
 try{
  if(!/^(?:code\.(?:generate|revise|debug|refactor|test|convert|optimize)|document\.create\.(?:docx|markdown|html|text)|content\.understand)$/.test(m.tool))throw new Error('unsupported worker tool');
  const memory=new Map(Object.entries(m.storage||{}));self.localStorage={getItem:k=>memory.get(k)||null,setItem:(k,v)=>memory.set(k,String(v))};
  const circuit=new FFCProxyCore.ConnectomeMultiplexer(graph,{maxLanes:8});circuit.restore(m.circuit);
  const state={tools:new Map(),capabilities:new Set(),connectomeSelector:circuit};
  self.BANC888_FLY_AGENT={state,execute(name,args){if(name==='chat.compose')return{ok:true,value:{reply:m.args.modelDraft||''}};const t=state.tools.get(name);return t?{ok:true,value:t.handler(args)}:{ok:false,error:'worker body unavailable: '+name}}};
  delete self.FFC_CAPABILITIES;importScripts('capability-tools.js');
  const value=state.tools.get(m.tool).handler(m.args);
  self.postMessage({id:m.id,ok:true,result:{ok:true,status:'OK',tool:m.tool,value},runtime:{worker:true,lane:m.args.threadCode}});
 }catch(e){self.postMessage({id:m.id,ok:false,error:String(e.message||e)})}
};
