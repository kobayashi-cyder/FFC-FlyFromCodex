package jp.tapmarker;
import android.os.Handler;
import android.os.SystemClock;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Corrects a stale per-display manual-intervention state after a preview tap.
 * Never dispatches a tap, dismisses an ad, restarts a process or touches
 * another virtual display. The existing SplitPilot logic remains authoritative.
 */
public final class ManualPreviewTimeoutBridge {
 private static final Map<Object,Long> TOUCHES =
     Collections.synchronizedMap(new WeakHashMap<Object,Long>());
 private static Field field(Object owner,String name)throws Exception {
  for(Class<?> cls=owner.getClass();cls!=null;cls=cls.getSuperclass())
   try {Field f=cls.getDeclaredField(name);f.setAccessible(true);return f;}
   catch(NoSuchFieldException ignored){}
  throw new NoSuchFieldException(name);
 }
 private static Object get(Object owner,String name)throws Exception{return field(owner,name).get(owner);}
 private static boolean bool(Object owner,String name)throws Exception{return field(owner,name).getBoolean(owner);}
 private static int integer(Object owner,String name)throws Exception{return field(owner,name).getInt(owner);}
 private static Method method(Object owner,String name,int argc)throws Exception{
  Method found=null;
  for(Class<?> cls=owner.getClass();cls!=null;cls=cls.getSuperclass())
   for(Method m:cls.getDeclaredMethods())if(name.equals(m.getName())&&m.getParameterTypes().length==argc){
    if(found!=null)throw new NoSuchMethodException("ambiguous "+name);
    found=m;
   }
  if(found==null)throw new NoSuchMethodException(name);
  found.setAccessible(true);return found;
 }
 private static void resetStatus(Object runner,Object window,Object slot,int epoch,long touched){
  try{
   Long current=TOUCHES.get(window);
   if(current==null||current.longValue()!=touched)return;
   long now=SystemClock.uptimeMillis();
   boolean holding=(Boolean)method(get(slot,"manual"),"holding",1).invoke(get(slot,"manual"),now);
   if(holding)return;
   if(!ManualPreviewTimeoutPolicy.expired(touched,now,false,integer(runner,"token"),epoch))return;
   if(!ManualPreviewTimeoutPolicy.recoverable(bool(runner,"active"),bool(runner,"virtualMode"),bool(slot,"stopped"),bool(window,"closed")))return;
   if(!((String)get(window,"stopReason")).isEmpty())return;
   Object app=get(runner,"s"),pilot=get(app,"pilot"),windows=get(pilot,"virtualWindows");
   // A touch-up event may have been dropped by WindowManager. The 10-second
   // hold already expired; clear its stale per-window preview guard.
   boolean staleTouch=bool(window,"previewTouchActive");
   if(staleTouch)field(window,"previewTouchActive").setBoolean(window,false);
   String prior=String.valueOf(get(window,"runState"));
   boolean staleLabel=prior.startsWith("手動操作中");
   if(staleLabel)method(windows,"state",2).invoke(windows,window,"手動操作を終了 / 新しい画像を確認中");
   // Restore the normal lane scheduler once, and only if it is not in a
   // currently running sample. later() invalidates prior scheduled tickets.
   if(staleTouch||staleLabel){
    if(!bool(slot,"cyclePending"))method(runner,"later",3).invoke(runner,slot,epoch,0L);
   }
  }catch(Exception|LinkageError ignored){
   // ABI mismatch must not crash the accessibility service.
  }finally{
   synchronized(TOUCHES){Long last=TOUCHES.get(window);if(last!=null&&last.longValue()==touched)TOUCHES.remove(window);}
  }
 }
 public static void afterManualBegin(Object runner,Object window,boolean accepted){
  if(!accepted||runner==null||window==null)return;
  try{
   Object app=get(runner,"s");
   Handler h=(Handler)get(app,"h");
   final int epoch=integer(runner,"token");
   final long at=SystemClock.uptimeMillis();
   final Object slot=method(runner,"manualSlot",1).invoke(runner,window);
   if(slot==null)return;
   synchronized(TOUCHES){TOUCHES.put(window,at);}
   h.postDelayed(()->resetStatus(runner,window,slot,epoch,at),ManualPreviewTimeoutPolicy.RECHECK_MS);
  }catch(Exception|LinkageError ignored){} // original manual path continues
 }
 private ManualPreviewTimeoutBridge(){}
}
