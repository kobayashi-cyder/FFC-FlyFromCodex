import test from 'node:test';
import assert from 'node:assert/strict';
import {solveWorkflow,workflowMessage,workflowHelp,WORKBENCH_LIMITS} from '../chat_web/wsp_workbench.js';
import {SolZeroEngine} from '../chat_web/engine.js';
import {WSPGraph} from '../chat_web/wsp_graph.js';
const invoice={
 inputs:{prices:[100,200],rate:1.1},
 steps:[{id:'subtotal',op:'sum',args:['$prices']},{id:'total',op:'mul',args:['$subtotal','$rate']},
        {id:'rounded',op:'round',args:['$total',2]}],
 result:'$rounded',
 tests:[{inputs:{prices:[200,300],rate:1.2},expected:600},
        {inputs:{prices:[80,120],rate:1.05},expected:210}]
};
test('composes a numerical workflow with two independent unseen inputs',()=>{
 const r=solveWorkflow(invoice);
 assert.equal(r.result,330);
 assert.deepEqual(r.trace.map(x=>x.id),['subtotal','total','rounded']);
 assert.equal(r.checks.passed,2);
 assert.equal(r.checks.verified,true);
 assert.match(workflowMessage(JSON.stringify(invoice)),/別入力評価 2\/2 合格/);
});
test('a mismatched expected output is visible, never considered verified',()=>{
 const r=solveWorkflow({...invoice,tests:[{inputs:{prices:[200,300],rate:1.2},expected:999}]});
 assert.equal(r.result,330);
 assert.equal(r.checks.verified,false);
 assert.equal(r.checks.results[0].actual,600);
 assert.match(workflowMessage(JSON.stringify({...invoice,tests:[{inputs:{prices:[200,300],rate:1.2},expected:999}]})),/不合格/);
 assert.equal(solveWorkflow({...invoice,tests:[]}).checks.verified,false);
});
test('new arbitrary composition: filter, affine mapping, sorting, uniqueness',()=>{
 const p={inputs:{scores:[4,2,8,4,10],limit:4},
  steps:[{id:'filtered',op:'filter_ge',args:['$scores','$limit']},
         {id:'mapped',op:'map_affine',args:['$filtered',2,1]},
         {id:'unique_list',op:'unique',args:['$mapped']},
         {id:'ordered',op:'sort',args:['$unique_list']}],
  result:'$ordered',
  tests:[{inputs:{scores:[3,3,5,7],limit:4},expected:[11,15]}]};
 const r=solveWorkflow(p);
 assert.deepEqual(r.result,[9,17,21]);assert.equal(r.checks.verified,true);
});
test('non-numeric strings, conditionals and lengths can be composed',()=>{
 const p={inputs:{message:'Hello World',term:'world'},
  steps:[{id:'lower_text',op:'lower',args:['$message']},
         {id:'match',op:'contains',args:['$lower_text','$term']},
         {id:'label',op:'choose',args:['$match','found','missing']},
         {id:'caption',op:'concat',args:['result: ','$label']}],
  result:'$caption',
  tests:[{inputs:{message:'HELLO',term:'world'},expected:'result: missing'}]};
 const r=solveWorkflow(p);assert.equal(r.result,'result: found');assert.equal(r.checks.verified,true);
});
test('the default WSP graph participates as one isolated numerical operator',()=>{
 const graph=new WSPGraph();
 const a={inputs:{bits:[0,1]},steps:[{id:'out',op:'wsp',args:['$bits']},
   {id:'first',op:'count',args:['$out']}],result:'$first'};
 const r=solveWorkflow(a,{graph});assert.ok(r.result>=1);assert.ok(r.trace[0].value.length>=1);
 assert.match(workflowMessage(JSON.stringify(a),{graph}),/WSP連携ワークフロー/);
});
test('unsafe, ambiguous and unsupported steps are refused before executing',()=>{
 const b=(patch={})=>({...invoice,...patch});
 assert.throws(()=>solveWorkflow('not json'),/JSON構文/);
 assert.throws(()=>solveWorkflow(b({steps:[{id:'x',op:'fetch',args:[]}]})),/手順のID/);
 assert.throws(()=>solveWorkflow(b({steps:[{id:'x',op:'add',args:['$later',1]}]})),/未来参照/);
 assert.throws(()=>solveWorkflow(b({steps:[{id:'x',op:'div',args:[10,0]}],result:'$x'})),/ゼロ除算/);
 assert.throws(()=>solveWorkflow(b({steps:[{id:'x',op:'pow',args:[100000,8]}],result:'$x'})),/範囲外/);
 assert.throws(()=>solveWorkflow(b({inputs:{constructor:1},steps:[{id:'x',op:'sum',args:[[1,2]]}],result:'$x'})),/入力名/);
 assert.throws(()=>solveWorkflow(b({inputs:{'__proto__':1}})),/入力変数/); // Missing original fields.
 assert.throws(()=>solveWorkflow(b({steps:Array(21).fill({id:'sum',op:'sum',args:[[1]]})})),/形式/);
 assert.throws(()=>solveWorkflow(b({invalid:true})),/形式/);
 assert.throws(()=>solveWorkflow(b({tests:[{inputs:invoice.inputs,expected:330}]})),/重複/);
 assert.throws(()=>solveWorkflow(b({tests:[{inputs:{prices:[3,4],rate:1},expected:7,tolerance:100}]})),/許容誤差/);
 assert.throws(()=>solveWorkflow(b({steps:[{id:'x',op:'map_affine',args:[[1e12],2,1]}],result:'$x'})),/範囲外/);
 assert.throws(()=>solveWorkflow(b({steps:[{id:'x',op:'sum',args:[Array(65).fill(1)]}],result:'$x'})),/配列/);
});
test('help and chat route safely expose the sandbox workbench without running JS',()=>{
 const e=new SolZeroEngine();const msg=e.reply('/solve '+JSON.stringify(invoice));
 assert.match(msg,/最終結果：330/);assert.match(msg,/2\/2 合格/);
 assert.match(e.reply('/solve help'),/map_affine/);
 assert.match(e.reply('/help'),/\/solve/);
 assert.match(e.reply('/solve {"inputs":{},"steps":[]}'),/拒否/);
 assert.match(workflowHelp(),/任意コード実行なし/);
 assert.equal(WORKBENCH_LIMITS.steps,20);
});
