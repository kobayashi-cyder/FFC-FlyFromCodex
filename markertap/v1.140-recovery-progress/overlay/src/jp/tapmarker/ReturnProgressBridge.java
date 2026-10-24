package jp.tapmarker;

import android.os.SystemClock;
import java.util.Map;
import java.util.WeakHashMap;

/** Surgical replacement of the preserved AutoPilot return preflight. */
final class ReturnProgressBridge {
 private static final class Run {
  final int token;final String app;final PhaseEngine engine;
  final ReturnProgressGate gate=new ReturnProgressGate();
  long observedAt=-1;
  Run(AutoPilot p){token=p.token;app=p.target;engine=p.phases.engine;}
 }
 private static final WeakHashMap<AutoPilot,Run> runs=new WeakHashMap<>();
 private ReturnProgressBridge(){}
 private static boolean supports(AutoPilot p){
  return p!=null&&p.active&&p.phases!=null&&p.phases.engine!=null&&p.phases.enabled&&!p.phases.customized&&
   !p.phases.engine.windowRelative&&(p.farmActive()||p.usagiPresetActive());
 }
 private static Run run(AutoPilot p){
  Run r=runs.get(p);
  if(r==null||r.token!=p.token||!r.app.equals(p.target)||r.engine!=p.phases.engine){r=new Run(p);runs.put(p,r);}
  return r;
 }
 static String report(AutoPilot p,long now){Run r=runs.get(p);return r==null?"取り消し監視：未開始":r.gate.report(now);}
 static boolean monitor(AutoPilot p,int expected){
  if(!supports(p)||p.token!=expected){if(p!=null)runs.remove(p);return false;}
  Run r=run(p);long now=SystemClock.uptimeMillis();
  if(p.target.equals(p.packageNow())){
   long at=p.phaseSampleTakenAt;
   r.gate.home(p.phaseWindowVersion==p.s.inputTargetVersion&&RecoveryEvidence.home(p.phases.engine,p.phaseSamples),at,p.s.inputTargetVersion,now);
   if(at>r.observedAt){r.observedAt=at;observed(p,r,"実画面の状態確認",now);}
  }
  if(r.gate.expired(now)){stop(p,r,now);return true;}
  return false;
 }
 private static void observed(AutoPilot p,Run r,String reason,long now){
  if(p.s.progressErrors==null)return;
  p.s.progressErrors.journal.observed(p.target,0,r.token,p.phases.engine,p.phaseSamples,
    p.phaseSampleTakenAt,p.recognition,p.recognition,reason+" / "+r.gate.report(now)+
    " / window_version="+p.s.inputTargetVersion);
 }
 private static void stop(AutoPilot p,Run r,long now){
  p.s.stopMarkers();p.phaseOwnedMarkers=false;
  observed(p,r,"復帰取り消し後の進捗なし",now);
  if(p.s.progressErrors!=null)p.s.progressErrors.fatal(p.target,p.phases.engine,
    "RECOVERY_NO_PROGRESS","復帰の取り消し後も主画面を確認できません："+r.gate.report(now));
  p.stopSingle(true);
 }
 // The false result preserves the untouched original for custom/other automata.
 static boolean intercept(AutoPilot p,int expected,int request,Runnable done,boolean viaHome){
  if(!supports(p))return false;
  verify(p,expected,request,done,viaHome);return true;
 }
 private static String visibleRule(PhaseEngine e,Map<String,float[]> samples){
  PhaseEngine.Phase phase=e.phases.get(e.current);if(phase==null)return "";
  String found="";
  for(PhaseEngine.Watch w:phase.watches){
   if(w.condition!=PhaseEngine.MATCH||w.action==PhaseEngine.RETURN_APP||w.reference==null||
      !e.eligible(w)||!(w.distance(samples.get(w.regionId))<=w.threshold)||!e.guardsMatch(w,samples))continue;
   // Ambiguity must not be used as evidence to cancel recovery.
   if(!found.isEmpty())return "";
   found=w.id;
  }
  return found;
 }
 private static void verify(AutoPilot p,int expected,int request,Runnable done,boolean viaHome){
  if(!p.validReturn(expected,request))return;
  if(p.s.isScreenGuarded()||p.s.guardDialog()){
   p.s.h.postDelayed(()->verify(p,expected,request,done,viaHome),350);return;
  }
  if(!p.target.equals(p.packageNow())||p.phases.engine.activeRegions().isEmpty()){
   p.launchRegisteredReturn(expected,request,done,viaHome);return;
  }
  final long version=p.s.inputTargetVersion;
  p.snapshot(expected,(raw,embed,width,height)->{
   if(!p.validReturn(expected,request))return;
   if(version!=p.s.inputTargetVersion||!p.target.equals(p.packageNow())){
    p.s.h.postDelayed(()->verify(p,expected,request,done,viaHome),350);return;
   }
   long now=SystemClock.uptimeMillis();
   p.adExit.filter(p.phases.engine,p.phaseSamples,width,height,p.phaseSampleTakenAt,now);
   if(!p.validReturn(expected,request))return; // Filtering may change the state.
   Run r=run(p);
   r.gate.home(RecoveryEvidence.home(p.phases.engine,p.phaseSamples),p.phaseSampleTakenAt,version,now);
   String candidate=visibleRule(p.phases.engine,p.phaseSamples);
   ReturnProgressGate.Decision d=r.gate.decide(request,candidate,p.phases.engine.current,
     p.phases.engine.epoch,version,p.phaseSampleTakenAt,now,p.adExit.controls.cooling(now));
   observed(p,r,"復帰直前："+d+" / rule="+candidate+" / frame_at="+p.phaseSampleTakenAt,now);
   if(d==ReturnProgressGate.Decision.STOP){stop(p,r,now);return;}
   if(d==ReturnProgressGate.Decision.WAIT){
    // Keep this request alive until a distinct fresh frame confirms a branch.
    // The regular phase loop can still complete that branch in the meantime.
    p.s.h.postDelayed(()->verify(p,expected,request,done,viaHome),350);
    p.schedulePhase(expected,350);return;
   }
   if(d==ReturnProgressGate.Decision.CANCEL_RETURN){
    RecoveryRequest.release(p.phases.engine);p.cancelRegisteredReturn();
    p.phases.record("新しい異なる2枚で操作候補を確認 / 復帰要求を取り消し："+candidate+
      " / "+r.gate.report(now));
    p.schedulePhase(expected,0);return;
   }
   if(viaHome&&!AdHomeRecovery.visible(p.phaseSamples,width,height)){
    p.cancelRegisteredReturn();p.phases.record("CMの目印が変わったためホーム操作を保留・再判定");
    p.schedulePhase(expected,120);return;
   }
   p.phases.record("復帰を取り消す根拠が未確定または上限 / 既存の復帰処理へ / "+r.gate.report(now));
   p.launchRegisteredReturn(expected,request,done,viaHome);
  });
 }
}
