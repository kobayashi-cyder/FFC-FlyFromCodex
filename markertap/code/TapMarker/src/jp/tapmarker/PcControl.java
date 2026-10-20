package jp.tapmarker;

import android.content.Context;
import android.content.Intent;
import android.os.*;
import android.util.Base64;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Shell-only PC commands delegate to the app's existing SplitPilot and Tapper. */
final class PcControl {
 static final int PROTOCOL=1;
 static PcControl current;
 final TapService s;
 final PcRunLease lease=new PcRunLease();
 final PcPlacementRequest placement=new PcPlacementRequest();
 String issue="";
 PcControl(TapService service){s=service;}
 static boolean ownsRun(TapService service){return current!=null&&current.s==service&&current.lease.owns(current.lease.token,service.pilot.virtualWindows.generation);}
 static PcControl forService(TapService service){if(current==null||current.s!=service)current=new PcControl(service);return current;}
 static boolean waitingForPc(TapService service){return current!=null&&current.s==service&&("waiting".equals(current.placement.state)||"claimed".equals(current.placement.state));}
 static String waitingSummary(TapService service){return "PC配置："+current.placement.packages.length+"画面 / PC接続待ち";}
 static void cancelPlacement(TapService service,String reason){if(current!=null&&current.s==service)current.placement.cancel(reason);}
 static void requestPlacement(TapService service,String[] packages){
  try{
   if(Build.VERSION.SDK_INT<30||android.os.Process.myUid()/100000!=0)throw new IllegalStateException("PC配置はAndroid11以降の所有者ユーザーで使用してください");
   PcControl pc=forService(service);
   pc.targets(new JSONObject().put("packages",new JSONArray(java.util.Arrays.asList(packages))));
   service.stopAll("PC配置へ切替");service.pilot.virtualWindows.close();
   pc.lease.clear();service.h.removeCallbacks(pc.watchdog);pc.issue="";
   String id=java.util.UUID.randomUUID().toString().replace("-","");
   pc.placement.request(id,packages,SystemClock.uptimeMillis());
   service.compactPanel(true);
   service.pilot.note("PC配置："+packages.length+"画面 / USBで接続し、PCのMultiScreen 2.6を起動してください（2分間待機）");
   service.h.postDelayed(()->{if(pc.placement.id.equals(id)&&pc.placement.expire(SystemClock.uptimeMillis()))service.pilot.note(pc.placement.error);},PcPlacementRequest.TIMEOUT_MS);
  }catch(Exception error){service.pilot.note("PC配置："+error.getMessage());}
 }
 JSONObject placementInfo()throws JSONException{
  placement.expire(SystemClock.uptimeMillis());
  return new JSONObject().put("id",placement.id).put("state",placement.state).put("packages",new JSONArray(java.util.Arrays.asList(placement.packages))).put("error",placement.error);
 }

 static Bundle call(String method,String argument,Context context){
  final String[] result=new String[1];
  CountDownLatch done=new CountDownLatch(1);
  AtomicBoolean cancelled=new AtomicBoolean();
  Handler main=new Handler(Looper.getMainLooper());
  main.post(()->{
   try{
    if(cancelled.get())return;
    TapService tap=TapService.instance;
    JSONObject reply;
    if("pc_info".equals(method)){
     reply=new JSONObject().put("protocol",PROTOCOL).put("version","1.89")
      .put("user",android.os.Process.myUid()/100000)
      .put("accessibility",tap!=null&&tap.pilot!=null)
      .put("backend",VirtualBridgeProvider.connected()!=null);
     if(tap!=null&&tap.pilot!=null)reply.put("placement",forService(tap).placementInfo());
     if(VirtualBridgeProvider.connected()!=null)reply.put("implementation",new VirtualProtocol(VirtualBridgeProvider.connected()).implementation());
    }else{
     if(tap==null||tap.pilot==null)throw new IllegalStateException("TapMaker（マーカー連打）のユーザー補助を有効にしてください");
     if(current==null||current.s!=tap)current=new PcControl(tap);
     JSONObject request=new JSONObject(new String(Base64.decode(argument,Base64.URL_SAFE|Base64.NO_WRAP),"UTF-8"));
     reply=current.command(method,request);
    }
    reply.put("ok",true);result[0]=reply.toString();
   }catch(Throwable error){try{result[0]=new JSONObject().put("ok",false).put("error",VirtualWindows.brief(error)).toString();}catch(JSONException ignored){result[0]="{\"ok\":false}";}}
   finally{done.countDown();}
  });
  try{if(!done.await(8,TimeUnit.SECONDS)){cancelled.set(true);result[0]="{\"ok\":false,\"error\":\"TapMakerの応答待ちが時間切れです。状態を再取得してください\"}";}}
  catch(InterruptedException error){Thread.currentThread().interrupt();result[0]="{\"ok\":false,\"error\":\"応答待ちが中断されました\"}";}
  Bundle bundle=new Bundle();
  try{bundle.putString("data",Base64.encodeToString(result[0].getBytes("UTF-8"),Base64.URL_SAFE|Base64.NO_WRAP));}catch(Exception ignored){}
  return bundle;
 }

