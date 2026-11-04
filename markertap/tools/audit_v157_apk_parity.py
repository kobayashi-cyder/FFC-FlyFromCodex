#!/usr/bin/env python3
"""Compare a rebuilt MarkerTap APK with an authenticated, signed v1.157 APK.

The diff is a hard release gate: losing any v1.157 class or asset is a
regression unless a reviewed behavior-parity migration explicitly approves it.
Does NOT certify functional equivalence or establish signature continuity.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import zipfile

from check_preserved_features import defined

V157_SHA256 = "0108aadfa717c676dfe30b4f678a5c1526277e11b01ae16f85317d8b8dd50f7d"

def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()

def scan(path: Path):
    with zipfile.ZipFile(path) as z:
        corruption=z.testzip()
        if corruption:
            raise ValueError(f"Corrupt APK entry {path}: {corruption}")
        classes=set()
        assets={}
        for name in z.namelist():
            if name=="classes.dex" or (name.startswith("classes") and name.endswith(".dex")):
                classes.update(defined(z.read(name)))
            elif name.startswith("assets/") and not name.endswith("/"):
                assets[name]=digest(z.read(name))
        return classes,assets

def main():
    p=argparse.ArgumentParser()
    p.add_argument("--baseline",type=Path,required=True)
    p.add_argument("--candidate",type=Path,required=True)
    p.add_argument("--report",type=Path,required=True)
    args=p.parse_args()
    actual=digest(args.baseline.read_bytes())
    if actual != V157_SHA256:
        raise SystemExit("STOP: baseline is not the exact signed v1.157 release")
    old_classes,old_assets=scan(args.baseline)
    new_classes,new_assets=scan(args.candidate)
    missing_classes=sorted(old_classes-new_classes)
    missing_assets=sorted(old_assets.keys()-new_assets.keys())
    changed_assets=sorted(
        n for n in old_assets.keys()&new_assets.keys()
        if old_assets[n]!=new_assets[n])
    report={
        "baseline_sha256":actual,
        "candidate_sha256":digest(args.candidate.read_bytes()),
        "old_classes":len(old_classes),
        "new_classes":len(new_classes),
        "removed_class_count":len(missing_classes),
        "removed_classes":missing_classes,
        "removed_asset_count":len(missing_assets),
        "removed_assets":missing_assets,
        "changed_asset_count":len(changed_assets),
        "changed_assets":changed_assets,
        "result":"PASS_structural_only" if not any(
            [missing_classes,missing_assets,changed_assets]) else "FAIL_preservation",
        "warning":"Passing is not evidence of behavior equivalence, Android 17 compatibility, or original signature.",
    }
    args.report.parent.mkdir(parents=True,exist_ok=True)
    args.report.write_text(json.dumps(report,indent=2,ensure_ascii=False)+"\n",
                           encoding="utf-8")
    print(json.dumps({k:v for k,v in report.items()
        if k not in ("removed_classes","removed_assets","changed_assets")},
        ensure_ascii=False,indent=2))
    print("Missing classes (first 20):",missing_classes[:20])
    print("Missing assets:",missing_assets[:20])
    if report["result"].startswith("FAIL"):
        raise SystemExit("STOP: v1.157 APK features/assets not preserved; no release")

if __name__=="__main__":
    main()
