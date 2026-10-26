"""Executable review loop for a synthetic two-layer circuit; not BANC/APK."""
import copy, hashlib, json, pathlib
import numpy as np

def digest(state):
 return hashlib.sha256(json.dumps(state,sort_keys=True,separators=(',',':'),allow_nan=False).encode()).hexdigest()

def operate(model,x):
 return np.tanh(np.asarray(x)[:,None]*np.array(model['input_weights'])+np.array(model['biases']))@np.array(model['output_weights'])+model['output_bias']

def learn(state,x,y,steps):
 p=state['model'];a=np.array(p['input_weights']);b=np.array(p['biases']);v=np.array(p['output_weights']);c=p['output_bias']
 for _ in range(steps):
  h=np.tanh(x[:,None]*a+b);d=2*(h@v+c-y)/len(x);dh=d[:,None]*v*(1-h*h)
  da=(dh*x[:,None]).sum(0);db=dh.sum(0);dv=h.T@d;dc=d.sum()
  a-=.01*np.clip(da,-5,5);b-=.01*np.clip(db,-5,5);v-=.01*np.clip(dv,-5,5);c-=.01*np.clip(dc,-5,5)
 p.update(input_weights=a.tolist(),biases=b.tolist(),output_weights=v.tolist(),output_bias=float(c));state['updates']+=steps;state['unit_update_cost']+=steps*len(a)

