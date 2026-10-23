#!/usr/bin/env python3
"""Check shipping-source integration rather than a reference-only patch."""
from pathlib import Path
import subprocess,sys
here=Path(__file__).resolve().parent
project=Path(sys.argv[1]).resolve()
subprocess.run([sys.executable,str(here.parent/"v1.135-hardening"/"source_audit.py"),str(project)],check=True)
java=project/"src/jp/tapmarker"
windows=(java/"VirtualWindows.java").read_text(encoding="utf-8")
gate=(java/"FrameFreshnessGate.java").read_text(encoding="utf-8")
watch=(java/"ProgressWatchdog.java").read_text(encoding="utf-8")
assert 'FrameFreshnessGate.shouldDecode(now,w.frameAt,w.decodeDueAt,w.analysisPending.get())' in windows
assert 'now-frameAt>=REFRESH_AGE_MS' in gate
assert 'return !analysisPending&&now>=frameAt' in gate
assert 'if(!w.analysisPending.compareAndSet(false,true)){s.h.postDelayed(()->{if(current.getAsBoolean()){requestFrameRefresh(w);failed.run();}},250);return;}' in windows
assert 'failure->{if(!current.getAsBoolean())return;w.analysisIssue=' in windows
assert 'w.analysisIssueAt=SystemClock.uptimeMillis();requestFrameRefresh(w);' in windows
assert '同じ状態への再入場を含む' in watch
assert '1500' in (java/"FrameCircuit.java").read_text(encoding="utf-8")
print("v1.136 source audit: 8 frame freshness and isolation invariants passed")
