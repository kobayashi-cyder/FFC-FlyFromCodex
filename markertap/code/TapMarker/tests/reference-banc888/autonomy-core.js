(function(root,factory){
 const api=factory();
 if(typeof module==='object'&&module.exports)module.exports=api;
 root.FFCAutonomyCore=api;
})(typeof globalThis!=='undefined'?globalThis:this,function(){
'use strict';

const VERSION='1.0';
const clamp=(v,a=-1,b=1)=>Math.max(a,Math.min(b,Number(v)||0));
const norm=s=>String(s||'').normalize('NFKC').toLowerCase().replace(/(?<!\w)[+-]?(?:\d+(?:\.\d+)?|\.\d+)(?!\w)/g,'#').replace(/\s+/g,' ').trim();
const stop=new Set(['を','に','へ','で','と','が','は','の','する','して','ください','お願い','the','a','an','to','for','of','and','please']);
function tokens(s){return [...new Set((norm(s).match(/[\wぁ-んァ-ン一-龥]+/g)||[]).map(x=>x.toLowerCase()).filter(x=>x.length>1&&!stop.has(x)))].sort()}
function similarity(a,b){a=new Set(a||[]);b=new Set(b||[]);if(!a.size&&!b.size)return 1;if(!a.size||!b.size)return 0;let n=0;for(const x of a)if(b.has(x))n++;const contain=n/Math.max(1,Math.min(a.size,b.size)),jac=n/Math.max(1,new Set([...a,...b]).size);return .62*contain+.38*jac}
function hash(s){let h=2166136261>>>0;for(const ch of String(s)){h^=ch.charCodeAt(0);h=Math.imul(h,16777619)>>>0}return h.toString(16).padStart(8,'0')}
function clone(v){return v==null?v:JSON.parse(JSON.stringify(v))}
function templ(v,goal){
 if(typeof v==='string'){if(v===goal)return{__goal__:true};return goal&&v.includes(goal)?v.split(goal).join('{{goal}}'):v}
 if(Array.isArray(v))return v.map(x=>templ(x,goal));
 if(v&&typeof v==='object'){const o={};for(const [k,x] of Object.entries(v))o[k]=templ(x,goal);return o}
 return v
}
function instantiate(v,goal){
 if(Array.isArray(v))return v.map(x=>instantiate(x,goal));
 if(v&&typeof v==='object'){if(v.__goal__===true&&Object.keys(v).length===1)return goal;const o={};for(const [k,x] of Object.entries(v))o[k]=instantiate(x,goal);return o}
 return typeof v==='string'?v.split('{{goal}}').join(goal):v
}
function blank(){return{schema:1,tools:{},candidates:{},skills:{},episodes:[]}}

class Autonomy{
 constructor(state,opts={}){
  const base=blank(),s=state&&typeof state==='object'?state:{};
  this.state={
    schema:1,
    tools:s.tools&&typeof s.tools==='object'?clone(s.tools):{},
    candidates:s.candidates&&typeof s.candidates==='object'?clone(s.candidates):{},
    skills:s.skills&&typeof s.skills==='object'?clone(s.skills):{},
    episodes:Array.isArray(s.episodes)?clone(s.episodes.slice(-256)):[]
  };
  this.minSuccesses=Math.max(1,Number(opts.minSuccesses||2));
  this.minReward=Number(opts.minReward==null ? .05 : opts.minReward);
  this.minReliability=Number(opts.minReliability==null ? .66 : opts.minReliability);
  this.matchThreshold=Number(opts.matchThreshold==null ? .58 : opts.matchThreshold);
  this.maxSkills=Math.max(8,Number(opts.maxSkills||128));
 }
 syncTools(manifest=[]){
  const t=Date.now();
  for(const x of manifest||[]){
    if(!x||!x.name)continue;
    const n=String(x.name),r=this.state.tools[n]||(this.state.tools[n]={name:n,attempts:0,successes:0,failures:0,rewardSum:0,lastReward:0});
    Object.assign(r,{capability:String(x.capability||'unknown'),description:String(x.description||''),sideEffect:!!x.sideEffect,resource:x.resource||null,executable:x.executable!==false,updatedAt:t});
  }
  return this.status();
 }
 observeTool(name,outcome={}){
  if(outcome.learnable===false)return null;
  const n=String(name||'');if(!n)return null;
  const r=this.state.tools[n]||(this.state.tools[n]={name:n,attempts:0,successes:0,failures:0,rewardSum:0,lastReward:0,capability:'unknown',description:'',sideEffect:false,resource:null,executable:true});
  const ok=!!outcome.ok,reward=clamp(outcome.reward==null?(ok ? .2 : -.5):outcome.reward);
  r.attempts=(+r.attempts||0)+1;r[ok?'successes':'failures']=(+r[ok?'successes':'failures']||0)+1;r.rewardSum=(+r.rewardSum||0)+reward;r.lastReward=reward;r.updatedAt=Date.now();
  if(outcome.quality&&typeof outcome.quality==='object')r.lastQuality=clone(outcome.quality);
  return r
 }
 toolBias(name){
  const r=this.state.tools[String(name||'')];if(!r)return .03;
  const attempts=Math.max(1,+r.attempts||0),rate=(+r.successes||0)/attempts,avg=(+r.rewardSum||0)/attempts,explore=.04/Math.sqrt(attempts);
  return clamp(avg*.10+(rate-.5)*.06+explore,-.18,.18)
 }
 enrichCandidates(list=[]){
  return (list||[]).map(c=>({...c,autonomyBias:this.toolBias(c.tool),excitation:(+c.excitation||0)+this.toolBias(c.tool)}));
 }
 recordEpisode(goal,steps,outcome={}){
  if(outcome.learnable===false||!Array.isArray(steps)||!steps.length)return null;
  const success=!!outcome.success,reward=clamp(outcome.reward==null?(success ? .2 : -.5):outcome.reward),goalNorm=norm(goal),sig=tokens(goal),toolSeq=steps.map(s=>String(s.tool||''));
  const key=hash(goalNorm+'\n'+toolSeq.join('\n')),t=Date.now();
  const row=this.state.candidates[key]||(this.state.candidates[key]={
    id:key,goalPattern:goalNorm,signature:sig,steps:steps.map(s=>({tool:String(s.tool||''),args:templ(s.args||{},String(goal||'')),description:String(s.description||''),maxRetries:Number(s.maxRetries||s.max_retries||2),expectedEffect:String(s.expectedEffect||s.expected_effect||'')})),
    attempts:0,successes:0,failures:0,rewardSum:0,createdAt:t,updatedAt:t
  });
  row.attempts++;row[success?'successes':'failures']++;row.rewardSum+=reward;row.updatedAt=t;
  this.state.episodes.push({time:t,goal:goalNorm,tools:toolSeq,success,reward,source:String(outcome.source||'runtime'),skillId:outcome.skillId||null});if(this.state.episodes.length>256)this.state.episodes=this.state.episodes.slice(-256);
  const avg=row.rewardSum/Math.max(1,row.attempts),rel=row.successes/Math.max(1,row.attempts);
  let promoted=null;
  if(row.successes>=this.minSuccesses&&rel>=this.minReliability&&avg>=this.minReward){
    const id='skill-'+hash(row.goalPattern+'|'+toolSeq.join('|'));
    let s=this.state.skills[id];
    if(!s){
      s=this.state.skills[id]={id,label:toolSeq.join(' → '),goalPattern:row.goalPattern,signature:[...row.signature],steps:clone(row.steps),attempts:row.attempts,successes:row.successes,failures:row.failures,rewardSum:row.rewardSum,createdAt:row.createdAt,updatedAt:t};
      promoted=clone(s);
      this.trim();
    }else Object.assign(s,{attempts:row.attempts,successes:row.successes,failures:row.failures,rewardSum:row.rewardSum,updatedAt:t});
  }
  return promoted
 }
 matchSkill(goal,toolExists){
  const sig=tokens(goal),gn=norm(goal),ranked=[];
  for(const s of Object.values(this.state.skills)){
    if(!s||!Array.isArray(s.steps)||!s.steps.length)continue;
    if(toolExists&&!s.steps.every(x=>toolExists(String(x.tool||''))))continue;
    const attempts=Math.max(1,+s.attempts||0),rel=(+s.successes||0)/attempts,avg=(+s.rewardSum||0)/attempts,sim=s.goalPattern===gn?1:similarity(sig,s.signature||[]);
    const score=sim*(.72+.18*Math.max(.15,rel)+.10*Math.max(.15,(avg+1)/2));
    if(score>=this.matchThreshold)ranked.push({score,s});
  }
  ranked.sort((a,b)=>b.score-a.score);
  if(!ranked.length)return null;
  const {score,s}=ranked[0];
  return{
    id:s.id,score,
    proposal:{
      source:'autonomy-skill:'+s.id,
      confidence:clamp(.58+score*.38,0,.99),
      rationale:'acquired composite skill '+s.id,
      steps:s.steps.map(x=>({tool:String(x.tool),args:instantiate(x.args||{},String(goal||'')),description:String(x.description||'learned skill '+s.id),maxRetries:Number(x.maxRetries||2),expectedEffect:String(x.expectedEffect||'')}))
    }
  }
 }
 status(){
  const skills=Object.values(this.state.skills).map(s=>{const a=Math.max(1,+s.attempts||0);return{...clone(s),reliability:(+s.successes||0)/a,averageReward:(+s.rewardSum||0)/a}}).sort((a,b)=>b.reliability-a.reliability||b.averageReward-a.averageReward);
  const tools=Object.values(this.state.tools).map(r=>{const a=Math.max(1,+r.attempts||0);return{...clone(r),successRate:(+r.successes||0)/a,averageReward:(+r.rewardSum||0)/a,bias:this.toolBias(r.name)}}).sort((a,b)=>b.bias-a.bias);
  return{version:VERSION,discoveredTools:tools.length,candidateSkills:Object.keys(this.state.candidates).length,learnedSkills:skills.length,episodes:this.state.episodes.length,tools:tools.slice(0,24),skills:skills.slice(0,24)}
 }
 snapshot(){return clone(this.state)}
 trim(){
  const arr=Object.values(this.state.skills);if(arr.length<=this.maxSkills)return;
  arr.sort((a,b)=>{const aa=Math.max(1,+a.attempts||0),ba=Math.max(1,+b.attempts||0),ar=(+a.successes||0)/aa,br=(+b.successes||0)/ba,av=(+a.rewardSum||0)/aa,bv=(+b.rewardSum||0)/ba;return br-ar||bv-av||(+b.updatedAt||0)-(+a.updatedAt||0)});
  this.state.skills=Object.fromEntries(arr.slice(0,this.maxSkills).map(x=>[x.id,x]))
 }
}

return{VERSION,Autonomy,create:(state,opts)=>new Autonomy(state,opts),normalizeGoal:norm,tokens,similarity};
});