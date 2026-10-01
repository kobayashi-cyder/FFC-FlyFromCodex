const assert=require('node:assert/strict');
const Q=require('../app/src/main/assets/image-quality-core.js');

const ir={kind:'ImageIR',subject:'猫',request:'猫の画像を作成して',style:['photo'],composition:['centered'],lighting:['soft'],quality:'high'};
const variants=Q.expand(ir,12);
assert.equal(variants.length,12);
assert.equal(new Set(variants.map(x=>x.prompt)).size,12);
assert.equal(new Set(variants.map(x=>x.seed)).size,12);
assert.deepEqual(Q.requestedKinds('猫の画像を作成して'),['cat']);

function cand(id,x=480,y=330,scale=1,extra=[]){
  return {id,value:{objects:4+extra.length,scene:{width:960,height:600},sceneDetail:{
    width:960,height:600,mode:'photoish',
    entities:[{id:'cat_01',kind:'cat',x,y,scale,z:y,pose:'idle',depth:'mid'},
      {id:'sun_01',kind:'sun',x:790,y:80,scale:1,z:30,pose:'idle',depth:'back'},
      {id:'cloud_01',kind:'cloud',x:230,y:90,scale:1,z:40,pose:'idle',depth:'back'},...extra]
  }}};
}
const good=Q.evaluate(cand('good'),ir);
assert.equal(good.pass,true);
assert.ok(good.score>=72);

const wrong=Q.evaluate({id:'wrong',value:{objects:3,scene:{width:960,height:600},sceneDetail:{width:960,height:600,mode:'photoish',entities:[
  {kind:'connectome',x:480,y:330,scale:1},{kind:'sun',x:790,y:80,scale:1},{kind:'cloud',x:230,y:90,scale:1}
]}}},ir);
assert.equal(wrong.pass,false);
assert.ok(wrong.hardIssues.includes('subject-missing:cat'));

const broken=Q.evaluate(cand('broken',1800,330,4),ir);
assert.equal(broken.pass,false);
assert.ok(broken.hardIssues.includes('entity-out-of-bounds'));
assert.ok(broken.hardIssues.includes('implausible-scale'));

const ranked=Q.rank([cand('lower',210,470),cand('best',480,330)],ir);
assert.equal(ranked.selected.id,'best');
assert.ok(ranked.passed.length>=1);

const unknown={kind:'ImageIR',subject:'パンダ',request:'パンダの画像を作成して',style:[],composition:[],lighting:[],quality:'normal'};
const defaultGarbage={id:'fallback',value:{objects:3,scene:{width:960,height:600},sceneDetail:{width:960,height:600,mode:'photoish',entities:[
  {kind:'connectome',x:480,y:330,scale:1},{kind:'sun',x:790,y:80,scale:1},{kind:'cloud',x:230,y:90,scale:1}
]}}};
const unsupported=Q.evaluate(defaultGarbage,unknown);
assert.equal(unsupported.pass,false);
assert.ok(unsupported.hardIssues.includes('unsupported-subject'));

const flat=cand('flat');
flat.value.sceneDetail.renderMetrics={samples:1000,meanLuma:127,stdLuma:2,clippedRatio:0,edgeDensity:0.001};
const flatEval=Q.evaluate(flat,ir);
assert.equal(flatEval.pass,false);
assert.ok(flatEval.hardIssues.includes('flat-render'));

const visible={samples:40,stdLuma:14,edgeDensity:.06,dynamicRange:90};
const personIr={kind:'ImageIR',subject:'人物',request:'人物の画像を作成して',style:['photo'],quality:'high'};
const personGood={id:'person-good',value:{objects:3,scene:{width:960,height:600},sceneDetail:{
  width:960,height:600,mode:'photoish',
  entities:[{id:'person_01',kind:'person',x:480,y:350,scale:1,z:350,pose:'stand',depth:'mid'}],
  entityVisual:{person_01:{kind:'person',parts:{head:visible,torso:visible,limbs:visible}}}
}}};
const personEval=Q.evaluate(personGood,personIr);
assert.equal(personEval.pass,true);
assert.equal(personEval.metrics.categoryIntegrity,1);

const personBroken=JSON.parse(JSON.stringify(personGood));
personBroken.id='person-broken';
personBroken.value.sceneDetail.entityVisual.person_01.parts.limbs={samples:40,stdLuma:0,edgeDensity:0,dynamicRange:0};
const personBrokenEval=Q.evaluate(personBroken,personIr);
assert.equal(personBrokenEval.pass,false);
assert.ok(personBrokenEval.hardIssues.includes('category-structure:person:limbs'));

const carIr={kind:'ImageIR',subject:'車',request:'車の画像を作成して',style:['photo'],quality:'high'};
const carBroken={id:'car-broken',value:{objects:1,scene:{width:960,height:600},sceneDetail:{
  width:960,height:600,mode:'photoish',
  entities:[{id:'car_01',kind:'car',x:480,y:400,scale:1,z:400,pose:'idle',depth:'mid'}],
  entityVisual:{car_01:{kind:'car',parts:{body:visible,cabin:visible,wheels:{samples:40,stdLuma:0,edgeDensity:0,dynamicRange:0}}}}
}}};
const carBrokenEval=Q.evaluate(carBroken,carIr);
assert.equal(carBrokenEval.pass,false);
assert.ok(carBrokenEval.hardIssues.includes('category-structure:car:wheels'));

assert.ok(Q.expand(personIr,8)[0].prompt.includes('no extra limbs'));
assert.ok(Q.expand(carIr,8)[0].prompt.includes('aligned wheels'));

console.log('image-quality-core: PASS');
