package jp.tapmarker;
import android.content.*;import android.hardware.display.*;import android.os.*;import android.view.*;import java.lang.reflect.*;import java.util.*;import java.util.concurrent.TimeUnit;
/** Runs only in a user-authorized Shizuku or ADB process. No arbitrary shell RPC. */
public final class VirtualShell extends Binder {
 volatile TargetedInput input;final Context context;final int controllerUid;final Map<Integer,Entry> displays=new java.util.concurrent.ConcurrentHashMap<>();volatile boolean armed,closed;volatile IBinder ownerHost;final java.util.concurrent.atomic.AtomicLong epoch=new java.util.concurrent.atomic.AtomicLong();
 static final class Entry {VirtualDisplay display;Surface surface;String component,pkg;int width,height,uid;volatile int id;}
 public VirtualShell(Context supplied)throws Exception{if(android.os.Process.myUid()!=2000&&android.os.Process.myUid()!=0)throw new SecurityException("許可されたシェルプロセスが必要です");context=ShellContext.create();controllerUid=context.getPackageManager().getApplicationInfo("jp.tapmarker.lite",0).uid;attachInterface(null,VirtualProtocol.DESCRIPTOR);}
 @Override protected boolean onTransact(int code,Parcel data,Parcel reply,int flags)throws RemoteException{
  if(code==INTERFACE_TRANSACTION){reply.writeString(VirtualProtocol.DESCRIPTOR);return true;}
  int caller=Binder.getCallingUid();if(caller!=controllerUid&&caller!=android.os.Process.myUid()&&caller!=0)throw new SecurityException("このAPK以外の入力を拒否");
  if(code==VirtualProtocol.DESTROY){closeAll();System.exit(0);return true;}
  data.enforceInterface(VirtualProtocol.DESCRIPTOR);
  try{
   switch(code){
    case VirtualProtocol.CREATE:{int w=data.readInt(),h=data.readInt(),dpi=data.readInt();String component=data.readString();Surface surface=Surface.CREATOR.createFromParcel(data);int id=create(w,h,dpi,component,surface);reply.writeNoException();reply.writeInt(id);return true;}
    case VirtualProtocol.LAUNCH:launch(data.readInt());reply.writeNoException();return true;
    case VirtualProtocol.RESTORE:{int id=data.readInt();long requestEpoch=data.readLong();boolean ok=false;Entry entry=owned(id);synchronized(entry){if(armed&&epoch.get()==requestEpoch){launch(id,Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);ok=true;}}reply.writeNoException();reply.writeInt(ok?1:0);return true;}
    case VirtualProtocol.RESTART_TASK:{int id=data.readInt();long requestEpoch=data.readLong();boolean ok=false;Entry entry=owned(id);synchronized(entry){if(armed&&epoch.get()==requestEpoch){launch(id,Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);ok=true;}}reply.writeNoException();reply.writeInt(ok?1:0);return true;}
    case VirtualProtocol.TAP:{int id=data.readInt();float x=data.readFloat(),y=data.readFloat();long duration=data.readLong(),requestEpoch=data.readLong();boolean ok;Entry entry=owned(id);synchronized(entry){ok=armed&&epoch.get()==requestEpoch&&tap(id,x,y,duration,requestEpoch);}reply.writeNoException();reply.writeInt(ok?1:0);return true;}
    case VirtualProtocol.GESTURE:{int id=data.readInt();float[] points=data.createFloatArray();long duration=data.readLong(),requestEpoch=data.readLong();boolean ok;Entry entry=owned(id);synchronized(entry){ok=armed&&epoch.get()==requestEpoch&&gesture(id,points,duration,requestEpoch);}reply.writeNoException();reply.writeInt(ok?1:0);return true;}
    case VirtualProtocol.BACK:{int id=data.readInt();long requestEpoch=data.readLong();boolean ok;Entry entry=owned(id);synchronized(entry){ok=armed&&epoch.get()==requestEpoch&&back(id);}reply.writeNoException();reply.writeInt(ok?1:0);return true;}
    case VirtualProtocol.RELEASE:release(data.readInt());reply.writeNoException();return true;
    case VirtualProtocol.PAUSE:armed=false;epoch.incrementAndGet();reply.writeNoException();return true;
    case VirtualProtocol.VERSION:reply.writeNoException();reply.writeInt(VirtualProtocol.IMPLEMENTATION);return true;
    case VirtualProtocol.REGISTER_OWNER:registerOwner(data.readStrongBinder());reply.writeNoException();return true;
    case VirtualProtocol.ARM:input();if(closed)throw new IllegalStateException("実行部は停止済みです");long version=epoch.incrementAndGet();armed=true;reply.writeNoException();reply.writeLong(version);return true;
    case VirtualProtocol.SHUTDOWN:closeAll();reply.writeNoException();new Handler(Looper.getMainLooper()).postDelayed(()->System.exit(0),100);return true;
    default:return super.onTransact(code,data,reply,flags);
   }
  }catch(Exception e){Throwable cause=e instanceof InvocationTargetException?((InvocationTargetException)e).getTargetException():e;reply.writeException(new IllegalStateException("仮想画面："+cause.getClass().getSimpleName()+" / "+cause.getMessage()));return true;}
 }
 /** A dead APK owner must not leave shell-owned displays or tap workers running.
  * If a native VirtualDisplay.release() blocks, hard-stop this shell process
  * after five seconds. Android then reclaims the shell-owned descriptors.
  * This never force-stops the game applications themselves.
  */
 void closeAfterOwnerDeath(){
  Thread watchdog=new Thread(()->{
   SystemClock.sleep(5000);
   android.os.Process.killProcess(android.os.Process.myPid());
  },"MarkerTap-owner-death-hard-stop");
  watchdog.setDaemon(true);watchdog.start();
  try{closeAll();}finally{
   android.os.Process.killProcess(android.os.Process.myPid());
   System.exit(0);
  }
 }
 synchronized void registerOwner(IBinder host)throws RemoteException{
  if(closed||host==null||!host.isBinderAlive())throw new IllegalStateException("所有アプリが切断されています");
  if(ownerHost==host)return;
  if(ownerHost!=null&&ownerHost.isBinderAlive())throw new SecurityException("別の生存中プロセスが所有しています");
  ownerHost=host;
  host.linkToDeath(this::closeAfterOwnerDeath,0);
 }
 int create(int w,int h,int dpi,String component,Surface surface)throws Exception{
  if(closed){surface.release();throw new IllegalStateException("実行部は停止済みです");}
  if(!VirtualResolutionPolicy.backendGeometry(w,h,dpi)||!surface.isValid()){surface.release();throw new IllegalArgumentException("表示サイズが無効です");}
  ComponentName name=ComponentName.unflattenFromString(component==null?"":component);if(name==null||!name.getPackageName().matches("[A-Za-z0-9_.]+")||!name.getClassName().matches("[A-Za-z0-9_.$]+")){surface.release();throw new IllegalArgumentException("起動アプリが無効です");}
  DisplayManager manager=(DisplayManager)context.getSystemService(Context.DISPLAY_SERVICE);
  // Own-content displays do not mirror the phone. Touch support is an AOSP flag.
  int displayFlags=VirtualDisplayPolicy.flags(Build.VERSION.SDK_INT,true);
  Entry e=new Entry();VirtualDisplay.Callback callback=new VirtualDisplay.Callback(){public void onStopped(){if(e.id>0)release(e.id);}};
  VirtualDisplay display=null;try{try{display=manager.createVirtualDisplay("TapMarker:"+component,w,h,dpi,surface,displayFlags,callback,new Handler(Looper.getMainLooper()));}catch(SecurityException denied){display=manager.createVirtualDisplay("TapMarker:"+component,w,h,dpi,surface,VirtualDisplayPolicy.flags(Build.VERSION.SDK_INT,false),callback,new Handler(Looper.getMainLooper()));}if(display==null)throw new IllegalStateException("仮想ディスプレイを作成できません");e.display=display;e.surface=surface;e.component=name.flattenToString();e.pkg=name.getPackageName();e.width=w;e.height=h;e.uid=context.getPackageManager().getApplicationInfo(name.getPackageName(),0).uid;if(e.uid<=0||e.uid==controllerUid)throw new IllegalArgumentException("入力先アプリが無効です");int id=display.getDisplay().getDisplayId();if(id<=0||!display.getDisplay().isValid())throw new IllegalStateException("本体画面への入力を拒否");e.id=id;displays.put(id,e);if(closed){release(id);throw new IllegalStateException("停止中の仮想画面生成を破棄しました");}return id;}catch(Exception failure){if(display!=null)display.release();surface.release();throw failure;}
 }
 Entry owned(int id){Entry e=displays.get(id);if(closed||e==null||id<=0||!e.display.getDisplay().isValid()||!e.surface.isValid())throw new IllegalArgumentException("このAPKの有効な仮想画面ではありません");return e;}
 void launch(int id)throws Exception{launch(id,Intent.FLAG_ACTIVITY_NEW_TASK);}
 void launch(int id,int flags)throws Exception{Entry e=owned(id);Intent intent=new Intent().setComponent(ComponentName.unflattenFromString(e.component)).addFlags(flags);android.app.ActivityManager manager=(android.app.ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);if(!manager.isActivityStartAllowedOnDisplay(context,id,intent))throw new SecurityException("仮想画面での起動を拒否 / 本体画面には起動しません");java.lang.Process process=new ProcessBuilder("/system/bin/am","start","--user","current","--display",String.valueOf(id),"-n",e.component,"-f","0x"+Integer.toHexString(flags)).redirectErrorStream(true).start();if(!process.waitFor(10,TimeUnit.SECONDS)){process.destroy();throw new IllegalStateException("アプリ起動がタイムアウト");}java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();byte[] b=new byte[1024];int n;while((n=process.getInputStream().read(b))>0)out.write(b,0,n);String result=out.toString("UTF-8");if(process.exitValue()!=0||result.contains("Error:")||result.contains("SecurityException"))throw new IllegalStateException(result.substring(0,Math.min(600,result.length())));}
 synchronized TargetedInput input()throws Exception{if(input!=null)return input;Class<?> klass;try{klass=Class.forName("android.hardware.input.InputManagerGlobal");}catch(ClassNotFoundException old){klass=Class.forName("android.hardware.input.InputManager");}try{TargetedInput.TargetGuard legacyGuard=null;if(Build.VERSION.SDK_INT>=30&&Build.VERSION.SDK_INT<=32){Class<?> managerType=Class.forName("android.app.ActivityTaskManager");Object tasks=managerType.getMethod("getService").invoke(null);DisplayTaskGuard guard=new DisplayTaskGuard(tasks,Class.forName("android.app.IActivityTaskManager"));legacyGuard=(id,uid)->{Entry entry=owned(id);return entry.uid==uid&&guard.accepts(id,uid,entry.pkg);};}input=new TargetedInput(klass,InputEvent.class,legacyGuard);}catch(NoSuchMethodException unsupported){throw new IllegalStateException("このAndroidでは対象画面を確認した仮想入力を準備できません",unsupported);}return input;}
 void display(InputEvent event,int id)throws Exception{input().display(event,id);}
 boolean inject(InputEvent event,int id,int uid)throws Exception{return input().inject(event,id,uid);}
 boolean tap(int id,float x,float y,long duration,long requestEpoch)throws Exception{
  Entry e=owned(id);if(!Float.isFinite(x)||!Float.isFinite(y)||x<0||y<0||x>=e.width||y>=e.height||duration<1||duration>1000)throw new IllegalArgumentException("仮想画面外のタップ");
  float[] point=DiscreteTapPosition.point(x,y,new int[]{0,0,e.width,e.height});
  return new DiscreteTouch(new DiscreteTouch.Clock(){public long now(){return SystemClock.uptimeMillis();}public void sleep(long millis){SystemClock.sleep(millis);}},(down,action,px,py)->motion(id,e.uid,down,action,new float[]{px,py})).tap(point[0],point[1],duration,()->armed&&epoch.get()==requestEpoch&&!closed&&displays.get(id)==e);
 }
 boolean gesture(int id,float[] points,long duration,long requestEpoch)throws Exception{
  Entry e=owned(id);GesturePath path=new GesturePath(points,e.width,e.height,duration);if(points.length==2&&duration<=1000)return tap(id,points[0],points[1],duration,requestEpoch);long down=SystemClock.uptimeMillis();float[] position=path.position(0);boolean sent=false,complete=false,released=false;
  try{sent=motion(id,e.uid,down,MotionEvent.ACTION_DOWN,position);if(!sent)return false;while(SystemClock.uptimeMillis()-down<duration){if(!armed||epoch.get()!=requestEpoch||closed||!displays.containsKey(id))return false;long elapsed=SystemClock.uptimeMillis()-down;position=path.position(elapsed/(float)duration);if(points.length>2&&!motion(id,e.uid,down,MotionEvent.ACTION_MOVE,position))return false;SystemClock.sleep(Math.min(16,Math.max(1,duration-elapsed)));}if(!armed||epoch.get()!=requestEpoch)return false;position=path.position(1);complete=true;
  }finally{if(sent)released=motion(id,e.uid,down,complete?MotionEvent.ACTION_UP:MotionEvent.ACTION_CANCEL,position);}
  return complete&&released;
 }
 boolean motion(int id,int uid,long down,int action,float[] point)throws Exception{
  MotionEvent.PointerProperties finger=new MotionEvent.PointerProperties();finger.id=0;finger.toolType=MotionEvent.TOOL_TYPE_FINGER;
  MotionEvent.PointerCoords contact=new MotionEvent.PointerCoords();contact.x=point[0];contact.y=point[1];contact.pressure=action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL?0:1;contact.size=1;
  MotionEvent event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,1,new MotionEvent.PointerProperties[]{finger},new MotionEvent.PointerCoords[]{contact},0,0,1,1,0,0,InputDevice.SOURCE_TOUCHSCREEN,0);
  try{display(event,id);return inject(event,id,uid);}finally{event.recycle();}
 }
 boolean back(int id)throws Exception{Entry e=owned(id);long now=SystemClock.uptimeMillis();KeyEvent down=new KeyEvent(now,now,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BACK,0);KeyEvent up=new KeyEvent(now,now,KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BACK,0);display(down,id);display(up,id);if(!inject(down,id,e.uid))return false;return inject(up,id,e.uid);}
 void release(int id){Entry e=displays.remove(id);if(e!=null){try{e.display.release();}finally{e.surface.release();}}}
 // Each display must be released even if another one throws during cleanup.
 void closeAll(){closed=true;armed=false;epoch.incrementAndGet();for(Integer id:new ArrayList<>(displays.keySet()))try{release(id);}catch(Throwable ignored){}}

}
