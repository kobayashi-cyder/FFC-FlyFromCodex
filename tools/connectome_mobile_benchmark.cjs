// CPU benchmark against the unchanged topology-v3 implementation. No AI/model comparison.
const {performance} = require('node:perf_hooks');
const fs=require('node:fs'),vm=require('node:vm'),crypto=require('node:crypto');
const {ConnectomeSelector: Mobile} = require('../app/src/main/assets/proxy-agent-core.js');
const {ConnectomeSelector: Reference} = require('../tests/fixtures/connectome_reference.cjs');
const {graph} = require('../tests/connectome_mobile.test.cjs');
function measure(Kernel,data,candidates) {
  const kernel=new Kernel(data), times=[];
  for(let i=0;i<30;i++)kernel.select(candidates);
  if(global.gc)global.gc();
  const heapStart=process.memoryUsage().heapUsed;let peakHeap=heapStart;
  for(let i=0;i<100;i++) {const t=performance.now();kernel.select(candidates);times.push(performance.now()-t);peakHeap=Math.max(peakHeap,process.memoryUsage().heapUsed)}
  times.sort((a,b)=>a-b);
  return {sampledPeakHeapGrowthBytes:global.gc?peakHeap-heapStart:null,p50Ms:times[50],p95Ms:times[95],numericWorkspaceBytes:kernel.stats?kernel.stats().workspaceBytes:null,traceFramesPerSelection:kernel.stats?kernel.stats().traceFrames/130:candidates.length*6};
}
const html=fs.readFileSync('app/src/main/assets/index.html','utf8');
const csv=html.match(/const RAW=\{[\s\S]*?edges:`([\s\S]*?)`/)[1];
const edges=csv.trim().split('\n').slice(1).map(line=>{const [from,to,count,...nt]=line.split(',');return{from,to,count:+count,pre_top_nt:nt.join(',').replaceAll('"','')}});
const routes=vm.runInNewContext(html.match(/const O1P_SHORTCUTS=(\[[\s\S]*?\]);/)[1]);
const toolRoutes=vm.runInNewContext('('+html.match(/const AGENT_ROUTE_ROLE=Object.freeze\((\{[\s\S]*?\})\);/)[1]+')');
const embedded={nodes:[...new Set(edges.flatMap(e=>[e.from,e.to]))],edges,routes,toolRoutes,source:'embedded BANC Figure 3g aggregate'};
const results=[];
for(const [label,data] of [['embedded BANC',embedded],...[128,512].map(n=>['synthetic '+n,graph(n)])]) {
  for(const count of [1,8]) {
  const candidates=Object.keys(data.toolRoutes).slice(0,count).map((tool,i)=>({tool,excitation:.5+i/10}));
  const before=measure(Reference,data,candidates),after=measure(Mobile,data,candidates);
  results.push({label,nodes:data.nodes.length,edges:data.edges.length,candidates:candidates.length,before,after,p50Speedup:before.p50Ms/after.p50Ms});
  }
}
const {ConnectomeMultiplexer}=require('../app/src/main/assets/proxy-agent-core.js');
const multiplex=new ConnectomeMultiplexer(embedded,{maxLanes:8});
const jobs=Array.from({length:8},(_,i)=>({lane:'thread-'+i,candidates:[{tool:'micro.compile'}]}));
multiplex.selectMany(jobs);
const sharedWorkspace=multiplex.stats().workspaceBytes;
const isolatedWorkspace=new Mobile(embedded).stats().workspaceBytes*8;
console.log(JSON.stringify({baselineCommit:'3e9e8cdc3a632489b768c2b9a974cbfc0e84d574',measuredAt:new Date().toISOString(),kernelSha256:crypto.createHash('sha256').update(fs.readFileSync('app/src/main/assets/proxy-agent-core.js')).digest('hex'),multiplex:{lanes:8,sharedWorkspaceBytes:sharedWorkspace,eightIndependentWorkspacesBytes:isolatedWorkspace,savedFraction:1-sharedWorkspace/isolatedWorkspace},runtime:process.version,platform:process.platform,architecture:process.arch,samples:100,results,note:'Host CPU results, not Android latency, process RSS, battery usage or AI task accuracy. Workspace excludes graph metadata and retained result objects. Heap growth is sampled after selections with --expose-gc; it includes garbage awaiting collection and is not total app memory.'},null,2));
