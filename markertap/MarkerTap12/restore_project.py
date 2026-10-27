#!/usr/bin/env python3
"""Restore the reviewed MarkerTap source, then apply the independent MarkerTap12 profile."""
from pathlib import Path
import re, subprocess, sys

HERE = Path(__file__).resolve().parent
REPO_MARKERTAP = HERE.parent

def apply_profile(project):
    manifest = project / "AndroidManifest.xml"
    if not manifest.exists():
        raise RuntimeError("restored AndroidManifest.xml not found")
    text = manifest.read_text(encoding="utf-8")
    text = text.replace('package="jp.tapmarker.lite"', 'package="jp.tapmarker12.lite"')
    text = text.replace('android:targetSdkVersion="35"', 'android:targetSdkVersion="31"')
    text = re.sub(r'android:versionCode="\d+"', 'android:versionCode="1200"', text, count=1)
    text = re.sub(r'android:versionName="[^"]+"', 'android:versionName="12.0.0"', text, count=1)
    if 'package="jp.tapmarker12.lite"' not in text or 'android:targetSdkVersion="31"' not in text or 'android:versionCode="1200"' not in text or 'android:versionName="12.0.0"' not in text:
        raise RuntimeError("MarkerTap12 manifest profile did not apply cleanly")
    manifest.write_text(text, encoding="utf-8")

    # Keep package identity and release metadata consistent throughout the generated project.
    # Only UTF-8 text files are changed; binary assets are untouched.
    for path in project.rglob("*"):
        if not path.is_file() or path == manifest:
            continue
        try:
            data = path.read_text(encoding="utf-8")
        except (UnicodeDecodeError, OSError):
            continue
        updated = data.replace("jp.tapmarker.lite", "jp.tapmarker12.lite")
        updated = re.sub(r'(def manifest_version\(data, code=)\d+(, version=")[^"]+("\):)', r'\g<1>1200\g<2>12.0.0\g<3>', updated)
        updated = re.sub(r'versionCode\s*=?\s*\d+', 'versionCode1200', updated)
        updated = re.sub(r"(['\"]version_code['\"]\s*:\s*)\d+", r'\g<1>1200', updated)
        updated = updated.replace('versionName="1.143"', 'versionName="12.0.0"').replace('versionName="1.144"', 'versionName="12.0.0"')
        updated = updated.replace('return "v1.143"', 'return "v12.0.0"').replace('return "v1.144"', 'return "v12.0.0"')
        updated = updated.replace("MarkerTap-v1.143", "MarkerTap12-v12.0.0").replace("MarkerTap-v1.144", "MarkerTap12-v12.0.0")
        if updated != data:
            path.write_text(updated, encoding="utf-8")

    manifest_text = manifest.read_text(encoding="utf-8")
    if 'package="jp.tapmarker.lite"' in manifest_text or 'android:targetSdkVersion="35"' in manifest_text:
        raise RuntimeError("Legacy application identity or target SDK remains in manifest")
    print("MarkerTap12 profile applied: package=jp.tapmarker12.lite, targetSdk=31, version=12.0.0 (1200)")

def main():
    if len(sys.argv) != 2:
        raise SystemExit("Usage: restore_project.py DESTINATION")
    project = Path(sys.argv[1]).resolve()
    source = REPO_MARKERTAP / "v1.143-kaitaku-actions" / "restore_project.py"
    subprocess.run([sys.executable, str(source), str(project)], check=True)
    apply_profile(project)

if __name__ == "__main__":
    main()
