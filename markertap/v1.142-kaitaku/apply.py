#!/usr/bin/env python3
from pathlib import Path
import shutil

def once(path,before,after):
    text=path.read_text()
    if text.count(before)!=1:raise RuntimeError(f'{path}: anchor count {text.count(before)} for {before[:90]}')
    path.write_text(text.replace(before,after))

def apply(project):
    project=Path(project);here=Path(__file__).resolve().parent;java=project/'src/jp/tapmarker'
    for src in (here/'overlay/src/jp/tapmarker').glob('*.java'):shutil.copyfile(src,java/src.name)
    for src in (here/'assets').glob('*'):shutil.copyfile(src,project/'assets'/src.name)
    for src in (here/'tests').glob('*'):shutil.copyfile(src,project/'tests'/src.name)
    shutil.copyfile(here/'tools/KaitakuAudit.java',project/'tools/KaitakuAudit.java')
    patch=project/'tools/PatchRuntime.java'
    once(patch,'c.getType().equals("Ljp/tapmarker/AutoPilot$1;")?captureClock(c):',
         'c.getType().equals("Ljp/tapmarker/AppPhases;")?kaitakuPhases(c):c.getType().equals("Ljp/tapmarker/AdaptiveLayout;")?kaitakuLayout(c):c.getType().equals("Ljp/tapmarker/AutoPilot$1;")?captureClock(c):')
    once(patch,'static ClassDef migrateLoader(ClassDef original){','''
 static ClassDef kaitakuPhases(ClassDef original){
  List<Method> methods=new ArrayList<>();int count=0;
  for(Method m:original.getMethods()){
   if((m.getName().equals("load")||m.getName().equals("loadTarget"))&&m.getParameterTypes().toString().equals("[Ljava/lang/String;]")){
    count++;String saved=m.getName()+"KaitakuOriginal";
    methods.add(new ImmutableMethod(original.getType(),saved,m.getParameters(),m.getReturnType(),m.getAccessFlags(),m.getAnnotations(),m.getHiddenApiRestrictions(),m.getImplementation()));
    MethodReference load=new ImmutableMethodReference(original.getType(),saved,Arrays.asList("Ljava/lang/String;"),"V");
    MethodReference after=new ImmutableMethodReference("Ljp/tapmarker/KaitakuStartup;","loaded",Arrays.asList(original.getType()),"V");
    ImmutableMethodImplementation code=new ImmutableMethodImplementation(2,Arrays.asList(
     new ImmutableInstruction35c(Opcode.INVOKE_VIRTUAL,2,0,1,0,0,0,load),
     new ImmutableInstruction35c(Opcode.INVOKE_STATIC,1,0,0,0,0,0,after),new ImmutableInstruction10x(Opcode.RETURN_VOID)),Collections.emptyList(),Collections.emptyList());
    methods.add(new ImmutableMethod(original.getType(),m.getName(),m.getParameters(),m.getReturnType(),m.getAccessFlags(),m.getAnnotations(),m.getHiddenApiRestrictions(),code));
   }else if(m.getName().equals("resetPreset")&&m.getParameterTypes().isEmpty()){
    count++;methods.add(new ImmutableMethod(original.getType(),"resetPresetKaitakuOriginal",m.getParameters(),m.getReturnType(),m.getAccessFlags(),m.getAnnotations(),m.getHiddenApiRestrictions(),m.getImplementation()));
    MethodReference reset=new ImmutableMethodReference("Ljp/tapmarker/KaitakuStartup;","reset",Arrays.asList(original.getType()),"I");
    MethodReference old=new ImmutableMethodReference(original.getType(),"resetPresetKaitakuOriginal",Collections.emptyList(),"Z");
    ImmutableMethodImplementation code=new ImmutableMethodImplementation(2,Arrays.asList(
     new ImmutableInstruction35c(Opcode.INVOKE_STATIC,1,1,0,0,0,0,reset),new ImmutableInstruction11x(Opcode.MOVE_RESULT,0),
     new ImmutableInstruction21t(Opcode.IF_LTZ,0,3),new ImmutableInstruction11x(Opcode.RETURN,0),
     new ImmutableInstruction35c(Opcode.INVOKE_VIRTUAL,1,1,0,0,0,0,old),new ImmutableInstruction11x(Opcode.MOVE_RESULT,0),new ImmutableInstruction11x(Opcode.RETURN,0)),Collections.emptyList(),Collections.emptyList());
    methods.add(new ImmutableMethod(original.getType(),m.getName(),m.getParameters(),m.getReturnType(),m.getAccessFlags(),m.getAnnotations(),m.getHiddenApiRestrictions(),code));
   }else methods.add(m);
  }
  if(count!=3)throw new IllegalStateException("Kaitaku AppPhases hooks changed: "+count);
  return new ImmutableClassDef(original.getType(),original.getAccessFlags(),original.getSuperclass(),original.getInterfaces(),original.getSourceFile(),original.getAnnotations(),original.getFields(),methods);
 }
 static ClassDef kaitakuLayout(ClassDef original){
  List<Method> methods=new ArrayList<>();boolean found=false;
  for(Method m:original.getMethods()){
   if(m.getName().equals("install")&&m.getParameterTypes().size()==4){
    found=true;methods.add(new ImmutableMethod(original.getType(),"installKaitakuOriginal",m.getParameters(),m.getReturnType(),m.getAccessFlags(),m.getAnnotations(),m.getHiddenApiRestrictions(),m.getImplementation()));
    MethodReference choose=new ImmutableMethodReference("Ljp/tapmarker/KaitakuLayout;","choose",Arrays.asList("Ljp/tapmarker/PhaseEngine;","Ljava/lang/String;","Ljava/util/Map;"),original.getType());
    List<String> params=new ArrayList<>();for(CharSequence p:m.getParameterTypes())params.add(p.toString());
    MethodReference old=new ImmutableMethodReference(original.getType(),"installKaitakuOriginal",params,original.getType());
    ImmutableMethodImplementation code=new ImmutableMethodImplementation(5,Arrays.asList(
     new ImmutableInstruction35c(Opcode.INVOKE_STATIC,3,1,2,3,0,0,choose),new ImmutableInstruction11x(Opcode.MOVE_RESULT_OBJECT,0),
     new ImmutableInstruction21t(Opcode.IF_EQZ,0,3),new ImmutableInstruction11x(Opcode.RETURN_OBJECT,0),
     new ImmutableInstruction35c(Opcode.INVOKE_STATIC,4,1,2,3,4,0,old),new ImmutableInstruction11x(Opcode.MOVE_RESULT_OBJECT,0),new ImmutableInstruction11x(Opcode.RETURN_OBJECT,0)),Collections.emptyList(),Collections.emptyList());
    methods.add(new ImmutableMethod(original.getType(),m.getName(),m.getParameters(),m.getReturnType(),m.getAccessFlags(),m.getAnnotations(),m.getHiddenApiRestrictions(),code));
   }else methods.add(m);
  }
  if(!found)throw new IllegalStateException("Kaitaku layout installer missing");
  return new ImmutableClassDef(original.getType(),original.getAccessFlags(),original.getSuperclass(),original.getInterfaces(),original.getSourceFile(),original.getAnnotations(),original.getFields(),methods);
 }
 static ClassDef migrateLoader(ClassDef original){''')
    once(patch,'if(method.getName().equals("phaseCycle")', '''if(method.getName().equals("startSingle")&&method.getParameterTypes().isEmpty()){
    methods.add(new ImmutableMethod(original.getType(),"startSingleKaitakuOriginal",method.getParameters(),method.getReturnType(),method.getAccessFlags(),method.getAnnotations(),method.getHiddenApiRestrictions(),method.getImplementation()));
    MethodReference bridge=new ImmutableMethodReference("Ljp/tapmarker/KaitakuStartup;","start",Arrays.asList(original.getType()),"Z");
    MethodReference old=new ImmutableMethodReference(original.getType(),"startSingleKaitakuOriginal",Collections.emptyList(),"V");
    ImmutableMethodImplementation code=new ImmutableMethodImplementation(2,Arrays.asList(
     new ImmutableInstruction35c(Opcode.INVOKE_STATIC,1,1,0,0,0,0,bridge),new ImmutableInstruction11x(Opcode.MOVE_RESULT,0),
     new ImmutableInstruction21t(Opcode.IF_EQZ,0,3),new ImmutableInstruction10x(Opcode.RETURN_VOID),
     new ImmutableInstruction35c(Opcode.INVOKE_VIRTUAL,1,1,0,0,0,0,old),new ImmutableInstruction10x(Opcode.RETURN_VOID)),Collections.emptyList(),Collections.emptyList());
    methods.add(new ImmutableMethod(original.getType(),method.getName(),method.getParameters(),method.getReturnType(),method.getAccessFlags(),method.getAnnotations(),method.getHiddenApiRestrictions(),code));
   }else if(method.getName().equals("canTapUsagi")&&method.getParameterTypes().isEmpty()){
    methods.add(new ImmutableMethod(original.getType(),"canTapUsagiKaitakuOriginal",method.getParameters(),method.getReturnType(),method.getAccessFlags(),method.getAnnotations(),method.getHiddenApiRestrictions(),method.getImplementation()));
    MethodReference running=new ImmutableMethodReference("Ljp/tapmarker/KaitakuStartup;","running",Arrays.asList(original.getType()),"Z");
    MethodReference tap=new ImmutableMethodReference("Ljp/tapmarker/KaitakuStartup;","canTap",Arrays.asList(original.getType()),"Z");
    MethodReference old=new ImmutableMethodReference(original.getType(),"canTapUsagiKaitakuOriginal",Collections.emptyList(),"Z");
    ImmutableMethodImplementation code=new ImmutableMethodImplementation(2,Arrays.asList(
     new ImmutableInstruction35c(Opcode.INVOKE_STATIC,1,1,0,0,0,0,running),new ImmutableInstruction11x(Opcode.MOVE_RESULT,0),
     new ImmutableInstruction21t(Opcode.IF_EQZ,0,7),new ImmutableInstruction35c(Opcode.INVOKE_STATIC,1,1,0,0,0,0,tap),new ImmutableInstruction11x(Opcode.MOVE_RESULT,0),new ImmutableInstruction11x(Opcode.RETURN,0),
     new ImmutableInstruction35c(Opcode.INVOKE_VIRTUAL,1,1,0,0,0,0,old),new ImmutableInstruction11x(Opcode.MOVE_RESULT,0),new ImmutableInstruction11x(Opcode.RETURN,0)),Collections.emptyList(),Collections.emptyList());
    methods.add(new ImmutableMethod(original.getType(),method.getName(),method.getParameters(),method.getReturnType(),method.getAccessFlags(),method.getAnnotations(),method.getHiddenApiRestrictions(),code));
   }else if(method.getName().equals("phaseCycle")''')
    once(java/'ReturnProgressBridge.java','(p.farmActive()||p.usagiPresetActive())','(p.farmActive()||p.usagiPresetActive()||KaitakuStartup.running(p))')
    once(java/'SplitPilot.java','slot.home=usagi?','slot.home=KaitakuPreset.APP.equals(slot.pkg)?KaitakuPreset.home(samples):usagi?')
    once(project/'tools/package_apk.py','def manifest_version(data, code=144, version="1.141"):', 'def manifest_version(data, code=145, version="1.142"):')
    once(patch,'if(text.equals("v1.123"))return "v1.141";','if(text.equals("v1.123"))return "v1.142";')
    for name in ['build.sh','run-host-tests.sh']:
        p=project/name;p.write_text(p.read_text().replace('1.141','1.142').replace('versionCode144','versionCode145'))
    with (project/'run-tests.sh').open('a') as f:f.write('\njava -Xmx512m -cp "build/runtime:build/api:build/tests:baseline-runtime.jar:$TEST_LIBS:$JAR:$LIBS" jp.tapmarker.KaitakuIncidentTest\n')
    print('v1.142 Kaitaku: separate profile/startup/layout/tap-freshness hooks integrated')

if __name__=='__main__':
    import sys
    apply(Path(sys.argv[1]))
