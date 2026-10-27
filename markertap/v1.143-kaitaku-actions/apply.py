#!/usr/bin/env python3
from pathlib import Path
import shutil
def once(path,before,after):
 text=path.read_text()
 if text.count(before)!=1:raise RuntimeError(str(path)+': changed anchor '+before[:90])
 path.write_text(text.replace(before,after))
def apply(project):
 project=Path(project);here=Path(__file__).resolve().parent
 # Android 12 compatibility profile: API 31 target while retaining API 35 compile tools.
 manifest=project/'AndroidManifest.xml'
 once(manifest,'android:targetSdkVersion="35"','android:targetSdkVersion="31"')
 import xml.etree.ElementTree as ET
 root=ET.parse(manifest).getroot()
 ns='{http://schemas.android.com/apk/res/android}'
 sdk=root.find('uses-sdk')
 if sdk is None or sdk.get(ns+'targetSdkVersion')!='31':raise RuntimeError('Android 12 target SDK must be 31')
 app=root.find('application')
 if app is None:raise RuntimeError('application element missing')
 for component in list(app):
  if component.find('intent-filter') is not None and component.get(ns+'exported') not in ('true','false'):
   raise RuntimeError('Android 12 requires explicit exported on '+str(component.get(ns+'name')))
 service=project/'res/xml/service.xml'
 if 'android:canTakeScreenshot="true"' not in service.read_text():
  raise RuntimeError('Accessibility screenshot capability missing')
 for folder,dest in [('overlay/src/jp/tapmarker','src/jp/tapmarker'),('tests','tests'),('assets','assets'),('tools','tools')]:
  for src in (here/folder).glob('*'):
   if src.is_file():shutil.copyfile(src,project/dest/src.name)
 once(project/'tools/package_apk.py','def manifest_version(data, code=147, version="1.144"):','def manifest_version(data, code=147, version="1.144"):')
 once(project/'tools/PatchRuntime.java','if(text.equals("v1.123"))return "v1.144";','if(text.equals("v1.123"))return "v1.144";')
 for name in ['build.sh','run-host-tests.sh']:
  p=project/name;p.write_text(p.read_text().replace('1.142','1.144').replace('versionCode145','versionCode147'))
 once(project/'tests/KaitakuIncidentTest.java','e.phases.size()==4&&e.current.equals(KaitakuPreset.P+"check")','e.phases.containsKey(KaitakuPreset.P+"missions")&&e.phases.containsKey(KaitakuPreset.P+"mini")&&e.current.equals(KaitakuPreset.P+"check")')
 with (project/'run-tests.sh').open('a') as out:out.write('\njava -Xmx512m -cp "build/runtime:build/api:build/tests:baseline-runtime.jar:$TEST_LIBS:$JAR:$LIBS" jp.tapmarker.KaitakuActionsTest\n')
 print('v1.144: Android 12 target SDK 31; retained recorded controls, mission loop, named mini-game and migration')
if __name__=='__main__':
 import sys
 apply(Path(sys.argv[1]))
