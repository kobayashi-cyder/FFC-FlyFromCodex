#!/usr/bin/env python3
"""Fail-closed integrated build: pinned baseline -> Java host tests -> DEX -> old-key signature.

NEVER stage the signing key, password or user diagnostics under the output folder.
This script does NOT publish untested APKs to GitHub by itself.
"""
from pathlib import Path
import argparse
import hashlib
import json
import os
import subprocess
import sys

ROOT=Path(__file__).resolve().parent
BASE_SHA="f62e8d6020a3cacb2ea9a9085be235ea48da12f03c5de2f4e3ae80b801b6e970"
VERSION="1.150"
NAME="MarkerTap-v1.150-integrated-signed.apk"

def sha256(path):
    digest=hashlib.sha256()
    with path.open("rb") as f:
        for part in iter(lambda:f.read(1024*1024),b""):
            digest.update(part)
    return digest.hexdigest()

def run(cmd):
    subprocess.run([str(x) for x in cmd],cwd=ROOT,check=True)

def main():
    p=argparse.ArgumentParser()
    p.add_argument("--baseline",type=Path,required=True)
    p.add_argument("--keystore",type=Path,required=True)
    p.add_argument("--sdk",type=Path,required=True)
    p.add_argument("--jadx",type=Path,required=True)
    p.add_argument("--outdir",type=Path,default=ROOT/"output")
    a=p.parse_args()
    base=a.baseline.resolve()
    key=a.keystore.resolve()
    outdir=a.outdir.resolve()
    if not base.is_file() or sha256(base)!=BASE_SHA:
        raise ValueError("STOP: original v1.144 integrated baseline SHA-256 mismatch")
    if not key.is_file():
        raise FileNotFoundError("STOP: existing MarkerTap keystore is required")
    if not os.environ.get("MARKERTAP_STORE_PASSWORD"):
        raise ValueError("STOP: signing password is not in a private environment variable")
    if outdir==key.parent or key.is_relative_to(outdir):
        raise ValueError("STOP: signing key must never be in public output directory")
    run([sys.executable,ROOT/"run-tests.py"])
    run([sys.executable,ROOT/"build.py","--baseline",base,"--sdk",a.sdk.resolve(),"--jadx",a.jadx.resolve()])
    unsigned=ROOT/"output/MarkerTap-v1.150-integrated-unsigned.apk"
    outdir.mkdir(parents=True,exist_ok=True)
    signed=outdir/NAME
    run([sys.executable,ROOT/"sign-release.py","--unsigned",unsigned,"--keystore",key,"--sdk",a.sdk.resolve(),"--out",signed])
    receipt=signed.with_name(signed.stem+".validation.json")
    result=json.loads(receipt.read_text(encoding="utf-8"))
    if result["signed_apk_sha256"]!=sha256(signed) or result["version_name"]!=VERSION:
        signed.unlink(missing_ok=True)
        raise ValueError("STOP: signed file differs from independent receipt")
    (outdir/"SHA256SUMS-v1.150.txt").write_text(sha256(signed)+"  "+signed.name+"\n",encoding="utf-8")
    print("Signed integrated APK host verified (not device tested):",signed)
    print("SHA256="+sha256(signed))

if __name__=="__main__":
    main()
