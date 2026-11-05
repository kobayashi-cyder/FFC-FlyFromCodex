package jp.tapmarker;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.IBinder;
import android.os.SystemClock;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * CM検知関数の実行部。対象ゲームごと/画面ごとに状態を分離する。
 * First: positive CM evidence -> HOME -> verified launcher -> registered Intent.
 * Next: fresh CM evidence persisting >=2s AFTER relaunch ->
 *       HOME -> game-only force-stop -> registered Intent.
 * No advertisement completion or reward credit is inferred by recovery.
 */
final class AdRecoveryRuntime {
  private static final class PhysicalState {
    final CmDetectionFunction cm=new CmDetectionFunction();
    int token=Integer.MIN_VALUE;
    String externalPkg="";
    long externalSince=-1;
  }
  private static final WeakHashMap<AutoPilot,PhysicalState> physical=new WeakHashMap<>();
  private static PhysicalState state(AutoPilot p){
    synchronized(physical){
      PhysicalState s=physical.get(p);
      if(s==null){s=new PhysicalState();physical.put(p,s);}
      if(s.token!=p.token){
        s.cm.reset();s.token=p.token;s.externalPkg="";s.externalSince=-1;
      }
      return s;
    }
  }
  static boolean busy(AutoPilot p){
    CmDetectionFunction.Step step=state(p).cm.step();
    return step==CmDetectionFunction.Step.HOME||
           step==CmDetectionFunction.Step.KILL||
           step==CmDetectionFunction.Step.INTENT;
  }
  static boolean gameHome(String pkg,PhaseEngine e,Map<String,float[]> samples){
    return RecoveryEvidence.home(e,samples)||KaitakuPreset.APP.equals(pkg)&&
      (KaitakuPreset.home(samples)||KaitakuActionEvidence.gamePage(samples)||
       KaitakuActionEvidence.present(samples,"tap-ad-start"));
  }
  static boolean advertisement(PhaseEngine e,Map<String,float[]> samples,
                               int width,int height,String pkg){
    if(e==null||samples==null||e.windowRelative||!AdRecoveryShell.known(pkg))
      return false;
    if(KaitakuPreset.APP.equals(pkg)&&samples.containsKey(KaitakuFullScreenAd.KEY))return true;
    if(gameHome(pkg,e,samples))return false;
    // Named states and timeouts alone are not proof. Full-screen CM may
    // interrupt main/verify/upgrade/claim, so inspect positive evidence there.
    if(PlayableAdEvidence.visible(samples,width,height)||
       AdHomeRecovery.visible(samples,width,height))return true;
    for(String key:new String[]{AdCloseAutomaton.LEFT,AdCloseAutomaton.RIGHT}){
      float[] v=samples.get(key);
      if(v!=null&&v.length==1&&v[0]==1)return true;
    }
    return false;
  }

  static boolean physical(AutoPilot p,int token,Map<String,float[]> samples,
                          int width,int height){
    if(p==null||!p.active||p.token!=token||p.splitPilot.active||p.phases==null||
       p.phases.customized||p.phases.engine.windowRelative||
       !AdRecoveryShell.known(p.target))return false;
    PhysicalState st=state(p);CmDetectionFunction cm=st.cm;
    long now=SystemClock.uptimeMillis();
    boolean home=gameHome(p.target,p.phases.engine,samples);
    boolean ad=advertisement(p.phases.engine,samples,width,height,p.target);
    p.evaluation.observeAd(p.phaseSampleTakenAt,now,ad);
    CmDetectionFunction.Action action=
      cm.observe(p.target,p.phaseSampleTakenAt,now,ad,home);
    if(action==CmDetectionFunction.Action.NONE){
      if(!waitingOnFullScreenAd(cm,samples))return false;
      p.s.stopMarkers();p.phaseOwnedMarkers=false;p.schedulePhase(token,120);return true;
    }
    beginPhysical(p,token,cm,action);return true;
  }

