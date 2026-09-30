'use strict';

class ToolBusError extends Error {}
class CapabilityDenied extends ToolBusError {}
class InvalidToolRequest extends ToolBusError {}
class UnknownTool extends ToolBusError {}

class SafeMath {
  constructor(text) { this.s = String(text); this.i = 0; if (this.s.length > 256) throw new InvalidToolRequest('expression too long'); }
  skip(){ while (/\s/.test(this.s[this.i] || '')) this.i++; }
  eat(x){ this.skip(); if (this.s.slice(this.i, this.i + x.length) === x) { this.i += x.length; return true; } return false; }
  parse(){ const v=this.expr(); this.skip(); if(this.i!==this.s.length) throw new InvalidToolRequest('unexpected math token'); if(!Number.isFinite(v)) throw new InvalidToolRequest('non-finite result'); return v; }
  expr(){ let v=this.term(); for(;;){ if(this.eat('+')) v+=this.term(); else if(this.eat('-')) v-=this.term(); else return v; } }
  term(){ let v=this.power(); for(;;){ if(this.eat('*')) { if(this.eat('*')) { this.i-=2; return v; } v*=this.power(); } else if(this.eat('/')) v/=this.power(); else if(this.eat('%')) v%=this.power(); else return v; } }
  power(){ let v=this.unary(); if(this.eat('**')) { const e=this.power(); if(Math.abs(e)>32) throw new InvalidToolRequest('power exponent exceeds safe bound'); v=Math.pow(v,e); } return v; }
  unary(){ if(this.eat('+')) return +this.unary(); if(this.eat('-')) return -this.unary(); return this.atom(); }
  atom(){
    this.skip();
    if(this.eat('(')){ const v=this.expr(); if(!this.eat(')')) throw new InvalidToolRequest('missing )'); return v; }
    const tail=this.s.slice(this.i);
    const cm=tail.match(/^(pi|e)\b/i); if(cm){ this.i+=cm[0].length; return cm[1].toLowerCase()==='pi'?Math.PI:Math.E; }
    const fm=tail.match(/^(sqrt|abs|sin|cos|tan|log|exp|round|min|max)\s*\(/i);
    if(fm){ const name=fm[1].toLowerCase(); this.i+=fm[0].length; const args=[this.expr()]; while(this.eat(',')) args.push(this.expr()); if(!this.eat(')')) throw new InvalidToolRequest('missing )'); const f={sqrt:Math.sqrt,abs:Math.abs,sin:Math.sin,cos:Math.cos,tan:Math.tan,log:Math.log,exp:Math.exp,round:Math.round,min:Math.min,max:Math.max}[name]; return f(...args); }
    const nm=tail.match(/^(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][+-]?\d+)?/); if(!nm) throw new InvalidToolRequest('expected number'); this.i+=nm[0].length; return Number(nm[0]);
  }
}

