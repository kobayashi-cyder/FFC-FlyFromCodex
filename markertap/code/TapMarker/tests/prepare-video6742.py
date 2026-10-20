#!/usr/bin/env python3
"""Extract unchanged RGB frames from the user-supplied recording; never update recognition templates."""
import gzip
import hashlib
import json
import struct
import subprocess
import sys
from pathlib import Path

source = Path(sys.argv[1])
root = Path(__file__).parent
times = [0, 2, 4, 8, 18, 20, 24, 26, 28, 32, 34, 36, 38, 44]
probe = json.loads(subprocess.check_output([
    'ffprobe', '-v', 'error', '-show_streams', '-show_format', '-of', 'json', str(source)]))
video = next(s for s in probe['streams'] if s['codec_type'] == 'video')
width, height = video['width'], video['height']
with gzip.open(root / 'video6742-replay.bin.gz', 'wb', compresslevel=6) as out:
    out.write(struct.pack('>i', len(times)))
    for second in times:
        pixels = subprocess.check_output([
            'ffmpeg', '-v', 'error', '-ss', str(second), '-i', str(source),
            '-frames:v', '1', '-f', 'rawvideo', '-pix_fmt', 'rgb24', 'pipe:1'])
        assert len(pixels) == width * height * 3
        label = str(second).encode('ascii')
        out.write(struct.pack('>H', len(label)) + label + struct.pack('>ii', width, height) + pixels)
evidence = {
    'source_name': source.name, 'source_sha256': hashlib.sha256(source.read_bytes()).hexdigest(),
    'width': width, 'height': height, 'duration_seconds': float(probe['format']['duration']),
    'sample_seconds': times, 'pixels': 'native decoded RGB, no recognition templates changed',
    'observations': {'18': 'Google Play brand pill, not an exit', '20': 'external store',
                     '24': 'end card before Close', '26': 'Close at upper right',
                     '34': 'second ad with upper-right Close', '36': 'Close with touch indicator',
                     '28,32,38,44': 'farm game visible'},
    'limits': 'Input source (human/automation) and installed APK version are not identifiable; event order and input completions in tests are simulated.'
}
(root / 'video6742-evidence.json').write_text(json.dumps(evidence, ensure_ascii=False, indent=2) + '\n')
print('extracted', len(times), 'native frames', width, height)
