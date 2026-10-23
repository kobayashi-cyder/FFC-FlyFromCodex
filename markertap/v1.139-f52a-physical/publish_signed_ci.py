#!/usr/bin/env python3
"""One-shot v1.139 signing using the original MarkerTap certificate.

The private keystore is received through the established ephemeral RSA-OAEP
handoff. It is never written to Git, Actions artifacts or public logs.
Requires an explicit publish branch run and a matching encrypted key handoff.
"""
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import time
import urllib.request
import zipfile

ROOT = Path(os.environ["GITHUB_WORKSPACE"])
sys.path.insert(0, str(ROOT / "markertap/tools"))
import import_source as channel

CHANNEL = "markertap/v1139-signed-publish-channel/"
channel.CHANNEL = CHANNEL
spec = importlib.util.spec_from_file_location(
    "secure_signing", ROOT / "markertap/tools/build_source.py")
transport = importlib.util.module_from_spec(spec)
spec.loader.exec_module(transport)
channel.CHANNEL = CHANNEL

REPO = "kobayashi-cyder/FFC-FlyFromCodex"
BRANCH = "markertap/publish-signed-v1.139"
CI_RUN_ID = 40033135831
CI_ARTIFACT_ID = 12536465241
CI_ARTIFACT_DIGEST = "sha256:e2a936804418a08f6034367ceb8228b94ad79475c61f98e6291a667cd2aa5e9a"
CI_SOURCE_COMMIT = "73fc415c9ed2cb598f8afe4657b945e9983c71a5"
CERT = "7f42d636e4d3c251f475eb998fbe8e92b515ded8bce3ff4d30aa90b9a738cfb1"
TAG = "markertap-v1.139-f52a"
UNSIGNED = "MarkerTap-v1.139-unsigned.apk"
SIGNED = "MarkerTap-v1.139-F52A-signed.apk"


