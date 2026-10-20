"""Extract regression frames without changing any recognition template.
Usage: python3 tests/prepare-video6750.py /absolute/path/6750.mp4
"""
from pathlib import Path
import gzip, hashlib, json, struct, subprocess, sys

root = Path(__file__).resolve().parent.parent
video = Path(sys.argv[1]).resolve()
times = [1.5, 10.5, 18, 28, 42, 44, 46, 50, 54, 55, 58, 61, 64, 66, 70,
         72, 74, 78, 80, 82, 84.5, 85.5, 88, 90, 92]
frames = [(str(t), t, 540) for t in times] + [("80-native", 80, 1080), ("72-native", 72, 1080)]
with gzip.open(root / "tests/video6750-replay.bin.gz", "wb", compresslevel=6) as out:
    out.write(struct.pack(">i", len(frames)))
    for name, timestamp, width in frames:
        height = width * 2424 // 1080
        raw = subprocess.check_output(["ffmpeg", "-v", "error", "-ss", str(timestamp),
            "-i", str(video), "-frames:v", "1", "-vf", f"scale={width}:{height}",
            "-pix_fmt", "rgb24", "-f", "rawvideo", "-"])
        assert len(raw) == width * height * 3
        key = name.encode()
        out.write(struct.pack(">H", len(key)) + key + struct.pack(">ii", width, height) + raw)
(root / "tests/video6750-evidence.json").write_text(json.dumps({
    "video": "6750.mp4", "sha256": hashlib.sha256(video.read_bytes()).hexdigest(),
    "frames": frames, "reference_changes": False,
    "purpose": "Reproduce mission tap failure with existing shipped templates; Android input delivery is not tested."
}, indent=2) + "\n")
print("Wrote", len(frames), "frames; recognition templates unchanged")
