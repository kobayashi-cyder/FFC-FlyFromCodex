#!/usr/bin/env python3
"""Store the four supplied full screenshots without training recognition on them."""
from pathlib import Path
from PIL import Image
import gzip
import struct
import sys

root = Path(sys.argv[1])
output = Path(__file__).with_name('ad-stalls-20261013.bin.gz')
names = [('6695', '04-6695.png'), ('6696', '03-6696.png'),
         ('6697', '02-6697.png'), ('6698', '01-6698.png')]
with gzip.open(output, 'wb') as out:
    out.write(struct.pack('>i', len(names)))
    for name, source in names:
        image = Image.open(root / source).convert('RGB')
        encoded = name.encode('ascii')
        out.write(struct.pack('>H', len(encoded)))
        out.write(encoded)
        out.write(struct.pack('>ii', *image.size))
        out.write(image.tobytes())
print(output.name)
