"""Build F-52A native UI evidence and held-out replay pixels from 6680.mp4."""
import sys,json,gzip,struct,hashlib,subprocess
from pathlib import Path
import numpy as np
from PIL import Image

root=Path(__file__).resolve().parent.parent
video=Path(sys.argv[1]);width,height=1080,2280
normal_y=np.float32((.78-.097)/(.775-.0246));normal_top=np.float32(.097-np.float32(.0246)*normal_y)
layout=np.asarray([1,.965,0,.044],dtype=np.float32)
header=np.asarray([.66,.0096,.77,.0396],dtype=np.float32)
label=np.asarray([.178,(np.float32(.842)-normal_top)/normal_y,.308,(np.float32(.854)-normal_top)/normal_y],dtype=np.float32)
def mapped(bounds):
    return bounds*np.asarray([layout[0],layout[1],layout[0],layout[1]],dtype=np.float32)+np.asarray([layout[2],layout[3],layout[2],layout[3]],dtype=np.float32)
def frame(t):
    data=subprocess.check_output(['ffmpeg','-v','error','-ss',str(t),'-i',str(video),'-frames:v','1','-f','rawvideo','-pix_fmt','rgb24','pipe:1'])
    if len(data)!=width*height*3:raise ValueError('unexpected F-52A video size')
    return Image.frombytes('RGB',(width,height),data)
def sample(image,bounds):
    w,h=image.size;array=np.asarray(image);l,t,r,b=[int(v) for v in bounds*np.array([w,h,w,h],dtype=np.float32)];rw,rh=r-l,b-t;result=[]
    for y in range(16):
        for x in range(16):
            color=np.zeros(3,dtype=np.float32)
            for dy in range(3):
                for dx in range(3):
                    px=min(rw-1,int(np.float32((np.float32(x)+np.float32(dx+.5)/np.float32(3))*np.float32(rw)/np.float32(16))))
                    py=min(rh-1,int(np.float32((np.float32(y)+np.float32(dy+.5)/np.float32(3))*np.float32(rh)/np.float32(16))))
                    color+=array[t+py,l+px]
            result.extend((color/np.float32(9*255)).tolist())
    return result
def features(image):
    a=np.asarray(image);white=(a.min(axis=2)>=222)&(a.max(axis=2).astype(int)-a.min(axis=2).astype(int)<=35)
    mask=Image.fromarray(np.repeat((white.astype(np.uint8)*255)[:,:,None],3,axis=2))
    return sample(image,mapped(header))+sample(mask,mapped(label))
def utf(stream,text):
    data=text.encode();stream.write(struct.pack('>H',len(data))+data)
def write_refs(path,refs):
    with path.open('wb') as stream:
        stream.write(struct.pack('>i',len(refs)))
        for key,values in refs.items():
            utf(stream,key);stream.write(struct.pack('>i',len(values)));stream.write(struct.pack('>'+str(len(values))+'f',*values))

training=frame(2);refs={}
for w,h in [(1080,2280),(540,1140),(720,1520)]:
    for name,method in [('nearest',Image.Resampling.NEAREST),('smooth',Image.Resampling.BILINEAR)]:
        if w==1080 and name=='smooth':continue
        refs[f'usagi-portable-f52a-home-{w}x{h}-{name}']=features(training.resize((w,h),method))
asset=root/'assets/virtual-home-preset.json';previous=json.loads(asset.read_text());previous.update(refs);asset.write_text(json.dumps(previous,separators=(',',':'))+'\n')
write_refs(root/'tests/video6680-references.bin',previous)

times=[0,.5,1,3,6.5,8,12,14,17,21,27,35,48,63,80,127,137,152,157,163,168,173]
native={0,17,63,127};metadata=[]
with gzip.open(root/'tests/video6680-replay.bin.gz','wb',compresslevel=6) as stream:
    stream.write(struct.pack('>i',len(times)))
    for t in times:
        image=frame(t)
        if t not in native:image=image.resize((540,1140),Image.Resampling.BILINEAR)
        data=image.tobytes();utf(stream,str(t));stream.write(struct.pack('>ii',*image.size));stream.write(data)
        metadata.append({'seconds':t,'size':list(image.size),'native_pixels':t in native,'rgb_sha256':hashlib.sha256(data).hexdigest()})
digest=hashlib.sha256()
with video.open('rb') as stream:
    while data:=stream.read(1024*1024):digest.update(data)
evidence={'source':'6680.mp4','source_sha256':digest.hexdigest(),'reported_device':'F-52A','source_size':[width,height],'training_seconds':2,'virtual_to_recorded_layout':layout.tolist(),'header_bounds':mapped(header).tolist(),'label_bounds':mapped(label).tolist(),'new_reference_keys':list(refs),'replay_frames':metadata,'limits':'Native F-52A video recognition and host coordinate replay. No new-APK installation or input test; no treasure, close, start or all-daily-complete button screen in this recording.'}
(root/'tests/video6680-evidence.json').write_text(json.dumps(evidence,ensure_ascii=False,indent=2)+'\n')
print(json.dumps({'references':len(refs),'frames':len(times),'native_frames':len(native),'fixture_bytes':(root/'tests/video6680-replay.bin.gz').stat().st_size}))
