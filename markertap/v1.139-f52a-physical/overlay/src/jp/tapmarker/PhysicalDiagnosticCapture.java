package jp.tapmarker;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Bitmap;
import android.hardware.HardwareBuffer;
import android.os.Build;
import android.os.SystemClock;
import android.view.Display;
import android.util.Log;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Best-effort, bounded, on-demand diagnostic screenshot for the real display.
 *
 * The accessibility service already declares canTakeScreenshot. Do not capture
 * any unrelated foreground app, do not upload automatically, and do not block
 * the accessibility main thread on JPEG or pixel processing. Every incident
 * produces a ZIP even when Android denies or times out screenshot acquisition.
 */
final class PhysicalDiagnosticCapture {
 private static final int TIMEOUT_MS=4500;
 private static final ThreadPoolExecutor WORK=new ThreadPoolExecutor(
  1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<Runnable>(2),
  r->{Thread t=new Thread(r,"MarkerTap-physical-diagnostic");t.setDaemon(true);return t;},
  new ThreadPoolExecutor.AbortPolicy());

 private PhysicalDiagnosticCapture(){}

 private static void complete(TapService s,DiagnosticJournal journal,
   String app,int run,String archive,String report,long detectedAt,
   AtomicBoolean done,Runnable timeout,String status){
  if(!done.compareAndSet(false,true))return;
  s.h.removeCallbacks(timeout);
  journal.incident(app,0,run,archive,report+"\n実画面の画像取得："+status,detectedAt);
 }

 static void save(TapService s,DiagnosticJournal journal,String app,int run,
   String archive,String report,long detectedAt){
  final AtomicBoolean done=new AtomicBoolean();
  final Runnable[] timeoutHolder=new Runnable[1];
  timeoutHolder[0]=()->complete(s,journal,app,run,archive,report,detectedAt,
    done,timeoutHolder[0],"失敗（4500msタイムアウト）");
  final Runnable timeout=timeoutHolder[0];
  if(Build.VERSION.SDK_INT<30){
   complete(s,journal,app,run,archive,report,detectedAt,done,timeout,
    "未対応（Android 11未満）");return;
  }
  if(s.pilot==null||!app.equals(s.pilot.packageNow())){
   complete(s,journal,app,run,archive,report,detectedAt,done,timeout,
    "対象アプリが前面にないため取得を中止");return;
  }
  s.h.postDelayed(timeout,TIMEOUT_MS);
  try{
   s.takeScreenshot(Display.DEFAULT_DISPLAY,s.getMainExecutor(),
    new AccessibilityService.TakeScreenshotCallback(){
     @Override public void onFailure(int errorCode){
      complete(s,journal,app,run,archive,report,detectedAt,done,timeout,
       "失敗（Android screenshot error="+errorCode+"）");
     }
     @Override public void onSuccess(AccessibilityService.ScreenshotResult shot){
      final HardwareBuffer buffer;
      try{buffer=shot.getHardwareBuffer();}
      catch(RuntimeException failure){
       complete(s,journal,app,run,archive,report,detectedAt,done,timeout,
        "失敗（画面バッファ参照："+failure.getClass().getSimpleName()+"）");return;
      }
      if(buffer==null){
       complete(s,journal,app,run,archive,report,detectedAt,done,timeout,
        "失敗（画面バッファが空）");return;
      }
      if(done.get()||s.pilot==null||!app.equals(s.pilot.packageNow())){
       buffer.close();
       complete(s,journal,app,run,archive,report,detectedAt,done,timeout,
        "画面変化を検出したため取得を中止");
       return;
      }
      try{
       WORK.execute(()->{
        Bitmap hardware=null,software=null;
        try{
         if(done.get())return;
         hardware=Bitmap.wrapHardwareBuffer(buffer,shot.getColorSpace());
         if(hardware==null)throw new IllegalStateException("hardware bitmap unavailable");
         software=hardware.copy(Bitmap.Config.ARGB_8888,false);
         if(software==null)throw new IllegalStateException("software bitmap unavailable");
         // Claim the result before queuing the JPEG. The journal's single
         // worker then encodes this frame before it writes the ZIP.
         if(!done.compareAndSet(false,true))return;
         s.h.removeCallbacks(timeout);
         try{
          journal.frame(app,0,run,software.getWidth(),software.getHeight(),
           BitmapPixels.of(software),SystemClock.uptimeMillis(),"physical-error");
         }catch(RuntimeException|OutOfMemoryError error){
          Log.w("MarkerTap","Real-screen diagnostic frame unavailable",error);
         }
         journal.incident(app,0,run,archive,report+
          "\n実画面の画像取得：要求成功（frame_countでJPEG格納結果を確認）",detectedAt);
        }catch(RuntimeException|OutOfMemoryError error){
         Log.w("MarkerTap","Real-screen screenshot conversion failed",error);
         complete(s,journal,app,run,archive,report,detectedAt,done,timeout,
          "失敗（画像変換："+error.getClass().getSimpleName()+"）");
        }finally{
         if(software!=null)software.recycle();
         if(hardware!=null)hardware.recycle();
         buffer.close();
        }
       });
      }catch(RejectedExecutionException overloaded){
       buffer.close();
       complete(s,journal,app,run,archive,report,detectedAt,done,timeout,
        "失敗（画像処理キュー満杯）");
      }
     }
    });
  }catch(RuntimeException error){
   Log.w("MarkerTap","Real-screen screenshot request failed",error);
   complete(s,journal,app,run,archive,report,detectedAt,done,timeout,
    "失敗（取得要求："+error.getClass().getSimpleName()+"）");
  }
 }
}