def sha256(path):
    result = hashlib.sha256()
    with open(path, "rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            result.update(block)
    return result.hexdigest()


def command(*args):
    return subprocess.check_output(args, stderr=subprocess.STDOUT, text=True)


def verify_download(url, expected):
    for attempt in range(12):
        try:
            request = urllib.request.Request(
                url + "?proof=" + expected,
                headers={"User-Agent": "MarkerTap-v1139-signing-verification",
                         "Cache-Control": "no-cache"})
            with urllib.request.urlopen(request, timeout=45) as response:
                if response.status == 200 and hashlib.sha256(response.read()).hexdigest() == expected:
                    return
        except Exception:
            pass
        time.sleep(3)
    raise RuntimeError("public signed APK download failed SHA-256 verification")


def verify_unsigned():
    metadata = channel.api("actions/artifacts/" + str(CI_ARTIFACT_ID))
    if (metadata["name"] != "MarkerTap-v1.139-F52A-unsigned-evidence"
            or metadata["digest"] != CI_ARTIFACT_DIGEST
            or metadata["workflow_run"]["id"] != CI_RUN_ID
            or metadata["workflow_run"]["head_sha"] != CI_SOURCE_COMMIT
            or metadata["expired"]):
        raise RuntimeError("the pinned v1.139 build artifact has changed or expired")
    folder = ROOT / "signed-upload-input"
    validation = json.loads((folder / "validation.json").read_text())
    expected = {"status": "unsigned_host_verified", "version_name": "1.139",
                "version_code": 142, "package": "jp.tapmarker.lite",
                "source_commit": CI_SOURCE_COMMIT, "apk": UNSIGNED,
                "signature_verified": False, "device_tested": False}
    for field, value in expected.items():
        if validation.get(field) != value:
            raise RuntimeError("unexpected unsigned build metadata: " + field)
    apk = folder / UNSIGNED
    if not apk.is_file() or sha256(apk) != validation["apk_sha256"]:
        raise RuntimeError("unsigned APK digest mismatch")
    with zipfile.ZipFile(apk) as package:
        if package.testzip() is not None:
            raise RuntimeError("unsigned APK ZIP CRC failed")
    return apk, validation


def main():
    if os.environ["GITHUB_REF_NAME"] != BRANCH:
        raise RuntimeError("publisher must run from the explicit signed release branch")
    if os.environ["GITHUB_REPOSITORY"] != REPO:
        raise RuntimeError("unexpected publisher repository")
    unsigned, checked = verify_unsigned()  # Verify before requesting the private key.
    with tempfile.TemporaryDirectory(prefix="markertap-v1139-private-",
                                     dir=os.environ["RUNNER_TEMP"]) as tmp:
        private = Path(tmp)
        private.chmod(0o700)
        keystore = transport.receive_key(private)
        build_tools = Path(os.environ["ANDROID_HOME"]) / "build-tools/35.0.0"
        signed = private / SIGNED
        subprocess.run([str(build_tools / "apksigner"), "sign",
                        "--ks", str(keystore), "--ks-key-alias", "tapmarker",
                        "--ks-pass", "pass:android",
                        "--v4-signing-enabled", "false",
                        "--out", str(signed), str(unsigned)], check=True)
        verified = command(str(build_tools / "apksigner"), "verify", "--verbose",
                           "--print-certs", str(signed))
        if "Signer #1 certificate SHA-256 digest: " + CERT not in verified:
            raise RuntimeError("signed APK certificate does not match existing installs")
        badging = command(str(build_tools / "aapt2"), "dump", "badging", str(signed))
        for word in ("name='jp.tapmarker.lite'", "versionCode='142'", "versionName='1.139'"):
            if word not in badging:
                raise RuntimeError("signed APK package identity mismatch")
        command(str(build_tools / "zipalign"), "-c", "-P", "16", "4", str(signed))
        with zipfile.ZipFile(signed) as package:
            if package.testzip() is not None:
                raise RuntimeError("signed APK ZIP CRC failed")
        checksum = sha256(signed)
        receipt = {
            "status": "signed_host_verified",
            "version_name": "1.139", "version_code": 142,
            "package": "jp.tapmarker.lite",
            "original_certificate_match": True,
            "signing_certificate_sha256": CERT,
            "unsigned_sha256": checked["apk_sha256"],
            "signed_apk_sha256": checksum, "source_run_id": CI_RUN_ID,
            "source_commit": CI_SOURCE_COMMIT,
            "f52a_device_tested": False,
            "physical_zip_jpeg_confirmed": False,
            "public_download_verified": False,
        }
        release = ROOT / "signed-publication"
        release.mkdir(exist_ok=True)
        shutil.copyfile(signed, release / SIGNED)
        (release / "SHA256SUMS.txt").write_text(checksum + "  " + SIGNED + "\n")
        (release / "validation.json").write_text(
            json.dumps(receipt, ensure_ascii=False, indent=2) + "\n")
        notes = private / "notes.md"
        notes.write_text(
            "MarkerTap v1.139 F-52A physical-display recovery and one-shot screenshot "
            "diagnostic ZIP.\n\n"
            "Audited CI source: " + CI_SOURCE_COMMIT + ". "
            "This signed APK matches the original MarkerTap signing certificate. "
            "F-52A real-device behavior and image-containing ZIP are NOT yet verified.\n\n"
            "SHA-256: " + checksum + "\n")
        subprocess.run([
            "gh", "release", "create", TAG, str(release / SIGNED),
            str(release / "SHA256SUMS.txt"),
            str(release / "validation.json"),
            "--repo", REPO, "--target", os.environ["GITHUB_SHA"],
            "--title", "MarkerTap v1.139 F-52A original-certificate signed APK",
            "--notes-file", str(notes), "--prerelease", "--latest=false"],
            check=True, timeout=120)
        url = "https://github.com/" + REPO + "/releases/download/" + TAG + "/" + SIGNED
        verify_download(url, checksum)
        receipt.update({"status": "public_release_verified",
                        "public_download_verified": True, "apk_url": url})
        (release / "validation.json").write_text(
            json.dumps(receipt, ensure_ascii=False, indent=2) + "\n")
        channel.publish("result.json", receipt)
        print("v1.139 original-certificate signed release verified: " + url)


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        try:
            channel.publish("result.json", {
                "status": "failed", "failure_class": type(error).__name__,
                "public_download_verified": False})
        except Exception:
            pass
        raise
