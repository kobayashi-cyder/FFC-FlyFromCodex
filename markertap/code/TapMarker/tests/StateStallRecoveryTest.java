package jp.tapmarker;
import java.util.*;

/** Actual factory graphs and synthetic timing; Android launch completion is simulated. */
public final class StateStallRecoveryTest {
 static int checks;
 static void check(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
 static PhaseEngine engine(String app)throws Exception{
  String fixture=app.equals(UsagiPreset.APP)?"usagi-current-references.bin":app.equals(FarmPreset.APP)?"farm-replay.bin":app.equals(CoinPreset.APP)?"video6720-references.bin":app.equals(ShoppingPreset.APP)?"video6717-references.bin":app.equals(BoxPreset.APP)?"video6724-references.bin":"image6727-references.bin";
  Map<String,float[]> refs=VirtualPresetProfileTest.canonical("tests/"+fixture);PhaseEngine e=new PhaseEngine();
  if(app.equals(UsagiPreset.APP))UsagiPreset.install(e,refs);else if(app.equals(FarmPreset.APP))FarmPreset.install(e,refs);else if(app.equals(CoinPreset.APP))CoinPreset.install(e,refs);else if(app.equals(ShoppingPreset.APP))ShoppingPreset.install(e,refs);else if(app.equals(BoxPreset.APP))BoxPreset.install(e,refs);else PoipoiPreset.install(e,refs);
  e.strictRuntime=true;AdCloseAutomaton.install(e,app);e.enter(PresetRegistry.verify(app),0);return e;
 }
 static AdExitPolicy stalled(PhaseEngine e,long duration,boolean mission)throws Exception{
  AdExitPolicy ad=new AdExitPolicy();String app=null;
  for(String candidate:PresetRegistry.APPS)if(e.current.startsWith(PresetRegistry.prefix(candidate)))app=candidate;
  int returns=0,restarts=0;long firstRestart=-1;Map<String,float[]> empty=new HashMap<>();
  for(long now=500;now<=duration;now+=500){
   ad.filter(e,empty,320,718,now,now);e.observe(empty,now);PhaseEngine.Watch w=ad.withFallback(e,e.poll(),empty,now);if(w==null)continue;
   if(w.action==PhaseEngine.WAIT){e.finish(w,now);continue;}
   check(w.action==PhaseEngine.RETURN_APP,"unknown pixels cannot cause game input");
   int exit=ad.choose(e,w,empty,now);
   if(exit==PhaseEngine.WAIT||exit==PhaseEngine.RETURN_APP&&!ad.returnAllowed(now)){e.cancelIfCurrent(w,e.current,e.epoch);continue;}
   if(exit==PhaseEngine.BACK){ad.sent(now);e.finish(w,now);continue;}
   boolean restart=ad.restartNeeded(now);if(restart){check(ad.restartCurrent(now),"restart requires a current unresolved frame");restarts++;if(firstRestart<0)firstRestart=now;}else returns++;
   ad.returned(now,restart);e.enter(RecoveryRoute.target(app,e.current,false,false,mission),now);
  }
  check(returns==3&&restarts==2,"763-second stall has a finite recovery ladder: "+returns+" / "+restarts);
  check(firstRestart>=RecoveryEscalation.STALL_MS&&firstRestart<=45000,"same-state entry cannot postpone escalation: "+firstRestart);
  check(ad.recovery.exhausted()&&!ad.returnAllowed(duration),"an unresolved display cannot restart forever");
  check(!ad.status(e,empty,duration).contains("CM終了待ち"),"unknown pixels are not reported as an observed ad");return ad;
 }
 static void reportedCoin()throws Exception{
  PhaseEngine e=engine(CoinPreset.APP);AdExitPolicy ad=stalled(e,763500,false);
  Map<String,float[]> home=new HashMap<>();PhaseEngine.Watch verify=e.rule(CoinPreset.P+"verify-home");home.put(verify.regionId,verify.reference.clone());
  check(!CoinPreset.playable(e,home),"reported flag includes state eligibility");
  check(RecognitionDiagnostics.describe(e,home).contains("home：一致"),"diagnostic exposes exact home pixels while verify holds tapping");
  ad.filter(e,new HashMap<>(home),320,718,764000,764000);check(ad.recovery.exhausted(),"one match cannot replenish budget");e.observe(home,764000);
  ad.filter(e,new HashMap<>(home),320,718,764400,764400);e.observe(home,764400);PhaseEngine.Watch recognized=e.poll();
  check(recognized!=null&&recognized.nextPhase.equals(CoinPreset.P+"main"),"fresh home resumes after exhausted recovery");e.finish(recognized,764400);
  check(CoinPreset.playable(e,home)&&!ad.recovery.exhausted(),"visual recovery releases main and a later episode");
  e.enter(CoinPreset.P+"verify",765000);check(!ad.restartNeeded(765000),"completed episode cannot authorize an immediate restart");
 }
 static void lateClose()throws Exception{
  PhaseEngine e=engine(CoinPreset.APP);AdExitPolicy ad=stalled(e,763500,false);Map<String,float[]> x=new HashMap<>();AdCloseAutomaton.putControl(x,AdCloseAutomaton.RIGHT,310,12,320,718);
  ad.filter(e,x,320,718,764000,764000);e.observe(x,764000);ad.filter(e,x,320,718,764400,764400);e.observe(x,764400);PhaseEngine.Watch w=e.poll();
  check(w!=null&&w.action==PhaseEngine.CENTER&&AdCloseAutomaton.handles(w.regionId),"late Close wins after restart limit");
  Map<String,float[]> fresh=new HashMap<>();AdCloseAutomaton.putControl(fresh,AdCloseAutomaton.RIGHT,310,12,320,718);
  check(ad.preferVisibleBranch(e,fresh,320,718,764800,764800),"fresh Close cancels queued recovery");
 }
 static void freshness(){
  RecoveryEscalation a=new RecoveryEscalation(),b=new RecoveryEscalation();a.observe(false,1000,1000);a.observe(false,1000,1300);a.sent(5000,false);a.sent(10000,false);a.sent(15000,false);
  check(!a.restartReady(31000),"old/repeated pixels cannot authorize restart");a.observe(false,32000,31000);check(!a.restartReady(31000),"future image cannot authorize restart");a.observe(false,31000,31000);check(a.restartReady(31000),"fresh unknown image confirms escalation");
  check(!b.restartReady(31000)&&b.normalAllowed(31000),"another display has an independent budget");a.sent(31000,true);check(!a.restartReady(31000)&&a.unknownCurrent(31000),"charging restart retains dispatch freshness guard");
  a.observe(true,31500,31500);check(!a.unknownCurrent(31500),"game image immediately revokes dispatch");a.reset();check(!a.unknownCurrent(31500)&&!a.restartReady(31500),"stop invalidates previous authority");
  b.observe(true,32000,32000);b.observe(true,32400,32400);check(!b.normalAllowed(32400)&&b.normalAllowed(34000),"old game evidence cannot block external-app recovery forever");
 }
 static void mission()throws Exception{
  PhaseEngine e=engine(CoinPreset.APP);CoinMissionJourney journey=new CoinMissionJourney();journey.route(CoinPreset.P+"missions");
  for(String state:new String[]{"verify","ad","external","mission-ad"})check(RecoveryRoute.target(CoinPreset.APP,CoinPreset.P+state,false,false,journey.blocksMain()).equals(CoinPreset.P+"mission-opening"),"restore keeps collection goal: "+state);
  journey.reconcile(e,1000);check(journey.blocksMain(),"restoring the app is not verified Play");
 }
 public static void main(String[] args)throws Exception{
  reportedCoin();lateClose();freshness();mission();for(String app:PresetRegistry.APPS)if(!app.equals(CoinPreset.APP))stalled(engine(app),763500,false);
  System.out.println("StateStallRecoveryTest: "+checks+" checks; 763-second unknown-display episodes, six factory graphs, finite escalation, late Close, fresh-image recovery and mission continuity; Android launch untested");
 }
}
