(function(root,factory){const api=factory();if(typeof module==='object'&&module.exports)module.exports=api;root.FFCFlyParallelCore=api})(typeof globalThis!=='undefined'?globalThis:this,function(){
'use strict';
class Scheduler{
 constructor({limit=2,maxQueued=256,select=null,onChange=()=>{}}={}){this.limit=limit;this.maxQueued=maxQueued;this.select=select;this.onChange=onChange;this.queue=[];this.running=new Map();this.resources=new Set();this.sequence=0;this.completed=0;this.failed=0;this.lastThread=null;}
 configure(n){if(!Number.isInteger(n)||n<1||n>4)throw new Error('parallel limit must be 1–4');this.limit=n;this.drain();return this.status()}
 submit({threadId,resource='compute',run}){if(this.queue.length>=this.maxQueued)return Promise.reject(new Error('実行待ちが上限に達しました。'));if(this.queue.filter(x=>x.threadId===threadId).length>=16)return Promise.reject(new Error('この会話の実行待ちは16件までです。'));return new Promise((resolve,reject)=>{this.queue.push({id:++this.sequence,threadId,resource,run,resolve,reject,time:Date.now()});this.drain()})}
 eligible(){const seen=new Set();return this.queue.filter(x=>{if(seen.has(x.threadId))return false;seen.add(x.threadId);return ![...this.running.values()].some(y=>y.threadId===x.threadId)&&(x.resource==='compute'||!this.resources.has(x.resource))})}
 drain(){while(this.running.size<this.limit){let pool=this.eligible();if(!pool.length)break;const other=pool.filter(x=>x.threadId!==this.lastThread);if(other.length)pool=other;const pick=this.select?.(pool)||pool[0];const job=pool.find(x=>x.id===pick.id)||pool[0];this.queue.splice(this.queue.indexOf(job),1);this.running.set(job.id,job);if(job.resource!=='compute')this.resources.add(job.resource);this.lastThread=job.threadId;Promise.resolve().then(job.run).then(x=>{this.completed++;job.resolve(x)},e=>{this.failed++;job.reject(e)}).finally(()=>{this.running.delete(job.id);this.resources.delete(job.resource);this.onChange(this.status());this.drain()});}this.onChange(this.status())}
 status(){return{limit:this.limit,running:[...this.running.values()].map(({id,threadId,resource})=>({id,threadId,resource})),queued:this.queue.map(({id,threadId,resource})=>({id,threadId,resource})),completed:this.completed,failed:this.failed}}
}
return{Scheduler};
});
