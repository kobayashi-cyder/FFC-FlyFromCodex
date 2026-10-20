#!/usr/bin/env python3
"""Retain recorded preview crops and two amount-independent Coin offline labels."""
import argparse, gzip, hashlib, importlib.util, io, json, struct, subprocess
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('popup6772', ROOT/'tools/create-popup6772-replay.py')
shared = importlib.util.module_from_spec(spec)
spec.loader.exec_module(shared)
TIMES = [0, 2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24]
CROPS = {'usagi':[132,264,400,866], 'farm':[672,264,940,866],
         'coin':[132,990,400,1592], 'poipoi':[672,990,940,1592]}
OFFLINE = [[76/268,156/602,195/268,201/602], [117/268,455/602,151/268,478/602]]

def main():
    parser=argparse.ArgumentParser();parser.add_argument('video',type=Path);args=parser.parse_args()
    frames={}
    for seconds in TIMES:
        raw=subprocess.check_output(['ffmpeg','-v','error','-ss',str(seconds),'-i',str(args.video),'-frames:v','1','-f','image2pipe','-vcodec','png','-'])
        full=Image.open(io.BytesIO(raw)).convert('RGB')
        for app,box in CROPS.items():frames[f'{app}:{seconds:02d}']=full.crop(box)
    targets=json.loads((ROOT/'assets/visual-targets.json').read_text())
    targets['popup6774-coin-offline']=sum([shared.sample(frames['coin:06'],b) for b in OFFLINE],[])
    (ROOT/'assets/visual-targets.json').write_text(json.dumps(targets,separators=(',',':'))+'\n')
    with (ROOT/'tests/visual-targets.bin').open('wb') as out:shared.references(out,targets)
    with gzip.open(ROOT/'tests/video6774-replay.bin.gz','wb') as out:
        out.write(struct.pack('>i',len(frames)))
        for name,im in frames.items():
            shared.utf(out,name);out.write(struct.pack('>ii',*im.size));out.write(im.tobytes())
    meta={'source':args.video.name,'sha256':hashlib.sha256(args.video.read_bytes()).hexdigest(),
          'training':['coin:06 offline title and ordinary acquisition label; amount area excluded'],
          'seconds':TIMES,'preview_crops':CROPS,'anchors':OFFLINE,
          'held_out':'Other times of the same incident, not independent app sessions. coin:00 is covered by an advertisement.',
          'scope':'Visible preview crops only. Resizing, relocation, changed amount, masking and completion are synthetic. Android input delivery, reward credit and multiwindow endurance are unverified.'}
    (ROOT/'tests/video6774-evidence.json').write_text(json.dumps(meta,ensure_ascii=False,indent=2)+'\n')
    print(f'Created {len(frames)} recorded preview crops and two Coin offline text anchors')

if __name__=='__main__':main()
