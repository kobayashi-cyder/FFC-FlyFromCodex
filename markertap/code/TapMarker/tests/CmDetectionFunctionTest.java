package jp.tapmarker;

/** Pure-Java regression for the user-specified CM -> HOME -> Intent, then
 * persistent CM >=2s -> HOME -> kill -> Intent sequence. */
public final class CmDetectionFunctionTest {
  private static int checks;
  private static void yes(boolean ok,String message){
    checks++;if(!ok)throw new AssertionError(message);
  }
  private static CmDetectionFunction.Action ad(CmDetectionFunction p,long at,long now){
    return p.observe("com.kurashiru.kaitaku.usagi",at,now,true,false);
  }
  public static void main(String[] args){
    CmDetectionFunction p=new CmDetectionFunction();
    yes(ad(p,100,100)==CmDetectionFunction.Action.NONE,"one frame cannot start");
    yes(ad(p,100,500)==CmDetectionFunction.Action.NONE,"duplicate frame cannot start");
    yes(ad(p,250,250)==CmDetectionFunction.Action.HOME_INTENT,"positive detection starts immediate HOME");
    yes(p.step()==CmDetectionFunction.Step.HOME,"first HOME");
    yes(!p.confirmedKill(300,true),"force-stop forbidden before HOME and retry");
    yes(p.confirmedHome(350,true),"HOME visible");
    yes(!p.requiresKill()&&p.step()==CmDetectionFunction.Step.INTENT,"first recovery cannot kill");
    yes(p.confirmedIntent(400,true),"first registered intent");
    yes(ad(p,350,450)==CmDetectionFunction.Action.NONE,"pre-launch frame ignored");
    yes(ad(p,500,500)==CmDetectionFunction.Action.NONE,"first post-launch positive");
    yes(ad(p,1400,1400)==CmDetectionFunction.Action.NONE,"less than two seconds");
    yes(ad(p,2600,2600)==CmDetectionFunction.Action.HOME_KILL,"two seconds of persistent ad escalates");
    yes(!p.confirmedKill(2610,true),"kill cannot precede confirmed second HOME");
    yes(p.confirmedHome(2650,true),"second HOME confirmed");
    yes(p.requiresKill(),"only second recovery requires kill");
    yes(p.confirmedKill(2700,true),"target-only shell kill acknowledged");
    yes(p.confirmedIntent(2750,true),"relaunch after confirmed kill");
    yes(p.step()==CmDetectionFunction.Step.VERIFY,"fresh main screen is still required");
    yes(p.observe("com.kurashiru.kaitaku.usagi",2820,2820,false,true)==CmDetectionFunction.Action.NONE,
        "verified game home does not restart recovery");
    yes(p.step()==CmDetectionFunction.Step.OBSERVE,"verified game re-arms detection");
    yes(ad(p,3000,3000)==CmDetectionFunction.Action.NONE,"next CM still needs two frames");
    yes(ad(p,3150,3150)==CmDetectionFunction.Action.HOME_INTENT,"next CM inside the old 60s hold recovers");
    yes(p.confirmedRemote(3250,5),"second independent recovery");
    p.observe("com.kurashiru.kaitaku.usagi",3300,3300,false,true);
    ad(p,3500,3500);
    yes(ad(p,3650,3650)==CmDetectionFunction.Action.HOME_INTENT,"third confirmed episode is allowed");
    yes(p.confirmedRemote(3750,5),"third independent recovery");

    CmDetectionFunction noKill=new CmDetectionFunction();
    ad(noKill,100,100);ad(noKill,250,250);
    yes(!noKill.confirmedHome(350,false),"unconfirmed HOME forbids Intent and kill");
    yes(noKill.step()==CmDetectionFunction.Step.HOLD,"failure holds");
    noKill.reset();ad(noKill,100,100);ad(noKill,300,300);
    noKill.confirmedHome(350,true);noKill.confirmedIntent(360,true);
    noKill.observe("com.kurashiru.kaitaku.usagi",400,400,false,true);
    yes(noKill.step()==CmDetectionFunction.Step.OBSERVE,"resolved ad never escalates");

    CmDetectionFunction interrupted=new CmDetectionFunction();
    ad(interrupted,100,100);ad(interrupted,200,200);
    yes(interrupted.confirmedRemote(300,5),"relaunch before residual detection");
    ad(interrupted,500,500);
    interrupted.changedForeground();
    yes(ad(interrupted,2600,2600)==CmDetectionFunction.Action.NONE,
        "foreign foreground interrupts residual evidence");
    yes(ad(interrupted,4700,4700)==CmDetectionFunction.Action.HOME_KILL,
        "two seconds of verified residual CM after returning to the app");
    interrupted.failed(4800);
    yes(ad(interrupted,5100,5100)==CmDetectionFunction.Action.NONE,"failed episode still cools down");

    CmDetectionFunction stale=new CmDetectionFunction();
    stale.observe("com.kurashiru.kaitaku.usagi",100,2100,true,false);
    yes(stale.step()==CmDetectionFunction.Step.OBSERVE,"stale screenshot cannot detect CM");
    ad(stale,3000,3000);ad(stale,3500,3500);
    yes(!stale.confirmedRemote(3600,7),"first remote recovery must NOT force-stop");
    stale.reset();ad(stale,100,100);ad(stale,200,200);
    yes(stale.confirmedRemote(250,5),"remote HOME+Intent verified");
    yes(ad(stale,350,350)==CmDetectionFunction.Action.NONE,"residual starts from return");
    yes(ad(stale,2450,2450)==CmDetectionFunction.Action.HOME_KILL,"virtual residual ad escalates");
    yes(stale.confirmedRemote(2500,7),"virtual escalation requires all three proofs");
    yes(stale.step()==CmDetectionFunction.Step.VERIFY,"remote waits for game proof");
    CmDetectionFunction split=new CmDetectionFunction();
    yes(ad(split,100,100)==CmDetectionFunction.Action.NONE,"second display independent");
    yes(split.step()==CmDetectionFunction.Step.OBSERVE,"per-lane state isolated");
    System.out.println("CmDetectionFunctionTest: "+checks+" checks passed");
  }
}
