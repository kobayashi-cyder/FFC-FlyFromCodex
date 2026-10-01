(function(root,factory){
  const api=factory();
  if(typeof module==='object'&&module.exports)module.exports=api;
  root.FFCFeedback=api;
})(typeof globalThis!=='undefined'?globalThis:this,function(){
'use strict';

const TIERS=Object.freeze({TRAINING:'training',REGRESSION:'regression',HOLDOUT:'holdout'});
const SCALE=Object.freeze({training:1,regression:.35,holdout:0});
const clamp=(x,a=-1,b=1)=>Math.max(a,Math.min(b,Number(x)||0));
const metric=v=>clamp((Number(v)||0)*2-1);
const qualityScore=s=>clamp(((Number(s)||0)-72)/28);
const gainScore=g=>clamp((Number(g)||0)/30);
const tierOf=t=>Object.values(TIERS).includes(t)?t:TIERS.TRAINING;

function normalize(v={}){
  return{
    success:clamp(v.success),
    quality:clamp(v.quality),
    qualityGain:clamp(v.qualityGain),
    novelty:clamp(v.novelty,0,1),
    regression:clamp(v.regression,0,1),
    hardFailure:clamp(v.hardFailure,0,1),
    fidelity:clamp(v.fidelity),
    composition:clamp(v.composition),
    structure:clamp(v.structure),
    render:clamp(v.render),
    details:v.details&&typeof v.details==='object'?v.details:{}
  };
}
function scalar(v){
  v=normalize(v);
  return clamp(
    .18*v.success+
    .20*v.quality+
    .12*v.qualityGain+
    .05*v.novelty+
    .12*v.fidelity+
    .08*v.composition+
    .12*v.structure+
    .05*v.render-
    .18*v.regression-
    .35*v.hardFailure
  );
}
function fromQuality(quality={},opts={}){
  const tier=tierOf(opts.tier),metrics=quality.metrics&&typeof quality.metrics==='object'?quality.metrics:{};
  const hard=[...(quality.hardIssues||[])];
  let gain=quality.evolutionGain;
  if(gain==null&&opts.previousScore!=null)gain=(Number(quality.score)||0)-(Number(opts.previousScore)||0);
  const vector=normalize({
    success:quality.pass?1:-.65,
    quality:qualityScore(quality.score),
    qualityGain:gainScore(gain),
    novelty:quality.novelty||0,
    regression:quality.regression?1:0,
    hardFailure:hard.length?1:0,
    fidelity:metric(metrics.subjectCoverage),
    composition:metric(metrics.composition),
    structure:metric(metrics.categoryIntegrity),
    render:metric(metrics.render),
    details:{score:quality.score,hardIssues:hard,issues:[...(quality.issues||[])]}
  });
  return event(opts.action||'delegate',opts.source||'quality',tier,vector,{quality});
}
function fromTest(name,passed,opts={}){
  const tier=tierOf(opts.tier||TIERS.TRAINING);
  const vector=normalize({
    success:passed?1:-1,
    quality:passed?.25:-.45,
    novelty:opts.novelty||0,
    regression:tier===TIERS.REGRESSION&&!passed?1:0,
    hardFailure:passed?0:.85,
    details:{test:name,failure:opts.failure||null}
  });
  return event(opts.action||'delegate','test:'+name,tier,vector,{passed:!!passed,failure:opts.failure||null});
}
function event(action,source,tier,vector,metadata={}){
  tier=tierOf(tier);vector=normalize(vector);
  return{
    action:String(action||'delegate'),
    source:String(source||'feedback'),
    tier,
    vector,
    reward:scalar(vector),
    learnable:SCALE[tier]>0,
    learningScale:SCALE[tier],
    metadata
  };
}
function stimuli(ev){
  const v=normalize(ev?.vector||{}),r=scalar(v);
  return[
    {channel:'feedback_quality',value:Math.max(0,r),salience:1},
    {channel:'feedback_error',value:Math.max(0,-r),salience:1},
    {channel:'novelty',value:Math.max(0,v.novelty),salience:.6},
    {channel:'feedback_regression',value:v.regression,salience:1},
    {channel:'feedback_hard_failure',value:v.hardFailure,salience:1}
  ];
}
return{TIERS,SCALE,normalize,scalar,fromQuality,fromTest,event,stimuli};
});
