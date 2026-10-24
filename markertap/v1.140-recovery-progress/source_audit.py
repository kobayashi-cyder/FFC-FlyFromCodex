#!/usr/bin/env python3
from pathlib import Path
import subprocess
import sys

here = Path(__file__).resolve().parent
project = Path(sys.argv[1]).resolve()
subprocess.run([sys.executable, str(here.parent / "v1.139-f52a-physical/source_audit.py"), str(project)], check=True)
java = project / "src/jp/tapmarker"
patcher = (project / "tools/PatchRuntime.java").read_text()
bridge = (java / "ReturnProgressBridge.java").read_text()
gate = (java / "ReturnProgressGate.java").read_text()
errors = (java / "ProgressErrors.java").read_text()
journal = (java / "DiagnosticJournal.java").read_text()
assert '"verifyRegisteredReturnOriginal"' in patcher
assert '"Ljp/tapmarker/ReturnProgressBridge;","intercept"' in patcher
assert 'sawReturnPreflight' in patcher
assert 'ReturnProgressBridge.monitor(pilot,expected)' in (java / "ExternalAdHandoffBridge.java").read_text()
assert 'if(!p.validReturn(expected,request))return' in bridge
assert 'version!=p.s.inputTargetVersion' in bridge
assert 'p.launchRegisteredReturn(expected,request,done,viaHome)' in bridge
assert 'if(!supports(p))return false' in bridge
assert '!(w.distance(samples.get(w.regionId))<=w.threshold)' in bridge
assert 'FRESH_MS=1500' in gate and 'if(distinct>=2)' in gate
assert 'MAX_CANCELLATIONS=3' in gate and 'EPISODE_LIMIT_MS=90000' in gate
assert 'frameAt<=homeAt' in gate
assert 's.pilot.splitPilot.active?s.pilot.splitPilot.token:s.pilot.token' in errors
assert 'journal.observed(event.app,0,run' in errors
assert 'String observation=frozenObservation' in journal
assert 'if(display==0)frames.removeIf(f->f.at<detectedAt)' in journal
assert '.put("mode",display==0?"physical":"virtual")' in journal
assert '.put("capture_status"' in journal
assert 'journal.captureStatus(app,0,run' in (java / "PhysicalDiagnosticCapture.java").read_text()
print("v1.140 source audit: return/capture invariants passed")
