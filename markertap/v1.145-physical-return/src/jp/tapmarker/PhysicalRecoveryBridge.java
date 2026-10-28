package jp.tapmarker;

import android.content.Intent;
import android.os.SystemClock;
import java.util.Map;
import java.util.WeakHashMap;

/** Physical Kaitaku foreground recovery; all other existing paths delegate unchanged. */
final class PhysicalRecoveryBridge {
 private static final class Run {
  final int token;final String app;final PhaseEngine engine;
  final PhysicalReturnEpisode episode=new PhysicalReturnEpisode();
  final ExternalAdHandoff handoff=new ExternalAdHandoff();
  boolean external,reported;long returnedAt;int launches;String foreground="",result="未送信";
  Run(AutoPilot p){token=p.token;app=p.target;engine=p.phases.engine;}
 }
 private static final WeakHashMap<AutoPilot,Run> runs=new WeakHashMap<>();
 private static boolean supports(AutoPilot p,int expected){
  return p!=null&&p.active&&p.token==expected&&p.phases!=null&&p.phases.enabled&&
   !p.splitPilot.active&&KaitakuStartup.running(p);
 }
 private static Run run(AutoPilot p){
  Run r=runs.get(p);
  if(r==null||r.token!=p.token||!r.app.equals(p.target)||r.engine!=p.phases.engine){r=new Run(p);runs.put(p,r);}
  return r;
 }
 static synchronized boolean defer(AutoPilot p,int expected){
  if(!supports(p,expected)){if(p!=null)runs.remove(p);return ExternalAdHandoffBridge.defer(p,expected);}
  long now=SystemClock.uptimeMillis();Run r=run(p);r.foreground=p.packageNow();
  if(p.target.equals(r.foreground)){
   if(r.external){
    r.external=false;r.returnedAt=now;r.handoff.reset();p.cancelRegisteredReturn();
    p.phaseSamples=new java.util.HashMap<>();p.phaseSampleTakenAt=0;
    String verify="kaitaku-default-verify";
    if(p.phases.engine.phases.containsKey(verify))p.phases.engine.enter(verify,now);
    else p.phases.engine.invalidateObservation();
    p.phases.record("対象ゲームの前面復帰を確認 / 新しい画像で再判定");
   }
   boolean home=p.phaseWindowVersion==p.s.inputTargetVersion&&p.snapshotWindowCurrent(p.phaseWindowScope,p.phaseWindowVersion)&&
    p.phases.engine.current.equals("kaitaku-default-main")&&KaitakuPreset.home(p.phaseSamples);
   if(r.episode.home(home,p.phaseSampleTakenAt,p.phaseWindowVersion,now))r.reported=false;
   return ExternalAdHandoffBridge.defer(p,expected);
  }
  if(!r.external){
   r.external=true;r.episode.begin(now);r.episode.home(false,0,0,now);
   p.cancelRegisteredReturn();p.actionPending=false;p.s.stopMarkers();p.phaseOwnedMarkers=false;
   if(!p.phases.engine.current.equals("kaitaku-default-ad"))p.phases.engine.enter("kaitaku-default-ad",now);
   else p.phases.engine.invalidateObservation();
   p.phases.record("外部画面の復帰待ちを開始 / foreground="+r.foreground);
  }
  if(r.reported){p.stopSingle(true);return true;}
  boolean stable=r.handoff.observe(p.target,r.foreground,true,now)==ExternalAdHandoff.Decision.READY;
  RecoveryEscalation shared=p.adExit.recovery;
  PhysicalReturnEpisode.Decision d=r.episode.decide(stable,now,shared.physicalReturns(),shared.physicalRestarts(),shared.physicalNextAt(),p.lastReturnIntent);
  if(d==PhysicalReturnEpisode.Decision.STOP){fail(p,r,now);return true;}
  if(!p.s.isScreenGuarded()&&!p.s.guardDialog()&&(d==PhysicalReturnEpisode.Decision.RETURN||d==PhysicalReturnEpisode.Decision.RESTART)){
   Intent launch=p.s.getPackageManager().getLaunchIntentForPackage(p.target);
   if(launch==null){r.result="起動先なし";r.episode.unavailable(now);p.phases.record("登録アプリの起動先を取得できません");}
   else{
    boolean restart=d==PhysicalReturnEpisode.Decision.RESTART;
    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|(restart?Intent.FLAG_ACTIVITY_CLEAR_TASK:Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));
    // Share the original attempt counters. No acknowledgement replenishes them.
    p.adExit.returned(now,restart);p.lastReturnIntent=now;r.episode.attempted(now,restart);r.launches++;
    String id="physical-return:"+expected+":"+r.launches;
    try{p.s.startActivity(launch);r.result=id+" / Intent送信 / "+(restart?"タスク再起動":"通常復帰");}
    catch(RuntimeException failure){r.result=id+" / 起動例外："+failure.getClass().getSimpleName();}
    p.phases.record(r.result+" / foreground="+r.foreground);
   }
  }
  p.note("対象ゲームの前面復帰待ち / "+r.episode.duration(now)/1000+"秒 / "+r.result);
  p.schedulePhase(expected,350);return true;
 }
 private static void fail(AutoPilot p,Run r,long now){
  if(!r.reported&&p.s.progressErrors!=null){
   r.reported=true;p.s.progressErrors.fatal(p.target,p.phases.engine,"FOREGROUND_RETURN_FAILED",
    "対象ゲームの前面復帰を確認できません / "+r.episode.duration(now)+"ms / foreground="+r.foreground+" / "+r.result);
  }
  p.stopSingle(true);
 }
 /** DEX call-site receives the original watchdog receiver and unchanged sample. */
 static synchronized ProgressWatchdog.Event observe(ProgressWatchdog watchdog,ProgressWatchdog.Sample sample,long now){
  if(sample.separateAnalysis)for(Map.Entry<AutoPilot,Run> e:runs.entrySet()){
   final AutoPilot p=e.getKey();Run r=e.getValue();
   if(!supports(p,r.token)||sample.run!=r.token||!sample.app.equals(r.app)||p.phases.engine!=r.engine)continue;
   if(r.external){
    if(r.episode.expired(now)&&!r.reported){
     r.reported=true;final int expected=r.token;
     // Let ProgressErrors save its frozen report before invalidating this run.
     p.s.h.post(()->{if(p.active&&p.token==expected)p.stopSingle(true);});
     return new ProgressWatchdog.Event("FOREGROUND_RETURN_FAILED",sample,
      "対象ゲームの前面復帰が90秒以内に完了しません / foreground="+r.foreground+" / "+r.result,r.episode.duration(now));
    }
    sample.expectFrames=false;sample.expectInput=false;
   }else if(r.returnedAt>0&&now>=r.returnedAt&&now-r.returnedAt<15000&&
      (sample.capturedAt<r.returnedAt||sample.analysisAt<r.returnedAt))sample.expectFrames=false;
   break;
  }
  return watchdog.observe(sample,now);
 }
 static synchronized String append(String body,String app){
  for(Map.Entry<AutoPilot,Run> e:runs.entrySet()){
   AutoPilot p=e.getKey();Run r=e.getValue();
   if(!supports(p,r.token)||!app.equals(r.app)||p.phases.engine!=r.engine)continue;
   return body+"\n前面復帰診断：foreground="+r.foreground+" / 撮影保留="+r.external+
    " / 復帰未確認="+r.episode.duration(SystemClock.uptimeMillis())+"ms / "+r.result;
  }
  return body;
 }
 private PhysicalRecoveryBridge(){}
}
