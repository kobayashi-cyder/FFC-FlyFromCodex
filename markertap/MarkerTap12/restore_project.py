#!/usr/bin/env python3
"""Restore the reviewed MarkerTap source, then apply the independent MarkerTap12 profile."""
from pathlib import Path
import re, subprocess, sys

HERE = Path(__file__).resolve().parent
REPO_MARKERTAP = HERE.parent

def once(path, before, after):
    data = path.read_text(encoding="utf-8")
    if data.count(before) != 1:
        raise RuntimeError(f"{path}: expected exactly one patch anchor: {before[:100]!r}")
    path.write_text(data.replace(before, after), encoding="utf-8")

def add_gesture_diagnostics(project):
    tap = project / "src/jp/tapmarker/TapService.java"
    once(tap,
         'long inputTargetVersion;String inputHold="未開始",lastInputError="";long inputRequests,inputCompleted,inputCancelled,inputRejected,inputTimedOut;',
         'long inputTargetVersion;String inputHold="未開始",lastInputError="";long inputRequests,inputCompleted,inputCancelled,inputRejected,inputTimedOut;long inputEventSequence;String lastInputEvent="未送信";')
    once(tap,
         '+"\\n直近の入力エラー："+(lastInputError.isEmpty()?"なし":lastInputError)+"\\n本体認識画像："+',
         '+"\\n直近の入力エラー："+(lastInputError.isEmpty()?"なし":lastInputError)+"\\n直近の入力イベント："+lastInputEvent+"\\n本体認識画像："+')
    once(tap,
         '  inputRequests++;inputHold="送信中";if(progressErrors!=null)progressErrors.action(target,"ジェスチャー",0,Float.NaN,Float.NaN);',
         '  final long eventAt=SystemClock.uptimeMillis(),eventId=++inputEventSequence;String eventPhase=pilot==null?"":pilot.phases.engine.current;long eventFrameAt=pilot==null?0:pilot.phaseSampleTakenAt;final long eventRun=pilot==null?-1:(pilot.splitPilot.active?pilot.splitPilot.token:pilot.token);if(pilot!=null&&pilot.splitPilot.active&&pilot.splitPilot.slots!=null)for(SplitPilot.Slot slot:pilot.splitPilot.slots)if(slot.pkg.equals(target)){eventPhase=slot.phases.engine.current;eventFrameAt=slot.observedAt;break;}final long eventFrameAge=eventFrameAt<=0?-1:Math.max(0,eventAt-eventFrameAt);final String eventBase="id="+eventId+" target="+target+" phase="+eventPhase+" run="+eventRun+" frameAgeMs="+eventFrameAge+" strokes="+gesture.getStrokeCount();lastInputEvent=eventBase+" stage=request";inputRequests++;inputHold="送信中";if(progressErrors!=null)progressErrors.action(target,"ジェスチャー",0,Float.NaN,Float.NaN);')
    once(tap,
         'if(outcome==GestureDispatchGuard.COMPLETED){inputSuccessAt=SystemClock.uptimeMillis();inputCompleted++;inputHold="送信完了";if(callback!=null)callback.onCompleted(gesture);}',
         'if(outcome==GestureDispatchGuard.COMPLETED){inputSuccessAt=SystemClock.uptimeMillis();inputCompleted++;inputHold="送信完了";if(eventId==inputEventSequence)lastInputEvent=eventBase+" stage=completed elapsedMs="+Math.max(0,SystemClock.uptimeMillis()-eventAt);if(callback!=null)callback.onCompleted(gesture);}')
    once(tap,
         'else{inputFailureAt=SystemClock.uptimeMillis();inputCancelled++;if(outcome==GestureDispatchGuard.TIMED_OUT)inputTimedOut++;inputHold=outcome==GestureDispatchGuard.TIMED_OUT?"入力完了の通知待ちを終了・再確認":"Androidによる入力中断";lastInputError=inputHold;if(callback!=null)callback.onCancelled(gesture);}',
         'else{inputFailureAt=SystemClock.uptimeMillis();inputCancelled++;if(outcome==GestureDispatchGuard.TIMED_OUT)inputTimedOut++;inputHold=outcome==GestureDispatchGuard.TIMED_OUT?"入力完了の通知待ちを終了・再確認":"Androidによる入力中断";lastInputError=inputHold;if(eventId==inputEventSequence)lastInputEvent=eventBase+" stage="+(outcome==GestureDispatchGuard.TIMED_OUT?"timeout":"cancelled")+" elapsedMs="+Math.max(0,SystemClock.uptimeMillis()-eventAt)+" detail="+inputHold;if(callback!=null)callback.onCancelled(gesture);}')
    once(tap,
         '});if(!accepted){inputFailureAt=SystemClock.uptimeMillis();inputRejected++;inputHold="Androidが入力を拒否・他の入力処理中";lastInputError=inputHold;}return accepted;',
         '});if(!accepted){inputFailureAt=SystemClock.uptimeMillis();inputRejected++;inputHold="Androidが入力を拒否・他の入力処理中";lastInputError=inputHold;lastInputEvent=eventBase+" stage=rejected elapsedMs="+Math.max(0,SystemClock.uptimeMillis()-eventAt);}else if(eventId==inputEventSequence)lastInputEvent=eventBase+" stage=queued accepted=true";return accepted;')
    once(tap,
         'catch(RuntimeException failure){inputFailureAt=SystemClock.uptimeMillis();inputRejected++;inputHold="入力エラー："+VirtualWindows.brief(failure);lastInputError=inputHold;return false;}',
         'catch(RuntimeException failure){inputFailureAt=SystemClock.uptimeMillis();inputRejected++;inputHold="入力エラー："+VirtualWindows.brief(failure);lastInputError=inputHold;lastInputEvent=eventBase+" stage=exception elapsedMs="+Math.max(0,SystemClock.uptimeMillis()-eventAt)+" detail="+VirtualWindows.brief(failure);return false;}')
    progress = project / "src/jp/tapmarker/ProgressErrors.java"
    once(progress,
         'if(w==null)b.append("\\n入力状況：").append(s.inputHold).append("\\n直近の入力エラー：").append(s.lastInputError);',
         'if(w==null)b.append("\\n入力イベント診断：").append(s.inputReport());')

