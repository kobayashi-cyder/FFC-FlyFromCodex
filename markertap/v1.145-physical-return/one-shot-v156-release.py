#!/usr/bin/env python3
"""One-shot MarkerTap v1.156 original-key signer, with RSA-OAEP ephemeral handoff.

Only session public modulus and encrypted ciphertext are stored in GitHub.
The runner private RSA key, decrypted original JKS and passwords exist only
in a private temporary directory / process environment and are deleted.
Publish a signed v1.156 RC only after signature and exact certificate checks.
The stable README is NOT changed before Android device acceptance.
"""
import base64
import hashlib
import json
import os
from pathlib import Path
import secrets
import subprocess
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request

REPO = "kobayashi-cyder/FFC-FlyFromCodex"
BRANCH = "markertap/v1.156-manual-preview-timeout"
CHANNEL = "markertap/v1156-signer-handoff/"
TAG = "markertap-v1.156-rc1"
APP = "MarkerTap-v1.156-integrated-signed.apk"
CERT = "7f42d636e4d3c251f475eb998fbe8e92b515ded8bce3ff4d30aa90b9a738cfb1"
UNSIGNED = "d38e69927837ba3f6139d211cd1d848ca1b38745b48c7e556dfd0d86bc709b4a"
ROOT = Path(__file__).resolve().parent


def sh(*args, env=None, capture=False):
    return subprocess.run([str(a) for a in args], cwd=ROOT, env=env, check=True,
                          stdout=subprocess.PIPE if capture else None,
                          stderr=subprocess.PIPE if capture else None)


def sha256(path):
    h = hashlib.sha256()
    with Path(path).open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def api(path, body=None, method=None):
    token = os.environ["GH_TOKEN"]
    request = urllib.request.Request(
        "https://api.github.com/repos/" + REPO + "/" + path,
        data=None if body is None else json.dumps(body).encode("utf-8"),
        method=method,
        headers={"Authorization": "Bearer " + token,
                 "Accept": "application/vnd.github+json",
                 "Content-Type": "application/json",
                 "User-Agent": "MarkerTap-one-shot-v156",
                 "Cache-Control": "no-cache"})
    with urllib.request.urlopen(request, timeout=45) as response:
        return json.load(response)


def fetch(name):
    path = "contents/" + CHANNEL + name
    try:
        return api(path + "?ref=" + urllib.parse.quote(BRANCH, safe=""))
    except urllib.error.HTTPError as e:
        if e.code == 404:
            return None
        raise


def publish(name, content):
    old = fetch(name)
    body = {"message": "Publish ephemeral MarkerTap signing session (public metadata only)",
            "branch": BRANCH,
            "content": base64.b64encode((json.dumps(content, sort_keys=True) + "\n").encode()).decode()}
    if old is not None:
        body["sha"] = old["sha"]
    api("contents/" + CHANNEL + name, body, "PUT")


def cleanup(name):
    old = fetch(name)
    if old is None:
        return
    api("contents/" + CHANNEL + name, {
        "message": "Erase short-lived MarkerTap encrypted signing handoff",
        "branch": BRANCH, "sha": old["sha"]}, "DELETE")


def receive_original_signer(folder):
    rsa = folder / "ephemeral-rsa.pem"
    sh("openssl", "genpkey", "-algorithm", "RSA", "-pkeyopt", "rsa_keygen_bits:3072",
       "-pkeyopt", "rsa_keygen_pubexp:65537", "-out", rsa, capture=True)
    rsa.chmod(0o600)
    n = sh("openssl", "rsa", "-in", rsa, "-noout", "-modulus", capture=True)
    modulus = n.stdout.decode().strip().split("=", 1)[1]
    run_id = os.environ["GITHUB_RUN_ID"]
    session = secrets.token_hex(16)
    session_file = "session-" + run_id + ".json"
    inbox_file = "inbox-" + run_id + ".json"
    publish(session_file, {
        "run_id": run_id, "session": session,
        "status": "awaiting_encrypted_original_key",
        "algorithm": "RSA-OAEP-SHA256",
        "modulus_hex": modulus, "exponent": 65537,
        "modulus_bytes": 384,
        "inbox_path": CHANNEL + inbox_file,
    })
    print("Original-key handoff session prepared. Waiting for RSA-OAEP encrypted payload.", flush=True)
    try:
        deadline = time.monotonic() + 15 * 60
        while time.monotonic() < deadline:
            file = fetch(inbox_file)
            if file is None:
                time.sleep(5)
                continue
            payload = json.loads(base64.b64decode(file["content"]))
            if payload.get("run_id") != run_id or payload.get("session") != session:
                time.sleep(5)
                continue
            items = payload.get("chunks")
            if not isinstance(items, list) or not 1 <= len(items) <= 80:
                raise ValueError("Encrypted signing payload chunk count invalid")
            plaintext = bytearray()
            for index, encoded in enumerate(items):
                cipher = folder / "chunk.rsa"
                cipher.write_bytes(base64.b64decode(encoded, validate=True))
                label = ("markertap-v156:" + run_id + ":" + session + ":" +
                         str(index) + ":" + str(len(items))).encode()
                result = sh("openssl", "pkeyutl", "-decrypt", "-inkey", rsa,
                            "-in", cipher, "-pkeyopt", "rsa_padding_mode:oaep",
                            "-pkeyopt", "rsa_oaep_md:sha256", "-pkeyopt", "rsa_mgf1_md:sha256",
                            "-pkeyopt", "rsa_oaep_label:" + label.hex(), capture=True)
                plaintext.extend(result.stdout)
            if hashlib.sha256(plaintext).hexdigest() != payload.get("payload_sha256"):
                raise ValueError("Encrypted signing payload integrity mismatch")
            secret = json.loads(plaintext)
            key = base64.b64decode(secret["keystore_b64"], validate=True)
            if not (500 <= len(key) <= 65536):
                raise ValueError("Original signing keystore invalid size")
            password = secret["store_password"]
            key_password = secret.get("key_password", password)
            if not isinstance(password, str) or not password or not isinstance(key_password, str):
                raise ValueError("Signing password must be nonempty string")
            keystore = folder / "original.jks"
            keystore.write_bytes(key)
            keystore.chmod(0o600)
            for i in range(len(plaintext)):
                plaintext[i] = 0
            return keystore, password, key_password
        raise TimeoutError("No encrypted signing reference received in current CI run")
    finally:
        for name in (inbox_file, session_file):
            try:
                cleanup(name)
            except Exception:
                pass


