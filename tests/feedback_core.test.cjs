const assert=require('node:assert/strict');
const F=require('../app/src/main/assets/feedback-core.js');

const good=F.fromQuality({
  pass:true,score:88,evolutionGain:12,hardIssues:[],
  metrics:{subjectCoverage:1,composition:.9,categoryIntegrity:1,render:.8}
},{action:'image.generate',tier:F.TIERS.TRAINING});
assert.equal(good.learnable,true);
assert.ok(good.reward>0);
assert.ok(F.stimuli(good).some(x=>x.channel==='feedback_quality'&&x.value>0));

const bad=F.fromQuality({
  pass:false,score:40,hardIssues:['category-structure:cat:tail'],
  metrics:{subjectCoverage:.4,composition:.2,categoryIntegrity:.3,render:.2}
},{action:'image.generate',tier:F.TIERS.TRAINING});
assert.ok(bad.reward<0);
assert.equal(bad.vector.hardFailure,1);

const regression=F.fromTest('image_quality_core',false,{tier:F.TIERS.REGRESSION});
assert.equal(regression.learnable,true);
assert.equal(regression.learningScale,.35);
assert.equal(regression.vector.regression,1);

const holdout=F.fromTest('secret_eval',false,{tier:F.TIERS.HOLDOUT});
assert.equal(holdout.learnable,false);
assert.equal(holdout.learningScale,0);

console.log('feedback-core: PASS');
