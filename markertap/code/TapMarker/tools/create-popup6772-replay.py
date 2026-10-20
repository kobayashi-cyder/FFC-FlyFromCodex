#!/usr/bin/env python3
"""Retain observed preview pixels; no claim of raw ImageReader or Android input replay."""
import argparse, gzip, hashlib, io, json, struct, subprocess
from pathlib import Path
import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
TIMES = [0,3.12,6.24,9.36,12.48,15.60,18.72,21.84,24.96,28.07,31.19,34.31]
CROPS = {'usagi':[126,264,406,893], 'farm':[666,264,946,893],
         'coin':[240,1430,520,2059], 'poipoi':[628,1450,908,2079]}
FOOTER = [[.03,.978,.43,1],[.55,.980,.84,1]]
PROMO = [[.125,.224,.83,.267],[.125,.292,.86,.332]]

def sample(im, region):
    a=np.asarray(im.convert('RGB')); h,w=a.shape[:2]
    l,t,r,b=[int(np.float32(v)*np.float32(n)) for v,n in zip(region,[w,h,w,h])]
    values=[]
    for y in range(16):
        for x in range(16):
            points=[a[t+min(b-t-1,int(np.float32(y+np.float32((dy+.5)/3))*np.float32(b-t)/16)),
                      l+min(r-l-1,int(np.float32(x+np.float32((dx+.5)/3))*np.float32(r-l)/16))]
                    for dy in range(3) for dx in range(3)]
            values.extend((np.sum(points,axis=0,dtype=np.float32)/np.float32(2295)).tolist())
    return values

def utf(out, text):
    data=text.encode('utf-8');out.write(struct.pack('>H',len(data)));out.write(data)

def references(out, refs):
    out.write(struct.pack('>i',len(refs)))
    for key,values in refs.items():
        utf(out,key);out.write(struct.pack('>i',len(values)));out.write(struct.pack('>'+str(len(values))+'f',*values))

def main():
    parser=argparse.ArgumentParser();parser.add_argument('video',type=Path);args=parser.parse_args()
    frames={}
    for seconds in TIMES:
        raw=subprocess.check_output(['ffmpeg','-v','error','-ss',str(seconds),'-i',str(args.video),'-frames:v','1','-f','image2pipe','-vcodec','png','-'])
        full=Image.open(io.BytesIO(raw)).convert('RGB')
        for app,box in CROPS.items():frames[f'{app}:{seconds:.2f}']=full.crop(box)
    targets=json.loads((ROOT/'assets/visual-targets.json').read_text())
    targets['popup6772-footer']=sum([sample(frames['usagi:0.00'],b) for b in FOOTER],[])
    targets['popup6772-farm-promo']=sum([sample(frames['farm:28.07'],b) for b in PROMO],[])
    (ROOT/'assets/visual-targets.json').write_text(json.dumps(targets,separators=(',',':'))+'\n')
    with (ROOT/'tests/visual-targets.bin').open('wb') as out:references(out,targets)
    with gzip.open(ROOT/'tests/video6772-replay.bin.gz','wb') as out:
        out.write(struct.pack('>i',len(frames)))
        for name,im in frames.items():
            utf(out,name);out.write(struct.pack('>ii',*im.size));out.write(im.tobytes())
    meta={'source':args.video.name,'sha256':hashlib.sha256(args.video.read_bytes()).hexdigest(),
          'training':['usagi:0.00 footer appearance','farm:28.07 announcement headings'],
          'existing_evidence':'Poipoi continuation uses the original 6725 heading and ordinary continuation text; no new bonus reference is trained.',
          'held_out':'Other timestamps; same incident and similar static content, not independent unseen popup designs.',
          'seconds':TIMES,'preview_crops':CROPS,
          'scope':'48 visible preview crops. Repeated observation times, resizes, moved windows, masks and input completions are simulated. No raw 320x718 ImageReader frame, actual tap delivery, app response or reward balance is asserted.'}
    (ROOT/'tests/video6772-evidence.json').write_text(json.dumps(meta,ensure_ascii=False,indent=2)+'\n')
    print(f'Created {len(frames)} recorded preview crops and two sparse exit references')

if __name__=='__main__':main()
