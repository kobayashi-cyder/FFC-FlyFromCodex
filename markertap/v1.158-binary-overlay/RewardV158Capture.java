package jp.tapmarker;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Bitmap;
import android.hardware.HardwareBuffer;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Safe, low-rate, read-only HUD evidence for physical runs on API >=34.
 * Never taps, changes panel visibility, or touches the original screenshot
 * scheduling. Failure/timeout means NO score, not a guessed point total.
 */
final class RewardV158Capture {
  private static final long MIN_SCAN_MS=1600,EXPIRE_MS=5000;
  private static final ExecutorService WORKER=
    Executors.newSingleThreadExecutor(r->{
      Thread t=new Thread(r,"MarkerTap-v158-pt-proof");
      t.setDaemon(true);return t;
    });
  private static final class Request {
    long requestedAt=-1;
    boolean busy;
  }
  private static final Map<Object,Request> SCANS=
    Collections.synchronizedMap(new WeakHashMap<Object,Request>());

  private static Request request(Object key){
    synchronized(SCANS){
      Request r=SCANS.get(key);
      if(r==null){r=new Request();SCANS.put(key,r);}
      return r;
    }
  }
  private static int verifyWindow(AccessibilityService s,String pkg){
    AccessibilityNodeInfo n=s.getRootInActiveWindow();
    if(n==null)return -1;
    try{
      CharSequence source=n.getPackageName();
      return source!=null&&pkg.equals(source.toString())?n.getWindowId():-1;
    }finally{n.recycle();}
  }
  static void capture(Object lane,AccessibilityService service,String pkg) {
    if(lane==null||service==null||pkg==null||pkg.isEmpty()
        ||android.os.Build.VERSION.SDK_INT<34)return;
    long now=SystemClock.uptimeMillis();
    Request gate=request(lane);
    synchronized(gate){
      if(gate.busy&&now-gate.requestedAt<EXPIRE_MS)return;
      if(now-gate.requestedAt<MIN_SCAN_MS)return;
      gate.busy=true;gate.requestedAt=now;
    }
    final int id;
    try{id=verifyWindow(service,pkg);}
    catch(RuntimeException unavailable){release(gate);return;}
    if(id<0){release(gate);return;}
    try {
      service.takeScreenshotOfWindow(id,service.getMainExecutor(),
          new AccessibilityService.TakeScreenshotCallback(){
        public void onFailure(int error){release(gate);}
        public void onSuccess(AccessibilityService.ScreenshotResult result){
          // The callback does not hold the main thread during OCR or pixel copy.
          WORKER.execute(()->{
            Bitmap hw=null,copy=null;
            HardwareBuffer buffer=result.getHardwareBuffer();
            try{
              long capturedAt=SystemClock.uptimeMillis();
              if(buffer==null)return;
              hw=Bitmap.wrapHardwareBuffer(buffer,result.getColorSpace());
              if(hw==null)return;
              copy=hw.copy(Bitmap.Config.ARGB_8888,false);
              if(copy==null)return;
              final Bitmap b=copy;
              long points=KurashiruPointHud.read((x,y)->b.getPixel(x,y),
                  b.getWidth(),b.getHeight());
              long processedAt=SystemClock.uptimeMillis();
              if(points<0||processedAt-capturedAt>=1500)return;
              if(verifyWindow(service,pkg)!=id)return;
              RewardV158Observer.balance(lane,pkg,points,capturedAt,processedAt);
            }catch(RuntimeException|LinkageError denied){
              // No screenshot = no reward claim, never recover by guessing.
            }finally{
              if(copy!=null&&!copy.isRecycled())copy.recycle();
              if(hw!=null&&!hw.isRecycled())hw.recycle();
              if(buffer!=null)buffer.close();
              release(gate);
            }
          });
        }
      });
    }catch(RuntimeException|LinkageError unsupported){release(gate);}
  }
  private static void release(Request gate){
    synchronized(gate){gate.busy=false;}
  }
  private RewardV158Capture(){}
}
