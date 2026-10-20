"""Recreate package-specific Poipoi evidence and timestamp-disjoint replay from 6725.mp4.
Usage: python3 tests/prepare-video6725.py /absolute/path/6725.mp4 [--refs-only]
"""
from pathlib import Path
from PIL import Image
import json, subprocess, struct, gzip, hashlib, sys
import numpy as np
root=Path(__file__).resolve().parent.parent
video=Path(sys.argv[1]).resolve()
def b(l,t,r,z):return [l/360,t/808,r/360,z/808]
sat=b(147,132,213,149);mission=b(37,631,96,652);title=b(104,134,257,153);back=b(8,134,23,156)
anchors={
 'game':[sat,mission], 'full':[sat,b(135,157,228,184)],
 'offline':[b(109,188,258,255),b(156,524,204,548)],
 'bonus':[b(67,218,296,245),b(112,499,252,520)],
 'missions':[title,back], 'reward':[b(64,273,297,297),b(147,446,213,468)],
 'get':[title,b(263,614,303,636)]
}
ads=[[b(27,751,86,771),b(32,779,106,794)]]*3
training={'game':'25.2','full':'72.6','offline':'0.6','bonus':'13.8','missions':'11.9','reward':'84.05','get':'75.6','ad-0':'6.2','ad-1':'62.0','ad-2':'81.0'}
def frame(t,w=540):
 raw=subprocess.check_output(['ffmpeg','-v','error','-ss',str(t),'-i',str(video),'-frames:v','1','-vf',f'scale={w}:{w*2424//1080}','-pix_fmt','rgb24','-f','rawvideo','-'])
 return Image.frombytes('RGB',(w,w*2424//1080),raw)
def sample(im,box,ink=False):
 a=np.asarray(im,dtype=np.int32);h,w=a.shape[:2]
 if ink:
  v=(a.min(axis=2)>=180)&(a.max(axis=2)-a.min(axis=2)<=45);a=np.repeat(v[:,:,None].astype(np.int32)*255,3,axis=2)
 l,t,r,z=[int(np.float32(v)*size) for v,size in zip(box,[w,h,w,h])];r=max(l+1,r);z=max(t+1,z);bw,bh=r-l,z-t
 xs=[l+min(bw-1,int((cell+(sub+.5)/3)*bw/16)) for cell in range(16) for sub in range(3)];ys=[t+min(bh-1,int((cell+(sub+.5)/3)*bh/16)) for cell in range(16) for sub in range(3)]
 out=[]
 for y in range(16):
  for x in range(16):out.extend(np.float32(a[np.array(ys[y*3:y*3+3])[:,None],np.array(xs[x*3:x*3+3])[None,:]].sum(axis=(0,1))/(9*255)).tolist())
 return out
refs={}
for key,boxes in anchors.items():
 im=frame(training[key],1080);refs[key]=sum([sample(im,box) for box in boxes],[])
 if key=='full':refs['full-ink']=sample(im,boxes[1],True)
for i,boxes in enumerate(ads):
 im=frame(training[f'ad-{i}'],1080)
 for j,name in enumerate(['a','b']):refs[f'ad-{i}-{name}']=sample(im,boxes[j],True)
(root/'assets/poipoi-preset.json').write_text(json.dumps(refs,ensure_ascii=False,separators=(',',':')))
with (root/'tests/video6725-references.bin').open('wb') as f:
 f.write(struct.pack('>i',len(refs)))
 for key,values in refs.items():
  k=key.encode();f.write(struct.pack('>H',len(k))+k+struct.pack('>i',len(values))+struct.pack('>'+str(len(values))+'f',*values))
if '--refs-only' in sys.argv[2:]:print('Updated reference pixels only.');sys.exit(0)
extra=['0.2','0.8','1.2','1.8','2.2','2.8','3.2','3.8','4.2','4.6','5.2','5.8','6.8','7.4','8.4','9.4','10.4','10.8','11.2','11.6','12.2','13.2','13.6','14.2','14.6','15.2','18.8','19.4','20.4','21.4','22.4','23.4','24.4','39.5','40.5','41.5','42.5','43.5','44.5','61.3','62.5','63.5','64.5','65.5','66.5','71.5','72.2','72.9','73.2','73.7','74.2','74.7','75.2','75.5','76.5','77.5','79.5','80.5','81.5','82.2','82.7','83.2','83.7','84.2','84.4','85.2','86.2','87.5','88.5','89.5','90.5','91.5','92.5','93.5','94.5','95.5','96.5','97.5','98.5','99.2']
timestamps=[str(i)+'.0' for i in range(0,99,2)]+extra
timestamps=[t for t in timestamps if t not in training.values()];assert len(set(timestamps))==len(timestamps)
native=['2.2','4.2','13.6','25.2','72.9','75.5','84.2','62.5','81.5']
native=[t for t in native if t not in training.values()]
with gzip.open(root/'tests/video6725-replay.bin.gz','wb',compresslevel=6) as f:
 f.write(struct.pack('>i',len(timestamps)+len(native)))
 for timestamp,w in [(t,540) for t in timestamps]+[(t,1080) for t in native]:
  im=frame(timestamp,w);name=(timestamp+('-native' if w==1080 else '')).encode();f.write(struct.pack('>H',len(name))+name+struct.pack('>ii',*im.size)+im.tobytes())
provenance={'video':'6725.mp4','sha256':hashlib.sha256(video.read_bytes()).hexdigest(),'package':'com.kurashiru.poipoi.usagi','training':training,'anchors':anchors,'ad_pairs':ads,'replay_timestamps':timestamps,'native_replays':native,'scope':'Observed ordinary feeding, offline acquisition without multiplier CM, exact 100.00% and badge detection, visible Get-only mission collection, reward close, continue, and CM/HOME recovery.','limits':'Timestamp-disjoint frames from one recording. Android input, actual rewards, unobserved scrolling, boosts and end-of-day completion are untested.'}
(root/'tests/video6725-evidence.json').write_text(json.dumps(provenance,ensure_ascii=False,indent=2)+'\n')
print('Poipoi refs',len(refs),'replay frames',len(timestamps)+len(native))
