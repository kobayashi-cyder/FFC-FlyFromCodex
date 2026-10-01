const assert = require('node:assert/strict');
const {ConnectomeSelector: Mobile} = require('../app/src/main/assets/proxy-agent-core.js');
const {ConnectomeSelector: Reference} = require('./fixtures/connectome_reference.cjs');

function graph(n = 32) {
  const nodes = Array.from({length:n}, (_,i)=>'node'+i);
  const edges = [];
  const transmitters = ['acetylcholine','gaba','glutamate','serotonin','dh44','unknown'];
  for (let i=0;i<n;i++) for(let j=1;j<=4;j++) edges.push({from:nodes[i],to:nodes[(i+j*j)%n],count:(i*7+j*13)%97,pre_top_nt:transmitters[(i+j)%6]});
  const routes = Array.from({length:8},(_,i)=>({id:'route'+i,source:nodes[i%3],target:nodes[(i+5)%n]}));
  const toolRoutes = Object.fromEntries(routes.map((r,i)=>['tool.'+i,r.id]));
  return {nodes,edges,routes,toolRoutes,source:'synthetic performance fixture; not biological data'};
}
module.exports = {graph};
if(require.main === module) {
  let checks = 0;
  for(const n of [8,32,128]) for(const ticks of [1,2,6,7,32]) {
    const data=graph(n), mobile=new Mobile(data,{ticks}), reference=new Reference(data,{ticks});
    for(let turn=0;turn<12;turn++) {
      const candidates=Array.from({length:8},(_,i)=>({tool:'tool.'+((i+turn)%8),excitation:((i*7+turn)%13+1)/10,confidence:.8,inhibition:turn%3/10,args:{turn}}));
      assert.deepEqual(mobile.select(candidates),reference.select(candidates));checks++;
      if(mobile.last) {
        const tool=mobile.last.tool, tier=turn%3===0?'holdout':'training';
        assert.equal(mobile.reinforce(tool,turn%2?1:-.6,{tier}),reference.reinforce(tool,turn%2?1:-.6,{tier}));
        assert.deepEqual(mobile.snapshot(),reference.snapshot());
      }
    }
    const restored=new Mobile(data,{ticks});restored.restore(mobile.snapshot());
    assert.deepEqual(restored.snapshot(),mobile.snapshot());
    assert.deepEqual(restored.select([{tool:'tool.0'}]),mobile.select([{tool:'tool.0'}]));
  }
  const data=graph(), mobile=new Mobile(data), reference=new Reference(data);
  const inputs=Object.keys(data.toolRoutes).map(tool=>({tool,excitation:1}));
  const buffers=[mobile.activity,mobile.next,mobile.credit,mobile.src,mobile.dst,mobile.weights,mobile.gains];
  const first=mobile.select(inputs), saved=JSON.stringify(first);
  for(let i=0;i<50;i++) mobile.select(inputs);
  assert.equal(JSON.stringify(first),saved,'a later selection must not mutate an earlier trace');
  assert.equal(mobile.stats().traceFrames,51*6,'trace only the winning candidate');
  for(const buffer of buffers) assert.ok([mobile.activity,mobile.next,mobile.credit,mobile.src,mobile.dst,mobile.weights,mobile.gains].includes(buffer),'reuse numeric workspace');
  assert.deepEqual(mobile.select(inputs.slice().reverse()),reference.select(inputs.slice().reverse()));
  assert.equal(mobile.select([{tool:'tool.0',excitation:Infinity}]),null);
  assert.equal(mobile.reinforce('tool.0',1),false,'invalid selection clears reward credit');
  const broken=new Mobile({...data,edges:[]});assert.equal(broken.select(inputs),null);
  const stableData=graph(),stable=new Mobile(stableData),original=new Reference(stableData);
  stableData.edges[0].count=999;stableData.edges.push({from:'node0',to:'node1',count:100});
  assert.deepEqual(stable.select(inputs),original.select(inputs),'compiled topology is isolated from later input-edge edits');
  const special=graph();special.nodes[0]='__proto__';for(const e of special.edges){if(e.from==='node0')e.from='__proto__';if(e.to==='node0')e.to='__proto__'}for(const r of special.routes){if(r.source==='node0')r.source='__proto__'}
  assert.deepEqual(new Mobile(special).select(inputs),new Reference(special).select(inputs));
  const tie=graph();tie.routes[1]={...tie.routes[0],id:'route1'};
  assert.equal(new Mobile(tie).select([{tool:'tool.1'},{tool:'tool.0'}]).tool,'tool.0');
  const shared=new Mobile(tie);shared.select(Array(20).fill({tool:'tool.0'}));
  assert.equal(shared.stats().propagations,2,'reuse identical candidate drive; rerun only winner for trace');
  assert.equal(shared.stats().workspaceBytes,25*tie.nodes.length+36*tie.edges.length+4);
  console.log(`connectome-mobile: PASS (${checks} exact reference comparisons, learning, buffer reuse, trace budget, tie/cut/invalid input)`);
}
