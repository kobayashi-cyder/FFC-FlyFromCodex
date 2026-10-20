#!/usr/bin/env python3
"""Create an independent native RGB replay; no templates are fitted to this recording."""
import argparse, gzip, hashlib, json, struct, subprocess, tempfile
from pathlib import Path
from PIL import Image

parser = argparse.ArgumentParser()
parser.add_argument('video', type=Path)
args = parser.parse_args()
root = Path(__file__).resolve().parent
times = [7,10,14,16,18,22,24,26,28,30,50,60,76,87,90,154,158,160,174,176,180,183,187,190,192,196]
interruptions = {28:'chest',30:'reward',60:'ad',90:'confirmation',183:'ad during fever',192:'ad'}
frames = []
with tempfile.TemporaryDirectory() as temporary, gzip.open(root/'video6732-replay.bin.gz','wb') as output:
    output.write(struct.pack('>i',len(times)))
    for second in times:
        path = Path(temporary)/'frame.png'
        subprocess.run(['ffmpeg','-v','error','-ss',str(second),'-i',str(args.video),'-frames:v','1','-y',str(path)],check=True)
        image = Image.open(path).convert('RGB'); raw=image.tobytes(); name=str(second).encode()
        output.write(struct.pack('>H',len(name))+name+struct.pack('>ii',image.width,image.height)+raw)
        frames.append({'seconds':second,'scene':interruptions.get(second,'harvest play'),'width':image.width,'height':image.height,'rgb_sha256':hashlib.sha256(raw).hexdigest()})
(root/'video6732-evidence.json').write_text(json.dumps({'source':args.video.name,'video_sha256':hashlib.sha256(args.video.read_bytes()).hexdigest(),'templates_added':False,'frames':frames,'limits':['Replay pixels and host workload are checked. Device frame rate, gesture acceptance and game harvest rate are not measured.']},ensure_ascii=False,indent=2)+'\n')
print('Wrote native replay and provenance for',len(times),'frames')
