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
 # Tie each real gesture request to a bounded, local diagnostic trace.
 tap=project/'src/jp/tapmarker/TapService.java'
 once(tap,'long inputTargetVersion;String inputHold="未開始",lastInputError="";long inputRequests,inputCompleted,inputCancelled,inputRejected,inputTimedOut;',
      'long inputTargetVersion;String inputHold="未開始",lastInputError="";long inputRequests,inputCompleted,inputCancelled,inputRejected,inputTimedOut;long inputEventSequence;String lastInputEvent="未送信";')
 once(tap,'+"\\n直近の入力エラー："+(lastInputError.isEmpty()?"なし":lastInputError)+"\\n本体認識画像："+',
      '+"\\n直近の入力エラー："+(lastInputError.isEmpty()?"なし":lastInputError)+"\\n直近の入力イベント："+lastInputEvent+"\\n本体認識画像："+')
 once(tap,'  inputRequests++;inputHold="送信中";if(progressErrors!=null)progressErrors.action(target,"ジェスチャー",0,Float.NaN,Float.NaN);',
      '  final long eventAt=SystemClock.uptimeMillis(),eventId=++inputEventSequence;String eventPhase=pilot==null?"":pilot.phases.engine.current;long eventFrameAt=pilot==null?0:pilot.phaseSampleTakenAt;final long eventRun=pilot==null?-1:(pilot.splitPilot.active?pilot.splitPilot.token:pilot.token);if(pilot!=null&&pilot.splitPilot.active&&pilot.splitPilot.slots!=null)for(SplitPilot.Slot slot:pilot.splitPilot.slots)if(slot.pkg.equals(target)){eventPhase=slot.phases.engine.current;eventFrameAt=slot.observedAt;break;}final long eventFrameAge=eventFrameAt<=0?-1:Math.max(0,eventAt-eventFrameAt);final String eventBase="id="+eventId+" target="+target+" phase="+eventPhase+" run="+eventRun+" frameAgeMs="+eventFrameAge+" strokes="+gesture.getStrokeCount();lastInputEvent=eventBase+" stage=request";inputRequests++;inputHold="送信中";if(progressErrors!=null)progressErrors.action(target,"ジェスチャー",0,Float.NaN,Float.NaN);')
 once(tap,'if(outcome==GestureDispatchGuard.COMPLETED){inputSuccessAt=SystemClock.uptimeMillis();inputCompleted++;inputHold="送信完了";if(callback!=null)callback.onCompleted(gesture);}',
      'if(outcome==GestureDispatchGuard.COMPLETED){inputSuccessAt=SystemClock.uptimeMillis();inputCompleted++;inputHold="送信完了";lastInputEvent=eventBase+" stage=completed elapsedMs="+Math.max(0,SystemClock.uptimeMillis()-eventAt);if(callback!=null)callback.onCompleted(gesture);}')
 once(tap,'else{inputFailureAt=SystemClock.uptimeMillis();inputCancelled++;if(outcome==GestureDispatchGuard.TIMED_OUT)inputTimedOut++;inputHold=outcome==GestureDispatchGuard.TIMED_OUT?"入力完了の通知待ちを終了・再確認":"Androidによる入力中断";lastInputError=inputHold;if(callback!=null)callback.onCancelled(gesture);}',
      'else{inputFailureAt=SystemClock.uptimeMillis();inputCancelled++;if(outcome==GestureDispatchGuard.TIMED_OUT)inputTimedOut++;inputHold=outcome==GestureDispatchGuard.TIMED_OUT?"入力完了の通知待ちを終了・再確認":"Androidによる入力中断";lastInputError=inputHold;lastInputEvent=eventBase+" stage="+(outcome==GestureDispatchGuard.TIMED_OUT?"timeout":"cancelled")+" elapsedMs="+Math.max(0,SystemClock.uptimeMillis()-eventAt)+" detail="+inputHold;if(callback!=null)callback.onCancelled(gesture);}')
 once(tap,'});if(!accepted){inputFailureAt=SystemClock.uptimeMillis();inputRejected++;inputHold="Androidが入力を拒否・他の入力処理中";lastInputError=inputHold;}return accepted;',
      '});if(!accepted){inputFailureAt=SystemClock.uptimeMillis();inputRejected++;inputHold="Androidが入力を拒否・他の入力処理中";lastInputError=inputHold;lastInputEvent=eventBase+" stage=rejected elapsedMs="+Math.max(0,SystemClock.uptimeMillis()-eventAt);}else lastInputEvent=eventBase+" stage=queued accepted=true";return accepted;')
 once(tap,'catch(RuntimeException failure){inputFailureAt=SystemClock.uptimeMillis();inputRejected++;inputHold="入力エラー："+VirtualWindows.brief(failure);lastInputError=inputHold;return false;}',
      'catch(RuntimeException failure){inputFailureAt=SystemClock.uptimeMillis();inputRejected++;inputHold="入力エラー："+VirtualWindows.brief(failure);lastInputError=inputHold;lastInputEvent=eventBase+" stage=exception elapsedMs="+Math.max(0,SystemClock.uptimeMillis()-eventAt)+" detail="+VirtualWindows.brief(failure);return false;}')
 const progress=project/'src/jp/tapmarker/ProgressErrors.java'
 once(progress,'if(w==null)b.append("\\n入力状況：").append(s.inputHold).append("\\n直近の入力エラー：").append(s.lastInputError);',
      'if(w==null)b.append("\\n入力イベント診断：").append(s.inputReport());')
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
