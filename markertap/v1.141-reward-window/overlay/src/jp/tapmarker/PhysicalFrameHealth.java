package jp.tapmarker;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Rect;
import android.os.SystemClock;
import java.util.WeakHashMap;

/** Capture receipt is independent from image conversion and accepted analysis. */
final class PhysicalFrameHealth {
 private static final class Run {
  final int token;final String app;long capturedAt;int width,height;Rect bounds;
  Run(AutoPilot p){token=p.token;app=p.target;}
 }
 private static final WeakHashMap<AutoPilot,Run> runs=new WeakHashMap<>();
 private PhysicalFrameHealth(){}
 // The DEX callback supplies its frozen expected token, not the pilot's new token.
 static synchronized void captured(AutoPilot p,int expected,AccessibilityService.ScreenshotResult shot,Rect bounds){
  if(p==null||!p.active||p.token!=expected||p.phases==null||!p.phases.enabled||p.phases.engine.windowRelative||shot==null)return;
  Run r=runs.get(p);
  if(r==null||r.token!=expected||!r.app.equals(p.target)){r=new Run(p);runs.put(p,r);}
  r.capturedAt=SystemClock.uptimeMillis();
  r.bounds=bounds==null?null:new Rect(bounds);
  // Observe metadata only. The original callback retains sole buffer ownership.
  try{r.width=shot.getHardwareBuffer().getWidth();r.height=shot.getHardwareBuffer().getHeight();}catch(RuntimeException unavailable){r.width=0;r.height=0;}
 }
 static synchronized long capturedAt(AutoPilot p){
  Run r=runs.get(p);return r!=null&&r.token==p.token&&r.app.equals(p.target)?r.capturedAt:0;
 }
 static synchronized String report(AutoPilot p,long now){
  Run r=runs.get(p);if(r==null||r.token!=p.token||!r.app.equals(p.target))return "実画面の取得成功：未記録";
  return "実画面の取得成功："+Math.max(0,now-r.capturedAt)+"ms前 / raw="+r.width+"x"+r.height+" / window="+r.bounds;
 }
}
