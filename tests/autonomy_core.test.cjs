const assert=require('node:assert/strict');
const A=require('../app/src/main/assets/autonomy-core.js');

const auto=new A.Autonomy({}, {minSuccesses:2,minReward:0});
auto.syncTools([
  {name:'image.generate',capability:'image.write',description:'image',executable:true},
  {name:'code.generate',capability:'code.write',description:'code',executable:true},
  {name:'network.read',capability:'network.read',description:'net',executable:false},
]);
assert.equal(auto.status().discoveredTools,3);
assert.equal(auto.state.tools['network.read'].executable,false);

const goal='猫の画像を自動生成して';
const steps=[{tool:'image.generate',args:{prompt:goal},description:'generate image'}];
assert.equal(auto.recordEpisode(goal,steps,{success:true,reward:.4}),null);
const promoted=auto.recordEpisode(goal,steps,{success:true,reward:.6});
assert.ok(promoted);
assert.equal(auto.status().learnedSkills,1);

const match=auto.matchSkill(goal,n=>n==='image.generate');
assert.ok(match);
assert.equal(match.proposal.steps[0].tool,'image.generate');
assert.equal(match.proposal.steps[0].args.prompt,goal);
assert.ok(match.proposal.confidence>.5);

const before=auto.state.tools['image.generate'].attempts||0;
auto.observeTool('image.generate',{ok:true,reward:.7,learnable:false});
assert.equal(auto.state.tools['image.generate'].attempts||0,before);
auto.observeTool('image.generate',{ok:true,reward:.7});
assert.equal(auto.state.tools['image.generate'].attempts,before+1);
assert.ok(auto.toolBias('image.generate')>0);

const enriched=auto.enrichCandidates([{tool:'image.generate',excitation:.5},{tool:'code.generate',excitation:.5}]);
assert.ok(enriched[0].excitation>.5);
assert.equal(A.normalizeGoal('TEST 123'),'test #');
console.log('autonomy-core: PASS');
