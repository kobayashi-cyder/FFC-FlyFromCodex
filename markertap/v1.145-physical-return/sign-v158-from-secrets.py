#!/usr/bin/env python3
"""Sign v1.158 only with the original MarkerTap key from protected CI secrets.

The original JKS is NEVER committed, printed, or uploaded. The runner keeps it
in a private temporary directory, and the pre-existing v1.158 signer refuses
any certificate other than the exact SHA256 used by installed MarkerTap.
"""
import base64
import binascii
import hashlib
import json
import os
from pathlib import Path
import runpy
import subprocess
import tempfile

ROOT=Path(__file__).resolve().parent
APP="MarkerTap-v1.158-integrated-signed.apk"
ORIGINAL_CERT="7f42d636e4d3c251f475eb998fbe8e92b515ded8bce3ff4d30aa90b9a738cfb1"

def digest(path):
    h=hashlib.sha256()
    with path.open("rb") as f:
        for block in iter(lambda:f.read(1024*1024),b""):h.update(block)
    return h.hexdigest()

def main():
    if os.environ.get("GITHUB_REPOSITORY")!="kobayashi-cyder/FFC-FlyFromCodex" or os.environ.get("GITHUB_REF_NAME")!="markertap/v1.158-reward-gated-connectome":
        raise RuntimeError("Refusing to sign from an unexpected GitHub repository or branch")
    # Never log or serialize the original key or password into GitHub files.
    original=os.environ.get("MARKERTAP_ORIGINAL_JKS_B64","")
    password=os.environ.get("MARKERTAP_ORIGINAL_STORE_PASSWORD","")
    key_password=os.environ.get("MARKERTAP_ORIGINAL_KEY_PASSWORD","")
    audited=os.environ.get("MARKERTAP_EXPECTED_UNSIGNED_SHA256","")
    if not original or not password:
        raise RuntimeError("Required protected GitHub original-signing-key secrets are not configured; no signed release created")
    if len(audited)!=64 or any(c not in "0123456789abcdef" for c in audited):
        raise RuntimeError("Unsigned build SHA256 attestation missing")
    unsigned=ROOT/"output"/"MarkerTap-v1.158-integrated-unsigned.apk"
    if not unsigned.is_file() or digest(unsigned)!=audited:
        raise RuntimeError("Audited unsigned v1.158 APK is missing or differs from CI digest")
    try:
        key=base64.b64decode(original,validate=True)
    except (ValueError,binascii.Error) as exc:
        raise RuntimeError("Original signing secret must be a base64-encoded JKS file") from exc
    if not 500<=len(key)<=65536:
        raise RuntimeError("Original JKS byte length outside expected safe range")
    with tempfile.TemporaryDirectory(prefix="markertap-original-signing-",dir=os.environ.get("RUNNER_TEMP")) as temp:
        private=Path(temp)
        private.chmod(0o700)
        jks=private/"original.jks"
        jks.write_bytes(key)
        jks.chmod(0o600)
        del key
        env=os.environ.copy()
        # Do not pass encoded original key to the signer subprocess.
        env.pop("MARKERTAP_ORIGINAL_JKS_B64",None)
        env.pop("MARKERTAP_ORIGINAL_STORE_PASSWORD",None)
        env.pop("MARKERTAP_ORIGINAL_KEY_PASSWORD",None)
        env["MARKERTAP_STORE_PASSWORD"]=password
        env["MARKERTAP_KEY_PASSWORD"]=key_password or password
        signed=ROOT/"output"/APP
        command=["python3",str(ROOT/"sign-v158-release.py"),
                 "--unsigned",str(unsigned),
                 "--keystore",str(jks),
                 "--sdk",os.environ["ANDROID_HOME"],
                 "--out",str(signed),
                 "--alias",os.environ.get("MARKERTAP_ORIGINAL_KEY_ALIAS","tapmarker")]
        subprocess.run(command,cwd=ROOT,env=env,check=True)
        receipt=json.loads((ROOT/"output"/"MarkerTap-v1.158-integrated-signed.validation.json").read_text())
        if not (receipt.get("certificate_sha256")==ORIGINAL_CERT
                and receipt.get("certificate_matches_existing_install") is True
                and receipt.get("unsigned_apk_sha256")==audited
                and receipt.get("signed_apk_sha256")==digest(signed)):
            signed.unlink(missing_ok=True)
            raise RuntimeError("Signed certificate, package, unsigned digest, or signature mismatch; publication denied")
        # The verified publisher refuses to overwrite a release and checks
        # its publicly downloadable bytes match the signed APK.
        handoff=runpy.run_path(str(ROOT/"one-shot-v158-release.py"))
        handoff["publish_candidate"](signed)

if __name__=="__main__":
    main()
