#!/usr/bin/env python3
"""Restore the saved source, receive a private key in memory-safe transport, test, sign and publish."""
import base64, hashlib, json, os, pathlib, re, secrets, shutil, subprocess, sys, tempfile, time, zipfile
import import_source as channel

channel.CHANNEL = "markertap/build-channel/"
ROOT = channel.ROOT
BRANCH = channel.BRANCH
EXPECTED_CERT = "7f42d636e4d3c251f475eb998fbe8e92b515ded8bce3ff4d30aa90b9a738cfb1"
KEY_NAME = "MarkerTap-lite-signing-backup.zip"
OUTPUT_NAME = "MarkerTap-v1.123-preview-connectome.apk"

def receive_key(private):
    key = private / "transport-key.pem"
    channel.command(["openssl", "genpkey", "-algorithm", "RSA", "-pkeyopt", "rsa_keygen_bits:3072",
        "-pkeyopt", "rsa_keygen_pubexp:65537", "-out", str(key)])
    key.chmod(0o600)
    modulus = channel.command(["openssl", "rsa", "-in", str(key), "-noout", "-modulus"]).stdout.decode().strip().split("=", 1)[1]
    session = secrets.token_hex(16)
    channel.publish("session.json", {"run_id": channel.RUN_ID, "attempt": channel.ATTEMPT,
        "session": session, "algorithm": "RSA-OAEP-SHA256", "modulus_hex": modulus,
        "exponent": 65537, "modulus_bytes": 384, "status": "awaiting_encrypted_signing_reference"})
    deadline = time.monotonic() + 15 * 60
    while time.monotonic() < deadline:
        inbox = channel.read_channel("inbox.json")
        if inbox and inbox.get("session") == session:
            break
        time.sleep(5)
    else:
        raise RuntimeError("private signing reference was not received")
    chunks = inbox["chunks"]
    if not 1 <= len(chunks) <= 1000:
        raise RuntimeError("invalid payload size")
    plaintext = bytearray()
    for index, item in enumerate(chunks):
        cipher = private / "chunk.rsa"
        cipher.write_bytes(base64.b64decode(item, validate=True))
        label = ("markertap-source-v1:" + session + ":" + str(index) + ":" + str(len(chunks))).encode()
        plaintext.extend(channel.command(["openssl", "pkeyutl", "-decrypt", "-inkey", str(key), "-in", str(cipher),
            "-pkeyopt", "rsa_padding_mode:oaep", "-pkeyopt", "rsa_oaep_md:sha256",
            "-pkeyopt", "rsa_mgf1_md:sha256", "-pkeyopt", "rsa_oaep_label:" + label.hex()]).stdout)
    if hashlib.sha256(plaintext).hexdigest() != inbox["payload_sha256"]:
        raise RuntimeError("payload integrity check failed")
    payload = json.loads(plaintext)
    # An encrypted direct keystore is accepted as a one-shot alternative to
    # dynamic Library transfer helpers. Only the temporary runner sees plaintext.
    if "keystore_b64" in payload:
        raw = base64.b64decode(payload["keystore_b64"], validate=True)
        if len(raw) > 65536 or len(raw) < 500:
            raise RuntimeError("unexpected private keystore size")
        keystore = private / "signing.jks"
        keystore.write_bytes(raw)
        keystore.chmod(0o600)
        return keystore
    transfer = payload["transfer"]
    if transfer.get("file_name") != KEY_NAME:
        raise RuntimeError("unexpected signing backup")
    helpers = private / "helpers"
    helpers.mkdir(mode=0o700)
    (helpers / "library_file_transfer.py").write_text(payload["transfer_helper"])
    (helpers / "library_hosted_apps.py").write_text(payload["hosted_apps_helper"])
    archive = private / KEY_NAME
    channel.command([sys.executable, str(helpers / "library_file_transfer.py"), "materialize", str(archive)],
        input=json.dumps(transfer).encode())
    with zipfile.ZipFile(archive) as z:
        candidates = [info for info in z.infolist() if info.filename.lower().endswith((".jks", ".keystore"))]
        if len(candidates) != 1:
            raise RuntimeError("signing backup must contain exactly one keystore")
        keystore = private / "signing.jks"
        keystore.write_bytes(z.read(candidates[0]))
        keystore.chmod(0o600)
    return keystore

def logged(args, cwd, env, destination):
    with destination.open("wb") as stream:
        result = subprocess.run(args, cwd=cwd, env=env, stdout=stream, stderr=subprocess.STDOUT)
    if result.returncode:
        raise RuntimeError("stage failed: " + destination.stem)

