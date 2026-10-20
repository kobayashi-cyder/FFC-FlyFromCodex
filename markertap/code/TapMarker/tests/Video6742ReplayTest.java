package jp.tapmarker;

import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** Actual recording pixels; input completions, event order and repeated journeys are simulated. */
public final class Video6742ReplayTest {
 static int checks;
 static final Map<String,VirtualPresetProfileTest.Image> images=new LinkedHashMap<>();
 static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 static void load()throws Exception{
  try(DataInputStream in=new DataInputStream(new GZIPInputStream(new FileInputStream("tests/video6742-replay.bin.gz")))){
   int n=in.readInt();for(int i=0;i<n;i++)images.put(in.readUTF(),new VirtualPresetProfileTest.Image(in));
   check(n==14&&in.read()==-1,"all native-size held-out recording frames read");
  }
 }
 static final class Run {
  final PhaseEngine e=new PhaseEngine();final AdaptiveLayout layout;final AdExitPolicy exits=new AdExitPolicy();final BancAutonomy banc=new BancAutonomy();
  long now=1000;Map<String,float[]> samples;int width=1080,height=2424;
  Run(String state)throws Exception{
   Map<String,float[]> refs=VirtualPresetProfileTest.canonical("tests/farm-replay.bin");FarmPreset.install(e,refs);
   layout=AdaptiveLayout.install(e,FarmPreset.APP,refs,VirtualPresetProfileTest.canonical("tests/current-layout-evidence.bin"));
   layout.configureTargets(FarmPreset.APP,VirtualPresetProfileTest.canonical("tests/visual-targets.bin"));
   AdCloseAutomaton.install(e,FarmPreset.APP);FarmControlEvidence.install(e);new UpgradeEngine(e,FarmPreset.APP);
   e.strictRuntime=true;e.enter(FarmPreset.P+state,0);banc.reset(0);
  }
  Map<String,float[]> sample(String id,long at,List<PhaseEngine.Region> regions){VirtualPresetProfileTest.Image im=images.get(id);width=im.width;height=im.height;return layout.sampleRelative(regions,width,height,im,at);}
  PhaseEngine.Watch frame(String id){now+=400;samples=sample(id,now,e.activeRegions());exits.filter(e,samples,width,height,now,now);e.observe(samples,now);return banc.pollRegistered(e);}
  PhaseEngine.Watch confirm(String id){check(frame(id)==null,"first native frame cannot commit: "+id+" / "+e.current);PhaseEngine.Watch w=frame(id);check(w!=null,"second frame produces registered branch: "+id+" / "+e.diagnostic);return w;}
  void close(String id){String before=e.current;PhaseEngine.Watch w=confirm(id);check(w.action==PhaseEngine.CENTER&&AdCloseAutomaton.handles(w.regionId),"real Close is selected: "+id);
   Map<String,float[]> fresh=sample(id,now+50,ScanPlan.select(e,w.regionId,false));check(exits.preflight(w,fresh,width,height)&&e.confirmsAction(w,before,e.epoch,fresh),"independent fresh preflight: "+id);
   float[] point=AdCloseAutomaton.controlPoint(w.regionId,fresh,width,height);float x=id.equals("26")?998.5f:1030f,y=id.equals("26")?416.5f:230f;
   check(point!=null&&Math.abs(point[0]-x)<12&&Math.abs(point[1]-y)<12,"tap remains inside actual Close: "+id+" / "+Arrays.toString(point));
   exits.controlSent(w,now+60);long entered=e.enteredAt;e.finish(w,now+60);
   check(e.current.equals(FarmPreset.P+"ad"),"input completion keeps observing the advertisement");
   if(before.equals(e.current))check(e.enteredAt==entered,"another card does not restart the ad recovery deadline");
  }
 }
 static void nativeScenes()throws Exception{
  for(String id:new String[]{"0","2","28","32","38","44"}){
   Run r=new Run("ad");PhaseEngine.Watch w=r.confirm(id);check(w.action==PhaseEngine.WAIT&&w.nextPhase.equals(FarmPreset.P+"main"),"actual game image resumes the registered game path: "+id);r.e.finish(w,r.now);
   r.frame(id);check(FarmPreset.home(r.e,r.samples)&&r.layout.mainPointRelative(r.samples,r.width,r.height)!=null,"current harvest object is reacquired: "+id);
   check(ContinuousTapPolicy.allow(r.e,true,false,false,false,true,r.now,r.now),"ordinary input resumes only with fresh game evidence: "+id);
  }
  for(String id:new String[]{"4","8","18","20","24"}){Run r=new Run("ad");check(r.frame(id)==null&&r.frame(id)==null,"video, brand, store and unfinished card are not exits: "+id);check(!FarmPreset.home(r.e,r.samples),"ad/store cannot authorize harvest: "+id);}
  for(String id:new String[]{"26","34","36"})new Run("ad").close(id);
 }
 static void repeatedJourneys()throws Exception{
  Run r=new Run("ad");
  for(int cycle=0;cycle<3;cycle++){
   r.close("26");check(r.frame("24")==null&&r.frame("24")==null,"unfinished next card remains observation");r.close("34");
   PhaseEngine.Watch home=r.confirm("28");check(home.action==PhaseEngine.WAIT&&home.nextPhase.equals(FarmPreset.P+"main"),"two-card journey returns through actual game evidence");r.e.finish(home,r.now);
   r.frame("28");check(r.layout.mainPointRelative(r.samples,r.width,r.height)!=null,"cycle reacquires current input target");
   r.close("34"); // An immediately visible exit also works from main, without waiting for BANC.
   r.frame("24");r.frame("24");
  }
 }
 static void pending(Run r)throws Exception{
  BancAutonomy.Control control=new BancAutonomy.Control("閉じる","close",new float[]{.1f,.1f,.2f,.15f});
  BancAutonomy.Ui ui=new BancAutonomy.Ui("広告",Collections.singletonList(control));BancAutonomy.Decision d=new BancAutonomy.Decision();d.kind=BancAutonomy.CONTROL;d.control=control;d.id=control.id;
  Map<String,float[]> prior=new HashMap<>();prior.put(BancAutonomy.OVERVIEW,BancAutonomyTest.texture(.02f));
  check(r.banc.begin(d,r.e,ui,prior,r.now),"synthetic pending exploratory action is grounded in its fixture");r.banc.sent(r.now);
 }
 static void arbitration()throws Exception{
  for(String id:new String[]{"26","34","28"}){
   Run r=new Run("ad");pending(r);
   r.now+=400;r.samples=r.sample(id,r.now,r.e.activeRegions());r.exits.filter(r.e,r.samples,r.width,r.height,r.now,r.now);r.e.observe(r.samples,r.now);check(r.banc.pollRegistered(r.e)==null&&r.banc.pending(),"first frame keeps independent result wait");
   r.now+=400;r.samples=r.sample(id,r.now,r.e.activeRegions());r.exits.filter(r.e,r.samples,r.width,r.height,r.now,r.now);r.e.observe(r.samples,r.now);
   r.samples.put(BancAutonomy.OVERVIEW,BancAutonomyTest.texture(.02f));
   check(r.banc.review(r.e,r.samples,BancAutonomy.Ui.empty(),r.now,r.now,Collections.emptySet()).kind==BancAutonomy.WAIT,"legacy early result-wait would consume the ready native branch: "+id);
   PhaseEngine.Watch w=r.banc.pollRegistered(r.e);check(w!=null&&!r.banc.pending(),"registered Close/game transition preempts result wait: "+id);check(r.banc.skills.isEmpty()&&r.banc.verified==0,"preemption invents neither success nor learned skill");
  }
  Run r=new Run("ad");pending(r);PhaseEngine.Watch passive=new PhaseEngine.Watch();passive.action=PhaseEngine.WAIT;passive.nextPhase=r.e.current;r.banc.registered(r.e,passive);check(r.banc.pending(),"passive same-state wait does not cancel an independent result");
 }
 static void stalePreflight()throws Exception{
  Run r=new Run("ad");PhaseEngine.Watch w=r.confirm("34");Map<String,float[]> game=r.sample("28",r.now+50,ScanPlan.select(r.e,w.regionId,false));
  check(!r.exits.preflight(w,game,r.width,r.height),"game returning before dispatch rejects stale Close");
  AdCloseAutomaton.addNamed(game,AdCloseAutomaton.RIGHT,1030,230,r.width,r.height);
  check(!r.exits.preflight(w,game,r.width,r.height),"positive game evidence also rejects mixed old Close metadata");r.exits.filter(r.e,game,r.width,r.height,r.now+50,r.now+50);
  check(game.get(AdCloseAutomaton.RIGHT)[0]==0&&AdCloseAutomaton.controlPoint(AdCloseAutomaton.RIGHT,game,r.width,r.height)==null,"ordinary game evidence suppresses stale corner flags and points");
 }
 static void cancelledRequests()throws Exception{
  Run r=new Run("ad");long entered=r.e.enteredAt;
  for(int i=0;i<2;i++){PhaseEngine.Watch w=r.confirm("34");Map<String,float[]> fresh=r.sample("34",r.now+50,ScanPlan.select(r.e,w.regionId,false));check(r.exits.preflight(w,fresh,r.width,r.height),"cancelled request still starts from a verified control");long epoch=r.e.epoch;r.exits.controlSent(w,r.now+60);r.e.invalidateObservation();r.now+=900;check(r.e.enteredAt==entered&&!r.e.finishIfCurrent(w,r.e.current,epoch,r.now),"cancellation retains deadline and rejects late completion");}
  check(r.exits.controls.total==2,"cancelled requests consume the same bounded input budget");r.now=31000;r.samples=r.sample("34",r.now,r.e.activeRegions());r.exits.filter(r.e,r.samples,r.width,r.height,r.now,r.now);r.e.observe(r.samples,r.now);PhaseEngine.Watch w=r.banc.pollRegistered(r.e);
  check(w!=null&&w.action==PhaseEngine.RETURN_APP&&r.exits.choose(r.e,w,r.samples,r.now)==PhaseEngine.BACK,"cancelled Close attempts eventually reach registered recovery");
 }
 static void windowEvents(){
  WindowSnapshotScope captured=new WindowSnapshotScope(FarmPreset.APP,12,0,0,1080,2424);
  for(int overlayEvent=0;overlayEvent<20;overlayEvent++)check(captured.matches(new WindowSnapshotScope(FarmPreset.APP,12,0,0,1080,2424)),"unrelated overlay event preserves the actual target window");
  for(WindowSnapshotScope changed:new WindowSnapshotScope[]{new WindowSnapshotScope("com.android.vending",12,0,0,1080,2424),new WindowSnapshotScope(FarmPreset.APP,13,0,0,1080,2424),new WindowSnapshotScope(FarmPreset.APP,12,10,0,1090,2424),new WindowSnapshotScope(FarmPreset.APP,12,0,0,720,1616),null})check(!captured.matches(changed),"package/id/move/resize/missing window invalidates old input");
 }
 static void allFactoryGraphs()throws Exception{
  for(String app:PresetRegistry.APPS){PhaseEngine e=AdContinuationTest.stock(app);AdCloseAutomaton.install(e,app);String prefix=PresetRegistry.prefix(app);
   for(PhaseEngine.Phase phase:e.phases.values()){String name=phase.id.substring(prefix.length());boolean protectedState=phase.terminal||Arrays.asList("account","error","unknown","external").contains(name);int count=0;
    for(PhaseEngine.Watch w:phase.watches)if(AdCloseAutomaton.handles(w.regionId)){count++;check(w.nextPhase.equals(AdInterruption.recoveryState(phase.id)),"Close preserves the proper ordinary/mission ad path");check(e.phases.containsKey(w.nextPhase),"ad observation target exists");}
    check(count==(protectedState?0:2),"all nonprotected factory phases observe sparse exits: "+phase.id);
   }
   PhaseEngine custom=AdContinuationTest.stock(app);custom.windowRelative=true;AdCloseAutomaton.install(custom,app);check(!custom.regions.containsKey(AdCloseAutomaton.LEFT),"learned workflows retain their own input rules");
  }
 }
 public static void main(String[] args)throws Exception{load();nativeScenes();repeatedJourneys();arbitration();stalePreflight();cancelledRequests();windowEvents();allFactoryGraphs();System.out.println("Video6742ReplayTest: "+checks+" checks passed; actual native recording pixels, synthetic multi-card journeys/result-wait arbitration/cancellation/window events; Android device input untested");}
}
