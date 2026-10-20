"""Recreate compact coin-control evidence from native frames sought by video timestamps."""
import sys,json,gzip,struct,hashlib,subprocess,tempfile
from pathlib import Path
import numpy as np
from PIL import Image

root=Path(__file__).resolve().parent.parent
video=Path(sys.argv[1])
pairs={
 'bonus':[[.265,.574,.735,.589],[.595,.775,.675,.797]],
 'reward':[[.179,.350,.824,.368],[.421,.556,.576,.577]],
 'boost-pocket':[[.272,.577,.745,.609],[.256,.740,.739,.769]],
}
training={'bonus':2,'reward':38,'boost-pocket':50,'ad-next':12}
times=[0,4,6,8,10,12.5,13.2,14,16,18,19.8,20.5,21,22,24,25,26,30,32,34,36,37.3,37.6,38.5,39,39.5,40,40.5,41,42,44,46,48,49,49.5,50.3,51,52.5,54]
native=[14,24,38.5,44,50.3,32]

def feature(data,bounds):
 h,w=data.shape[:2];r=np.asarray(bounds,dtype=np.float32)
 left,top,right,bottom=[int(v) for v in r*np.array([w,h,w,h],dtype=np.float32)]
 left,top=max(0,min(w-1,left)),max(0,min(h-1,top));right,bottom=max(left+1,min(w,right)),max(top+1,min(h,bottom))
 rw,rh=right-left,bottom-top;result=[]
 for y in range(16):
  for x in range(16):
   pixel=np.zeros(3,dtype=np.float32)
   for dy in range(3):
    for dx in range(3):
     px=min(rw-1,int((x+(dx+.5)/3)*rw/16));py=min(rh-1,int((y+(dy+.5)/3)*rh/16))
     pixel+=data[top+py,left+px]
   result.extend((pixel/np.float32(9*255)).tolist())
 return result

def utf(out,value):
 b=value.encode('utf-8');out.write(struct.pack('>H',len(b)));out.write(b)

with tempfile.TemporaryDirectory() as tmp:
 images={}
 for t in sorted(set(times+list(training.values()))):
  p=Path(tmp)/f'{t:.1f}.png';subprocess.run(['ffmpeg','-v','error','-ss',str(t),'-i',str(video),'-frames:v','1','-y',str(p)],check=True)
  images[t]=Image.open(p).convert('RGB');assert images[t].size==(1080,2424)
 refs=json.loads((root/'assets/coin-preset.json').read_text())
 refs={k:v for k,v in refs.items() if not k.startswith(('coin-control-','coin-ad-next-'))}
 original_keys=sorted(refs)
 for key,t in training.items():
  if key=='ad-next':continue
  for suffix,size,filter in [('native',(1080,2424),Image.Resampling.NEAREST),('half',(540,1212),Image.Resampling.NEAREST),('smooth',(720,1616),Image.Resampling.BILINEAR)]:
   data=np.asarray(images[t].resize(size,filter));refs[f'coin-control-{key}-6682-{suffix}']=sum([feature(data,b) for b in pairs[key]],[])
 # Component bounds of the ready disk in the native training frame. Its actual
 # triangle-and-bar are retained in the template, rather than accepting any circle.
 next_bounds=[986/1080,205/2424,1049/1080,268/2424]
 for suffix,size,filter in [('native',(1080,2424),Image.Resampling.NEAREST),('half',(540,1212),Image.Resampling.NEAREST),('smooth',(720,1616),Image.Resampling.BILINEAR)]:
  data=np.asarray(images[12].resize(size,filter));refs[f'coin-ad-next-6682-{suffix}']=feature(data,next_bounds)
 (root/'assets/coin-preset.json').write_text(json.dumps(refs,separators=(',',':'))+'\n')
 with (root/'tests/video6682-references.bin').open('wb') as out:
  out.write(struct.pack('>i',len(refs)))
  for key,values in sorted(refs.items()):utf(out,key);out.write(struct.pack('>i',len(values)));out.write(struct.pack('>'+str(len(values))+'f',*values))
 frames=[(str(t),images[t].resize((540,1212),Image.Resampling.NEAREST)) for t in times]
 frames.extend((str(t)+'-native',images[t]) for t in native)
 with gzip.open(root/'tests/video6682-replay.bin.gz','wb',compresslevel=6) as out:
  out.write(struct.pack('>i',len(frames)))
  for name,im in frames:utf(out,name);out.write(struct.pack('>ii',*im.size));out.write(im.tobytes())
 evidence={'source':'6682.mp4','sha256':hashlib.sha256(video.read_bytes()).hexdigest(),'app':'com.kurashiru.coin.drop','width':1080,'height':2424,'duration_seconds':54.545944,
  'timestamp_selection':'ffmpeg -ss seconds before input; VFR recording, no frame-index assumptions','training_seconds':training,'pairs':pairs,'ready_next_bounds':next_bounds,'untouched_original_keys':original_keys,
  'replay_frames':[{'id':name,'size':im.size,'rgb_sha256':hashlib.sha256(im.tobytes()).hexdigest()} for name,im in frames],
  'limits':'Control training timestamps are excluded from the replay frames. Native holdouts, reduced frames, actual overlay/marker, browser/ad/system UI are included. Translated/inset/missing-anchor tests are synthetic. Device identity, new APK input, Chromebook-specific UI and all-daily-done screen are unverified.'}
 (root/'tests/video6682-evidence.json').write_text(json.dumps(evidence,indent=2)+'\n')
print(json.dumps({'new_references':len(refs)-len(original_keys),'replay_frames':len(frames),'native_holdouts':len(native)}))
