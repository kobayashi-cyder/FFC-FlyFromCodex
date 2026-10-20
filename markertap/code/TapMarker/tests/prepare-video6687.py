"""Rebuild coin evidence and held-out temporal fixtures from 6687.mp4."""
import sys,json,gzip,struct,hashlib,subprocess,tempfile
from pathlib import Path
import numpy as np
from PIL import Image
root=Path(__file__).resolve().parent.parent
video=Path(sys.argv[1])
pairs={'boost-tap2':[[.247,.580,.758,.628],[.287,.740,.705,.767]],'get':[[.289,.166,.715,.193],[.759,.400,.864,.418]]}
training={'boost-tap2':20,'solid-next':35,'outline-next':119,'get':111.5}
times=[5,10,15,25,30,35.5,36,40,45,65,70,90,95,95.5,96,97.3,97.6,98,98.5,99,100,103,104,106,108,109,110,111,111.7,111.8,112,112.1,113,115,120,125,150,153,154,155,156,157,160,165]
native=[15,25,30,35.5,95.5,96,98,103,104,111.7,111.8,112,112.1,120,153,154,155,157]
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
     px=min(rw-1,int((x+(dx+.5)/3)*rw/16));py=min(rh-1,int((y+(dy+.5)/3)*rh/16));pixel+=data[top+py,left+px]
   result.extend((pixel/np.float32(9*255)).tolist())
 return result

def utf(out,value):
 b=value.encode('utf-8');out.write(struct.pack('>H',len(b)));out.write(b)
with tempfile.TemporaryDirectory() as tmp:
 images={}
 for t in sorted(set(times+list(training.values()))):
  p=Path(tmp)/f'{t:.1f}.png';subprocess.run(['ffmpeg','-v','error','-ss',str(t),'-i',str(video),'-frames:v','1','-y',str(p)],check=True)
  images[t]=Image.open(p).convert('RGB');assert images[t].size==(856,1920)
 refs=json.loads((root/'assets/coin-preset.json').read_text());refs={k:v for k,v in refs.items() if '6687' not in k and k!='boost-tap2'};original_keys=sorted(refs)
 refs['boost-tap2']=sum([feature(np.asarray(images[20]),b) for b in pairs['boost-tap2']],[])
 for key,bounds in pairs.items():
  for suffix,size,filter in [('native',(856,1920),Image.Resampling.NEAREST),('half',(428,960),Image.Resampling.NEAREST),('smooth',(720,1616),Image.Resampling.BILINEAR)]:
   data=np.asarray(images[training[key]].resize(size,filter));refs[f'coin-control-{key}-6687-{suffix}']=sum([feature(data,b) for b in bounds],[])
 # Bright glyph component bounds, including the separate vertical bar in the outline variant.
 ad_bounds={'solid-next':[768/856,125/1920,793/856,155/1920],'outline-next':[39/856,177/1920,61/856,197/1920]}
 for key,b in ad_bounds.items():refs[f'coin-ad-next-6687-{key}']=feature(np.asarray(images[training[key]]),b)
 (root/'assets/coin-preset.json').write_text(json.dumps(refs,separators=(',',':'))+'\n')
 with (root/'tests/video6687-references.bin').open('wb') as out:
  out.write(struct.pack('>i',len(refs)))
  for key,values in sorted(refs.items()):utf(out,key);out.write(struct.pack('>i',len(values)));out.write(struct.pack('>'+str(len(values))+'f',*values))
 frames=[(str(t),images[t].resize((428,960),Image.Resampling.NEAREST)) for t in times]
 frames.extend((str(t)+'-native',images[t]) for t in native)
 with gzip.open(root/'tests/video6687-replay.bin.gz','wb',compresslevel=6) as out:
  out.write(struct.pack('>i',len(frames)))
  for name,im in frames:utf(out,name);out.write(struct.pack('>ii',*im.size));out.write(im.tobytes())
 evidence={'source':'6687.mp4','sha256':hashlib.sha256(video.read_bytes()).hexdigest(),'app':'com.kurashiru.coin.drop','width':856,'height':1920,'duration_seconds':168.585743,'training_seconds':training,'pairs':pairs,'ad_glyph_bounds':ad_bounds,'untouched_previous_keys':original_keys,'replay_frames':[{'id':name,'size':im.size,'rgb_sha256':hashlib.sha256(im.tobytes()).hexdigest()} for name,im in frames],'limits':'Training timestamps excluded from replay. Replays use observed frame order and explicit input acknowledgements, not physical input. Missing anchors, translated/inset images, stale frames and alternate timer schedules are synthetic negatives. Recording device/version, new APK device input, additional four games and Chromebook-specific target UIs remain unverified.'}
 (root/'tests/video6687-evidence.json').write_text(json.dumps(evidence,indent=2)+'\n')
print(json.dumps({'new_references':len(refs)-len(original_keys),'replay_frames':len(frames),'native_holdouts':len(native)}))