class ReviewLoop:
 def __init__(self,seed=0):
  rng=np.random.default_rng(seed);w=8
  self.state={'schema':1,'model':{'ids':list(range(w)),'input_weights':rng.normal(0,.7,w).tolist(),'biases':rng.normal(0,.2,w).tolist(),'output_weights':rng.normal(0,.05,w).tolist(),'output_bias':0.},'next_id':w,'updates':0,'unit_update_cost':0}
  self.history=[copy.deepcopy(self.state)];self.proposals={};self.events=[];self.spent_cost=0
 def metrics(self,state,x,y):
  p=state['model'];pred=operate(p,x);mse=float(np.mean((pred-y)**2));faults=[]
  for seed in range(8):
   q=copy.deepcopy(p);rng=np.random.default_rng(seed);indices=rng.choice(len(q['ids']),max(1,len(q['ids'])//10),replace=False)
   for i in indices:q['output_weights'][int(i)]=0
   faults.append(float(np.mean((operate(q,x)-y)**2)))
  return {'mse':mse,'fault_mse':float(np.mean(faults)),'units':len(p['ids']),'connections':2*len(p['ids'])}
 def propose(self,name,kind,x,y,steps=200):
  if name in self.proposals:raise ValueError('proposal name already exists')
  if not isinstance(steps,int) or not 0<=steps<=2000:raise ValueError('invalid steps')
  base=digest(self.state);candidate=copy.deepcopy(self.state);p=candidate['model'];w=len(p['ids'])
  if kind=='grow':
   if w*2>32:raise ValueError('growth limit')
   p['ids']+=list(range(candidate['next_id'],candidate['next_id']+w));candidate['next_id']+=w
   p['input_weights']*=2;p['biases']*=2;p['output_weights']=[v/2 for v in p['output_weights']]*2
  elif kind=='prune':
   if w<=4:raise ValueError('minimum units')
   h=np.tanh(x[:,None]*np.array(p['input_weights'])+np.array(p['biases']));importance=np.std(h*np.array(p['output_weights']),axis=0);keep=sorted(np.argsort(importance)[w//4:].tolist())
   for key in ['ids','input_weights','biases','output_weights']:p[key]=[p[key][i] for i in keep]
  elif kind!='learn':raise ValueError('unknown generation rule')
  cost=steps*len(p['ids'])
  if self.spent_cost+cost>30000:raise ValueError('total experiment budget exceeded')
  learn(candidate,x,y,steps);self.spent_cost+=cost
  self.proposals[name]={'base':base,'candidate':candidate,'kind':kind,'review':None};self.events.append({'event':'proposal','name':name,'kind':kind,'base':base,'cost':cost});return copy.deepcopy(candidate)
 def review(self,name,x,y):
  proposal=self.proposals[name]
  if proposal['base']!=digest(self.state):raise ValueError('stale base: rebase and reevaluate')
  before=self.metrics(self.state,x,y);after=self.metrics(proposal['candidate'],x,y)
  accepted=all(np.isfinite(v) for v in after.values()) and after['mse']<=before['mse']*1.02+1e-5 and after['fault_mse']<=before['fault_mse']*1.1+1e-5
  proposal['review']={'candidate_hash':digest(proposal['candidate']),'accepted':bool(accepted),'before':before,'after':after};self.events.append({'event':'review','name':name,**proposal['review']});return copy.deepcopy(proposal['review'])
 def merge(self,name):
  p=self.proposals[name]
  if p['base']!=digest(self.state):raise ValueError('stale base: rebase and reevaluate')
  if not p['review'] or not p['review']['accepted']:raise ValueError('review did not approve')
  if digest(p['candidate'])!=p['review']['candidate_hash']:raise ValueError('candidate changed after review')
  self.state=copy.deepcopy(p['candidate']);self.history.append(copy.deepcopy(self.state));self.events.append({'event':'merge','name':name,'revision':len(self.history)-1});return digest(self.state)
 def revert(self,revision):
  if not isinstance(revision,int) or not 0<=revision<len(self.history):raise ValueError('invalid revision')
  self.state=copy.deepcopy(self.history[revision]);self.history.append(copy.deepcopy(self.state));self.events.append({'event':'revert','to_revision':revision,'revision':len(self.history)-1});return digest(self.state)
 @classmethod
 def load(cls,path):
  data=json.loads(pathlib.Path(path).read_text());obj=cls()
  for state in [data['state'],*data['history'],*(p['candidate'] for p in data['proposals'].values())]:
   m=state['model'];w=len(m['ids'])
   if not 4<=w<=32 or len(set(m['ids']))!=w or any(len(m[k])!=w for k in ['input_weights','biases','output_weights']):raise ValueError('invalid model shape')
   if any(not np.all(np.isfinite(m[k])) for k in ['input_weights','biases','output_weights']) or not np.isfinite(m['output_bias']):raise ValueError('invalid model weights')
   if state['next_id']<=max(m['ids']):raise ValueError('invalid id counter')
  if not 0<=data['spent_cost']<=30000:raise ValueError('invalid budget')
  obj.state=data['state'];obj.history=data['history'];obj.proposals=data['proposals'];obj.events=data['events'];obj.spent_cost=data['spent_cost'];return obj
 def save(self,path):
  pathlib.Path(path).write_text(json.dumps({'state':self.state,'history':self.history,'proposals':self.proposals,'events':self.events,'spent_cost':self.spent_cost},indent=2,allow_nan=False))

if __name__=='__main__':
 root=pathlib.Path(__file__).parent;loop=ReviewLoop();x=np.linspace(-1,1,81);y=np.sin(3*x)+.3*x*x;rng=np.random.default_rng(42);vx=rng.uniform(-1,1,61);vy=np.sin(3*vx)+.3*vx*vx;tx=np.linspace(-.991,.991,151);ty=np.sin(3*tx)+.3*tx*tx
 initial=loop.metrics(loop.state,tx,ty)
 for name,kind in [('learn-1','learn'),('grow-1','grow'),('prune-1','prune')]:
  loop.propose(name,kind,x,y,400);review=loop.review(name,vx,vy)
  if review['accepted']:loop.merge(name)
 final=loop.metrics(loop.state,tx,ty);loop.save(root/'integrated-checkpoint.json');result={'scope':'Synthetic feedforward tanh circuit. Generation, execution, supervised SGD, PR-style review, adoption and full-state reversion integrated. Validation governs acceptance; final test inputs separate. Fault metric zeros outputs of random units, not graph topology failure. Not BANC, biological rules, free conversation or APK.','initial_test':initial,'final_test':final,'events':loop.events,'spent_unit_update_cost':loop.spent_cost};(root/'integrated-results.json').write_text(json.dumps(result,indent=2));print(json.dumps({'initial_test':initial,'final_test':final,'decisions':[(e['name'],e['accepted']) for e in loop.events if e['event']=='review']}))
