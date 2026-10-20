"""Reproduce ordinary farm dialog masks and held-out pixels from 6612.mp4."""
import sys,subprocess,json,gzip,struct,hashlib
from pathlib import Path
import numpy as np
from PIL import Image
root=Path(__file__).resolve().parent.parent
video=Path(sys.argv[1])
width,height=1080,2424
bounds={'close':[[.215,.506,.79,.533],[.40,.648,.60,.676]],'start':[[.215,.759,.435,.784],[.34,.831,.68,.85]]}
def frame(t):
 data=subprocess.check_output(['ffmpeg','-v','error','-ss',str(t),'-i',str(video),'-frames:v','1','-f','rawvideo','-pix_fmt','rgb24','pipe:1'])
 if len(data)!=width*height*3:raise ValueError('unexpected video frame size')
 return Image.frombytes('RGB',(width,height),data)
def sample(image,b):
 w,h=image.size;a=np.asarray(image);b=np.asarray(b,dtype=np.float32);l,t,r,bt=[int(x) for x in b*np.array([w,h,w,h],dtype=np.float32)];rw,rh=r-l,bt-t;out=[]
 for y in range(16):
  for x in range(16):
   color=np.zeros(3,dtype=np.float32)
   for dy in range(3):
    for dx in range(3):color+=a[t+min(rh-1,int((y+(dy+.5)/3)*rh/16)),l+min(rw-1,int((x+(dx+.5)/3)*rw/16))]
   out.extend((color/np.float32(9*255)).tolist())
 return out
def masked(image,white):
 a=np.asarray(image);minimum=a.min(axis=2);maximum=a.max(axis=2);ink=((minimum>=222)&(maximum.astype(int)-minimum.astype(int)<=35)) if white else (maximum<=95)
 return Image.fromarray(np.repeat((ink.astype(np.uint8)*255)[:,:,None],3,axis=2))
refs={}
for kind,t in [('close',66),('start',70)]:
 source=frame(t)
 for w,h in [(1080,2424),(540,1212),(540,1140),(720,1520)]:
  for mode,resample in [('nearest',Image.Resampling.NEAREST),('smooth',Image.Resampling.BILINEAR)]:
   image=masked(source.resize((w,h),resample),kind=='start');refs[f'farm-control-{kind}-{w}x{h}-{mode}']=sum((sample(image,b) for b in bounds[kind]),[])
asset=root/'assets/virtual-home-preset.json';a=json.loads(asset.read_text());a.update(refs);asset.write_text(json.dumps(a,separators=(',',':'))+'\n')
def utf(f,s):
 b=s.encode();f.write(struct.pack('>H',len(b)));f.write(b)
with (root/'tests/farm-dialog-references.bin').open('wb') as f:
 f.write(struct.pack('>i',len(refs)))
 for key,v in refs.items():utf(f,key);f.write(struct.pack('>i',len(v)));f.write(struct.pack('>'+str(len(v))+'f',*v))
times=[20,42,44,65.5,66.2,66.4,68,69.8,70.2,70.4,71,72]
metadata=[]
with gzip.open(root/'tests/farm-dialog-replay.bin.gz','wb',compresslevel=6) as f:
 f.write(struct.pack('>i',len(times)))
 for t in times:
  image=frame(t).resize((720,1616),Image.Resampling.BILINEAR);data=image.tobytes();utf(f,str(t));f.write(struct.pack('>ii',*image.size));f.write(data);metadata.append({'seconds':t,'rgb_sha256':hashlib.sha256(data).hexdigest()})
digest=hashlib.sha256()
with video.open('rb') as f:
 while block:=f.read(1024*1024):digest.update(block)
(root/'tests/farm-dialog-evidence.json').write_text(json.dumps({'source':'6612.mp4','source_sha256':digest.hexdigest(),'source_size':[width,height],'training_seconds':{'close':66,'start':70},'bounds':bounds,'replay_frames':metadata,'limits':'Existing Pixel recording resampled to F-52A dimensions; no real F-52A input or treasure-opening button pixels supplied.'},ensure_ascii=False,indent=2)+'\n')
print(json.dumps({'references':len(refs),'replay_frames':len(times),'fixture_bytes':(root/'tests/farm-dialog-replay.bin.gz').stat().st_size}))
