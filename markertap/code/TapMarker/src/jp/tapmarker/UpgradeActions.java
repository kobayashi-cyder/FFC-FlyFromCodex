package jp.tapmarker;

import android.graphics.Rect;
import android.os.SystemClock;
import java.util.*;
import java.util.function.*;

/** Dedicated one-shot input lane for side upgrades; never pauses or restarts main taps. */
final class UpgradeActions {
 final UpgradeEngine engine;private final PhaseEngine model;private final TapService service;private final String pkg;private final Tapper sender;private final AndroidTapDriver driver;
 private UpgradeEngine.Decision pending;private BooleanSupplier permitted=()->false;private int reservedDisplay=-1;private Map<String,float[]> latestSamples=Collections.emptyMap();private long latestFrame;
 UpgradeActions(TapService service,String pkg,PhaseEngine model){
  this.service=service;this.pkg=pkg;this.model=model;engine=new UpgradeEngine(model,pkg);driver=new AndroidTapDriver(service);
  sender=new Tapper(SystemClock::uptimeMillis,(task,delay)->service.h.postDelayed(task,delay),new Tapper.Driver(){
   public boolean ready(Tapper.Command command){if(!valid()){release();return true;}reserve(command.displayId);return driver.ready(command);}
   public void send(Tapper.Command command,float[] point,BooleanSupplier current,Tapper.Result result){
    if(!valid()||!engine.visible(pending,latestSamples)||SystemClock.uptimeMillis()-latestFrame>=1500){release();result.finished(Tapper.INVALID_TARGET);return;}
    try{driver.send(command,point,()->current.getAsBoolean()&&valid(),value->{release();result.finished(value);});}catch(RuntimeException error){release();throw error;}
   }
  },new Tapper.Listener(){
   public void state(String state){if(!sender.active()&&pending!=null){engine.failed(pending,SystemClock.uptimeMillis());pending=null;release();}}
   public void result(Tapper.Command command,int result){if(result==Tapper.COMPLETED&&valid()){engine.sent(pending,SystemClock.uptimeMillis());pending=null;}}
  });
 }
 private void reserve(int display){if(reservedDisplay!=display){release();reservedDisplay=display;}service.inputPriority.request(display,this);}
 private void release(){if(reservedDisplay>=0){service.inputPriority.release(reservedDisplay,this);reservedDisplay=-1;}}
 private boolean valid(){return pending!=null&&engine.pending()==pending&&model.current.equals(pending.phase)&&model.epoch==pending.epoch&&permitted.getAsBoolean();}
 void cancel(String reason){pending=null;release();sender.pause(reason);engine.interrupt(SystemClock.uptimeMillis(),reason);}
 void addRegions(List<PhaseEngine.Region> regions){engine.addRegions(regions);}
 String report(){return "強化専用："+engine.status()+"\n"+sender.report();}
 void review(Map<String,float[]> samples,long capturedAt,boolean mainAllowed,int display,Rect bounds,Function<PhaseEngine.Watch,float[]> point,BooleanSupplier permitted){
  if(!engine.enabled())return;
  if(!mainAllowed){cancel("他イベントのため強化操作を保留");return;}
  latestSamples=samples;latestFrame=capturedAt;
  this.permitted=permitted;
  UpgradeEngine.Decision decision=engine.observe(samples,capturedAt,SystemClock.uptimeMillis(),true);
  if(decision==null)return;
  float[] location=point.apply(decision.watch);
  if(location==null||bounds==null||!bounds.contains((int)location[0],(int)location[1])){engine.failed(decision,SystemClock.uptimeMillis());return;}
  pending=decision;
  try{sender.update(new Tapper.Command(pkg,display,"upgrade:"+decision.watch.id+":"+decision.imageAt+":"+decision.epoch,new int[]{bounds.left,bounds.top,bounds.right,bounds.bottom},location,60,1,capturedAt+1500,false,1));}
  catch(IllegalArgumentException invalid){engine.failed(decision,SystemClock.uptimeMillis());pending=null;release();}
 }
}
