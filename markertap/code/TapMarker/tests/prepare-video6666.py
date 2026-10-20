"""Recreate sparse templates and replay from 6666.mp4 at explicit fps-filter frame IDs."""
import sys, json, gzip, struct, hashlib, subprocess, tempfile
from pathlib import Path
import numpy as np
from PIL import Image

root=Path(__file__).resolve().parent.parent
video=Path(sys.argv[1])
pairs={
 'shop':[[.562,.069,.633,.081],[.392,.774,.603,.797]],
 'home':[[.118,.130,.246,.144],[.431,.707,.575,.738]],
 'empty':[[.711,.303,.854,.321],[.72,.373,.85,.400]],
 'customer-crown':[[.392,.774,.603,.797],[.166,.399,.286,.423]],
 'customer-normal':[[.392,.774,.603,.797],[.473,.506,.585,.535]],
 'customer-gold':[[.392,.774,.603,.797],[.173,.604,.287,.640]],
 'stock':[[.405,.245,.596,.275],[.440,.811,.560,.835]],
 'carrot':[[.405,.245,.596,.275],[.362,.340,.539,.364]],
 'apple':[[.405,.245,.596,.275],[.362,.425,.493,.448]],
 'milk':[[.405,.245,.596,.275],[.361,.509,.491,.533]],
 'fish':[[.405,.245,.596,.275],[.361,.593,.494,.617]],
 'meat':[[.405,.245,.596,.275],[.361,.678,.493,.703]],
 'shift':[[.244,.364,.755,.426],[.326,.637,.742,.664]],
 'summon':[[.337,.244,.660,.264],[.463,.539,.536,.557]],
 'boost':[[.304,.292,.697,.324],[.458,.608,.546,.630]],
 'boost-ready':[[.118,.130,.246,.144],[.813,.707,.943,.726]],
 'boost-active':[[.820,.235,.955,.249],[.824,.256,.947,.275]],
 'arrival':[[.235,.232,.736,.259],[.670,.312,.850,.335]],
 'arrival-claim':[[.235,.232,.736,.259],[.342,.532,.656,.554]],
 'clear':[[.354,.419,.488,.444],[.330,.597,.499,.623]],
 'missions':[[.289,.165,.719,.192],[.027,.166,.058,.194]],
 'get':[[.289,.165,.719,.192],[.725,.398,.863,.422]],
 'play':[[.289,.165,.719,.192],[.712,.398,.860,.422]],
 'reward':[[.175,.340,.825,.371],[.424,.555,.572,.579]],
}
training={'shop':2,'home':153,'empty':2,'customer-crown':2,'customer-normal':2,'customer-gold':2,
 'stock':63,'carrot':63,'apple':63,'milk':63,'fish':63,'meat':63,'shift':4,'summon':120,
 'boost':176,'boost-ready':153,'boost-active':236,'arrival':43,'arrival-v2':89,'arrival-v3':94,'arrival-v4':99,
 'arrival-claim':94,'arrival-claim-v2':99,'clear':139,'clear-v2':206,'clear-v3':237,
 'missions':141,'get':141,'play':137,'reward':152,'stock-v2':103,'meat-v2':103,'carrot-v2':103}

def feature(a,b):
 h,w=a.shape[:2];r=np.asarray(b,dtype=np.float32)
 l,t,rr,bb=[int(v) for v in r*np.array([w,h,w,h],dtype=np.float32)]
 l,t=max(0,min(w-1,l)),max(0,min(h-1,t));rr,bb=max(l+1,min(w,rr)),max(t+1,min(h,bb))
 out=[]
 for y in range(16):
  for x in range(16):
   s=np.zeros(3,dtype=np.float32)
   for dy in range(3):
    for dx in range(3):
     px=min(rr-l-1,int((x+(dx+.5)/3)*(rr-l)/16));py=min(bb-t-1,int((y+(dy+.5)/3)*(bb-t)/16))
     s+=a[t+py,l+px]
   out.extend((s/np.float32(9*255)).tolist())
 return out

def utf(f,s):
 b=s.encode();f.write(struct.pack('>H',len(b)));f.write(b)

