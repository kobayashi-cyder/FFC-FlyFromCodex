package jp.tapmarker;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.IBinder;
import android.os.SystemClock;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Runtime adapter for the SHA-pinned v1.151 DEX. Do not change owner/window
 * lifetimes, display IDs, or another lane. All privileged operations use the
 * existing user-paired local ADB backend; this bridge never grants permissions.
 *
 * Reflection is restricted to the application's own jp.tapmarker objects.
 * Any missing binary contract fails closed, leaving the original call path.
 */
public final class EmbeddedRecoveryBridge {
 private static final class Lane {
  final VirtualRefreshPolicy policy = new VirtualRefreshPolicy();
  boolean removed, rebuilding, isolated;
 }
 private static final Map<Object,Lane> lanes =
     Collections.synchronizedMap(new WeakHashMap<Object,Lane>());
 private static Lane lane(Object w) {
  synchronized(lanes) {
   Lane v=lanes.get(w);
   if(v==null){v=new Lane();lanes.put(w,v);}
   return v;
  }
 }
 private static Field field(Class<?> t,String name)throws Exception {
  for(Class<?> c=t;c!=null;c=c.getSuperclass()) {
   try{Field f=c.getDeclaredField(name);f.setAccessible(true);return f;}
   catch(NoSuchFieldException ignore){}
  }
  throw new NoSuchFieldException(t.getName()+"."+name);
 }
 private static Object get(Object target,String name)throws Exception {
  return field(target.getClass(),name).get(target);
 }
 private static void set(Object target,String name,Object value)throws Exception {
  field(target.getClass(),name).set(target,value);
 }
 private static boolean yes(Object target,String name)throws Exception {
  return field(target.getClass(),name).getBoolean(target);
 }
 private static int integer(Object target,String name)throws Exception {
  return field(target.getClass(),name).getInt(target);
 }
 private static long number(Object target,String name)throws Exception {
  return field(target.getClass(),name).getLong(target);
 }
 private static Object call(Object target,String name,Object...args)throws Exception {
  Method found=null;
  for(Class<?> c=target.getClass();c!=null&&found==null;c=c.getSuperclass())
   for(Method m:c.getDeclaredMethods())if(m.getName().equals(name)&&m.getParameterTypes().length==args.length) {
    if(found!=null)throw new IllegalStateException("ambiguous "+name);
    found=m;
   }
  if(found==null)throw new NoSuchMethodException(name);
  found.setAccessible(true);
  return found.invoke(target,args);
 }
 private static Object pilot(Object parent)throws Exception {
  return get(get(parent,"s"),"pilot");
 }
 private static void state(Object parent,Object w,String value) {
  try{call(parent,"state",w,value);}catch(Exception ignored){}
 }
 private static void note(Object parent,String value) {
  try{call(pilot(parent),"note",value);}catch(Exception ignored){}
 }
 private static void quarantine(Object parent,Object w,Lane st,String reason) {
  st.isolated=true;st.rebuilding=false;st.removed=false;
  try{set(w,"stopReason",reason);}catch(Exception ignored){}
  try{call(get(w,"inputLane"),"cancel");}catch(Exception ignored){}
  state(parent,w,"個別停止");
  note(parent,"仮想画面を個別隔離しました：再開操作までタップを停止");
 }
 private static boolean network(Object parent) {
  try {
   Object service=get(parent,"s");
   if(!(service instanceof Context))return true;
   ConnectivityManager cm=(ConnectivityManager)((Context)service).getSystemService(Context.CONNECTIVITY_SERVICE);
   if(cm==null)return true;
   Network n=cm.getActiveNetwork();
   NetworkCapabilities caps=n==null?null:cm.getNetworkCapabilities(n);
   return caps!=null&&caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
  }catch(Exception e){return true;} // No permission: do not infer an outage.
 }
 private static boolean dns(Object w,long now) {
  try {
   long at=number(w,"screenInfoAt");
   if(at<=0||at>now||now-at>2000)return false;
   Object info=get(w,"screenInfo");
   if(info==null)return false;
   String text=String.valueOf(get(info,"text"));
   return text.contains("Cannot resolve destination host")
       ||text.contains("ERR_NAME_NOT_RESOLVED")
       ||text.contains("名前を解決できません");
  }catch(Exception error){return false;}
 }
 private static boolean active(Object parent) {
  try {
   Object split=get(pilot(parent),"splitPilot");
   return yes(split,"active") && yes(split,"virtualMode");
  }catch(Exception ignored){return false;}
 }
 private static boolean windowsContain(Object parent,Object w) {
  try{return ((Collection<?>)get(parent,"windows")).contains(w);}catch(Exception e){return false;}
 }
 private static boolean valid(Object parent,Object w) {
  try{return !yes(w,"closed")&&windowsContain(parent,w);}catch(Exception e){return false;}
 }
 /** Return true when the old Shizuku route was replaced by the internal ADB wizard. */
 public static boolean privilegedOpen(Object parent,boolean requested) {
  if(!requested)return false;
  try {
   Object wireless=get(pilot(parent),"wireless");
   call(wireless,"guide",Boolean.TRUE);
   return true;
  }catch(Exception failure){return false;} // Original path only if adapter cannot be installed.
 }
 /** Called only when the user invokes the app's autonomy start entrypoint. */
 public static void explicitStart(Object parent) {
  try{
   for(Object w:(Collection<?>)get(parent,"windows")){
    Lane st=lane(w);st.policy.resetAfterExplicitUserStart();
    st.removed=false;st.rebuilding=false;st.isolated=false;
    set(w,"stopReason","");
   }
  }catch(Exception ignored){}
 }
 /**
  * Pre-check guard. false = skip the existing input/recognition check.
  * The original method still handles taps and display correctness.
  */
 public static boolean allowCheck(Object parent,Object w) {
  if(w==null)return false;
  Lane st=lane(w);
  if(st.isolated)return false;
  try {
   if(!valid(parent,w))return false;
   long now=SystemClock.uptimeMillis();
   long frameAt=number(w,"frameAt");
   if(frameAt<0||frameAt>now)frameAt=0;
   VirtualRefreshPolicy.Decision decision=st.policy.observe(
    new VirtualRefreshPolicy.Observation(
     now,frameAt,active(parent),yes(w,"previewTouchActive"),
     network(parent),dns(w,now),st.removed,
     st.rebuilding||yes(w,"rebuilding")));
   switch(decision.action) {
    case WAIT_NETWORK:
     state(parent,w,"ネットワーク回復待ち");return false;
    case WAIT_DNS:
     state(parent,w,"DNS復旧待ち（再起動しません）");return false;
    case REQUEST_FRAME:
     try{call(parent,"requestFrameRefresh",w);}catch(Exception ignored){}
     state(parent,w,"画像を再取得中");return false;
    case RECREATE_DISPLAY:
     recreate(parent,w,st,decision.reason);return false;
    case QUARANTINE:
     quarantine(parent,w,st,decision.reason);return false;
    default:
     return true;
   }
  }catch(Exception failed){return true;} // Retain baseline checks on unexpected ABI mismatch.
 }
 private static void recreate(Object parent,Object w,Lane st,String reason) {
  if(st.rebuilding||st.isolated||!valid(parent,w))return;
  st.rebuilding=true;
  final BooleanSupplier valid=()->valid(parent,w);
  final Runnable done=()->{
   st.rebuilding=false;st.removed=false;
   state(parent,w,"画面再生成後の状態を確認中");
  };
  final Consumer<String> failed=error->{
   st.policy.failRebuild();
   quarantine(parent,w,st,"画面再生成失敗："+error);
  };
  try{
   call(parent,"rebuild",w,valid,done,failed);
  }catch(Exception e){
   st.policy.failRebuild();
   quarantine(parent,w,st,"画面再生成を実行できません");
  }
 }
 /**
  * Return true if a single removed display was handled; false retains the
  * original all-stop behavior (notably if the backend Binder has died).
  */
 public static boolean displayRemoved(Object listener,int id) {
  Object parent;
  try{parent=get(listener,"this$0");}catch(Exception e){return false;}
  try {
   Object service=get(parent,"remote");
   if(service!=null){
    IBinder binder=(IBinder)get(service,"binder");
    if(binder==null||!binder.isBinderAlive())return false;
   }
   for(Object w:(Collection<?>)get(parent,"windows")) {
    if(integer(w,"id")!=id||yes(w,"closed"))continue;
    Lane st=lane(w);
    if(st.isolated||st.rebuilding||yes(w,"rebuilding"))return true;
    st.removed=true;
    allowCheck(parent,w);
    return true;
   }
   return true;
  }catch(Exception e){return false;} // Baseline global safety stop remains.
 }
 private EmbeddedRecoveryBridge(){}
}
