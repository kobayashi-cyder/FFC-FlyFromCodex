package jp.tapmarker;
import android.app.ActivityManager;import android.content.Context;import android.os.*;import java.lang.reflect.*;
/** app_process has no registered ApplicationThread; acquire providers as a shell client. */
final class ShellProvider {
 static Bundle call(Context shell,String method,Bundle extras)throws Exception{
  Class<?> managerType=Class.forName("android.app.IActivityManager");
  Object manager=ActivityManager.class.getMethod("getService").invoke(null);
  IBinder token=new Binder();int user=android.os.Process.myUid()/100000;
  Object holder=managerType.getMethod("getContentProviderExternal",String.class,int.class,IBinder.class,String.class).invoke(manager,VirtualBridgeProvider.AUTHORITY,user,token,"TapMarker");
  if(holder==null)throw new IllegalStateException("v1.52以降のAPKをインストールし、ユーザー補助を有効にしてください");
  try{
   Object provider=holder.getClass().getField("provider").get(holder);
   if(provider==null)throw new IllegalStateException("APKのADB接続窓口を取得できません");
   Class<?> type=Class.forName("android.content.IContentProvider");
   for(Method call:type.getMethods())if(call.getName().equals("call")){
    Class<?>[] parameters=call.getParameterTypes();
    if(parameters.length==5&&parameters[0].getName().equals("android.content.AttributionSource"))return (Bundle)call.invoke(provider,shell.getAttributionSource(),VirtualBridgeProvider.AUTHORITY,method,null,extras);
    if(parameters.length==6&&parameters[0]==String.class)return (Bundle)call.invoke(provider,"com.android.shell",null,VirtualBridgeProvider.AUTHORITY,method,null,extras);
    if(parameters.length==5&&parameters[0]==String.class)return (Bundle)call.invoke(provider,"com.android.shell",VirtualBridgeProvider.AUTHORITY,method,null,extras);
   }
   throw new IllegalStateException("このAndroidのADB接続APIに対応していません");
  }finally{managerType.getMethod("removeContentProviderExternalAsUser",String.class,IBinder.class,int.class).invoke(manager,VirtualBridgeProvider.AUTHORITY,token,user);}
 }
}