  /** External ad package is a weaker signal. Only a previously confirmed
   * game ad state and two stable foreign-foreground observations may trigger
   * initial HOME. Never infer successful reward receipt from this path. */
  static boolean external(AutoPilot p,int token){
    if(p==null||!p.active||p.token!=token||p.splitPilot.active||p.phases==null||
       p.phases.customized||!AdRecoveryShell.known(p.target))return false;
    PhysicalState st=state(p);CmDetectionFunction cm=st.cm;
    String foreground=p.packageNow();long now=SystemClock.uptimeMillis();
    if(p.target.equals(foreground)){
      st.externalPkg="";st.externalSince=-1;return false;
    }
    if(cm.step()==CmDetectionFunction.Step.VERIFY){
      // The launcher or a foreign app is not a post-Intent image of this CM.
      cm.changedForeground();return false;
    }
    if(foreground==null||foreground.isEmpty()||foreground.equals("com.android.systemui")||
       foreground.startsWith("jp.tapmarker.")||p.homeVisible()){
      st.externalPkg="";st.externalSince=-1;cm.changedForeground();return false;
    }
    boolean witnessed=AdExitPolicy.adState(p.phases.engine.current);
    if(!witnessed)return false;
    if(!foreground.equals(st.externalPkg)||now<st.externalSince){
      st.externalPkg=foreground;st.externalSince=now;cm.changedForeground();
    }
    // Stable external application in a known ad handoff is a separate signal.
    CmDetectionFunction.Action action=cm.observe(
      p.target,now,now,true,false);
    if(action!=CmDetectionFunction.Action.NONE)beginPhysical(p,token,cm,action);
    return witnessed;
  }

