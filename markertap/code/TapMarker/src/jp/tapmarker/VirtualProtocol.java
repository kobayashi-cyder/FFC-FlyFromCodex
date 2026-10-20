package jp.tapmarker;
import android.os.*;import android.view.Surface;
/** Private RPC to the user-authorized Shizuku or ADB shell process. */
final class VirtualProtocol {
 static final String DESCRIPTOR="jp.tapmarker.lite.VirtualShell";
 static final int CREATE=1,LAUNCH=2,TAP=3,BACK=4,RELEASE=5,PAUSE=6,ARM=7,SHUTDOWN=8,VERSION=9,RESTORE=10,GESTURE=11,RESTART_TASK=12,REGISTER_OWNER=13,DESTROY=16777115;
 // Revision 91 adds a bounded, display-local task restart after a positively
 // identified playable ad survives Back and the ordinary registered return.
 static final int IMPLEMENTATION=92;
 final IBinder binder;
 VirtualProtocol(IBinder b){binder=b;}
 Parcel call(int code,Parcel data)throws RemoteException{Parcel reply=Parcel.obtain();try{if(!binder.transact(code,data,reply,0))throw new RemoteException("仮想画面サービスが応答しません");reply.readException();return reply;}catch(RuntimeException|RemoteException e){reply.recycle();throw e;}finally{data.recycle();}}
 Parcel request(){Parcel d=Parcel.obtain();d.writeInterfaceToken(DESCRIPTOR);return d;}
 int create(Surface surface,int width,int height,int density,String component)throws RemoteException{Parcel d=request();d.writeInt(width);d.writeInt(height);d.writeInt(density);d.writeString(component);surface.writeToParcel(d,0);Parcel r=call(CREATE,d);try{return r.readInt();}finally{r.recycle();}}
 boolean restore(int id,long epoch)throws RemoteException{Parcel d=request();d.writeInt(id);d.writeLong(epoch);Parcel r=call(RESTORE,d);try{return r.readInt()!=0;}finally{r.recycle();}}
 boolean restore(int id,long epoch,boolean restart)throws RemoteException{if(!restart)return restore(id,epoch);Parcel d=request();d.writeInt(id);d.writeLong(epoch);Parcel r=call(RESTART_TASK,d);try{return r.readInt()!=0;}finally{r.recycle();}}
 void launch(int id)throws RemoteException{Parcel d=request();d.writeInt(id);call(LAUNCH,d).recycle();}
 boolean tap(int id,float x,float y,long duration,long epoch)throws RemoteException{Parcel d=request();d.writeInt(id);d.writeFloat(x);d.writeFloat(y);d.writeLong(duration);d.writeLong(epoch);Parcel r=call(TAP,d);try{return r.readInt()!=0;}finally{r.recycle();}}
 boolean gesture(int id,float[] points,long duration,long epoch)throws RemoteException{Parcel d=request();d.writeInt(id);d.writeFloatArray(points);d.writeLong(duration);d.writeLong(epoch);Parcel r=call(GESTURE,d);try{return r.readInt()!=0;}finally{r.recycle();}}
 boolean back(int id,long epoch)throws RemoteException{Parcel d=request();d.writeInt(id);d.writeLong(epoch);Parcel r=call(BACK,d);try{return r.readInt()!=0;}finally{r.recycle();}}
 void release(int id)throws RemoteException{Parcel d=request();d.writeInt(id);call(RELEASE,d).recycle();}
 void pause()throws RemoteException{call(PAUSE,request()).recycle();}
 long arm()throws RemoteException{Parcel r=call(ARM,request());try{return r.readLong();}finally{r.recycle();}}
 int implementation()throws RemoteException{Parcel r=call(VERSION,request());try{return r.readInt();}finally{r.recycle();}}
 void registerOwner(IBinder host)throws RemoteException{Parcel d=request();d.writeStrongBinder(host);call(REGISTER_OWNER,d).recycle();}
 // One-way termination must work even when the app's main thread has crashed.
 void destroyAsync()throws RemoteException{Parcel d=request();try{if(!binder.transact(DESTROY,d,null,IBinder.FLAG_ONEWAY))throw new RemoteException("実行部の終了要求を送れません");}finally{d.recycle();}}
 void shutdown()throws RemoteException{call(SHUTDOWN,request()).recycle();}
}
