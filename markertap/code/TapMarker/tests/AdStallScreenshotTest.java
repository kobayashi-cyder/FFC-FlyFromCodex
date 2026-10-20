package jp.tapmarker;
import java.io.*;import java.util.*;import java.util.zip.*;

/** Full supplied frames are holdouts: letterbox, skip -> end card, timer, scaling and window offsets. */
public final class AdStallScreenshotTest {
 static int checks;static Map<String,VirtualPresetProfileTest.Image> images=new LinkedHashMap<>();
 static void check(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
 static final class Run {
  final PhaseEngine e=new PhaseEngine();final AdaptiveLayout layout;
  Run(){FarmPreset.install(e,FarmDialogReplayTest.canonical);e.strictRuntime=true;layout=AdaptiveLayout.install(e,FarmPreset.APP,FarmDialogReplayTest.canonical,FarmDialogReplayTest.evidence);AdCloseAutomaton.install(e,FarmPreset.APP);}
  Map<String,float[]> sample(int w,int h,RegionSampler.Pixels pixels,long now,int[] window){return layout.sampleRelative(e.activeRegions(),w,h,pixels,now,window);}
 }
 static float[] box(String name){return name.equals("6698")?new float[]{616,248,648,281}:name.equals("6697")?new float[]{39,251,65,277}:new float[]{34,246,57,268};}
 static PhaseEngine.Watch confirm(Run r,Map<String,float[]> s,long now){r.e.observe(s,now);check(r.e.poll()==null,"first fresh visible exit beats an expired fallback and waits for confirmation");r.e.observe(s,now+350);return r.e.poll();}
 static void frame(String name,String state,int w,int h,RegionSampler.Pixels pixels)throws Exception {
  VirtualPresetProfileTest.Image im=images.get(name);Run r=new Run();r.e.enter(FarmPreset.P+state,0);Map<String,float[]> s=r.sample(w,h,pixels,1000,null);if(name.equals("6696")){check(s.get(AdCloseAutomaton.LEFT)[0]==0&&s.get(AdCloseAutomaton.RIGHT)[0]==0,"Google Play brand pill alone never authorizes an exit");r.e.observe(s,1000);r.e.observe(s,1350);check(r.e.poll()==null,"brand label waits for a separately observed exit");return;}String expected=name.equals("6698")?AdCloseAutomaton.RIGHT:AdCloseAutomaton.LEFT;check(s.get(expected)[0]==1,"expected exit flag: "+name+" / "+state+" / "+w+"x"+h);PhaseEngine.Watch event=confirm(r,s,31000);
  String side=name.equals("6698")?AdCloseAutomaton.RIGHT:AdCloseAutomaton.LEFT;
  check(event!=null&&event.action==PhaseEngine.CENTER&&side.equals(event.regionId),"supplied "+name+" exit recognized in "+state+" at "+w+"x"+h+" / "+r.e.diagnostic);
  float[] point=r.layout.pointRelative(event,s,w,h),b=box(name);
  check(point!=null&&point[0]>=b[0]*w/im.width&&point[0]<=b[2]*w/im.width&&point[1]>=b[1]*h/im.height&&point[1]<=b[3]*h/im.height,"tap belongs to the visible current exit: "+name+" / "+Arrays.toString(point));
  check(r.layout.pointRelative(event,s,w+1,h)==null,"a resized frame rejects the old point");
  check(r.e.confirmsAction(event,r.e.current,r.e.epoch,s),"exit is rechecked before input");
  s.put(side,new float[]{0});check(!r.e.confirmsAction(event,r.e.current,r.e.epoch,s),"disappeared exit cancels preflight input");
 }
 static void countdown(){
  VirtualPresetProfileTest.Image im=images.get("6695");Run r=new Run();r.e.enter(FarmPreset.P+"ad",0);Map<String,float[]> s=r.sample(im.width,im.height,im,1000,null);
  check(s.get(AdCloseAutomaton.LEFT)[0]==0&&s.get(AdCloseAutomaton.RIGHT)[0]==0,"5s and feedback icon are not exits");
  r.e.observe(s,1000);r.e.observe(s,29999);check(r.e.poll()==null,"countdown waits for an actual exit without touching the advertised app");
  r.e.observe(s,30000);PhaseEngine.Watch timer=r.e.poll();check(timer!=null&&timer.condition==PhaseEngine.TIMEOUT,"missing exit still reaches bounded fallback");AdExitPolicy policy=new AdExitPolicy();policy.observe(r.e,s);check(policy.choose(r.e,timer,s,30000)==PhaseEngine.BACK,"first missing exit uses Back after the configured wait");
 }
 static void sequence(){
  Run r=new Run();r.e.enter(FarmPreset.P+"ad",0);
  for(String name:new String[]{"6697","6698"}){VirtualPresetProfileTest.Image im=images.get(name);Map<String,float[]> s=r.sample(im.width,im.height,im,1000,null);PhaseEngine.Watch event=confirm(r,s,31000);check(event!=null&&event.regionId.equals(name.equals("6697")?AdCloseAutomaton.LEFT:AdCloseAutomaton.RIGHT),"skip then a different-position end-card X both execute");r.e.finish(event,31350);check(r.e.current.equals(FarmPreset.P+"ad"),"every exit keeps observing until a registered game scene returns");}
  PhaseEngine.Watch home=r.e.rule(FarmPreset.P+"ad-home");Map<String,float[]> homeSamples=new HashMap<>();homeSamples.put(home.regionId,home.reference.clone());PhaseEngine.Watch event=confirm(r,homeSamples,32000);check(event==home,"confirmed game restores the main loop");r.e.finish(event,32350);check(r.e.current.equals(FarmPreset.P+"main"),"main harvest loop resumes after confirmed recovery");
 }
 static void negatives(){
  for(String name:new String[]{"6696","6697"}){VirtualPresetProfileTest.Image im=images.get(name);int left=name.equals("6696")?50:55,right=name.equals("6696")?54:61;RegionSampler.Pixels noBar=(x,y)->x>=left&&x<right&&y>=248&&y<275?0xff888888:im.get(x,y);Run r=new Run();r.e.enter(FarmPreset.P+"ad",0);Map<String,float[]> s=r.sample(im.width,im.height,noBar,1000,null);check(s.get(AdCloseAutomaton.LEFT)[0]==0,"triangle alone cannot authorize Play input: "+name);}
  VirtualPresetProfileTest.Image im=images.get("6698");RegionSampler.Pixels noClose=(x,y)->x>=618&&x<647&&y>=248&&y<280?0xff252628:im.get(x,y);Run r=new Run();r.e.enter(FarmPreset.P+"ad",0);Map<String,float[]> s=r.sample(im.width,im.height,noClose,1000,null);check(s.get(AdCloseAutomaton.RIGHT)[0]==0,"install, interest and advertising artwork without X do not authorize input");
  for(String name:new String[]{"farm-main","farm-main-pressed","farm-reward","farm-reward-holdout","usagi-main"}){VirtualPresetProfileTest.Image game=VirtualRecoveryReplayTest.images.get(name);Run g=new Run();g.e.enter(FarmPreset.P+"ad",0);Map<String,float[]> m=g.sample(game.width,game.height,game,1000,null);check(m.get(AdCloseAutomaton.LEFT)[0]==0&&m.get(AdCloseAutomaton.RIGHT)[0]==0,"prior game/reward UI is never a new ad exit: "+name);}
  check(AdExitPolicy.corner("Close",new float[]{.88f,.22f,.96f,.25f},.14f).equals(AdCloseAutomaton.RIGHT),"named exit follows the ad inset");check(AdExitPolicy.corner("Play",new float[]{.88f,.22f,.96f,.25f},.14f).isEmpty(),"inset never grants a Play label");check(AdExitPolicy.corner("Close",new float[]{.88f,.42f,.96f,.45f},.14f).isEmpty(),"inset never opens the middle advertising area");
 }
 static void moved(){
  for(String name:new String[]{"6696","6697","6698"}){VirtualPresetProfileTest.Image im=images.get(name);Run r=new Run();int l=510,t=80,w=1600,h=1800;RegionSampler.Pixels desktop=(x,y)->x>=l&&x<l+im.width&&y>=t&&y<t+im.height?im.get(x-l,y-t):0xff223344;r.e.enter(FarmPreset.P+"ad",0);Map<String,float[]> s=r.sample(w,h,desktop,1000,new int[]{l,t,l+im.width,t+im.height});if(name.equals("6696")){check(s.get(AdCloseAutomaton.LEFT)[0]==0,"moved brand pill remains a negative");continue;}PhaseEngine.Watch event=confirm(r,s,31000);check(event!=null&&event.action==PhaseEngine.CENTER,"moved desktop window recognizes "+name);float[] point=r.layout.pointRelative(event,s,w,h),b=box(name);check(point!=null&&point[0]>=l+b[0]&&point[0]<=l+b[2]&&point[1]>=t+b[1]&&point[1]<=t+b[3],"window offset is applied exactly once: "+name);check(r.layout.pointRelative(event,s,w,h+1)==null,"moved point expires after desktop resize");}
 }
 public static void main(String[] args)throws Exception {
  FarmDialogReplayTest.load();try(DataInputStream in=new DataInputStream(new GZIPInputStream(new FileInputStream("tests/ad-stalls-20261013.bin.gz")))){int n=in.readInt();for(int i=0;i<n;i++)images.put(in.readUTF(),new VirtualPresetProfileTest.Image(in));check(n==4&&in.read()==-1,"four complete supplied frames");}
  for(String name:new String[]{"6696","6697","6698"}){VirtualPresetProfileTest.Image im=images.get(name);for(String state:new String[]{"check","ad","verify"})frame(name,state,im.width,im.height,im);for(int[] size:new int[][]{{540,1213},{720,1616},{1080,2280}})for(boolean smooth:new boolean[]{false,true})frame(name,"ad",size[0],size[1],FarmDialogReplayTest.resize(im,size[0],size[1],smooth));}
  countdown();sequence();negatives();moved();
  System.out.println("AdStallScreenshotTest: "+checks+" checks passed; four supplied full-frame holdouts, original/resampled pixels, countdown, Play negatives, skip-to-X recovery and moved windows; physical Android input untested");
 }
}
