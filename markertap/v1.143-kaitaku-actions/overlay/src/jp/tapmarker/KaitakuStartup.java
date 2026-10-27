package jp.tapmarker;

import android.os.SystemClock;

/** Factory initialization is scoped to the new package; existing edits stay intact. */
final class KaitakuStartup {
 static void loaded(AppPhases phases){
  if(!KaitakuPreset.APP.equals(phases.pkg)||!phases.loadIssue.isEmpty()||phases.engine.windowRelative)return;
  if(!phases.engine.phases.isEmpty()&&(phases.customized||!KaitakuPreset.isLegacy(phases.engine)))return;
  android.content.SharedPreferences prefs=phases.context.getSharedPreferences("appPhases",0);
  String key=phases.storage(),backup="presetBackup:kaitaku:1.143:"+key;
  if(!prefs.contains(backup)&&!prefs.edit().putString(backup,prefs.getString(key,"{}")).commit())throw new IllegalStateException("開拓うさぎの設定を退避できません");
  PhaseEngine prior=phases.engine;boolean enabled=phases.enabled,customized=phases.customized;
  try{phases.engine=new PhaseEngine();KaitakuPreset.install(phases.engine);phases.enabled=true;phases.customized=false;phases.save();}
  catch(RuntimeException failure){phases.engine=prior;phases.enabled=enabled;phases.customized=customized;throw failure;}
 }
 static boolean start(AutoPilot pilot){
  String foreground=pilot.packageNow();if(!KaitakuPreset.APP.equals(foreground)&&!KaitakuPreset.APP.equals(pilot.s.currentApp))return false;
  if(!KaitakuPreset.APP.equals(foreground)){pilot.note("開拓うさぎを開いてから自動開始してください");return true;}
  if(!pilot.s.ensureProfile())return true;
  // Rebind even when the selected marker profile is already current: an old
  // Farm engine must never supply recognition or state for the new package.
  if(!KaitakuPreset.APP.equals(pilot.phases.pkg)||!KaitakuPreset.APP.equals(pilot.phases.storage()))pilot.phases.load(KaitakuPreset.APP);
  loaded(pilot.phases);
  if(!pilot.phases.enabled){pilot.note("開拓うさぎのオートマトンを有効にしてください");return true;}
  pilot.resumeManualMarkers=false;pilot.s.stopMarkers();pilot.begin();if(pilot.active)pilot.s.compactPanel(true);return true;
 }
 static int reset(AppPhases p){
  if(!KaitakuPreset.APP.equals(p.pkg))return -1;
  android.content.SharedPreferences prefs=p.context.getSharedPreferences("appPhases",0);
  if(!prefs.edit().putString("presetResetBackup:"+p.storage(),prefs.getString(p.storage(),"{}")).commit())throw new IllegalStateException("現在の設定を退避できませんでした");
  PhaseEngine prior=p.engine;boolean customized=p.customized,enabled=p.enabled;
  try{p.engine=new PhaseEngine();KaitakuPreset.install(p.engine);p.customized=false;p.enabled=true;p.save();return 1;}
  catch(RuntimeException failure){p.engine=prior;p.customized=customized;p.enabled=enabled;throw failure;}
 }
 static boolean running(AutoPilot p){return p!=null&&KaitakuPreset.APP.equals(p.target)&&p.phases!=null&&KaitakuPreset.owns(p.phases.engine)&&!p.phases.customized&&!p.phases.engine.windowRelative;}
 static boolean canTap(AutoPilot p){
  long now=SystemClock.uptimeMillis();boolean play=p.phases.engine.current.equals(KaitakuPreset.P+"main")&&KaitakuPreset.home(p.phaseSamples);
  boolean mini=p.phases.engine.current.equals(KaitakuPreset.P+"mini")&&KaitakuActionEvidence.present(p.phaseSamples,"mini-live");
  boolean ok=p.active&&p.phases.enabled&&(play||mini)&&
   p.phaseSampleTakenAt>0&&p.phaseSampleTakenAt<=now&&now-p.phaseSampleTakenAt<ContinuousTapPolicy.MAX_FRAME_AGE_MS&&
   p.target.equals(p.packageNow())&&p.phaseWindowVersion==p.s.inputTargetVersion&&p.snapshotWindowCurrent(p.phaseWindowScope,p.phaseWindowVersion)&&!p.returning&&!p.actionPending;
  if(!ok)p.s.inputHold="開拓うさぎの新しい主画面を確認中";return ok;
 }
 private KaitakuStartup(){}
}
