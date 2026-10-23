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
assert 'w.analysisIssueAt=SystemClock.uptimeMillis();if(failure instanceof OutOfMemoryError)s.performance.memoryPressure();else requestFrameRefresh(w);' in windows
assert '同じ状態への再入場を含む' in watch
# The inherited v1.134 FrameCircuit delegates its 1500ms validity to
# AnalysisBudget.fresh; it intentionally has no inline numeric literal.
frame=(java/"FrameCircuit.java").read_text(encoding="utf-8")
assert 'AnalysisBudget.fresh(' in frame
assert 'frame freshness deadline exceeded' in frame
print("v1.136 source audit: 8 frame freshness and isolation invariants passed")