 String[] targets(JSONObject request)throws Exception{
  JSONArray values=request.getJSONArray("packages");
  if(values.length()<1||values.length()>32)throw new IllegalArgumentException("対象アプリを1〜32個選択してください");
  LinkedHashSet<String> packages=new LinkedHashSet<>();
  for(int i=0;i<values.length();i++){
   String pkg=values.getString(i);
   if(!PcRunLease.validPackage(pkg)||!packages.add(pkg))throw new IllegalArgumentException("対象アプリの指定が無効です");
   Intent launch=s.getPackageManager().getLaunchIntentForPackage(pkg);
   if(launch==null)throw new IllegalArgumentException(pkg+"：このユーザーで起動できません");
   try{new SplitPilot.Slot(s,pkg);}catch(Exception error){throw new IllegalArgumentException(s.appLabel(pkg)+"："+error.getMessage()+"。このゲームの設定をTapMakerで確認するか、PCの選択から外してください");}
  }
  return packages.toArray(new String[0]);
 }

 JSONObject command(String method,JSONObject request)throws Exception{
  VirtualWindows windows=s.pilot.virtualWindows;
  String token=request.optString("token","");
  if(!PcRunLease.validToken(token))throw new IllegalArgumentException("PCの接続情報が無効です");
  String placementId=request.optString("placementId","");
  if("pc_take_placement".equals(method)){
   String[] packages=placement.take(placementId,token,SystemClock.uptimeMillis());
   JSONObject reply=new JSONObject().put("claimed",packages!=null);
   if(packages!=null)reply.put("packages",new JSONArray(java.util.Arrays.asList(packages)));
   return reply;
  }
  if("pc_finish_placement".equals(method)){
   String error=request.optString("error","");if(error.length()>1200)error=error.substring(0,1200);
   boolean recorded=placement.finish(placementId,token,error);
   if(recorded&&!error.isEmpty())s.pilot.note("PC配置："+error);
   return new JSONObject().put("recorded",recorded);
  }
  if("pc_cancel_placement".equals(method))return new JSONObject().put("cancelled",placement.cancel("PCから配置待機を取消しました"));
  if("pc_check".equals(method)){String[] packages=targets(request);return new JSONObject().put("count",packages.length);}
  if("pc_prepare".equals(method)){
   if(!placementId.isEmpty()&&!placement.matches(placementId,token,targets(request),SystemClock.uptimeMillis()))throw new IllegalStateException("PC配置は取り消されたか変更されました");
   if(placementId.isEmpty())placement.cancel("PC側で対象を選択しました");
   if(Build.VERSION.SDK_INT<30)throw new IllegalStateException("仮想画面への入力にはAndroid11以降が必要です");
   if(android.os.Process.myUid()/100000!=0)throw new IllegalStateException("PCからのTapMaker連携は所有者ユーザーで使用してください");
   if(windows.hasSession()&&!lease.owns(token,windows.generation))throw new IllegalStateException("スマホ側の仮想画面が使用中です。TapMakerで終了してから再試行してください");
   lease.prepare(token,SystemClock.uptimeMillis());issue="";
   if(VirtualBridgeProvider.connected()==null)VirtualBridgeProvider.prepareLocal(token);
   s.h.removeCallbacks(watchdog);s.h.postDelayed(watchdog,2000);
   return new JSONObject();
  }
  if("pc_stop".equals(method)&&!lease.matches(token))return new JSONObject().put("stopped",false);
  if(!lease.matches(token))throw new SecurityException("このPCが開始した処理ではありません");
  if("pc_status".equals(method)){lease.beat(token,SystemClock.uptimeMillis());return status(token);}
  if("pc_start".equals(method)){
   if(lease.expired)throw new IllegalStateException("PC接続待ちで停止しました。最初から再試行してください");
   String[] packages=targets(request);
   if(!placementId.isEmpty()&&!placement.matches(placementId,token,packages,SystemClock.uptimeMillis()))throw new IllegalStateException("PC配置は取り消されたか変更されました");
   if(windows.hasSession()&&!lease.owns(token,windows.generation))throw new IllegalStateException("スマホ側で別の仮想画面に切り替わりました");
   lease.beat(token,SystemClock.uptimeMillis());issue="";
   try{windows.openPc(packages);}finally{lease.started(windows.generation);}
   if(!windows.lastStartError.isEmpty())issue=windows.lastStartError;
   if(request.optBoolean("minimize",true)&&!windows.windows.isEmpty())windows.minimize(true);
   if(!placementId.isEmpty())placement.finish(placementId,token,"");
   return status(token);
  }
  if("pc_stop".equals(method)){
   boolean owned=lease.owns(token,windows.generation);
   if(owned){s.stopAll("PCから停止");if(request.optBoolean("close",false))windows.close();}
   if(request.optBoolean("close",false)){
    // A phone-side replacement run must retain its backend and windows.
    if(owned||lease.generation<0&&!windows.hasSession())VirtualBridgeProvider.cancelLocal(token);
    lease.clear();s.h.removeCallbacks(watchdog);
   }
   return new JSONObject().put("stopped",owned);
  }
  throw new IllegalArgumentException("未対応のPC操作です");
 }

