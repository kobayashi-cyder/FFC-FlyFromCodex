"""Extract control-object templates from the included replay data, excluding the orange tap overlay.
The reference timestamps are reused only for fitting. Relocation and later timestamps are tests.
"""
from pathlib import Path
import gzip, json, struct
import numpy as np
from PIL import Image

root=Path(__file__).resolve().parent.parent
def frame(name,key):
    path=root/'tests'/name
    with (gzip.open(path,'rb') if name.endswith('.gz') else path.open('rb')) as f:
        for _ in range(struct.unpack('>i',f.read(4))[0]):
            k=f.read(struct.unpack('>H',f.read(2))[0]).decode()
            w,h=struct.unpack('>ii',f.read(8)); raw=f.read(w*h*3)
            if k==key:return Image.frombytes('RGB',(w,h),raw)
    raise ValueError((name,key))
def sample(im,box):
    a=np.asarray(im,dtype=np.int32);h,w=a.shape[:2]
    l,t,r,b=[int(np.float32(v)*size) for v,size in zip(box,[w,h,w,h])];r=max(l+1,r);b=max(t+1,b);bw,bh=r-l,b-t
    xs=[l+min(bw-1,int((cell+(sub+.5)/3)*bw/16)) for cell in range(16) for sub in range(3)]
    ys=[t+min(bh-1,int((cell+(sub+.5)/3)*bh/16)) for cell in range(16) for sub in range(3)]
    out=[]
    for y in range(16):
        for x in range(16):out.extend(np.float32(a[np.array(ys[y*3:y*3+3])[:,None],np.array(xs[x*3:x*3+3])[None,:]].sum(axis=(0,1))/(9*255)).tolist())
    return out
specs=[
 ('com.kurashiru.fue.usagi','virtual-recovery-replay.bin','usagi-main',[.28,.64,.73,.86],[.5,.775]),
 ('com.kurashiru.nouen.usagi','virtual-recovery-replay.bin','farm-main',[.392,.802,.593,.825],[.4925,.8135]),
 ('com.kurashiru.coin.drop','video6720-replay.bin.gz','17',[.27,.70,.73,.89],[.5,.807]),
 ('com.kurashiru.shopping.usagi','video6717-replay.bin.gz','40',[.29,.60,.71,.79],[.5,.725]),
 ('com.kurashiru.box.merge','video6724-replay.bin.gz','17.0',[.01,.19,.99,.77],[.5,.68]),
 ('com.kurashiru.poipoi.usagi','video6725-replay.bin.gz','26.0',[.10,.39,.89,.68],[.5,.60]),
]
refs={};provenance=[]
for app,file,key,box,point in specs:
    im=frame(file,key);mask=[];control_sample=sample(im,box)
    for y in range(16):
        for x in range(16):
            u=box[0]+(x+.5)/16*(box[2]-box[0]);v=box[1]+(y+.5)/16*(box[3]-box[1])
            keep=True
            # The training image's movable orange marker is never visual evidence.
            if app.endswith(('coin.drop','shopping.usagi')):
                keep=((u-point[0])/.065)**2+((v-point[1])/.031)**2>1.2
            if app.endswith('coin.drop'):
                # Keep the actual yellow housing; blue/pink stage backgrounds are not button identity.
                values=control_sample[(y*16+x)*3:(y*16+x)*3+3]
                keep=keep and v>=.735 and not (values[2]>.60 and values[2]>values[0]+.05)
            if app.endswith('poipoi.usagi'):
                # Feeding animates the rabbit above the stable carrot pile and plate.
                keep=y>=8
            if app.endswith('box.merge'):keep=(x<4 or x>=12 or y>=12)
            mask.append(float(keep))
    prefix=app+':main'
    if app=='com.kurashiru.nouen.usagi':
        a=np.asarray(im,dtype=np.int32);keep=(a.min(axis=2)>=222)&(a.max(axis=2)-a.min(axis=2)<=35);im=Image.fromarray(np.repeat((keep*255).astype(np.uint8)[:,:,None],3,axis=2));refs[prefix+':ink']=[1.0]
    refs[prefix+':bounds']=box;refs[prefix+':reference']=sample(im,box);refs[prefix+':mask']=mask
    refs[prefix+':point']=[(point[0]-box[0])/(box[2]-box[0]),(point[1]-box[1])/(box[3]-box[1])]
    if app=='com.kurashiru.coin.drop':
        # New evidence is a fitted variant; the screenshot is not a held-out accuracy measurement.
        refs[prefix+':reference:1']=sample(frame('image6738-replay.bin.gz','6738'),box)
    if app=='com.kurashiru.nouen.usagi':
        for index,timestamp in enumerate(['80','63'],1):
            native=np.asarray(frame('video6680-replay.bin.gz',timestamp),dtype=np.int32);white=(native.min(axis=2)>=222)&(native.max(axis=2)-native.min(axis=2)<=35);variant=Image.fromarray(np.repeat((white*255).astype(np.uint8)[:,:,None],3,axis=2));refs[prefix+':reference:'+str(index)]=sample(variant,box)
    provenance.append({'app':app,'fixture':file,'reference_frame':key,'bounds':box,'scope':'play object only; movable tap marker excluded','additional_native_style_frames':(['6680 / 80','6680 / 63'] if app=='com.kurashiru.nouen.usagi' else [])})
