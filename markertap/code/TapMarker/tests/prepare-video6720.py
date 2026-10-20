"""Build game-specific evidence and timestamp-disjoint replay frames from 6720."""
import sys, json, struct, gzip, hashlib, subprocess, tempfile
from pathlib import Path
import numpy as np
from PIL import Image

root = Path(__file__).resolve().parent.parent
video = Path(sys.argv[1]).resolve()
training = {'fever': 25, 'clear': 35.8, 'missions': 77, 'boost-rare': 19,
            'boost-pocket': 31, 'bonus': 65, 'get': 77, 'play': 82, 'reward': 80}
pairs = {
    'fever': [[.104, .286, .322, .330], [.442, .560, .555, .578]],
    'clear': [[.350, .585, .650, .630], [.450, .530, .560, .575]],
    'missions': [[.289, .166, .715, .181], [.019, .168, .053, .196]],
    'boost-rare': [[.247, .580, .758, .628], [.267, .749, .738, .775]],
    'boost-pocket': [[.272, .577, .745, .609], [.256, .740, .739, .769]],
    'bonus': [[.265, .574, .735, .589], [.595, .775, .675, .797]],
    'get': [[.289, .166, .715, .193], [.759, .400, .864, .418]],
    'play': [[.289, .166, .715, .181], [.729, .400, .869, .425]],
    'reward': [[.179, .350, .824, .368], [.421, .556, .576, .577]],
}
holdouts = [0, 5, 11, 12, 15, 17, 18.5, 19.5, 24, 24.8, 26, 30.5,
            31.5, 35.5, 36.2, 36.5, 38, 39, 41, 44, 50, 55, 61, 64,
            66, 69, 73, 75, 77.3, 78, 79, 80.5, 81, 82.3, 82.6,
            85, 89, 100, 111, 115]
native = [19.5, 24.8, 36.5, 69, 77.3, 80.5, 82.3]
assert not set(holdouts) & set(training.values())

def feature(data, bounds):
    h, w = data.shape[:2]
    b = np.asarray(bounds, dtype=np.float32) * np.array([w,h,w,h],dtype=np.float32)
    left,top,right,bottom = map(int,b)
    left,top = max(0,min(w-1,left)),max(0,min(h-1,top))
    right,bottom = max(left+1,min(w,right)),max(top+1,min(h,bottom))
    rw,rh = right-left,bottom-top
    result = []
    for y in range(16):
        for x in range(16):
            value = np.zeros(3, dtype=np.float32)
            for dy in range(3):
                for dx in range(3):
                    px=min(rw-1,int((x+(dx+.5)/3)*rw/16))
                    py=min(rh-1,int((y+(dy+.5)/3)*rh/16))
                    value += data[top+py,left+px]
            result.extend((value/np.float32(9*255)).tolist())
    return result

def utf(out, name):
    value=name.encode('utf-8');out.write(struct.pack('>H',len(value)));out.write(value)

with tempfile.TemporaryDirectory() as temporary:
    frames={}
    for at in sorted(set(holdouts+list(training.values()))):
        path=Path(temporary)/f'{at}.png'
        subprocess.run(['ffmpeg','-v','error','-ss',str(at),'-i',str(video),
                        '-frames:v','1','-y',str(path)],check=True)
        frames[at]=Image.open(path).convert('RGB')
        assert frames[at].size == (1080,2424)
    refs=json.loads((root/'assets/coin-preset.json').read_text())
    refs={k:v for k,v in refs.items() if '-6720-' not in k}
    for key,at in training.items():
        for suffix,size,filter in [('native',(1080,2424),Image.Resampling.NEAREST),
                                   ('half',(540,1212),Image.Resampling.NEAREST),
                                   ('smooth',(720,1616),Image.Resampling.BILINEAR)]:
            pixels=np.asarray(frames[at].resize(size,filter))
            refs[f'coin-control-{key}-6720-{suffix}']=sum([feature(pixels,b) for b in pairs[key]],[])
            if key in ('boost-rare','boost-pocket'):
                identity=[pairs[key][0],[.34,.40,.68,.54]]
                refs[f'coin-identity-{key}-6720-{suffix}']=sum([feature(pixels,b) for b in identity],[])
    (root/'assets/coin-preset.json').write_text(json.dumps(refs,separators=(',',':'))+'\n')
    with (root/'tests/video6720-references.bin').open('wb') as out:
        out.write(struct.pack('>i',len(refs)))
        for key,values in sorted(refs.items()):
            utf(out,key);out.write(struct.pack('>i',len(values)))
            out.write(struct.pack('>'+str(len(values))+'f',*values))
    replays=[(str(t),frames[t].resize((540,1212),Image.Resampling.NEAREST)) for t in holdouts]
    replays += [(str(t)+'-native',frames[t]) for t in native]
    with gzip.open(root/'tests/video6720-replay.bin.gz','wb',compresslevel=6) as out:
        out.write(struct.pack('>i',len(replays)))
        for key,im in replays:
            utf(out,key);out.write(struct.pack('>ii',*im.size));out.write(im.tobytes())
    evidence={'source':'6720.mp4','sha256':hashlib.sha256(video.read_bytes()).hexdigest(),
              'training_seconds':training,'pairs':pairs,'holdout_seconds':holdouts,
              'native_holdouts':native,'reference_entries':len(refs),
              'limits':'Frames test recognition, state transitions and input preflight; they do not execute physical device touches. Blackened/pressed frames must not authorize game input. Device-side sampling failure stack was not provided and its exact cause is not established.'}
    (root/'tests/video6720-evidence.json').write_text(json.dumps(evidence,indent=2)+'\n')
print(json.dumps({'references':len(refs),'holdout_frames':len(replays)}))
