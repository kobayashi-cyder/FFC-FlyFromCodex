#!/usr/bin/env python3
from pathlib import Path
import hashlib
import json

ROOT = Path(__file__).resolve().parent
manifest = json.loads((ROOT / "manifest.json").read_text(encoding="utf-8"))

def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()

for spec in manifest["files"]:
    out_path = ROOT / spec["source"]
    with out_path.open("wb") as out:
        for part in spec["parts"]:
            p = ROOT / part["name"]
            data = p.read_bytes()
            if len(data) != part["size"]:
                raise SystemExit(f"size mismatch: {p.name}")
            if sha256(data) != part["sha256"]:
                raise SystemExit(f"SHA-256 mismatch: {p.name}")
            out.write(data)

    data = out_path.read_bytes()
    if len(data) != spec["size"]:
        raise SystemExit(f"reconstructed size mismatch: {out_path.name}")
    if sha256(data) != spec["sha256"]:
        raise SystemExit(f"reconstructed SHA-256 mismatch: {out_path.name}")
    print(f"OK {out_path.name} {len(data)} bytes {spec['sha256']}")
