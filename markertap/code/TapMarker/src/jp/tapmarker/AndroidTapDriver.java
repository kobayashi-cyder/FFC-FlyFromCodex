package jp.tapmarker;

import android.accessibilityservice.*;
import android.graphics.*;
import android.os.SystemClock;
import android.view.Display;
import java.util.Arrays;
import java.util.function.BooleanSupplier;

/** Platform adapter for Tapper. Target validation is separate from game recognition. */
final class AndroidTapDriver implements Tapper.Driver {
 private final TapService service;
 private Tapper.Command checkedCommand;private long checkedAt=-1,checkedVersion=-1;private boolean checkedTarget;
 AndroidTapDriver(TapService service){this.service=service;}
 public boolean ready(Tapper.Command command){return service.inputPriority.allows(command.displayId,command.priority>0)&&(command.displayId!=Display.DEFAULT_DISPLAY&&service.pilot.virtualWindows.remote!=null||!service.busy);}
 private boolean target(Tapper.Command command){
  long now=SystemClock.uptimeMillis();
  if(checkedCommand!=null&&checkedCommand.sameRoute(command)&&checkedVersion==service.inputTargetVersion&&now-checkedAt<16)return checkedTarget;
  checkedCommand=command;checkedVersion=service.inputTargetVersion;checkedAt=now;checkedTarget=inspectTarget(command);return checkedTarget;
 }
 private boolean inspectTarget(Tapper.Command command){
  if(command.displayId!=Display.DEFAULT_DISPLAY){
   VirtualWindows.Window window=service.pilot.virtualWindows.window(command.target);
   return window!=null&&window.id==command.displayId&&!window.closed&&Arrays.equals(command.bounds(),new int[]{0,0,window.width,window.height})&&command.target.equals(service.pilot.virtualWindows.screen(window).pkg);
  }
  Rect bounds=service.pilot.splitLauncher.bounds(command.target);int[] saved=command.bounds();
  return bounds!=null&&Arrays.equals(saved,new int[]{bounds.left,bounds.top,bounds.right,bounds.bottom})&&service.pilot.splitLauncher.exposed(command.target,bounds);
 }
 public void send(Tapper.Command command,float[] point,BooleanSupplier current,Tapper.Result result){
  if(!current.getAsBoolean()||!target(command)){result.finished(Tapper.INVALID_TARGET);return;}
  if(!ready(command)){result.finished(Tapper.BUSY);return;}
  if(command.displayId!=Display.DEFAULT_DISPLAY){
   VirtualWindows.Window window=service.pilot.virtualWindows.window(command.target);boolean global=service.pilot.virtualWindows.remote==null;if(global)service.busy=true;
   if(command.priority>0&&service.progressErrors!=null)service.progressErrors.journal.priorityControl(command.target,command.displayId,service.pilot.splitPilot.token);service.pilot.virtualWindows.tap(window,point,command.pressMillis,()->current.getAsBoolean()&&target(command),()->{if(global)service.busy=false;result.finished(Tapper.COMPLETED);},()->{if(global)service.busy=false;result.finished(current.getAsBoolean()&&target(command)?Tapper.REJECTED:Tapper.INVALID_TARGET);});return;
  }
  service.busy=true;service.touchable(false);boolean covered=service.pilot.panelCovers(point[0],point[1]);if(covered)service.pilot.panelTouchable(false);
  Runnable release=()->{service.busy=false;if(covered)service.pilot.panelTouchable(true);service.touchable(!service.running);};
  Runnable send=()->{
   if(!current.getAsBoolean()||!target(command)){release.run();result.finished(Tapper.INVALID_TARGET);return;}
   Path path=new Path();path.moveTo(point[0],point[1]);GestureDescription gesture=new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(path,0,command.pressMillis)).build();
   boolean accepted=service.dispatchTrackedGesture(command.target,gesture,new AccessibilityService.GestureResultCallback(){
    public void onCompleted(GestureDescription g){release.run();result.finished(Tapper.COMPLETED);}
    public void onCancelled(GestureDescription g){release.run();result.finished(Tapper.CANCELLED);}
   },service.h);if(!accepted){release.run();result.finished(Tapper.REJECTED);}
  };if(covered)service.h.postDelayed(send,32);else send.run();
 }
}
