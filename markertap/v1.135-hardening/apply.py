#!/usr/bin/env python3
"""MarkerTap 1.135: fail-closed, anchor-checked fixes atop reconstructed 1.134.

Do not patch only code/TapMarker: the shipping runtime is reconstructed from the
verified archived foundation and layered upgrades.
"""
from pathlib import Path

def once(path, before, after):
    path = Path(path)
    src = path.read_text(encoding="utf-8")
    count = src.count(before)
    if count != 1:
        raise RuntimeError(f"{path}: expected one verified anchor, found {count}: {before[:140]}")
    path.write_text(src.replace(before, after), encoding="utf-8")

def apply(project):
    project = Path(project)
    java = project / "src/jp/tapmarker"

    # The recovery circuit belongs to the actual display, not the short-lived
    # SplitPilot.Slot. A manual stop/restart must NOT mint another rebuild.
    p = java / "VirtualWindows.java"
    # When resource retirement is busy, still acquire and close the next
    # ImageReader image. Returning before acquireLatestImage risks backlogging
    # buffers until no more callbacks or fresh frames arrive.
    once(p,
         'if(reader!=w.reader||w.closed||task!=generation||pendingFrameRetirements.get()>=8)return;',
         'if(reader!=w.reader||w.closed||task!=generation)return;')
    once(p,
         'image=reader.acquireLatestImage();if(image==null||reader!=w.reader||w.closed||task!=generation)return;',
         'image=reader.acquireLatestImage();if(image==null||reader!=w.reader||w.closed||task!=generation)return;if(pendingFrameRetirements.get()>=8)return;')
    once(p, "final RestoreReceipt restoreReceipt=new RestoreReceipt();",
         "final RestoreReceipt restoreReceipt=new RestoreReceipt();"
         "final DisplayRecovery recoveryGuard=new DisplayRecovery();")
    once(p, 'volatile String frameIssue="";',
         'volatile String frameIssue="",analysisIssue="";volatile long analysisIssueAt;')
    once(p,
         'return result==null?null:()->{w.roiCopiedPixels=copied[0];',
         'return result==null?null:()->{w.analysisIssue="";w.roiCopiedPixels=copied[0];')
    once(p,
         'failure->{if(failure instanceof OutOfMemoryError)s.performance.memoryPressure();failed.run();}',
         'failure->{if(current.getAsBoolean()){w.analysisIssue="解析失敗："+(failure==null?"reason-unavailable":brief(failure));w.analysisIssueAt=SystemClock.uptimeMillis();}if(failure instanceof OutOfMemoryError)s.performance.memoryPressure();failed.run();}')

    p = java / "SplitPilot.java"
    once(p, "final DisplayRecovery displayRecovery=new DisplayRecovery();",
         "DisplayRecovery displayRecovery=new DisplayRecovery();")
    once(p, "Slot slot=new Slot(s,pkg);slot.phases.runtimeOnly=true;",
         "Slot slot=new Slot(s,pkg);slot.displayRecovery=w.recoveryGuard;slot.phases.runtimeOnly=true;")
    # No change to display/gesture ownership. Old callbacks remain guarded by
    # run token and display generation; old rebuilding tickets are not reset.
    # Never confuse a blank image with an absent display.

    # Capture processing can fail without a Throwable (COPY with null input);
    # classify it instead of silently retrying with no evidence.
    p = java / "FrameCircuit.java"
    once(p,
         'input=request.source.copy(workspace);signal=input!=null;',
         'input=request.source.copy(workspace);signal=input!=null;if(!signal)failure=new IllegalStateException("capture source provided no frame");')

    # Distinguish display loss from stale frames and slow analysis.
    p = java / "ProgressWatchdog.java"
    once(p, 'boolean running=true,terminal,home,expectFrames=true,expectInput;',
         'boolean running=true,terminal,home,expectFrames=true,expectInput,displayMissing;')
    once(p, 'if(s.expectFrames&&(s.frameAge<0||s.frameAge>1500)){',
         'if(s.displayMissing){code="DISPLAY_MISSING";reason="自律対象の仮想画面が存在しません";grace=5000;}\n  else if(s.expectFrames&&(s.frameAge<0||s.frameAge>1500)){')

    p = java / "ProgressErrors.java"
    once(p, 'sample.key=app+":"+(w==null?0:w.id);',
         'sample.key=app+":"+(pilotVirtual(w)?"virtual":Integer.toString(w==null?0:w.id));sample.displayMissing=w==null&&s.pilot.splitPilot.active&&s.pilot.splitPilot.virtualMode;')
    # Stable virtual key prevents a new display id from erasing the episode.
    once(p, 'static long age(long at,long now){',
         'boolean pilotVirtual(VirtualWindows.Window w){return s.pilot!=null&&s.pilot.splitPilot.active&&s.pilot.splitPilot.virtualMode;}\n static long age(long at,long now){')
    once(p,
         'if(w!=null)b.append("\\n復帰要求と確認：").append(w.restoreReceipt.report());',
         'if(w!=null)b.append("\\n復帰要求と確認：").append(w.restoreReceipt.report()).append("\\n画像解析の最終失敗：").append(w.analysisIssue.isEmpty()?"記録なし":w.analysisIssue).append(" / ").append(age(w.analysisIssueAt,now)).append("ms前");')

    # A display being deliberately rebuilt has no frame until its new
    # ImageReader starts. This is not the same as an unexpected frame stall.
    once(p, 'live.add(sample.key);ProgressWatchdog.Event event=watchdog.observe(sample,now);',
         'if(w!=null&&w.rebuilding)sample.expectFrames=false;live.add(sample.key);ProgressWatchdog.Event event=watchdog.observe(sample,now);')

    # Archive the previous display's bounded frames, retaining their original
    # JSON metadata (source display id). Do not use these as live observations.
    p = java / "DiagnosticJournal.java"
    once(p,
         'lanes.put(app,lane);if(lanes.size()>6)',
         'if(lanes.containsKey(app)){Lane previous=lanes.get(app);long now=SystemClock.uptimeMillis();for(DiagnosticArchive.Frame saved:previous.frames)if(saved.at>0&&now>=saved.at&&now-saved.at<=60000)lane.frames.addLast(saved);while(lane.frames.size()>DiagnosticArchive.MAX_FRAMES)lane.frames.removeFirst();}lanes.put(app,lane);if(lanes.size()>6)')

    # Fail closed: source API/runtime may vary. Every source anchor must be
    # confirmed, no broad blind replacements or rewritten detector thresholds.
    t = project / "run-tests.sh"
    with t.open("a", encoding="utf-8") as fp:
        fp.write("\nfor test in FrameFailureReason RecoveryGuardReuse DisplayMissing; do\n"
                 ' java -cp "build/runtime:build/api:build/tests:baseline-runtime.jar:$TEST_LIBS:$JAR:$LIBS" jp.tapmarker.${test}Test\n'
                 "done\n")
    import shutil
    tests = Path(__file__).resolve().parent / "tests"
    for name in ["FrameFailureReasonTest.java", "RecoveryGuardReuseTest.java", "DisplayMissingTest.java"]:
        shutil.copyfile(tests / name, project / "tests" / name)

    # No display resolution, DPI, ROI recognition threshold or keyboard/input
    # injection changes. Increment identity without changing signing key.
    once(project / "tools/package_apk.py",
         'def manifest_version(data, code=137, version="1.134"):',
         'def manifest_version(data, code=138, version="1.135"):')
    once(project / "tools/PatchRuntime.java",
         'if(text.equals("v1.123"))return "v1.134";',
         'if(text.equals("v1.123"))return "v1.135";')
    for name in ["build.sh", "run-host-tests.sh"]:
        p=project/name
        raw=p.read_text(encoding="utf-8")
        if "1.134" not in raw:raise RuntimeError(name+": source lineage missing")
        p.write_text(raw.replace("1.134","1.135").replace("versionCode137","versionCode138"),encoding="utf-8")
    print("MarkerTap v1.135 hardening: inspected all anchors and applied fixes")

if __name__ == "__main__":
    import sys
    apply(Path(sys.argv[1]))
