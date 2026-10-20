package jp.tapmarker;
import android.content.*;import android.database.Cursor;import android.net.Uri;import android.os.*;
/** Accepts an ADB-started Binder only from UID shell/root, never from another app. */
public final class VirtualBridgeProvider extends ContentProvider {
 static final String AUTHORITY="jp.tapmarker.lite.virtualbridge";
 static final int VERSION=2;private static final Object LOCK=new Object();private static final IBinder HOST=new Binder();
 private static IBinder service;
 private static String expectedLocalToken,serviceLocalToken;
 static void prepareLocal(String token){if(!LocalLaunch.validToken(token))throw new IllegalArgumentException();synchronized(LOCK){expectedLocalToken=token;}}
 static void finishLocal(String token){synchronized(LOCK){if(token!=null&&token.equals(expectedLocalToken))expectedLocalToken=null;}}
 static void cancelLocal(String token){IBinder old=null;synchronized(LOCK){if(token!=null&&token.equals(expectedLocalToken))expectedLocalToken=null;if(token!=null&&token.equals(serviceLocalToken)){old=service;service=null;serviceLocalToken=null;}}if(old!=null)try{new VirtualProtocol(old).shutdown();}catch(RemoteException ignored){}}
 static IBinder connected(){synchronized(LOCK){return service!=null&&service.isBinderAlive()?service:null;}}
 static void forget(IBinder expected){synchronized(LOCK){if(service==expected){service=null;serviceLocalToken=null;expectedLocalToken=null;}}}
 static boolean localConnected(String token){synchronized(LOCK){return token!=null&&token.equals(serviceLocalToken)&&service!=null&&service.isBinderAlive();}}
 static boolean localDetached(){synchronized(LOCK){return serviceLocalToken!=null&&service!=null&&service.isBinderAlive();}}
 static void closeLocal(){String token;synchronized(LOCK){token=serviceLocalToken;}cancelLocal(token);}
 @Override public boolean onCreate(){return true;}
 @Override public Bundle call(String method,String argument,Bundle extras){
  int uid=Binder.getCallingUid();if(uid!=2000&&uid!=0)throw new SecurityException("ADBからの接続だけを受け付けます");
  if(method!=null&&method.startsWith("pc_"))return PcControl.call(method,argument,getContext());
  if("status".equals(method)){Bundle reply=new Bundle();String token=extras==null?null:extras.getString("localToken");synchronized(LOCK){reply.putBoolean("connected",token!=null&&token.equals(serviceLocalToken)&&service!=null&&service.isBinderAlive());}return reply;}
  if("stop".equals(method)){IBinder old;synchronized(LOCK){old=service;service=null;serviceLocalToken=null;expectedLocalToken=null;}if(old!=null)try{new VirtualProtocol(old).shutdown();}catch(RemoteException ignored){}return new Bundle();}
  if(!"connect".equals(method)||extras==null||extras.getInt("version")!=VERSION)throw new IllegalArgumentException("ADB実行部とAPKのバージョンが一致しません");
  IBinder incoming=extras.getBinder("service");
  try{if(incoming==null||!incoming.isBinderAlive()||!VirtualProtocol.DESCRIPTOR.equals(incoming.getInterfaceDescriptor()))throw new IllegalArgumentException("仮想画面の実行部が無効です");}catch(RemoteException e){throw new IllegalStateException("ADB実行部が終了しました",e);}
  Bundle reply=new Bundle();synchronized(LOCK){
   String token=extras.getString("localToken");if(token!=null&&!token.equals(expectedLocalToken))throw new SecurityException("接続準備は取り消されました");
   if(service!=null&&service.isBinderAlive()){reply.putBoolean("alreadyConnected",true);return reply;}
   service=incoming;serviceLocalToken=token;try{incoming.linkToDeath(()->{synchronized(LOCK){if(service==incoming){service=null;serviceLocalToken=null;}}},0);}catch(RemoteException e){service=null;serviceLocalToken=null;throw new IllegalStateException("ADB接続が終了しました",e);}
  }
  reply.putBinder("host",HOST);
  TapService tap=TapService.instance;if(tap!=null)tap.h.post(()->{if(tap.pilot!=null)tap.pilot.note("ADB接続済み / 「仮想画面（ADB接続）」を選択してください");});
  return reply;
 }
 @Override public Cursor query(Uri uri,String[] projection,String selection,String[] arguments,String order){throw new UnsupportedOperationException();}
 @Override public String getType(Uri uri){return null;}
 @Override public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException();}
 @Override public int delete(Uri uri,String selection,String[] arguments){throw new UnsupportedOperationException();}
 @Override public int update(Uri uri,ContentValues values,String selection,String[] arguments){throw new UnsupportedOperationException();}
}
