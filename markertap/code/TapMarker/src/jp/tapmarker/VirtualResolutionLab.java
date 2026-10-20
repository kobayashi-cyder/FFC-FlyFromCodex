package jp.tapmarker;

import android.app.*;
import android.content.*;
import android.os.*;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.*;
import java.util.*;
import org.json.*;

/** Bounded startup records and explicit human assessments; no success inferred from input ACKs. */
final class VirtualResolutionLab {
 static final String PREFS="virtualResolutionLab",WIDTH="startupWidth";
 static final String[] OUTCOMES={"PASS","DISPLAY_FAIL","RECOGNITION_FAIL","TAP_FAIL","START_FAIL","UNASSESSED"};
 static final String[] OUTCOME_LABELS={"表示・認識・タップが正常（手動確認）","表示が崩れる","認識できない","タップが合わない・届かない","起動できない","未確認に戻す"};
 static SharedPreferences prefs(Context c){return c.getSharedPreferences(PREFS,0);}
 static void migrate(Context c){SharedPreferences settings=prefs(c);if(VirtualResolutionPolicy.needsNativeMigration(settings.getInt(WIDTH,0),settings.getBoolean("nativeResolutionV118",false)))settings.edit().putInt(WIDTH,0).remove("lastGoodWidth").remove("lastGoodDevice").remove("ascendingInitialized").putBoolean("nativeResolutionV118",true).apply();}
 static int selected(Context c){migrate(c);return 0;}
 static JSONArray history(Context c){try{return new JSONArray(prefs(c).getString("history","[]"));}catch(JSONException invalid){return new JSONArray();}}
 static String widthLabel(Context c,int width){return UiStrings.text(c,"端末の通常解像度");}
 static String deviceKey(Context c){DisplayMetrics m=new DisplayMetrics();((android.view.WindowManager)c.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRealMetrics(m);return Build.MODEL+":"+Build.VERSION.SDK_INT+":"+m.widthPixels+":"+m.heightPixels+":"+m.densityDpi;}
 static String geometry(Context c,int width,int height,int dpi){return c.getString(R.string.startup_geometry_value,width,height,dpi);}
 static VirtualWindows live(){TapService s=TapService.instance;return s==null||s.pilot==null?null:s.pilot.virtualWindows;}
 static int index(JSONArray rows,String id){for(int i=rows.length()-1;i>=0;i--){JSONObject row=rows.optJSONObject(i);if(row!=null&&id.equals(row.optString("trialId")))return i;}return -1;}
 static void write(Context c,JSONArray rows){JSONArray bounded=new JSONArray();for(int i=Math.max(0,rows.length()-80);i<rows.length();i++)bounded.put(rows.optJSONObject(i));prefs(c).edit().putString("history",bounded.toString()).apply();}
 static void started(TapService s,VirtualWindows.Window w,int count,DisplayMetrics m){
  try{JSONObject row=new JSONObject();w.trialStartedAt=SystemClock.uptimeMillis();w.trialId=System.currentTimeMillis()+":"+s.pilot.virtualWindows.generation+":"+w.startIndex;
   row.put("trialId",w.trialId).put("startedAt",System.currentTimeMillis()).put("version",ProgressErrors.version(s)).put("device",deviceKey(s)).put("app",w.pkg).put("requestedWidth",w.resolutionWidth).put("width",w.width).put("height",w.height).put("densityDpi",w.density).put("sourceWidth",m.widthPixels).put("sourceHeight",m.heightPixels).put("sourceDpi",m.densityDpi).put("windows",count).put("performance",s.performance.describe()).put("imageArraysBytesEstimate",VirtualResolutionPolicy.imageBytes(w.width,w.height,count)).put("outcome","UNASSESSED");
   JSONArray rows=history(s);rows.put(row);write(s,rows);
  }catch(JSONException impossible){android.util.Log.w("MarkerTap","Resolution startup record",impossible);}
 }
 static void snapshot(TapService s,VirtualWindows.Window w,JSONObject row)throws JSONException{
  long now=SystemClock.uptimeMillis();Runtime runtime=Runtime.getRuntime();ActivityManager am=(ActivityManager)s.getSystemService(Context.ACTIVITY_SERVICE);ActivityManager.MemoryInfo memory=new ActivityManager.MemoryInfo();if(am!=null)am.getMemoryInfo(memory);
  row.put("sampledAt",System.currentTimeMillis()).put("elapsedMs",Math.max(0,now-w.trialStartedAt)).put("displayId",w.id).put("launched",w.launched).put("closed",w.closed).put("frameAgeMs",ProgressErrors.age(w.frameAt,now)).put("inputCompletions",w.inputs).put("state",w.runState).put("inputError",w.inputError).put("frameIssue",w.frameIssue).put("startupError",w.error).put("javaHeapUsedBytes",runtime.totalMemory()-runtime.freeMemory()).put("systemAvailableBytes",memory.availMem);
  row.remove("homeRecognized");row.remove("analysisMs");row.remove("observationAgeMs");
  if(s.pilot.splitPilot.slots!=null)for(SplitPilot.Slot slot:s.pilot.splitPilot.slots)if(slot.pkg.equals(w.pkg)&&slot.observedAt>=w.trialStartedAt){row.put("homeRecognized",slot.home).put("analysisMs",slot.processingMs).put("observationAgeMs",ProgressErrors.age(slot.observedAt,now));break;}
 }
 static void failed(TapService s,VirtualWindows.Window w,String reason){
  JSONArray rows=history(s);int at=index(rows,w.trialId);if(at<0)return;
  try{JSONObject row=rows.getJSONObject(at);snapshot(s,w,row);row.put("automaticStartupFailure",reason);write(s,rows);}catch(JSONException impossible){android.util.Log.w("MarkerTap","Resolution failure record",impossible);}
 }
 static String report(Context c){try{return "MarkerTap 画面の起動記録 v"+ProgressErrors.version(c)+"\n表示・認識・タップの合否は手動評価です。入力完了回数はゲーム反応の成功数ではありません。\nimageArraysBytesEstimateは2配列の理論量、javaHeapUsedBytesは本アプリのJavaヒープ、systemAvailableBytesは端末全体の空きメモリの瞬間値です。ゲーム・GPUを含む総消費量や削減量を測定した値ではありません。\n直近80件までの起動を保存します。\n"+history(c).toString(2);}catch(JSONException impossible){return history(c).toString();}}
 static void showReport(Activity a){TextView text=new TextView(a);text.setText(report(a));text.setTextIsSelectable(true);text.setTextSize(12);text.setPadding(20,12,20,12);ScrollView scroll=new ScrollView(a);scroll.addView(text);new UiStrings.DialogBuilder(a).setTitle("過去の起動記録").setView(scroll).setPositiveButton("記録をコピー",(d,i)->((ClipboardManager)a.getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("MarkerTap resolution experiment",text.getText()))).setNegativeButton("閉じる",null).show();}
 static void toast(Context c,String text){UiStrings.toast(c,text,Toast.LENGTH_LONG).show();}
 static int[] proposed(Activity a,int saved){TapService s=TapService.instance;PerformanceSettings performance=s==null?new PerformanceSettings(a):s.performance;DisplayMetrics m=new DisplayMetrics();a.getWindowManager().getDefaultDisplay().getRealMetrics(m);VirtualWindows owner=live();int count=owner==null||owner.windows.isEmpty()?1:owner.windows.size();return VirtualResolutionPolicy.geometry(performance.profile(),m.widthPixels,m.heightPixels,m.densityDpi,count,performance.heapBytes,saved);}
 static void show(Activity a){
  migrate(a);
  LinearLayout box=UiKit.column(a);box.setPadding(UiKit.dp(a,16),UiKit.dp(a,8),UiKit.dp(a,16),UiKit.dp(a,12));
  box.addView(UiKit.text(a,"端末の通常解像度・DPIで起動します。保存済みの低解像度指定は解除されます。",14,UiKit.INK,false));
  StringBuilder info=new StringBuilder();
  try{int[] g=proposed(a,0);info.append(a.getString(R.string.startup_next_geometry,geometry(a,g[0],g[1],g[2])));}
  catch(IllegalArgumentException limited){info.append(UiStrings.text(a,limited.getMessage()));}
  VirtualWindows owner=live();if(owner!=null)for(VirtualWindows.Window w:owner.windows)info.append('\n').append(owner.s.appLabel(w.pkg)).append(" · ").append(geometry(a,w.width,w.height,w.density));
  box.addView(UiKit.text(a,info.toString(),13,UiKit.MUTED,false));
  box.addView(UiKit.text(a,"メモリが不足する場合は、同時に開く仮想画面の数を減らしてください。表示パネルは縮小できます。",13,UiKit.MUTED,false));
  UiKit.full(a,box,UiKit.button(a,"過去の起動記録を見る",false,()->showReport(a)));
  ScrollView scroll=new ScrollView(a);scroll.addView(box);
  AlertDialog dialog=new UiStrings.DialogBuilder(a).setTitle("仮想画面の解像度").setView(scroll).setPositiveButton("通常解像度で開き直す",null).setNegativeButton("閉じる",null).create();
  dialog.show();dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{VirtualWindows current=live();if(current==null){toast(a,"ユーザー補助を有効にしてください");return;}if(current.restartResolution())dialog.dismiss();});
 }
 private VirtualResolutionLab(){}
}