def publish_candidate(signed):
    if sha256(signed) != sha256(ROOT / "output" / APP):
        raise ValueError("Signed APK digest changed before upload")
    result = sh("gh", "release", "view", TAG, "-R", REPO, capture=True) if False else None
    # Never overwrite an existing tag or signed APK asset.
    seen = subprocess.run(["gh", "release", "view", TAG, "-R", REPO], cwd=ROOT,
                          stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    if seen.returncode == 0:
        raise FileExistsError("Existing signed v1.156 RC release must be reviewed, not overwritten")
    digest = sha256(signed)
    output = ROOT / "output"
    (output / "SHA256SUMS-v1.156.txt").write_text(digest + "  " + APP + "\n")
    sh("gh", "release", "create", TAG,
       signed, output / "SHA256SUMS-v1.156.txt",
       output / "MarkerTap-v1.156-integrated-signed.validation.json",
       "--repo", REPO, "--target", os.environ["GITHUB_SHA"],
       "--title", "MarkerTap v1.156 original-certificate signed candidate — device validation pending",
       "--notes",
       "SHA-pinned update from signed v1.155: a 12-second per-display manual-preview timeout monitor clears stale manual state after user touch, does not tap ads or restart displays, and retains bounded loopback ADB retries. Original MarkerTap signing certificate and host/DEX audits verified. "
       "Pixel 10/Android17, two virtual displays, collapsed/offline state, ads and "
       "reward collection are not yet device tested. Pre-release only: "
       "v1.150 remains the current stable version.",
       "--prerelease", "--latest=false")
    url = "https://github.com/" + REPO + "/releases/download/" + TAG + "/" + APP
    for attempt in range(10):
        try:
            with urllib.request.urlopen(urllib.request.Request(
                    url, headers={"User-Agent": "MarkerTap-v156-signed-check",
                                  "Cache-Control": "no-cache"}), timeout=60) as response:
                data = response.read()
            if hashlib.sha256(data).hexdigest() == digest:
                print("PUBLIC ORIGINAL-CERTIFICATE SIGNED RELEASE VERIFIED", url)
                print("SHA256", digest)
                return
        except Exception:
            time.sleep(3)
    raise RuntimeError("Signed release created but anonymous download verification failed")


def main():
    if os.environ.get("GITHUB_REPOSITORY") != REPO or os.environ.get("GITHUB_REF_NAME") != BRANCH:
        raise RuntimeError("Unexpected repository/branch for original-key signing")
    unsigned = ROOT / "output" / "MarkerTap-v1.156-integrated-unsigned.apk"
    if not unsigned.exists() or sha256(unsigned) != UNSIGNED:
        raise ValueError("Unsigned integrated APK differs from audited CI digest")
    with tempfile.TemporaryDirectory(prefix="markertap-original-key-", dir=os.environ["RUNNER_TEMP"]) as temp:
        folder = Path(temp)
        folder.chmod(0o700)
        key, password, key_password = receive_original_signer(folder)
        env = os.environ.copy()
        env["MARKERTAP_STORE_PASSWORD"] = password
        env["MARKERTAP_KEY_PASSWORD"] = key_password
        signed = ROOT / "output" / APP
        sh("python3", "sign-v156-release.py",
           "--unsigned", unsigned, "--keystore", key,
           "--sdk", ROOT / "tooling" / "sdk", "--out", signed, env=env)
        del env
        receipt = json.loads((ROOT / "output" / "MarkerTap-v1.156-integrated-signed.validation.json").read_text())
        if (receipt.get("signed_apk_sha256") != sha256(signed)
                or receipt.get("certificate_sha256") != CERT
                or receipt.get("unsigned_apk_sha256") != UNSIGNED
                or receipt.get("certificate_matches_existing_install") is not True):
            raise ValueError("STOP: exact original certificate/signature or source digest mismatch")
        publish_candidate(signed)

if __name__ == "__main__":
    main()
