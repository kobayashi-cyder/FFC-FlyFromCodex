#!/usr/bin/env python3
"""Wire the shared connectome admission graph into the shipping virtual runner."""
from pathlib import Path
import shutil

def once(path,before,after):
 path=Path(path)
 source=path.read_text(encoding="utf-8")
 count=source.count(before)
 if count!=1:
  hints=[]
  for hint in ("final TapService s;","void resetRun()","slots=ready.toArray","long period=PerformancePolicy.scanInterval","applySamples(slot,sampledRegions"):
   pos=source.find(hint)
   if pos>=0:hints.append(hint+" >>> "+source[max(0,pos-160):pos+450])
  raise RuntimeError(f"{path}: verified anchor count {count}, expected 1: {before[:140]} / candidate context: {hints}")
 path.write_text(source.replace(before,after),encoding="utf-8")

def apply(project):
 project=Path(project)
 here=Path(__file__).resolve().parent
 java=project/"src/jp/tapmarker"
 shutil.copyfile(here/"overlay/src/jp/tapmarker/ConnectomeMultiplexer.java",java/"ConnectomeMultiplexer.java")
 shutil.copyfile(here/"tests/ConnectomeMultiplexerTest.java",project/"tests/ConnectomeMultiplexerTest.java")
 p=java/"SplitPilot.java"
 once(p,
  "final TapService s;",
  "final TapService s;final ConnectomeMultiplexer virtualConnectome=new ConnectomeMultiplexer();")
 once(p,
  "active=false;token++;virtualMode=false;parallelVirtual=false;}",
  "virtualConnectome.reset();active=false;token++;virtualMode=false;parallelVirtual=false;}")
 once(p,
  "slots=ready.toArray(new Slot[0]);virtualMode=true;parallelVirtual=s.pilot.virtualWindows.remote!=null;active=true;",
  "slots=ready.toArray(new Slot[0]);String[] connectomeKeys=new String[slots.length];for(int i=0;i<slots.length;i++)connectomeKeys[i]=slots[i].pkg;virtualConnectome.start(connectomeKeys);virtualMode=true;parallelVirtual=s.pilot.virtualWindows.remote!=null;active=true;")
 once(p,
  "  long period=PerformancePolicy.scanInterval(s.performance.profile(),slot.processingMs,slots.length,s.performance.cores);",
  """  // Frame-driven sparse graph: do not queue another analysis for a frame
  // already consumed by this lane. The other display proceeds independently.
  ConnectomeMultiplexer.Decision admission=virtualConnectome.offer(slot.pkg,w.id,w.frameAt,slot.lastFrameAt,SystemClock.uptimeMillis());
  if(!admission.admit){
   if(admission.requestRefresh)s.pilot.virtualWindows.requestFrameRefresh(w);
   later(slot,t,admission.retryMs);return;
  }
  long period=PerformancePolicy.scanInterval(s.performance.profile(),slot.processingMs,slots.length,s.performance.cores);""")
 once(p,
  "applySamples(slot,sampledRegions,new Rect(0,0,width,height),t);};",
  "virtualConnectome.published(slot.pkg,capturedAt);applySamples(slot,sampledRegions,new Rect(0,0,width,height),t);};")
 # Existing physical adapter, tap dispatch and 1500ms validation are untouched.
 with (project/"run-tests.sh").open("a",encoding="utf-8") as f:
  f.write('\njava -cp "build/runtime:build/api:build/tests:baseline-runtime.jar:$TEST_LIBS:$JAR:$LIBS" jp.tapmarker.ConnectomeMultiplexerTest\n')
 once(project/"tools/package_apk.py",
  'def manifest_version(data, code=139, version="1.136"):',
  'def manifest_version(data, code=140, version="1.137"):')
 once(project/"tools/PatchRuntime.java",
  'if(text.equals("v1.123"))return "v1.136";',
  'if(text.equals("v1.123"))return "v1.137";')
 for name in ("build.sh","run-host-tests.sh"):
  path=project/name;src=path.read_text(encoding="utf-8")
  if "1.136" not in src:raise RuntimeError(name+": v1.136 lineage missing")
  path.write_text(src.replace("1.136","1.137").replace("versionCode139","versionCode140"),encoding="utf-8")
 print("MarkerTap v1.137 connectome + virtual two-lane admission integrated")

if __name__=="__main__":
 import sys
 apply(Path(sys.argv[1]))
