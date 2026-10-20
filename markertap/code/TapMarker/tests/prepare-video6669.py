"""Rebuild the compact farm recognition evidence from the supplied 6669.mp4."""
import sys,json,gzip,struct,hashlib,subprocess
from pathlib import Path
import numpy as np
root=Path(__file__).resolve().parent.parent
video=Path(sys.argv[1])
header=[.75,.077,.805,.092]
label=[.392,.802,.593,.825]
times=[0,1,2,3,4,5,6,8,10,12,14,15,16,17,18,19,20,22,24,30,40,48,54,55,58,61]
meta=json.loads(subprocess.check_output(['ffprobe','-v','error','-select_streams','v:0','-show_entries','stream=width,height','-of','json',str(video)]))
width=meta['streams'][0]['width'];height=meta['streams'][0]['height']
def feature(image,bounds):
    r=np.asarray(bounds,dtype=np.float32)
    left,top,right,bottom=[int(v) for v in r*np.array([width,height,width,height],dtype=np.float32)]
    left,top=max(0,min(width-1,left)),max(0,min(height-1,top))
    right,bottom=max(left+1,min(width,right)),max(top+1,min(height,bottom))
    w,h=right-left,bottom-top
    values=[]
    for y in range(16):
        for x in range(16):
            pixel=np.zeros(3,dtype=np.float32)
            for dy in range(3):
                for dx in range(3):
                    px=min(w-1,int((x+(dx+.5)/3)*w/16))
                    py=min(h-1,int((y+(dy+.5)/3)*h/16))
                    pixel+=image[top+py,left+px]
            values.extend((pixel/np.float32(9*255)).tolist())
    return values
def utf(out,value):
    encoded=value.encode('utf-8');out.write(struct.pack('>H',len(encoded)));out.write(encoded)
references={};frames=[]
with gzip.open(root/'tests/video6669-replay.bin.gz','wb',compresslevel=9) as out:
    out.write(struct.pack('>i',len(times)))
    for t in times:
        rgb=subprocess.check_output(['ffmpeg','-v','error','-ss',str(t),'-i',str(video),'-frames:v','1','-f','rawvideo','-pix_fmt','rgb24','pipe:1'])
        if len(rgb)!=width*height*3:raise ValueError('short recorded frame')
        image=np.frombuffer(rgb,dtype=np.uint8).reshape(height,width,3)
        if t in [12,22]:
            white=((image.min(axis=2)>=222)&(image.max(axis=2).astype(int)-image.min(axis=2).astype(int)<=35)).astype(np.uint8)*255
            mask=np.repeat(white[:,:,None],3,axis=2)
            key='farm-stable-home' if t==22 else 'farm-stable-pressed'
            reference=feature(image,header)+feature(mask,label)
            references[key]=reference
            filename='video6669-stable-reference.bin' if t==22 else 'video6669-pressed-reference.bin'
            with (root/'tests'/filename).open('wb') as refout:
                refout.write(struct.pack('>i',len(reference)))
                refout.write(struct.pack('>'+str(len(reference))+'f',*reference))
        utf(out,f'{t:.2f}');out.write(struct.pack('>ii',width,height));out.write(rgb)
        frames.append({'seconds':t,'rgb_sha256':hashlib.sha256(rgb).hexdigest()})
asset=root/'assets/virtual-home-preset.json'
data=json.loads(asset.read_text());data.update(references);asset.write_text(json.dumps(data,separators=(',',':'))+'\n')
digest=hashlib.sha256()
with video.open('rb') as stream:
    while chunk:=stream.read(1024*1024):digest.update(chunk)
evidence={'source':'6669.mp4','video_sha256':digest.hexdigest(),'width':width,'height':height,
 'training_seconds':{'farm-stable-home':22,'farm-stable-pressed':12},
 'header_bounds':header,'harvest_label_bounds':label,'white_min_channel':222,'white_max_channel_spread':35,
 'replay_frames':frames,'limits':'Recorded pixel recognition only. Input dispatch and long-running parallel operation on the phone are not tested.'}
(root/'tests/video6669-evidence.json').write_text(json.dumps(evidence,ensure_ascii=False,indent=2)+'\n')
print(json.dumps({'frames':len(frames),'fixture_bytes':(root/'tests/video6669-replay.bin.gz').stat().st_size}))
