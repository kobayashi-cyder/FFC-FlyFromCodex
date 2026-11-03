package jp.tapmarker;

import android.accessibilityservice.AccessibilityService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.graphics.Rect;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.SystemClock;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.TimeUnit;

/** V1.157 binary bridge: fail-closed HOME -> target-only stop -> registered Intent. */
public final class AdV157DexBridge {
 private static final Map<Object,AdTwoSecondRecovery> EPISODES=Collections.synchronizedMap(new WeakHashMap<Object,AdTwoSecondRecovery>());
 private static final Map<Object,Boolean> RUNNING=Collections.synchronizedMap(new WeakHashMap<Object,Boolean>());
 private static final String ROOT="jp.tapmarker.",TOKEN="jp.tapmarker.lite.VirtualShell";
 private static final String[] GAMES={
   "com.kurashiru.kaitaku.usagi","com.kurashiru.fue.usagi",
   "com.kurashiru.nouen.usagi","com.kurashiru.coin.drop",
   "com.kurashiru.shopping.usagi","com.kurashiru.box.merge",
   "com.kurashiru.poipoi.usagi"};
 private static final int STOP_GAME=14,RECOVER_DISPLAY=15;
 private static boolean known(String name){return name!=null&&Arrays.asList(GAMES).contains(name);}
 private static Field field(Object o,String key)throws Exception{
   for(Class<?> c=o.getClass();c!=null;c=c.getSuperclass())
     try{Field f=c.getDeclaredField(key);f.setAccessible(true);return f;}catch(NoSuchFieldException missing){}
   throw new NoSuchFieldException(key);
 }
 private static Object get(Object o,String k)throws Exception{return field(o,k).get(o);}
 private static int number(Object o,String k)throws Exception{return field(o,k).getInt(o);}
 private static long time(Object o,String k)throws Exception{return field(o,k).getLong(o);}
 private static boolean flag(Object o,String k)throws Exception{return field(o,k).getBoolean(o);}
 private static Method method(Class<?> type,String name,int argc)throws Exception{
   for(Class<?> c=type;c!=null;c=c.getSuperclass())for(Method m:c.getDeclaredMethods())
     if(name.equals(m.getName())&&m.getParameterTypes().length==argc){m.setAccessible(true);return m;}
   throw new NoSuchMethodException(name+"/"+argc);
 }
 private static Object invoke(Object o,String key,Object...args)throws Exception{
   try{return method(o.getClass(),key,args.length).invoke(o,args);}
   catch(InvocationTargetException x){Throwable t=x.getCause();if(t instanceof Exception)throw (Exception)t;
     if(t instanceof Error)throw (Error)t;throw new IllegalStateException(t);}
 }
 private static Object stat(String name,String key,Object...args)throws Exception{
   return method(Class.forName(ROOT+name),key,args.length).invoke(null,args);
 }
 private static AdTwoSecondRecovery episode(Object o){
   synchronized(EPISODES){AdTwoSecondRecovery s=EPISODES.get(o);
     if(s==null){s=new AdTwoSecondRecovery();EPISODES.put(o,s);}return s;}
 }
 private static boolean running(Object o){return Boolean.TRUE.equals(RUNNING.get(o));}
 private static void running(Object o,boolean yes){if(yes)RUNNING.put(o,true);else RUNNING.remove(o);}
 private static boolean home(Object model,Map<String,float[]> samples){
   try{return Boolean.TRUE.equals(stat("RecoveryEvidence","home",model,samples));}catch(Exception ex){return false;}
 }
 private static boolean ad(Object model,Map<String,float[]> samples,int width,int height){
   if(samples==null||model==null||width<=0||height<=0)return false;
   try{
     if(flag(model,"windowRelative")||home(model,samples)||
        !Boolean.TRUE.equals(stat("AdExitPolicy","adState",get(model,"current"))))return false;
     if(Boolean.TRUE.equals(stat("PlayableAdEvidence","visible",samples,width,height))||
        Boolean.TRUE.equals(stat("AdHomeRecovery","visible",samples,width,height)))return true;
     Class<?> close=Class.forName(ROOT+"AdCloseAutomaton");
     for(String side:new String[]{"LEFT","RIGHT"}){
       Field f=close.getDeclaredField(side);f.setAccessible(true);
       float[] value=samples.get(String.valueOf(f.get(null)));
       if(value!=null&&value.length==1&&value[0]==1f)return true;
     }
   }catch(Exception|LinkageError unavailable){}
   return false;
 }
 private static void log(Object obj,String text){try{invoke(obj,"record",text);}catch(Exception ignored){}}
 private static boolean samePhysical(Object p,int token){
   try{return flag(p,"active")&&number(p,"token")==token;}catch(Exception ignored){return false;}
 }
 private static void endPhysical(Object p,int token,String msg){
   running(p,false);
   if(!samePhysical(p,token))return;
   try{field(p,"actionPending").setBoolean(p,false);
       invoke(p,"note",msg);invoke(p,"schedulePhase",token,250L);}catch(Exception ignored){}
 }
 @SuppressWarnings("unchecked")
 private static boolean physically(Object p,int token){
   if(running(p))return true;
   boolean touched=false;
   try{
     if(!samePhysical(p,token)||flag(get(p,"splitPilot"),"active"))return false;
     Object phases=get(p,"phases");if(phases==null||flag(phases,"customized"))return false;
     Object model=get(phases,"engine");String pkg=(String)get(p,"target");
     if(!known(pkg)||flag(model,"windowRelative")||flag(p,"actionPending")||flag(p,"returning"))return false;
     Map<String,float[]> samples=(Map<String,float[]>)get(p,"phaseSamples");
     int[] size=(int[])invoke(p,"displaySize");
     long captured=time(p,"phaseSampleTakenAt"),now=SystemClock.uptimeMillis();
     AdTwoSecondRecovery policy=episode(p);
     if(policy.step()==AdTwoSecondRecovery.Step.VERIFY){policy.verifiedGame(now,home(model,samples));return false;}
     if(!policy.observe(pkg,captured,now,ad(model,samples,size[0],size[1]),home(model,samples)))return false;
     running(p,true);touched=true;
     field(p,"actionPending").setBoolean(p,true);
     Object s=get(p,"s");invoke(s,"stopMarkers");field(p,"phaseOwnedMarkers").setBoolean(p,false);
     invoke(p,"cancelRegisteredReturn");
     log(phases,"CM継続2秒を確認：HOME→対象ゲームのみ停止→登録Intent");
     if(!(s instanceof AccessibilityService)||
        !((AccessibilityService)s).performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)){
       policy.failed(now);endPhysical(p,token,"HOME未受理：タスク停止を保留");return true;
     }
     Handler h=(Handler)get(s,"h");checkPhysicalHome(p,token,policy,h,now);return true;
   }catch(Exception|LinkageError fail){
     episode(p).failed(SystemClock.uptimeMillis());
     if(touched){endPhysical(p,token,"CM復帰の事前検証失敗：停止を保留");return true;}
     return false;
   }
 }
 private static void checkPhysicalHome(Object p,int token,AdTwoSecondRecovery policy,Handler h,long started){
   if(!samePhysical(p,token)){running(p,false);return;}
   long now=SystemClock.uptimeMillis();
   try{
     if(Boolean.TRUE.equals(invoke(p,"homeVisible"))){
       if(!policy.confirmedHome(now,true)){endPhysical(p,token,"HOME検証期限切れ");return;}
       String pkg=(String)get(p,"target");
       Thread worker=new Thread(()->{
         boolean stopped=false;
         try{IBinder b=(IBinder)stat("VirtualBridgeProvider","connected");
           if(b!=null)stopped=forceStop(b,pkg);}catch(Exception denied){}
         final boolean result=stopped;
         h.post(()->afterPhysicalStop(p,token,policy,result));
       },"MarkerTap-157-target-stop");
       worker.setDaemon(true);worker.start();return;
     }
   }catch(Exception ignored){}
   if(now-started>=AdTwoSecondRecovery.HOME_DEADLINE_MS){
     policy.failed(now);endPhysical(p,token,"HOME表示未確認：タスク停止拒否");return;
   }
   h.postDelayed(()->checkPhysicalHome(p,token,policy,h,started),100L);
 }
 private static void afterPhysicalStop(Object p,int token,AdTwoSecondRecovery policy,boolean stopped){
   if(!samePhysical(p,token)){running(p,false);return;}
   long now=SystemClock.uptimeMillis();
   if(!policy.confirmedKill(now,stopped)){endPhysical(p,token,"対象ゲームの停止失敗：起動保留");return;}
   try{
     Object s=get(p,"s");String pkg=(String)get(p,"target");
     Context context=(Context)s;Intent intent=context.getPackageManager().getLaunchIntentForPackage(pkg);
     if(intent==null||intent.getComponent()==null||
        !pkg.equals(intent.getComponent().getPackageName()))throw new SecurityException("Launch Intent mismatch");
     intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);
     context.startActivity(intent);
     if(!policy.confirmedIntent(SystemClock.uptimeMillis(),true))throw new IllegalStateException("Sequence expired");
     Object phases=get(p,"phases");invoke(get(phases,"engine"),"invalidateObservation");
     field(p,"phaseSampleTakenAt").setLong(p,0);
     log(phases,"HOME・force-stop・起動Intent成功。報酬の加算は未確認");
     endPhysical(p,token,"新しい主画面の認識待ち");
   }catch(Exception failed){policy.failed(SystemClock.uptimeMillis());
     endPhysical(p,token,"登録Intentの再起動未確認：手動確認待ち");}
 }
 private static boolean forceStop(IBinder b,String pkg)throws Exception{
   Parcel data=Parcel.obtain(),reply=Parcel.obtain();
   try{data.writeInterfaceToken(TOKEN);data.writeString(pkg);
     if(!b.transact(STOP_GAME,data,reply,0))return false;
     reply.readException();return reply.readInt()==1;
   }finally{data.recycle();reply.recycle();}
 }
 private static int recover(IBinder b,int display,long epoch)throws Exception{
   Parcel data=Parcel.obtain(),reply=Parcel.obtain();
   try{data.writeInterfaceToken(TOKEN);data.writeInt(display);data.writeLong(epoch);
     if(!b.transact(RECOVER_DISPLAY,data,reply,0))return 0;
     reply.readException();return reply.readInt();
   }finally{data.recycle();reply.recycle();}
 }
 public static void runPhysical(Object original,int token){
   boolean bypass=false;
   try{bypass=physically(original,token);}catch(Throwable unexpected){}
   if(!bypass)try{invoke(original,"v157$original$phaseCycle",token);}
   catch(Exception error){throw new IllegalStateException("Original phaseCycle failed",error);}
 }
 private static boolean sameVirtual(Object p,int token){
   try{return flag(p,"active")&&number(p,"token")==token;}catch(Exception ignored){return false;}
 }
 @SuppressWarnings("unchecked")
 private static boolean virtually(Object p,Object slot,Map<String,float[]> samples,Rect rect,int token){
   if(running(slot))return true;
   boolean touched=false;
   try{
     if(!sameVirtual(p,token)||!flag(p,"virtualMode")||flag(slot,"stopped"))return false;
     Object phases=get(slot,"phases"),model=get(phases,"engine");
     if(flag(phases,"customized")||flag(model,"windowRelative"))return false;
     String pkg=(String)get(slot,"pkg");if(!known(pkg))return false;
     long captured=time(slot,"observedAt"),now=SystemClock.uptimeMillis();
     AdTwoSecondRecovery policy=episode(slot);
     if(policy.step()==AdTwoSecondRecovery.Step.VERIFY){policy.verifiedGame(now,home(model,samples));return false;}
     if(!policy.observe(pkg,captured,now,ad(model,samples,rect.width(),rect.height()),home(model,samples)))return false;
     Object service=get(p,"s"),main=get(service,"pilot"),windows=get(main,"virtualWindows");
     Object w=invoke(windows,"window",pkg),remote=get(windows,"remote");
     if(w==null||remote==null){policy.failed(now);return false;}
     int display=number(w,"id");long epoch=time(windows,"remoteEpoch");
     IBinder binder=(IBinder)get(remote,"binder");
     if(display<=0||binder==null){policy.failed(now);return false;}
     invoke(p,"pauseMain",slot);running(slot,true);touched=true;
     Handler h=(Handler)get(service,"h");
     Thread worker=new Thread(()->{
       int result=0;
       try{result=recover(binder,display,epoch);}catch(Exception failed){}
       final int proof=result;
       h.post(()->{
         if(!sameVirtual(p,token)){running(slot,false);return;}
         if(policy.confirmedRemote(SystemClock.uptimeMillis(),proof)){
           try{invoke(model,"invalidateObservation");log(phases,"画面別HOME・force-stop・Intent実行。報酬未確認");}
           catch(Exception ignored){}
         }else try{invoke(main,"note","仮想画面の復帰保留："+proof);}catch(Exception ignored){}
         running(slot,false);
         try{invoke(p,"later",slot,token,250L);}catch(Exception ignored){}
       });
     },"MarkerTap-v157-virtual-"+display);
     worker.setDaemon(true);worker.start();return true;
   }catch(Exception|LinkageError fail){
     episode(slot).failed(SystemClock.uptimeMillis());
     if(touched)running(slot,false);
     return touched;
   }
 }
 public static void runVirtual(Object original,Object slot,Map<String,float[]> samples,Rect rect,int token){
   boolean bypass=false;
   try{bypass=virtually(original,slot,samples,rect,token);}catch(Throwable unexpected){}
   if(!bypass)try{invoke(original,"v157$original$applySamples",slot,samples,rect,token);}
   catch(Exception failed){throw new IllegalStateException("Original applySamples failed",failed);}
 }
 public static boolean shell(Object shell,int code,Parcel data,Parcel reply,int flags)throws RemoteException{
   if(code!=STOP_GAME&&code!=RECOVER_DISPLAY){
     try{return Boolean.TRUE.equals(invoke(shell,"v157$original$onTransact",code,data,reply,flags));}
     catch(RemoteException error){throw error;}
     catch(Exception failure){throw new RemoteException("Original shell error: "+failure.getClass().getSimpleName());}
   }
   int uid=android.os.Process.myUid();
   if(uid!=2000&&uid!=0)throw new SecurityException("Only authorized shell process");
   try{
     int caller=Binder.getCallingUid(),owner=number(shell,"controllerUid");
     if(caller!=owner&&caller!=uid&&caller!=0)throw new SecurityException("Unauthorized caller");
     data.enforceInterface(TOKEN);
     int answer=code==STOP_GAME?stopGame(shell,data.readString(),-1)?1:0:
         recoverOwnedDisplay(shell,data.readInt(),data.readLong());
     reply.writeNoException();reply.writeInt(answer);return true;
   }catch(SecurityException denied){throw denied;}
   catch(Exception failure){throw new RemoteException("Shell ad recovery unsupported: "+failure.getClass().getSimpleName());}
 }
 @SuppressWarnings("unchecked")
 private static boolean stopGame(Object shell,String pkg,int display)throws Exception{
   if(!known(pkg)||!flag(shell,"armed")||flag(shell,"closed"))return false;
   Map<Integer,Object> displays=(Map<Integer,Object>)get(shell,"displays");
   for(Object w:displays.values())if(pkg.equals(get(w,"pkg"))&&number(w,"id")!=display)return false;
   return command(5000,"/system/bin/am","force-stop","--user","current",pkg);
 }
 private static boolean command(long duration,String...args)throws Exception{
   Process p=new ProcessBuilder(args).redirectErrorStream(true).start();
   try{if(!p.waitFor(duration,TimeUnit.MILLISECONDS)){p.destroyForcibly();return false;}
     return p.exitValue()==0;}finally{p.destroy();}
 }
 private static boolean homeOnDisplay(Object shell,int id)throws Exception{
   Context context=(Context)get(shell,"context");
   ResolveInfo home=context.getPackageManager().resolveActivity(
       new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),0);
   if(home==null||home.activityInfo==null)return false;
   Object manager=Class.forName("android.app.ActivityTaskManager").getMethod("getService").invoke(null);
   Method tasks;
   try{tasks=manager.getClass().getMethod("getAllRootTaskInfosOnDisplay",int.class);}
   catch(NoSuchMethodException legacy){tasks=manager.getClass().getMethod("getAllStackInfosOnDisplay",int.class);}
   Object list=tasks.invoke(manager,id);
   if(!(list instanceof List))return false;
   for(Object task:(List<?>)list){
     if(task==null||task.getClass().getField("displayId").getInt(task)!=id)return false;
     if(!task.getClass().getField("visible").getBoolean(task))continue;
     Object top=task.getClass().getField("topActivity").get(task);
     return top instanceof ComponentName&&home.activityInfo.packageName.equals(
         ((ComponentName)top).getPackageName());
   }
   return false;
 }
 @SuppressWarnings("unchecked")
 private static int recoverOwnedDisplay(Object shell,int id,long expected)throws Exception{
   if(id<=0||!flag(shell,"armed")||flag(shell,"closed"))return 0;
   java.util.concurrent.atomic.AtomicLong generation=
       (java.util.concurrent.atomic.AtomicLong)get(shell,"epoch");
   if(generation.get()!=expected)return 0;
   Map<Integer,Object> displays=(Map<Integer,Object>)get(shell,"displays");
   Object w=displays.get(id);if(w==null||!known((String)get(w,"pkg")))return 0;
   synchronized(w){
     if(!flag(shell,"armed")||flag(shell,"closed")||generation.get()!=expected)return 0;
     if(!command(3000,"/system/bin/input","-d",String.valueOf(id),"keyevent","3"))return 0;
     long limit=SystemClock.uptimeMillis()+2000;
     boolean verified=false;
     while(SystemClock.uptimeMillis()<limit){
       if(generation.get()!=expected||displays.get(id)!=w||!flag(shell,"armed"))return 0;
       try{if(homeOnDisplay(shell,id)){verified=true;break;}}catch(Exception unsupported){return 0;}
       SystemClock.sleep(100);
     }
     if(!verified)return 0;
     int proof=1;
     if(!stopGame(shell,(String)get(w,"pkg"),id))return proof;
     proof|=2;
     if(generation.get()!=expected||displays.get(id)!=w||!flag(shell,"armed"))return proof;
     try{invoke(shell,"launch",id,Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);
       proof|=4;}catch(Exception unsupported){}
     return proof;
   }
 }
 private AdV157DexBridge(){}
}
