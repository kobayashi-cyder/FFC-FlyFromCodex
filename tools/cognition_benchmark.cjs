'use strict';
// Deterministic, synthetic *planner* benchmark. This is not an AGI evaluation.
const CG=require('../app/src/main/assets/cognition-core.js');
const COUNT=96;
const generator=(seed)=>{
 let rand=seed>>>0;
 const next=()=>{rand=(Math.imul(rand,1664525)+1013904223)>>>0;return rand};
 const depth=3+(seed%5),actions=[];
 for(let i=0;i<depth;i++){
  actions.push({id:'advance-'+i,tool:'tool.sim',requires:{stage:i},effects:{stage:i+1},cost:1,args:{}});
  actions.push({id:'trap-'+i,tool:'tool.sim',requires:{stage:i},effects:{stage:-1},cost:.2,args:{}});
  actions.push({id:'detour-'+i,tool:'tool.sim',requires:{stage:i},effects:{stage:-2},cost:.3,args:{}});
 }
 // Permute action order so the search is not a hardcoded index recipe.
 for(let i=actions.length-1;i>0;i--){const j=next()%(i+1);[actions[i],actions[j]]=[actions[j],actions[i]]}
 return{schema:1,facts:{stage:0},goal:{completed:true},actions,rules:[{id:'finish',if:{stage:depth},then:{completed:true}}],maxStates:1200,maxDepth:12};
};
const summary={version:1,benchmark:'synthetic finite-state planning holdout',seedRange:[401,496],scenarios:COUNT,planned:0,observedDone:0,greedySuccess:0,missingObservationDone:0,totalExpanded:0,maxExpanded:0,totalSteps:0};
async function main(){
 for(let seed=401;seed<401+COUNT;seed++){
  const task=generator(seed),planned=CG.plan(task);
  if(planned.status!=='planned')throw new Error('unsolved generated task '+seed+': '+planned.reason);
  summary.planned++;summary.totalExpanded+=planned.expanded;summary.maxExpanded=Math.max(summary.maxExpanded,planned.expanded);
  summary.totalSteps+=planned.steps.length;
  const adapter={
   authorize:()=>true,select:action=>({tool:action.tool,connectome:{route:'synthetic-allow'}}),
   invoke:action=>({ok:true,observedFacts:{...action.effects}})
  };
  const solved=await CG.solve(task,adapter,{execute:true,maxSteps:12});
  if(solved.status!=='done')throw new Error('observed solve failed seed '+seed+': '+solved.reason);
  summary.observedDone++;
  const unknown=await CG.solve(task,{...adapter,invoke:()=>({ok:true})},{execute:true});
  if(unknown.status==='done')summary.missingObservationDone++;
  let facts={...task.facts},greedyDone=false;
  for(let i=0;i<12;i++){
   const inferred=CG.infer({...task,facts});if(inferred.goalSatisfied){greedyDone=true;break}
   const available=task.actions.filter(a=>Object.entries(a.requires).every(([k,v])=>inferred.facts[k]===v)).sort((a,b)=>a.cost-b.cost);
   if(!available.length)break;
   facts={...facts,...available[0].effects};
  }
  if(greedyDone)summary.greedySuccess++;
 }
 summary.meanExpanded=+(summary.totalExpanded/COUNT).toFixed(3);
 summary.meanPlanSteps=+(summary.totalSteps/COUNT).toFixed(3);
 summary.passRate=summary.observedDone/COUNT;
 if(summary.missingObservationDone!==0)throw new Error('unobserved actions may never report done');
 console.log(JSON.stringify(summary,null,2));
}
main().catch(e=>{console.error(e);process.exitCode=1});
