#!/usr/bin/env python3
"""MarkerTap 1.138: source-verified physical ad destination -> Intent return."""
from pathlib import Path
import shutil

def once(path,before,after):
 path=Path(path);source=path.read_text(encoding="utf-8")
 if source.count(before)!=1:raise RuntimeError(str(path)+": source anchor expected once, got "+str(source.count(before))+": "+before[:100])
 path.write_text(source.replace(before,after),encoding="utf-8")

def apply(project):
 project=Path(project);here=Path(__file__).resolve().parent
 java=project/"src/jp/tapmarker"
 shutil.copyfile(here/"overlay/src/jp/tapmarker/ExternalAdHandoff.java",java/"ExternalAdHandoff.java")
 shutil.copyfile(here/"tests/ExternalAdHandoffTest.java",project/"tests/ExternalAdHandoffTest.java")
 p=java/"AutoPilot.java"
 once(p,
  'final AdHomeRecovery adHome=new AdHomeRecovery();',
  'final AdHomeRecovery adHome=new AdHomeRecovery();final ExternalAdHandoff externalAdHandoff=new ExternalAdHandoff();')
 once(p,
  'target=packageNow();configuredEngine=phases.engine;',
  'target=packageNow();externalAdHandoff.reset();configuredEngine=phases.engine;')
 once(p,
  'if(!target.equals(packageNow())){usagiGameConfirmed=false;',
  'if(!target.equals(packageNow())){ExternalAdHandoff.Decision handoff=externalAdHandoff.observe(target,packageNow(),AdExitPolicy.adState(phases.engine.current),SystemClock.uptimeMillis());if(handoff==ExternalAdHandoff.Decision.WAIT){s.stopMarkers();phaseOwnedMarkers=false;note("広告遷移先の表示確定待ち / 連打保留");schedulePhase(expected,350);return;}usagiGameConfirmed=false;')
 once(p,
  'schedulePhase(expected,800);return;}PhaseEngine.Phase p=phases.engine.phases.get(phases.engine.current);',
  'schedulePhase(expected,800);return;}externalAdHandoff.reset();PhaseEngine.Phase p=phases.engine.phases.get(phases.engine.current);')
 # Preserve existing returnRegistered/launchRegisteredReturn Intent routing,
 # including target-package checks, cancel tokens and bounded retries.
 once(project/"tools/package_apk.py",
  'def manifest_version(data, code=140, version="1.137"):',
  'def manifest_version(data, code=141, version="1.138"):')
 once(project/"tools/PatchRuntime.java",
  'if(text.equals("v1.123"))return "v1.137";',
  'if(text.equals("v1.123"))return "v1.138";')
 for name in ("build.sh","run-host-tests.sh"):
  path=project/name;src=path.read_text(encoding="utf-8")
  if "1.137" not in src:raise RuntimeError(name+": unexpected baseline")
  path.write_text(src.replace("1.137","1.138").replace("versionCode140","versionCode141"),encoding="utf-8")
 with (project/"run-tests.sh").open("a",encoding="utf-8") as f:
  f.write('\njava -cp "build/runtime:build/api:build/tests:baseline-runtime.jar:$TEST_LIBS:$JAR:$LIBS" jp.tapmarker.ExternalAdHandoffTest\n')
 print("v1.138: stable external-ad observation integrated with physical Intent return")
if __name__=="__main__":
 import sys
 apply(Path(sys.argv[1]))
