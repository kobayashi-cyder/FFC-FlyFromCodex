#!/usr/bin/env python3
"""Layer F-52A physical flow recovery on proven v1.138 DEX shipping bridge."""
from pathlib import Path
import shutil

def once(path, before, after):
 p=Path(path);src=p.read_text(encoding="utf-8")
 n=src.count(before)
 if n!=1: raise RuntimeError(f"{p}: anchor expected once, found {n}: {before[:100]}")
 p.write_text(src.replace(before,after),encoding="utf-8")

def apply(project):
 project=Path(project)
 here=Path(__file__).resolve().parent
 java=project/"src/jp/tapmarker"
 shutil.copyfile(here/"overlay/src/jp/tapmarker/PhysicalFlowRecovery.java",java/"PhysicalFlowRecovery.java")
 shutil.copyfile(here/"overlay/src/jp/tapmarker/PhysicalDiagnosticCapture.java",java/"PhysicalDiagnosticCapture.java")
 shutil.copyfile(here/"tests/PhysicalFlowRecoveryTest.java",project/"tests/PhysicalFlowRecoveryTest.java")
 bridge=java/"ExternalAdHandoffBridge.java"
 once(bridge,
  'private static final class Run {int token;final ExternalAdHandoff gate=new ExternalAdHandoff();}',
  'private static final class Run {int token;final ExternalAdHandoff gate=new ExternalAdHandoff();final PhysicalFlowRecovery physical=new PhysicalFlowRecovery();}')
 once(bridge,
  'if(pilot.target.equals(current)){run.gate.reset();return false;}',
  '''if(pilot.target.equals(current)){
   run.gate.reset();
   if("F-52A".equalsIgnoreCase(android.os.Build.MODEL)
      &&PhysicalFlowRecovery.GAME.equals(pilot.target)
      &&!pilot.phases.customized&&pilot.usagiPresetActive()
      &&pilot.phases.engine!=null){
    PhysicalFlowRecovery.Decision d=run.physical.observe(
      pilot.phases.engine.current,true,pilot.usagiGameConfirmed,
      pilot.actionPending,pilot.returning,pilot.phaseSampleTakenAt,
      SystemClock.uptimeMillis());
    if(d==PhysicalFlowRecovery.Decision.RECLASSIFY){
     // A transition to classify requests a new actual screenshot.
     // No speculative taps and no relaxing of the 1500ms frame gate.
     pilot.s.stopMarkers();pilot.phaseOwnedMarkers=false;
     pilot.enterUsagi("classify","F-52A: 異なる新鮮な画像を2枚確認 / 主画面未認識を再判別");
     pilot.schedulePhase(expected,150);return true;
    }
    if(d==PhysicalFlowRecovery.Decision.STOP_WITH_DIAGNOSTIC){
     pilot.s.stopMarkers();pilot.phaseOwnedMarkers=false;
     if(pilot.s.progressErrors!=null)
       pilot.s.progressErrors.fatal(pilot.target,pilot.phases.engine,
         "USAGI_HOME_UNRECOGNIZED",
         "F-52A: 新しい画像でも主画面を確認できません。3回の再判別後に安全停止");
     pilot.stopSingle(true);return true;
    }
   }else run.physical.reset();
   return false;
  }''')
 # Real-display incident ZIPs must await a bounded screenshot callback.
 # Virtual displays keep their established frame/incident flow unchanged.
 once(java/"ProgressErrors.java",
  'final String frozenBody=body;VirtualWindows.Window capture=s.pilot.virtualWindows.window(event.app);if(capture!=null)image(capture,"error");journal.incident(event.app,display,run,archive,frozenBody,now);\n  s.h.postDelayed(()->{if(journal.current(event.app,display,run))journal.incident(event.app,display,run,archive,frozenBody,now);},1000);',
  '''final String frozenBody=body;VirtualWindows.Window capture=s.pilot.virtualWindows.window(event.app);
  if(capture!=null){
   image(capture,"error");
   journal.incident(event.app,display,run,archive,frozenBody,now);
   s.h.postDelayed(()->{if(journal.current(event.app,display,run))journal.incident(event.app,display,run,archive,frozenBody,now);},1000);
  }else{
   PhysicalDiagnosticCapture.save(s,journal,event.app,run,archive,frozenBody,now);
  }''')
 once(project/"tools/package_apk.py",
  'def manifest_version(data, code=141, version="1.138"):',
  'def manifest_version(data, code=142, version="1.139"):')
 once(project/"tools/PatchRuntime.java",
  'if(text.equals("v1.123"))return "v1.138";',
  'if(text.equals("v1.123"))return "v1.139";')
 for name in ("build.sh","run-host-tests.sh"):
  p=project/name
  original=p.read_text(encoding="utf-8")
  if "1.138" not in original:raise RuntimeError(name+": unexpected v1.138 lineage")
  p.write_text(original.replace("1.138","1.139").replace("versionCode141","versionCode142"),encoding="utf-8")
 with (project/"run-tests.sh").open("a",encoding="utf-8") as f:
  f.write('\njava -cp "build/runtime:build/api:build/tests:baseline-runtime.jar:$TEST_LIBS:$JAR:$LIBS" jp.tapmarker.PhysicalFlowRecoveryTest\n')
 print("v1.139 F-52A physical recognition recovery integrated into DEX AutoPilot bridge")
if __name__=="__main__":
 import sys
 apply(Path(sys.argv[1]))
