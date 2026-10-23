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
layout=(java/"AdaptiveLayout.java").read_text(encoding="utf-8")
assert 'if(usagi&&!located&&distance>homeTolerance)' in layout
assert 'matched!=null&&homeDistance(matched)<=homeTolerance' in layout
assert 'return matched;' in layout
print("v1.139 physical source audit: 12 F-52A routing, strict home-recognition and safety assertions passed")
