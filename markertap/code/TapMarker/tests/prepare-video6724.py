"""Rebuild timestamp-disjoint box-merge image evidence and replay.
Usage: python3 tests/prepare-video6724.py /absolute/path/6724.mp4
"""
from pathlib import Path
from PIL import Image
import json, subprocess, struct, gzip, hashlib, sys
import numpy as np
root=Path(__file__).resolve().parent.parent
video=Path(sys.argv[1]).resolve()
def b(l,t,r,z):return [l/360,t/808,r/360,z/808]
anchors={
 'game':[b(190,71,252,87),b(151,721,207,733)],
 'notice':[b(80,247,285,269),b(311,211,330,237)],
 'title':[b(104,186,273,263),b(151,648,211,669)],
 'bonus':[b(68,280,296,305),b(112,561,253,580)],
 'reward':[b(64,274,297,296),b(148,447,213,468)],
 'claim':[b(151,721,207,733),b(143,128,234,148)],
}
ads=[[b(143,452,220,472),b(133,729,225,747)],[b(30,761,83,777),b(231,777,325,793)],[b(30,761,83,777),b(231,777,325,793)]]
training={'game':'14.0','notice':'1.6','title':'11.8','bonus':'38.8','reward':'47.3','claim':'40.3','ad-0':'4.4','ad-1':'42.4','ad-2':'34.4'}
def frame(t,w=540):
 raw=subprocess.check_output(['ffmpeg','-v','error','-ss',str(t),'-i',str(video),'-frames:v','1','-vf',f'scale={w}:{w*2424//1080}','-pix_fmt','rgb24','-f','rawvideo','-'])
 return Image.frombytes('RGB',(w,w*2424//1080),raw)
def sample(im,box,white=False,dark=False,gray=False):
 a=np.asarray(im,dtype=np.int32);h,w=a.shape[:2]
 if white or dark or gray:
  v=a.max(axis=2)<=180 if dark else (a.min(axis=2)>=130)&(a.max(axis=2)-a.min(axis=2)<=45) if gray else (a.min(axis=2)>=230)&(a.max(axis=2)-a.min(axis=2)<=35);a=np.repeat(v[:,:,None].astype(np.int32)*255,3,axis=2)
 l,t,r,z=[int(np.float32(v)*size) for v,size in zip(box,[w,h,w,h])];r=max(l+1,r);z=max(t+1,z);bw,bh=r-l,z-t
 xs=[l+min(bw-1,int((cell+(sub+.5)/3)*bw/16)) for cell in range(16) for sub in range(3)]
 ys=[t+min(bh-1,int((cell+(sub+.5)/3)*bh/16)) for cell in range(16) for sub in range(3)]
 out=[]
 for y in range(16):
  for x in range(16):out.extend(np.float32(a[np.array(ys[y*3:y*3+3])[:,None],np.array(xs[x*3:x*3+3])[None,:]].sum(axis=(0,1))/(9*255)).tolist())
 return out
refs={key:sum([sample(frame(training[key],1080),box) for box in boxes],[]) for key,boxes in anchors.items()}
for i,boxes in enumerate(ads):
 im=frame(training[f'ad-{i}'],1080)
 for j,name in enumerate(['a','b']):refs[f'ad-{i}-{name}']=sample(im,boxes[j],white=j==1,dark=i==0 and j==0,gray=i>=1 and j==0)
(root/'assets/box-preset.json').write_text(json.dumps(refs,ensure_ascii=False,separators=(',',':')))
with (root/'tests/video6724-references.bin').open('wb') as f:
 f.write(struct.pack('>i',len(refs)))
 for key,values in refs.items():
  k=key.encode();f.write(struct.pack('>H',len(k))+k+struct.pack('>i',len(values))+struct.pack('>'+str(len(values))+'f',*values))
if '--refs-only' in sys.argv[2:]:
 print('Updated reference pixels only; existing disjoint replay is unchanged.');sys.exit(0)
timestamps=[str(i)+'.0' for i in range(53) if i<52 and str(i)+'.0' not in training.values()]
timestamps+=['1.2','2.1','3.5','8.5','10.5','11.3','12.3','13.6','31.5','32.5','33.5','38.6','38.7','38.9','39.2','39.4','39.5','40.6','41.6','42.8','46.5','47.7','48.5','50.5','51.5']
assert not set(timestamps)&set(training.values())
with gzip.open(root/'tests/video6724-replay.bin.gz','wb',compresslevel=6) as f:
 f.write(struct.pack('>i',len(timestamps)+5))
 for timestamp in timestamps:
  im=frame(timestamp);name=timestamp.encode();f.write(struct.pack('>H',len(name))+name+struct.pack('>ii',*im.size)+im.tobytes())
 for timestamp in ['13.6','40.6','47.7','4.0','42.8']:
  im=frame(timestamp,1080);name=(timestamp+'-native').encode();f.write(struct.pack('>H',len(name))+name+struct.pack('>ii',*im.size)+im.tobytes())
provenance={'video':'6724.mp4','sha256':hashlib.sha256(video.read_bytes()).hexdigest(),'training':training,'anchors':anchors,'ad_pairs':ads,'replay_timestamps':timestamps,'native_replays':['13.6','40.6','47.7','4.0','42.8'],'scope':'Observed start, announcement close, central throwing, optional-boost skip, 100-throw claim, reward close, two-family HOME recovery. No unobserved mission list or score-optimizing strategy.','limits':'Timestamp-disjoint frames from the same recording; device and unseen game versions untested.'}
(root/'tests/video6724-evidence.json').write_text(json.dumps(provenance,ensure_ascii=False,indent=2)+'\n')
print('Box references:',len(refs),'timestamp-disjoint replay images:',len(timestamps)+5)
