#!/usr/bin/env python3
"""v1.136: isolate virtual-frame freshness/retry without altering physical input."""
from pathlib import Path
import shutil

def once(path,before,after):
 path=Path(path);source=path.read_text(encoding="utf-8")
 count=source.count(before)
 if count!=1:raise RuntimeError(f"{path}: verified source anchor count {count}, expected 1: {before[:100]}")
 path.write_text(source.replace(before,after),encoding="utf-8")

def apply(project):
 project=Path(project)
 here=Path(__file__).resolve().parent
 java=project/"src/jp/tapmarker"
 window=java/"VirtualWindows.java"
 shutil.copyfile(here/"overlay/src/jp/tapmarker/FrameFreshnessGate.java",java/"FrameFreshnessGate.java")

 # Normal decode cadence is preserved while recognition owns the lane.
 # If no worker is active and the last decoded frame approaches expiry,
 # decode the new ImageReader image rather than waiting until the long cadence.
 once(window,
  'if(w.frameAt>0&&now<w.decodeDueAt)return;w.decodedAt=now;',
  'if(!FrameFreshnessGate.shouldDecode(now,w.frameAt,w.decodeDueAt,w.analysisPending.get()))return;w.decodedAt=now;')

 # An in-flight analysis isn't a failed capture. Delay retry rather than
 # escalating the recovery state every 120ms. Preserve target-display checks.
 once(window,
  'if(!w.analysisPending.compareAndSet(false,true)){s.h.postDelayed(()->{if(current.getAsBoolean())failed.run();},120);return;}',
  'if(!w.analysisPending.compareAndSet(false,true)){s.h.postDelayed(()->{if(current.getAsBoolean()){requestFrameRefresh(w);failed.run();}},250);return;}')

 # Asynchronous failures are reported only for the still-current display run;
 # request a new frame and leave the previous invalid result unpublished.
 once(window,
  'failure->{if(current.getAsBoolean()){w.analysisIssue="解析失敗："+(failure==null?"reason-unavailable":brief(failure));w.analysisIssueAt=SystemClock.uptimeMillis();}if(failure instanceof OutOfMemoryError)s.performance.memoryPressure();failed.run();}',
  'failure->{if(!current.getAsBoolean())return;w.analysisIssue="解析失敗："+(failure==null?"reason-unavailable":brief(failure));w.analysisIssueAt=SystemClock.uptimeMillis();requestFrameRefresh(w);if(failure instanceof OutOfMemoryError)s.performance.memoryPressure();failed.run();}')

 # A monitor's state age can span same-state re-entry. Say "unverified
 # progress" instead of incorrectly claiming one continuous phase entry.
 once(java/"ProgressWatchdog.java",
  'reason="設定された待機時間と猶予を超えて同じ状態に留まっています";',
  'reason="監視上の進捗が待機時間と猶予を超えて確認できません（同じ状態への再入場を含む）";')

 shutil.copyfile(here/"tests/VirtualFreshnessGateTest.java",project/"tests/VirtualFreshnessGateTest.java")
 with (project/"run-tests.sh").open("a",encoding="utf-8") as fp:
  fp.write('\njava -cp "build/runtime:build/api:build/tests:baseline-runtime.jar:$TEST_LIBS:$JAR:$LIBS" jp.tapmarker.VirtualFreshnessGateTest\n')
 once(project/"tools/package_apk.py",
  'def manifest_version(data, code=138, version="1.135"):',
  'def manifest_version(data, code=139, version="1.136"):')
 once(project/"tools/PatchRuntime.java",
  'if(text.equals("v1.123"))return "v1.135";',
  'if(text.equals("v1.123"))return "v1.136";')
 for name in ("build.sh","run-host-tests.sh"):
  p=project/name;src=p.read_text(encoding="utf-8")
  if "1.135" not in src:raise RuntimeError(f"{name}: unexpected version lineage")
  p.write_text(src.replace("1.135","1.136").replace("versionCode138","versionCode139"),encoding="utf-8")
 print("MarkerTap v1.136 virtual-frame guard applied")
if __name__=="__main__":
 import sys
 apply(Path(sys.argv[1]))
