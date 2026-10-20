package jp.tapmarker;
import java.io.*;
import java.util.*;
import java.util.zip.*;

/** Held-out visible previews. Repeated frames, sizes and acknowledgements are synthetic. */
public final class Video6767ReplayTest {
 static int checks;static boolean baseline;
 static final String P=UsagiPreset.PREFIX;
 static Map<String,float[]> refs,evidence,targets;
 static final Map<String,VirtualPresetProfileTest.Image> frames=new LinkedHashMap<>();
 static void check(boolean b,String message){checks++;if(!b)throw new AssertionError(message);}
 static final class Run {
  final PhaseEngine e=new PhaseEngine();final AdaptiveLayout layout;final AdExitPolicy ad=new AdExitPolicy();Map<String,float[]> samples;
  Run(String app,String state)throws Exception{
   Map<String,float[]> canonical=app.equals(UsagiPreset.APP)?refs:VirtualPresetProfileTest.canonical("tests/farm-replay.bin");
   if(app.equals(UsagiPreset.APP))UsagiPreset.install(e,canonical);else FarmPreset.install(e,canonical);
   e.strictRuntime=true;layout=AdaptiveLayout.install(e,app,canonical,evidence);layout.configureTargets(app,targets);AdCloseAutomaton.install(e,app);e.enter(PresetRegistry.prefix(app)+state,0);
  }
  Map<String,float[]> sample(int w,int h,RegionSampler.Pixels pixels,long at){
   List<PhaseEngine.Region> regions=e.activeRegions();if(!regions.contains(e.regions.get(layout.profile.region)))regions.add(e.regions.get(layout.profile.region));
   samples=layout.sampleRelative(regions,w,h,pixels,at);ad.filter(e,samples,w,h,at,at);return samples;
  }
  PhaseEngine.Watch event(int w,int h,RegionSampler.Pixels pixels,long at){sample(w,h,pixels,at);e.observe(samples,at);return ad.withFallback(e,e.poll(),samples,at);}
 }
 static RegionSampler.Pixels resize(VirtualPresetProfileTest.Image im,int w,int h){return FarmDialogReplayTest.resize(im,w,h,true);}
 static void cancel()throws Exception{
  for(String t:new String[]{"17.5","18","19","20"})for(int w:new int[]{294,320,400})for(String state:new String[]{"verify","ad","classify","main","use"}){
   VirtualPresetProfileTest.Image im=frames.get("usagi:"+t);int h=Math.round(w*im.height/(float)im.width);RegionSampler.Pixels p=resize(im,w,h);
   Run r=new Run(UsagiPreset.APP,state);PhaseEngine.Watch action=null;
   for(long at=1000;at<=3000;at+=350){PhaseEngine.Watch event=r.event(w,h,p,at);if(event!=null){if(event.action==PhaseEngine.CENTER){action=event;break;}check(event.action==PhaseEngine.WAIT,"cancel page never starts an ad or presses play");r.e.finish(event,at);}}
   if(baseline){System.out.println("cancel "+t+" width="+w+" state="+state+" action="+(action==null?"NONE":action.id));continue;}
   check(action!=null&&action.id.equals(P+"use"),"cancel must be reached: "+t+" / "+w+" / "+state+" / "+r.e.diagnostic);
   float[] point=r.layout.pointRelative(action,r.samples,w,h);check(point!=null&&point[0]>w*105f/294&&point[0]<w*192f/294&&point[1]>h*375f/660&&point[1]<h*398f/660,"integer-scaled point is inside Cancel, below Use: "+Arrays.toString(point));
   check(r.samples.containsKey(UsagiCancelEvidence.PAGE)&&!r.samples.containsKey(AdaptiveLayout.MAIN),"modal blocks ordinary tapping and upgrade");
   check(r.e.confirmsAction(action,r.e.current,r.e.epoch,r.sample(w,h,p,3200)),"current frame confirms dispatch");
   r.e.finish(action,3300);check(r.e.current.equals(P+"use-wait"),"sending Cancel waits for its disappearance");
   check(r.event(w,h,p,3500)==null&&r.event(w,h,p,3850)==null,"unchanged dialog is not counted as success");
  }
 }
 static void missing()throws Exception{
  for(String id:new String[]{"usagi:14","usagi:16"}){VirtualPresetProfileTest.Image early=frames.get(id);Run wait=new Run(UsagiPreset.APP,"use");wait.sample(early.width,early.height,early,1000);check(!wait.samples.containsKey(UsagiCancelEvidence.PAGE),"delayed Cancel label must become visible before authorizing input: "+id);}
  VirtualPresetProfileTest.Image im=frames.get("usagi:18");
  for(String key:new String[]{"use","cancel"}){
   float[] b=evidence.get("usagi-cancel:"+key+":bounds");int[] box=RegionMath.bounds(b,im.width,im.height);
   RegionSampler.Pixels pixels=(x,y)->x>=box[0]-2&&x<box[2]+2&&y>=box[1]-2&&y<box[3]+2?(key.equals("use")?0xffffcb00:0xffffffff):im.get(x,y);
   Run r=new Run(UsagiPreset.APP,"use");r.sample(im.width,im.height,im,1000);PhaseEngine.Watch action=r.e.rule(P+"use");
   check(r.samples.containsKey(UsagiCancelEvidence.PAGE),"negative begins with present cancel page");
   r.sample(im.width,im.height,pixels,1400);check(!r.samples.containsKey(UsagiCancelEvidence.PAGE),"both labels required; missing "+key);
   check(!r.e.confirmsAction(action,r.e.current,r.e.epoch,r.samples),"missing label revokes input preflight: "+key);
  }
 }
 static void playable()throws Exception{
  PlayableAdEvidence detector=new PlayableAdEvidence(targets);
  for(Map.Entry<String,VirtualPresetProfileTest.Image> row:frames.entrySet()){
   boolean expected=row.getKey().startsWith("farm:")&&Integer.parseInt(row.getKey().split(":")[1])>=27;
   VirtualPresetProfileTest.Image im=row.getValue();
   for(int w:new int[]{294,320,400}){int h=Math.round(w*im.height/(float)im.width);Run r=new Run(FarmPreset.APP,"ad");Map<String,float[]> s=new HashMap<>();detector.sample(r.e,s,w,h,resize(im,w,h),w,h);boolean found=PlayableAdEvidence.visible(s,w,h);
    if(baseline)System.out.println("playable "+row.getKey()+" width="+w+" expected="+expected+" found="+found);else check(found==expected,"two-anchor playable identity: "+row.getKey()+" / "+w+" / "+found);
   }
  }
  VirtualPresetProfileTest.Image im=frames.get("farm:42");
  for(String key:new String[]{"logo","hint"}){float[] b=targets.get("playable-last-asylum:"+key+":bounds");int[] box=RegionMath.bounds(b,im.width,im.height);RegionSampler.Pixels missing=(x,y)->x>=box[0]-2&&x<box[2]+2&&y>=box[1]-2&&y<box[3]+2?0xff777777:im.get(x,y);Run r=new Run(FarmPreset.APP,"ad");Map<String,float[]> s=new HashMap<>();detector.sample(r.e,s,im.width,im.height,missing,im.width,im.height);check(!s.containsKey(PlayableAdEvidence.KEY),"no playable fallback from missing "+key);}
 }
 static void fallback()throws Exception{
  for(String app:PresetRegistry.APPS){
   PhaseEngine e=new PhaseEngine();String p=PresetRegistry.prefix(app);PhaseEngine.Phase phase=new PhaseEngine.Phase();phase.id=p+"ad";phase.name="test ad";e.phases.put(phase.id,phase);
   PhaseEngine.Watch timer=new PhaseEngine.Watch();timer.id=p+"ad-timeout";timer.condition=PhaseEngine.TIMEOUT;timer.action=PhaseEngine.RETURN_APP;timer.nextPhase=phase.id;timer.fallback=true;timer.timeoutMs=30000;phase.watches.add(timer);e.enter(phase.id,0);
   AdExitPolicy a=new AdExitPolicy();Map<String,float[]> sample=new HashMap<>();sample.put(PlayableAdEvidence.KEY,new float[]{1,320,718});
   for(long at=1000;at<=9000;at+=500){a.filter(e,new HashMap<>(sample),320,718,at,at);PhaseEngine.Watch event=a.withFallback(e,null,sample,at);check((event!=null)==(at>=9000),"playable waits for fresh evidence and 8 seconds: "+app);}
   check(a.choose(e,timer,sample,9000)==PhaseEngine.BACK,"first playable fallback is display-local Back");a.sent(9000);e.enter(phase.id,9500);
   for(long at=9500;at<=11500;at+=500)a.filter(e,new HashMap<>(sample),320,718,at,at);
   check(a.withFallback(e,null,sample,11500)==timer,"phase changes do not restart playable deadline");check(a.choose(e,timer,sample,11500)==PhaseEngine.BACK,"second Back can dismiss an ad confirmation");a.sent(11500);
   for(long at=12000;at<=14000;at+=500)a.filter(e,new HashMap<>(sample),320,718,at,at);
   check(a.choose(e,timer,sample,14000)==PhaseEngine.RETURN_APP,"ignored Back escalates to the registered app intent");a.returned(14000);check(a.withFallback(e,null,sample,14000)==null,"relaunch cooldown prevents a tight loop");
   AdExitPolicy other=new AdExitPolicy();check(other.withFallback(e,null,sample,14000)==null,"another game/display cannot inherit the episode");
   check(a.withFallback(e,null,sample,16000)==null,"stale last observation cannot authorize another action");
   for(long at=14500;at<=22000;at+=500){a.filter(e,new HashMap<>(sample),320,718,at,at);check(a.restartNeeded(at)==(at==22000),"task restart requires an unsuccessful ordinary return, fresh identity and cooldown");}
   a.returned(22000,true);check(!a.restartNeeded(22000)&&a.choose(e,timer,sample,22000)==PhaseEngine.WAIT,"task restart has its own thirty-second quiet interval");
   for(long at=22500;at<=52000;at+=500)a.filter(e,new HashMap<>(sample),320,718,at,at);
   check(a.restartNeeded(52000),"one later task restart is permitted for a still positively observed ad");a.returned(52000,true);
   for(long at=52500;at<=82500;at+=500)a.filter(e,new HashMap<>(sample),320,718,at,at);
   check(!a.restartNeeded(82500)&&a.choose(e,timer,sample,82500)==PhaseEngine.WAIT,"two task restarts cannot become an endless restart loop");
   a.reset();check(!a.restartNeeded(82500),"a new episode cannot inherit restart authority");
  }
 }
 static void sequence()throws Exception{
  Run r=new Run(FarmPreset.APP,"ad");int w=320,h=718,backs=0,returns=0;String[] scenes={"farm:30","farm:33","farm:36","farm:39","farm:42","farm:45","farm:48","farm:51"};
  for(long at=1000;at<=20000;at+=500){VirtualPresetProfileTest.Image im=frames.get(scenes[(int)((at-1000)/2500)%scenes.length]);PhaseEngine.Watch event=r.event(w,h,resize(im,w,h),at);
   check(PlayableAdEvidence.visible(r.samples,w,h),"complete sampler retains playable identity despite background animation");
   check(!FarmPreset.home(r.e,r.samples)&&r.layout.mainPointRelative(r.samples,w,h)==null,"playable content never authorizes harvest input");
   if(event==null)continue;check(event.action==PhaseEngine.RETURN_APP,"playable episode may only request registered recovery");int action=r.ad.choose(r.e,event,r.samples,at);
   if(action==PhaseEngine.WAIT){r.e.cancelIfCurrent(event,r.e.current,r.e.epoch);continue;}
   if(action==PhaseEngine.BACK){backs++;r.ad.sent(at);}else if(action==PhaseEngine.RETURN_APP){returns++;r.ad.returned(at);}else throw new AssertionError("unexpected playable input");
   r.e.finish(event,at);
  }
  check(backs==2&&returns==1,"animated playable receives two Back attempts and one bounded app return: "+backs+" / "+returns);
  check(r.e.current.equals(FarmPreset.P+"verify"),"input acknowledgements do not claim game/reward success");
 }
 public static void main(String[] args)throws Exception{
  baseline=args.length>0&&args[0].equals("baseline");refs=VirtualPresetProfileTest.canonical("tests/usagi-current-references.bin");evidence=VirtualPresetProfileTest.canonical("tests/current-layout-evidence.bin");targets=VirtualPresetProfileTest.canonical("tests/visual-targets.bin");
  try(DataInputStream in=new DataInputStream(new GZIPInputStream(new FileInputStream("tests/video6767-replay.bin.gz")))){int n=in.readInt();for(int i=0;i<n;i++)frames.put(in.readUTF(),new VirtualPresetProfileTest.Image(in));check(in.read()==-1,"complete held-out fixture");}
  cancel();if(!baseline)missing();playable();if(!baseline){fallback();sequence();}
  System.out.println("Video6767ReplayTest: "+checks+" checks; preview images/synthetic timestamps only, 320px raw frames and Android game responses untested");
 }
}
