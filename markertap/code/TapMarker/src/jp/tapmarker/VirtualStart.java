package jp.tapmarker;
import android.content.Context;import android.os.*;import java.io.File;
/** Starts a shell-UID child and confirms its Binder before the ADB session exits. */
public final class VirtualStart {
 public static void main(String[] args){java.lang.Process child=null;boolean confirmed=false;try{
  if(android.os.Process.myUid()!=2000&&android.os.Process.myUid()!=0)throw new SecurityException("ADBから起動してください");
  if(args.length!=2)throw new IllegalArgumentException("起動情報がありません");LocalLaunch.command(args[0],args[1]);
  if(Looper.getMainLooper()==null)Looper.prepareMainLooper();Context context=ShellContext.create();
  ProcessBuilder builder=new ProcessBuilder("/system/bin/app_process","-Djava.class.path="+args[0],"/system/bin","--nice-name=tapmarker_lite_virtual","jp.tapmarker.VirtualBootstrap","--detached",args[1]);
  File nothing=new File("/dev/null");builder.redirectInput(nothing);builder.redirectOutput(nothing);builder.redirectError(nothing);child=builder.start();
  Bundle query=new Bundle();query.putString("localToken",args[1]);long until=SystemClock.uptimeMillis()+10000;
  while(SystemClock.uptimeMillis()<until){Bundle status=ShellProvider.call(context,"status",query);if(status!=null&&status.getBoolean("connected")){confirmed=true;System.out.println("TAPMARKER_ADB_CONNECTED");System.out.flush();return;}SystemClock.sleep(100);}
  throw new IllegalStateException("実行部の起動を確認できません");
 }catch(Throwable error){if(child!=null)child.destroy();System.err.println("TAPMARKER_ADB_ERROR: "+error.getClass().getSimpleName());System.exit(1);}finally{if(!confirmed&&child!=null)child.destroy();}}
}