 JSONObject status(String token)throws Exception{
  VirtualWindows windows=s.pilot.virtualWindows;
  boolean owned=lease.owns(token,windows.generation);
  boolean active=owned&&s.pilot.splitPilot.active&&s.pilot.splitPilot.virtualMode;
  JSONObject reply=new JSONObject().put("owned",owned).put("active",active).put("deviceModel",Build.MODEL).put("androidApi",Build.VERSION.SDK_INT).put("androidVersion",Build.VERSION.RELEASE).put("implementation",VirtualProtocol.IMPLEMENTATION)
   .put("parallel",active&&s.pilot.splitPilot.parallelVirtual)
   .put("preparing",owned&&windows.autoPending)
   .put("error",!issue.isEmpty()?issue:owned?windows.lastStartError:"")
   .put("message",owned?s.pilot.message:"PCの対象画面は未作成か、スマホ側で切り替え済みです");
  JSONArray rows=new JSONArray();long now=SystemClock.uptimeMillis();
  if(owned)for(VirtualWindows.Window window:windows.windows){
   rows.put(new JSONObject().put("package",window.pkg).put("display",window.id)
    .put("launched",window.launched).put("failed",window.failed||window.closed)
    .put("state",window.runState).put("inputs",window.inputs).put("automationPhase",window.inputRecovery.pending?"recovering":s.pilot.splitPilot.pcPhase(window.pkg))
    .put("frameAgeMs",window.frameAt>0?now-window.frameAt:-1)
    .put("error",window.error).put("inputError",window.inputError).put("previousInputError",window.lastInputError).put("frameIssue",window.frameIssue)
    .put("stopReason",window.stopReason).put("visiblePackage",windows.screen(window).pkg).put("inputTargetState",window.inputRecovery.status()).put("targetRestoreAttempts",window.inputRecovery.attempts));
  }
  return reply.put("windows",rows);
 }

 final Runnable watchdog=new Runnable(){public void run(){
  if(TapService.instance!=s||s.pilot==null||lease.token.isEmpty())return;
  if(lease.timedOut(SystemClock.uptimeMillis())){
   lease.expired=true;
   if(lease.owns(lease.token,s.pilot.virtualWindows.generation)){
    issue="PCとの通信が20秒以上途切れたため自動処理を停止しました";
    s.stopAll(issue);
   }else if(lease.generation<0){if(!s.pilot.virtualWindows.hasSession())VirtualBridgeProvider.cancelLocal(lease.token);lease.clear();return;}
  }
  s.h.postDelayed(this,2000);
 }};
}
