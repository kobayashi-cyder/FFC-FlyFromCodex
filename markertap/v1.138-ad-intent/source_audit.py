#!/usr/bin/env python3
"""Fail build if the ad handoff was not applied to shipping Android sources."""
from pathlib import Path
import subprocess,sys
here=Path(__file__).resolve().parent
project=Path(sys.argv[1]).resolve()
subprocess.run([sys.executable,str(here.parent/"v1.137-connectome"/"source_audit.py"),str(project)],check=True)
java=project/"src/jp/tapmarker"
pilot=(java/"AutoPilot.java").read_text(encoding="utf-8")
gate=(java/"ExternalAdHandoff.java").read_text(encoding="utf-8")
assert "new ExternalAdHandoff()" in pilot
assert "externalAdHandoff.observe(target,packageNow(),AdExitPolicy.adState(phases.engine.current)" in pilot
assert "handoff==ExternalAdHandoff.Decision.WAIT" in pilot
assert "externalAdHandoff.reset();PhaseEngine.Phase p=" in pilot
assert "target=packageNow();externalAdHandoff.reset();" in pilot
assert "getLaunchIntentForPackage(target)" in pilot
assert "s.startActivity(launch)" in pilot
assert "MIN_EXTERNAL_DWELL_MS=1200" in gate
assert "foreground.equals(\"com.android.systemui\")" in gate
assert "observations>=2" in gate
print("v1.138 source audit: 10 physical handoff / Intent invariants passed")
