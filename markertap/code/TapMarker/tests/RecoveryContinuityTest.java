package jp.tapmarker;

/** Real ordering regressions: store/self-dismiss, failed restore, and mission route. */
public final class RecoveryContinuityTest {
 static int checks;
 static void check(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
 static void naturalReturn(){
  InputTargetRecovery r=new InputTargetRecovery();r.rejected(1000);
  check(!r.confirm(true,999,1800),"pre-rejection image cannot resume");
  check(!r.confirm(true,1100,1800),"one own-app frame is insufficient without a launch");
  check(!r.confirm(true,1100,1800),"reusing the same frame cannot simulate a second observation");
  check(!r.confirm(true,1700,1600),"future timestamp cannot resume");
  check(r.confirm(true,1800,1800),"self-dismissed sheet resumes without any successful restore");
  check(!r.pending&&r.attempts==0,"natural return consumes no restore budget");
 }
 static void afterFailedRestores(){
  InputTargetRecovery r=new InputTargetRecovery();r.rejected(1000);
  int ticket=r.begin(1750);r.finished(ticket,false,1800);
  check(!r.confirm(false,2000,2000),"foreign app cannot resume after failed launch");
  check(!r.confirm(true,2100,2100),"first fresh own frame is held");
  check(r.confirm(true,2700,2700),"failed launch does not lock out a natural return");
  r.rejected(3000);ticket=r.begin(11750);r.finished(ticket,false,11800);
  ticket=r.begin(21750);r.finished(ticket,false,21800);
  check(r.held(),"bounded retries still hold an unresolved display");
  check(!r.confirm(true,22000,22000),"held display still needs fresh confirmation");
  check(r.confirm(true,22500,22500),"held display recovers when user or Android returns to game");
  check(r.attempts==3,"confirmation does not erase the failed-input history");
 }
 static void transientSwitch(){
  InputTargetRecovery a=new InputTargetRecovery(),b=new InputTargetRecovery();String app=PoipoiPreset.APP;
  a.observePackage(app,"com.android.vending",1000,1000);check(!a.pending,"one overlay appearance cannot request relaunch");
  a.observePackage(app,"com.android.vending",1000,1400);check(!a.pending,"cached image is not a new observation");
  a.observePackage(app,app,1600,1600);check(!a.pending,"short-lived sheet returning to game needs no recovery");
  a.observePackage(app,"com.android.vending",2000,2000);a.observePackage(app,"com.android.vending",2800,2800);
  check(a.pending&&!b.pending,"persistent foreign display suspends only its own lane");
  int ticket=a.revision;a.reset();a.finished(ticket,true,3000);check(!a.pending&&!a.inFlight,"stop cancels delayed launch completion");
 }
 static void routes(){
  for(String state:new String[]{"missions","mission-ad","claim-wait","reward","mission-decline"})check(RecoveryRoute.target(PoipoiPreset.APP,PoipoiPreset.P+state,false,false,false).equals(PoipoiPreset.P+"mission-verify"),"poipoi collection retained: "+state);
  check(RecoveryRoute.target(PoipoiPreset.APP,PoipoiPreset.P+"main",false,false,false).equals(PoipoiPreset.P+"verify"),"ordinary play resumes via normal classification");
  check(RecoveryRoute.target(ShoppingPreset.APP,ShoppingPreset.P+"mission-ad",false,false,false).equals(ShoppingPreset.P+"mission-opening"),"shopping mission is not discarded");
  check(RecoveryRoute.target(CoinPreset.APP,CoinPreset.P+"main",false,false,true).equals(CoinPreset.P+"mission-opening"),"coin pending journey survives transient main state");
  check(RecoveryRoute.target(FarmPreset.APP,FarmPreset.P+"main",false,false,false).equals(FarmPreset.P+"verify"),"farm must recognize play again before tapping");
  check(RecoveryRoute.target(PoipoiPreset.APP,"custom-step",true,false,false).equals("custom-step"),"custom graph is not forced into preset");
  check(RecoveryRoute.target(PoipoiPreset.APP,"learned-step",false,true,false).equals("learned-step"),"learned graph keeps its current task");
 }
 public static void main(String[] args){naturalReturn();afterFailedRestores();transientSwitch();routes();System.out.println("RecoveryContinuityTest: "+checks+" checks passed; natural returns, failed restore, per-lane isolation and mission continuity; Android launch untested");}
}
