#!/usr/bin/env python3
"""Structural integration checks on the *restored shipping sources*."""
from pathlib import Path
import sys
root=Path(sys.argv[1]).resolve()/"src/jp/tapmarker"
def source(name):return (root/name).read_text(encoding="utf-8")
windows=source("VirtualWindows.java")
split=source("SplitPilot.java")
watch=source("ProgressWatchdog.java")
report=source("ProgressErrors.java")
journal=source("DiagnosticJournal.java")
frame=source("FrameCircuit.java")
assert "final DisplayRecovery recoveryGuard=new DisplayRecovery();" in windows
assert "slot.displayRecovery=w.recoveryGuard" in split
assert "final DisplayRecovery displayRecovery=new DisplayRecovery();" not in split
assert 'code="DISPLAY_MISSING"' in watch
assert "sample.displayMissing=w==null" in report
assert "if(!signal)failure=new IllegalStateException" in frame
assert "w.analysisIssueAt=SystemClock.uptimeMillis()" in windows
assert "now-saved.at<=60000" in journal
# Acquiring and closing the buffer is mandatory even while asynchronous
# retirement is backpressured. Never let ImageReader stop draining.
a=windows.index("void frame(Window w,ImageReader reader,int task)")
b=windows.index("boolean frameCaptureAllowed()",a)
body=windows[a:b]
assert body.index("image=reader.acquireLatestImage()") < body.index("if(pendingFrameRetirements.get()>=8)return")
assert "finally{if(image!=null)image.close();}" in body
# Keep existing safeguards against accidental default-display input.
assert "if(w==null||w.id<=0||w.closed||!valid.getAsBoolean()||!checkInput(w))" in windows
print("v1.135 restored-source audit: 11 invariants passed")
