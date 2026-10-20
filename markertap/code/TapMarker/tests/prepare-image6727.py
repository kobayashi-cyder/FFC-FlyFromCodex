"""Add screenshot 6727's decline control after running prepare-video6725.py.

Usage: python3 tests/prepare-image6727.py /absolute/path/01-6727.png
The screenshot supplies both the new reference and the original-pixel fixture.
It is a reproduction test, not an independent held-out real screenshot.
"""
from pathlib import Path
from PIL import Image
import gzip, hashlib, json, struct, sys
import numpy as np

root = Path(__file__).resolve().parent.parent
source = Path(sys.argv[1]).resolve()
im = Image.open(source).convert('RGB')
assert im.size == (684, 1536), im.size
anchors = [[89/360,342/808,272/360,421/808], [128/360,522/808,234/360,548/808]]

def sample(box):
    a = np.asarray(im, dtype=np.int32)
    w, h = im.size
    l,t,r,z = [int(np.float32(v)*size) for v,size in zip(box,[w,h,w,h])]
    r,z = max(l+1,r),max(t+1,z)
    bw,bh = r-l,z-t
    xs = [l+min(bw-1,int((cell+(sub+.5)/3)*bw/16)) for cell in range(16) for sub in range(3)]
    ys = [t+min(bh-1,int((cell+(sub+.5)/3)*bh/16)) for cell in range(16) for sub in range(3)]
    result = []
    for y in range(16):
        for x in range(16):
            result.extend(np.float32(a[np.array(ys[y*3:y*3+3])[:,None],np.array(xs[x*3:x*3+3])[None,:]].sum(axis=(0,1))/(9*255)).tolist())
    return result

asset = root/'assets/poipoi-preset.json'
refs = json.loads(asset.read_text())
refs['decline'] = sample(anchors[0])+sample(anchors[1])
asset.write_text(json.dumps(refs,ensure_ascii=False,separators=(',',':')))
with (root/'tests/image6727-references.bin').open('wb') as f:
    f.write(struct.pack('>i',len(refs)))
    for key,values in refs.items():
        k=key.encode();f.write(struct.pack('>H',len(k))+k+struct.pack('>i',len(values))+struct.pack('>'+str(len(values))+'f',*values))
with gzip.open(root/'tests/image6727-replay.bin.gz','wb',compresslevel=6) as f:
    name=b'6727'
    f.write(struct.pack('>iH',1,len(name))+name+struct.pack('>ii',*im.size)+im.tobytes())
evidence = {
    'source':'01-6727.png','sha256':hashlib.sha256(source.read_bytes()).hexdigest(),
    'size':list(im.size),'package':'com.kurashiru.poipoi.usagi','anchors':anchors,
    'action':'使用しない','reference':'carrot illustration plus visible decline text; no covered title or ad accept button',
    'limits':'One supplied screenshot is shared by reference and replay. Resized, inset and occluded variants are synthetic; Android input and real post-decline transition are not tested.'
}
(root/'tests/image6727-evidence.json').write_text(json.dumps(evidence,ensure_ascii=False,indent=2)+'\n')
print('Updated Poipoi reference set:',len(refs),'entries; one original screenshot fixture.')