fever_key='com.kurashiru.nouen.usagi:fever-banner'
refs[fever_key+':bounds']=[.154,.609,.55,.635]
refs[fever_key+':reference']=sample(frame('farm-dialog-replay.bin.gz','20'),refs[fever_key+':bounds'])
provenance.append({'app':'com.kurashiru.nouen.usagi','role':'fever-banner','fixture':'farm-dialog-replay.bin.gz','reference_frame':'20','held_out_recording':'6680 / 168','scope':'caption excludes changing countdown; harvest uses white letters rather than button colours'})
for app,file,key,box,point in [
 ('com.kurashiru.fue.usagi','virtual-recovery-replay.bin','usagi-main',[.33,0,.57,.055],[.44,.028]),
 ('com.kurashiru.coin.drop','video6720-replay.bin.gz','17',[.35,.078,.535,.120],[.43,.098]),
 ('com.kurashiru.shopping.usagi','video6717-replay.bin.gz','40',[.69,.06,.88,.088],[.778,.075]),
]:
    prefix=app+':mission';refs[prefix+':bounds']=box;refs[prefix+':reference']=sample(frame(file,key),box);refs[prefix+':mask']=[1.0]*256
    refs[prefix+':point']=[(point[0]-box[0])/(box[2]-box[0]),(point[1]-box[1])/(box[3]-box[1])]
    provenance.append({'app':app,'role':'mission','fixture':file,'reference_frame':key,'bounds':box})
(root/'assets/visual-targets.json').write_text(json.dumps(refs,separators=(',',':'))+'\n')
with (root/'tests/visual-targets.bin').open('wb') as f:
    f.write(struct.pack('>i',len(refs)))
    for key,values in refs.items():
        encoded=key.encode();f.write(struct.pack('>H',len(encoded))+encoded+struct.pack('>i',len(values))+struct.pack('>'+str(len(values))+'f',*values))
(root/'tests/visual-targets-evidence.json').write_text(json.dumps(provenance,indent=2)+'\n')
layout_refs={k:v for k,v in json.loads((root/'assets/virtual-home-preset.json').read_text()).items() if isinstance(v,list)}
with (root/'tests/current-layout-evidence.bin').open('wb') as f:
    f.write(struct.pack('>i',len(layout_refs)))
    for key,values in layout_refs.items():
        encoded=key.encode();f.write(struct.pack('>H',len(encoded))+encoded+struct.pack('>i',len(values))+struct.pack('>'+str(len(values))+'f',*values))
print('Wrote play, mission and fever templates, local touch positions and shipped layout-reference fixtures.')
