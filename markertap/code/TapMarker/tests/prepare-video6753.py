"""Fit small-preview mission evidence and amount controls from identified source frames.
6753 at 0s and archived 6677 at 0001/0059 are fitting images, not validation images.
"""
from pathlib import Path
import gzip, hashlib, json, struct, subprocess, sys
import numpy as np
from PIL import Image

root=Path(__file__).resolve().parent.parent
video=Path(sys.argv[1]).resolve()
def old_frame(key):
    with gzip.open(root/'tests/video6677-replay.bin.gz','rb') as f:
        for _ in range(struct.unpack('>i',f.read(4))[0]):
            name=f.read(struct.unpack('>H',f.read(2))[0]).decode()
            w,h=struct.unpack('>ii',f.read(8));raw=f.read(w*h*3)
            if name==key:return Image.frombytes('RGB',(w,h),raw)
    raise ValueError(key)
def crop(seconds):
    raw=subprocess.check_output(['ffmpeg','-v','error','-ss',str(seconds),'-i',str(video),'-frames:v','1','-vf','crop=166:374:370:264','-pix_fmt','rgb24','-f','rawvideo','-'])
    assert len(raw)==166*374*3
    return Image.frombytes('RGB',(166,374),raw)
def sample(im,box):
    a=np.asarray(im,dtype=np.int32);h,w=a.shape[:2]
    l,t,r,b=[int(np.float32(v)*np.float32(size)) for v,size in zip(box,[w,h,w,h])]
    bw,bh=max(1,r-l),max(1,b-t)
    xs=[l+min(bw-1,int(np.float32(np.float32(np.float32(cell)+np.float32(sub+.5)/np.float32(3))*np.float32(bw)/np.float32(16)))) for cell in range(16) for sub in range(3)]
    ys=[t+min(bh-1,int(np.float32(np.float32(np.float32(cell)+np.float32(sub+.5)/np.float32(3))*np.float32(bh)/np.float32(16)))) for cell in range(16) for sub in range(3)]
    return [float(v) for y in range(16) for x in range(16) for v in np.float32(a[np.array(ys[y*3:y*3+3])[:,None],np.array(xs[x*3:x*3+3])[None,:]].sum(axis=(0,1))/np.float32(9*255))]
def virtual(b):
    sy=np.float32((.78-.097)/(.775-.0246));top=np.float32(.097)-np.float32(.0246)*sy
    return [b[0],float((np.float32(b[1])-top)/sy),b[2],float((np.float32(b[3])-top)/sy)]
def write_refs(path,refs):
    with path.open('wb') as f:
        f.write(struct.pack('>i',len(refs)))
        for key,values in refs.items():
            text=key.encode();f.write(struct.pack('>H',len(text))+text+struct.pack('>i',len(values))+struct.pack('>'+str(len(values))+'f',*values))

path=root/'assets/virtual-home-preset.json';evidence=json.loads(path.read_text())
mission=crop(0);amount=old_frame('0001');amount_ad=old_frame('0059')
for key,box in [('title',[.28,.118,.73,.145]),('play',[.736,.563,.87,.591])]:
    evidence['usagi-mission-small:'+key+':bounds']=box
    evidence['usagi-mission-small:'+key+':reference']=sample(mission,box)
for key,box in [('title',[.178,.842,.308,.854]),('necessary',[.178,.859,.223,.872])]:
    evidence['usagi-amount:'+key+':bounds']=virtual(box)
    evidence['usagi-amount:'+key+':reference']=sample(amount,box)
evidence['usagi-amount:level:bounds']=virtual([.065,.835,.16,.867])
evidence['usagi-amount:touch:bounds']=virtual([.045,.824,.35,.884])
ad_box=[.17,.856,.285,.876]
evidence['usagi-amount:ad:bounds']=virtual(ad_box)
evidence['usagi-amount:ad:reference']=sample(amount_ad,ad_box)
path.write_text(json.dumps(evidence,separators=(',',':'))+'\n')
write_refs(root/'tests/current-layout-evidence.bin',evidence)
write_refs(root/'tests/usagi-current-references.bin',json.loads((root/'assets/usagi-preset.json').read_text()))
times=[2,8,16,24,32,39]
with gzip.open(root/'tests/video6753-replay.bin.gz','wb',compresslevel=6) as f:
    f.write(struct.pack('>i',len(times)))
    for t in times:
        im=crop(t);name=str(t).encode();f.write(struct.pack('>H',len(name))+name+struct.pack('>ii',*im.size)+im.tobytes())
(root/'tests/video6753-evidence.json').write_text(json.dumps({
    'video':'6753.mp4','sha256':hashlib.sha256(video.read_bytes()).hexdigest(),
    'preview_crop':[370,264,536,638],'mission_training_seconds':0,
    'mission_held_out_seconds':times,'amount_training':['archived 6677 / 0001','archived 6677 / 0059 for the CM upgrade label'],
    'limits':'Visible 166x374 preview pixels. Upscaled frames are synthetic. No raw ImageReader frame, new APK device input or measured amount increase in the supplied recording.'
},ensure_ascii=False,indent=2)+'\n')
print('Wrote mission and amount templates, six held-out preview crops, and evidence provenance.')
