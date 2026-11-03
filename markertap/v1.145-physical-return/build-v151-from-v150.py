#!/usr/bin/env python3
"""Integrate v1.151 fixes into the public, verified 1.150 signed baseline.

This produces an UNSIGNED internal regression artifact, never an installable
or signed update. All original APK signing blocks become invalid when bytes
change; they are deliberately discarded by ZIP repack.
"""
from pathlib import Path
import argparse
import hashlib
import json
import os
import struct
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parent
BASE_SHA256 = "58e6cd830350473dad218352b3e23f4efcb214b2624e1122823ceb1093345067"
VERSION = "1.151"
CODE = 154

def run(cmd, logfile):
    logfile.parent.mkdir(parents=True, exist_ok=True)
    with logfile.open("w", encoding="utf-8") as out:
        subprocess.run([str(x) for x in cmd], stdout=out, stderr=subprocess.STDOUT, check=True)

def manifest(data):
    """Rewrite only the typed manifest code and the existing same-length version string."""
    data = bytearray(data)
    pos = 8
    strings = []
    locations = []
    changes = set()

    def length(at, wide):
        if wide:
            v = struct.unpack_from("<H", data, at)[0]
            at += 2
            if v & 0x8000:
                v = ((v & 0x7fff) << 16) | struct.unpack_from("<H", data, at)[0]
                at += 2
        else:
            v = data[at]
            at += 1
            if v & 0x80:
                v = ((v & 0x7f) << 8) | data[at]
                at += 1
        return v, at

    while pos < len(data):
        typ, header, size = struct.unpack_from("<HHI", data, pos)
        if size < header or pos + size > len(data):
            raise ValueError("invalid Android manifest chunk")
        if typ == 1:
            count, _, flags, start = struct.unpack_from("<4I", data, pos + 8)
            for i in range(count):
                at = pos + start + struct.unpack_from("<I", data, pos + header + 4 * i)[0]
                if flags & 256:
                    _, at = length(at, False)
                    n, at = length(at, False)
                    encoding = "utf-8"
                else:
                    n, at = length(at, True)
                    n *= 2
                    encoding = "utf-16le"
                strings.append(bytes(data[at:at + n]).decode(encoding))
                locations.append((at, n, encoding))
        elif typ == 0x102:
            node = struct.unpack_from("<I", data, pos + 20)[0]
            if strings[node] == "manifest":
                attr_start, step, count = struct.unpack_from("<3H", data, pos + 24)
                for i in range(count):
                    at = pos + 16 + attr_start + step * i
                    _, name, _ = struct.unpack_from("<3I", data, at)
                    if strings[name] == "versionCode":
                        current = struct.unpack_from("<I", data, at + 16)[0]
                        if current != 153:
                            raise ValueError("wrong baseline APK version code: " + str(current))
                        struct.pack_into("<I", data, at + 16, CODE)
                        changes.add("code")
                    elif strings[name] == "versionName":
                        idx = struct.unpack_from("<I", data, at + 16)[0]
                        if strings[idx] != "1.150":
                            raise ValueError("wrong baseline version name: " + strings[idx])
                        where, n, encoding = locations[idx]
                        replacement = VERSION.encode(encoding)
                        if len(replacement) != n:
                            raise ValueError("new version cannot fit manifest string")
                        data[where:where + n] = replacement
                        changes.add("name")
        pos += size
    if changes != {"code", "name"}:
        raise ValueError("manifest attributes not patched: " + repr(changes))
    return bytes(data)

def signature(name):
    upper = name.upper()
    return upper.startswith("META-INF/") and (
        upper.endswith((".SF", ".RSA", ".DSA", ".EC"))
        or upper == "META-INF/MANIFEST.MF"
    )

def sha256(path):
    digest = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()

