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
 shutil.copyfile(here/"overlay/src/jp/tapmarker/ExternalAdHandoffBridge.java",java/"ExternalAdHandoffBridge.java")
 shutil.copyfile(here/"tests/ExternalAdHandoffTest.java",project/"tests/ExternalAdHandoffTest.java")
 p=project/"tools/PatchRuntime.java"
 # The real APK preserves AutoPilot from the original compiled DEX.
 # Wrap only its phaseCycle(int) method with a fail-closed admission hook.
 # The original method is retained as phaseCycleOriginal(int).
 once(p,
  'static ClassDef migrateLoader(ClassDef original){\n  List<Method> methods=new ArrayList<>();\n  for(Method method:original.getMethods()){\n   if(method.getName().equals("load")',
  '''static ClassDef migrateLoader(ClassDef original){
  List<Method> methods=new ArrayList<>();boolean sawPhysicalPhase=false;
  for(Method method:original.getMethods()){
   if(method.getName().equals("phaseCycle")&&method.getParameterTypes().size()==1&&method.getParameterTypes().get(0).toString().equals("I")){
    sawPhysicalPhase=true;
    methods.add(new ImmutableMethod(method.getDefiningClass(),"phaseCycleOriginal",method.getParameters(),method.getReturnType(),method.getAccessFlags(),method.getAnnotations(),method.getHiddenApiRestrictions(),method.getImplementation()));
    ImmutableMethodReference bridge=new ImmutableMethodReference("Ljp/tapmarker/ExternalAdHandoffBridge;","defer",Arrays.asList("Ljp/tapmarker/AutoPilot;","I"),"Z");
    ImmutableMethodReference originalRef=new ImmutableMethodReference(method.getDefiningClass(),"phaseCycleOriginal",Arrays.asList("I"),"V");
    ImmutableMethodImplementation replacement=new ImmutableMethodImplementation(3,Arrays.asList(
      new ImmutableInstruction35c(Opcode.INVOKE_STATIC,2,1,2,0,0,0,bridge),
      new ImmutableInstruction11x(Opcode.MOVE_RESULT,0),
      new ImmutableInstruction21t(Opcode.IF_EQZ,0,3),
      new ImmutableInstruction10x(Opcode.RETURN_VOID),
      new ImmutableInstruction35c(Opcode.INVOKE_VIRTUAL,2,1,2,0,0,0,originalRef),
      new ImmutableInstruction10x(Opcode.RETURN_VOID)),Collections.emptyList(),Collections.emptyList());
    methods.add(new ImmutableMethod(method.getDefiningClass(),"phaseCycle",method.getParameters(),method.getReturnType(),method.getAccessFlags(),method.getAnnotations(),method.getHiddenApiRestrictions(),replacement));
   }else if(method.getName().equals("load")''')
 once(p,
  '  return new ImmutableClassDef(original.getType(),original.getAccessFlags(),original.getSuperclass(),original.getInterfaces(),original.getSourceFile(),original.getAnnotations(),original.getFields(),methods);\n }\n public static void main',
  '  if(!sawPhysicalPhase)throw new IllegalStateException("physical phaseCycle(int) bridge missing from shipping AutoPilot");\n  return new ImmutableClassDef(original.getType(),original.getAccessFlags(),original.getSuperclass(),original.getInterfaces(),original.getSourceFile(),original.getAnnotations(),original.getFields(),methods);\n }\n public static void main')
 # Existing returnRegistered/launchRegisteredReturn Intent logic remains in DEX.
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
