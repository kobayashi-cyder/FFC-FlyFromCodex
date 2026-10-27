#!/usr/bin/env python3
"""Fail-closed source audit for the independent MarkerTap12 Android 12 profile."""
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

if len(sys.argv) != 2:
    raise SystemExit("Usage: marker_tap12_audit.py RESTORED_PROJECT")
project = Path(sys.argv[1])
text_sources = []
for item in project.rglob("*"):
    if not item.is_file():
        continue
    try:
        text = item.read_text(encoding="utf-8")
    except (UnicodeDecodeError, OSError):
        continue
    text_sources.append((str(item.relative_to(project)), text))
all_text = "\\n".join(body for _, body in text_sources)
checks = []
checks.append(("package ID in restored sources", "jp.tapmarker12.lite" in all_text))
checks.append(("versionCode 1200 in restored sources", "1200" in all_text))
checks.append(("versionName 12.00 in restored sources", "12.00" in all_text))
tap = project / "src/jp/tapmarker/TapService.java"
if tap.exists():
    content = tap.read_text(encoding="utf-8")
    for marker in ("inputEventSequence", "lastInputEvent", "stage=queued", "stage=completed",
                   "stage=rejected", "cancelled", "timeout", "stage=exception"):
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
