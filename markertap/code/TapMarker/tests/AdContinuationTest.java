package jp.tapmarker;
import java.util.*;

/** Shipped recovery decisions with actual replay pixels and simulated input completions.
 * Multi-card button absence and three repeated journeys are synthetic timelines. */
public final class AdContinuationTest {
 static int checks;
 static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 static Map<String,float[]> exit(int width,int height){Map<String,float[]> s=new HashMap<>();AdCloseAutomaton.addNamed(s,AdCloseAutomaton.RIGHT,width*.94f,height*.06f,width,height);return s;}
 static PhaseEngine coin(String state)throws Exception{PhaseEngine e=new PhaseEngine();CoinPreset.install(e,VirtualPresetProfileTest.canonical("tests/video6720-references.bin"));e.strictRuntime=true;AdCloseAutomaton.install(e,CoinPreset.APP);e.enter(CoinPreset.P+state,0);return e;}
 static void spentClose()throws Exception{
  PhaseEngine e=coin("ad");AdExitPolicy policy=new AdExitPolicy();
  for(long now:new long[]{1000,2000}){Map<String,float[]> s=exit(540,1212);policy.filter(e,s,540,1212,now,now);check(s.get(AdCloseAutomaton.RIGHT)[0]==1,"observed Close gets its bounded attempts");policy.controls.sent(AdCloseAutomaton.RIGHT,now);}
  for(long now:new long[]{4000,5000,6000}){
   Map<String,float[]> raw=exit(540,1212);check(RecoveryRequest.visibleBranch(e,raw),"raw Close reproduces the old pre-return cancellation trigger");
   check(!policy.preferVisibleBranch(e,raw,540,1212,now,now),"exhausted drawn Close cannot repeatedly cancel app recovery");
  }
  AdExitPolicy fresh=new AdExitPolicy();Map<String,float[]> s=exit(540,1212);
  check(fresh.preferVisibleBranch(e,s,540,1212,1000,1000),"an untried fresh Close still takes precedence over relaunch");fresh.controls.sent(AdCloseAutomaton.RIGHT,1000);
  check(fresh.preferVisibleBranch(e,exit(540,1212),540,1212,1100,1100),"recent Close completion gets its result-observation interval");
  check(!fresh.preferVisibleBranch(e,exit(540,1212),540,1212,1100,2700),"stale corner image cannot cancel a return");
  e.enter(CoinPreset.P+"verify",7000);PhaseEngine.Watch home=e.rule(CoinPreset.P+"verify-home");Map<String,float[]> game=new HashMap<>();game.put(home.regionId,home.reference.clone());
  check(policy.preferVisibleBranch(e,game,540,1212,7200,7200),"a freshly recognized game cancels the unneeded intent");
  e.enter(CoinPreset.P+"ad",8000);check(policy.preferVisibleBranch(e,exit(540,1212),540,1212,8100,8100),"next advertisement receives its own exit budget");
 }
 static void cards(){
  AdExitAttempts controls=new AdExitAttempts();long now=1000;
  for(int i=0;i<8;i++){
   Map<String,float[]> s=exit(540,1212);controls.select(s,540,1212,now,now);
   check(AdCloseAutomaton.controlPoint(AdCloseAutomaton.RIGHT,s,540,1212)!=null,"same-corner Close is usable on a new observed card: "+i);
   check(controls.preflight(AdCloseAutomaton.RIGHT,exit(540,1212),540,1212),"new card's Close is rechecked before input");controls.sent(AdCloseAutomaton.RIGHT,now);
   now+=400;controls.select(new HashMap<>(),540,1212,now,now);now+=350;controls.select(new HashMap<>(),540,1212,now,now);now+=400;
  }
  Map<String,float[]> capped=exit(540,1212);controls.select(capped,540,1212,now,now);
  check(controls.total==8&&AdCloseAutomaton.controlPoint(AdCloseAutomaton.RIGHT,capped,540,1212)==null,"disappearance cannot bypass the episode's total input cap");
  controls=new AdExitAttempts();now=1000;
  for(int i=0;i<2;i++){Map<String,float[]> s=exit(540,1212);controls.select(s,540,1212,now,now);controls.sent(AdCloseAutomaton.RIGHT,now);now+=1000;}
  controls.select(new HashMap<>(),540,1212,now,now);controls.select(new HashMap<>(),540,1212,now,now+400);
  Map<String,float[]> duplicate=exit(540,1212);controls.select(duplicate,540,1212,now+800,now+800);
  check(duplicate.get(AdCloseAutomaton.RIGHT)[0]==0,"a duplicate missing screenshot does not create a new appearance");
  AdExitAttempts other=new AdExitAttempts();Map<String,float[]> separate=exit(540,1212);other.select(separate,540,1212,1000,1000);
  check(separate.get(AdCloseAutomaton.RIGHT)[0]==1,"another display retains its independent budget");
 }
 static PhaseEngine stock(String app)throws Exception{
  PhaseEngine e=new PhaseEngine();String file=app.equals(CoinPreset.APP)?"tests/video6720-references.bin":app.equals(PoipoiPreset.APP)?"tests/image6727-references.bin":app.equals(BoxPreset.APP)?"tests/video6724-references.bin":app.equals(ShoppingPreset.APP)?"tests/video6717-references.bin":app.equals(FarmPreset.APP)?"tests/farm-replay.bin":"tests/usagi-replay.bin";
  Map<String,float[]> refs=VirtualPresetProfileTest.canonical(file);
  if(app.equals(CoinPreset.APP))CoinPreset.install(e,refs);else if(app.equals(PoipoiPreset.APP))PoipoiPreset.install(e,refs);else if(app.equals(BoxPreset.APP))BoxPreset.install(e,refs);else if(app.equals(ShoppingPreset.APP))ShoppingPreset.install(e,refs);else if(app.equals(FarmPreset.APP))FarmPreset.install(e,refs);else UsagiPreset.install(e,refs);
  e.strictRuntime=true;return e;
 }
 static Map<String,float[]> unknown(PhaseEngine e){
  Map<String,float[]> s=new HashMap<>();for(PhaseEngine.Watch w:e.phases.get(e.current).watches)if(w.reference!=null&&!w.regionId.isEmpty()){float[] raw=new float[w.reference.length];for(int i=0;i<raw.length;i++)raw[i]=w.reference[i]<.5f?1:0;s.put(w.regionId,raw);}return s;
 }
 static void interruptedStates()throws Exception{
  for(String app:PresetRegistry.APPS){PhaseEngine e=stock(app);String p=PresetRegistry.prefix(app);
   for(PhaseEngine.Phase phase:e.phases.values()){
    String name=phase.id.substring(p.length());if(phase.terminal||AdExitPolicy.adState(phase.id)||Arrays.asList("verify","mission-verify","classify","check","external","account","error","unknown").contains(name))continue;
    e.enter(phase.id,0);AdInterruption interruption=new AdInterruption();Map<String,float[]> s=unknown(e);
    boolean measurable=!s.isEmpty();if(!measurable)continue;
    check(!interruption.observe(e,s,1000,1000)&&!interruption.observe(e,s,1800,1800),"brief unknown content does not navigate away from "+phase.id);
    check(!interruption.observe(e,s,1800,2000),"a repeated screenshot is not another observation");
    check(interruption.observe(e,s,3700,3700),"persistent unknown display gets ad observation from "+phase.id);
    check(e.phases.containsKey(AdInterruption.recoveryState(e)),"the recovery phase exists for "+phase.id);
   }
   e.enter(p+"main",0);AdInterruption a=new AdInterruption();Map<String,float[]> s=unknown(e);a.observe(e,s,1000,1000);a.observe(e,s,1800,1800);
   check(!a.observe(e,s,3700,5300),"stale images cannot complete interruption confirmation");
   e.windowRelative=true;check(!a.observe(e,s,7000,7000),"learned window-relative workflows retain their configured behavior");
  }
  PhaseEngine e=coin("claim-wait");check(AdInterruption.recoveryState(e).equals(CoinPreset.P+"mission-ad"),"coin Get interrupted by CM preserves collection intent");
  e=stock(PoipoiPreset.APP);e.enter(PoipoiPreset.P+"claim-wait",0);check(AdInterruption.recoveryState(e).equals(PoipoiPreset.P+"mission-ad"),"Poipoi Get interrupted by CM preserves collection intent");
 }
 static final class Run {
  final PhaseEngine e;final AdaptiveLayout layout;final AdExitPolicy policy=new AdExitPolicy();final AdInterruption interruption=new AdInterruption();
  Map<String,float[]> samples;long now=1000;int width,height;
  Run()throws Exception{e=stock(PoipoiPreset.APP);layout=AdaptiveLayout.install(e,PoipoiPreset.APP,Video6725ReplayTest.refs,VirtualRecoveryReplayTest.evidence);layout.configureTargets(PoipoiPreset.APP,VirtualPresetProfileTest.canonical("tests/visual-targets.bin"));AdCloseAutomaton.install(e,PoipoiPreset.APP);e.enter(PoipoiPreset.P+"main",0);}
  PhaseEngine.Watch frame(String id,long elapsed){now+=elapsed;VirtualPresetProfileTest.Image im=Video6725ReplayTest.images.get(id);width=im.width;height=im.height;samples=layout.sampleRelative(e.activeRegions(),width,height,im,now);policy.filter(e,samples,width,height,now,now);
   if(interruption.observe(e,samples,now,now)){e.enter(AdInterruption.recoveryState(e),now);return null;}e.observe(samples,now);return e.poll();}
  void step(String id,String next){PhaseEngine.Watch first=frame(id,400);check(first==null,"new frame waits for confirmation at "+e.current);PhaseEngine.Watch w=frame(id,350);check(w!=null,"recorded screen produces a transition: "+id+" / "+e.current+" / "+e.diagnostic);if(w.action==PhaseEngine.CENTER)check(e.confirmsAction(w,e.current,e.epoch,samples)&&layout.pointRelative(w,samples,width,height)!=null,"recorded action has fresh bounds");e.finish(w,now);check(e.current.equals(PoipoiPreset.P+next),"recorded "+id+" -> "+next);}
  void ad(String id){check(frame(id,400)==null,"first unexpected CM holds input");check(frame(id,800)==null,"short interruption remains observation");check(frame(id,1800)==null&&e.current.equals(PoipoiPreset.P+"mission-ad"),"unrecognized CM leaves claim/opening wait for the ad handler");check(!PoipoiPreset.playable(e,samples),"CM never authorizes ordinary tapping");}
  void nextCard(String id){check(frame(id,400)==null,"next CM card holds input on its first actual frame");check(frame(id,800)==null,"next CM card remains under observation");check(e.current.equals(PoipoiPreset.P+"mission-ad"),"input acknowledgement retains the collection CM state");check(!PoipoiPreset.playable(e,samples),"second actual CM cannot authorize feeding");}
  void close(){PhaseEngine.Watch w=null;for(int i=0;i<2;i++){now+=400;samples=exit(width,height);policy.filter(e,samples,width,height,now,now);e.observe(samples,now);w=e.poll();if(i==0)check(w==null,"new card's Close needs confirmation");}
   check(w!=null&&w.action==PhaseEngine.CENTER&&AdCloseAutomaton.handles(w.regionId),"confirmed ad exit remains usable across consecutive cards");
   Map<String,float[]> fresh=exit(width,height);check(policy.preflight(w,fresh,width,height)&&e.confirmsAction(w,e.current,e.epoch,fresh),"ad exit gets a fresh independent preflight");policy.controlSent(w,now);e.finish(w,now);check(e.current.equals(PoipoiPreset.P+"mission-ad"),"closing a collection CM retains ad observation and collection intent");
  }
 }
 static void repeatedJourneys()throws Exception{
  VirtualRecoveryReplayTest.load();VirtualRecoveryReplayTest.layoutEvidence();Video6725ReplayTest.load();Run r=new Run();
  for(int cycle=0;cycle<3;cycle++){
   r.step("72.9","mission-tap");r.step("74.2","mission-opening");r.step("75.5","missions");r.step("75.5","claim-wait");
   r.ad("6.8");r.close();r.nextCard("81.5");r.close();
   r.step("84.2","reward");r.step("84.2","mission-opening");r.step("85.2","missions");r.step("85.2","verify");r.step("98.5","main");
   r.frame("98.5",400);check(PoipoiPreset.playable(r.e,r.samples)&&r.layout.mainPointRelative(r.samples,r.width,r.height)!=null,"cycle "+cycle+" reacquires the actual feeding object");
   check(ContinuousTapPolicy.allow(r.e,true,false,false,false,true,r.now,r.now),"cycle "+cycle+" resumes main tapping without a start operation");
  }
 }
 public static void main(String[] args)throws Exception{spentClose();cards();interruptedStates();repeatedJourneys();System.out.println("AdContinuationTest: "+checks+" checks passed; exhausted-close recovery, observed new cards, all factory interruptions and three synthetic two-CM journeys using real Poipoi replay pixels; no Android device operation");}
}
