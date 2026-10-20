package jp.tapmarker;
import android.app.*;import android.content.*;import android.graphics.*;import android.os.*;import android.view.*;import android.view.accessibility.*;import android.widget.*;import org.json.*;import java.util.*;

/** Separate completion profiles for ordinary and virtual target-app UIs. */
final class DailyRunner {
 final AutoPilot pilot;final TapService s;final DailyRoute route=new DailyRoute();boolean internal,virtualMode;int launchGeneration;
 final IdentityHashMap<PhaseEngine,Long> engineRuns=new IdentityHashMap<>();final LinkedHashSet<String> runApps=new LinkedHashSet<>(),doneApps=new LinkedHashSet<>();
 final IdentityHashMap<PhaseEngine,DailyCompletion> pendingEntry=new IdentityHashMap<>();
 DailyCapture capture;
 DailyRunner(AutoPilot p){pilot=p;s=p.s;}
 void cancel(){if(internal)return;route.stop();engineRuns.clear();pendingEntry.clear();launchGeneration++;if(capture!=null){capture.cancel();capture=null;}}
 String key(String pkg,boolean virtual){return (pkg.indexOf('#')>=0?pkg:GameProfiles.key(s,pkg))+(virtual?":virtual":":ordinary");}
 DailyCompletion profile(String pkg,boolean virtual){try{String raw=s.getSharedPreferences("dailyCompletion",0).getString(key(pkg,virtual),"");if(raw.isEmpty())return null;JSONObject j=new JSONObject(raw);return new DailyCompletion(AppLogic.read(j.getJSONArray("doneBounds")),AppLogic.read(j.getJSONArray("headingBounds")),AppLogic.read(j.getJSONArray("done")),AppLogic.read(j.getJSONArray("heading")));}catch(Exception missing){return null;}}
 void save(String pkg,boolean virtual,DailyCompletion p){try{JSONObject j=new JSONObject();j.put("doneBounds",AppLogic.array(p.doneBounds));j.put("headingBounds",AppLogic.array(p.headingBounds));j.put("done",AppLogic.array(p.done));j.put("heading",AppLogic.array(p.heading));if(!s.getSharedPreferences("dailyCompletion",0).edit().putString(key(pkg,virtual),j.toString()).commit())throw new IllegalStateException("完了条件を保存できません");pilot.note(s.appLabel(pkg)+"：デイリー完了の2領域を登録しました");}catch(Exception error){pilot.note("完了条件の保存に失敗しました");}}
 void configure(){
  s.stop("デイリー完了条件の登録");ArrayList<String> apps=new ArrayList<>();ArrayList<VirtualWindows.Window> displays=new ArrayList<>();ArrayList<String> labels=new ArrayList<>();String ordinary=pilot.packageNow();if(s.validApp(ordinary)){apps.add(GameProfiles.key(s,ordinary));displays.add(null);labels.add(s.appLabel(ordinary)+"（通常画面）");}for(VirtualWindows.Window w:pilot.virtualWindows.windows)if(w.launched&&!w.closed&&!w.minimized){apps.add(GameProfiles.key(s,w.pkg));displays.add(w);labels.add(s.appLabel(w.pkg)+"（仮想画面）");}
  if(apps.isEmpty()){pilot.note("全達成・報酬受取済みのミッション画面を開いてください");return;}
  pilot.phaseUi.show(new UiStrings.DialogBuilder(pilot.phaseUi.context()).setTitle("完了条件を登録する画面").setItems(labels.toArray(new String[labels.size()]),(d,i)->{
   pilot.phaseUi.show(new UiStrings.DialogBuilder(pilot.phaseUi.context()).setTitle("デイリー全達成の表示を記録").setMessage("全デイリーを達成し、報酬も受け取り済みの表示を出してください。\n① 完了を示す文字・印を囲む\n② ミッション画面の見出しを囲む\n両方が一致したときだけ次のアプリへ進みます。空欄や「プレイ」ボタンだけを完了条件にしないでください。うさポでは、そのゲームに固有の名称やミッション条件も2番目の領域に含めてください。通常画面と仮想画面の条件は別々に保存します。").setPositiveButton("2領域を選ぶ",(dialog,which)->{capture=new DailyCapture(this,apps.get(i),displays.get(i));s.h.postDelayed(()->{if(capture!=null)capture.start();},150);}).setNegativeButton("取消",null).create());
  }).setNegativeButton("閉じる",null).create());
 }
 void choose(){
  s.stop("デイリー巡回の対象選択");virtualMode=pilot.virtualSession();LinkedHashSet<String> order=new LinkedHashSet<>(Arrays.asList(pilot.splitLauncher.targets()));order.addAll(Arrays.asList(PresetRegistry.APPS));order.addAll(Arrays.asList(GameTarget.ADDED_APPS));ArrayList<String> apps=new ArrayList<>();for(String pkg:order)if(s.getPackageManager().getLaunchIntentForPackage(pkg)!=null&&(!virtualMode||pilot.virtualWindows.window(pkg)!=null))apps.addAll(Arrays.asList(GameTarget.profiles(pkg)));if(apps.isEmpty()){pilot.note("巡回するアプリを開いてください");return;}
  String[] labels=new String[apps.size()];boolean[] selected=new boolean[apps.size()];for(int i=0;i<labels.length;i++){labels[i]=(i+1)+". "+s.appLabel(apps.get(i))+(profile(apps.get(i),virtualMode)==null?"（完了条件未登録）":"");selected[i]=GameTarget.dailyDefault(apps.get(i),profile(apps.get(i),virtualMode)!=null,GameProfiles.key(s,GameTarget.USAPO));}
  pilot.phaseUi.show(new UiStrings.DialogBuilder(pilot.phaseUi.context()).setTitle("デイリー巡回：表示順で1周").setMultiChoiceItems(labels,selected,(d,i,on)->{selected[i]=on;if(on&&GameTarget.USAPO.equals(GameTarget.packageName(apps.get(i))))for(int j=0;j<selected.length;j++)if(j!=i&&GameTarget.USAPO.equals(GameTarget.packageName(apps.get(j)))){selected[j]=false;((AlertDialog)d).getListView().setItemChecked(j,false);}}).setPositiveButton("巡回開始",(d,w)->{ArrayList<String> chosen=new ArrayList<>();for(int i=0;i<selected.length;i++)if(selected[i])chosen.add(apps.get(i));start(chosen.toArray(new String[chosen.size()]),virtualMode);}).setNegativeButton("取消",null).create());
 }
 void start(String[] apps,boolean virtual){
  if(!GameTarget.dailyOrderSupported(apps)){pilot.note("うさポは1周につき1ゲームを選択してください。ゲーム一覧への自動復帰は実画面の対応待ちです");return;}
  s.stop("デイリー巡回の開始");if(Build.VERSION.SDK_INT<30){pilot.note("デイリー巡回にはAndroid11以降が必要です");return;}for(String pkg:apps)if(profile(pkg,virtual)==null){pilot.note(s.appLabel(pkg)+"のデイリー完了条件を先に登録してください");return;}else if(virtual&&pilot.virtualWindows.window(GameTarget.packageName(pkg))==null){pilot.note(s.appLabel(pkg)+"の仮想画面を開いてください");return;}
  for(String pkg:apps){AppPhases settings=new AppPhases(s);settings.loadTarget(pkg);if(!PresetRegistry.supports(GameTarget.packageName(pkg))&&(!settings.enabled||settings.engine.phases.isEmpty())){pilot.note(s.appLabel(pkg)+"のオートマトンを先に設定してください");return;}}
  try{route.start(apps);}catch(Exception invalid){pilot.note(invalid.getMessage());return;}runApps.clear();runApps.addAll(Arrays.asList(apps));doneApps.clear();virtualMode=virtual;launchCurrent();
 }
 void install(PhaseEngine engine,String pkg,boolean virtual){if(!route.active()||virtualMode!=virtual||!route.current().equals(GameProfiles.key(s,pkg)))return;DailyCompletion p=profile(pkg,virtual);if(p==null)throw new IllegalStateException("デイリー完了条件がありません");if(GameTarget.USAPO.equals(pkg)&&engine.windowRelative)pendingEntry.put(engine,p);else p.install(engine);engineRuns.put(engine,route.generation());}
 void entered(PhaseEngine engine,String pkg){DailyCompletion p=pendingEntry.get(engine);if(p==null||!route.active()||!route.current().equals(GameProfiles.key(s,pkg))||!GameTarget.entryCompleted(engine))return;pendingEntry.remove(engine);p.install(engine);}
 boolean completed(String pkg,PhaseEngine engine){
  if(!DailyCompletion.PHASE.equals(engine.current)||!engine.lastCompletedRule.startsWith(DailyCompletion.RULE+":")||!route.active()||!route.current().equals(GameProfiles.key(s,pkg)))return false;
  Long run=engineRuns.remove(engine);if(run==null||!route.complete(GameProfiles.key(s,pkg),run))return false;doneApps.add(GameProfiles.key(s,pkg));internal=true;try{s.stop("デイリー達成・次のアプリへ");}finally{internal=false;}
  if(!route.active()){virtualStates();pilot.note("選択した全アプリのデイリーが完了しました / 巡回停止");return true;}launchCurrent();return true;
 }
 void launchCurrent(){
  if(!route.active())return;String target=route.current(),pkg=GameTarget.packageName(target);long run=route.generation();if(!GameProfiles.select(s,target)){route.stop();pilot.note("次のゲームの設定を選択できません / 巡回停止");return;}int task=++launchGeneration;pilot.note(s.appLabel(pkg)+"を開始");
  if(virtualMode){internal=true;try{pilot.splitPilot.startVirtual(new String[]{pkg});}finally{internal=false;}if(!pilot.splitPilot.active){route.stop();pilot.note("仮想画面を開始できません / 巡回停止");}else virtualStates();return;}
  Intent intent=s.getPackageManager().getLaunchIntentForPackage(pkg);if(intent==null){route.stop();pilot.note(s.appLabel(pkg)+"を起動できません / 巡回停止");return;}try{s.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForeground(target,run,task,0);}catch(Exception error){route.stop();pilot.note("次のアプリを開けません / 巡回停止");}
 }
 void waitForeground(String target,long run,int task,int attempt){
  String pkg=GameTarget.packageName(target);
  if(!route.active()||route.generation()!=run||task!=launchGeneration||!route.current().equals(target))return;
  if(!pkg.equals(pilot.packageNow())||s.busy){if(attempt>=50){route.stop();pilot.note(s.appLabel(pkg)+"の表示・入力待機を確認できません / 巡回停止");return;}s.h.postDelayed(()->waitForeground(target,run,task,attempt+1),400);return;}
  internal=true;try{s.switchProfile(pkg);pilot.startSingle();}finally{internal=false;}
  if(!pilot.active){pilot.stopSingle(false);route.stop();pilot.note(s.appLabel(pkg)+"の自律を開始できません / 巡回停止");}
 }
 void virtualStates(){if(!virtualMode)return;for(VirtualWindows.Window w:pilot.virtualWindows.windows){if(doneApps.contains(GameProfiles.key(s,w.pkg)))pilot.virtualWindows.state(w,"デイリー完了");else if(!GameProfiles.key(s,w.pkg).equals(route.current()))pilot.virtualWindows.state(w,runApps.contains(GameProfiles.key(s,w.pkg))?"順番待ち":"巡回対象外");}}
}
