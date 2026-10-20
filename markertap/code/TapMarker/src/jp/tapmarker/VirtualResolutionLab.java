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
 static int selected(Context c){return VirtualResolutionPolicy.selection(prefs(c).getInt(WIDTH,0));}
 static JSONArray history(Context c){try{return new JSONArray(prefs(c).getString("history","[]"));}catch(JSONException invalid){return new JSONArray();}}
 static String widthLabel(Context c,int width){return width==0?UiStrings.text(c,"自動（従来設定）"):c.getString(R.string.startup_width_value,width);}
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
 static void assess(Activity a,VirtualWindows owner,VirtualWindows.Window w,int choice){
  if(live()!=owner||!owner.windows.contains(w)){toast(a,"画面が入れ替わりました。結果を選び直してください。");return;}
  if(choice==0&&(w.closed||w.failed||w.id<=0||!w.launched||w.frameAt<=0)){toast(a,"起動・画像取得を確認できない画面は正常として記録できません。");return;}
  JSONArray rows=history(a);int at=index(rows,w.trialId);if(at<0){toast(a,"この起動の記録は残っていません。画面を開き直してください。");return;}
  try{JSONObject row=rows.getJSONObject(at);snapshot(owner.s,w,row);row.put("outcome",OUTCOMES[choice]).put("assessment","manual");write(a,rows);
   boolean all=choice==0&&!owner.windows.isEmpty();int width=w.width;
   for(VirtualWindows.Window other:owner.windows){int otherAt=index(rows,other.trialId);all&=!other.closed&&!other.failed&&other.width==width&&other.height==w.height&&other.density==w.density&&otherAt>=0&&"PASS".equals(otherAt<0?"":rows.getJSONObject(otherAt).optString("outcome"));}
   if(all)prefs(a).edit().putInt("lastGoodWidth",width).putString("lastGoodDevice",deviceKey(a)).apply();
   toast(a,"実験結果を保存しました。");
  }catch(JSONException impossible){toast(a,"実験結果を保存できませんでした。");}
 }
 static void chooseResult(Activity a){
  VirtualWindows owner=live();if(owner==null||owner.windows.isEmpty()){toast(a,"仮想画面を開いてから結果を記録してください。");return;}
  ArrayList<VirtualWindows.Window> windows=new ArrayList<>(owner.windows);String[] names=new String[windows.size()];
  for(int i=0;i<names.length;i++){VirtualWindows.Window w=windows.get(i);names[i]=owner.s.appLabel(w.pkg)+" · "+geometry(a,w.width,w.height,w.density);}
  new UiStrings.DialogBuilder(a).setTitle("結果を記録するアプリ").setItems(names,(d,i)->new UiStrings.DialogBuilder(a).setTitle(names[i]).setItems(OUTCOME_LABELS,(dialog,choice)->assess(a,owner,windows.get(i),choice)).setNegativeButton("閉じる",null).show()).setNegativeButton("閉じる",null).show();
 }
 static String report(Context c){try{return "MarkerTap 起動解像度の実験 v"+ProgressErrors.version(c)+"\n表示・認識・タップの合否は手動評価です。入力完了回数はゲーム反応の成功数ではありません。\nimageArraysBytesEstimateは2配列の理論量、javaHeapUsedBytesは本アプリのJavaヒープ、systemAvailableBytesは端末全体の空きメモリの瞬間値です。ゲーム・GPUを含む総消費量や削減量を測定した値ではありません。\n直近80件までの起動を保存します。\n"+history(c).toString(2);}catch(JSONException impossible){return history(c).toString();}}
 static void showReport(Activity a){TextView text=new TextView(a);text.setText(report(a));text.setTextIsSelectable(true);text.setTextSize(12);text.setPadding(20,12,20,12);ScrollView scroll=new ScrollView(a);scroll.addView(text);new UiStrings.DialogBuilder(a).setTitle("解像度の実験記録").setView(scroll).setPositiveButton("実験記録をコピー",(d,i)->((ClipboardManager)a.getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("MarkerTap resolution experiment",text.getText()))).setNegativeButton("閉じる",null).show();}
 static void toast(Context c,String text){UiStrings.toast(c,text,Toast.LENGTH_LONG).show();}
 static int[] proposed(Activity a,int saved){TapService s=TapService.instance;PerformanceSettings performance=s==null?new PerformanceSettings(a):s.performance;DisplayMetrics m=new DisplayMetrics();a.getWindowManager().getDefaultDisplay().getRealMetrics(m);VirtualWindows owner=live();int count=owner==null||owner.windows.isEmpty()?1:owner.windows.size();return VirtualResolutionPolicy.geometry(performance.profile(),m.widthPixels,m.heightPixels,m.densityDpi,count,performance.heapBytes,saved);}
 static void show(Activity a){
  SharedPreferences settings=prefs(a);if(!settings.getBoolean("ascendingInitialized",false))settings.edit().putInt(WIDTH,VirtualResolutionPolicy.initialExperimentSelection(settings.getInt(WIDTH,0),false)).putBoolean("ascendingInitialized",true).apply();
  LinearLayout box=UiKit.column(a);box.setPadding(UiKit.dp(a,16),UiKit.dp(a,8),UiKit.dp(a,16),UiKit.dp(a,12));
  box.addView(UiKit.text(a,"最小幅32pxから始め、動作しなければ一段ずつ上げます。DPIも連動します。変更後は画面を開き直してください。",14,UiKit.INK,false));
  ArrayList<Integer> values=new ArrayList<>();for(int width:VirtualResolutionPolicy.WIDTHS)values.add(width);int saved=selected(a),lastGood=VirtualResolutionPolicy.selection(prefs(a).getInt("lastGoodWidth",0));if(!values.contains(saved))values.add(saved);if(!values.contains(lastGood))values.add(lastGood);Collections.sort(values);values.remove(Integer.valueOf(0));values.add(Integer.valueOf(0));
  String[] labels=new String[values.size()];for(int i=0;i<labels.length;i++)labels[i]=widthLabel(a,values.get(i));
  Spinner spinner=new Spinner(a);ArrayAdapter<String> adapter=new ArrayAdapter<>(a,android.R.layout.simple_spinner_item,new ArrayList<>(Arrays.asList(labels)));adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);spinner.setAdapter(adapter);spinner.setSelection(values.indexOf(saved));spinner.setMinimumHeight(UiKit.dp(a,48));box.addView(spinner,new LinearLayout.LayoutParams(-1,-2));
  TextView info=UiKit.text(a,"",13,UiKit.MUTED,false);box.addView(info);
  Runnable refresh=()->{StringBuilder b=new StringBuilder();try{int[] g=proposed(a,selected(a));b.append(a.getString(R.string.startup_next_geometry,geometry(a,g[0],g[1],g[2])));}catch(IllegalArgumentException limited){b.append(UiStrings.text(a,limited.getMessage()));}VirtualWindows owner=live();if(owner!=null)for(VirtualWindows.Window w:owner.windows)b.append('\n').append(owner.s.appLabel(w.pkg)).append(" · ").append(geometry(a,w.width,w.height,w.density));info.setText(b.toString());};
  spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){}public void onItemSelected(AdapterView<?> p,View v,int position,long id){prefs(a).edit().putInt(WIDTH,values.get(position)).apply();refresh.run();}});refresh.run();
  UiKit.full(a,box,UiKit.button(a,"最小幅32pxからやり直す",false,()->{prefs(a).edit().putInt(WIDTH,VirtualResolutionPolicy.MIN_WIDTH).apply();spinner.setSelection(values.indexOf(VirtualResolutionPolicy.MIN_WIDTH));}));
  UiKit.full(a,box,UiKit.button(a,"一段上げる（次回起動）",false,()->{int next=VirtualResolutionPolicy.nextHigher(selected(a));if(next<0){toast(a,"この実験版の上限は幅720pxです。");return;}prefs(a).edit().putInt(WIDTH,next).apply();spinner.setSelection(values.indexOf(next));}));
  UiKit.full(a,box,UiKit.button(a,"正常と記録した幅へ戻す",false,()->{int width=prefs(a).getInt("lastGoodWidth",0);if(width==0||!deviceKey(a).equals(prefs(a).getString("lastGoodDevice",""))){toast(a,"現在の端末設定で全画面を正常と記録した幅がありません。");return;}if(!values.contains(width)){values.add(width);adapter.add(widthLabel(a,width));}prefs(a).edit().putInt(WIDTH,width).apply();spinner.setSelection(values.indexOf(width));}));
  UiKit.full(a,box,UiKit.button(a,"動作結果を記録",false,()->chooseResult(a)));
  UiKit.full(a,box,UiKit.button(a,"実験記録を見る・コピー",false,()->showReport(a)));
  box.addView(UiKit.text(a,"同じアプリ・画面数・負荷設定で、小さい幅から比較します。目印と位置関係、主画面、ミッション、ダイアログ、CM復帰、タップを確認してください。",13,UiKit.MUTED,false));
  ScrollView scroll=new ScrollView(a);scroll.addView(box);
  AlertDialog dialog=new UiStrings.DialogBuilder(a).setTitle("起動解像度の実験").setView(scroll).setPositiveButton("選択した幅で開き直す",null).setNegativeButton("閉じる",null).create();dialog.show();dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{prefs(a).edit().putInt(WIDTH,values.get(spinner.getSelectedItemPosition())).apply();VirtualWindows owner=live();if(owner==null){toast(a,"ユーザー補助を有効にしてください");return;}if(owner.restartResolution())dialog.dismiss();});
 }
 private VirtualResolutionLab(){}
}