def publish_failure(private, stage, error):
    channel.publish("result.json", {"run_id": channel.RUN_ID, "status": "failed",
        "stage": stage, "error_type": type(error).__name__, "apk_published": False})
    # Build/test output contains no transfer reference or key contents. Preserve
    # it for diagnosis, while all private material stays in the temporary directory.
    logs = {}
    for name in ("build.log", "tests.log"):
        path = private / name
        if path.exists():
            lines = path.read_text(errors="replace").splitlines()
            logs[name] = lines[-80:]
    if logs:
        channel.publish("failure-log.json", {"run_id": channel.RUN_ID, "stage": stage, "logs": logs})

def main():
    if BRANCH != "markertap/recovery":
        raise RuntimeError("unexpected branch")
    with tempfile.TemporaryDirectory(prefix="markertap-build-private-", dir=os.environ["RUNNER_TEMP"]) as directory:
        private = pathlib.Path(directory)
        stage = "private_signing_reference"
        try:
            keystore = receive_key(private)
            stage = "restore_source"
            restored = private / "restored"
            channel.command([sys.executable, str(ROOT / "markertap/tools/restore_source.py"),
                "--output", str(private / "baseline.zip"), "--extract-to", str(restored)], cwd=ROOT)
            project = restored / "TapMarker"
            if not (project / "build.sh").is_file():
                raise RuntimeError("restored project is missing build.sh")
            env = os.environ.copy()
            env["ANDROID_SDK_ROOT"] = os.environ["ANDROID_HOME"]
            env["TAPMARKER_KEYSTORE"] = str(keystore)
            output = private / OUTPUT_NAME
            env["TAPMARKER_OUTPUT"] = str(output)
            stage = "build"
            channel.publish("progress.json", {"run_id": channel.RUN_ID, "stage": stage})
            logged(["bash", "build.sh"], project, env, private / "build.log")
            stage = "core_tests"
            channel.publish("progress.json", {"run_id": channel.RUN_ID, "stage": stage})
            logged(["bash", "run-tests.sh"], project, env, private / "tests.log")
            stage = "apk_verification"
            tools = pathlib.Path(env["ANDROID_SDK_ROOT"]) / "build-tools/35.0.0"
            verification = channel.command([str(tools / "apksigner"), "verify", "--verbose", "--print-certs", str(output)]).stdout.decode()
            cert = re.search(r"certificate SHA-256 digest:\s*([0-9a-fA-F]+)", verification)
            if cert is None or cert.group(1).lower() != EXPECTED_CERT:
                raise RuntimeError("APK signing certificate does not match the existing version")
            badging = channel.command([str(tools / "aapt2"), "dump", "badging", str(output)]).stdout.decode()
            if "name='jp.tapmarker.lite'" not in badging or "versionCode='126'" not in badging or "versionName='1.123'" not in badging:
                raise RuntimeError("unexpected APK identity")
            channel.command([str(tools / "zipalign"), "-c", "-P", "16", "4", str(output)])
            with zipfile.ZipFile(output) as apk:
                if apk.testzip() is not None:
                    raise RuntimeError("APK ZIP CRC verification failed")
            validation = {"version_name": "1.123", "version_code": 126, "package": "jp.tapmarker.lite",
                "core_tests": "passed", "signing_certificate_sha256": EXPECTED_CERT,
                "apk_sha256": channel.digest(output), "apk_size_bytes": output.stat().st_size,
                "zip_alignment": "passed_16KB", "source_baseline": "1.117",
                "source_overlay_commit": os.environ["GITHUB_SHA"], "device_tested": False,
                "native_resolution": "implemented", "legacy_low_resolution_selection": "disabled",
                "signing_key_included": False,
                "region_capture": "native_sparse_point_reads_no_full_argb_array",
                "region_preview": "fresh_pixel_comparison_per_patch",
                "graph_external_helpers": "same_frame_dynamic_tile_expansion",
                "device_multiplex_capacity": "not_measured",
                "session_recovery": "manual_active_and_automatic_modes",
                "wifi_loss_keeps_live_bootstrap": True,
                "capture_exception_boundary": True}
            stage = "publish"
            channel.command(["git", "pull", "--ff-only", "origin", BRANCH], cwd=ROOT)
            release = ROOT / "markertap/releases"
            release.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(output, release / OUTPUT_NAME)
            shutil.copyfile(private / "build.log", release / "build-v1.123.log")
            shutil.copyfile(private / "tests.log", release / "tests-v1.123.log")
            (release / "VALIDATION-v1.123.json").write_text(json.dumps(validation, ensure_ascii=False, indent=2) + "\n")
            (release / "apk-verification-v1.123.txt").write_text(verification + "\n" + badging.splitlines()[0] + "\n")
            status = ROOT / "markertap/BACKUP_STATUS.json"
            state = json.loads(status.read_text())
            state.update({"current_version_name": "1.123", "native_resolution_fix": "built_and_host_tested",
                "device_tested": False, "apk": "releases/" + OUTPUT_NAME,
                "apk_sha256": validation["apk_sha256"]})
            status.write_text(json.dumps(state, ensure_ascii=False, indent=2) + "\n")
            guide = ROOT / "markertap/code/TapMarker/GUIDE-v1.123.md"
            guide.write_text(guide.read_text().replace("実装済み。検証待ちです。", "ビルド・既存core回帰試験（描画コネクトーム試験含む）・従来版との署名証明書一致・APKの16KB境界配置を検証済みです。CPU/GPU負荷の実測は未実施です。結果はmarkertap/releasesに保存しています。"))
            work = ROOT / "markertap/WORK_NEXT.md"
            work.write_text(work.read_text().replace("ビルド・core回帰試験・同一署名の検証待ち。", "ビルド・core回帰試験・同一署名・16KB境界配置を検証済み。"))
            channel.command(["git", "config", "user.name", "github-actions[bot]"], cwd=ROOT)
            channel.command(["git", "config", "user.email", "41898282+github-actions[bot]@users.noreply.github.com"], cwd=ROOT)
            channel.command(["git", "add", "-f", "markertap/releases", "markertap/BACKUP_STATUS.json",
                "markertap/WORK_NEXT.md", "markertap/code/TapMarker/GUIDE-v1.123.md"], cwd=ROOT)
            channel.command(["git", "commit", "-m", "Publish verified MarkerTap v1.123 native-resolution APK"], cwd=ROOT)
            channel.command(["git", "push", "origin", "HEAD:refs/heads/" + BRANCH], cwd=ROOT)
            stage = "publish_download"
            target = channel.command(["git", "rev-parse", "HEAD"], cwd=ROOT).stdout.decode().strip()
            bundle = private / "MarkerTap-v1.123.zip"
            with zipfile.ZipFile(bundle, "w", zipfile.ZIP_DEFLATED) as z:
                z.write(output, OUTPUT_NAME)
            sums = private / "SHA256SUMS.txt"
            sums.write_text(channel.digest(output) + "  " + OUTPUT_NAME + "\n" +
                channel.digest(bundle) + "  " + bundle.name + "\n")
            notes = private / "RELEASE_NOTES.md"
            notes.write_text((ROOT / "markertap/code/TapMarker/GUIDE-v1.123.md").read_text())
            channel.command(["gh", "release", "create", "markertap-v1.123", str(output), str(bundle), str(sums),
                "--repo", channel.REPO, "--target", target, "--title", "MarkerTap v1.123 描画コネクトーム",
                "--notes-file", str(notes), "--prerelease", "--latest=false"], cwd=ROOT)
            # Check anonymous download bytes, including the APK inside the one-file ZIP.
            download_base = "https://github.com/" + channel.REPO + "/releases/download/markertap-v1.123/"
            for name in (OUTPUT_NAME, bundle.name):
                for attempt in range(6):
                    try:
                        request = channel.urllib.request.Request(download_base + name, headers={"User-Agent": "MarkerTap-download-check"})
                        with channel.urllib.request.urlopen(request, timeout=45) as response:
                            data = response.read()
                        break
                    except Exception:
                        if attempt == 5: raise
                        time.sleep(2)
                if name.endswith(".zip"):
                    with zipfile.ZipFile(channel.io.BytesIO(data)) as z:
                        if z.namelist() != [OUTPUT_NAME]: raise RuntimeError("unexpected download bundle")
                        data = z.read(OUTPUT_NAME)
                if hashlib.sha256(data).hexdigest() != validation["apk_sha256"]:
                    raise RuntimeError("downloaded APK integrity mismatch")
            channel.publish("result.json", {"run_id": channel.RUN_ID, "status": "complete",
                "download_verified": True, "apk_url": download_base + OUTPUT_NAME,
                "bundle_url": download_base + bundle.name, **validation})
            print("MarkerTap v1.123 build, tests and signing verification passed.")
        except Exception as error:
            publish_failure(private, stage, error)
            raise

if __name__ == "__main__":
    try:
        main()
    except Exception:
        print("Build stopped. Private signing material was not published; inspect the safe status files.", file=sys.stderr)
        sys.exit(1)
