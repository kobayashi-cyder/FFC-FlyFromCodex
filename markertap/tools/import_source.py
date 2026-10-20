#!/usr/bin/env python3
"""One-run, RSA-OAEP protected import of the user's MarkerTap source backup."""
import base64, hashlib, io, json, os, pathlib, re, secrets, shutil, stat
import subprocess, sys, tempfile, time, urllib.error, urllib.parse, urllib.request, zipfile

REPO = os.environ["GITHUB_REPOSITORY"]
BRANCH = os.environ["GITHUB_REF_NAME"]
TOKEN = os.environ["GH_TOKEN"]
RUN_ID = os.environ["GITHUB_RUN_ID"]
ATTEMPT = os.environ.get("GITHUB_RUN_ATTEMPT", "1")
CHANNEL = "markertap/recovery-channel/"
ROOT = pathlib.Path(os.environ["GITHUB_WORKSPACE"])
EXPECTED_NAME = "MarkerTap-v1.117-display-recovery-source.zip"
EXPECTED_SIZE = 349706693

def api(path, body=None, method=None):
    raw = None if body is None else json.dumps(body).encode()
    request = urllib.request.Request("https://api.github.com/repos/" + REPO + "/" + path,
        data=raw, method=method, headers={
            "Authorization": "Bearer " + TOKEN,
            "Accept": "application/vnd.github+json", "Content-Type": "application/json",
            "User-Agent": "markertap-recovery", "Cache-Control": "no-cache"})
    with urllib.request.urlopen(request, timeout=45) as response:
        return json.load(response)

def read_channel(name):
    try:
        result = api("contents/" + CHANNEL + name + "?ref=" + urllib.parse.quote(BRANCH, safe=""))
        return json.loads(base64.b64decode(result["content"]))
    except urllib.error.HTTPError as error:
        if error.code == 404:
            return None
        raise

def publish(name, value):
    path = "contents/" + CHANNEL + name
    old = None
    try:
        old = api(path + "?ref=" + urllib.parse.quote(BRANCH, safe=""))
    except urllib.error.HTTPError as error:
        if error.code != 404:
            raise
    body = {"message": "Record MarkerTap recovery import status", "branch": BRANCH,
            "content": base64.b64encode((json.dumps(value, ensure_ascii=False, indent=2) + "\n").encode()).decode()}
    if old:
        body["sha"] = old["sha"]
    api(path, body, "PUT")

def command(args, **kwargs):
    return subprocess.run(args, check=True, stdout=subprocess.PIPE,
                          stderr=subprocess.PIPE, **kwargs)

