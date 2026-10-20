"""6767: preview crops, not raw 320px ImageReader frames or successful device input.
Fit cancel at 17s and playable at 28s; all other seconds are held out.
"""
from pathlib import Path
import gzip, hashlib, json, struct, subprocess, sys
import numpy as np
from PIL import Image

root=Path(__file__).resolve().parent.parent
video=Path(sys.argv[1]).resolve()
def crop(seconds,game):
    box='294:660:60:620' if game=='usagi' else '294:660:368:628'
    raw=subprocess.check_output(['ffmpeg','-v','error','-ss',str(seconds),'-i',str(video),'-frames:v','1','-vf','crop='+box,'-pix_fmt','rgb24','-f','rawvideo','-'])
    assert len(raw)==294*660*3
    return Image.frombytes('RGB',(294,660),raw)
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

cancel=crop(17,'usagi');playable=crop(28,'farm')
path=root/'assets/virtual-home-preset.json';evidence=json.loads(path.read_text())
for key,pixel in [('use',[106,320,192,352]),('cancel',[105,375,192,398])]:
    box=[pixel[0]/294,pixel[1]/660,pixel[2]/294,pixel[3]/660]
    evidence['usagi-cancel:'+key+':bounds']=box
    evidence['usagi-cancel:'+key+':reference']=sample(cancel,box)
path.write_text(json.dumps(evidence,separators=(',',':'))+'\n')
write_refs(root/'tests/current-layout-evidence.bin',evidence)
path=root/'assets/visual-targets.json';targets=json.loads(path.read_text())
for old in [key for key in targets if key.startswith('playable-last-asylum:')]:del targets[old]
for key,pixel in [('logo',[9,17,116,67]),('hint',[90,632,210,657])]:
    box=[pixel[0]/294,pixel[1]/660,pixel[2]/294,pixel[3]/660]
    targets['playable-last-asylum:'+key+':bounds']=box
    image=playable
    if key=='hint':
        a=np.asarray(playable);mask=(a.min(axis=2)>=215)&(a.max(axis=2).astype(int)-a.min(axis=2).astype(int)<=45);image=Image.fromarray(np.repeat(np.where(mask,255,0).astype('uint8')[:,:,None],3,axis=2))
    targets['playable-last-asylum:'+key+':reference']=sample(image,box)
path.write_text(json.dumps(targets,separators=(',',':'))+'\n')
write_refs(root/'tests/visual-targets.bin',targets)
cases=[('usagi',s) for s in [0,3,6,9,12,14,16,17.5,18,19,20,21,24,30,42,51]]+[('farm',s) for s in [0,3,6,9,12,15,18,21,24,27,30,33,36,39,42,45,48,51]]
with gzip.open(root/'tests/video6767-replay.bin.gz','wb',compresslevel=6) as f:
    f.write(struct.pack('>i',len(cases)))
    for game,t in cases:
        im=crop(t,game);name=(game+':'+str(t)).encode();f.write(struct.pack('>H',len(name))+name+struct.pack('>ii',*im.size)+im.tobytes())
(root/'tests/video6767-evidence.json').write_text(json.dumps({'video':'6767.mp4','sha256':hashlib.sha256(video.read_bytes()).hexdigest(),'reported_internal_width':320,'visible_preview_size':[294,660],'crop_usagi':[60,620,354,1280],'crop_farm':[368,628,662,1288],'training_seconds':{'cancel':17,'playable':28},'held_out_cases':cases,'limits':'Preview pixels and explicitly synthetic resizing. No raw virtual frame, no device injection and no proof of successful ad dismissal or game reward.'},ensure_ascii=False,indent=2)+'\n')
print('Wrote two fitting templates and',len(cases),'held-out preview crops.')
