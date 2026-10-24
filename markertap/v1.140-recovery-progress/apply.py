#!/usr/bin/env python3
"""Bound physical return cancellation without replacing the preserved AutoPilot."""
from pathlib import Path
import shutil


def once(path, before, after):
    path = Path(path)
    source = path.read_text(encoding="utf-8")
    if source.count(before) != 1:
        raise RuntimeError(f"{path}: expected one source anchor: {before[:100]}")
    path.write_text(source.replace(before, after), encoding="utf-8")


def apply(project):
    project = Path(project)
    here = Path(__file__).resolve().parent
    java = project / "src/jp/tapmarker"
    for name in ("ReturnProgressGate.java", "ReturnProgressBridge.java"):
        shutil.copyfile(here / "overlay/src/jp/tapmarker" / name, java / name)
    shutil.copyfile(here / "tests/ReturnProgressGateTest.java", project / "tests/ReturnProgressGateTest.java")
    shutil.copyfile(here / "tools/ReturnProgressAudit.java", project / "tools/ReturnProgressAudit.java")

    # Retain the old method for custom automata and audit the real DEX call site.
    patcher = project / "tools/PatchRuntime.java"
    once(patcher, "List<Method> methods=new ArrayList<>();boolean sawPhysicalPhase=false;",
         "List<Method> methods=new ArrayList<>();boolean sawPhysicalPhase=false,sawReturnPreflight=false;")
    once(patcher, '   }else if(method.getName().equals("load")&&method.getParameterTypes().isEmpty()){',
         '''   }else if(method.getName().equals("verifyRegisteredReturn")&&method.getParameterTypes().toString().equals("[I, I, Ljava/lang/Runnable;, Z]")){
    sawReturnPreflight=true;
    methods.add(new ImmutableMethod(method.getDefiningClass(),"verifyRegisteredReturnOriginal",method.getParameters(),method.getReturnType(),method.getAccessFlags(),method.getAnnotations(),method.getHiddenApiRestrictions(),method.getImplementation()));
    ImmutableMethodReference bridge=new ImmutableMethodReference("Ljp/tapmarker/ReturnProgressBridge;","intercept",Arrays.asList("Ljp/tapmarker/AutoPilot;","I","I","Ljava/lang/Runnable;","Z"),"Z");
    ImmutableMethodReference originalRef=new ImmutableMethodReference(method.getDefiningClass(),"verifyRegisteredReturnOriginal",Arrays.asList("I","I","Ljava/lang/Runnable;","Z"),"V");
    ImmutableMethodImplementation replacement=new ImmutableMethodImplementation(6,Arrays.asList(
      new ImmutableInstruction35c(Opcode.INVOKE_STATIC,5,1,2,3,4,5,bridge),
      new ImmutableInstruction11x(Opcode.MOVE_RESULT,0),
      new ImmutableInstruction21t(Opcode.IF_EQZ,0,3),
      new ImmutableInstruction10x(Opcode.RETURN_VOID),
      new ImmutableInstruction35c(Opcode.INVOKE_VIRTUAL,5,1,2,3,4,5,originalRef),
      new ImmutableInstruction10x(Opcode.RETURN_VOID)),Collections.emptyList(),Collections.emptyList());
    methods.add(new ImmutableMethod(method.getDefiningClass(),"verifyRegisteredReturn",method.getParameters(),method.getReturnType(),method.getAccessFlags(),method.getAnnotations(),method.getHiddenApiRestrictions(),replacement));
   }else if(method.getName().equals("load")&&method.getParameterTypes().isEmpty()){''')
    once(patcher, '  if(!sawPhysicalPhase)throw new IllegalStateException("physical phaseCycle(int) bridge missing from shipping AutoPilot");',
         '  if(!sawPhysicalPhase)throw new IllegalStateException("physical phaseCycle(int) bridge missing from shipping AutoPilot");\n  if(!sawReturnPreflight)throw new IllegalStateException("return preflight bridge missing from shipping AutoPilot");')
    once(java / "ExternalAdHandoffBridge.java", "  String current=pilot.packageNow();",
         "  if(ReturnProgressBridge.monitor(pilot,expected))return true;\n  String current=pilot.packageNow();")

    errors = java / "ProgressErrors.java"
    once(errors, 'final String archive=journal.reserve(event.app);final int display=s.pilot.virtualWindows.window(event.app)==null?0:s.pilot.virtualWindows.window(event.app).id;final int run=s.pilot.splitPilot.token;',
         '''final String archive=journal.reserve(event.app);
  final boolean virtual=s.pilot.splitPilot.active&&s.pilot.splitPilot.virtualMode;
  final VirtualWindows.Window errorWindow=virtual?s.pilot.virtualWindows.window(event.app):null;
  final int display=errorWindow==null?0:errorWindow.id;
  final int run=s.pilot.splitPilot.active?s.pilot.splitPilot.token:s.pilot.token;
  if(!virtual&&s.pilot.phases!=null&&event.app.equals(s.pilot.target))
   journal.observed(event.app,0,run,s.pilot.phases.engine,s.pilot.phaseSamples,s.pilot.phaseSampleTakenAt,
     s.pilot.recognition,s.pilot.recognition,ReturnProgressBridge.report(s.pilot,now));''')
    once(errors, 'final String frozenBody=body;VirtualWindows.Window capture=s.pilot.virtualWindows.window(event.app);',
         'final String frozenBody=body;VirtualWindows.Window capture=errorWindow;')
    once(errors, 'VirtualWindows.Window w=s.pilot.virtualWindows.window(sample.app);',
         'VirtualWindows.Window w=s.pilot.splitPilot.active&&s.pilot.splitPilot.virtualMode?s.pilot.virtualWindows.window(sample.app):null;')
    once(errors, '.append(processing).append("ms");',
         '.append(w==null&&!s.pilot.splitPilot.active?"未計測":processing+"ms");\n  b.append("\\n実行モード：").append(pilotVirtual(w)?"仮想画面":"実画面");')
    once(errors, '\\n画像付き診断：', '\\n診断ZIP（画像取得の結果をZIP内に記録）：')

    # Freeze detection-time metadata. Image encoding remains on the bounded worker.
    journal = java / "DiagnosticJournal.java"
    once(journal, 'Selection selection=EMPTY;long action;String observation="{}";',
         'Selection selection=EMPTY;long action;String observation="{}",captureStatus="not_requested";')
    once(journal, ' String reserve(String app){',
         ' synchronized void captureStatus(String app,int display,int run,String status){lane(app,display,run).captureStatus=status;}\n String reserve(String app){')
    once(journal, 'observation=lane.observation;missing=lane.policy.posts;', 'missing=lane.policy.posts;')
    once(journal, 'final Lane lane;synchronized(this){lane=lanes.get(app);}',
         '''final Lane lane;final String frozenObservation,frozenCapture;
  synchronized(this){lane=lanes.get(app);boolean current=lane!=null&&lane.display==display&&lane.run==run;
   frozenObservation=current?lane.observation:"{}";frozenCapture=current?lane.captureStatus:"no_matching_run";}''')
    once(journal, 'List<DiagnosticArchive.Frame> frames=new ArrayList<>();String observation="{}";int missing=0;',
         'List<DiagnosticArchive.Frame> frames=new ArrayList<>();String observation=frozenObservation;int missing=0;')
    once(journal, '   frames.sort(Comparator.comparingLong(f->f.at));',
         '   if(display==0)frames.removeIf(f->f.at<detectedAt);\n   frames.sort(Comparator.comparingLong(f->f.at));')
    once(journal, '.put("frame_count",frames.size())',
         '.put("frame_count",frames.size()).put("mode",display==0?"physical":"virtual").put("capture_status",frames.isEmpty()?(frozenCapture.equals("queued")?"image_not_saved":frozenCapture):"saved")')
    once(journal, 'DiagnosticArchive.write(file,report,manifest.toString(2),frames);',
         'DiagnosticArchive.write(file,report+"\\n診断画像："+frames.size()+"枚 / "+(frames.isEmpty()?frozenCapture:"保存済み"),manifest.toString(2),frames);')
    capture = java / "PhysicalDiagnosticCapture.java"
    once(capture, '  journal.incident(app,0,run,archive,report+"\\n実画面の画像取得："+status,detectedAt);',
         '  journal.captureStatus(app,0,run,status);\n  journal.incident(app,0,run,archive,report+"\\n実画面の画像取得："+status,detectedAt);')
    once(capture, '         journal.incident(app,0,run,archive,report+',
         '         journal.captureStatus(app,0,run,"queued");\n         journal.incident(app,0,run,archive,report+')

    once(project / "tools/package_apk.py", 'def manifest_version(data, code=142, version="1.139"):',
         'def manifest_version(data, code=143, version="1.140"):')
    once(patcher, 'if(text.equals("v1.123"))return "v1.139";', 'if(text.equals("v1.123"))return "v1.140";')
    for name in ("build.sh", "run-host-tests.sh"):
        p = project / name
        text = p.read_text(encoding="utf-8")
        if "1.139" not in text:
            raise RuntimeError(name + ": unexpected build lineage")
        p.write_text(text.replace("1.139", "1.140").replace("versionCode142", "versionCode143"), encoding="utf-8")
    with (project / "run-tests.sh").open("a", encoding="utf-8") as stream:
        stream.write('\njava -cp "build/runtime:build/api:build/tests:baseline-runtime.jar:$TEST_LIBS:$JAR:$LIBS" jp.tapmarker.ReturnProgressGateTest\n')
    print("v1.140: bounded return preflight and physical diagnostic identity integrated")


if __name__ == "__main__":
    import sys
    apply(Path(sys.argv[1]))
