package jp.tapmarker;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.*;

/** One lightweight poll per second. Reports are local until the user copies them. */
final class ProgressErrors {
 static final String CHANNEL="progress-errors",EXTRA_REPORT="progressReport";
 static final int NOTIFICATION_ID=1708;
 final TapService s;final ProgressWatchdog watchdog=new ProgressWatchdog();final DiagnosticJournal journal;
 final Map<String,Long> notifiedAt=new HashMap<>();final Map<String,Action> actions=new HashMap<>();
 Button banner;boolean closed;
 static final class Action {String type;int display;float x,y;long at;}
 final Runnable ticker=new Runnable(){public void run(){if(closed)return;try{poll();}catch(RuntimeException failure){android.util.Log.w("MarkerTap","Progress monitor",failure);}finally{if(!closed)s.h.postDelayed(this,1000);}}};
 ProgressErrors(TapService service){s=service;journal=new DiagnosticJournal(service);}
 void start(){closed=false;s.h.removeCallbacks(ticker);s.h.postDelayed(ticker,1000);}
 void close(){closed=true;s.h.removeCallbacks(ticker);watchdog.tracks.clear();actions.clear();journal.close();}
 void action(String app,String type,int display,float x,float y){
  Action a=actions.get(app);if(a==null){a=new Action();actions.put(app,a);}a.type=type;a.display=display;a.x=x;a.y=y;a.at=SystemClock.uptimeMillis();if(display>0&&s.pilot!=null&&s.pilot.splitPilot.active){VirtualWindows.Window w=s.pilot.virtualWindows.window(app);if(w!=null&&w.id==display){if(journal.action(app,display,s.pilot.splitPilot.token,type,x,y,a.at))image(w,"before");}}
 }
 void image(VirtualWindows.Window w,String forced){if(closed||w==null||w.closed||s.pilot==null||!s.pilot.splitPilot.active||!s.pilot.splitPilot.virtualMode)return;synchronized(w.frameLock){journal.frame(w.pkg,w.id,s.pilot.splitPilot.token,w.width,w.height,w.latest==null?null:w.latest.points(),w.frameAt,forced);}}
 void observed(SplitPilot.Slot slot,Map<String,float[]> samples,long at,String before,String after){VirtualWindows.Window w=s.pilot.virtualWindows.window(slot.pkg);if(w!=null)journal.observed(slot.pkg,w.id,s.pilot.splitPilot.token,slot.phases.engine,samples,at,before,after,slot.adExit.recovery.report(SystemClock.uptimeMillis())+" / "+slot.adExit.popupExits.report()+" / "+slot.adExit.popupReceipt.report()+" / "+slot.displayRecovery.report(SystemClock.uptimeMillis()));}
 void control(SplitPilot.Slot slot,PhaseEngine.Watch rule,Map<String,float[]> samples,int width,int height){VirtualWindows.Window w=s.pilot.virtualWindows.window(slot.pkg);if(w!=null)journal.control(slot.pkg,w.id,s.pilot.splitPilot.token,slot.phases.engine,rule,samples,width,height);}
 void attach(LinearLayout panel){banner=s.button("進行エラーを見る",()->showLatest(s));banner.setTextColor(0xffa52b34);banner.setMaxLines(2);banner.setBackground(UiKit.shape(s,0xffffe8e5,10));panel.addView(banner,new LinearLayout.LayoutParams(-1,-2));refreshBanner();}
 void refreshBanner(){if(banner!=null)banner.setVisibility(s.getSharedPreferences("automationDiagnostics",0).getBoolean("progressUnread",false)?View.VISIBLE:View.GONE);}
 static long age(long at,long now){return at<=0?-1:Math.max(0,now-at);}
 void poll(){
  if(s.pilot==null)return;AutoPilot pilot=s.pilot;Set<String> live=new HashSet<>();long now=SystemClock.uptimeMillis();
  if(pilot.learning.active){watchdog.retain(live);return;}
  if(pilot.splitPilot.active&&pilot.splitPilot.slots!=null){
   for(SplitPilot.Slot slot:pilot.splitPilot.slots){
    if(slot.stopped)continue;VirtualWindows.Window w=pilot.splitPilot.virtualMode?pilot.virtualWindows.window(slot.pkg):null;
    observe(slot.pkg,slot.phases,pilot.splitPilot.token,w,slot.home,slot.observedAt,slot.processingMs,w==null?s.inputCompleted:w.inputs,w==null?physicalError():w.inputError,now,live);
   }
  }else if(pilot.active&&pilot.phases.enabled&&(pilot.activeDialog==null||!pilot.activeDialog.isShowing())){
   boolean home=pilot.usagiGameConfirmed||pilot.farmConfirmed||pilot.coinConfirmed||pilot.shoppingConfirmed||pilot.boxConfirmed||pilot.poipoiConfirmed;
   observe(pilot.target,pilot.phases,pilot.token,null,home,pilot.phaseSampleTakenAt,0,s.inputCompleted,physicalError(),now,live);
  }
  watchdog.retain(live);
 }
 String physicalError(){return s.inputFailureAt>s.inputSuccessAt?s.lastInputError:"";}
 void observe(String app,AppPhases phases,long run,VirtualWindows.Window w,boolean home,long observed,long processing,long inputs,String inputError,long now,Set<String> live){
  PhaseEngine e=phases.engine;PhaseEngine.Phase phase=e.phases.get(e.current);if(phase==null)return;
  ProgressWatchdog.Sample sample=new ProgressWatchdog.Sample();sample.app=app;sample.key=app+":"+(w==null?0:w.id);sample.run=run;sample.state=e.current;sample.home=home;sample.terminal=phase.terminal;sample.frameAge=age(w==null?observed:w.frameAt,now);sample.analysisMs=processing;sample.inputs=inputs;sample.inputError=inputError;sample.diagnostic=e.diagnostic;sample.stateAge=Math.max(0,now-e.enteredAt);sample.expectedWaitMs=ProgressWatchdog.expectedWait(e);
  sample.expectInput=phase.markersDuring&&home&&!e.awaitingBranch();if(s.pilot.splitPilot.active&&s.pilot.splitPilot.slots!=null){for(SplitPilot.Slot slot:s.pilot.splitPilot.slots)if(slot.pkg.equals(app)&&(slot.journey.blocksMain()||slot.coinJourney.blocksMain()||slot.banc.pending()))sample.expectInput=false;}else if(s.pilot.actionPending||s.pilot.returning||s.pilot.banc!=null&&s.pilot.banc.pending())sample.expectInput=false;
  // An explicit long action may intentionally defer physical screenshots.
  if(w==null&&s.pilot.actionPending)for(PhaseEngine.Watch watch:phase.watches)if(watch.duration>15000){sample.expectFrames=false;break;}
  live.add(sample.key);ProgressWatchdog.Event event=watchdog.observe(sample,now);
  if(event!=null)save(event,details(app,phases,w,home,observed,processing,now));
 }
 String details(String app,AppPhases phases,VirtualWindows.Window w,boolean home,long observed,long processing,long now){
  PhaseEngine e=phases==null?null:phases.engine;StringBuilder b=new StringBuilder();
  b.append("アプリ名：").append(s.appLabel(app)).append("\n稼働画面数：").append(s.pilot.virtualWindows.windows.size()).append(" / 設定上限 ").append(s.pilot.splitLauncher.virtualLimit());
  b.append("\n負荷設定：").append(s.performance.describe()).append("\n主画面の認識：").append(home).append("\n最後の判定画像：").append(age(observed,now)).append("ms前\n直近の判定時間：").append(processing).append("ms");
  if(w!=null)b.append("\n仮想画面ID：").append(w.id).append("\n画像寸法：").append(w.width).append('x').append(w.height).append("\n表示密度：").append(w.density).append(" dpi\n起動時の指定幅：").append(w.resolutionWidth).append("\n元画像の古さ：").append(age(w.frameAt,now)).append("ms\n表示状態：").append(w.runState).append("\n入力完了回数：").append(w.inputs).append("\n入力エラー：").append(w.inputError).append("\n画像取得の状況：").append(w.frameIssue);
  if(w!=null)b.append("\n復帰要求と確認：").append(w.restoreReceipt.report());
  if(w!=null)b.append("\n対象画面のウインドウと操作部：\n").append(VirtualWindowEvidence.describe(s,w));
  if(w==null)b.append("\n入力状況：").append(s.inputHold).append("\n直近の入力エラー：").append(s.lastInputError);
  Action action=actions.get(app);if(action!=null){b.append("\n直前に要求した操作：").append(action.type).append(" / 画面 ").append(action.display).append(" / ").append(Math.max(0,now-action.at)).append("ms前");if(Float.isFinite(action.x)&&Float.isFinite(action.y))b.append(" / 座標 ").append(action.x).append(',').append(action.y);}
  if(e!=null){PhaseEngine.Phase phase=e.phases.get(e.current);b.append("\n状態名：").append(phase==null?"":phase.name).append("\n状態ID：").append(e.current).append("\n現在の状態入場からの経過：").append(Math.max(0,now-e.enteredAt)).append("ms\n最後に完了したルール：").append(e.lastCompletedRule).append("\nオートマトンの診断：").append(e.diagnostic);}
  if(s.pilot.splitPilot.active)b.append('\n').append(s.pilot.splitPilot.recoveryReport(app,now));
  else if(s.pilot.active)b.append("\n画像照合（状態条件を除く）：").append(s.pilot.recognition).append("\n復帰処理：").append(s.pilot.adExit.recovery.report(now));
  if(phases!=null){b.append("\n直近の処理履歴：\n");for(int i=Math.max(0,phases.log.size()-8);i<phases.log.size();i++)b.append(AutomationIncident.limit(phases.log.get(i),400)).append('\n');}
  b.append("\n選択対象：").append(java.util.Arrays.toString(s.pilot.splitLauncher.targets())).append("\n各仮想画面：\n");for(VirtualWindows.Window other:s.pilot.virtualWindows.windows)b.append(other.pkg).append(" #").append(other.id).append(" / ").append(AutomationIncident.limit(other.runState,160)).append(" / 元画像 ").append(age(other.frameAt,now)).append("ms前\n");
  b.append("\n開始・停止の履歴：\n").append(s.pilot.virtualWindows.eventText());return b.toString();
 }
 void fatal(String app,PhaseEngine engine,String code,String reason){
  ProgressWatchdog.Sample sample=new ProgressWatchdog.Sample();sample.app=app==null?"":app;sample.state=engine==null?"":engine.current;
  AppPhases phases=null;if(s.pilot.splitPilot.slots!=null)for(SplitPilot.Slot slot:s.pilot.splitPilot.slots)if(slot.pkg.equals(sample.app))phases=slot.phases;
  if(phases==null&&s.pilot.phases.pkg.equals(sample.app))phases=s.pilot.phases;
  VirtualWindows.Window w=s.pilot.virtualWindows.window(sample.app);
  save(new ProgressWatchdog.Event(code,sample,reason,0),details(sample.app,phases,w,false,0,0,SystemClock.uptimeMillis()));
 }
 void save(ProgressWatchdog.Event event,String details){
  long now=SystemClock.uptimeMillis();String key=event.app+":"+event.code;Long last=notifiedAt.get(key);if(last!=null&&now-last<60000)return;notifiedAt.put(key,now);
  String body=ProgressReport.build(event,version(s),Build.MANUFACTURER+" "+Build.MODEL+" / Android "+Build.VERSION.RELEASE+" (API "+Build.VERSION.SDK_INT+")",details,System.currentTimeMillis());
  final String archive=journal.reserve(event.app);final int display=s.pilot.virtualWindows.window(event.app)==null?0:s.pilot.virtualWindows.window(event.app).id;final int run=s.pilot.splitPilot.token;
  body+="\n画像付き診断："+archive+"\n「画像付き診断を共有」から画像と判断記録をまとめて共有できます。";
  final String frozenBody=body;VirtualWindows.Window capture=s.pilot.virtualWindows.window(event.app);if(capture!=null)image(capture,"error");journal.incident(event.app,display,run,archive,frozenBody,now);
  s.h.postDelayed(()->{if(journal.current(event.app,display,run))journal.incident(event.app,display,run,archive,frozenBody,now);},1000);
  android.content.SharedPreferences prefs=s.getSharedPreferences("automationDiagnostics",0);JSONArray next=new JSONArray();
  try{JSONArray old=new JSONArray(prefs.getString("progressHistory","[]"));for(int i=Math.max(0,old.length()-4);i<old.length();i++)next.put(old.getJSONObject(i));JSONObject entry=new JSONObject();entry.put("title",new java.text.SimpleDateFormat("MM/dd HH:mm:ss",Locale.ROOT).format(new Date())+" / "+s.appLabel(event.app)+" / "+event.code);entry.put("body",body);entry.put("archive",archive);next.put(entry);}catch(JSONException ignored){next=new JSONArray();try{next.put(new JSONObject().put("title",event.code+" / "+event.app).put("body",body));}catch(JSONException impossible){}}
  prefs.edit().putString("progressReport",body).putString("progressHistory",next.toString()).putString("progressArchive",archive).putBoolean("progressUnread",true).apply();
  refreshBanner();if(s.pilot.virtualWindows.headings!=null)s.pilot.virtualWindows.headings.clampPanel();post(event,body);
 }
 static void shareLatest(Context context){DiagnosticJournal.share(context,context.getSharedPreferences("automationDiagnostics",0).getString("progressArchive",""));}
 static void shareReport(Context context,String body){String name="";try{JSONArray history=new JSONArray(context.getSharedPreferences("automationDiagnostics",0).getString("progressHistory","[]"));for(int i=history.length()-1;i>=0;i--){JSONObject row=history.getJSONObject(i);if(body.equals(row.optString("body"))){name=row.optString("archive");break;}}}catch(JSONException ignored){}DiagnosticJournal.share(context,name);}
 /** User initiated handoff, not background upload. ChatGPT may not support ZIP in shares. */
 static void shareToChatGPT(Context context,String body){
  if(body==null||body.isEmpty()){UiStrings.toast(context,"共有できる診断文がありません",0).show();return;}
  Intent send=new Intent(Intent.ACTION_SEND).setType("text/plain")
    .putExtra(Intent.EXTRA_TEXT,"MarkerTap 障害診断（原因・修正案の確認をお願いします）\\n\\n"+body);
  send.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_GRANT_READ_URI_PERMISSION);
  // A ZIP is additional context only; EXTRA_TEXT remains available independently.
  try{
   String name="";JSONArray history=new JSONArray(context.getSharedPreferences("automationDiagnostics",0).getString("progressHistory","[]"));
   for(int i=history.length()-1;i>=0;i--){JSONObject entry=history.getJSONObject(i);if(body.equals(entry.optString("body"))){name=entry.optString("archive");break;}}
   if(!name.isEmpty()){
    java.io.File zip=DiagnosticArchive.resolve(new java.io.File(context.getFilesDir(),"diagnostic-incidents"),name);
    if(zip.isFile()){android.net.Uri uri=new android.net.Uri.Builder().scheme("content").authority(context.getPackageName()+".diagnostics").appendPath(zip.getName()).build();send.setClipData(ClipData.newRawUri("MarkerTap incident ZIP",uri));}
   }
  }catch(Exception unavailable){android.util.Log.w("MarkerTap","Optional diagnostic attachment unavailable",unavailable);}
  try{context.startActivity(new Intent(send).setPackage("com.openai.chatgpt"));}
  catch(android.content.ActivityNotFoundException|SecurityException unsupported){
   try{context.startActivity(Intent.createChooser(send,UiStrings.text(context,"ChatGPTなどに診断文を共有")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}
   catch(RuntimeException failed){ClipboardManager clip=(ClipboardManager)context.getSystemService(Context.CLIPBOARD_SERVICE);if(clip!=null)clip.setPrimaryClip(ClipData.newPlainText("MarkerTap diagnostic",body));UiStrings.toast(context,"共有先を開けません。診断文をコピーしました。",1).show();}
  }
 }
 static String version(Context c){try{return c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionName;}catch(Exception missing){return "unknown";}}
 static boolean notificationsEnabled(Context c){NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);return nm!=null&&nm.areNotificationsEnabled()&&(Build.VERSION.SDK_INT<33||c.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED);}
 void post(ProgressWatchdog.Event event,String body){
  if(!notificationsEnabled(s))return;NotificationManager nm=(NotificationManager)s.getSystemService(Context.NOTIFICATION_SERVICE);
  try{if(Build.VERSION.SDK_INT>=26)nm.createNotificationChannel(new NotificationChannel(CHANNEL,UiStrings.text(s,"進行エラー"),NotificationManager.IMPORTANCE_DEFAULT));
   Intent open=new Intent(s,MainActivity.class).setAction("jp.tapmarker.PROGRESS_ERROR."+event.app).putExtra(EXTRA_REPORT,body).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP);
   PendingIntent pending=PendingIntent.getActivity(s,event.app.hashCode(),open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
   Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(s,CHANNEL):new Notification.Builder(s);
   b.setSmallIcon(android.R.drawable.stat_notify_error).setContentTitle(UiStrings.text(s,"MarkerTap：進行エラー")).setContentText(s.appLabel(event.app)+" / "+event.code).setStyle(new Notification.BigTextStyle().bigText(UiStrings.text(s,"タップして診断文を表示・コピーできます"))).setContentIntent(pending).setAutoCancel(true).setOnlyAlertOnce(true).setVisibility(Notification.VISIBILITY_PRIVATE);
   nm.notify(event.app,NOTIFICATION_ID,b.build());
  }catch(RuntimeException unavailable){android.util.Log.w("MarkerTap","Progress notification unavailable",unavailable);}
 }
 static void notificationSettings(Activity activity){
  if(Build.VERSION.SDK_INT>=33&&activity.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED&&(!activity.getSharedPreferences("automationDiagnostics",0).getBoolean("notificationAsked",false)||activity.shouldShowRequestPermissionRationale(android.Manifest.permission.POST_NOTIFICATIONS))){activity.getSharedPreferences("automationDiagnostics",0).edit().putBoolean("notificationAsked",true).apply();activity.requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},1708);return;}
  Intent intent=new Intent(Build.VERSION.SDK_INT>=26?Settings.ACTION_APP_NOTIFICATION_SETTINGS:Settings.ACTION_APPLICATION_DETAILS_SETTINGS);if(Build.VERSION.SDK_INT>=26)intent.putExtra(Settings.EXTRA_APP_PACKAGE,activity.getPackageName());else intent.setData(android.net.Uri.parse("package:"+activity.getPackageName()));activity.startActivity(intent);
 }
 static void showLatest(Context context){String body=context.getSharedPreferences("automationDiagnostics",0).getString("progressReport","");if(body.isEmpty()){UiStrings.toast(context,"進行エラーは記録されていません",0).show();return;}showReport(context,body);}
 static void showReport(Context context,String body){
  if(TapService.instance!=null&&TapService.instance.pilot!=null&&!TapService.instance.pilot.virtualSession())TapService.instance.stop("進行エラーの診断文を表示");
  if(body.equals(context.getSharedPreferences("automationDiagnostics",0).getString("progressReport","")))context.getSharedPreferences("automationDiagnostics",0).edit().putBoolean("progressUnread",false).apply();if(TapService.instance!=null&&TapService.instance.progressErrors!=null)TapService.instance.progressErrors.refreshBanner();
  TextView text=new TextView(context);text.setText(body);text.setTextIsSelectable(true);text.setTextSize(13);text.setPadding(20,16,20,16);ScrollView scroll=new ScrollView(context);scroll.addView(text);
  LinearLayout box=new LinearLayout(context);box.setOrientation(1);int h=(int)(context.getResources().getDisplayMetrics().heightPixels/context.getResources().getDisplayMetrics().density);box.addView(scroll,new LinearLayout.LayoutParams(-1,UiKit.dp(context,Math.max(100,Math.min(360,h*.45f)))));Button toChatGPT=UiKit.button(context,"ChatGPTに診断を送る（共有画面で確認）",false,()->shareToChatGPT(context,body));box.addView(toChatGPT,new LinearLayout.LayoutParams(-1,-2));Button share=UiKit.button(context,"画像付き診断ZIPを共有",false,()->shareReport(context,body));box.addView(share,new LinearLayout.LayoutParams(-1,-2));
  AlertDialog dialog=new UiStrings.DialogBuilder(context).setTitle("進行エラーの診断文").setView(box).setPositiveButton("診断文をコピー",(d,i)->{ClipboardManager clipboard=(ClipboardManager)context.getSystemService(Context.CLIPBOARD_SERVICE);clipboard.setPrimaryClip(ClipData.newPlainText("MarkerTap progress report",body));UiStrings.toast(context,"診断文をコピーしました。ChatGPTの会話に貼り付けてください（Workも利用できます）。",Toast.LENGTH_LONG).show();}).setNegativeButton("閉じる",null).setNeutralButton("過去のエラー",(d,i)->showHistory(context)).create();if(context instanceof TapService)dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);dialog.show();
 }
 static void showHistory(Context context){
  try{JSONArray saved=new JSONArray(context.getSharedPreferences("automationDiagnostics",0).getString("progressHistory","[]"));if(saved.length()==0){showLatest(context);return;}String[] titles=new String[saved.length()],bodies=new String[saved.length()];for(int i=0;i<saved.length();i++){JSONObject row=saved.getJSONObject(saved.length()-1-i);titles[i]=row.getString("title");bodies[i]=row.getString("body");}AlertDialog dialog=new UiStrings.DialogBuilder(context).setTitle("過去のエラー").setItems(titles,(d,i)->showReport(context,bodies[i])).setNegativeButton("閉じる",null).create();if(context instanceof TapService)dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);dialog.show();}catch(JSONException invalid){UiStrings.toast(context,"進行エラーは記録されていません",0).show();}
 }
 static void openFromIntent(Activity activity,Intent intent){String body=intent.getStringExtra(EXTRA_REPORT);if(body!=null){intent.removeExtra(EXTRA_REPORT);activity.getWindow().getDecorView().post(()->showReport(activity,body));}}
}
