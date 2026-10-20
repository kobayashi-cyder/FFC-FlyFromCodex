"""Prepare screenshot evidence; transformed replays are synthetic, not device results."""
import gzip
import hashlib
import json
from pathlib import Path
import struct
import sys

import numpy as np
from PIL import Image

root = Path(__file__).resolve().parent.parent
source = Path(sys.argv[1])
image = Image.open(source).convert('RGB')
assert image.size == (684, 1536)
# Neither landmark includes the operator panel or orange marker.
anchors = [[145/684, 910/1536, 541/684, 951/1536],
           [386/684, 1248/1536, 435/684, 1292/1536]]


def feature(im, bounds):
    data = np.asarray(im)
    h, w = data.shape[:2]
    left, top, right, bottom = (np.array(bounds, dtype=np.float32)
                                * np.array([w, h, w, h], dtype=np.float32)).astype(int)
    rw, rh = right-left, bottom-top
    values = []
    for y in range(16):
        for x in range(16):
            pixel = np.zeros(3, dtype=np.float32)
            for dy in range(3):
                for dx in range(3):
                    px = min(rw-1, int(np.float32(x+(dx+.5)/3)*rw/16))
                    py = min(rh-1, int(np.float32(y+(dy+.5)/3)*rh/16))
                    pixel += data[top+py, left+px]
            values.extend((pixel/np.float32(9*255)).tolist())
    return values


def utf(out, value):
    encoded = value.encode('utf-8')
    out.write(struct.pack('>H', len(encoded)))
    out.write(encoded)


refs = json.loads((root/'assets/coin-preset.json').read_text())
refs['boost-pot'] = sum((feature(image, bounds) for bounds in anchors), [])
for suffix, size, method in [('native', image.size, Image.Resampling.NEAREST),
                             ('half', (342, 768), Image.Resampling.NEAREST),
                             ('smooth', (720, 1616), Image.Resampling.BILINEAR)]:
    scaled = image.resize(size, method)
    refs['coin-control-boost-pot-133104-'+suffix] = sum(
        (feature(scaled, bounds) for bounds in anchors), [])
(root/'assets/coin-preset.json').write_text(json.dumps(refs, separators=(',', ':'))+'\n')
with (root/'tests/coin-cancel-references.bin').open('wb') as out:
    out.write(struct.pack('>i', len(refs)))
    for key, values in sorted(refs.items()):
        utf(out, key)
        out.write(struct.pack('>i', len(values)))
        out.write(struct.pack('>'+str(len(values))+'f', *values))
with gzip.open(root/'tests/coin-cancel-screenshot.bin.gz', 'wb') as out:
    out.write(struct.pack('>ii', *image.size))
    out.write(image.tobytes())
(root/'tests/coin-cancel-evidence.json').write_text(json.dumps({
    'source': source.name, 'sha256': hashlib.sha256(source.read_bytes()).hexdigest(),
    'size': image.size, 'anchors': anchors,
    'touch_bounds': [394/684, 1257/1536, 427/684, 1284/1536],
    'limits': 'One supplied screenshot. Resizes, translations, occlusions and temporal sequences are synthetic. Device input and actual dismissal remain untested.'
}, indent=2)+'\n')
print('Prepared screenshot fixture and four pot-cancel reference entries')
