package jp.tapmarker;
import android.app.*;import android.content.*;

/** Namespaced settings for separate game UIs hosted in the same APK. */
final class GameProfiles {
 static String key(Context context,String app){return GameTarget.key(app,context.getSharedPreferences("gameProfiles",0).getString(app,""));}
 static boolean activate(Context context,String target){String app=GameTarget.packageName(target);if(!GameTarget.USAPO.equals(app))return true;return context.getSharedPreferences("gameProfiles",0).edit().putString(app,GameTarget.mode(target)).commit();}
 static void choose(AutoPilot pilot){
  TapService s=pilot.s;String app=pilot.editingVirtualPackage.isEmpty()?pilot.packageNow():pilot.editingVirtualPackage;
  if(!GameTarget.USAPO.equals(app)&&pilot.virtualSession()&&pilot.virtualWindows.window(GameTarget.USAPO)!=null)app=GameTarget.USAPO;
  if(!GameTarget.USAPO.equals(app)){pilot.note("うさポ内のゲームを開いてから選択してください。お店屋さん・ボックスマージはアプリ別に学習できます");return;}
  s.stop("対象ゲームの選択");
  pilot.phaseUi.show(new UiStrings.DialogBuilder(pilot.phaseUi.context()).setTitle("うさポ内の対象ゲーム").setItems(new String[]{"農園うさぎ","風船パンチ","うさポ共通画面"},(d,i)->{
   String target=i==0?GameTarget.FARM:i==1?GameTarget.BALLOON:GameTarget.USAPO;
   if(!select(s,target)){pilot.note("対象ゲームを保存できませんでした");return;}
   pilot.note((GameTarget.label(target).isEmpty()?"うさポ共通画面":GameTarget.label(target))+"を選択 / 実画面を開き、学習開始で操作を実演してください");
  }).setNegativeButton("取消",null).create());
 }
 static boolean select(TapService s,String target){
  String app=GameTarget.packageName(target),old=key(s,app);
  if(old.equals(target))return true;
  if(app.equals(s.currentApp))s.save();
  if(!activate(s,target))return false;
  if(app.equals(s.currentApp)){
   s.removeCapture();for(TapService.Mark m:s.marks)s.wm.removeView(m.view);s.marks.clear();s.load();s.save();
   s.pilot.logic.load(app);s.pilot.phases.load(app);s.renumber();
  }else if(app.equals(s.pilot.editingVirtualPackage))s.pilot.phases.load(app);
  return true;
 }
}
