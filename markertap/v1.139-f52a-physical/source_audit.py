#!/usr/bin/env python3
"""Hard guard: real binary shipping bridge must contain F-52A admission route."""
from pathlib import Path
import subprocess,sys
here=Path(__file__).resolve().parent
project=Path(sys.argv[1]).resolve()
subprocess.run([sys.executable,str(here.parent/"v1.138-ad-intent"/"source_audit.py"),str(project)],check=True)
java=project/"src/jp/tapmarker"
bridge=(java/"ExternalAdHandoffBridge.java").read_text(encoding="utf-8")
flow=(java/"PhysicalFlowRecovery.java").read_text(encoding="utf-8")
assert '"F-52A".equalsIgnoreCase(android.os.Build.MODEL)' in bridge
assert 'pilot.enterUsagi("classify"' in bridge
assert 'PhysicalFlowRecovery.Decision.STOP_WITH_DIAGNOSTIC' in bridge
assert 'pilot.s.progressErrors.fatal(' in bridge
assert 'new PhysicalFlowRecovery()' in bridge
assert 'pilot.phaseSampleTakenAt' in bridge
assert 'frameAt<=0||now<frameAt||now-frameAt>=FRESH_MS' in flow
assert 'distinct<2||now-firstAt<PERSIST_MS' in flow
assert 'if(++attempts>3)' in flow
# Physical failures must produce an image-bearing ZIP whenever Android permits.
# The real-display screenshot is an on-demand AccessibilityService capture;
# virtual display image handling must remain unchanged.
errors=(java/"ProgressErrors.java").read_text(encoding="utf-8")
capture=(java/"PhysicalDiagnosticCapture.java").read_text(encoding="utf-8")
service_xml=(project/"res/xml/service.xml").read_text(encoding="utf-8")
assert 'PhysicalDiagnosticCapture.save(s,journal,event.app,run,archive,frozenBody,now)' in errors
assert 'if(capture!=null)' in errors and 'image(capture,"error")' in errors
assert 'android:canTakeScreenshot="true"' in service_xml
assert 's.takeScreenshot(Display.DEFAULT_DISPLAY,s.getMainExecutor()' in capture
assert 'app.equals(s.pilot.packageNow())' in capture
assert 'new ArrayBlockingQueue<Runnable>(2)' in capture
assert 'new AtomicBoolean()' in capture and 'TIMEOUT_MS=4500' in capture
assert 'BitmapPixels.of(software)' in capture
assert '"physical-error"' in capture
assert 'journal.frame(app,0,run' in capture
assert 'journal.incident(app,0,run,archive' in capture
assert 'buffer.close()' in capture
assert '"\\n実画面の画像取得：' in capture
print("v1.139 physical source audit: 9 recovery + 13 physical ZIP safety assertions passed")
