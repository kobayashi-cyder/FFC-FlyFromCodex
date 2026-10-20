package jp.tapmarker;
import android.content.Context;import android.os.*;import java.lang.reflect.InvocationTargetException;
/** Entry point launched explicitly with adb shell app_process, without Shizuku. */
public final class VirtualBootstrap {
 public static void main(String[] arguments){
  VirtualShell backend=null;
  try{
   if(android.os.Process.myUid()!=2000&&android.os.Process.myUid()!=0)throw new SecurityException("ADBのシェルから起動してください");
   String localToken=null;
   if(arguments.length==2&&"--detached".equals(arguments[0])){if(!LocalLaunch.validToken(arguments[1]))throw new IllegalArgumentException("起動情報が無効です");android.system.Os.setsid();localToken=arguments[1];}
   if(Looper.getMainLooper()==null)Looper.prepareMainLooper();
   Context shell=ShellContext.create();
   if(arguments.length==1&&"--stop".equals(arguments[0])){ShellProvider.call(shell,"stop",null);System.out.println("TAPMARKER_ADB_STOPPED");return;}
   backend=new VirtualShell(shell);
   final VirtualShell running=backend;
   Bundle extras=new Bundle();extras.putInt("version",VirtualBridgeProvider.VERSION);extras.putBinder("service",running);
   if(localToken!=null)extras.putString("localToken",localToken);
   Bundle result=ShellProvider.call(shell,"connect",extras);
   if(result==null)throw new IllegalStateException("APKから接続確認を取得できません");
   if(result.getBoolean("alreadyConnected")){System.out.println("TAPMARKER_ADB_ALREADY_CONNECTED");return;}
   IBinder host=result.getBinder("host");if(host==null)throw new IllegalStateException("APKの接続監視を開始できません");
   host.linkToDeath(()->{try{running.closeAll();}finally{System.exit(0);}},0);
   Runtime.getRuntime().addShutdownHook(new Thread(()->running.closeAll(),"tapmarker-cleanup"));
   System.out.println("TAPMARKER_ADB_CONNECTED");System.out.flush();
   Looper.loop();
  }catch(Throwable failure){
   Throwable cause=failure instanceof InvocationTargetException?((InvocationTargetException)failure).getTargetException():failure;
   System.err.println("TAPMARKER_ADB_ERROR: "+cause.getClass().getSimpleName()+": "+cause.getMessage());cause.printStackTrace(System.err);
   if(backend!=null)backend.closeAll();System.exit(1);
  }finally{if(backend!=null)backend.closeAll();}
 }
}
