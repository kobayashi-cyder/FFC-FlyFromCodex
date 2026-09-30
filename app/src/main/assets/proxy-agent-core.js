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
 return{Status,Result,ToolPolicy,BodyArbiter,Executive,recoverGoals,nextGoal,resourceForTool,classifyCapability};
});