with tempfile.TemporaryDirectory() as tmp:
 subprocess.run(['ffmpeg','-v','error','-i',str(video),'-vf','fps=4','-y',str(Path(tmp)/'frame-%04d.png')],check=True)
 images={n:Image.open(Path(tmp)/f'frame-{n:04d}.png').convert('RGB') for n in range(1,241)}
 refs={}
 for key,n in training.items():
  base=key.split('-v')[0];anchors=pairs[base]
  if key in ('stock-v2','meat-v2','carrot-v2'):
   anchors=[[b[0]*.84+.065,b[1]*.84+.08,b[2]*.84+.065,b[3]*.84+.08] for b in anchors]
  refs[key]=sum((feature(np.asarray(images[n]),b) for b in anchors),[])
 refs['customer']=refs['customer-crown']
 # Exact ready grey badge; digits in an otherwise identical badge must fail.
 a=np.asarray(images[24]);mask=(a.min(axis=2)>205)&((a.max(axis=2).astype(int)-a.min(axis=2))<15)
 skip=[781/856,196/1920,810/856,219/1920]
 binary=np.repeat((mask*255).astype(np.uint8)[:,:,None],3,axis=2)
 refs['ad-chevron']=feature(binary,skip)
 for key,n,small in [('ad-chevron-small',24,True),('ad-chevron-dark',49,False),('ad-chevron-dark-small',49,True)]:
  im=images[n].resize((428,960),Image.Resampling.NEAREST) if small else images[n]
  a=np.asarray(im);hh,ww=a.shape[:2];mask=(a.min(axis=2)>205)&((a.max(axis=2).astype(int)-a.min(axis=2))<15)
  left,top,right,bottom=int(.89*ww),int(.09*hh),int(.97*ww),int(.13*hh);seen=set();glyphs=[]
  for y in range(top,bottom):
   for x in range(left,right):
    if not mask[y,x] or (x,y) in seen:continue
    todo=[(x,y)];seen.add((x,y))
    for px,py in todo:
     for dy in [-1,0,1]:
      for dx in [-1,0,1]:
       nx,ny=px+dx,py+dy
       if left<=nx<right and top<=ny<bottom and mask[ny,nx] and (nx,ny) not in seen:seen.add((nx,ny));todo.append((nx,ny))
    xs,ys=zip(*todo);l,t,r,b=min(xs),min(ys),max(xs),max(ys);gw,gh=r-l+1,b-t+1
    if max(4,round(ww*.014))<=gh<=round(ww*.045) and .3<=gw/gh<=.85 and .15<=len(todo)/(gw*gh)<=.70:glyphs.append((l,t,r,b))
  assert len(glyphs)==2,(key,glyphs)
  l=min(g[0] for g in glyphs);t=min(g[1] for g in glyphs);r=max(g[2] for g in glyphs)+1;b=max(g[3] for g in glyphs)+1
  binary=np.repeat((mask*255).astype(np.uint8)[:,:,None],3,axis=2);refs[key]=feature(binary,[l/ww,t/hh,r/ww,b/hh])
 (root/'assets/shopping-preset.json').write_text(json.dumps(refs,separators=(',',':'))+'\n')
 with (root/'tests/video6666-references.bin').open('wb') as f:
  f.write(struct.pack('>i',len(refs)))
  for k,v in refs.items():utf(f,k);f.write(struct.pack('>i',len(v)));f.write(struct.pack('>'+str(len(v))+'f',*v))
 frames=[(str(n),im.resize((428,960),Image.Resampling.NEAREST)) for n,im in images.items()]
 frames += [(str(n)+'-native',images[n]) for n in [2,24,63,94,120,124,137,141,152,176,198,206,208,236,240]]
 with gzip.open(root/'tests/video6666-replay.bin.gz','wb',compresslevel=6) as f:
  f.write(struct.pack('>i',len(frames)))
  for name,im in frames:utf(f,name);f.write(struct.pack('>ii',*im.size));f.write(im.tobytes())
 evidence={'source':'6666.mp4','sha256':hashlib.sha256(video.read_bytes()).hexdigest(),
  'app':'com.kurashiru.shopping.usagi','package_source':'https://play.google.com/store/apps/details?id=com.kurashiru.shopping.usagi',
  'width':856,'height':1920,'duration_seconds':59.946554,'extraction':'ffmpeg fps=4; IDs are one-based filter output, nominal timestamp=(ID-1)/4',
  'training_frame_ids':training,'pairs':pairs,'ad_ready_bounds':skip,'ad_ready_frame':24,'ad_dark_ready_frame':49,'ad_small_variants':'428x960 nearest sampling of the two recorded ready glyphs',
  'replay_frames':[{'id':name,'size':im.size,'rgb_sha256':hashlib.sha256(im.tobytes()).hexdigest()} for name,im in frames],
  'limits':'Edited manual InShot recording with cuts and accelerated playback. Native and holdout pixels are real; relocated controls and display insets are synthetic. Android input and unknown adverts are not device tested.'}
 (root/'tests/video6666-evidence.json').write_text(json.dumps(evidence,indent=2)+'\n')
print(json.dumps({'references':len(refs),'frames':len(frames)}))
