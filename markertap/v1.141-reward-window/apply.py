#!/usr/bin/env python3
"""Forward-port the supplied farm reward, visible-window geometry and capture clock."""
from pathlib import Path
import shutil


def once(path, before, after):
    path = Path(path)
    text = path.read_text(encoding="utf-8")
    if text.count(before) != 1:
        raise RuntimeError(f"{path}: expected one anchor: {before[:100]}")
    path.write_text(text.replace(before, after), encoding="utf-8")


def apply(project):
    project = Path(project)
    here = Path(__file__).resolve().parent
    java = project / "src/jp/tapmarker"
    for source in (here / "overlay/src/jp/tapmarker").glob("*.java"):
        shutil.copyfile(source, java / source.name)
    for source in (here / "tests").iterdir():
        if source.is_file():
            shutil.copyfile(source, project / "tests" / source.name)
    shutil.copyfile(here / "tools/RewardWindowAudit.java", project / "tools/RewardWindowAudit.java")

    # Wrap the ACTUAL screenshot callback, reading its frozen run token and
    # window bounds. Keep original conversion, cleanup and async fencing intact.
    patcher = project / "tools/PatchRuntime.java"
    once(patcher,
         'c.getType().equals("Ljp/tapmarker/AutoPilot;")?migrateLoader(c):',
         'c.getType().equals("Ljp/tapmarker/AutoPilot$1;")?captureClock(c):c.getType().equals("Ljp/tapmarker/AutoPilot;")?migrateLoader(c):')
    once(patcher, " static ClassDef migrateLoader(ClassDef original){", '''
 static ClassDef captureClock(ClassDef original){
  final String owner=original.getType();
  Map<String,String> fields=new HashMap<>();
  for(Field f:original.getFields())fields.put(f.getName(),f.getType());
  if(!"Ljp/tapmarker/AutoPilot;".equals(fields.get("this$0"))||!"I".equals(fields.get("val$expected"))||!"Landroid/graphics/Rect;".equals(fields.get("val$windowBounds")))throw new IllegalStateException("physical screenshot capture fields changed");
  List<Method> methods=new ArrayList<>();boolean found=false;
  String shot="Landroid/accessibilityservice/AccessibilityService$ScreenshotResult;";
  for(Method m:original.getMethods()){
   if(m.getName().equals("onSuccess")&&m.getParameterTypes().toString().equals("["+shot+"]")){
    found=true;
    methods.add(new ImmutableMethod(owner,"onSuccessOriginal",m.getParameters(),m.getReturnType(),m.getAccessFlags(),m.getAnnotations(),m.getHiddenApiRestrictions(),m.getImplementation()));
    ImmutableMethodReference clock=new ImmutableMethodReference("Ljp/tapmarker/PhysicalFrameHealth;","captured",Arrays.asList("Ljp/tapmarker/AutoPilot;","I",shot,"Landroid/graphics/Rect;"),"V");
    ImmutableMethodReference fallback=new ImmutableMethodReference(owner,"onSuccessOriginal",Arrays.asList(shot),"V");
    ImmutableMethodImplementation body=new ImmutableMethodImplementation(6,Arrays.asList(
     new ImmutableInstruction22c(Opcode.IGET_OBJECT,0,4,new ImmutableFieldReference(owner,"this$0","Ljp/tapmarker/AutoPilot;")),
     new ImmutableInstruction22c(Opcode.IGET,1,4,new ImmutableFieldReference(owner,"val$expected","I")),
     new ImmutableInstruction22c(Opcode.IGET_OBJECT,2,4,new ImmutableFieldReference(owner,"val$windowBounds","Landroid/graphics/Rect;")),
     new ImmutableInstruction35c(Opcode.INVOKE_STATIC,4,0,1,5,2,0,clock),
     new ImmutableInstruction35c(Opcode.INVOKE_VIRTUAL,2,4,5,0,0,0,fallback),
     new ImmutableInstruction10x(Opcode.RETURN_VOID)),Collections.emptyList(),Collections.emptyList());
    methods.add(new ImmutableMethod(owner,m.getName(),m.getParameters(),m.getReturnType(),m.getAccessFlags(),m.getAnnotations(),m.getHiddenApiRestrictions(),body));
   }else methods.add(m);
  }
  if(!found)throw new IllegalStateException("physical screenshot onSuccess missing");
  return new ImmutableClassDef(owner,original.getAccessFlags(),original.getSuperclass(),original.getInterfaces(),original.getSourceFile(),original.getAnnotations(),original.getFields(),methods);
 }
 static ClassDef migrateLoader(ClassDef original){''')

    errors = java / "ProgressErrors.java"
    once(errors, "sample.frameAge=age(w==null?observed:w.frameAt,now);",
         "sample.separateAnalysis=w==null&&!s.pilot.splitPilot.active&&!e.windowRelative&&app.equals(s.pilot.target);"
         "sample.capturedAt=sample.separateAnalysis?PhysicalFrameHealth.capturedAt(s.pilot):w==null?observed:w.frameAt;"
         "sample.analysisAt=observed;sample.analysisAge=age(observed,now);sample.frameAge=age(sample.capturedAt,now);")
    once(errors, 'b.append("\\n実行モード：").append(pilotVirtual(w)?"仮想画面":"実画面");',
         'b.append("\\n実行モード：").append(pilotVirtual(w)?"仮想画面":"実画面");\n'
         '  if(w==null&&!s.pilot.splitPilot.active)b.append("\\n").append(PhysicalFrameHealth.report(s.pilot,now));')

    once(project / "tools/package_apk.py", 'def manifest_version(data, code=143, version="1.140"):',
         'def manifest_version(data, code=144, version="1.141"):')
    once(patcher, 'if(text.equals("v1.123"))return "v1.140";',
         'if(text.equals("v1.123"))return "v1.141";')
    for name in ("build.sh", "run-host-tests.sh"):
        path = project / name
        text = path.read_text(encoding="utf-8")
        if "1.140" not in text:
            raise RuntimeError(name + ": unexpected version lineage")
        path.write_text(text.replace("1.140", "1.141").replace("versionCode143", "versionCode144"), encoding="utf-8")
    with (project / "run-tests.sh").open("a", encoding="utf-8") as stream:
        stream.write('\nfor test in VisibleWindowGeometry PhysicalCaptureWatchdog FarmRewardIncident; do\n java -cp "build/runtime:build/api:build/tests:baseline-runtime.jar:$TEST_LIBS:$JAR:$LIBS" jp.tapmarker.${test}Test\ndone\n')
    print("v1.141: visible-window intersection, paired reward masks and physical capture clock integrated")


if __name__ == "__main__":
    import sys
    apply(Path(sys.argv[1]))
