import test from 'node:test';
import assert from 'node:assert/strict';
import {SolZeroPersonaAgent,SolZeroVoice,SOLZERO_VOICE_PRESET} from '../chat_web/character_agent.js';
test('plans do not mutate conversation until approved execution',async()=>{
 const agent=new SolZeroPersonaAgent();let notes=0;
 const services={reply:()=> '分析OK',addNote:()=>{notes++;return true;}};
 agent.makePlan('計画を整理');assert.equal(notes,0);
 await agent.step(services,false);assert.equal(notes,0);
 const denied=await agent.step(services,false);assert.equal(denied.ok,false);
 assert.equal(notes,0);
 const ran=await agent.run(services,true,3);
 assert.equal(ran.ok,true);assert.equal(notes,1);assert.equal(agent.active.status,'completed');
});
test('generates media locally and verifies nonempty result',async()=>{
 const agent=new SolZeroPersonaAgent();agent.makePlan('海の画像を作成');
 let kind='';
 const done=await agent.run({reply:()=> '要約',generate:async(k)=>{kind=k;return {name:'sea.png',bytes:new Uint8Array(20)};}},true);
 assert.equal(kind,'image');assert.equal(done.ok,true);assert.equal(agent.active.status,'completed');
});
test('failed side effects stop further execution',async()=>{
 const agent=new SolZeroPersonaAgent();agent.makePlan('通知音を生成');
 const r=await agent.run({reply:()=> '分析',generate:async()=>({name:'bad.wav',bytes:new Uint8Array()})},true);
 assert.equal(r.ok,false);assert.equal(agent.stopped,true);
 assert.equal(agent.active.status,'failed');assert.equal(agent.active.cursor,1);
});
test('stop and resume are explicit',async()=>{
 const agent=new SolZeroPersonaAgent();agent.makePlan('タスクを追加');
 agent.stop();const r=await agent.step({reply:()=>''},true);assert.equal(r.ok,false);
 agent.resume();assert.equal(agent.stopped,false);
});
test('snapshot restore rejects inconsistent action and resumes interrupted run safely',async()=>{
 const agent=new SolZeroPersonaAgent();agent.makePlan('タスクを追加');
 let snap=agent.snapshot();snap.active.steps[1].action='create_media';
 assert.equal(new SolZeroPersonaAgent().restore(snap),false);
 snap=agent.snapshot();snap.active.status='running';
 const restored=new SolZeroPersonaAgent();assert.equal(restored.restore(snap),true);
 assert.equal(restored.active.status,'planned');
});
test('voice starts disabled; TTS uses reference pitch/rate without a cloned voice',()=>{
 const args=[],synth={cancel:()=>args.push('cancel'),speak:(v)=>args.push(v),getVoices:()=>[{lang:'ja-JP'}]};
 class Utterance{constructor(text){this.text=text;}}
 const v=new SolZeroVoice(synth,Utterance);
 assert.equal(v.speak('test'),false);
 v.setEnabled(true);assert.equal(v.speak('こんにちは'),true);
 assert.equal(args[1].pitch,SOLZERO_VOICE_PRESET.pitch);
 assert.equal(args[1].rate,SOLZERO_VOICE_PRESET.rate);
 v.setEnabled(false);assert.equal(v.speak('test'),false);
});