def build(base, sdk, jadx):
    if sha256(base) != BASE_SHA256:
        raise ValueError("STOP: public signed 1.150 APK baseline digest mismatch")
    builddir = ROOT / "build" / "v151-incremental"
    evidence = builddir / "evidence"
    for name in ("helpers", "classes", "dex", "evidence"):
        (builddir / name).mkdir(parents=True, exist_ok=True)
    android = sdk / "platforms" / "android-35" / "android.jar"
    bt = sdk / "build-tools" / "35.0.0"
    if not android.is_file() or not jadx.is_file():
        raise FileNotFoundError("Pinned SDK35 and JADX toolchain required")

    patcher = ROOT / "tools" / "PatchVirtualLanesV151.java"
    run(["javac", "--release", "8", "-encoding", "UTF-8", "-cp", jadx,
         "-d", builddir / "helpers", patcher], evidence / "compile-patcher.log")
    sources = [ROOT / "src/jp/tapmarker" / (name + ".java") for name in (
        "VirtualFrameQueue", "VirtualPreviewVisibilityBridge", "VirtualCopyExecutorFactory"
    )]
    run(["javac", "--release", "8", "-encoding", "UTF-8", "-cp", android,
         "-d", builddir / "classes"] + sources, evidence / "compile-runtime.log")
    jar = builddir / "runtime.jar"
    with zipfile.ZipFile(jar, "w", zipfile.ZIP_DEFLATED) as z:
        for f in sorted((builddir / "classes").rglob("*.class")):
            z.write(f, f.relative_to(builddir / "classes"))
    run([bt / "d8", "--release", "--min-api", "24", "--lib", android,
         "--output", builddir / "dex", jar], evidence / "d8.log")

    helper = ["java", "-Xmx768m", "-cp",
              str(builddir / "helpers") + os.pathsep + str(jadx), "PatchVirtualLanesV151"]
    merged = builddir / "merged.dex"
    dex = builddir / "dex" / "classes.dex"
    run(helper + ["merge", base, dex, merged], evidence / "merge.log")
    run(helper + ["audit", base, dex, merged], evidence / "dex-preservation-audit.log")

    unaligned = builddir / "unaligned.apk"
    with zipfile.ZipFile(base) as old, zipfile.ZipFile(unaligned, "w") as new:
        if old.testzip() is not None:
            raise ValueError("baseline ZIP CRC failed")
        for info in old.infolist():
            if signature(info.filename):
                continue
            data = old.read(info)
            if info.filename == "classes.dex":
                data = merged.read_bytes()
            elif info.filename == "AndroidManifest.xml":
                data = manifest(data)
            new.writestr(info, data)
    with zipfile.ZipFile(base) as old, zipfile.ZipFile(unaligned) as new:
        preserved = 0
        for name in old.namelist():
            if name not in ("classes.dex", "AndroidManifest.xml") and not signature(name):
                if old.read(name) != new.read(name):
                    raise ValueError("unrelated APK resource changed: " + name)
                preserved += 1
        if set(new.namelist()) != {n for n in old.namelist() if not signature(n)}:
            raise ValueError("repack changed APK entries")

    outdir = ROOT / "output"
    outdir.mkdir(exist_ok=True)
    unsigned = outdir / "MarkerTap-v1.151-integrated-unsigned.apk"
    run([bt / "zipalign", "-P", "16", "-f", "4", unaligned, unsigned],
        evidence / "zipalign.log")
    run([bt / "zipalign", "-c", "-P", "16", "4", unsigned],
        evidence / "zipalign-check.log")
    run([bt / "aapt2", "dump", "badging", unsigned],
        evidence / "badging.log")
    badging = (evidence / "badging.log").read_text(encoding="utf-8")
    for value in ("name='jp.tapmarker.lite'", "versionCode='154'", "versionName='1.151'"):
        if value not in badging:
            raise ValueError("wrong APK identity: " + value)
    with zipfile.ZipFile(unsigned) as zipout:
        if zipout.testzip() is not None:
            raise ValueError("unsigned APK ZIP CRC failed")
    report = {
        "status": "unsigned_host_build_only_not_installable_or_device_tested",
        "base_version": "1.150",
        "base_sha256": BASE_SHA256,
        "base_release": "https://github.com/kobayashi-cyder/FFC-FlyFromCodex/releases/tag/markertap-v1.150",
        "new_version": VERSION,
        "new_version_code": CODE,
        "package": "jp.tapmarker.lite",
        "unsigned_sha256": sha256(unsigned),
        "unsigned_bytes": unsigned.stat().st_size,
        "preserved_resource_entries": preserved,
        "dex_audit": "passed",
        "signature": "NONE (APK v2/v3 MUST be re-signed by original private key)",
        "pixel10_device_tested": False,
        "safe_for_release": False,
    }
    (evidence / "result.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print("Unsigned integrated APK built + verified (NOT a signed update):", unsigned)
    print("SHA256=", report["unsigned_sha256"])
    return unsigned

if __name__ == "__main__":
    p = argparse.ArgumentParser()
    p.add_argument("--baseline", type=Path, required=True)
    p.add_argument("--sdk", type=Path, required=True)
    p.add_argument("--jadx", type=Path, required=True)
    args = p.parse_args()
    build(args.baseline.resolve(), args.sdk.resolve(), args.jadx.resolve())
