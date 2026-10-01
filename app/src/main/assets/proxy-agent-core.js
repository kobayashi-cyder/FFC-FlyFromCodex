(function(root,factory){
 const api=factory(); if(typeof module==='object'&&module.exports)module.exports=api; root.FFCProxyCore=api;
})(typeof globalThis!=='undefined'?globalThis:this,function(){
 'use strict';
 const Status=Object.freeze({QUEUED:'queued',RUNNING:'running',WAITING:'waiting',BLOCKED:'blocked',DONE:'done',FAILED:'failed',CANCELLED:'cancelled'});
 const Result=Object.freeze({SUCCESS:'success',RETRY:'retry',FAILED:'failed',BLOCKED:'blocked',UNSUPPORTED:'unsupported'});
 class ToolPolicy{
  constructor(allowed){this.allowed=allowed===undefined?new Set(['compute','read_state','write_state','human_output','code.write','image.write','voice.input','document.write']):new Set(allowed)}
  allows(cap){return this.allowed.has(cap)}
 }
 class BodyArbiter{
  constructor(){this.leases=new Map()}
  acquire(resource,owner){if(!resource)return{resource:null,owner:owner||'__system__',leaseId:'none'};owner=owner||'__system__';const c=this.leases.get(resource);if(!c){const l={resource,owner,leaseId:Math.random().toString(36).slice(2,12),depth:1};this.leases.set(resource,l);return Object.assign({},l)}if(c.owner===owner){c.depth++;return Object.assign({},c)}return null}
  release(lease){if(!lease||!lease.resource)return;const c=this.leases.get(lease.resource);if(!c)return;if(c.leaseId!==lease.leaseId||c.owner!==lease.owner)throw new Error('lease ownership mismatch');c.depth--;if(c.depth<=0)this.leases.delete(lease.resource)}
  snapshot(){return [...this.leases.entries()].map(([resource,v])=>({resource,owner:v.owner,leaseId:v.leaseId,depth:v.depth}))}
 }
 class Executive{
  constructor(opts={}){this.policy=opts.policy||new ToolPolicy();this.maxPlanSteps=opts.maxPlanSteps||16}
  evaluate(proposal,toolLookup){if(!proposal||!Array.isArray(proposal.steps)||!proposal.steps.length)return{accepted:false,status:Result.UNSUPPORTED,reason:'empty proposal'};if(proposal.steps.length>this.maxPlanSteps)return{accepted:false,status:Result.BLOCKED,reason:'plan too long'};for(let i=0;i<proposal.steps.length;i++){const s=proposal.steps[i],spec=toolLookup(s.tool);if(!spec)return{accepted:false,status:Result.UNSUPPORTED,reason:'unknown tool '+s.tool};if(!this.policy.allows(spec.capability))return{accepted:false,status:Result.BLOCKED,reason:'capability blocked '+spec.capability}}return{accepted:true,status:Result.SUCCESS,reason:'accepted'}}
 }
 function recoverGoals(goals){return (goals||[]).map(g=>g&&g.status===Status.RUNNING?Object.assign({},g,{status:Status.QUEUED,lastError:'interrupted while running'}):g)}
 function nextGoal(goals,lastThread){const q=(goals||[]).filter(g=>g.status===Status.QUEUED);if(!q.length)return null;const top=Math.max(...q.map(g=>+g.priority||0)),same=q.filter(g=>(+g.priority||0)===top).sort((a,b)=>(a.createdAt||0)-(b.createdAt||0));return same.find(g=>g.threadId!==lastThread)||same[0]}
 function resourceForTool(name){if(/^voice\.listen/.test(name))return'microphone';if(/^voice\.(speak|configure)/.test(name))return'speaker';if(/^image\./.test(name))return'image-generator';if(/^document\./.test(name))return'artifact';if(/^code\./.test(name))return'compute';return'compute'}
 function classifyCapability(name,spec){if(spec&&spec.capability)return spec.capability;if(/^device\./.test(name))return'device';if(/^network\./.test(name))return'network';if(/^code\./.test(name))return'code.write';if(/^image\./.test(name))return'image.write';if(/^voice\./.test(name))return'voice.input';if(/^document\./.test(name))return'document.write';return'compute'}
 // Biological edges, engineered encoding/readout. Transmitter signs are model
 // assumptions inherited from CPF, not measurements of functional causality.
 class ConnectomeSelector{
  constructor(data,opts={}){
   if(!data||!Array.isArray(data.edges)||!Array.isArray(data.nodes))throw new Error('missing connectome data');
   this.nodes=[...new Set(data.nodes)];this.ids=new Set(this.nodes);
   this.routes=new Map((data.routes||[]).map(r=>[r.id,r]));this.toolRoutes=data.toolRoutes||{};
   this.source=data.source||'unspecified';
   this.identity=JSON.stringify({nodes:this.nodes,edges:data.edges,routes:data.routes,toolRoutes:this.toolRoutes});
   this.ticks=opts.ticks===undefined?6:opts.ticks;
   if(!Number.isInteger(this.ticks)||this.ticks<1||this.ticks>32)throw new Error('invalid propagation ticks');
   this.factors=new Map();this.last=null;
   const totals=new Map();
   this.edges=data.edges.map((e,i)=>{
    if(!this.ids.has(e.from)||!this.ids.has(e.to)||!Number.isFinite(Number(e.count))||Number(e.count)<0)throw new Error('invalid connectome edge');
    const nt=String(e.pre_top_nt||'').toLowerCase();
    const sign=nt.includes('gaba')?-1:nt.includes('acetylcholine')?1:nt.includes('glutamate')?-.35:nt.includes('serotonin')?.28:nt.includes('dh44')||nt.includes('lk')||nt.includes('ilp')||nt.includes('darc')?.18:.10;
    const magnitude=Math.sqrt(Number(e.count));totals.set(e.from,(totals.get(e.from)||0)+magnitude);
    return{...e,id:String(i),magnitude,sign};
   });
   for(const e of this.edges)e.weight=e.sign*e.magnitude/Math.max(1e-9,totals.get(e.from));
  }
  binding(tool){
   let id=this.toolRoutes[tool];
   // Explicit machine task bindings, not biological semantic annotations.
   if(!id)id=/^(code|image|document)\./.test(tool)?'o1p0':/^(feedback|autonomy)\./.test(tool)?'o1p2':'o1p3';
   const r=this.routes.get(id);return r&&r.source!==r.target&&this.ids.has(r.source)&&this.ids.has(r.target)?r:null;
  }
  propagate(candidate){
   const route=this.binding(String(candidate.tool||''));if(!route)return null;
   const raw=(Number(candidate.excitation??1)-Number(candidate.inhibition??0))*Number(candidate.confidence??1);
   if(!Number.isFinite(raw)||raw<=0)return null;
   const drive=Math.min(1.2,raw),trace=[],eligibility=new Map();
   let activity=new Map(this.nodes.map(n=>[n,0]));
   for(let tick=0;tick<this.ticks;tick++){
    const next=new Map(this.nodes.map(n=>[n,(activity.get(n)||0)*.35]));
    next.set(route.source,next.get(route.source)+drive);
    for(const e of this.edges){
     const flux=(activity.get(e.from)||0)*e.weight*(this.factors.get(e.id)||1);
     next.set(e.to,next.get(e.to)+flux);
     eligibility.set(e.id,(eligibility.get(e.id)||0)+Math.abs(flux));
    }
    for(const n of this.nodes)next.set(n,Math.tanh(next.get(n)));
    activity=next;trace.push(Object.fromEntries(activity));
   }
   const output=activity.get(route.target)||0;
   // Magnitude lets an inhibitory pathway carry an engineered action signal.
   return{candidate,tool:candidate.tool,activation:Math.abs(output),signedOutput:output,route:route.id,sourceNode:route.source,targetNode:route.target,trace,eligibility};
  }
  select(candidates=[]){
   const ranked=candidates.map(c=>this.propagate(c)).filter(x=>x&&x.activation>=1e-6);
   ranked.sort((a,b)=>b.activation-a.activation||String(a.tool).localeCompare(String(b.tool)));
   this.last=ranked[0]||null;
   if(!this.last)return null;
   const {eligibility,...decision}=this.last;
   return{...decision.candidate,activation:decision.activation,connectome:{...decision,candidate:undefined,provenance:this.source,granularity:'display_name aggregate',bindings:'engineered',transmitterSigns:'CPF model assumptions'}};
  }
  reinforce(tool,reward,{learnable=true,tier='training'}={}){
   if(!learnable||tier==='holdout'||!this.last||this.last.tool!==tool||!Number.isFinite(reward))return false;
   // Only edges both active and upstream of the selected output receive reward.
   const cone=new Set([this.last.targetNode]);let changed=true;
   while(changed){changed=false;for(const e of this.edges)if(cone.has(e.to)&&!cone.has(e.from)){cone.add(e.from);changed=true}}
   for(const e of this.edges){const eligibility=this.last.eligibility.get(e.id)||0;if(!cone.has(e.to)||!cone.has(e.from)||eligibility<1e-9)continue;
    const factor=(this.factors.get(e.id)||1)+.025*Math.max(-1,Math.min(1,reward))*Math.min(1,eligibility);
    this.factors.set(e.id,Math.max(.75,Math.min(1.25,factor)));
   }
   this.last=null;return true;
  }
  snapshot(){return{schema:1,source:this.source,identity:this.identity,factors:Object.fromEntries(this.factors)}}
  restore(state){if(!state||state.schema!==1||state.source!==this.source||state.identity!==this.identity)return;for(const e of this.edges){const f=state.factors?.[e.id];if(Number.isFinite(f)&&f>=.75&&f<=1.25)this.factors.set(e.id,f)}}
 }
 return{Status,Result,ToolPolicy,BodyArbiter,Executive,ConnectomeSelector,recoverGoals,nextGoal,resourceForTool,classifyCapability};
});
