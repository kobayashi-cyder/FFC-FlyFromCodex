import test from 'node:test';
import assert from 'node:assert/strict';
import {SolZeroAgenda} from '../chat_web/agent_agenda.js';
import {SolZeroPersonaAgent} from '../chat_web/character_agent.js';

test('queue never executes until explicit approval',async()=>{
 const q=new SolZeroAgenda(),agent=new SolZeroPersonaAgent();let n=0;
 q.add('タスク 新しい作業','high');
 assert.equal((await q.runNext(agent,{reply:()=>'',addTask:()=>{n++;return true;}},{approved:false})).ok,false);
 assert.equal(n,0);assert.equal(q.pending().length,1);
});
test('highest priority runs first and stores exactly once',async()=>{
 const q=new SolZeroAgenda(),agent=new SolZeroPersonaAgent();let recorded=[];
 q.add('タスク 下位','low');q.add('タスク 最優先','high');
 const services={reply:()=> '分析',addTask:text=>{recorded.push(text);return true;}};
 let checkpoints=0;
 const r=await q.runBatch(agent,services,{approved:true,maxGoals:2,checkpoint:()=>{checkpoints++;return true;}});
 assert.equal(r.ok,true);assert.deepEqual(recorded,['タスク 最優先','タスク 下位']);
 assert.equal(q.pending().length,0);assert.equal(q.queue.every(x=>x.status==='completed'),true);
 assert.ok(checkpoints>=6);
});
test('fail closed; do not execute subsequent goals after an empty media result',async()=>{
 const q=new SolZeroAgenda(),agent=new SolZeroPersonaAgent();
 q.add('画像を生成','high');q.add('タスク 後続','normal');let counter=0;
 const r=await q.runBatch(agent,{reply:()=> '分析',generate:async()=>({name:'empty',bytes:new Uint8Array()}),addTask:()=>{counter++;return true;}},{approved:true,maxGoals:2});
 assert.equal(r.ok,false);assert.equal(counter,0);assert.equal(q.queue[0].status,'failed');
 assert.equal(q.queue[1].status,'pending');
});
test('running state restores as interrupted and never retries automatically',async()=>{
 const q=new SolZeroAgenda();const one=q.add('タスク 途中');
 const snap=q.snapshot();snap.queue[0].status='running';
 const restored=new SolZeroAgenda();
 assert.equal(restored.restore(snap),true);
 assert.equal(restored.get(one.id).status,'interrupted');assert.equal(restored.paused,true);
 assert.equal(restored.pending().length,0);
});
test('invalid or duplicate input and status must be rejected',()=>{
 const q=new SolZeroAgenda();assert.throws(()=>q.add(''));assert.throws(()=>q.add('タスク','extreme'));
 q.add('タスク 実験');const bad=q.snapshot();bad.queue.push({...bad.queue[0]});
 assert.equal(new SolZeroAgenda().restore(bad),false);
});
test('queue cancel/pause/suggest and cleanup lifecycle',async()=>{
 const q=new SolZeroAgenda(),agent=new SolZeroPersonaAgent(),job=q.add('タスク 整理');
 assert.ok(q.suggest({tasks:[{text:'タスク 修理',done:false},{text:'完了',done:true}]}).includes('修理'));
 assert.equal(q.cancel(job.id),true);assert.equal(q.pending().length,0);
 assert.equal(q.clearCompleted(),1);
 q.add('メモ 朝の記録');q.pause(agent);
 assert.equal((await q.runNext(agent,{},{approved:true})).ok,false);
 q.resume();assert.equal(q.paused,false);
});
test('storage checkpoint failure prevents blind retry and pauses queue',async()=>{
 const q=new SolZeroAgenda(),agent=new SolZeroPersonaAgent();q.add('タスク 情報整理');
 const r=await q.runNext(agent,{reply:()=> '分析',addTask:()=>true},{approved:true,checkpoint:()=>false});
 assert.equal(r.ok,false);assert.equal(q.paused,true);assert.equal(q.queue[0].status,'interrupted');
});
test('batch size clamped to two for explicit bounded action',async()=>{
 const q=new SolZeroAgenda(),agent=new SolZeroPersonaAgent();
 for(let i=0;i<4;i++)q.add('タスク '+i);
 const r=await q.runBatch(agent,{reply:()=> '分析',addTask:()=>true},{approved:true,maxGoals:99});
 assert.equal(r.results.length,2);assert.equal(q.pending().length,2);
});

test('queue routes each goal to its original conversation and persists origin',async()=>{
 const q=new SolZeroAgenda(),agent=new SolZeroPersonaAgent(),stored={alpha:[],beta:[]};
 q.add('タスク 最初','low','alpha');
 q.add('タスク 次','high','beta');
 const factory=item=>({
   reply:()=> '分析',
   addTask:value=>{stored[item.chatId].push(value);return true;}
 });
 const r=await q.runBatch(agent,factory,{approved:true,maxGoals:2});
 assert.equal(r.ok,true);
 assert.deepEqual(stored.beta,['タスク 次']);
 assert.deepEqual(stored.alpha,['タスク 最初']);
 const saved=q.snapshot(),other=new SolZeroAgenda();
 assert.equal(other.restore(saved),true);
 assert.equal(other.queue[0].chatId,'alpha');
 assert.equal(other.queue[1].chatId,'beta');
});
test('removed source conversation stops action without writing elsewhere',async()=>{
 const q=new SolZeroAgenda(),agent=new SolZeroPersonaAgent();
 q.add('タスク 存在しない会話','normal','deleted-chat');
 let writes=0;
 const r=await q.runNext(agent,()=>{throw Error('元の会話がありません');},{approved:true});
 assert.equal(r.ok,false);
 assert.equal(q.queue[0].status,'interrupted');
 assert.equal(q.paused,true);
 assert.equal(writes,0);
});

test('failed goal requires explicit acknowledgement before subsequent goal; never repeats failed side effects',async()=>{
 const q=new SolZeroAgenda(),agent=new SolZeroPersonaAgent();let attempts=0,saved=0;
 q.add('画像を生成','high');
 q.add('タスク 次の作業','normal');
 const services={
  reply:()=> '分析',
  generate:async()=>{attempts++;return {name:'empty.png',bytes:new Uint8Array()};},
  addTask:()=>{saved++;return true;}
 };
 const first=await q.runBatch(agent,services,{approved:true,maxGoals:2});
 assert.equal(first.ok,false);assert.equal(attempts,1);
 const blocked=await q.runNext(agent,services,{approved:true});
 assert.equal(blocked.ok,false);assert.equal(saved,0);
 assert.equal(q.acknowledge(agent),true);
 const resumed=await q.runNext(agent,services,{approved:true});
 assert.equal(resumed.ok,true);assert.equal(attempts,1);assert.equal(saved,1);
 assert.equal(q.queue[0].status,'failed');
});
