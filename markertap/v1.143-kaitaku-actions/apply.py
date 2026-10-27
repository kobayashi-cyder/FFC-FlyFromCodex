#!/usr/bin/env python3
from pathlib import Path
import shutil
def once(path,before,after):
 text=path.read_text()
 if text.count(before)!=1:raise RuntimeError(str(path)+': changed anchor '+before[:90])
 path.write_text(text.replace(before,after))
def apply(project):
 project=Path(project);here=Path(__file__).resolve().parent
 for folder,dest in [('overlay/src/jp/tapmarker','src/jp/tapmarker'),('tests','tests'),('assets','assets'),('tools','tools')]:
  for src in (here/folder).glob('*'):
   if src.is_file():shutil.copyfile(src,project/dest/src.name)
 once(project/'tools/package_apk.py','def manifest_version(data, code=145, version="1.142"):','def manifest_version(data, code=146, version="1.143"):')
 once(project/'tools/PatchRuntime.java','if(text.equals("v1.123"))return "v1.142";','if(text.equals("v1.123"))return "v1.143";')
 for name in ['build.sh','run-host-tests.sh']:
  p=project/name;p.write_text(p.read_text().replace('1.142','1.143').replace('versionCode145','versionCode146'))
 once(project/'tests/KaitakuIncidentTest.java','e.phases.size()==4&&e.current.equals(KaitakuPreset.P+"check")','e.phases.containsKey(KaitakuPreset.P+"missions")&&e.phases.containsKey(KaitakuPreset.P+"mini")&&e.current.equals(KaitakuPreset.P+"check")')
 with (project/'run-tests.sh').open('a') as out:out.write('\njava -Xmx512m -cp "build/runtime:build/api:build/tests:baseline-runtime.jar:$TEST_LIBS:$JAR:$LIBS" jp.tapmarker.KaitakuActionsTest\n')
 print('v1.143: recorded upgrade/boost controls, mission claim loop, named mini-game and default-profile migration')
if __name__=='__main__':
 import sys
 apply(Path(sys.argv[1]))
