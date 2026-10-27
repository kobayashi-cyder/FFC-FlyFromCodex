#!/usr/bin/env python3
"""Fail-closed source audit for the independent MarkerTap12 Android 12 profile."""
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

if len(sys.argv) != 2:
    raise SystemExit("Usage: marker_tap12_audit.py RESTORED_PROJECT")
project = Path(sys.argv[1])
packager = project / "tools/package_apk.py"
if not packager.exists():
    raise SystemExit("FAIL: tools/package_apk.py manifest generator missing")
generated = packager.read_text(encoding="utf-8")
checks = []
checks.append(("package generator identity", "jp.tapmarker12.lite" in generated))
checks.append(("versionCode generator 1200", "code=1200" in generated or "versionCode1200" in generated or "versionCode=1200" in generated))
checks.append(("versionName generator 12.0.0", "12.0.0" in generated))
checks.append(("target SDK manifest generator", "targetSdkVersion" in generated and "31" in generated))
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
