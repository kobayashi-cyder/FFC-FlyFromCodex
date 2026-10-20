"""Lossless observed-frame fixtures and paired shopping evidence from 6717.mp4."""
import gzip,hashlib,json,struct,subprocess,sys
from pathlib import Path
from PIL import Image

root=Path(__file__).resolve().parent.parent
video=Path(sys.argv[1]).resolve()
cache=Path(sys.argv[2]).resolve() if len(sys.argv)>2 else root/'build/video6717'
cache.mkdir(parents=True,exist_ok=True)
anchors={
 'home':[[.118,.130,.246,.144],[.159,.777,.266,.794]],
 'resume':[[.318,.551,.690,.589],[.595,.697,.725,.722]],
 'reward':[[.28,.45,.435,.485],[.42,.547,.585,.568]],
 'boost':[[.235,.523,.780,.572],[.458,.608,.546,.630]],
 'get':[[.289,.165,.719,.192],[.776,.398,.861,.416]],
 'play':[[.289,.165,.719,.192],[.776,.398,.861,.416]]}
training={'home':1.0,'resume':117.0,'reward':167.8,'boost':121.8,'get':94.1,'play':95.9,'arrow':74.0}
times=[0,3,5,34,36,36.5,40,44,46,58,70,73,75,78,80,82,90,94,95.2,95.5,95.8,96,112,116,118,120,122,126,154,156,164,166,167.5,167.7,168,169,169.5,170,171,174,180,182,183]
native={0,73,80,118,168,170}
def image(t):
 path=cache/f'raw-{t:07.3f}.png'
 if not path.exists():subprocess.run(['ffmpeg','-v','error','-ss',str(t),'-i',str(video),'-frames:v','1','-y',str(path)],check=True)
 im=Image.open(path).convert('RGB');assert im.size==(1080,2424)
 return im
def feature(im,roi):
 w,h=im.size;l,t,r,b=[int(v*n) for v,n in zip(roi,[w,h,w,h])];l,t=max(0,min(w-1,l)),max(0,min(h-1,t));r,b=max(l+1,min(w,r)),max(t+1,min(h,b));rw,rh=r-l,b-t;pix=im.load();out=[]
 for gy in range(16):
  for gx in range(16):
   total=[0,0,0]
   for dy in range(3):
    for dx in range(3):
     x=l+min(rw-1,int((gx+(dx+.5)/3)*rw/16));y=t+min(rh-1,int((gy+(dy+.5)/3)*rh/16));c=pix[x,y]
     for k in range(3):total[k]+=c[k]
   out.extend(v/(9*255) for v in total)
 return out
def utf(out,text):
 data=text.encode();out.write(struct.pack('>H',len(data)));out.write(data)
refs=json.loads((root/'assets/shopping-preset.json').read_text())
old_keys=sorted(k for k in refs if not k.startswith('shopping-control-6717-'))
refs={k:v for k,v in refs.items() if not k.startswith('shopping-control-6717-')}
for kind,ts in training.items():
 if kind=='arrow':continue
 for name,size,filt in [('native',(1080,2424),Image.Resampling.NEAREST),('half',(540,1212),Image.Resampling.NEAREST),('smooth',(720,1616),Image.Resampling.BILINEAR)]:
  im=image(ts).resize(size,filt);refs[f'shopping-control-6717-{kind}-{name}']=sum([feature(im,b) for b in anchors[kind]],[])
(root/'assets/shopping-preset.json').write_text(json.dumps(refs,separators=(',',':'))+'\n')
with (root/'tests/video6717-references.bin').open('wb') as out:
 out.write(struct.pack('>i',len(refs)))
 for key,values in sorted(refs.items()):utf(out,key);out.write(struct.pack('>i',len(values)));out.write(struct.pack('>'+str(len(values))+'f',*values))
frames=[]
for t in times:frames.append((str(t),image(t).resize((540,1212),Image.Resampling.NEAREST)))
for t in sorted(native):frames.append((str(t)+'-native',image(t)))
with gzip.open(root/'tests/video6717-replay.bin.gz','wb',compresslevel=6) as out:
 out.write(struct.pack('>i',len(frames)))
 for name,im in frames:utf(out,name);out.write(struct.pack('>ii',*im.size));out.write(im.tobytes())
evidence={'source':'6717.mp4','sha256':hashlib.file_digest(video.open('rb'),'sha256').hexdigest(),'dimensions':[1080,2424],'duration_seconds':185.028044,'training_seconds':training,'anchors':anchors,'unchanged_keys':old_keys,'replay_frames':[{'id':name,'size':im.size,'rgb_sha256':hashlib.sha256(im.tobytes()).hexdigest()} for name,im in frames],'limits':['Recording panel uses the pre-v1.82 cadence wording. Device model and installed APK version are not proven by this video.','Frames are decoded losslessly from the supplied compressed video; training times are excluded from replay.','Later frames show explicit all-stop and manual touches. Ad landing pages cannot be attributed to automated taps from these images alone.','Any reconstructed input outcomes, modified anchors, resizes or alternate exit timelines are synthetic tests. New APK physical execution is untested.']}
(root/'tests/video6717-evidence.json').write_text(json.dumps(evidence,ensure_ascii=False,indent=2)+'\n')
print(json.dumps({'references_added':len(refs)-len(old_keys),'replay_frames':len(frames),'native_holdouts':len(native)}))
