#!/usr/bin/env python3
"""Fail-closed source audit for the independent MarkerTap12 Android 12 profile."""
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

if len(sys.argv) != 2:
    raise SystemExit("Usage: marker_tap12_audit.py RESTORED_PROJECT")
project = Path(sys.argv[1])
manifest = project / "AndroidManifest.xml"
if not manifest.exists():
    raise SystemExit("FAIL: AndroidManifest.xml missing")
root = ET.parse(manifest).getroot()
android = "{http://schemas.android.com/apk/res/android}"
checks = []
checks.append(("package", root.get("package") == "jp.tapmarker12.lite"))
sdk = root.find("uses-sdk")
checks.append(("targetSdkVersion 31", sdk is not None and sdk.get(android + "targetSdkVersion") == "31"))
checks.append(("versionCode 1200", root.get(android + "versionCode") == "1200"))
checks.append(("versionName 12.0.0", root.get(android + "versionName") == "12.0.0"))
app = root.find("application")
checks.append(("application element", app is not None))
if app is not None:
    for node in list(app):
        if node.find("intent-filter") is not None:
            checks.append(("android:exported " + str(node.get(android + "name")),
                           node.get(android + "exported") in ("true", "false")))
service = project / "res/xml/service.xml"
checks.append(("Accessibility screenshot capability",
               service.exists() and 'android:canTakeScreenshot="true"' in service.read_text(encoding="utf-8")))
tap = project / "src/jp/tapmarker/TapService.java"
if tap.exists():
    content = tap.read_text(encoding="utf-8")
    for marker in ("inputEventSequence", "lastInputEvent", "stage=queued", "stage=completed",
                   "stage=rejected", "stage=cancelled", "stage=timeout", "stage=exception"):
        checks.append(("gesture diagnostic " + marker, marker in content))
else:
    checks.append(("TapService source exists", False))
progress = project / "src/jp/tapmarker/ProgressErrors.java"
checks.append(("incident report includes inputReport",
               progress.exists() and "s.inputReport()" in progress.read_text(encoding="utf-8")))
for label, passed in checks:
    print(("PASS" if passed else "FAIL") + ": " + label)
if not all(passed for _, passed in checks):
    raise SystemExit(1)
print("MarkerTap12 source audit passed; F-52A device behavior remains unverified.")
