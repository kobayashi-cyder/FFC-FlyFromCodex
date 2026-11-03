package jp.tapmarker;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Conservative localhost-only reconnection. Never starts adbd, changes
 * debugging settings, probes a port range or connects to another device.
 *
 * The normal ADB client still performs its original TLS authentication.
 * A cached endpoint is usable only after prior local Binder authorization.
 */
public final class SafeLoopbackAdbBridge {
 private static final String PREFS="wirelessAdb";
 private static final String PORT="trustedLocalPort";
 private static final String PAIRED="paired";
 private static final String STARTED="offlineRetryStarted";
 private static final String ATTEMPTS="offlineRetryAttempts";

 private static Field field(Object o,String name)throws Exception {
  for(Class<?> c=o.getClass();c!=null;c=c.getSuperclass())
   try{Field f=c.getDeclaredField(name);f.setAccessible(true);return f;}
   catch(NoSuchFieldException ignored){}
  throw new NoSuchFieldException(name);
 }
 private static Context context(Object adb)throws Exception {
  Object s=field(adb,"s").get(adb);
  if(!(s instanceof Context))throw new SecurityException("not app context");
  return (Context)s;
 }
 private static SharedPreferences prefs(Object adb)throws Exception {
  return context(adb).getSharedPreferences(PREFS,0);
 }
 private static int cached(Object adb)throws Exception {
  SharedPreferences p=prefs(adb);
  return LoopbackRoutePolicy.eligiblePort(p.getBoolean(PAIRED,false),p.getInt(PORT,0));
 }
 private static boolean realWifi(Context s) {
  try{
   ConnectivityManager c=(ConnectivityManager)s.getSystemService(Context.CONNECTIVITY_SERVICE);
   if(c==null)return false;
   for(Network n:c.getAllNetworks()) {
    NetworkCapabilities caps=c.getNetworkCapabilities(n);
    if(caps!=null&&caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))return true;
   }
  }catch(RuntimeException ignored){}
  return false;
 }
 private static boolean quiet(Object adb)throws Exception {
  return field(adb,"quiet").getBoolean(adb);
 }
 private static boolean permitted(Object adb,boolean manual)throws Exception {
  if(manual)return true;
  SharedPreferences p=prefs(adb);
  return LoopbackRetryPolicy.autoAllowed(System.currentTimeMillis(),
    p.getLong(STARTED,0L),p.getInt(ATTEMPTS,0));
 }
 private static void countAuto(Object adb)throws Exception {
  SharedPreferences p=prefs(adb);
  long now=System.currentTimeMillis(),started=p.getLong(STARTED,0L);
  int count=p.getInt(ATTEMPTS,0);
  int active=LoopbackRetryPolicy.activeCount(now,started,count);
  p.edit().putLong(STARTED,active==0?now:started)
    .putInt(ATTEMPTS,active+1).apply();
 }
 /**
  * This is merely eligibility, not socket liveness. The original ADB client
  * must authenticate, and the 2-attempt circuit breaker prevents crash loops.
  */
 public static boolean transportAvailable(Object adb,boolean wifi) {
  if(wifi)return true;
  try{return cached(adb)>0&&permitted(adb,!quiet(adb));}
  catch(Exception unavailable){return false;}
 }
 /**
  * If Wi-Fi is present, leave mDNS discovery completely untouched.
  * Only on Wi-Fi loss, try the previously verified loopback port once.
  * Returning true consumes the legacy discover(I) action.
  */
 public static boolean discover(Object adb,int task) {
  try{
   if(realWifi(context(adb)))return false; // preserve original NSD behavior
   int port=cached(adb);
   if(port<=0||!permitted(adb,!quiet(adb)))return true;
   if(quiet(adb))countAuto(adb);
   Method connect=adb.getClass().getDeclaredMethod("connect",int.class,int.class);
   connect.setAccessible(true);
   connect.invoke(adb,task,port);
  }catch(Exception ignored){/* Fail closed; never scan or authorize. */}
  return true;
 }
 /**
  * This runs BEFORE finish(int) clears the short-lived local token.
  * Store the port only if that very token was accepted from shell UID by
  * VirtualBridgeProvider and the current launch generation is valid.
  */
 public static void rememberVerified(Object adb,int task) {
  try{
   if(!field(adb,"working").getBoolean(adb)
      ||field(adb,"generation").getInt(adb)!=task)return;
   Object token=field(adb,"localToken").get(adb);
   if(!(token instanceof String)||((String)token).isEmpty())return;
   int port=field(adb,"lastTriedPort").getInt(adb);
   if(!LoopbackRoutePolicy.validPort(port))return;
   Class<?> provider=Class.forName("jp.tapmarker.VirtualBridgeProvider");
   Method allowed=provider.getDeclaredMethod("localConnected",String.class);
   allowed.setAccessible(true);
   if(!Boolean.TRUE.equals(allowed.invoke(null,(String)token)))return;
   prefs(adb).edit().putInt(PORT,port).remove(STARTED).remove(ATTEMPTS).apply();
  }catch(Exception ignored){/* No new trusted endpoint on ABI mismatch. */}
 }
 private SafeLoopbackAdbBridge(){}
}
