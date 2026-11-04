#!/usr/bin/env python3
"""Fail-closed verification for the movable minimized MarkerTap control.

This checks the actual signed APK's DEX classes and specific UI contract
strings, not just the presence of Java source. When given a candidate APK
it also checks that none of the original panel classes disappear and that
the contract strings are still encoded in its DEX.

It is a structural check, not a substitute for Android17 touch tests.
"""
from __future__ import annotations
import argparse
import hashlib
import json
import struct
import sys
import zipfile
from pathlib import Path
from check_preserved_features import defined, uleb

BASELINE_SHA256="0108aadfa717c676dfe30b4f678a5c1526277e11b01ae16f85317d8b8dd50f7d"
REQUIRED_CLASSES={
 "Ljp/tapmarker/PanelModePolicy;",
 "Ljp/tapmarker/PanelModeController;",
}
REQUIRED_LITERALS={
 "panelUi", "displayMode", "lastExpandedMode",
 "minimizedX", "minimizedY", "MarkerTapに戻す",
 "最小化", "フロートに戻す",
}

def strings(dex):
    count,offset=struct.unpack_from("<II",dex,0x38)
    if count>200000 or offset+count*4>len(dex):
        raise ValueError("Corrupt DEX string table")
    results=set()
    for i in range(count):
        start=struct.unpack_from("<I",dex,offset+i*4)[0]
        if start>=len(dex):raise ValueError("Invalid DEX string offset")
        _,cursor=uleb(dex,start)
        end=dex.index(b"\0",cursor)
        results.add(dex[cursor:end].decode("utf-8","replace"))
    return results

def inspect(apk):
    all_classes=set()
    all_strings=set()
    with zipfile.ZipFile(apk) as z:
        if z.testzip() is not None:raise ValueError("Bad APK ZIP CRC")
        dex_files=[n for n in z.namelist()
             if n=="classes.dex" or
             (n.startswith("classes") and n.endswith(".dex")
              and n[7:-4].isdigit())]
        if not dex_files:raise ValueError("No classes.dex")
        for name in dex_files:
            dex=z.read(name)
            all_classes.update(defined(dex))
            all_strings.update(strings(dex))
    return all_classes,all_strings

def main():
    p=argparse.ArgumentParser()
    p.add_argument("--baseline",required=True,type=Path)
    p.add_argument("--candidate",type=Path)
    p.add_argument("--report",type=Path)
    args=p.parse_args()
    signed=hashlib.sha256(args.baseline.read_bytes()).hexdigest()
    if signed!=BASELINE_SHA256:
        raise SystemExit("STOP: unsigned or unexpected v1.157 baseline")
    old_classes,old_strings=inspect(args.baseline)
    old_ui={c for c in old_classes if c.startswith(
       "Ljp/tapmarker/PanelModeController") or c.startswith(
       "Ljp/tapmarker/PanelModePolicy")}
    missing_old_classes=sorted(REQUIRED_CLASSES-old_classes)
    missing_old_strings=sorted(REQUIRED_LITERALS-old_strings)
    missing_candidate_classes=[]
    missing_candidate_strings=[]
    if args.candidate:
        candidate_classes,candidate_strings=inspect(args.candidate)
        missing_candidate_classes=sorted(old_ui-candidate_classes)
        missing_candidate_strings=sorted(REQUIRED_LITERALS-candidate_strings)
    valid=not any([missing_old_classes,missing_old_strings,
                   missing_candidate_classes,missing_candidate_strings])
    result={
        "signed_baseline_sha256":signed,
        "old_panel_class_count":len(old_ui),
        "required_modes":["floating","docked","minimized"],
        "required_behavior":"draggable minimization restores previous mode and keeps automation running",
        "baseline_missing_classes":missing_old_classes,
        "baseline_missing_literals":missing_old_strings,
        "candidate_missing_classes":missing_candidate_classes,
        "candidate_missing_literals":missing_candidate_strings,
        "status":"PASS_structural_only" if valid else "FAIL_do_not_release",
        "device_behavior_tested":False,
    }
    if args.report:
        args.report.parent.mkdir(parents=True,exist_ok=True)
        args.report.write_text(json.dumps(result,indent=2,ensure_ascii=False)+"\n",encoding="utf-8")
    print(json.dumps(result,indent=2,ensure_ascii=False))
    if not valid:raise SystemExit("STOP: minimized floating restore is not preserved")
if __name__=="__main__":main()
