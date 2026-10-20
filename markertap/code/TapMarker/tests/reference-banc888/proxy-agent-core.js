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
   this.nodes=[...new Set(data.nodes)];
   this.index=new Map(this.nodes.map((n,i)=>[n,i]));this.ids=this.index;
   this.routes=new Map((data.routes||[]).map(r=>[r.id,r]));this.toolRoutes=data.toolRoutes||{};
   this.source=data.source||'unspecified';
   this.identity=JSON.stringify({nodes:this.nodes,edges:data.edges,routes:data.routes,toolRoutes:this.toolRoutes});
   this.ticks=opts.ticks===undefined?6:opts.ticks;
   if(!Number.isInteger(this.ticks)||this.ticks<1||this.ticks>32)throw new Error('invalid propagation ticks');
   this.factors=new Map();this.last=null;
   // Store edge IDs and compiled numbers; do not duplicate edge metadata.
   this.edgeIds=data.edges.map((_,i)=>String(i));
   // Compile once. Float64 preserves the former JS-number propagation results.
   const n=this.nodes.length,m=data.edges.length,totals=new Float64Array(n);
   this.src=new Uint32Array(m);this.dst=new Uint32Array(m);
   this.weights=new Float64Array(m);this.gains=new Float64Array(m);this.gains.fill(1);
   this.activity=new Float64Array(n);this.next=new Float64Array(n);
   this.credit=new Float64Array(m);this.cone=new Uint8Array(n);this.queue=new Uint32Array(n);
   this.incomingOffsets=new Uint32Array(n+1);this.incoming=new Uint32Array(m);
   for(let i=0;i<m;i++){
    const e=data.edges[i];
    if(!this.ids.has(e.from)||!this.ids.has(e.to)||!Number.isFinite(Number(e.count))||Number(e.count)<0)throw new Error('invalid connectome edge');
    this.src[i]=this.index.get(e.from);this.dst[i]=this.index.get(e.to);
    const nt=String(e.pre_top_nt||'').toLowerCase();
    const sign=nt.includes('gaba')?-1:nt.includes('acetylcholine')?1:nt.includes('glutamate')?-.35:nt.includes('serotonin')?.28:nt.includes('dh44')||nt.includes('lk')||nt.includes('ilp')||nt.includes('darc')?.18:.10;
    const magnitude=Math.sqrt(Number(e.count));totals[this.src[i]]+=magnitude;
    this.weights[i]=sign*magnitude;this.incomingOffsets[this.dst[i]+1]++;
   }
   for(let i=0;i<m;i++)this.weights[i]/=Math.max(1e-9,totals[this.src[i]]);
   for(let i=1;i<=n;i++)this.incomingOffsets[i]+=this.incomingOffsets[i-1];
   const cursor=this.incomingOffsets.slice();
   for(let i=0;i<m;i++)this.incoming[cursor[this.dst[i]]++]=this.src[i];
   this.coneTarget=-1;this.runs=0;this.traceFrames=0;
  }
  binding(tool){
   let id=this.toolRoutes[tool];
   // Explicit machine task bindings, not biological semantic annotations.
   if(!id)id=/^(code|image|document)\./.test(tool)?'o1p0':/^(feedback|autonomy)\./.test(tool)?'o1p2':'o1p3';
   const r=this.routes.get(id);return r&&r.source!==r.target&&this.ids.has(r.source)&&this.ids.has(r.target)?r:null;
  }
  input(candidate){
   if(!candidate)return null;
   const route=candidate.routeId===undefined?this.binding(String(candidate.tool||'')):this.routes.get(String(candidate.routeId));
   if(!route||route.source===route.target||!this.ids.has(route.source)||!this.ids.has(route.target))return null;
   const raw=(Number(candidate.excitation??1)-Number(candidate.inhibition??0))*Number(candidate.confidence??1);
   if(!Number.isFinite(raw)||raw<=0)return null;
   return{candidate,route,drive:Math.min(1.2,raw)};
  }
  run(source,drive,trace=null){
   let activity=this.activity,next=this.next;activity.fill(0);
   if(trace)this.credit.fill(0);
   this.runs++;
   for(let tick=0;tick<this.ticks;tick++){
    for(let i=0;i<activity.length;i++)next[i]=activity[i]*.35;
    next[source]+=drive;
    for(let i=0;i<this.src.length;i++){
     const flux=activity[this.src[i]]*this.weights[i]*this.gains[i];
     next[this.dst[i]]+=flux;
     if(trace)this.credit[i]+=Math.abs(flux);
    }
    for(let i=0;i<next.length;i++)next[i]=Math.tanh(next[i]);
    const swap=activity;activity=next;next=swap;
    if(trace){
     const frame={};
     for(let i=0;i<this.nodes.length;i++){
      const node=this.nodes[i];
      if(node==='__proto__')Object.defineProperty(frame,node,{value:activity[i],enumerable:true,writable:true,configurable:true});
      else frame[node]=activity[i];
     }
     trace.push(frame);this.traceFrames++;
    }
   }
   this.activity=activity;this.next=next;
  }
  propagate(candidate){
   const input=this.input(candidate);if(!input)return null;
   const {route,drive}=input,trace=[];
   this.run(this.index.get(route.source),drive,trace);
   const output=this.activity[this.index.get(route.target)]||0;
   const eligibility=new Map();
   for(let i=0;i<this.src.length;i++)eligibility.set(this.edgeIds[i],this.credit[i]);
   // Magnitude lets an inhibitory pathway carry an engineered action signal.
   return{candidate,tool:candidate.tool,activation:Math.abs(output),signedOutput:output,route:route.id,sourceNode:route.source,targetNode:route.target,trace,eligibility};
  }
  select(candidates=[]){
   let winner=null,best=0,lastSource=-1,lastDrive=NaN;
   for(const c of candidates){
    const input=this.input(c);if(!input)continue;
    const source=this.index.get(input.route.source);
    // Consecutive candidates sharing a stimulus reuse its final activity.
    if(source!==lastSource||input.drive!==lastDrive){this.run(source,input.drive);lastSource=source;lastDrive=input.drive}
    const activation=Math.abs(this.activity[this.index.get(input.route.target)]);
    if(!Number.isFinite(activation)||activation<1e-6)continue;
    if(!winner||activation>best||(activation===best&&String(c.tool).localeCompare(String(winner.tool))<0)){winner=c;best=activation}
   }
   // Only the selected action needs a diagnostic trace and learning credit.
   this.last=winner?this.propagate(winner):null;
   if(!this.last)return null;
   const {eligibility,...decision}=this.last;
   return{...decision.candidate,activation:decision.activation,connectome:{...decision,candidate:undefined,provenance:this.source,granularity:'display_name aggregate',bindings:'engineered',transmitterSigns:'CPF model assumptions'}};
  }
  reinforce(tool,reward,{learnable=true,tier='training'}={}){
   if(!learnable||tier==='holdout'||!this.last||this.last.tool!==tool||!Number.isFinite(reward))return false;
   // Only edges both active and upstream of the selected output receive reward.
   const target=this.index.get(this.last.targetNode);
   if(this.coneTarget!==target){
    this.cone.fill(0);this.cone[target]=1;this.queue[0]=target;let tail=1;
    for(let head=0;head<tail;head++){
     const node=this.queue[head];
     for(let i=this.incomingOffsets[node];i<this.incomingOffsets[node+1];i++){
      const parent=this.incoming[i];if(!this.cone[parent]){this.cone[parent]=1;this.queue[tail++]=parent}
     }
    }
    this.coneTarget=target;
   }
   for(let i=0;i<this.src.length;i++){
    const id=this.edgeIds[i],eligibility=this.last.eligibility.get(id)||0;
    if(!this.cone[this.dst[i]]||!this.cone[this.src[i]]||eligibility<1e-9)continue;
    const factor=this.gains[i]+.025*Math.max(-1,Math.min(1,reward))*Math.min(1,eligibility);
    this.gains[i]=Math.max(.75,Math.min(1.25,factor));this.factors.set(id,this.gains[i]);
   }
   this.last=null;return true;
  }
  snapshot(){return{schema:1,source:this.source,identity:this.identity,factors:Object.fromEntries(this.factors)}}
  restore(state){if(!state||state.schema!==1||state.source!==this.source||state.identity!==this.identity)return;for(let i=0;i<this.src.length;i++){const id=this.edgeIds[i],f=state.factors?.[id];if(Number.isFinite(f)&&f>=.75&&f<=1.25){this.factors.set(id,f);this.gains[i]=f}}}
  stats(){return{engine:'sparse-float64-v1',nodes:this.nodes.length,edges:this.src.length,ticks:this.ticks,propagations:this.runs,traceFrames:this.traceFrames,workspaceBytes:[this.src,this.dst,this.weights,this.gains,this.activity,this.next,this.credit,this.cone,this.queue,this.incomingOffsets,this.incoming].reduce((n,a)=>n+a.byteLength,0)}}
 }
 // Time multiplexing: compile the graph once, share scratch buffers, and keep
 // thread-local plasticity/credit. This is not a claim of parallel CPU execution.
 class ConnectomeMultiplexer{
  constructor(data,opts={}){
   this.kernel=new ConnectomeSelector(data,opts);this.maxLanes=opts.maxLanes??8;
   if(!Number.isInteger(this.maxLanes)||this.maxLanes<1||this.maxLanes>64)throw new Error('invalid lane capacity');
   this.lanes=new Map();this.seedFactors={};this.evictions=0;this.rejections=0;this.started=false;
  }
  activate(id,create=true){
   id=String(id??'__system__');if(!id||id.length>256)throw new Error('invalid lane ID');
   let lane=this.lanes.get(id);
   if(!lane&&create){
    if(this.lanes.size>=this.maxLanes){
     const idle=[...this.lanes].find(([,v])=>!v.last);
     if(!idle){this.rejections++;return null}
     this.lanes.delete(idle[0]);this.evictions++;
    }
    const k=this.kernel,gains=this.started?new Float64Array(k.src.length):k.gains;
    gains.fill(1);this.started=true;
    lane={id,gains,factors:new Map(),last:null};
    for(let i=0;i<k.edgeIds.length;i++){
     const f=this.seedFactors[k.edgeIds[i]];
     if(Number.isFinite(f)&&f>=.75&&f<=1.25){gains[i]=f;lane.factors.set(k.edgeIds[i],f)}
    }
   }
   if(!lane)return null;
   this.lanes.delete(id);this.lanes.set(id,lane);
   this.kernel.gains=lane.gains;this.kernel.factors=lane.factors;this.kernel.last=lane.last;
   return lane;
  }
  select(candidates=[],id='__system__'){
   const lane=this.activate(id);if(!lane)return null;
   const pick=this.kernel.select(candidates);lane.last=this.kernel.last;
   return pick?{...pick,connectome:{...pick.connectome,lane:lane.id}}:null;
  }
  selectMany(jobs){
   if(!Array.isArray(jobs)||jobs.length>64)throw new Error('batch exceeds 64 jobs');
   return jobs.map(job=>({lane:String(job.lane??'__system__'),selection:this.select(job.candidates||[],job.lane)}));
  }
  evaluate(candidates,id='__system__',opts={}){
   const lane=this.activate(id);if(!lane)return null;
   const pending=lane.last;
   try{
    const pick=this.select(candidates,id);
    if(pick&&opts.reward!==undefined)this.reinforce(pick.tool,opts.reward,{...opts,lane:id});
    return pick;
   }finally{lane.last=pending;this.activate(id,false)}
  }
  reinforce(tool,reward,opts={}){
   const lane=this.activate(opts.lane??'__system__',false);if(!lane)return false;
   // Evaluation credit cannot leak into a subsequent training reward.
   if(opts.learnable===false||opts.tier==='holdout'){lane.last=null;this.kernel.last=null;return false}
   const changed=this.kernel.reinforce(tool,reward,opts);lane.last=this.kernel.last;return changed;
  }
  discard(id='__system__'){
   const lane=this.activate(id,false);if(!lane)return false;
   lane.last=null;this.kernel.last=null;return true;
  }
  snapshot(){
   const k=this.kernel;
   return{schema:2,source:k.source,identity:k.identity,seedFactors:{...this.seedFactors},lanes:[...this.lanes.values()].map(v=>({id:v.id,factors:Object.fromEntries(v.factors)}))};
  }
  restore(state){
   const k=this.kernel;if(!state||state.source!==k.source||state.identity!==k.identity)return;
   if(state.schema===1){
    k.factors=new Map();k.gains.fill(1);k.restore(state);this.seedFactors=Object.fromEntries(k.factors);this.lanes.clear();this.activate('__system__');return;
   }
   if(state.schema!==2||!Array.isArray(state.lanes)||state.lanes.length>this.maxLanes)return;
   if(state.lanes.some(v=>!v||typeof v.id!=='string'||!v.id||v.id.length>256)||new Set(state.lanes.map(v=>v.id)).size!==state.lanes.length)return;
   this.seedFactors={};
   for(const id of k.edgeIds){const f=state.seedFactors?.[id];if(Number.isFinite(f)&&f>=.75&&f<=1.25)this.seedFactors[id]=f}
   this.lanes.clear();
   for(const saved of state.lanes){
    const lane=this.activate(saved.id);lane.gains.fill(1);lane.factors.clear();
    k.restore({schema:1,source:k.source,identity:k.identity,factors:saved.factors});
   }
  }
  stats(){
   const s=this.kernel.stats(),laneBytes=Math.max(0,this.lanes.size-1)*this.kernel.gains.byteLength;
   return{...s,workspaceBytes:s.workspaceBytes+laneBytes,multiplexed:true,sharedTopology:true,lanes:this.lanes.size,maxLanes:this.maxLanes,pendingCredits:[...this.lanes.values()].filter(v=>v.last).length,evictions:this.evictions,rejections:this.rejections};
  }
 }
 return{Status,Result,ToolPolicy,BodyArbiter,Executive,ConnectomeSelector,ConnectomeMultiplexer,recoverGoals,nextGoal,resourceForTool,classifyCapability};
});
