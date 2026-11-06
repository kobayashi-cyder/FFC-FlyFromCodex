import test from 'node:test';
import assert from 'node:assert/strict';
import {runTaskPlan,runTaskPlanAsync,validateTaskPlan,TASK_AGENT_TOOLS,TASK_AGENT_DEMO,TASK_AGENT_LIMITS} from '../chat_web/task_agent.js';
const run=(steps,ctx={},output)=>runTaskPlan({version:1,steps,output},ctx);
const v=(id,value)=>({id,op:'value',args:{value}});
const ref=id=>({$ref:id});

test('task agent has an explicit offline allowlist and meaningful bounds',()=>{
 assert.ok(TASK_AGENT_TOOLS.length>=20);
 assert.equal(new Set(TASK_AGENT_TOOLS).size,TASK_AGENT_TOOLS.length);
 assert.equal(TASK_AGENT_LIMITS.steps,16);
});
test('four-stage CSV report is executed with trace and correct totals',()=>{
 const r=runTaskPlan(TASK_AGENT_DEMO);
 assert.equal(r.ok,true);assert.match(r.output,/A,2,220/);assert.match(r.output,/B,2,100/);
 assert.deepEqual(r.trace.map(s=>s.op),['value','csv.parse','table.group','csv.stringify']);
});
test('CSV quote and line break parser and Excel formula output guards',()=>{
 const input='name,comment,number\n"Tanaka, A","line 1\nline 2",12\nOther,"quote ""hi""",3';
 const r=run([{id:'rows',op:'csv.parse',args:{text:input}},{id:'csv',op:'csv.stringify',args:{rows:ref('rows')}}]);
 assert.equal(r.ok,true);assert.deepEqual(r.outputs.rows.map(x=>x.number),['12','3']);
 assert.match(r.output,/"Tanaka, A"/);assert.match(r.output,/"line 1\nline 2"/);
 assert.match(r.output,/"quote ""hi"""/);
 const dangerous=run([{id:'out',op:'csv.stringify',args:{rows:[{c:'=HYPERLINK("bad")'},{c:'-1+2'}]}}]);
 assert.equal(dangerous.ok,true);assert.match(dangerous.output,/'=HYPERLINK/);assert.match(dangerous.output,/'-1\+2/);
});
test('table filters, sorts, projects and statistics are composable',()=>{
 const steps=[v('raw','team,total\nA,10\nB,30\nA,25'),{id:'rows',op:'csv.parse',args:{text:ref('raw')}},
 {id:'filtered',op:'table.filter',args:{rows:ref('rows'),column:'total',operator:'gte',value:20}},
 {id:'sorted',op:'table.sort',args:{rows:ref('filtered'),column:'total',numeric:true,descending:true}},
 {id:'projected',op:'table.select',args:{rows:ref('sorted'),columns:['total']}},
 {id:'stats',op:'table.stats',args:{rows:ref('projected'),column:'total'}}];
 const r=run(steps);assert.equal(r.ok,true);assert.deepEqual(r.outputs.sorted.map(x=>x.total),['30','25']);assert.deepEqual(r.output,{column:'total',count:2,sum:55,mean:27.5,min:25,max:30});
});
test('document search provides grounded excerpts and line numbers',()=>{
 const docs=[{title:'Memo A',text:'hello\nprofit 100\nlast'},{title:'Memo B',text:'profit 250\nbye'}];
 const r=run([{id:'x',op:'documents.search',args:{query:'profit'}}],{documents:docs});
 assert.equal(r.ok,true);assert.deepEqual(r.output.map(x=>[x.title,x.line]),[['Memo A',2],['Memo B',1]]);
 const d=run([{id:'x',op:'documents.read',args:{index:1}}],{documents:docs});assert.equal(d.output.content,'profit 250\nbye');
});
test('math, WSP, code and WSP export pass through explicit adapters',()=>{
 const graph={run(v){return {outputs:{y:[v[0]+v[1]]}}},snapshot(){return {name:'test'}}};
 const adapters={calculate:()=>({value:'6'}),graph,generateCode:()=>({ok:true,filename:'f.py',language:'python',code:'pass'}),generateWSPCode:()=>({filename:'wsp.py',language:'python',code:'def predict(x): return x'})};
 const r=run([{id:'num',op:'math.calculate',args:{expression:'2+4'}},{id:'wsp',op:'wsp.run',args:{input:[ref('num'),3]}},{id:'c',op:'code.generate',args:{request:'/code python factorial'}},{id:'exp',op:'wsp.export',args:{language:'python'}}],adapters);
 assert.equal(r.ok,true);assert.equal(r.outputs.wsp.y[0],9);assert.equal(r.output.filename,'wsp.py');
});
test('unknown operations, forward references, circular references and unsafe keys rejected before execution',()=>{
 for(const plan of [
  {version:1,steps:[{id:'x',op:'system.exec',args:{command:'ls'}}]},
  {version:1,steps:[{id:'x',op:'value',args:{value:{$ref:'y'}}},{id:'y',op:'value',args:{value:1}}]},
  {version:1,steps:[{id:'x',op:'value',args:{value:{$ref:'x'}}}]},
  {version:1,steps:[{id:'x',op:'value',args:{value:{constructor:'bad'}}}]},
  {version:1,steps:[{id:'__proto__',op:'value',args:{value:1}}]}
 ])assert.throws(()=>validateTaskPlan(plan));
});
test('explicit assertion gate fails closed and does not run subsequent steps',()=>{
 const result=run([v('x',10),{id:'gate',op:'assert.equal',args:{actual:ref('x'),expected:20}},v('tail','never')]);
 assert.equal(result.ok,false);assert.equal(result.failedAt,'gate');assert.deepEqual(result.trace.map(x=>x.id),['x']);assert.equal(Object.hasOwn(result.outputs,'tail'),false);
});
test('no arbitrary JavaScript syntax, regex or network calls can be evaluated',()=>{
 const r=run([{id:'x',op:'text.replace',args:{text:'a.b a.b',find:'.',replace:'-'}}]);
 assert.equal(r.output,'a-b a-b');
 assert.throws(()=>validateTaskPlan({version:1,steps:[{id:'x',op:'fetch',args:{url:'https://example.com'}}]}));
});
test('error cases stop at step and expose only completed work',()=>{
 const r=run([{id:'first',op:'value',args:{value:3}},{id:'bad',op:'table.stats',args:{rows:[{x:'not numeric'}],column:'x'}},{id:'after',op:'value',args:{value:9}}]);
 assert.equal(r.ok,false);assert.equal(r.failedAt,'bad');assert.equal(r.outputs.first,3);assert.equal(r.outputs.after,undefined);
});
test('spreadsheet injection and malformed CSV are denied',()=>{
 for(const input of ['name,name\na,b','a,b\n1','a\n"not closed','a\n"quoted"trailing','a,b\n1,2,3']){
 const r=run([{id:'r',op:'csv.parse',args:{text:input}}]);assert.equal(r.ok,false,input);
 }
});
test('numeric group rejects nonnumeric values rather than silently skipping',()=>{
 const r=run([{id:'g',op:'table.group',args:{rows:[{a:'A',sum:'bad'}],by:'a',column:'sum'}}]);assert.equal(r.ok,false);
});
test('plan steps are bounded and references cannot traverse prototypes',()=>{
 assert.throws(()=>validateTaskPlan({version:1,steps:Array.from({length:17},(_,i)=>v('a'+i,i))}));
 assert.throws(()=>validateTaskPlan({version:1,steps:[v('x',{a:1}),{id:'y',op:'value',args:{value:{$ref:'x',path:['__proto__']}}}]}));
});
test('read-only mode rejects media side effects',()=>{
 const r=run([{id:'asset',op:'media.generate',args:{kind:'image',prompt:'stars'}}]);assert.equal(r.ok,false);
});
test('async media requires explicit authorization and never silently writes',async()=>{
 const plan={version:1,steps:[{id:'asset',op:'media.generate',args:{kind:'image',prompt:'stars'}}]};let calls=0;
 await assert.rejects(()=>runTaskPlanAsync(plan,{generateMedia:async()=>{calls++;return {name:'x',bytes:new Uint8Array(100)}}}),/許可/);
 assert.equal(calls,0);
 const r=await runTaskPlanAsync(plan,{allowMediaWrites:true,generateMedia:async(kind,opts)=>{calls++;assert.equal(kind,'image');assert.equal(opts.prompt,'stars');return {name:'scene.png',bytes:new Uint8Array(123)};}});
 assert.equal(calls,1);assert.equal(r.ok,true);assert.deepEqual(r.output,{name:'scene.png',bytes:123,kind:'image'});
});
test('overlong plans are refused before execution or side effects',()=>{
 assert.throws(()=>run([{id:'x',op:'value',args:{value:'X'.repeat(155000)}}]),/長すぎ/);
});