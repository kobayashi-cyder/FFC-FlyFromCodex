import numpy as np,json,pathlib
x=np.linspace(-1,1,81);test=np.linspace(-.987,.987,103)
def target(t,v):return np.sin(3*v) if t==0 else v*v
# Each expert: 3w+1 params; two experts:6w+2.
# Shared trunk two heads:4h+2; h=1.5w matches scalar parameter count.
def init(seed,width,heads):
 r=np.random.default_rng(seed);return[r.normal(0,.7,width),r.normal(0,.2,width),r.normal(0,.2/np.sqrt(width),(heads,width)),np.zeros(heads)]
def predict(p,t,v):return np.tanh(v[:,None]*p[0]+p[1])@p[2][t]+p[3][t]
def train(p,t,task,steps):
 y=target(task,x);width=len(p[0]);lr=.4/width # Control feature-sum gradient growth with width.
 for _ in range(steps):
  h=np.tanh(x[:,None]*p[0]+p[1]);d=2*(h@p[2][t]+p[3][t]-y)/len(x);dh=d[:,None]*p[2][t]*(1-h*h)
  a=(dh*x[:,None]).sum(0);b=dh.sum(0);c=h.T@d;z=d.sum()
  p[0]-=lr*np.clip(a,-5,5);p[1]-=lr*np.clip(b,-5,5);p[2][t]-=lr*np.clip(c,-5,5);p[3][t]-=lr*np.clip(z,-5,5)
rows=[]
for width in [16,32,64,128]:
 for steps in [400,1600]:
  for seed in range(20):
   shared=init(seed,width*3//2,2);train(shared,0,0,steps);before=float(np.mean((predict(shared,0,test)-target(0,test))**2));train(shared,1,1,steps)
   experts=[init(seed,width,1),init(seed,width,1)];train(experts[0],0,0,steps);train(experts[1],0,1,steps)
   e=[float(np.mean((predict(experts[t],0,test)-target(t,test))**2)) for t in [0,1]];s=[float(np.mean((predict(shared,t,test)-target(t,test))**2)) for t in [0,1]]
   rows.append({'expert_width':width,'parameters':6*width+2,'steps_per_task':steps,'seed':seed,'shared_mse':sum(s)/2,'specialized_mse':sum(e)/2,'shared_A_before':before,'shared_A_after':s[0]})
summary=[]
for width in [16,32,64,128]:
 for steps in [400,1600]:
  a=[r for r in rows if r['expert_width']==width and r['steps_per_task']==steps];summary.append({'expert_width':width,'parameters':6*width+2,'steps_per_task':steps,'shared_mean_mse':float(np.mean([r['shared_mse'] for r in a])),'specialized_mean_mse':float(np.mean([r['specialized_mse'] for r in a])),'specialized_wins':sum(r['specialized_mse']<r['shared_mse'] for r in a),'seeds':20})
r={'scope':'Two sequential tasks: sin(3x), x². Same scalar parameter budget per architecture. Known task router; tanh units. 81 train inputs, 103 heldout interpolation inputs. LR=0.4/width and initial output weight scale=0.2/sqrt(width) differ from previous experiment to control instability; compare within this sweep. Fixed epochs, not equal FLOPs. Not BANC/APK or biological scaling.','summary':summary,'results':rows}
pathlib.Path(__file__).with_name('scale-specialization-results.json').write_text(json.dumps(r,indent=2));print(json.dumps(summary))
