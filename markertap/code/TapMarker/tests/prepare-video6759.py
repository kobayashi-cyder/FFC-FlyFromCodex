"""Get/back exemplars from 6759 at 0s; remaining times are replay holdouts.
The recording shows visible preview pixels, not ImageReader frames or successful inputs.
"""
from pathlib import Path
import gzip, hashlib, json, struct, subprocess, sys
import numpy as np
from PIL import Image

root=Path(__file__).resolve().parent.parent
video=Path(sys.argv[1]).resolve()
def crop(seconds):
    raw=subprocess.check_output(['ffmpeg','-v','error','-ss',str(seconds),'-i',str(video),'-frames:v','1','-vf','crop=316:714:242:264','-pix_fmt','rgb24','-f','rawvideo','-'])
    assert len(raw)==316*714*3
    return Image.frombytes('RGB',(316,714),raw)
def sample(im,box):
    a=np.asarray(im,dtype=np.int32);h,w=a.shape[:2]
    l,t,r,b=[int(np.float32(v)*np.float32(size)) for v,size in zip(box,[w,h,w,h])]
    bw,bh=max(1,r-l),max(1,b-t)
    xs=[l+min(bw-1,int(np.float32(np.float32(np.float32(cell)+np.float32(sub+.5)/np.float32(3))*np.float32(bw)/np.float32(16)))) for cell in range(16) for sub in range(3)]
    ys=[t+min(bh-1,int(np.float32(np.float32(np.float32(cell)+np.float32(sub+.5)/np.float32(3))*np.float32(bh)/np.float32(16)))) for cell in range(16) for sub in range(3)]
    return [float(v) for y in range(16) for x in range(16) for v in np.float32(a[np.array(ys[y*3:y*3+3])[:,None],np.array(xs[x*3:x*3+3])[None,:]].sum(axis=(0,1))/np.float32(9*255))]
def write_refs(path,refs):
    with path.open('wb') as f:
        f.write(struct.pack('>i',len(refs)))
        for key,values in refs.items():
            text=key.encode();f.write(struct.pack('>H',len(text))+text+struct.pack('>i',len(values))+struct.pack('>'+str(len(values))+'f',*values))

path=root/'assets/virtual-home-preset.json';evidence=json.loads(path.read_text())
training=crop(0)
for key,box in [('get',[.731,.567,.883,.600]),('back',[.016,.126,.060,.157])]:
    evidence['usagi-mission-small:'+key+':bounds']=box
    evidence['usagi-mission-small:'+key+':reference']=sample(training,box)
path.write_text(json.dumps(evidence,separators=(',',':'))+'\n')
write_refs(root/'tests/current-layout-evidence.bin',evidence)
times=list(range(0,19,2))
with gzip.open(root/'tests/video6759-replay.bin.gz','wb',compresslevel=6) as f:
    f.write(struct.pack('>i',len(times)))
    for t in times:
        im=crop(t);name=str(t).encode();f.write(struct.pack('>H',len(name))+name+struct.pack('>ii',*im.size)+im.tobytes())
(root/'tests/video6759-evidence.json').write_text(json.dumps({
    'video':'6759.mp4','sha256':hashlib.sha256(video.read_bytes()).hexdigest(),
    'preview_crop':[242,264,558,978],'training_seconds':[0],
    'held_out_seconds':times[1:],
    'observed':'Claimable first stage mission (1/1, Get) remains visible while status says verify return screen.',
    'limits':'The nine holdouts are the same static scene at different times. Resizing, masking, row movement, callbacks and subsequent reward/home transitions are synthetic. No Android input or completed collection/upgrade is demonstrated.'
},ensure_ascii=False,indent=2)+'\n')
print('Wrote Get/back exemplars and nine held-out recorded preview crops.')
