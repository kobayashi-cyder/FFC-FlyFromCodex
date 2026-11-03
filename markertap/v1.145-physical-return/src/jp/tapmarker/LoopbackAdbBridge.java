package jp.tapmarker;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.IBinder;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
/**
 * Runtime compatibility bridge for MarkerTap v1.153 DEX.
 *
 * The caller's existing ADB implementation performs its usual RSA/TLS
 * authentication against 127.0.0.1; the bridge NEVER starts adbd, grants
 * Android permissions, scans port ranges or connects to remote hosts.
 */
public final class LoopbackAdbBridge {
 private static final String PREFS="wirelessAdb";
 private static final String KEY="trustedLocalPort";
 private static final String PAIRED="paired";
 private static Field find(Class<?> c,String name)throws Exception {
  for(Class<?> p=c;p!=null;p=p.getSuperclass()){
   try{Field f=p.getDeclaredField(name);f.setAccessible(true);return f;}
   catch(NoSuchFieldException ignore){}
  }
  throw new NoSuchFieldException(name);
 }
 private static Context context(Object adb)throws Exception{
  Object s=find(adb.getClass(),"s").get(adb);
  if(!(s instanceof Context))throw new SecurityException("no app context");
  return (Context)s;
 }
 private static SharedPreferences prefs(Object adb)throws Exception{
  return context(adb).getSharedPreferences(PREFS,0);
 }
 private static int cached(Object adb)throws Exception{
  SharedPreferences p=prefs(adb);
  return LoopbackRoutePolicy.eligiblePort(p.getBoolean(PAIRED,false),
      p.getInt(KEY,0));
 }
 private static boolean realWifi(Context ctx){
  try{
   ConnectivityManager cm=(ConnectivityManager)ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
   if(cm==null)return false;
   for(Network n:cm.getAllNetworks()){
    NetworkCapabilities c=cm.getNetworkCapabilities(n);
    if(c!=null&&c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))return true;
   }
  }catch(Exception ignored){}
  return false;
 }
 /**
  * Called AFTER the v1.153 wifiConnected() body. It is deliberately a
  * conservative eligibility test, not proof the offline port is listening.
  * The original ADB authentication/launch will fail closed if it is stale.
  */
 public static boolean transportAvailable(Object adb,boolean wifi){
  if(wifi)return true;
  try{return cached(adb)>0;}catch(Exception unavailable){return false;}
 }
 /**
  * Called instead of/alongside NSD discovery. The cached port is ONLY a
  * verified local endpoint previously saved by rememberVerified().
  * Wifi present: continue original mDNS as fallback for rotated ADB ports.
  * Wifi absent: do not start network service discovery.
  */
 public static boolean discover(Object adb,int task){
  boolean wifi=false;
  try{
   wifi=realWifi(context(adb));
   int port=cached(adb);
   if(port>0){
    Method connect=adb.getClass().getDeclaredMethod("connect",int.class,int.class);
    connect.setAccessible(true);
    connect.invoke(adb,task,port);
   }
  }catch(Exception failure){
   // Keep fallback when Wi-Fi is present; otherwise fail closed.
  }
  return !wifi;
 }
 /**
  * Called only AFTER the baseline finish(int) completed. The port is
  * persisted only if a live shell-UID Binder registration was authenticated.
  */
 public static void rememberVerified(Object adb){
  try{
   Class<?> provider=Class.forName("jp.tapmarker.VirtualBridgeProvider");
   Method connected=provider.getDeclaredMethod("connected");
   connected.setAccessible(true);
   Object binder=connected.invoke(null);
   if(!(binder instanceof IBinder)||!((IBinder)binder).isBinderAlive())return;
   int port=find(adb.getClass(),"lastTriedPort").getInt(adb);
   if(!LoopbackRoutePolicy.validPort(port))return;
   prefs(adb).edit().putInt(KEY,port).apply();
  }catch(Exception unavailable){/* no implicit trust on any failure */}
 }
 private LoopbackAdbBridge(){}
}
