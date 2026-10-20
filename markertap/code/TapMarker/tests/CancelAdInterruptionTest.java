package jp.tapmarker;

import java.util.*;

/** Real previously supplied ad pixels plus explicitly synthetic cancellation,
 * named exits and input completions. The user's unrecorded incident is not replayed. */
public final class CancelAdInterruptionTest {
 static final String P=UsagiPreset.PREFIX;static int checks;
 static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 static final class Run {
  final PhaseEngine e;final AdExitPolicy policy=new AdExitPolicy();final AdaptiveLayout layout;
  Map<String,float[]> samples;
  Run(String state)throws Exception{
   e=AdContinuationTest.stock(UsagiPreset.APP);
   layout=AdaptiveLayout.install(e,UsagiPreset.APP,VirtualPresetProfileTest.canonical("tests/usagi-replay.bin"),VirtualPresetProfileTest.canonical("tests/current-layout-evidence.bin"));
   layout.configureTargets(UsagiPreset.APP,VirtualPresetProfileTest.canonical("tests/visual-targets.bin"));
   AdCloseAutomaton.install(e,UsagiPreset.APP);e.enter(P+state,0);
  }
  PhaseEngine.Watch observe(Map<String,float[]> raw,long at){samples=raw;policy.filter(e,samples,540,1212,at,at);e.observe(samples,at);return e.poll();}
  PhaseEngine.Watch frame(String id,long at){VirtualPresetProfileTest.Image im=Video6750ReplayTest.images.get(id);samples=layout.sampleRelative(e.activeRegions(),im.width,im.height,im,at);policy.filter(e,samples,im.width,im.height,at,at);e.observe(samples,at);return e.poll();}
 }
 static Map<String,float[]> absentCancel(PhaseEngine e,boolean exit){
  Map<String,float[]> raw=new HashMap<>();PhaseEngine.Watch gone=e.rule(P+"キャンセルが閉じた");float[] changed=gone.reference.clone();for(int i=0;i<changed.length;i++)changed[i]=changed[i]<.5f?1:0;raw.put(gone.regionId,changed);
  if(exit)AdCloseAutomaton.addNamed(raw,AdCloseAutomaton.RIGHT,510,72,540,1212);return raw;
 }
 static void competingCancellation()throws Exception{
  Run r=new Run("use-wait");MissionJourney journey=new MissionJourney();journey.route(P+"clear");check(r.observe(absentCancel(r.e,true),1000)==null,"first ad exit still requires confirmation");
  PhaseEngine.Watch w=r.observe(absentCancel(r.e,true),1350);
  check(w!=null&&w.action==PhaseEngine.CENTER&&AdCloseAutomaton.handles(w.regionId),"a visible ad exit must not conflict with cancel disappearance: "+r.e.diagnostic);
  check(!r.samples.containsKey(P+"cancel"),"an ad covering Cancel is not evidence of a completed cancellation");
  check(w.nextPhase.equals(P+"ad"),"confirmed ad exit enters ad observation");
  String before=r.e.current;long epoch=r.e.epoch;
  Map<String,float[]> fresh=absentCancel(r.e,true);r.policy.filter(r.e,fresh,540,1212,1450,1450);
  check(r.policy.preflight(w,fresh,540,1212)&&r.e.confirmsAction(w,before,epoch,fresh),"actual registered exit is rechecked before sending input");
  r.policy.controlSent(w,1450);check(r.e.finishIfCurrent(w,before,epoch,1500)&&r.e.current.equals(P+"ad"),"input acknowledgement cannot claim game recovery");
  check(!r.e.phases.get(r.e.current).markersDuring,"ad keeps normal tapping held");
  check(!r.e.finishIfCurrent(w,before,epoch,1600),"a late completion cannot alter the next state");
  check(journey.route(r.e.current).equals(P+"ad")&&journey.blocksMain(),"cancel interruption retains the pending mission task");
  Video6750ReplayTest.load();check(r.frame("80",2000)==null,"game return needs fresh confirmation after closing the ad");w=r.frame("80",2350);
  check(w!=null&&w.nextPhase.equals(P+"main")&&journey.route(w.nextPhase).equals(P+"open-missions"),"recognized game returns to the unfinished mission instead of ordinary tapping");
 }
 static void realAdvertisements()throws Exception{
  Video6750ReplayTest.load();
  for(String id:new String[]{"85.5"}){
   Run r=new Run("use-wait");check(r.frame(id,1000)==null,"first real ad frame requires confirmation: "+id);PhaseEngine.Watch w=r.frame(id,1350);
   check(w!=null&&w.action==PhaseEngine.CENTER&&AdCloseAutomaton.handles(w.regionId),"recorded ad interrupts cancel confirmation without conflicting branches: "+id+" / "+r.e.diagnostic);
   VirtualPresetProfileTest.Image im=Video6750ReplayTest.images.get(id);float[] point=AdCloseAutomaton.controlPoint(w.regionId,r.samples,im.width,im.height);
   check(point!=null&&point[0]>.85f*im.width&&point[1]<.18f*im.height,"recorded action targets the actual upper-right exit: "+id);
  }
  for(String id:new String[]{"18","28"}){
   Run r=new Run("use-wait");r.frame(id,1000);PhaseEngine.Watch w=r.frame(id,1350);
   check(w!=null&&w.action==PhaseEngine.WAIT&&w.nextPhase.equals(P+"classify"),"an ad without a detected exit goes to screen classification, not a guessed input: "+id);
   r.e.finish(w,1350);w=r.frame(id,9000);
   check(w!=null&&w.action==PhaseEngine.WAIT&&w.nextPhase.equals(P+"ad"),"unidentified actual ad reaches the bounded ad recovery state: "+id);
  }
 }
 static void ordinaryAndProtectedCases()throws Exception{
  Run r=new Run("use-wait");r.observe(absentCancel(r.e,false),1000);PhaseEngine.Watch w=r.observe(absentCancel(r.e,false),1350);
  check(w!=null&&w.id.equals(P+"キャンセルが閉じた")&&w.nextPhase.equals(P+"classify"),"ordinary cancellation still moves to fresh screen classification");
  r=new Run("use-wait");Map<String,float[]> visible=absentCancel(r.e,true);visible.put(P+"cancel",r.e.rule(P+"キャンセルが閉じた").reference.clone());
  check(r.observe(new HashMap<>(visible),1000)==null&&r.observe(new HashMap<>(visible),1350)==null,"the actual game Cancel label blocks a false ad exit");
  check(r.samples.containsKey(P+"cancel")&&r.samples.get(AdCloseAutomaton.RIGHT)[0]==0,"game evidence is retained and stale exits are cleared");
  r=new Run("use-wait");Map<String,float[]> missingPoint=absentCancel(r.e,false);missingPoint.put(AdCloseAutomaton.RIGHT,new float[]{1});r.observe(new HashMap<>(missingPoint),1000);w=r.observe(new HashMap<>(missingPoint),1350);
  check(w!=null&&w.id.equals(P+"キャンセルが閉じた"),"an exit flag without a verified coordinate cannot suppress normal confirmation");
 }
 static void interruptionEvidence()throws Exception{
  Run r=new Run("use-wait");AdInterruption interruption=new AdInterruption();
  check(!interruption.observe(r.e,absentCancel(r.e,true),1000,1000),"one unknown frame cannot redirect the state");
  check(!interruption.observe(r.e,absentCancel(r.e,true),1800,1800),"short interruptions remain under observation");
  check(interruption.observe(r.e,absentCancel(r.e,true),3700,3700),"an ad Close is not proof that the original Cancel dialog is still present");
 }
 static void factoryWaitStates()throws Exception{
  int covered=0;
  for(String app:PresetRegistry.APPS){PhaseEngine e=AdContinuationTest.stock(app);AdCloseAutomaton.install(e,app);
   for(PhaseEngine.Phase phase:e.phases.values()){
    boolean disappearance=false,exit=false;for(PhaseEngine.Watch w:phase.watches){if(w.condition==PhaseEngine.DISAPPEAR)disappearance=true;if(AdCloseAutomaton.handles(w.regionId))exit=true;}
    if(!disappearance||!exit)continue;e.enter(phase.id,0);AdExitPolicy policy=new AdExitPolicy();PhaseEngine.Watch selected=null;
    for(long now:new long[]{1000,1350}){Map<String,float[]> raw=AdContinuationTest.unknown(e);AdCloseAutomaton.addNamed(raw,AdCloseAutomaton.RIGHT,510,72,540,1212);policy.filter(e,raw,540,1212,now,now);e.observe(raw,now);selected=e.poll();}
    check(selected!=null&&selected.action==PhaseEngine.CENTER&&AdCloseAutomaton.handles(selected.regionId),"a proven ad exit interrupts factory disappearance wait: "+phase.id+" / "+e.diagnostic);covered++;
   }
  }
  check(covered>=10,"factory cancellation, clearing and result-wait branches are included");
 }
 public static void main(String[] args)throws Exception{
  competingCancellation();realAdvertisements();ordinaryAndProtectedCases();interruptionEvidence();factoryWaitStates();
  System.out.println("CancelAdInterruptionTest: "+checks+" checks passed; existing ad recording pixels, synthetic cancel/exit conflict and late completion, original cancellation and game-evidence guards; reported unrecorded incident and Android delivery untested");
 }
}
