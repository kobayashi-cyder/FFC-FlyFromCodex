#!/usr/bin/env python3
"""Recreate preview-pixel fixtures from the supplied recording and screenshot.

These crops exclude the overlay title bars. They are not native virtual-display
framebuffer captures, and they do not identify who generated any visible input.
"""
import hashlib
import io
import json
import subprocess
import sys
from pathlib import Path
from PIL import Image

source, screenshot = map(Path, sys.argv[1:])
tests = Path(__file__).parent
root = tests / 'fixtures' / 'video6745'
root.mkdir(parents=True, exist_ok=True)
times = [0, 8, 16, 20, 24, 28, 32, 36, 44, 48, 52, 60, 64, 68, 72, 76,
         80, 84, 88, 92, 100, 108, 110]
video_boxes = {'farm': (60, 1272, 502, 2264), 'poipoi': (559, 1272, 1002, 2264)}
image_boxes = {'farm': (46, 425, 326, 1054), 'poipoi': (389, 484, 669, 1113)}
for second in times:
    raw = subprocess.check_output([
        'ffmpeg', '-v', 'error', '-ss', str(second), '-i', str(source),
        '-frames:v', '1', '-f', 'image2pipe', '-vcodec', 'png', 'pipe:1'])
    frame = Image.open(io.BytesIO(raw)).convert('RGB')
    assert frame.size == (1080, 2424), 'unexpected recording geometry'
    for name, box in video_boxes.items():
        frame.crop(box).save(root / f'{name}-{second}.png')
frame = Image.open(screenshot).convert('RGB')
assert frame.size == (684, 1536), 'unexpected screenshot geometry'
for name, box in image_boxes.items():
    frame.crop(box).save(root / f'{name}-screenshot.png')
evidence = {
    'source_name': source.name,
    'source_sha256': hashlib.sha256(source.read_bytes()).hexdigest(),
    'screenshot_name': screenshot.name,
    'screenshot_sha256': hashlib.sha256(screenshot.read_bytes()).hexdigest(),
    'seconds': times,
    'preview_crop_boxes': video_boxes,
    'screenshot_crop_boxes': image_boxes,
    'header_reference': 'poipoi-0.png, Google Play heading only',
    'limitations': 'Displayed preview pixels, not original framebuffer pixels. '
                   'Input completions, resize and occlusion tests are simulated. '
                   'Android device not connected.'
}
(tests / 'video6745-evidence.json').write_text(
    json.dumps(evidence, ensure_ascii=False, indent=2) + '\n')
print('extracted', len(times) * 2 + 2, 'preview images')
