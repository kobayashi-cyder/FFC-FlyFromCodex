#!/usr/bin/env python3
"""Ensure the real shipping Java sources contain the event graph and its call sites."""
from pathlib import Path
import subprocess,sys
here=Path(__file__).resolve().parent
project=Path(sys.argv[1]).resolve()
subprocess.run([sys.executable,str(here.parent/"v1.136-frameguard"/"source_audit.py"),str(project)],check=True)
java=project/"src/jp/tapmarker"
runner=(java/"SplitPilot.java").read_text(encoding="utf-8")
graph=(java/"ConnectomeMultiplexer.java").read_text(encoding="utf-8")
assert 'new ConnectomeMultiplexer()' in runner
assert 'virtualConnectome.start(connectomeKeys)' in runner
assert 'virtualConnectome.reset()' in runner
assert 'virtualConnectome.offer(slot.pkg,w.id,w.frameAt,slot.lastFrameAt' in runner
assert 'virtualConnectome.published(slot.pkg,capturedAt)' in runner
assert 'requestFrameRefresh(w)' in runner
assert 'MAX_LANES=2' in graph
assert 'MAX_FRAME_AGE_MS=1500' in graph
assert 'capturedAt<=consumedAt||capturedAt<=lane.admittedFrame' in graph
assert 'new Decision(false,true,Math.min(350,125+lane.quiet*25L))' in graph
print("v1.137 source audit: 10 connectome wiring and isolation invariants passed")
