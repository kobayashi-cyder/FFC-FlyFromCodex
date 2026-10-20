package jp.tapmarker;
import android.app.Application;
import android.os.Process;

/** Fail closed on unhandled app exceptions, while preserving Android's crash logging. */
public final class CrashGuardApplication extends Application {
 @Override public void onCreate(){
  super.onCreate();
  final Thread.UncaughtExceptionHandler original=Thread.getDefaultUncaughtExceptionHandler();
  Thread.setDefaultUncaughtExceptionHandler((thread,problem)->{
   try{
    // A later process must not silently resume wireless-ADB sessions.
    getSharedPreferences("wirelessAdb",0).edit().putBoolean("fatalStopRequired",true).commit();
   }catch(Throwable ignored){}
   try{VirtualBridgeProvider.emergencyStop();}catch(Throwable ignored){}
   try{
    if(original!=null)original.uncaughtException(thread,problem);
   }finally{
    // In case a custom previous handler returned, terminate so binder owner
    // death also shuts down Shizuku/ADB processes and their virtual displays.
    Process.killProcess(Process.myPid());
    System.exit(10);
   }
  });
 }
}
