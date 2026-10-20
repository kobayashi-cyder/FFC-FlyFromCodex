package jp.tapmarker;
import android.accessibilityservice.*;import android.graphics.*;import android.os.*;import android.view.*;import android.view.accessibility.*;import java.util.*;

/** Capture two explicitly selected landmarks from the actual target-app UI. */
final class DailyCapture {
 final DailyRunner owner;final AutoPilot pilot;final TapService s;final String pkg,profileTarget;final VirtualWindows.Window window;
 Rect scope;final ArrayList<Rect> boxes=new ArrayList<>();View overlay;boolean capturing;
 DailyCapture(DailyRunner r,String p,VirtualWindows.Window w){owner=r;pilot=r.pilot;s=r.s;profileTarget=p;pkg=GameTarget.packageName(p);window=w;}
 boolean valid(){return owner.capture==this;}
 Rect bounds(){
  if(window==null)return pkg.equals(pilot.packageNow())?pilot.splitLauncher.bounds(pkg):null;
  return VirtualWindows.previewBounds(window);
 }
 void start(){
  if(!valid())return;scope=bounds();if(scope==null||scope.isEmpty()){fail("対象画面を表示してから登録してください");return;}
  overlay=new View(s){float x,y,endX,endY;boolean dragging;Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
   protected void onDraw(Canvas c){int[] origin=new int[2];getLocationOnScreen(origin);c.translate(-origin[0],-origin[1]);paint.setColor(0xffffff00);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(3);for(Rect box:boxes)c.drawRect(box,paint);if(dragging)c.drawRect(Math.min(x,endX),Math.min(y,endY),Math.max(x,endX),Math.max(y,endY),paint);}
   public boolean onTouchEvent(MotionEvent event){if(!valid()||capturing)return true;Rect current=bounds();if(current==null||!current.equals(scope)){fail("画面位置が変わりました。登録し直してください");return true;}
    if(event.getActionMasked()==MotionEvent.ACTION_DOWN){if(!scope.contains((int)event.getRawX(),(int)event.getRawY()))return true;x=endX=event.getRawX();y=endY=event.getRawY();dragging=true;invalidate();return true;}
    if(!dragging)return true;if(event.getActionMasked()==MotionEvent.ACTION_MOVE){endX=Math.max(scope.left,Math.min(scope.right,event.getRawX()));endY=Math.max(scope.top,Math.min(scope.bottom,event.getRawY()));invalidate();return true;}
    if(event.getActionMasked()==MotionEvent.ACTION_CANCEL){dragging=false;invalidate();return true;}
    if(event.getActionMasked()==MotionEvent.ACTION_UP){dragging=false;endX=Math.max(scope.left,Math.min(scope.right,event.getRawX()));endY=Math.max(scope.top,Math.min(scope.bottom,event.getRawY()));Rect box=new Rect((int)Math.min(x,endX),(int)Math.min(y,endY),(int)Math.max(x,endX),(int)Math.max(y,endY));if(box.width()<8||box.height()<8){pilot.note("文字・印が入る範囲をドラッグで囲んでください");invalidate();return true;}boxes.add(box);invalidate();if(boxes.size()==1)pilot.note("② デイリーミッションの見出しを囲んでください");else{remove();capturing=true;s.h.postDelayed(()->snapshot(),200);}return true;}return true;
   }
  };
  s.wm.addView(overlay,s.params(-1,-1));if(s.panel!=null){WindowManager.LayoutParams p=(WindowManager.LayoutParams)s.panel.getLayoutParams();s.wm.removeView(s.panel);s.wm.addView(s.panel,p);}pilot.note("① 全デイリー達成・受取済みを示す文字や印を囲んでください / 全停止で取消");
 }
 void remove(){if(overlay!=null){try{s.wm.removeView(overlay);}catch(Exception ignored){}overlay=null;}}
 void cancel(){remove();}
 void fail(String message){if(Looper.myLooper()!=s.h.getLooper()){s.h.post(()->fail(message));return;}remove();if(valid())owner.capture=null;pilot.note(message);}
 void snapshot(){
  if(!valid())return;Rect current=bounds();if(current==null||!current.equals(scope)){fail("対象画面が移動しました。登録し直してください");return;}
  if(window!=null){pilot.virtualWindows.capture(window,()->valid()&&!window.closed,image->decode(image),()->fail("仮想画面を取得できませんでした"));return;}
  if(Build.VERSION.SDK_INT<30){fail("完了画面の記録にはAndroid11以降が必要です");return;}
  AccessibilityNodeInfo root=s.getRootInActiveWindow();if(root==null){fail("対象アプリの表示を確認できません");return;}int windowId=root.getWindowId();boolean correct=pkg.contentEquals(root.getPackageName()==null?"":root.getPackageName());root.recycle();if(!correct){fail("対象アプリが変わりました");return;}
  AccessibilityService.TakeScreenshotCallback callback=new AccessibilityService.TakeScreenshotCallback(){public void onFailure(int code){fail("完了画面を取得できませんでした（"+code+"）");}public void onSuccess(AccessibilityService.ScreenshotResult result){Bitmap hardware=null,image=null;try{hardware=Bitmap.wrapHardwareBuffer(result.getHardwareBuffer(),result.getColorSpace());if(hardware!=null)image=hardware.copy(Bitmap.Config.ARGB_8888,false);if(image!=null)image=pilot.windowImage(image,scope);}catch(OutOfMemoryError pressure){if(image!=null)image.recycle();image=null;s.performance.memoryPressure();}catch(Exception error){if(image!=null)image.recycle();image=null;}finally{if(hardware!=null)hardware.recycle();result.getHardwareBuffer().close();}if(image==null){fail("完了画面を読み込めませんでした");return;}decode(image);}};
  try{if(Build.VERSION.SDK_INT>=34)s.takeScreenshotOfWindow(windowId,s.getMainExecutor(),callback);else s.takeScreenshot(Display.DEFAULT_DISPLAY,s.getMainExecutor(),callback);}catch(Exception error){fail("完了画面の取得を開始できませんでした");}
 }
 void decode(Bitmap image){
  if(!valid()){image.recycle();return;}try{pilot.worker.execute(()->{DailyCompletion profile=null;String error="";try{
   int width=image.getWidth(),height=image.getHeight();RegionSampler.Pixels pixels=BitmapPixels.of(image);ContentViewport viewport=ContentViewport.locate(width,height,pixels,null);float[][] normalized=new float[2][];float[][] references=new float[2][];
   for(int i=0;i<2;i++){Rect box=boxes.get(i);normalized[i]=new float[]{((box.left-scope.left)*width/(float)scope.width()-viewport.left)/viewport.width,((box.top-scope.top)*height/(float)scope.height()-viewport.top)/viewport.height,((box.right-scope.left)*width/(float)scope.width()-viewport.left)/viewport.width,((box.bottom-scope.top)*height/(float)scope.height()-viewport.top)/viewport.height};if(!AdaptiveLayout.inside(normalized[i]))throw new IllegalArgumentException("ゲーム表示内の文字・印を囲んでください");references[i]=RegionSampler.sample(viewport.width,viewport.height,normalized[i],(x,y)->pixels.get(x+viewport.left,y+viewport.top));}
   profile=new DailyCompletion(normalized[0],normalized[1],references[0],references[1]);
  }catch(Exception failure){error=failure.getMessage();}catch(OutOfMemoryError pressure){s.performance.memoryPressure();error="メモリ不足のため記録できませんでした";}finally{image.recycle();}
  final DailyCompletion saved=profile;final String reason=error;s.h.post(()->{if(!valid())return;Rect current=bounds();if(current==null||!current.equals(scope)){fail("画面位置が変わったため記録を取り消しました");return;}if(saved==null){fail(reason);return;}owner.save(profileTarget,window!=null,saved);owner.capture=null;});
  });}catch(Exception stopped){image.recycle();fail("記録を中止しました");}
 }
}
