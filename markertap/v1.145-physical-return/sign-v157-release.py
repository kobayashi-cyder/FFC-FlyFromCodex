#!/usr/bin/env python3
"""Sign the vetted integrated v1.157 APK with the SAME private key as installed MarkerTap.

This tool never downloads, displays, stores, commits or publishes the keystore.
Input must be build.py's unsigned, aligned v1.157 APK. The private keystore
is an explicit local path and passwords are injected via environment variables.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import zipfile

PACKAGE = "jp.tapmarker.lite"
VERSION = "1.157"
CODE = "160"
CERT_SHA256 = "7f42d636e4d3c251f475eb998fbe8e92b515ded8bce3ff4d30aa90b9a738cfb1"
# Byte-for-byte unsigned APK produced by the passing full-integration CI run.
# Update only after a new audited build and matching independent SHA-256.
EXPECTED_UNSIGNED_SHA256 = "5d03c73f9e84ffd2bb6de9ed2a6329311064f5cfa0a023a4fa1736db913b5ba2"

def sha256(path):
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()

def verify_unsigned_digest(path):
    actual = sha256(path)
    if actual != EXPECTED_UNSIGNED_SHA256:
        raise ValueError("STOP: unsigned APK differs from audited v1.157 CI artifact")
    return actual

def run(*cmd, env=None):
    p = subprocess.run([str(x) for x in cmd], env=env, stdout=subprocess.PIPE,
                       stderr=subprocess.STDOUT, text=True, check=True)
    return p.stdout

def check_badging(aapt2, apk):
    lines = run(aapt2, "dump", "badging", apk)
    for required in ("name='" + PACKAGE + "'", "versionCode='" + CODE + "'",
                     "versionName='" + VERSION + "'"):
        if required not in lines:
            raise ValueError("Unexpected APK identity: " + required)

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--unsigned", type=Path, required=True)
    parser.add_argument("--keystore", type=Path, required=True)
    parser.add_argument("--sdk", type=Path, required=True)
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--alias", default="tapmarker")
    args = parser.parse_args()
    unsigned, keystore = args.unsigned.resolve(), args.keystore.resolve()
    if not unsigned.is_file() or not keystore.is_file():
        raise FileNotFoundError("Expected the unsigned v1.157 APK and original JKS keystore")
    verify_unsigned_digest(unsigned)
    if unsigned == args.out.resolve():
        raise ValueError("Output cannot overwrite the unsigned build input")
    password = os.environ.get("MARKERTAP_STORE_PASSWORD")
    if not password:
        raise ValueError("MARKERTAP_STORE_PASSWORD must be set in the private shell")
    bt = args.sdk.resolve() / "build-tools" / "35.0.0"
    aapt2, zipalign, apksigner = (bt / n for n in ("aapt2", "zipalign", "apksigner"))
    for binary in (aapt2, zipalign, apksigner):
        if not binary.is_file():
            raise FileNotFoundError("Missing Android 35 build tool: " + str(binary))
    with zipfile.ZipFile(unsigned) as archive:
        if archive.testzip() is not None:
            raise ValueError("Corrupt unsigned input")
    check_badging(aapt2, unsigned)
    run(zipalign, "-c", "-P", "16", "4", unsigned)
    args.out.parent.mkdir(parents=True, exist_ok=True)
    env = os.environ.copy()
    # apksigner reads passwords from the environment, not from the command line.
    key_password = os.environ.get("MARKERTAP_KEY_PASSWORD")
    command = [apksigner, "sign", "--ks", keystore, "--ks-key-alias", args.alias,
               "--ks-pass", "env:MARKERTAP_STORE_PASSWORD"]
    if key_password:
        command.extend(["--key-pass", "env:MARKERTAP_KEY_PASSWORD"])
    command.extend(["--v4-signing-enabled", "false", "--out", args.out, unsigned])
    try:
        run(*command, env=env)
        certificate = run(apksigner, "verify", "--verbose", "--print-certs", args.out)
        if "Signer #1 certificate SHA-256 digest: " + CERT_SHA256 not in certificate:
            raise ValueError("STOP: new certificate does not match previously installed MarkerTap")
        if "Verified using v2 scheme (APK Signature Scheme v2): true" not in certificate:
            raise ValueError("APK v2 signature missing")
        if "Verified using v3 scheme (APK Signature Scheme v3): true" not in certificate:
            raise ValueError("APK v3 signature missing")
        run(zipalign, "-c", "-P", "16", "4", args.out)
        check_badging(aapt2, args.out)
        with zipfile.ZipFile(args.out) as archive:
            if archive.testzip() is not None:
                raise ValueError("Signed APK ZIP is corrupt")
    except BaseException:
        args.out.unlink(missing_ok=True)
        raise
    report = {
        "status": "signed_host_verified_not_device_tested",
        "package": PACKAGE,
        "version_name": VERSION,
        "version_code": int(CODE),
        "certificate_sha256": CERT_SHA256,
        "certificate_matches_existing_install": True,
        "signed_apk_sha256": sha256(args.out),
        "unsigned_apk_sha256": sha256(unsigned),
        "device_tested": False,
    }
    receipt = args.out.with_name(args.out.stem + ".validation.json")
    receipt.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print("Signature and update certificate verified: " + str(args.out))
    print("SHA256=" + report["signed_apk_sha256"])

if __name__ == "__main__":
    try:
        main()
    except Exception as exc:
        print(type(exc).__name__ + ": signing not completed", file=sys.stderr)
        raise