  private static void invalidateReward(AutoPilot p){
    SharedPointClaimArbiter.release(p.evaluation,p.pendingEvaluationReceipt);
    p.evaluation.invalidatePending();p.rewardEvidence.reset();
    p.farmPassive.reset();p.pendingEvaluationReceipt="";
  }
  private static void beginPhysical(AutoPilot p,int token,CmDetectionFunction cm,
                                    CmDetectionFunction.Action action){
    p.actionPending=true;invalidateReward(p);p.s.stopMarkers();
    p.phaseOwnedMarkers=false;p.cancelRegisteredReturn();
    p.phases.record(action==CmDetectionFunction.Action.HOME_KILL?
      "CM残留2秒：HOME→対象ゲーム限定task kill→登録Intent（報酬未確定）":
      "CM検知：HOME→登録Intent（task killなし、報酬未確定）");
    if(p.s.busy||p.s.guardDialog()||
       !p.s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)){
      cm.failed(SystemClock.uptimeMillis());
      finish(p,token,"HOME操作未受理 / 強制停止なしで保留");return;
    }
    checkPhysicalHome(p,token,cm,SystemClock.uptimeMillis());
  }
  private static void checkPhysicalHome(AutoPilot p,int token,CmDetectionFunction cm,
                                        long sent){
    if(!p.active||p.token!=token)return;
    long now=SystemClock.uptimeMillis();
    if(p.homeVisible()){
      if(!cm.confirmedHome(now,true)){
        finish(p,token,"HOME確認が遅延 / 強制停止せず保留");return;
      }
      if(!cm.requiresKill()){
        launchPhysical(p,token,cm,false);return;
      }
      IBinder binder=VirtualBridgeProvider.connected();
      if(binder==null){
        cm.failed(now);
        finish(p,token,"CM残留：ADB接続なし / 対象アプリ停止を保留");return;
      }
      final String target=p.target;
      Thread worker=new Thread(()->{
        boolean stopped=false;
        try{stopped=new VirtualProtocol(binder).forceStopKnownGame(target);}
        catch(Exception denied){/* no speculative relaunch */}
        final boolean result=stopped;
        p.s.h.post(()->afterPhysicalKill(p,token,cm,result));
      },"MarkerTap-cm-stop-"+target);
      worker.setDaemon(true);worker.start();
      return;
    }
    if(now-sent>=CmDetectionFunction.HOME_DEADLINE_MS){
      cm.failed(now);finish(p,token,"HOME画面を確認できず / 停止を拒否");return;
    }
    p.s.h.postDelayed(()->checkPhysicalHome(p,token,cm,sent),100);
  }
  private static void afterPhysicalKill(AutoPilot p,int token,
                                        CmDetectionFunction cm,boolean stopped){
    if(!p.active||p.token!=token)return;
    if(!cm.confirmedKill(SystemClock.uptimeMillis(),stopped)){
      finish(p,token,"対象ゲームのtask kill失敗 / 再起動を保留");return;
    }
    launchPhysical(p,token,cm,true);
  }
  private static void launchPhysical(AutoPilot p,int token,
                                     CmDetectionFunction cm,boolean killed){
    long now=SystemClock.uptimeMillis();
    Intent intent=p.s.getPackageManager().getLaunchIntentForPackage(p.target);
    if(intent==null||intent.getComponent()==null||
       !p.target.equals(intent.getComponent().getPackageName())){
      cm.failed(now);finish(p,token,"登録ゲームの起動Intentが無効");return;
    }
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|
       (killed?Intent.FLAG_ACTIVITY_CLEAR_TASK:
         Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));
    try{
      p.s.startActivity(intent);
      if(!cm.confirmedIntent(SystemClock.uptimeMillis(),true))
        throw new IllegalStateException("CM Intent state");
      p.phases.record(killed?
        "HOME・対象ゲーム停止・Intent送信 / 新鮮な主画面を再確認":
        "HOME・Intent送信（未停止） / CM残留2秒判定と主画面確認");
      setVerify(p.target,p.phases.engine,SystemClock.uptimeMillis());
      p.phaseSamples=new java.util.HashMap<>();p.phaseSampleTakenAt=0;
      finish(p,token,"ゲーム再起動を要求 / 次の画像で進行と報酬を検証");
    }catch(Exception failure){
      cm.failed(SystemClock.uptimeMillis());
      finish(p,token,"登録Intent実行失敗 / 状態観測を再開");
    }
  }
  private static void setVerify(String pkg,PhaseEngine e,long at){
    String verify=KaitakuPreset.APP.equals(pkg)?
      "kaitaku-default-verify":PresetRegistry.verify(pkg);
    if(!verify.isEmpty()&&e.phases.containsKey(verify))e.enter(verify,at);
    else e.invalidateObservation();
  }
  private static void finish(AutoPilot p,int token,String reason){
    if(!p.active||p.token!=token)return;
    p.actionPending=false;p.note(reason);p.schedulePhase(token,250);
  }

  static boolean virtual(SplitPilot pilot,SplitPilot.Slot slot,int token,
                         Map<String,float[]> samples,android.graphics.Rect rect){
    if(pilot==null||slot==null||!pilot.valid(token)||!pilot.virtualMode||
       slot.phases.customized||slot.phases.engine.windowRelative||
       !AdRecoveryShell.known(slot.pkg))return false;
    CmDetectionFunction cm=slot.cm;PhaseEngine e=slot.phases.engine;
    long now=SystemClock.uptimeMillis();
    boolean home=gameHome(slot.pkg,e,samples);
    boolean ad=advertisement(e,samples,rect.width(),rect.height(),slot.pkg);
    slot.evaluation.observeAd(slot.observedAt,now,ad);
    CmDetectionFunction.Action action=
      cm.observe(slot.pkg,slot.observedAt,now,ad,home);
    if(action==CmDetectionFunction.Action.NONE){
      if(!waitingOnFullScreenAd(cm,samples))return false;
      pilot.pauseMain(slot);slot.home=false;pilot.later(slot,token,150);return true;
    }
    SharedPointClaimArbiter.release(slot.evaluation,slot.pendingEvaluationReceipt);
    slot.evaluation.invalidatePending();slot.rewardEvidence.reset();
    slot.farmPassive.reset();slot.pendingEvaluationReceipt="";
    pilot.pauseMain(slot);slot.home=false;
    VirtualWindows.Window w=pilot.s.pilot.virtualWindows.window(slot.pkg);
    if(w==null){cm.failed(now);pilot.later(slot,token,500);return true;}
    java.util.function.BooleanSupplier valid=
      ()->pilot.valid(token)&&slot.cm==cm&&!slot.stopped;
    java.util.function.IntConsumer finished=bits->{
      long at=SystemClock.uptimeMillis();
      if(!cm.confirmedRemote(at,bits)){
        pilot.s.pilot.note("CM復帰保留 / "+(action==CmDetectionFunction.Action.HOME_KILL?
          "HOME・kill・Intent":"HOME・Intent")+" 証拠不足 ("+bits+")");
      }else{
        setVerify(slot.pkg,e,at);
        slot.phases.record(action==CmDetectionFunction.Action.HOME_KILL?
          "仮想画面CM残留2秒：HOME→対象ゲーム停止→Intent / 報酬未確定":
          "仮想画面CM検知：HOME→Intent（停止なし） / 新画像を再確認");
      }
      pilot.later(slot,token,250);
    };
    if(action==CmDetectionFunction.Action.HOME_KILL)
      pilot.s.pilot.virtualWindows.recoverAd(w,valid,finished);
    else pilot.s.pilot.virtualWindows.returnAd(w,valid,finished);
    return true;
  }
  private static boolean waitingOnFullScreenAd(CmDetectionFunction cm,Map<String,float[]> samples){
    return samples.containsKey(KaitakuFullScreenAd.KEY)&&
      (cm.step()==CmDetectionFunction.Step.OBSERVE||cm.step()==CmDetectionFunction.Step.VERIFY);
  }
  private AdRecoveryRuntime(){}
}
