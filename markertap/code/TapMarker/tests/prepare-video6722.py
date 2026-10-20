"""Rebuild paired ad evidence and disjoint CM/HOME replay pixels from 6722.mp4.
Usage: python3 tests/prepare-video6722.py /absolute/path/6722.mp4
"""
from pathlib import Path
from PIL import Image
import json, subprocess, struct, gzip, hashlib, sys
import numpy as np
root=Path(__file__).resolve().parent.parent
video=Path(sys.argv[1]).resolve()
HEADERS=[[48/1080,230/2424,112/1080,298/2424]]*2
LABELS=[[46/1080,2348/2424,105/1080,2379/2424],[16/1080,2023/2424,74/1080,2057/2424]]
# Both training timestamps are excluded from the replay set.
training=['1.0','61.0']
def sample(im,box):
 a=np.asarray(im,dtype=np.int32)
 rgb=a.min(axis=2)>=230
 rgb &= a.max(axis=2)-a.min(axis=2)<=35
 h,w=rgb.shape
 b=[int(np.float32(box[0])*w),int(np.float32(box[1])*h),int(np.float32(box[2])*w),int(np.float32(box[3])*h)]
 b[2]=max(b[0]+1,b[2]);b[3]=max(b[1]+1,b[3])
 bw,bh=b[2]-b[0],b[3]-b[1]
 xs=[b[0]+min(bw-1,int((cell+(sub+.5)/3)*bw/16)) for cell in range(16) for sub in range(3)]
 ys=[b[1]+min(bh-1,int((cell+(sub+.5)/3)*bh/16)) for cell in range(16) for sub in range(3)]
 out=[]
 for y in range(16):
  for x in range(16):
   v=float(np.float32(sum(int(rgb[ys[y*3+dy],xs[x*3+dx]]) for dy in range(3) for dx in range(3))/9))
   out.extend([v]*3)
 return out
refs=json.loads((root/'assets/coin-preset.json').read_text())
for i,timestamp in enumerate(training):
 raw=subprocess.check_output(['ffmpeg','-hide_banner','-loglevel','error','-ss',timestamp,'-i',str(video),'-frames:v','1','-pix_fmt','rgb24','-f','rawvideo','-'])
 im=Image.frombytes('RGB',(1080,2424),raw)
 for kind,box in [('header',HEADERS[i]),('label',LABELS[i])]:refs[f'coin-ad-home-{i}-{kind}']=sample(im,box)
(root/'assets/coin-preset.json').write_text(json.dumps(refs,ensure_ascii=False,separators=(',',':')))
# A complete reference fixture, so host tests exercise exactly what the APK reads.
with (root/'tests/video6722-references.bin').open('wb') as f:
 f.write(struct.pack('>i',len(refs)))
 for key,values in refs.items():
  k=key.encode();f.write(struct.pack('>H',len(k))+k+struct.pack('>i',len(values)))
  f.write(struct.pack('>'+str(len(values))+'f',*values))
timestamps=['0','0.6','1.4','2.2','3.0','3.6','4.2','5','7','12','14','20','21','22','25','27','33','34','37','38','40','42','43','44','47','48','49','50','54.8','55.5','56.5','57.2','58','60','60.4','60.8','61.4','61.8','62.2','62.6','62.95','63.15','63.45','64','65','68','69','70','71','72']
assert not set(timestamps) & set(training)
images=[]
for timestamp in timestamps:
 raw=subprocess.check_output(['ffmpeg','-hide_banner','-loglevel','error','-ss',timestamp,'-i',str(video),'-frames:v','1','-vf','scale=540:1212','-pix_fmt','rgb24','-f','rawvideo','-'])
 assert len(raw)==540*1212*3,(timestamp,len(raw))
 images.append((timestamp,540,1212,raw))
# Native-size positives and negatives exercise pixel bounds, sampling and coordinates.
for timestamp in ['1.4','61.4','55.5','63.45','64']:
 raw=subprocess.check_output(['ffmpeg','-hide_banner','-loglevel','error','-ss',timestamp,'-i',str(video),'-frames:v','1','-pix_fmt','rgb24','-f','rawvideo','-'])
 assert len(raw)==1080*2424*3
 images.append((timestamp+'-native',1080,2424,raw))
with gzip.open(root/'tests/video6722-replay.bin.gz','wb',compresslevel=6) as f:
 f.write(struct.pack('>i',len(images)))
 for name,w,h,raw in images:
  k=name.encode();f.write(struct.pack('>H',len(k))+k+struct.pack('>ii',w,h)+raw)
provenance={'video':'6722.mp4','sha256':hashlib.sha256(video.read_bytes()).hexdigest(),'training':training,'header_bounds':HEADERS,'label_bounds':LABELS,'holdout_timestamps':timestamps,'native_holdouts':['1.4','61.4','55.5','63.45','64'],'purpose':'Feedback glyph plus Advertising label authorize HOME, never an ad click. Timestamp-disjoint replay.','limits':'Only the supplied ad family authorizes early HOME. Unknown ads retain existing close/back/recovery. Device interaction unverified.'}
(root/'assets/cm-home-provenance.json').write_text(json.dumps(provenance,ensure_ascii=False,indent=2)+'\n')
print('created',len(images),'replay frames and',len(refs),'reference entries')
