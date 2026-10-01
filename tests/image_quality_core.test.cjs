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

console.log('image-quality-core: PASS');
