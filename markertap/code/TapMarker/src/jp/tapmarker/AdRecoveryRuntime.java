package jp.tapmarker;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.IBinder;
import android.os.SystemClock;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Universal ad timeout path. App-specific reward automata are left intact:
 * this routine never taps an advertisement or claims an unverified reward.
 * ADB shell is needed to force-stop another application's task on Android.
 */
final class AdRecoveryRuntime {
  private static final WeakHashMap<AutoPilot,AdTwoSecondRecovery> physical=new WeakHashMap<>();
  private static final class Foreign {String packageName="";long firstAt=-1;}
  private static final WeakHashMap<AutoPilot,Foreign> externalFirst=new WeakHashMap<>();
  private static AdTwoSecondRecovery policy(AutoPilot p){
    synchronized(physical){AdTwoSecondRecovery v=physical.get(p);if(v==null){v=new AdTwoSecondRecovery();physical.put(p,v);}return v;}
  }
  static boolean busy(AutoPilot p){AdTwoSecondRecovery r=policy(p);return r.step()==AdTwoSecondRecovery.Step.HOME||
     r.step()==AdTwoSecondRecovery.Step.KILL||r.step()==AdTwoSecondRecovery.Step.INTENT;}
  private static boolean hasKaitakuHome(Map<String,float[]> samples){
    if(samples==null)return false;
    float[] mark=samples.get("kaitaku-default-home");
    return mark!=null&&mark.length==1&&mark[0]==1;
  }
  static boolean gameHome(String pkg,PhaseEngine e,Map<String,float[]> samples){
    return RecoveryEvidence.home(e,samples)||
      "com.kurashiru.kaitaku.usagi".equals(pkg)&&hasKaitakuHome(samples);
  }
  static boolean advertisement(PhaseEngine e,Map<String,float[]> samples,int width,int height,String pkg){
    if(e==null||samples==null||e.windowRelative||!AdExitPolicy.adState(e.current))return false;
    if(gameHome(pkg,e,samples))return false;
    // A state name alone is NOT ad evidence: a loading/reward screen might
    // enter "-ad" through a timeout. Require a positive ad-specific marker.
    if(PlayableAdEvidence.visible(samples,width,height)||
       AdHomeRecovery.visible(samples,width,height))return true;
    for(String key:new String[]{AdCloseAutomaton.LEFT,AdCloseAutomaton.RIGHT}){
      float[] v=samples.get(key);
      if(v!=null&&v.length==1&&v[0]==1)return true;
    }
    return false;
  }
  static boolean physical(AutoPilot p,int token,Map<String,float[]> samples,int width,int height){
    if(p==null||!p.active||p.token!=token||p.splitPilot.active||p.phases==null||
       p.phases.customized||p.phases.engine.windowRelative||!AdRecoveryShell.known(p.target))return false;
    long now=SystemClock.uptimeMillis();PhaseEngine e=p.phases.engine;
    AdTwoSecondRecovery r=policy(p);boolean home=gameHome(p.target,e,samples);
    if(r.step()==AdTwoSecondRecovery.Step.VERIFY){r.verifiedGame(now,home);return false;}
    boolean ad=advertisement(e,samples,width,height,p.target);
    p.evaluation.observeAd(p.phaseSampleTakenAt,now,ad);
    if(!r.observe(p.target,p.phaseSampleTakenAt,now,ad,home))return false;
    beginPhysical(p,token,r);return true;
  }
  /** External ad handoff has no screenshot from the registered game window.
   * Requires a stable foreign foreground and a pre-existing AD state.
   */
  static boolean external(AutoPilot p,int token){
    if(p==null||!p.active||p.token!=token||p.splitPilot.active||p.phases==null||
       p.phases.customized||!AdRecoveryShell.known(p.target))return false;
    String foreground=p.packageNow();long now=SystemClock.uptimeMillis();
    if(p.target.equals(foreground)){synchronized(externalFirst){externalFirst.remove(p);}return false;}
    if(foreground==null||foreground.isEmpty()||foreground.equals("com.android.systemui")||
       foreground.equals("jp.tapmarker.lite")||!AdExitPolicy.adState(p.phases.engine.current))return false;
    long first;
    AdTwoSecondRecovery r=policy(p);
    synchronized(externalFirst){
      Foreign old=externalFirst.get(p);
      if(old==null){old=new Foreign();externalFirst.put(p,old);}
      if(!foreground.equals(old.packageName)||now<old.firstAt){
        old.packageName=foreground;old.firstAt=now;r.changedForeground();
      }
      first=old.firstAt;
    }
    // Foreign foreground must be stable, and every observation current.
    r.observe(p.target,now,now,true,false);
    if(now-first<AdTwoSecondRecovery.AD_MS)return true;
    if(r.step()==AdTwoSecondRecovery.Step.HOME)beginPhysical(p,token,r);
    return true;
  }
  private static void beginPhysical(AutoPilot p,int token,AdTwoSecondRecovery r){
    p.actionPending=true;p.evaluation.invalidatePending();p.rewardEvidence.reset();p.pendingEvaluationReceipt="";p.s.stopMarkers();p.phaseOwnedMarkers=false;
    p.cancelRegisteredReturn();
    p.phases.record("CM継続2秒を確認 / HOME→ゲーム限定タスク停止→起動Intent");
    if(p.s.busy||p.s.guardDialog()||!p.s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)){
      r.failed(SystemClock.uptimeMillis());finish(p,token,"HOME操作未受理 / タスク停止せず保留");return;
    }
    final long sent=SystemClock.uptimeMillis();
    checkPhysicalHome(p,token,r,sent);
  }
  private static void checkPhysicalHome(AutoPilot p,int token,AdTwoSecondRecovery r,long sent){
    if(!p.active||p.token!=token)return;
    long now=SystemClock.uptimeMillis();
    if(p.homeVisible()){
      if(!r.confirmedHome(now,true)){finish(p,token,"HOME検証期限切れ");return;}
      IBinder binder=VirtualBridgeProvider.connected();
      if(binder==null){r.failed(now);finish(p,token,"ゲームをタスク停止できません：ADB接続が必要");return;}
      final String target=p.target;
      Thread worker=new Thread(()->{
        boolean stopped=false;
        try{stopped=new VirtualProtocol(binder).forceStopKnownGame(target);}
        catch(Exception denied){/* failure is reported without an unverified relaunch */}
        final boolean result=stopped;
        p.s.h.post(()->afterPhysicalKill(p,token,r,result));
      },"MarkerTap-ad-stop-"+target);
      worker.setDaemon(true);worker.start();
      return;
    }
    if(now-sent>=AdTwoSecondRecovery.HOME_DEADLINE_MS){
      r.failed(now);finish(p,token,"HOME表示未確認 / タスク停止を拒否");return;
    }
    p.s.h.postDelayed(()->checkPhysicalHome(p,token,r,sent),100);
  }
  private static void afterPhysicalKill(AutoPilot p,int token,AdTwoSecondRecovery r,boolean stopped){
    if(!p.active||p.token!=token)return;
    long now=SystemClock.uptimeMillis();
    if(!r.confirmedKill(now,stopped)){finish(p,token,"ゲームのタスク停止に失敗 / 起動を保留");return;}
    Intent intent=p.s.getPackageManager().getLaunchIntentForPackage(p.target);
    if(intent==null||intent.getComponent()==null||!p.target.equals(intent.getComponent().getPackageName())){
      r.failed(now);finish(p,token,"登録ゲームの起動Intentが無効");return;
    }
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);
    try{
      p.s.startActivity(intent);
      if(!r.confirmedIntent(SystemClock.uptimeMillis(),true))throw new IllegalStateException("Intent stage");
      p.phases.record("HOME確認・対象ゲーム限定force-stop成功・登録Intent送信 / 進行と報酬は未確認");
      String verify="com.kurashiru.kaitaku.usagi".equals(p.target)?"kaitaku-default-verify":PresetRegistry.verify(p.target);
      if(!verify.isEmpty()&&p.phases.engine.phases.containsKey(verify))
        p.phases.engine.enter(verify,SystemClock.uptimeMillis());
      else p.phases.engine.invalidateObservation();
      p.phaseSamples=new java.util.HashMap<>();p.phaseSampleTakenAt=0;
      finish(p,token,"ゲームを起動 / 新しい主画面と報酬を検証中");
    }catch(Exception failure){r.failed(SystemClock.uptimeMillis());finish(p,token,"起動Intent失敗 / 画面を再確認");}
  }
  private static void finish(AutoPilot p,int token,String reason){
    if(!p.active||p.token!=token)return;
    p.actionPending=false;p.note(reason);p.schedulePhase(token,250);
  }
  static boolean virtual(SplitPilot pilot,SplitPilot.Slot slot,int token,
                         Map<String,float[]> samples,android.graphics.Rect rect){
    if(pilot==null||slot==null||!pilot.valid(token)||!pilot.virtualMode||
       slot.phases.customized||slot.phases.engine.windowRelative||!AdRecoveryShell.known(slot.pkg))return false;
    AdTwoSecondRecovery r=slot.adTwoSecond;PhaseEngine e=slot.phases.engine;
    long now=SystemClock.uptimeMillis();boolean home=gameHome(slot.pkg,e,samples);
    if(r.step()==AdTwoSecondRecovery.Step.VERIFY){r.verifiedGame(now,home);return false;}
    boolean ad=advertisement(e,samples,rect.width(),rect.height(),slot.pkg);
    slot.evaluation.observeAd(slot.observedAt,now,ad);
    if(!r.observe(slot.pkg,slot.observedAt,now,ad,home))return false;
    slot.evaluation.invalidatePending();slot.rewardEvidence.reset();slot.pendingEvaluationReceipt="";
    pilot.pauseMain(slot);slot.home=false;
    VirtualWindows.Window w=pilot.s.pilot.virtualWindows.window(slot.pkg);
    if(w==null){r.failed(now);pilot.later(slot,token,500);return true;}
    pilot.s.pilot.virtualWindows.recoverAd(w,()->pilot.valid(token)&&slot.adTwoSecond==r,
      result->{
        long at=SystemClock.uptimeMillis();
        if(!r.confirmedRemote(at,result))
          pilot.s.pilot.note("広告復帰保留 / HOME・task kill・Intentの証明不十分 ("+result+")");
        else{
          String verify="com.kurashiru.kaitaku.usagi".equals(slot.pkg)?"kaitaku-default-verify":PresetRegistry.verify(slot.pkg);
          if(!verify.isEmpty()&&e.phases.containsKey(verify))e.enter(verify,at);else e.invalidateObservation();
          slot.phases.record("対象仮想画面：HOME→force-stop→Intent送信済 / 報酬未確認");
        }
        pilot.later(slot,token,250);
      });
    return true;
  }
  private AdRecoveryRuntime(){}
}
