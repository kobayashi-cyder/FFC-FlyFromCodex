#!/usr/bin/env python3
"""Fail closed if the Android 12 target or real gesture diagnostics are absent."""
from pathlib import Path
import sys
root=Path(sys.argv[1]).resolve()
manifest=(root/"AndroidManifest.xml").read_text(encoding="utf-8")
service=(root/"res/xml/service.xml").read_text(encoding="utf-8")
tap=(root/"src/jp/tapmarker/TapService.java").read_text(encoding="utf-8")
progress=(root/"src/jp/tapmarker/ProgressErrors.java").read_text(encoding="utf-8")
assert 'android:targetSdkVersion="31"' in manifest, "target SDK is not Android 12"
assert 'android:minSdkVersion="24"' in manifest, "minimum SDK unexpectedly changed"
assert 'android:canTakeScreenshot="true"' in service, "accessibility screenshot capability missing"
for component in ('jp.tapmarker.MainActivity','jp.tapmarker.TapService'):
    start=manifest.find('android:name="'+component+'"')
    assert start >= 0, "missing component "+component
    segment=manifest[start:manifest.find('/>',start)+2]
    assert 'android:exported="true"' in segment, "Android 12 exported declaration missing for "+component
for token in ('inputEventSequence','lastInputEvent','stage=request','stage=queued accepted=true','stage=rejected','stage=exception','stage=completed'):
    assert token in tap, "gesture trace missing: "+token
assert 'eventId==inputEventSequence' in tap, "late callbacks can overwrite newer event trace"
assert '入力イベント診断：' in progress, 'input trace heading missing from incident report'
assert 's.inputReport()' in progress, "physical incident report does not include input trace"
print("Android12SourceAudit: targetSdk=31, exported components, screenshot capability and gesture event trace verified")