def digest(path):
    h = hashlib.sha256()
    with open(path, "rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            h.update(block)
    return h.hexdigest()

def excluded(name):
    lowered = name.lower()
    parts = pathlib.PurePosixPath(lowered).parts
    return (".git" in parts or "signing" in parts or "__pycache__" in parts
        or any(p == ".env" or p.startswith(".env.") for p in parts)
        or lowered.endswith((".jks", ".keystore", ".p12", ".pfx", ".pem", ".key", ".pyc"))
        or pathlib.PurePosixPath(lowered).name in ("id_rsa", "id_ed25519", "local.properties")
        or ("signing" in lowered and "backup" in lowered and lowered.endswith(".zip")))

TEXT = {".java", ".kt", ".kts", ".xml", ".sh", ".py", ".c", ".h", ".cpp", ".hpp",
        ".gradle", ".properties", ".json", ".yaml", ".yml", ".md", ".txt", ".toml",
        ".pro", ".cmake", ".js", ".cjs", ".mjs", ".html", ".css", ".bat", ".ps1"}
TOKEN_PATTERN = re.compile(rb"(?:github_pat_[A-Za-z0-9_]{30,}|gh[pousr]_[A-Za-z0-9]{30,}|AKIA[0-9A-Z]{16}|sk-(?:proj-)?[A-Za-z0-9_-]{40,})")
PRIVATE_PATTERN = re.compile(rb"-----BEGIN (?:RSA |EC |OPENSSH |ENCRYPTED )?PRIVATE KEY-----\s+[A-Za-z0-9+/=\r\n]{64,}")

def validate_zip(archive):
    keep, removed, findings = [], [], []
    total = 0
    with zipfile.ZipFile(archive) as z:
        entries = z.infolist()
        if len(entries) > 100000:
            raise RuntimeError("archive member limit exceeded")
        for info in entries:
            path = pathlib.PurePosixPath(info.filename)
            if path.is_absolute() or ".." in path.parts or "\\" in info.filename:
                raise RuntimeError("unsafe archive path")
            if stat.S_ISLNK(info.external_attr >> 16):
                removed.append({"path": info.filename, "reason": "symbolic link"})
                continue
            if info.flag_bits & 1:
                raise RuntimeError("encrypted ZIP member")
            if excluded(info.filename):
                removed.append({"path": info.filename, "reason": "private material or local state"})
                continue
            total += info.file_size
            if total > 4 * 1024**3:
                raise RuntimeError("archive size limit exceeded")
            if not info.is_dir() and path.suffix.lower() in TEXT and info.file_size <= 8 * 1024**2:
                data = z.read(info)
                if TOKEN_PATTERN.search(data) or PRIVATE_PATTERN.search(data):
                    findings.append({"path": info.filename, "reason": "possible private credential"})
            if not info.is_dir() and path.suffix.lower() == ".zip" and info.file_size <= 100 * 1024**2:
                try:
                    nested = zipfile.ZipFile(io.BytesIO(z.read(info)))
                    if any(excluded(item.filename) for item in nested.infolist()):
                        removed.append({"path": info.filename, "reason": "nested private material"})
                        continue
                except zipfile.BadZipFile:
                    pass
            keep.append(info.filename)
    return keep, removed, findings, total

def import_archive(archive, private):
    keep, removed, findings, uncompressed = validate_zip(archive)
    if findings:
        publish("inspection.json", {"run_id": RUN_ID, "status": "blocked_private_material",
                "findings": findings, "source_not_published": True})
        raise RuntimeError("possible private credentials require review")
    original_sha = digest(archive)
    clean = archive
    if removed:
        clean = private / "MarkerTap-v1.117-source-without-private-material.zip"
        with zipfile.ZipFile(archive) as source, zipfile.ZipFile(clean, "w", zipfile.ZIP_DEFLATED, compresslevel=6) as target:
            for name in keep:
                with source.open(name) as reader, target.open(name, "w", force_zip64=True) as writer:
                    shutil.copyfileobj(reader, writer, 1024 * 1024)
    command(["git", "pull", "--ff-only", "origin", BRANCH], cwd=ROOT)
    dest = ROOT / "markertap"
    archive_dir = dest / "archive"
    archive_dir.mkdir(parents=True, exist_ok=True)
    for old in archive_dir.glob("source.zip.part*"):
        old.unlink()
    pieces = []
    with open(clean, "rb") as reader:
        index = 0
        while True:
            data = reader.read(85 * 1024**2)
            if not data:
                break
            path = archive_dir / ("source.zip.part%03d" % index)
            path.write_bytes(data)
            pieces.append({"name": path.name, "size_bytes": len(data), "sha256": hashlib.sha256(data).hexdigest()})
            index += 1
    mirror = dest / "code"
    if mirror.exists():
        shutil.rmtree(mirror)
    count = 0
    with zipfile.ZipFile(clean) as z:
        for info in z.infolist():
            path = pathlib.PurePosixPath(info.filename)
            if not info.is_dir() and path.suffix.lower() in TEXT and info.file_size <= 4 * 1024**2:
                target = mirror.joinpath(*path.parts)
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(z.read(info))
                count += 1
    manifest = {"format_version": 1, "application": "MarkerTap", "version_name": "1.117",
        "original_filename": EXPECTED_NAME, "original_size_bytes": archive.stat().st_size,
        "original_sha256": original_sha, "archive_sha256": digest(clean),
        "archive_size_bytes": clean.stat().st_size, "member_count": len(keep),
        "uncompressed_size_bytes": uncompressed, "parts": pieces, "excluded_members": removed,
        "code_mirror_files": count, "signing_key_included": False,
        "native_resolution_fix": "pending", "restoration_verified": False}
    manifest_path = archive_dir / "manifest.json"
    manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n")
    verify = private / "verified.zip"
    command([sys.executable, str(dest / "tools/restore_source.py"), "--output", str(verify)], cwd=ROOT)
    if digest(verify) != digest(clean):
        raise RuntimeError("restoration hash mismatch")
    with zipfile.ZipFile(verify) as z:
        if z.testzip() is not None:
            raise RuntimeError("ZIP CRC verification failed")
    manifest["restoration_verified"] = True
    manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n")
    (dest / "BACKUP_STATUS.json").write_text(json.dumps({
        "status": "source_backed_up", "version_name": "1.117",
        "archive_sha256": manifest["archive_sha256"], "restoration_verified": True,
        "signing_key_included": False, "native_resolution_fix": "pending",
        "source_parts": len(pieces), "code_mirror_files": count}, ensure_ascii=False, indent=2) + "\n")
    command(["git", "config", "user.name", "github-actions[bot]"], cwd=ROOT)
    command(["git", "config", "user.email", "41898282+github-actions[bot]@users.noreply.github.com"], cwd=ROOT)
    command(["git", "add", "-f", "markertap/archive", "markertap/code", "markertap/BACKUP_STATUS.json"], cwd=ROOT)
    command(["git", "commit", "-m", "Back up MarkerTap v1.117 source with verified restoration"], cwd=ROOT)
    command(["git", "push", "origin", "HEAD:refs/heads/" + BRANCH], cwd=ROOT)
    publish("result.json", {"run_id": RUN_ID, "status": "complete", "archive_sha256": manifest["archive_sha256"],
        "parts": len(pieces), "code_mirror_files": count, "restoration_verified": True,
        "signing_key_included": False, "excluded_members": removed})
    print("MarkerTap source backup and restoration verification completed.")

def main():
    if BRANCH != "markertap/recovery":
        raise RuntimeError("unexpected branch")
    with tempfile.TemporaryDirectory(prefix="markertap-private-", dir=os.environ["RUNNER_TEMP"]) as directory:
        private = pathlib.Path(directory)
        key = private / "import-key.pem"
        command(["openssl", "genpkey", "-algorithm", "RSA", "-pkeyopt", "rsa_keygen_bits:3072",
                 "-pkeyopt", "rsa_keygen_pubexp:65537", "-out", str(key)])
        key.chmod(0o600)
        modulus = command(["openssl", "rsa", "-in", str(key), "-noout", "-modulus"]).stdout.decode().strip().split("=", 1)[1]
        session = secrets.token_hex(16)
        publish("session.json", {"run_id": RUN_ID, "attempt": ATTEMPT, "session": session,
            "algorithm": "RSA-OAEP-SHA256", "modulus_hex": modulus, "exponent": 65537,
            "modulus_bytes": 384, "status": "awaiting_encrypted_source_reference"})
        deadline = time.monotonic() + 20 * 60
        while time.monotonic() < deadline:
            inbox = read_channel("inbox.json")
            if inbox and inbox.get("session") == session:
                break
            time.sleep(5)
        else:
            raise RuntimeError("source reference was not received")
        chunks = inbox["chunks"]
        if not 1 <= len(chunks) <= 1000:
            raise RuntimeError("invalid encrypted payload size")
        plaintext = bytearray()
        for index, item in enumerate(chunks):
            cipher = private / "chunk.rsa"
            cipher.write_bytes(base64.b64decode(item, validate=True))
            label = ("markertap-source-v1:" + session + ":" + str(index) + ":" + str(len(chunks))).encode()
            plaintext.extend(command(["openssl", "pkeyutl", "-decrypt", "-inkey", str(key),
                "-in", str(cipher), "-pkeyopt", "rsa_padding_mode:oaep",
                "-pkeyopt", "rsa_oaep_md:sha256", "-pkeyopt", "rsa_mgf1_md:sha256",
                "-pkeyopt", "rsa_oaep_label:" + label.hex()]).stdout)
        if hashlib.sha256(plaintext).hexdigest() != inbox["payload_sha256"]:
            raise RuntimeError("payload integrity check failed")
        payload = json.loads(plaintext)
        transfer = payload["transfer"]
        if transfer.get("file_name") != EXPECTED_NAME:
            raise RuntimeError("unexpected source version")
        helpers = private / "helpers"
        helpers.mkdir(mode=0o700)
        (helpers / "library_file_transfer.py").write_text(payload["transfer_helper"])
        (helpers / "library_hosted_apps.py").write_text(payload["hosted_apps_helper"])
        archive = private / EXPECTED_NAME
        command([sys.executable, str(helpers / "library_file_transfer.py"), "materialize", str(archive)],
                input=json.dumps(transfer).encode())
        if archive.stat().st_size != EXPECTED_SIZE:
            raise RuntimeError("unexpected source size")
        publish("inspection.json", {"run_id": RUN_ID, "status": "source_received_privately",
            "source_size_bytes": EXPECTED_SIZE, "source_sha256": digest(archive)})
        import_archive(archive, private)

if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        try:
            publish("result.json", {"run_id": RUN_ID, "status": "failed",
                "error_type": type(error).__name__, "source_published": False})
        except Exception:
            pass
        print("MarkerTap import stopped. Inspect the safe status files; transfer credentials were not logged.", file=sys.stderr)
        sys.exit(1)