class FlyAgentToolBus {
  constructor({capabilities, minActivation=0.05, graph={}}={}) {
    this.memory = new Map();
    this.graph = graph;
    this.capabilities = new Set(capabilities || ['compute','memory.read','memory.write','graph.read','bridge.propose']);
    this.minActivation = Number(minActivation);
    this.tools = new Map();
    this.idempotency = new Map();
    this.trace = [];
    this.registerDefaults();
  }
  register(spec){ if(!spec.name || this.tools.has(spec.name)) throw new Error('duplicate/invalid tool name: '+spec.name); this.tools.set(spec.name, spec); }
  manifest(){ return [...this.tools.values()].map(({handler,...x})=>({...x, required_args:[...(x.required_args||[])], optional_args:[...(x.optional_args||[])]})).sort((a,b)=>a.name.localeCompare(b.name)); }
  chooseCandidate(candidates){
    const rejected=[], eligible=[];
    for(const raw of candidates){ const c={...raw, args:{...(raw.args||{})}, excitation:+(raw.excitation||0), inhibition:+(raw.inhibition||0), confidence:raw.confidence==null?1:+raw.confidence, source:String(raw.source||'connectome')}; c.activation=(c.excitation-c.inhibition)*c.confidence; if(!this.tools.has(c.tool)){rejected.push({tool:c.tool,reason:'unknown_tool',activation:c.activation});continue;} if(c.activation<this.minActivation){rejected.push({tool:c.tool,reason:'below_threshold',activation:c.activation});continue;} eligible.push(c); }
    if(!eligible.length) return {status:'NO_ACTION',selected:null,rejected};
    eligible.sort((a,b)=>b.activation-a.activation||b.confidence-a.confidence||String(a.tool).localeCompare(String(b.tool)));
    const c=eligible[0]; return {status:'SELECTED',selected:{tool:c.tool,args:c.args,activation:c.activation,excitation:c.excitation,inhibition:c.inhibition,confidence:c.confidence,source:c.source},rejected};
  }
  execute(tool,args={},opts={}){
    const spec=this.tools.get(tool); if(!spec) throw new UnknownTool(tool); if(!this.capabilities.has(spec.capability)) throw new CapabilityDenied('missing capability: '+spec.capability);
    for(const name of spec.required_args||[]) if(!(name in args)) throw new InvalidToolRequest('missing args: '+name);
    const allowed=new Set([...(spec.required_args||[]),...(spec.optional_args||[])]); const unexpected=Object.keys(args).filter(k=>!allowed.has(k)); if(unexpected.length) throw new InvalidToolRequest('unexpected args: '+unexpected.sort().join(', '));
    const idem=opts.idempotencyKey==null||opts.idempotencyKey===''?null:`${tool}:${String(opts.idempotencyKey)}`; if(idem&&this.idempotency.has(idem)) return {...this.idempotency.get(idem),replayed:true};
    const envelope={tool,args:{...args},side_effect:spec.side_effect,dry_run:!!opts.dryRun,idempotency_key:idem}; let result;
    if(opts.dryRun&&spec.side_effect!=='none') result={ok:true,status:'DRY_RUN',request:envelope};
    else { try { result={ok:true,status:'OK',tool,value:spec.handler(args)}; } catch(e){ if(e instanceof ToolBusError) throw e; result={ok:false,status:'ERROR',tool,error:`${e.constructor.name}: ${e.message}`}; } }
    this.trace.push({index:this.trace.length,...envelope,result}); if(idem) this.idempotency.set(idem,{...result}); return result;
  }
  registerDefaults(){
    const reg=(name,description,capability,side_effect,required_args,optional_args,handler)=>this.register({name,description,capability,side_effect,required_args,optional_args,handler});
    reg('compute.math','Evaluate bounded arithmetic without eval().','compute','none',['expression'],[],a=>new SafeMath(a.expression).parse());
    reg('memory.put','Store JSON-safe local session memory.','memory.write','local',['key','value'],[],a=>{ JSON.stringify(a.value); const k=String(a.key); if(!k||k.length>128) throw new InvalidToolRequest('invalid memory key'); this.memory.set(k,a.value); return {stored:k}; });
    reg('memory.get','Read local session memory.','memory.read','none',['key'],['default'],a=>this.memory.has(String(a.key))?this.memory.get(String(a.key)):a.default);
    reg('memory.delete','Delete a local session-memory key.','memory.write','local',['key'],[],a=>{const k=String(a.key),existed=this.memory.has(k);this.memory.delete(k);return{deleted:k,existed};});
    reg('graph.neighbors','Read 1-hop neighbors from the supplied connectome view.','graph.read','none',['node'],['limit'],a=>[...(this.graph[String(a.node)]||[])].slice(0,Math.max(0,Math.min(Number(a.limit??64),512))));
    reg('graph.hops','Breadth-first traversal over a supplied coarse connectome view.','graph.read','none',['node','depth'],['limit'],a=>{const start=String(a.node),depth=Number(a.depth);if(depth<0||depth>3)throw new InvalidToolRequest('depth must be in 0..3');const limit=Math.max(1,Math.min(Number(a.limit??128),512)),seen=new Set([start]);let frontier=[start];const layers=[[start]];for(let d=0;d<depth;d++){const next=[];for(const node of frontier){for(const n of this.graph[node]||[]){const x=String(n);if(!seen.has(x)){seen.add(x);next.push(x);if(seen.size>=limit)break;}}if(seen.size>=limit)break;}layers.push(next);frontier=next;if(!next.length||seen.size>=limit)break;}return{start,layers,visited:seen.size,truncated:seen.size>=limit};});
    reg('bridge.propose','Create a non-executing proposal for browser/OS/native bridges.','bridge.propose','external-proposal',['action'],['target','parameters','reason'],a=>({proposal_only:true,action:String(a.action),target:a.target,parameters:{...(a.parameters||{})},reason:a.reason,execution:'NOT_PERFORMED'}));
    reg('system.manifest','Return available tool contracts.','compute','none',[],[],()=>this.manifest());
  }
}

module.exports={FlyAgentToolBus,ToolBusError,CapabilityDenied,InvalidToolRequest,UnknownTool,SafeMath};

if(require.main===module){
  if(process.argv.includes('--manifest')) process.stdout.write(JSON.stringify(new FlyAgentToolBus().manifest()));
}
