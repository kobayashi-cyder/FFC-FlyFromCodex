package jp.tapmarker;
import android.app.*;import android.accessibilityservice.*;import android.content.*;import android.content.pm.PackageManager;import android.graphics.*;import android.hardware.display.*;import android.media.*;import android.os.*;import android.util.*;import android.view.*;import android.view.accessibility.*;import android.widget.*;import java.nio.ByteBuffer;import java.util.*;import java.util.concurrent.*;import rikka.shizuku.Shizuku;
/** Each display renders to its own ImageReader; resizing the preview never removes a display. */
final class VirtualWindows {
 final TapService s;final VirtualFrameQueue frameQueue=new VirtualFrameQueue();boolean framePumpScheduled;final ArrayList<Window> windows=new ArrayList<>();volatile int generation;volatile VirtualProtocol remote;
 final FrameCircuit frameCircuit=new FrameCircuit(SystemClock::uptimeMillis);
 final ThreadPoolExecutor frameWork=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<Runnable>(8),task->new Thread(task,"TapMarker-frame-circuit"));
 // A screenshot buffer must not wait behind up to four expensive recognition
 // requests before being copied, otherwise a fresh frame expires in the queue.
 final ExecutorService frameCopyWork=Executors.newSingleThreadExecutor(task->new Thread(task,"TapMarker-frame-copy"));
 boolean selected,pending,connecting,bound,adbMode,destroyed,pcPresentation,fatalClosing;volatile boolean autoPending;String[] requested;long remoteEpoch;VirtualPreparation preparation;
 final BackendRecoveryBudget recoveryBudget=new BackendRecoveryBudget();
 boolean recoverPending,recoverRun,recoverMinimized;String[] recoverTargets;
 int previewPercent;boolean panelHeaders;VirtualWindowPanel headings;
 String lastStop="開始前",lastStartError="";final ArrayDeque<String> events=new ArrayDeque<>();
 void record(String message){String time=new java.text.SimpleDateFormat("HH:mm:ss",java.util.Locale.JAPAN).format(new java.util.Date());events.addLast(time+" "+message);while(events.size()>40)events.removeFirst();s.getSharedPreferences("virtualDiagnostics",0).edit().putString("events",eventText()).apply();}
 String eventText(){StringBuilder result=new StringBuilder();for(String event:events)result.append(event).append('\n');return result.toString();}
 void stopped(String reason,StackTraceElement origin){lastStop=reason;if(hasSession())record("停止："+reason+(origin==null?"":" / "+origin.getClassName()+"."+origin.getMethodName()+":"+origin.getLineNumber()));}
 void startFailed(String reason){if(s.progressErrors!=null)s.progressErrors.fatal("",null,"START_FAILED",reason);lastStartError=reason;record("開始不可："+reason);s.pilot.note("開始不可："+reason);}
 void started(){lastStartError="";lastStop="なし（実行中）";for(Window w:windows){w.inputError="";w.stopReason="";w.inputRecovery.reset();state(w,"実行準備");}record("仮想"+windows.size()+"画面で自律開始");s.pilot.note(windows.size()+"個の仮想画面で自律開始 / ゲーム画面を再確認");}
 static String brief(Throwable error){while(error instanceof java.lang.reflect.InvocationTargetException&&error.getCause()!=null)error=error.getCause();String text=error.getClass().getSimpleName()+"："+(error.getMessage()==null?"入力エラー":error.getMessage());return text.length()>400?text.substring(0,400):text;}
 void inputFailed(Window w,String reason){if(!reason.equals(w.inputError))record("#"+w.id+" "+s.appLabel(w.pkg)+" 入力拒否："+reason);w.inputError=reason;w.lastInputError=reason;w.inputAt=SystemClock.uptimeMillis();if(InputTargetRecovery.changed(reason)){w.inputRecovery.rejected(w.inputAt);w.screenInfo=null;}state(w,w.inputRecovery.pending?w.inputRecovery.status():"入力拒否");s.pilot.note(s.appLabel(w.pkg)+"："+(w.inputRecovery.pending?w.inputRecovery.status():"入力拒否 / 「状況」で理由を確認できます"));}
 String report(){StringBuilder result=new StringBuilder("MarkerTap v"+ProgressErrors.version(s)+"\n");result.append("端末：").append(Build.MODEL).append(" / Android ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n").append("入力方式：").append(remote==null?"標準ジェスチャー":Build.VERSION.SDK_INT<=32?"対象画面確認つき旧Android入力":"アプリUID指定入力").append("\n");result.append("負荷設定：").append(s.performance.describe()).append("\n").append(summary()).append("\n開始不可：").append(lastStartError.isEmpty()?"なし":lastStartError).append("\n最後の停止理由：").append(lastStop).append('\n');long now=SystemClock.uptimeMillis();for(Window w:windows){Screen info=screen(w);result.append("\n#").append(w.id).append(' ').append(s.appLabel(w.pkg)).append("\n起動解像度：").append(w.width).append('x').append(w.height).append(" / ").append(w.density).append(" dpi / 指定幅 ").append(w.resolutionWidth).append("\n領域コピー：").append(w.roiCopiedPixels).append(" pixels / 監視範囲 ").append(w.roiCoveredPixels).append(" / 全体 ").append((long)w.width*w.height).append("\n表示パッチ：更新 ").append(w.preview.changedPatches).append(" / 再利用 ").append(w.preview.reusedPatches).append("\nプレビューグラフ：").append(w.preview.graph.summary()).append("\n状態：").append(w.runState).append("\n画像：").append(w.frameAt>0?(now-w.frameAt)+"ms前":"未取得").append("\n表示情報：").append(info.pkg.isEmpty()?"取得できません":info.pkg).append("\n").append(s.pilot.splitPilot.tapperReport(w.pkg)).append("\n入力完了：").append(w.inputs).append("回\n入力拒否：").append(w.inputError.isEmpty()?"なし":w.inputError).append("\nこの画面の停止理由：").append(w.stopReason.isEmpty()?"なし":w.stopReason).append("\n起動エラー：").append(w.error.isEmpty()?"なし":w.error).append('\n');}result.append("\n処理回路：8 nodes / worker=1 / workspace=").append(String.format(java.util.Locale.ROOT,"%.2f MiB",frameCircuit.retainedBytes/1048576.0)).append(" / complete=").append(frameCircuit.completed).append(" / cancelled=").append(frameCircuit.cancelled).append("\n").append(s.inputReport()).append("\n開始・停止の履歴\n").append(eventText());String prior=s.getSharedPreferences("virtualDiagnostics",0).getString("previousEvents","");if(!prior.isEmpty())result.append("\n前回の履歴\n").append(prior);return result.toString();}
 void showStatus(){String text=report();TextView status=new UiStrings.DisplayText(s);status.setText(text);status.setTextIsSelectable(true);status.setTextSize(13);status.setPadding(20,16,20,16);ScrollView scroll=new ScrollView(s);scroll.addView(status);AlertDialog dialog=new UiStrings.DialogBuilder(s).setTitle("入力と自動実行の状況").setView(scroll).setPositiveButton("閉じる",null).setNeutralButton("コピー",(d,which)->{((android.content.ClipboardManager)s.getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("入力と自動実行の状況",text));}).create();dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);dialog.show();}
 final DisplayManager displayManager;final DisplayManager.DisplayListener displayListener=new DisplayManager.DisplayListener(){public void onDisplayAdded(int id){}public void onDisplayChanged(int id){}public void onDisplayRemoved(int id){for(Window w:windows)if(w.id==id&&!w.closed){backendLost();return;}}};
 final Shizuku.UserServiceArgs args;final Shizuku.OnRequestPermissionResultListener permissions;
 VirtualWindows(TapService service){s=service;previewPercent=VirtualPreview.percent(s.getSharedPreferences("virtualPreview",0).getInt("percent",100));panelHeaders=s.getSharedPreferences("virtualPreview",0).getBoolean("panelHeaders",true);android.content.SharedPreferences history=s.getSharedPreferences("virtualDiagnostics",0);String previous=history.getString("events","");if(!previous.isEmpty())history.edit().putString("previousEvents",previous).apply();displayManager=(DisplayManager)s.getSystemService(Context.DISPLAY_SERVICE);if(displayManager!=null)displayManager.registerDisplayListener(displayListener,s.h);args=new Shizuku.UserServiceArgs(new ComponentName(s,VirtualShell.class)).daemon(false).processNameSuffix("virtual").tag("tapmarker-lite-virtual").version(VirtualProtocol.IMPLEMENTATION);permissions=(code,result)->{if(code==5101&&pending){pending=false;if(result==PackageManager.PERMISSION_GRANTED)bind();else s.pilot.note("仮想画面の追加権限は許可されませんでした");}};Shizuku.addRequestPermissionResultListener(permissions);}
 final class Window {
  final String pkg;final ExecutorService io=Executors.newSingleThreadExecutor();final Object frameLock=new Object();final DisplayLane inputLane=new DisplayLane();final InputTargetRecovery inputRecovery=new InputTargetRecovery();
  final RestoreReceipt restoreReceipt=new RestoreReceipt();
  volatile int id=-1;int width,height,density,startIndex,laneCount,resolutionWidth;String trialId="";long trialStartedAt;volatile boolean closed,rebuilding;boolean failed,launched;volatile boolean minimized;String runState="準備中",error="",inputError="",lastInputError="",stopReason="";volatile String frameIssue="";int frameFailures;long missingScreenAt;long inputs,inputAt;volatile long frameAt;long decodedAt,previewAt,decodeDueAt,decodePeriod;
  ImageReader reader;FrameResource readerLifetime;HandlerThread frames;Surface surface;VirtualDisplay local;RegionPreviewView preview;LinearLayout view;TextView title,subtitle;WindowManager.LayoutParams layout;int basePreviewWidth,expandedWidth,expandedHeight,previewRevision;boolean previewTouchActive,tinyPreview;
  RegionImage latest;volatile RegionPlan roiPlan;volatile long roiCopiedPixels,roiCoveredPixels;volatile boolean previewPending;Handler frameHandler;volatile int errors;long screenInfoAt;int screenInfoId=-1;Screen screenInfo;Window(String pkg){this.pkg=pkg;}
 }
 static final class Screen {String pkg="",text="";Rect bounds;}
 Window window(String pkg){for(Window w:windows)if(w.pkg.equals(pkg)&&(w.id>0||w.rebuilding)&&!w.failed&&!w.closed)return w;return null;}
 boolean hasSession(){return selected||!windows.isEmpty()||autoPending||pending||connecting||recoverPending;}
 String[] targets(){LinkedHashSet<String> result=new LinkedHashSet<>();for(Window w:windows)result.add(w.pkg);return result.toArray(new String[0]);}
 String summary(){if(PcControl.waitingForPc(s))return PcControl.waitingSummary(s);return "仮想"+windows.size()+"画面 / "+(s.pilot.splitPilot.active?"自律ON":autoPending||connecting||recoverPending?"準備中":"自律OFF");}
 String panelSummary(){
  if(headings!=null&&!windows.isEmpty())return summary();
  StringBuilder result=new StringBuilder(summary());
  for(Window w:windows){String state=w.inputRecovery.pending?w.inputRecovery.status():UiKit.concise(w.runState);if(!w.stopReason.isEmpty())state="停止："+w.stopReason;result.append('\n').append(s.appLabel(w.pkg)).append("  ").append(state);}
  if(windows.isEmpty())result.append('\n').append(s.pilot.message);
  return result.toString();
 }
 void attachHeadings(LinearLayout panel){headings=new VirtualWindowPanel(s,this,panel);}
 void refreshHeadings(){if(headings!=null)headings.refresh();}
 void showPreview(Window w){setPreviewVisible(w,true);}
 void hidePreview(Window w){setPreviewVisible(w,false);}
 void setPreviewVisible(Window w,boolean shown){
  if(!windows.contains(w)||w.view==null)return;
  DisplayMetrics dm=new DisplayMetrics();s.wm.getDefaultDisplay().getRealMetrics(dm);updatePreviewVisibility(w,shown,dm);refreshHeadings();if(headings!=null)headings.clampPanel();
 }
 void updatePreviewVisibility(Window w,boolean shown,DisplayMetrics dm){
  // Only the phone thumbnail changes. Keep display ID, ImageReader, frame arrays,
  // input lane and running automaton intact, including while a CM is playing.
  w.minimized=!shown;w.previewTouchActive=false;w.previewRevision++;w.view.setVisibility(shown?View.VISIBLE:View.GONE);applyPreviewSize(w,dm);
  if(shown){w.preview.post(()->preview(w,generation));}
  else{w.preview.clear();}
 }
 void showWindowStatus(Window w){
  String text=s.appLabel(w.pkg)+"\n\n状態："+w.runState+"\n送信完了："+w.inputs+"回\n停止理由："+(w.stopReason.isEmpty()?"なし":w.stopReason)+"\n入力先の確認："+(w.inputRecovery.pending?w.inputRecovery.status():"待機なし");
  AlertDialog dialog=new UiStrings.DialogBuilder(s).setTitle("この画面の状況").setMessage(text).setPositiveButton("閉じる",null).setNegativeButton(pcPresentation?"表示を隠す":"この画面を閉じる",(d,which)->remove(w)).setNeutralButton("詳しい状況",(d,which)->showStatus()).create();dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);dialog.show();
 }
 void state(Window w,String text){
  w.runState=text;refreshHeadings();if(w.title==null)return;
  String shown=w.inputRecovery.pending?w.inputRecovery.status():!w.inputError.isEmpty()?"入力を再確認中":UiKit.concise(text);
  String app=s.appLabel(w.pkg);if(shown.startsWith(app+"："))shown=shown.substring(app.length()+1);
  if(!w.stopReason.isEmpty())shown="停止 · 状況をタップ";
  if(w.tinyPreview)shown=!w.stopReason.isEmpty()?"停止":w.inputRecovery.pending?"復帰中":!w.inputError.isEmpty()?"入力確認":text.contains("認識待ち")?"確認中":text.contains("主画面確認済み")?"主画面":text.contains("CM")?"CM待ち":UiKit.concise(text);
  if(w.subtitle!=null){if(!UiStrings.status(s,shown).contentEquals(w.subtitle.getText()))w.subtitle.setText(shown);w.subtitle.setTextColor(w.stopReason.isEmpty()&&!w.inputRecovery.pending?0xffc1ede0:0xffffd599);}
 }
 void startAutonomy(){if(s.isScreenGuarded()){s.pilot.note("ロック中は自動開始しません");return;}
  if(PcControl.waitingForPc(s)){s.pilot.note("PC接続待ち / PCのMultiScreen 2.6を起動してください");return;}
  record("自動開始を要求 / "+windows.size()+"画面");lastStartError="";s.pilot.wireless.resumeMaintenance();if(s.pilot.splitPilot.active&&s.pilot.splitPilot.virtualMode){s.refresh();return;}
  if(recoverPending){recoverRun=true;backendAvailable();if(recoverPending)s.pilot.note("仮想画面の復帰待ち / 本体のアプリは操作しません");return;}
  if(pending||connecting){s.pilot.note("仮想画面の接続準備を待機");return;}
  if(autoPending){if(preparation!=null)preparation.requestRun();s.pilot.note("仮想画面の表示確認後に自律を開始します");return;}
  if(windows.isEmpty()){if(adbMode){IBinder binder=VirtualBridgeProvider.connected();if(binder!=null&&requested!=null&&requested.length>0&&attach(binder))createViews(true);else s.pilot.wireless.guide(true);}else s.pilot.note("仮想画面を対象選択から開き直してください");return;}
  for(Window w:windows)if(w.failed){startFailed(s.appLabel(w.pkg)+"："+w.error);return;}
  boolean complete=true;for(Window w:windows)complete&=!w.closed&&w.id>0&&w.launched&&w.frameAt>0;
  if(!complete){String[] actual=targets();boolean small=panelHeaders;closeViews();requested=actual;createViews(true);if(small)minimize(true);return;}
  s.pilot.splitPilot.startVirtual(targets());
 }
 void applyPreviewSize(Window w,DisplayMetrics dm){
  int bar=w.view.getChildAt(0).getLayoutParams().height;
  int[] expanded=VirtualPreview.size(w.basePreviewWidth,w.width,w.height,bar,dm.widthPixels,dm.heightPixels,previewPercent,false,Math.round(140*dm.density));
  int[] shown=w.minimized?VirtualPreview.size(w.basePreviewWidth,w.width,w.height,bar,dm.widthPixels,dm.heightPixels,previewPercent,true,Math.round(140*dm.density)):expanded;
  w.expandedWidth=expanded[0];w.expandedHeight=expanded[1];
  if(w.layout.width!=shown[0]||w.layout.height!=shown[1]){w.previewRevision++;w.previewTouchActive=false;}
  w.layout.width=shown[0];w.layout.height=shown[1];
  w.tinyPreview=shown[0]<UiKit.dp(s,100);LinearLayout header=(LinearLayout)w.view.getChildAt(0);header.getChildAt(1).setVisibility(w.tinyPreview?View.GONE:View.VISIBLE);
  String name=s.appLabel(w.pkg);if(w.tinyPreview)name=w.pkg.equals(FarmPreset.APP)?"農園":w.pkg.equals(PoipoiPreset.APP)?"ポイポイ":w.pkg.equals(CoinPreset.APP)?"コイン":w.pkg.equals(UsagiPreset.APP)?"増えうさぎ":w.pkg.equals(ShoppingPreset.APP)?"買い物":w.pkg.equals(BoxPreset.APP)?"マージ":name;
  w.title.setText(name);state(w,w.runState);
  w.layout.x=Math.max(0,Math.min(w.layout.x,dm.widthPixels-w.layout.width));w.layout.y=Math.max(0,Math.min(w.layout.y,dm.heightPixels-w.layout.height));
  s.wm.updateViewLayout(w.view,w.layout);
 }
 void reflow(){refreshHeadings();DisplayMetrics dm=new DisplayMetrics();s.wm.getDefaultDisplay().getRealMetrics(dm);for(Window w:windows)applyPreviewSize(w,dm);if(s.panel!=null){WindowManager.LayoutParams p=(WindowManager.LayoutParams)s.panel.getLayoutParams();p.x=Math.max(0,Math.min(p.x,dm.widthPixels-s.panel.getWidth()));p.y=Math.max(0,Math.min(p.y,dm.heightPixels-s.panel.getHeight()));s.wm.updateViewLayout(s.panel,p);}}
 void previewSizeDialog(){
  String[] choices={"100%（通常表示）","75%","50%","35%","見出しのみ（パネル内）"};
  AlertDialog dialog=new UiStrings.DialogBuilder(s).setTitle("仮想画面の表示サイズ").setItems(choices,(d,index)->{if(index==4)minimize(true);else resizePreviews(VirtualPreview.PERCENTAGES[index]);}).setNegativeButton("閉じる",null).create();
  dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);dialog.show();
 }
 void resizePreviews(int percent){
  previewPercent=VirtualPreview.percent(percent);s.getSharedPreferences("virtualPreview",0).edit().putInt("percent",previewPercent).apply();
  minimize(false);s.pilot.note("仮想画面 "+previewPercent+"%表示 / 表示サイズを変更");
 }
 static float[] previewPoint(Window w,float x,float y){
  if(w.closed||w.minimized||w.preview==null||!w.preview.ready())return null;
  return VirtualPreview.point(w.id,w.width,w.height,w.width,w.height,w.preview.getWidth(),w.preview.getHeight(),x,y);
 }
 static Rect previewBounds(Window w){
  if(w.closed||w.minimized||w.preview==null||!w.preview.ready())return null;
  float[] fitted=VirtualPreview.viewport(w.width,w.height,w.preview.getWidth(),w.preview.getHeight());if(fitted==null)return null;
  int[] origin=new int[2];w.preview.getLocationOnScreen(origin);
  return new Rect(origin[0]+Math.round(fitted[0]),origin[1]+Math.round(fitted[1]),origin[0]+Math.round(fitted[2]),origin[1]+Math.round(fitted[3]));
 }

 boolean prepare(){String[] chosen=s.pilot.splitLauncher.targets();if(chosen.length==0){s.pilot.note("対象アプリを選択してください");return false;}if(!s.pilot.splitLauncher.virtualCountAllowed(chosen.length))return false;close();requested=chosen;if(Build.VERSION.SDK_INT<30){s.pilot.note("仮想画面の入力にはAndroid11以降が必要です");return false;}selected=true;return true;}
 void open(boolean withShizuku){s.pilot.wireless.resumeMaintenance();if(!prepare())return;adbMode=false;if(!withShizuku){remote=null;createViews(true);return;}if(!Shizuku.pingBinder()){s.pilot.note("Shizuku未接続 / 端末内接続方式も選択できます");return;}if(Shizuku.checkSelfPermission()!=PackageManager.PERMISSION_GRANTED){pending=true;Shizuku.requestPermission(5101);return;}bind();}
 void openAdb(){s.pilot.wireless.resumeMaintenance();if(!prepare())return;adbMode=true;IBinder binder=VirtualBridgeProvider.connected();if(binder==null){remote=null;s.pilot.note("端末内実行部が未接続 / 「端末だけで接続」から開始してください");return;}if(attach(binder))createViews(true);}
 void openPc(String[] packages){
  IBinder binder=VirtualBridgeProvider.connected();
  if(binder==null)throw new IllegalStateException("TapMakerのADB実行部が未接続です");
  close();requested=packages.clone();selected=true;adbMode=true;pcPresentation=true;lastStartError="";
  // Use the same virtual-window creation and independent engines as the phone UI.
  // PC ownership and its heartbeat handle recovery; do not start a Wi-Fi takeover.
  s.pilot.wireless.pauseMaintenance();
  if(!attach(binder))throw new IllegalStateException(lastStartError);
  createViews(true);
 }
 boolean attach(IBinder binder){remote=new VirtualProtocol(binder);try{if(remote.implementation()!=VirtualProtocol.IMPLEMENTATION)throw new IllegalStateException("実行部が旧版です。「端末だけで接続」から再起動してください");remote.registerOwner(VirtualBridgeProvider.ownerBinder());binder.linkToDeath(()->s.h.post(()->{if(remote==null||remote.binder!=binder)return;backendLost();}),0);return true;}catch(Exception e){remote=null;startFailed("仮想画面サービス接続："+brief(e));return false;}}
 // A dead backend or a crashed display is a fatal whole-session stop, not a recovery.
 void backendLost(){fatalSession("実行部の異常終了 / すべて停止");}
 void fatalSession(String reason){
  if(destroyed||fatalClosing)return;
  fatalClosing=true;
  try{
   try{s.stopAll(reason);}catch(Throwable ignored){}
   try{cancelRecovery();}catch(Throwable ignored){}
   try{closeViews();}catch(Throwable ignored){}
   VirtualProtocol previous=remote;
   remote=null;selected=false;pending=false;connecting=false;autoPending=false;adbMode=false;pcPresentation=false;requested=null;
   if(previous!=null)try{previous.destroyAsync();}catch(Throwable ignored){}
   VirtualBridgeProvider.emergencyStop();
   record("異常検出・全停止："+reason);
   s.pilot.note("全停止："+reason+" / 残存実行部も終了要求済み。再開は手動操作");
  }finally{fatalClosing=false;}
 }
 void cancelRecovery(){recoverPending=false;recoverRun=false;recoverTargets=null;s.pilot.wireless.modePolicy.cancelSessionRecovery();}
 void backendAvailable(){if(!recoverPending||destroyed||s.pilot.wireless.modePolicy.paused)return;IBinder binder=VirtualBridgeProvider.connected();if(binder==null)return;String[] targets=recoverTargets;boolean run=recoverRun,small=recoverMinimized;if(targets==null||targets.length==0){cancelRecovery();return;}if(attach(binder)){cancelRecovery();requested=targets;adbMode=true;createViews(run);if(small)minimize(true);}}
 final ServiceConnection connection=new ServiceConnection(){public void onServiceConnected(ComponentName name,IBinder binder){if(!connecting||adbMode)return;connecting=false;if(attach(binder))createViews(true);}public void onServiceDisconnected(ComponentName name){bound=false;if(adbMode||remote==null&&!connecting)return;connecting=false;backendLost();}};
 void bind(){connecting=true;s.pilot.note("仮想画面サービス接続中");try{Shizuku.bindUserService(args,connection);bound=true;}catch(Exception e){connecting=false;s.pilot.note("接続失敗："+e.getMessage());}}
 boolean restartResolution(){
  if(pcPresentation||PcControl.waitingForPc(s)){VirtualResolutionLab.toast(s,"PC表示中は端末の仮想画面を作り直せません。");return false;}
  if(autoPending||pending||connecting||recoverPending){VirtualResolutionLab.toast(s,"起動・接続の完了を待ってから開き直してください。");return false;}
  if(windows.isEmpty()){VirtualResolutionLab.toast(s,"ホームから対象アプリの仮想画面を開いてください。端末の通常解像度で起動します。");return false;}
  String[] targets=targets();try{DisplayMetrics dm=new DisplayMetrics();s.wm.getDefaultDisplay().getRealMetrics(dm);s.performance.geometry(dm.widthPixels,dm.heightPixels,dm.densityDpi,targets.length);}catch(IllegalArgumentException limited){VirtualResolutionLab.toast(s,limited.getMessage());return false;}
  ArrayList<Window> previous=new ArrayList<>(windows);s.stop("通常解像度で仮想画面を再作成");closeViews();requested=targets;selected=true;autoPending=true;int task=generation;
  try{frameWork.execute(()->{boolean released=true;try{long until=SystemClock.uptimeMillis()+5000;for(Window old:previous){long left=until-SystemClock.uptimeMillis();if(left<=0||!old.io.awaitTermination(left,TimeUnit.MILLISECONDS)){released=false;break;}}}catch(InterruptedException cancelled){Thread.currentThread().interrupt();released=false;}final boolean complete=released;s.h.post(()->{if(destroyed||task!=generation||!autoPending)return;autoPending=false;if(!complete){startFailed("前の仮想画面の終了を確認できません。接続し直してください。");return;}createViews(false);});});}catch(RejectedExecutionException busy){autoPending=false;startFailed("画像処理の完了を待ってから画面を開き直してください。");return false;}return true;
 }
 void createViews(boolean run){if(s.isScreenGuarded()){startFailed("ロック画面の間は仮想画面を生成しません");return;}if(!pcPresentation&&(requested==null||requested.length==0||requested.length>VirtualLoadPolicy.MAX_LIMIT)){startFailed(s.getString(R.string.virtual_limit_exceeded,VirtualLoadPolicy.MAX_LIMIT));return;}s.stop();preparation=new VirtualPreparation(requested.length,run);autoPending=true;s.removeCapture();int task=++generation;
  DisplayMetrics dm=new DisplayMetrics();s.wm.getDefaultDisplay().getRealMetrics(dm);int[] geometry;try{geometry=s.performance.geometry(dm.widthPixels,dm.heightPixels,dm.densityDpi,requested.length);}catch(IllegalArgumentException limited){autoPending=false;preparation.cancel();startFailed(limited.getMessage());return;}int width=geometry[0],height=geometry[1],density=geometry[2];int top=Math.round(56*dm.density),barHeight=Math.max(44,Math.round(44*dm.density)),available=Math.max(240,dm.heightPixels-top-(pcPresentation?UiKit.dp(s,48):s.panel!=null&&s.panel.getHeight()>0?s.panel.getHeight()+UiKit.dp(s,28):UiKit.dp(s,250)));
  for(int i=0;i<requested.length;i++){Window w=new Window(requested[i]);w.minimized=panelHeaders;w.width=width;w.height=height;w.density=density;w.startIndex=i;w.laneCount=requested.length;w.resolutionWidth=VirtualResolutionLab.selected(s);windows.add(w);VirtualResolutionLab.started(s,w,requested.length,dm);int[] cell=MultiWindowLayout.cell(dm.widthPixels,available,requested.length,i);int boxWidth=Math.max(1,cell[2]-cell[0]-8),boxHeight=Math.max(barHeight+1,cell[3]-cell[1]-8);int cw=Math.max(1,Math.min(boxWidth,Math.round((boxHeight-barHeight)*width/(float)height))),ch=Math.max(1,Math.round(cw*height/(float)width));
   w.view=new LinearLayout(s);w.view.setOrientation(1);w.view.setBackgroundColor(0xff151b23);LinearLayout bar=new LinearLayout(s);w.view.addView(bar,new LinearLayout.LayoutParams(-1,barHeight));LinearLayout captions=UiKit.column(s);captions.setGravity(Gravity.CENTER_VERTICAL);captions.setPadding(UiKit.dp(s,6),0,UiKit.dp(s,2),0);bar.addView(captions,new LinearLayout.LayoutParams(0,-1,1));w.title=UiKit.text(s,s.appLabel(w.pkg),11,Color.WHITE,true);w.title.setSingleLine(true);w.title.setEllipsize(android.text.TextUtils.TruncateAt.END);captions.addView(w.title);w.subtitle=UiKit.text(s,"準備中",10,0xffc1ede0,false);w.subtitle.setSingleLine(true);w.subtitle.setEllipsize(android.text.TextUtils.TruncateAt.END);captions.addView(w.subtitle);Button windowClose=s.button("収納",()->hidePreview(w));windowClose.setContentDescription(UiStrings.text(s,"この映像を見出しへ収納"));windowClose.setTextColor(Color.WHITE);windowClose.setPadding(0,0,0,0);windowClose.setBackgroundColor(0xff303d4a);bar.addView(windowClose,new LinearLayout.LayoutParams(Math.round(44*dm.density),-1));
   w.preview=new RegionPreviewView(s);w.roiPlan=RegionPlan.full(width,height,"manual:"+task+":"+i);FrameLayout frame=new FrameLayout(s);w.view.addView(frame,new LinearLayout.LayoutParams(-1,0,1));frame.addView(w.preview,new FrameLayout.LayoutParams(-1,-1));w.layout=s.params(cw,ch+barHeight);w.basePreviewWidth=cw;w.expandedWidth=cw;w.expandedHeight=ch+barHeight;w.layout.x=cell[0]+(boxWidth-cw)/2;w.layout.y=top+cell[1];s.wm.addView(w.view,w.layout);applyPreviewSize(w,dm);w.view.setVisibility(w.minimized?View.GONE:View.VISIBLE);drag(w);
   w.preview.setOnTouchListener((v,e)->{
    if(s.pilot.learning!=null&&s.pilot.learning.previewTouch(w,e))return true;
    boolean live=s.pilot.splitPilot.active&&s.pilot.splitPilot.virtualMode&&!autoPending&&!pending&&!connecting;
    if(!VirtualRunActivation.manualAllowed(s.pilot.automationRunning(),autoPending||pending||connecting)&&!live){w.previewTouchActive=false;return true;}
    float[] point=previewPoint(w,e.getX(),e.getY());int action=e.getActionMasked();
    if(action==MotionEvent.ACTION_DOWN){w.previewTouchActive=e.getPointerCount()==1&&point!=null&&!w.failed;if(w.previewTouchActive&&live)w.previewTouchActive=s.pilot.splitPilot.manualBegin(w);}
    else if(action==MotionEvent.ACTION_CANCEL||e.getPointerCount()!=1||action==MotionEvent.ACTION_POINTER_UP||point==null){if(w.previewTouchActive&&live)s.pilot.splitPilot.manualCancel(w);w.previewTouchActive=false;}
    else if(action==MotionEvent.ACTION_UP){boolean send=w.previewTouchActive;w.previewTouchActive=false;if(send){if(live)s.pilot.splitPilot.manualTap(w,point);else manual(w,point);}}
    return true;
   });
   try{w.frames=new HandlerThread("TapMarker-frame-"+i);w.frames.start();w.frameHandler=new Handler(w.frames.getLooper());w.reader=ImageReader.newInstance(width,height,PixelFormat.RGBA_8888,4);ImageReader ownedReader=w.reader;w.readerLifetime=new FrameResource(ownedReader::close);w.reader.setOnImageAvailableListener(reader->frame(w,reader,task),w.frameHandler);w.surface=w.reader.getSurface();long delay=VirtualLoadPolicy.creationDelay(s.performance.profile(),i,requested.length);if(delay>0)s.h.postDelayed(()->create(w,task),delay);else create(w,task);}catch(Exception e){failure(w,e.getClass().getSimpleName()+": "+e.getMessage());}
  }
  s.compactPanel(true);s.pilot.note(requested.length+"個の仮想画面を作成中 / "+(remote==null?"標準方式":"独立した画面処理"));s.h.postDelayed(()->ready(task,0,run),300);
 }
 void drag(Window w){
  final float[] pos=new float[4];View.OnTouchListener listener=(v,e)->{if(e.getActionMasked()==MotionEvent.ACTION_DOWN){pos[0]=e.getRawX();pos[1]=e.getRawY();pos[2]=w.layout.x;pos[3]=w.layout.y;}
   else if(e.getActionMasked()==MotionEvent.ACTION_MOVE){DisplayMetrics dm=new DisplayMetrics();s.wm.getDefaultDisplay().getRealMetrics(dm);w.layout.x=Math.max(0,Math.min((int)(pos[2]+e.getRawX()-pos[0]),dm.widthPixels-w.layout.width));w.layout.y=Math.max(0,Math.min((int)(pos[3]+e.getRawY()-pos[1]),dm.heightPixels-w.layout.height));s.wm.updateViewLayout(w.view,w.layout);}
   else if(e.getActionMasked()==MotionEvent.ACTION_UP&&Math.hypot(e.getRawX()-pos[0],e.getRawY()-pos[1])<UiKit.dp(s,8))showWindowStatus(w);return true;};
  w.title.setOnTouchListener(listener);if(w.subtitle!=null)w.subtitle.setOnTouchListener(listener);
 }
 void frame(Window w,ImageReader reader,int task){
  Image image=null;try{
   if(reader!=w.reader||w.closed||task!=generation)return;
   image=reader.acquireLatestImage();if(image==null||reader!=w.reader||w.closed||task!=generation)return;
   long now=SystemClock.uptimeMillis();PerformancePolicy.Profile p=s.performance.profile();
   boolean running=s.pilot.splitPilot.active||autoPending||s.pilot.learning.active;
   long interval=VirtualLoadPolicy.decodeInterval(p,w.laneCount,running,w.minimized);
   if(interval!=w.decodePeriod){w.decodePeriod=interval;w.decodeDueAt=0;}
   if(w.frameAt>0&&now<w.decodeDueAt)return;w.decodedAt=now;w.decodeDueAt=VirtualLoadPolicy.nextDecodeAt(now,interval,w.startIndex,w.laneCount);
   synchronized(w.frameLock){
    if(reader!=w.reader||w.closed||task!=generation)return;
    RegionImage next=RegionImage.fromImage(image,w.width,w.height,now,w.readerLifetime);image=null;
    if(w.latest!=null)w.latest.close();w.latest=next;w.frameAt=now;w.errors=0;w.frameIssue="";
    if(s.progressErrors!=null)s.progressErrors.image(w,"");
   }
   if(!w.minimized&&now-w.previewAt>=p.previewMs&&!w.previewPending){w.previewAt=now;w.previewPending=true;s.h.post(()->preview(w,task));}
  }catch(OutOfMemoryError pressure){s.performance.memoryPressure();if(reader==w.reader){w.errors=3;s.h.post(()->{if(!w.closed)s.pilot.note(s.appLabel(w.pkg)+"：メモリ不足 / 画面数を減らしてください");});}}catch(Exception error){if(reader==w.reader&&!w.closed&&++w.errors==3)s.h.post(()->{if(task==generation&&!w.closed)s.pilot.note(s.appLabel(w.pkg)+"：画面取得 "+error.getClass().getSimpleName());});}
  finally{if(image!=null)image.close();}
 }
 boolean frameCaptureAllowed(){return !s.isScreenGuarded()&&!destroyed&&(autoPending||s.pilot.splitPilot.active&&s.pilot.splitPilot.virtualMode);}
 Window frameWindow(VirtualFrameQueue.Request request){if(request.generation!=generation)return null;for(Window w:windows)if(!w.closed&&w.id==request.display&&w.launched)return w;return null;}
 void requestFrameRefresh(Window w){if(Build.VERSION.SDK_INT<30||!frameCaptureAllowed()||w.closed||w.id<=0||!w.launched)return;if(frameQueue.offer(w.id,generation,w.width,w.height,w.frameAt,SystemClock.uptimeMillis()))scheduleFramePump();}
 void scheduleFramePump(){if(!framePumpScheduled&&frameQueue.hasWork()){framePumpScheduled=true;s.h.postDelayed(framePump,100);}}
 void requestDisplayRecovery(Window w,String reason){if(!s.pilot.splitPilot.active||w.inputRecovery.pending)return;record("#"+w.id+" 表示復帰："+reason);w.frameIssue=reason;w.inputRecovery.rejected(SystemClock.uptimeMillis());w.screenInfo=null;}
 void frameFailed(VirtualFrameQueue.Request request,String reason,boolean transientFailure){Window w=frameWindow(request);if(w==null)return;if(!VirtualFrameQueue.stale(w.frameAt,SystemClock.uptimeMillis())){w.frameFailures=0;w.frameIssue="";return;}w.frameIssue=reason;if(!transientFailure&&++w.frameFailures>=3)requestDisplayRecovery(w,reason);}
 final Runnable framePump=new Runnable(){public void run(){
  framePumpScheduled=false;if(!frameCaptureAllowed()){frameQueue.clear();return;}long now=SystemClock.uptimeMillis();
  VirtualFrameQueue.Request expired=frameQueue.expire(now);if(expired!=null)frameFailed(expired,"仮想画面の撮り直しが時間切れです",false);
  VirtualFrameQueue.Request request=frameQueue.poll(now);
  if(request!=null){Window w=frameWindow(request);if(w==null||!VirtualFrameQueue.stale(w.frameAt,now))frameQueue.complete(request,now,w!=null);else{
   AccessibilityService.TakeScreenshotCallback callback=new AccessibilityService.TakeScreenshotCallback(){
    public void onFailure(int code){if(frameQueue.complete(request,SystemClock.uptimeMillis(),false))frameFailed(request,"仮想画面の撮り直しに失敗 (code="+code+")",code==AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT);scheduleFramePump();}
    public void onSuccess(AccessibilityService.ScreenshotResult result){
     if(!frameQueue.current(request)||frameWindow(request)!=w){result.getHardwareBuffer().close();return;}
     try{frameCopyWork.execute(()->{
      Bitmap hardware=null;RegionImage captured=null;String issue="";
      try{
       hardware=Bitmap.wrapHardwareBuffer(result.getHardwareBuffer(),result.getColorSpace());
       if(hardware==null||hardware.getWidth()!=request.width||hardware.getHeight()!=request.height)throw new IllegalStateException("仮想画面と取得画像のサイズが一致しません");
       captured=RegionImage.fromHardware(hardware,request.startedAt);hardware=null;
      }catch(Exception failure){issue=brief(failure);}catch(OutOfMemoryError pressure){issue="画像再取得時のメモリ不足";s.performance.memoryPressure();}
      finally{if(hardware!=null)hardware.recycle();result.getHardwareBuffer().close();}
      final RegionImage ready=captured;final String failure=issue;s.h.post(()->{
       if(!frameQueue.current(request)){if(ready!=null)ready.close();return;}
       long completed=SystemClock.uptimeMillis();Window current=frameWindow(request);boolean usable=false;
       synchronized(w.frameLock){
        if(current==w&&ready!=null&&completed-request.startedAt<=1500&&frameQueue.accepts(request,w.id,generation,w.frameAt,completed)){
         if(w.latest!=null)w.latest.close();w.latest=ready;w.frameAt=request.startedAt;w.errors=0;usable=true;
        }
       }
       if(!usable&&ready!=null)ready.close();
       if(usable){w.frameFailures=0;w.frameIssue="";w.screenInfo=null;if(s.progressErrors!=null)s.progressErrors.image(w,"");if(!w.minimized)preview(w,generation);}
       frameQueue.complete(request,completed,usable);if(current==w&&!usable)frameFailed(request,failure.isEmpty()?"再取得した画像が古いため破棄しました":failure,false);scheduleFramePump();
      });
     });}catch(RejectedExecutionException closed){result.getHardwareBuffer().close();frameQueue.complete(request,SystemClock.uptimeMillis(),false);scheduleFramePump();}
    }
   };
   try{s.takeScreenshot(request.display,s.getMainExecutor(),callback);}catch(Exception failure){callback.onFailure(-1);}
  }}scheduleFramePump();
 }};
 void plan(Window w,RegionPlan plan){if(w.roiPlan==null||!plan.scope.equals(w.roiPlan.scope)){w.roiPlan=plan;w.preview.clear();}}
 void manualPreview(Window w){w.roiPlan=RegionPlan.full(w.width,w.height,"manual:"+generation+":"+w.id);if(!w.minimized&&!w.previewPending)preview(w,generation);}
 void preview(Window w,int task){
  if(w.closed||w.minimized||task!=generation||w.frameHandler==null){w.previewPending=false;return;}
  w.previewPending=true;
  int width=Math.max(1,Math.min(w.width,Math.min(s.performance.profile().previewWidth,w.preview.getWidth()))),revision=w.previewRevision,id=w.id;
  RegionPlan plan=w.roiPlan;
  if(plan==null){w.previewPending=false;return;}
  final PreviewConnectome.Schedule schedule=w.preview.schedule(plan,width,w.frameAt);
  if(!w.frameHandler.post(()->{
   RegionPreviewData data=null;
   try{synchronized(w.frameLock){if(!w.closed&&task==generation&&w.latest!=null)data=RegionPreviewData.capture(plan,width,w.frameAt,w.latest.points(),schedule);}}
   catch(Exception unavailable){}catch(OutOfMemoryError pressure){s.performance.memoryPressure();}
   final RegionPreviewData ready=data;
   s.h.post(()->{w.previewPending=false;if(ready==null||w.closed||w.minimized||task!=generation||id!=w.id||revision!=w.previewRevision||w.roiPlan==null||!plan.sameCoverage(w.roiPlan)||SystemClock.uptimeMillis()-ready.capturedAt>1500)return;try{w.preview.publish(ready);}catch(OutOfMemoryError pressure){s.performance.memoryPressure();}});
  }))w.previewPending=false;
 }
 void create(Window w,int task){if(task!=generation||w.closed||!autoPending||!preparation.begin(w.startIndex))return;Intent intent=s.getPackageManager().getLaunchIntentForPackage(w.pkg);if(intent==null||intent.getComponent()==null){failure(w,"起動先が見つかりません");return;}VirtualProtocol service=remote;
  w.io.execute(()->{int id=-1;try{if(w.closed||task!=generation||!autoPending)return;if(service==null){DisplayManager manager=(DisplayManager)s.getSystemService(Context.DISPLAY_SERVICE);w.local=manager.createVirtualDisplay("TapMarker:"+w.pkg,w.width,w.height,w.density,w.surface,VirtualDisplayPolicy.flags(Build.VERSION.SDK_INT,false),null,s.h);if(w.local==null)throw new IllegalStateException("仮想画面作成失敗");id=w.local.getDisplay().getDisplayId();if(id<=0)throw new IllegalStateException("画面IDが無効です");if(w.closed||task!=generation||!autoPending){cleanupDisplay(w,service,id);return;}w.id=id;launchLocal(w,intent);}else{id=service.create(w.surface,w.width,w.height,w.density,intent.getComponent().flattenToString());if(w.closed||task!=generation||!autoPending){service.release(id);return;}service.launch(id);}final int created=id;s.h.post(()->{if(w.closed||task!=generation||!autoPending){cleanupDisplay(w,service,created);return;}w.id=created;w.launched=true;preparation.launched(w.startIndex);state(w,"表示確認中");});}catch(Exception error){if(id>0)cleanupDisplay(w,service,id);s.h.post(()->{if(task==generation&&!w.closed)failure(w,error.getClass().getSimpleName()+": "+error.getMessage());});}});
 }
 void launchLocal(Window w,Intent intent){if(w.id<=0)throw new IllegalStateException("仮想画面がありません");intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP);ActivityManager manager=(ActivityManager)s.getSystemService(Context.ACTIVITY_SERVICE);if(!manager.isActivityStartAllowedOnDisplay(s,w.id,intent))throw new SecurityException("Androidが標準権限でのゲーム起動を拒否（画面作成は成功）");s.startActivity(intent,ActivityOptions.makeBasic().setLaunchDisplayId(w.id).toBundle());}
 void failure(Window w,String reason){if(w.failed)return;if(s.progressErrors!=null)s.progressErrors.fatal(w.pkg,null,"WINDOW_FAILED",reason);w.failed=true;w.closed=true;w.error=reason;VirtualResolutionLab.failed(s,w,reason);if(preparation!=null)preparation.fail(w.startIndex);release(w);state(w,"起動不可");TextView error=new UiStrings.DisplayText(s);error.setTextColor(Color.WHITE);error.setTextSize(12);error.setPadding(8,8,8,8);error.setText(reason);((FrameLayout)w.preview.getParent()).addView(error,new FrameLayout.LayoutParams(-1,-1));s.pilot.note(s.appLabel(w.pkg)+"："+reason);s.h.post(()->fatalSession("仮想画面の起動失敗："+reason));}
 Screen screen(Window w){long now=SystemClock.uptimeMillis();if(w.screenInfo!=null&&w.screenInfoId==w.id&&now-w.screenInfoAt<100)return w.screenInfo;Screen result=readScreen(w);w.screenInfo=result;w.screenInfoId=w.id;w.screenInfoAt=now;return result;}
 void invalidateScreenInfo(){for(Window w:windows)w.screenInfo=null;}
 Screen readScreen(Window w){Screen result=new Screen();if(w.id<=0)return result;List<AccessibilityWindowInfo> list=s.getWindowsOnAllDisplays().get(w.id);if(list==null)return result;int layer=Integer.MIN_VALUE;for(AccessibilityWindowInfo a:list){if(a.getType()!=AccessibilityWindowInfo.TYPE_APPLICATION||a.getLayer()<layer)continue;AccessibilityNodeInfo root=a.getRoot();if(root==null)continue;try{result.pkg=root.getPackageName()==null?"":root.getPackageName().toString();StringBuilder text=new StringBuilder();collect(root,text,new int[]{400});result.text=text.toString();result.bounds=new Rect();a.getBoundsInScreen(result.bounds);layer=a.getLayer();}finally{root.recycle();}}return result;}
 void collect(AccessibilityNodeInfo n,StringBuilder text,int[] remaining){if(--remaining[0]<0)return;if(n.getText()!=null)text.append(n.getText()).append(' ');if(n.getContentDescription()!=null)text.append(n.getContentDescription()).append(' ');for(int i=0;i<n.getChildCount();i++){AccessibilityNodeInfo child=n.getChild(i);if(child!=null){collect(child,text,remaining);child.recycle();}}}
 void ready(int task,int attempt,boolean run){if(task!=generation||!autoPending)return;for(Window w:windows){requestFrameRefresh(w);}for(Window w:windows)if(!w.failed&&w.launched&&w.frameAt>0&&w.pkg.equals(screen(w).pkg)){preparation.confirm(w.startIndex);state(w,"開始待ち");}int result=preparation.result();if(result==VirtualPreparation.ALL_READY){boolean launch=preparation.shouldRun();autoPending=false;if(launch)s.pilot.splitPilot.startVirtual(targets());else s.pilot.note("仮想画面を復元 / 自律停止のままです");return;}if(result==VirtualPreparation.FINISHED_WITH_ERRORS){autoPending=false;s.pilot.note("仮想画面：表示確認"+preparation.count(VirtualPreparation.CONFIRMED)+" / 起動不可"+preparation.count(VirtualPreparation.FAILED)+" / 自律停止");return;}if(attempt<Math.max(60,requested.length*40)){s.h.postDelayed(()->ready(task,attempt+1,run),300);return;}for(Window w:windows)if(!w.failed&&preparation.state(w.startIndex)!=VirtualPreparation.CONFIRMED)failure(w,w.launched?"仮想画面で対象アプリの表示を確認できません":"仮想画面の起動がタイムアウトしました");autoPending=false;}
 /** Full-size image only for an explicit learning/export request, never routine recognition. */
 Bitmap image(Window w){synchronized(w.frameLock){
  if(w.closed||w.latest==null)return null;Bitmap result=Bitmap.createBitmap(w.width,w.height,Bitmap.Config.ARGB_8888);
  try{int[] rows=new int[w.width*Math.min(32,w.height)];for(int y=0;y<w.height;y+=32){int h=Math.min(32,w.height-y);w.latest.source.read(0,y,w.width,h,rows,w.width);result.setPixels(rows,0,w.width,0,y,w.width,h);}return result;}
  catch(RuntimeException|OutOfMemoryError error){result.recycle();throw error;}
 }}
 void minimize(boolean enabled){
  DisplayMetrics dm=new DisplayMetrics();s.wm.getDefaultDisplay().getRealMetrics(dm);
  panelHeaders=enabled;s.getSharedPreferences("virtualPreview",0).edit().putBoolean("panelHeaders",enabled).apply();
  for(Window w:windows)updatePreviewVisibility(w,!enabled,dm);
  refreshHeadings();if(enabled&&headings!=null)headings.setExpanded(true);else if(headings!=null)headings.clampPanel();
  s.pilot.note(enabled?"見出しをパネル内に収納 / 各画面の処理を継続":"仮想画面 "+previewPercent+"%表示");
 }

 void capture(Window w,java.util.function.BooleanSupplier valid,java.util.function.Consumer<Bitmap> success,Runnable failed){if(w==null||w.id<=0||w.closed||!valid.getAsBoolean()){failed.run();return;}int id=w.id;w.io.execute(()->{Bitmap snapshot;try{snapshot=image(w);}catch(RuntimeException|OutOfMemoryError error){if(error instanceof OutOfMemoryError)s.performance.memoryPressure();s.h.post(()->{if(valid.getAsBoolean()&&!w.closed&&w.id==id){record("#"+id+" 画像取得を中断："+brief(error));failed.run();}});return;}s.h.post(()->{if(snapshot==null){if(valid.getAsBoolean())failed.run();return;}if(!valid.getAsBoolean()||w.id!=id||w.closed){snapshot.recycle();return;}success.accept(snapshot);});});}
 void samplePixels(Window w,RegionPlan plan,java.util.function.BooleanSupplier valid,java.util.function.Function<FrameCircuit.Frame,Runnable> sample,Runnable failed){
  if(w==null||w.id<=0||w.closed||!valid.getAsBoolean()){failed.run();return;}int id=w.id,version=generation;
  plan(w,plan);
  java.util.function.BooleanSupplier current=()->!destroyed&&version==generation&&!w.closed&&w.id==id&&valid.getAsBoolean();
  RegionPlan.Builder dependencies=new RegionPlan.Builder(plan);long[] copied={0};
  FrameCircuit.Request request=new FrameCircuit.Request(current,workspace->{
   synchronized(w.frameLock){
    if(!current.getAsBoolean()||w.latest==null)return null;RegionImage image=w.latest.retain();
    try{return new FrameCircuit.Input(w.width,w.height,w.frameAt,image.tiles(dependencies,copied),image::close);}
    catch(RuntimeException|OutOfMemoryError error){image.close();throw error;}
   }
  },frame->{
   Runnable result=sample.apply(frame);RegionPlan observed=dependencies.build();
   return result==null?null:()->{w.roiCopiedPixels=copied[0];w.roiCoveredPixels=observed.coveredPixels();if(w.roiPlan!=null&&plan.scope.equals(w.roiPlan.scope))w.roiPlan=observed;result.run();};
  },task->{s.h.post(task);},failure->{if(failure instanceof OutOfMemoryError)s.performance.memoryPressure();failed.run();});
  // Leased immutable image: UI and capture keep moving while this worker analyzes.
  try{frameWork.execute(()->frameCircuit.run(request));}catch(RejectedExecutionException stopped){if(current.getAsBoolean())failed.run();}
 }
 void trimFrameCircuit(){frameCircuit.requestTrim();try{frameWork.execute(frameCircuit::trim);}catch(RejectedExecutionException ignored){}}
 boolean check(Window w){if(s.isScreenGuarded()||w==null||w.id<=0||w.closed)return false;requestFrameRefresh(w);if(w.errors>=3){state(w,"画像取得エラー");return false;}if(w.frameAt<=0||SystemClock.uptimeMillis()-w.frameAt>1500){state(w,"新しい画像を待機");return false;}Screen info=screen(w);if(info.pkg.isEmpty()){long now=SystemClock.uptimeMillis();if(w.missingScreenAt==0)w.missingScreenAt=now;if(now-w.missingScreenAt>=5000)requestDisplayRecovery(w,"対象アプリの表示情報が5秒以上取得できません");state(w,"表示情報待ち");return false;}w.missingScreenAt=0;if(DialogSafety.classify(info.text)!=0){state(w,"保護表示で待機");s.pilot.note(s.appLabel(w.pkg)+"：アカウント・異常表示のためタップ禁止");return false;}return true;}
 boolean checkInput(Window w){if(w.inputRecovery.pending||!check(w))return false;Screen info=screen(w);if(w.pkg.equals(info.pkg))return true;inputFailed(w,InputTargetRecovery.MARKER+" display="+w.id+" / 表示アプリ="+info.pkg+" / 対象="+w.pkg);return false;}
 void pause(){frameQueue.clear();s.h.removeCallbacks(framePump);framePumpScheduled=false;autoPending=false;cancelRecovery();if(preparation!=null)preparation.cancel();pending=false;connecting=false;for(Window w:windows){w.roiPlan=RegionPlan.full(w.width,w.height,"manual:"+generation+":"+w.id);w.inputLane.cancel();w.inputRecovery.reset();if(!w.failed){if(w.stopReason.isEmpty())w.stopReason=lastStop;state(w,"停止中");}}if(remote!=null)try{remote.pause();}catch(Exception ignored){}}
 boolean arm(){if(s.isScreenGuarded())return false;if(remote==null)return true;try{if(remote.implementation()!=VirtualProtocol.IMPLEMENTATION)throw new IllegalStateException("実行部が旧版です。「端末だけで接続」から再起動してください");remoteEpoch=remote.arm();return true;}catch(Exception e){startFailed("仮想画面入力の準備："+brief(e));return false;}}
 void tap(Window w,float[] p,long duration,java.util.function.BooleanSupplier valid,Runnable done,Runnable cancelled){if(w==null||w.id<=0||w.closed||!valid.getAsBoolean()||!checkInput(w)){cancelled.run();return;}long lane=w.inputLane.enter();if(lane<0){cancelled.run();return;}int id=w.id;if(s.progressErrors!=null)s.progressErrors.action(w.pkg,"タップ",id,p[0],p[1]);Runnable success=()->{boolean current=w.inputLane.valid(lane);w.inputLane.complete(lane);if(current&&valid.getAsBoolean()&&w.id==id&&!w.closed){w.inputs++;w.inputAt=SystemClock.uptimeMillis();w.inputError="";w.inputRecovery.accepted();state(w,w.runState);done.run();}else cancelled.run();};Runnable failure=()->{w.inputLane.complete(lane);cancelled.run();};
  if(remote==null){Path path=new Path();path.moveTo(p[0],p[1]);GestureDescription g=new GestureDescription.Builder().setDisplayId(id).addStroke(new GestureDescription.StrokeDescription(path,0,s.tapPressMillis(w.pkg,duration))).build();if(!s.dispatchTrackedGesture(w.pkg,g,new AccessibilityService.GestureResultCallback(){public void onCompleted(GestureDescription g){success.run();}public void onCancelled(GestureDescription g){if(valid.getAsBoolean())inputFailed(w,"Androidのジェスチャーが中断されました");failure.run();}},s.h)){inputFailed(w,"Androidが仮想画面のジェスチャーを受け付けませんでした");failure.run();}return;}
  VirtualProtocol service=remote;long epoch=remoteEpoch;w.io.execute(()->{boolean ok=false;String reason="Androidが対象アプリへの入力を受け付けませんでした";try{if(valid.getAsBoolean()&&w.inputLane.valid(lane)&&w.id==id&&!w.closed)ok=service.tap(id,p[0],p[1],s.tapPressMillis(w.pkg,duration),epoch);}catch(Exception error){reason=brief(error);}boolean accepted=ok;String issue=reason;s.h.post(()->{if(accepted)s.afterTapRelease(w.pkg,success);else{if(valid.getAsBoolean()&&w.id==id&&!w.closed)inputFailed(w,issue);failure.run();}});});
 }
 void gesture(Window w,float[] points,long duration,java.util.function.BooleanSupplier valid,Runnable done,Runnable cancelled){
  if(w==null||w.id<=0||w.closed||!valid.getAsBoolean()||!checkInput(w)){cancelled.run();return;}try{new GesturePath(points,w.width,w.height,duration);}catch(Exception bad){cancelled.run();return;}
  long lane=w.inputLane.enter();if(lane<0){cancelled.run();return;}int id=w.id;if(s.progressErrors!=null)s.progressErrors.action(w.pkg,"ジェスチャー",id,points[0],points[1]);
  Runnable success=()->{boolean current=w.inputLane.valid(lane);w.inputLane.complete(lane);if(current&&valid.getAsBoolean()&&w.id==id&&!w.closed){w.inputs++;w.inputAt=SystemClock.uptimeMillis();w.inputError="";w.inputRecovery.accepted();done.run();}else cancelled.run();};Runnable failure=()->{w.inputLane.complete(lane);cancelled.run();};
  if(remote==null){Path path=new Path();path.moveTo(points[0],points[1]);for(int i=2;i<points.length;i+=2)path.lineTo(points[i],points[i+1]);GestureDescription g=new GestureDescription.Builder().setDisplayId(id).addStroke(new GestureDescription.StrokeDescription(path,0,points.length==2?s.tapPressMillis(w.pkg,duration):duration)).build();if(!s.dispatchTrackedGesture(w.pkg,g,new AccessibilityService.GestureResultCallback(){public void onCompleted(GestureDescription g){success.run();}public void onCancelled(GestureDescription g){failure.run();}},s.h))failure.run();return;}
  VirtualProtocol service=remote;long epoch=remoteEpoch;w.io.execute(()->{boolean ok=false;String reason="対象画面への曲線入力が拒否されました";try{if(valid.getAsBoolean()&&w.inputLane.valid(lane)&&w.id==id&&!w.closed)ok=service.gesture(id,points,points.length==2?s.tapPressMillis(w.pkg,duration):duration,epoch);}catch(Exception error){reason=brief(error);}boolean accepted=ok;String issue=reason;s.h.post(()->{if(accepted)s.afterTapRelease(w.pkg,success);else{if(valid.getAsBoolean()&&w.id==id&&!w.closed)inputFailed(w,issue);failure.run();}});});
 }
 void back(Window w,java.util.function.BooleanSupplier valid,Runnable done,Runnable failed){
  if(w==null||w.closed||w.id<=0||remote==null||!valid.getAsBoolean()||!checkInput(w)){failed.run();return;}long lane=w.inputLane.enter();if(lane<0){failed.run();return;}int id=w.id;VirtualProtocol service=remote;long epoch=remoteEpoch;if(s.progressErrors!=null)s.progressErrors.action(w.pkg,"戻る",id,Float.NaN,Float.NaN);
  w.io.execute(()->{boolean ok=false;String reason="";try{if(valid.getAsBoolean()&&w.inputLane.valid(lane)&&w.id==id&&!w.closed)ok=service.back(id,epoch);}catch(Exception error){reason=brief(error);}final boolean accepted=ok;final String issue=reason;s.h.post(()->{boolean current=w.inputLane.valid(lane);w.inputLane.complete(lane);if(accepted&&current&&valid.getAsBoolean()&&w.id==id&&!w.closed)done.run();else if(valid.getAsBoolean()&&w.id==id&&!w.closed){if(!issue.isEmpty())inputFailed(w,issue);failed.run();}});});
 }
 void manual(Window w,float[] p){if(arm())tap(w,p,60,()->window(w.pkg)==w,()->{},()->s.pilot.note("仮想画面への入力を受け付けませんでした"));}
 void restore(Window w,java.util.function.BooleanSupplier valid,Runnable done,Runnable failed){restore(w,false,valid,done,failed);}
 /** Replace only this display. Other input lanes and the remote arm epoch stay valid. */
 void rebuild(Window w,java.util.function.BooleanSupplier valid,Runnable done,java.util.function.Consumer<String> failed){
  if(w==null||w.closed||w.id<=0||w.rebuilding||!valid.getAsBoolean()){failed.accept("対象画面を再作成できません");return;}
  Intent intent=s.getPackageManager().getLaunchIntentForPackage(w.pkg);
  if(intent==null||intent.getComponent()==null){failed.accept("対象アプリの起動先がありません");return;}
  final int oldId=w.id,task=generation;final VirtualProtocol service=remote;final long armEpoch=remoteEpoch;
  final ImageReader fresh;
  try{fresh=ImageReader.newInstance(w.width,w.height,PixelFormat.RGBA_8888,3);fresh.setOnImageAvailableListener(reader->frame(w,reader,task),w.frameHandler);}
  catch(Exception error){failed.accept(brief(error));return;}
  final ImageReader prior=w.reader;final FrameResource priorLifetime=w.readerLifetime;final FrameResource freshLifetime=new FrameResource(fresh::close);
  w.rebuilding=true;w.launched=false;w.id=-1;w.inputLane.cancel();frameQueue.remove(oldId,task);
  synchronized(w.frameLock){w.reader=fresh;w.readerLifetime=freshLifetime;w.surface=fresh.getSurface();if(w.latest!=null)w.latest.close();w.latest=null;w.roiPlan=RegionPlan.full(w.width,w.height,"rebuild:"+task+":"+oldId);w.frameAt=0;w.decodeDueAt=0;}
  if(prior!=null){prior.setOnImageAvailableListener(null,null);priorLifetime.release();}
  w.screenInfo=null;w.screenInfoId=-1;w.screenInfoAt=0;w.previewTouchActive=false;w.preview.clear();
  state(w,"この画面を再作成中");record("#"+oldId+" "+s.appLabel(w.pkg)+" 復帰上限後に対象画面だけを再作成 / "+w.width+"x"+w.height+" / "+w.density+" dpi");
  if(s.progressErrors!=null)s.progressErrors.action(w.pkg,"仮想画面の再作成を要求",oldId,Float.NaN,Float.NaN);
  int receipt=w.restoreReceipt.begin(true,SystemClock.uptimeMillis());
  w.io.execute(()->{
   int created=-1;
   try{
    created=DisplayReplacement.replace(oldId,()->!w.closed&&task==generation&&valid.getAsBoolean(),new DisplayReplacement.Host(){
     public void release(int id)throws Exception{if(service!=null)service.release(id);else if(w.local!=null){w.local.release();w.local=null;}}
     public int create()throws Exception{if(service!=null)return service.create(w.surface,w.width,w.height,w.density,intent.getComponent().flattenToString());DisplayManager manager=(DisplayManager)s.getSystemService(Context.DISPLAY_SERVICE);w.local=manager.createVirtualDisplay("TapMarker:"+w.pkg,w.width,w.height,w.density,w.surface,VirtualDisplayPolicy.flags(Build.VERSION.SDK_INT,false),null,s.h);if(w.local==null)throw new IllegalStateException("仮想画面作成失敗");return w.local.getDisplay().getDisplayId();}
     public void launch(int id)throws Exception{if(service!=null){if(!service.restore(id,armEpoch,true))throw new IllegalStateException("再作成後の起動を受け付けませんでした");}else{w.id=id;launchLocal(w,intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));}}
    });
    final int id=created;
    s.h.post(()->{
     if(w.closed||task!=generation||!valid.getAsBoolean()){
      cleanupDisplay(w,service,id);w.rebuilding=false;return;
     }
     w.id=id;w.launched=true;w.rebuilding=false;w.errors=w.frameFailures=0;w.missingScreenAt=0;w.inputRecovery.reset();w.inputError=w.frameIssue="";w.screenInfo=null;
     w.restoreReceipt.accepted(receipt,SystemClock.uptimeMillis());state(w,"再作成した画面の主画面を確認中");record("#"+oldId+" → #"+id+" 対象画面の再作成を受付 / 主画面は未確認");requestFrameRefresh(w);done.run();
    });
   }catch(Exception error){
    if(created>0)cleanupDisplay(w,service,created);
    String reason=brief(error);s.h.post(()->{w.rebuilding=false;w.restoreReceipt.failed(receipt);if(task==generation&&!w.closed&&valid.getAsBoolean()){w.error=reason;state(w,"画面の再作成失敗");failed.accept(reason);}});
   }
  });
 }
 void restore(Window w,boolean restart,java.util.function.BooleanSupplier valid,Runnable done,Runnable failed){
  if(w==null||w.id<=0||w.closed||!valid.getAsBoolean()){failed.run();return;}
  if(s.progressErrors!=null)s.progressErrors.action(w.pkg,restart?"ゲームのタスク再起動を要求":"アプリ復帰を要求",w.id,Float.NaN,Float.NaN);
  int id=w.id,ticket=w.restoreReceipt.begin(restart,SystemClock.uptimeMillis());VirtualProtocol service=remote;long epoch=remoteEpoch;
  Runnable rejected=()->{w.restoreReceipt.failed(ticket);failed.run();};
  w.io.execute(()->{try{
   if(!valid.getAsBoolean()||w.closed||w.id!=id){s.h.post(rejected);return;}
   if(service==null)launchLocal(w,s.getPackageManager().getLaunchIntentForPackage(w.pkg).addFlags(restart?Intent.FLAG_ACTIVITY_CLEAR_TASK:Intent.FLAG_ACTIVITY_CLEAR_TOP));
   else if(!service.restore(id,epoch,restart))throw new IllegalStateException("停止済みの復帰要求です");
   s.h.post(()->{w.restoreReceipt.accepted(ticket,SystemClock.uptimeMillis());w.screenInfo=null;requestFrameRefresh(w);if(!w.closed&&w.id==id&&valid.getAsBoolean())done.run();else failed.run();});
  }catch(Exception error){s.h.post(rejected);}});
 }
 void cleanupDisplay(Window w,VirtualProtocol service,int id){if(w.id==id)w.id=-1;try{if(service!=null)service.release(id);else if(w.local!=null){w.local.release();w.local=null;}}catch(Exception ignored){}}
 void release(Window w){int id=w.id;frameQueue.remove(id,generation);w.id=-1;w.inputLane.cancel();VirtualProtocol service=remote;if(id>0&&!w.io.isShutdown())w.io.execute(()->cleanupDisplay(w,service,id));w.io.shutdown();
  synchronized(w.frameLock){if(w.latest!=null){w.latest.close();w.latest=null;}if(w.reader!=null){w.reader.setOnImageAvailableListener(null,null);w.readerLifetime.release();w.readerLifetime=null;w.reader=null;}}
  if(w.frames!=null)w.frames.quitSafely();w.preview.clear();
 }
 void remove(Window w){if(pcPresentation&&PcControl.ownsRun(s)){hidePreview(w);record("#"+w.id+" スマホのプレビューを非表示（PC処理継続）");s.pilot.note("スマホのプレビューを隠しました / PCの各画面は処理継続");return;}s.stop();w.closed=true;release(w);windows.remove(w);if(windows.isEmpty())selected=false;try{s.wm.removeView(w.view);}catch(Exception ignored){}s.pilot.note("仮想画面を閉じました / 自律停止");}
 void closeViews(){generation++;for(Window w:new ArrayList<>(windows)){w.closed=true;try{release(w);}catch(Throwable ignored){}try{s.wm.removeView(w.view);}catch(Throwable ignored){}}windows.clear();try{trimFrameCircuit();}catch(Throwable ignored){}try{refreshHeadings();}catch(Throwable ignored){}}
 void close(){pcPresentation=false;selected=false;pending=false;connecting=false;s.stop();closeViews();}
 void destroy(){destroyed=true;if(displayManager!=null)displayManager.unregisterDisplayListener(displayListener);close();frameWork.shutdown();frameCopyWork.shutdown();Shizuku.removeRequestPermissionResultListener(permissions);if(bound&&Shizuku.pingBinder())try{Shizuku.unbindUserService(args,connection,true);}catch(Exception ignored){}remote=null;}
}