def apply_profile(project):
    manifest = project / "AndroidManifest.xml"
    if not manifest.exists():
        raise RuntimeError("restored AndroidManifest.xml not found")
    text = manifest.read_text(encoding="utf-8")
    text = text.replace('package="jp.tapmarker.lite"', 'package="jp.tapmarker12.lite"')
    text = text.replace('android:targetSdkVersion="35"', 'android:targetSdkVersion="31"')
    text = re.sub(r'android:versionCode="\\d+"', 'android:versionCode="1200"', text, count=1)
    text = re.sub(r'android:versionName="[^"]+"', 'android:versionName="12.0.0"', text, count=1)
    if 'package="jp.tapmarker12.lite"' not in text or 'android:targetSdkVersion="31"' not in text or 'android:versionCode="1200"' not in text or 'android:versionName="12.0.0"' not in text:
        raise RuntimeError("MarkerTap12 manifest profile did not apply cleanly")
    manifest.write_text(text, encoding="utf-8")

    for path in project.rglob("*"):
        if not path.is_file() or path == manifest:
            continue
        try:
            data = path.read_text(encoding="utf-8")
        except (UnicodeDecodeError, OSError):
            continue
        updated = data.replace("jp.tapmarker.lite", "jp.tapmarker12.lite")
        updated = re.sub(r'(def manifest_version\\(data, code=)\\d+(, version=")[^"]+("\\):)', r'\\g<1>1200\\g<2>12.0.0\\g<3>', updated)
        updated = re.sub(r'versionCode\\s*=?\\s*\\d+', 'versionCode1200', updated)
        updated = re.sub(r"(['\\"]version_code['\\"]\\s*:\\s*)\\d+", r'\\g<1>1200', updated)
        updated = updated.replace('versionName="1.143"', 'versionName="12.0.0"').replace('versionName="1.144"', 'versionName="12.0.0"')
        updated = updated.replace('return "v1.143"', 'return "v12.0.0"').replace('return "v1.144"', 'return "v12.0.0"')
        updated = updated.replace("MarkerTap-v1.143", "MarkerTap12-v12.0.0").replace("MarkerTap-v1.144", "MarkerTap12-v12.0.0")
        if updated != data:
            path.write_text(updated, encoding="utf-8")

    manifest_text = manifest.read_text(encoding="utf-8")
    if 'package="jp.tapmarker.lite"' in manifest_text or 'android:targetSdkVersion="35"' in manifest_text:
        raise RuntimeError("Legacy application identity or target SDK remains in manifest")
    print("MarkerTap12 profile applied: package=jp.tapmarker12.lite, targetSdk=31, version=12.0.0 (1200)")

def main():
    if len(sys.argv) != 2:
        raise SystemExit("Usage: restore_project.py DESTINATION")
    project = Path(sys.argv[1]).resolve()
    source = REPO_MARKERTAP / "v1.143-kaitaku-actions" / "restore_project.py"
    subprocess.run([sys.executable, str(source), str(project)], check=True)
    add_gesture_diagnostics(project)
    apply_profile(project)

if __name__ == "__main__":
    main()
