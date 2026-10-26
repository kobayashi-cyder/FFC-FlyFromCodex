#!/usr/bin/env python3
from pathlib import Path
import subprocess
import sys

here = Path(__file__).resolve().parent
project = Path(sys.argv[1]).resolve()
subprocess.run([sys.executable, str(here.parent / "v1.140-recovery-progress/source_audit.py"), str(project)], check=True)
java = project / "src/jp/tapmarker"
patcher = (project / "tools/PatchRuntime.java").read_text()
capture = (java / "PhysicalFrameHealth.java").read_text()
viewport = (java / "ContentViewport.java").read_text()
watchdog = (java / "ProgressWatchdog.java").read_text()
errors = (java / "ProgressErrors.java").read_text()
assert '"Ljp/tapmarker/AutoPilot$1;")?captureClock(c)' in patcher
assert '"onSuccessOriginal"' in patcher and '"val$expected"' in patcher
assert 'p.token!=expected' in capture and '!p.active' in capture
assert 'r.token==p.token&&r.app.equals(p.target)' in capture
assert '.close()' not in capture and '.recycle()' not in capture
assert 'l=Math.max(0,window[0])' in viewport and 'r=Math.min(w,window[2])' in viewport
assert 'if(r<=l||b<=t)throw invalid(w,h,window)' in viewport
assert 'window.length!=4' in viewport and 'window[2]<=window[0]' in viewport
assert 'image=' in viewport and 'window=' in viewport
assert 'FarmRewardReference.addTo(list)' in (java / "FarmDialogEvidence.java").read_text()
popup = (java / "GamePopupEvidence.java").read_text()
assert 'farmReward.locate(id,w,h,pixels,0,found,false)' in popup
assert 'insideWindow(button,v,w,h)&&insideWindow(heading,v,w,h)' in popup
# Preserve the original close=.008 / start=.016 thresholds.
assert 'limit=id.endsWith("start")?.016f:.008f' in (java / "FarmDialogEvidence.java").read_text()
assert 'PhysicalFrameHealth.capturedAt(s.pilot)' in errors
assert '!s.pilot.splitPilot.active&&!e.windowRelative' in errors
assert 'ANALYSIS_UNAVAILABLE' in watchdog and 's.analysisAge>IMAGE_GRACE_MS' in watchdog
assert 'FRESH_MS=1500' in (java / "ReturnProgressGate.java").read_text()
print("v1.141 source audit: callback ownership, geometry, paired reward and separate clocks verified")
