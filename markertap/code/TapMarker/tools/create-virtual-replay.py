#!/usr/bin/env python3
"""Rebuild virtual evidence from the prior crop fixture and user-supplied 6645.mp4."""
import argparse, hashlib, io, json, struct, subprocess
from pathlib import Path
from PIL import Image
import numpy as np

ROOT = Path(__file__).resolve().parents[1]
def integer(stream):
    return struct.unpack('>i', stream.read(4))[0]
def image(stream):
    width, height = integer(stream), integer(stream)
    return Image.frombytes('RGB', (width, height), stream.read(width*height*3))
def sample(im, region):
    a = np.asarray(im.convert('RGB')); height, width = a.shape[:2]
    left, top, right, bottom = [int(np.float32(v)*np.float32(n)) for v,n in zip(region,[width,height,width,height])]
    w, h = right-left, bottom-top
    values = []
    for y in range(16):
        for x in range(16):
            points = [a[top+min(h-1,int((y+(dy+.5)/3)*h/16)),left+min(w-1,int((x+(dx+.5)/3)*w/16))] for dy in range(3) for dx in range(3)]
            values.extend(np.mean(points, axis=0)/255)
    return values
def paired(im, first, second):
    return sample(im, first)+sample(im, second)
def write_image(stream, im):
    stream.write(struct.pack('>ii', *im.size)); stream.write(im.convert('RGB').tobytes())

parser=argparse.ArgumentParser();parser.add_argument('video',type=Path);args=parser.parse_args()
with (ROOT/'tests/virtual-home-replay.bin').open('rb') as stream:
    assert integer(stream)==2
    previous=[]
    for i in range(2):
        im=image(stream); n=integer(stream); stream.read(4*n); previous.append(im)
evidence=json.loads((ROOT/'assets/virtual-home-preset.json').read_text())
evidence['farm-home']=paired(previous[1],[.75,.077,.805,.092],[.60,.765,.64,.789])
cases=[('usagi-main',20,[42,543,484,1536]),('usagi-ad-left',45,[42,543,484,1536]),
       ('farm-main',20,[538,559,982,1553]),('farm-main-pressed',25,[538,559,982,1553]),
       ('farm-ad-right',40,[538,559,982,1553]),('farm-ad-left',55,[538,559,982,1553]),
       ('farm-reward',70,[538,559,982,1553]),('farm-reward-holdout',85,[538,559,982,1553])]
cropped={}
for name,seconds,bounds in cases:
    result=subprocess.run(['ffmpeg','-v','error','-ss',str(seconds),'-i',str(args.video),'-frames:v','1','-f','image2pipe','-vcodec','png','-'],capture_output=True,check=True)
    cropped[name]=Image.open(io.BytesIO(result.stdout)).convert('RGB').crop(bounds)
evidence['farm-reward']=paired(cropped['farm-reward'],[.29,.338,.75,.377],[.405,.568,.595,.603])
(ROOT/'assets/virtual-home-preset.json').write_text(json.dumps(evidence,separators=(',',':'))+'\n')
with (ROOT/'tests/virtual-home-replay.bin').open('wb') as stream:
    stream.write(struct.pack('>i',2))
    for i,im in enumerate(previous):
        write_image(stream,im); ref=evidence['usagi-home' if i==0 else 'farm-home'];stream.write(struct.pack('>i',len(ref)));stream.write(struct.pack('>'+str(len(ref))+'f',*ref))
with (ROOT/'tests/virtual-recovery-replay.bin').open('wb') as stream:
    stream.write(struct.pack('>i',len(cases)))
    for name,_,_ in cases:
        encoded=name.encode('ascii');stream.write(struct.pack('>H',len(encoded)));stream.write(encoded);write_image(stream,cropped[name])
    encoded='farm-reward'.encode('ascii');stream.write(struct.pack('>H',len(encoded)));stream.write(encoded);ref=evidence['farm-reward'];stream.write(struct.pack('>i',len(ref)));stream.write(struct.pack('>'+str(len(ref))+'f',*ref))
metadata={'source':args.video.name,'sha256':hashlib.sha256(args.video.read_bytes()).hexdigest(),'training':'farm-reward at 70s; prior main evidence remains unchanged except the farm green-button anchor','held_out':'other video frames and farm reward at 85s','cases':[{'name':name,'seconds':seconds,'crop':bounds} for name,seconds,bounds in cases],'scope':'Visible virtual previews, not raw 720px ImageReader buffers or actual Android inputs.'}
(ROOT/'tests/virtual-recovery-evidence.json').write_text(json.dumps(metadata,ensure_ascii=False,indent=2)+'\n')
print('Updated sparse virtual evidence and',len(cases),'recorded preview cases')
