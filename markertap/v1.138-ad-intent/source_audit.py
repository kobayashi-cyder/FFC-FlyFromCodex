#!/usr/bin/env python3
"""Inspect the shipping DEX bytecode rewrite, not nonexistent AutoPilot.java."""
from pathlib import Path
import subprocess,sys
here=Path(__file__).resolve().parent
project=Path(sys.argv[1]).resolve()
subprocess.run([sys.executable,str(here.parent/"v1.137-connectome"/"source_audit.py"),str(project)],check=True)
java=project/"src/jp/tapmarker"
patch=(project/"tools/PatchRuntime.java").read_text(encoding="utf-8")
bridge=(java/"ExternalAdHandoffBridge.java").read_text(encoding="utf-8")
gate=(java/"ExternalAdHandoff.java").read_text(encoding="utf-8")
assert 'phaseCycleOriginal' in patch and 'Opcode.IF_EQZ' in patch
assert 'ExternalAdHandoffBridge;' in patch
assert 'physical phaseCycle(int) bridge missing' in patch
assert 'new ExternalAdHandoff()' in bridge
assert 'AdExitPolicy.adState(pilot.phases.engine.current)' in bridge
assert 'pilot.schedulePhase(expected,350)' in bridge
assert 'pilot.s.stopMarkers()' in bridge
assert 'MIN_EXTERNAL_DWELL_MS=1200' in gate
assert 'foreground.equals("com.android.systemui")' in gate
assert 'observations>=2' in gate
print("v1.138 audit: 10 physical DEX adapter / Intent handoff invariants passed")
