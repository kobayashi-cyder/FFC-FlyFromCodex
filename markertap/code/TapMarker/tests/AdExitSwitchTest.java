package jp.tapmarker;
import java.io.*;import java.util.*;import java.util.zip.*;

/** Supplied Close screenshot, synthetic relocation/resizing and stalled-exit timelines. */
public final class AdExitSwitchTest {
 static int checks;static VirtualPresetProfileTest.Image image;static Map<String,float[]> refs;
 static void check(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
 static final class Run {
  final PhaseEngine e=new PhaseEngine();final CoinLayout layout;final AdExitPolicy policy=new AdExitPolicy();Map<String,float[]> samples;
  Run(String state){CoinPreset.install(e,refs);e.strictRuntime=true;layout=CoinLayout.install(e,refs);AdCloseAutomaton.install(e,CoinPreset.APP);e.enter(CoinPreset.P+state,0);}
  PhaseEngine.Watch observe(int w,int h,RegionSampler.Pixels pixels,long now,int[] window){samples=layout.sampleRelative(e.activeRegions(),w,h,pixels,now,window);policy.filter(e,samples,w,h,now,now);e.observe(samples,now);return e.poll();}
 }
 static void screenshot(){
  for(String state:new String[]{"check","ad","verify","reward-ad","mission-ad"}){Run r=new Run(state);check(r.observe(684,1536,image,31000,null)==null,"first Close observation holds an expired timer");PhaseEngine.Watch w=r.observe(684,1536,image,31350,null);check(w!=null&&w.regionId.equals(AdCloseAutomaton.LEFT),"supplied left Close recognized from "+state+" / "+r.e.diagnostic);float[] p=r.layout.pointRelative(w,r.samples,684,1536);check(p!=null&&p[0]>=14&&p[0]<=134&&p[1]>=126&&p[1]<=174,"input stays inside actual Close, away from install and panel");check(!ContinuousTapPolicy.allow(r.e,false,false,false,false,true,31350,31350),"ad never resumes main tapping");
   Map<String,float[]> fresh=r.layout.sampleRelative(ScanPlan.select(r.e,w.regionId,false),684,1536,image,31400);check(r.policy.preflight(w,fresh,684,1536)&&r.e.confirmsAction(w,r.e.current,r.e.epoch,fresh),"fresh named/word target is rechecked before input");
  }
 }
 static void variants(){
  for(int[] size:new int[][]{{342,768},{540,1213},{720,1616},{1080,2424}})for(boolean smooth:new boolean[]{false,true}){Run r=new Run("ad");RegionSampler.Pixels pixels=FarmDialogReplayTest.resize(image,size[0],size[1],smooth);r.observe(size[0],size[1],pixels,1000,null);PhaseEngine.Watch w=r.observe(size[0],size[1],pixels,1350,null);check(w!=null&&w.regionId.equals(AdCloseAutomaton.LEFT),"Close survives screenshot resizing "+size[0]+" / "+smooth);float[] p=r.layout.pointRelative(w,r.samples,size[0],size[1]);check(p!=null&&Math.abs(p[0]/size[0]-73.5f/684)<.015&&Math.abs(p[1]/size[1]-149/1536f)<.015,"resized Close uses its current position");}
  RegionSampler.Pixels right=(x,y)->y>=110&&y<190&&x>=524?image.get(x-524,y):y>=110&&y<190&&x<160?0xffffffff:image.get(x,y);
  Run r=new Run("ad");r.observe(684,1536,right,1000,null);PhaseEngine.Watch w=r.observe(684,1536,right,1350,null);check(w!=null&&w.regionId.equals(AdCloseAutomaton.RIGHT),"same Close lettering can appear at the right corner");
  int left=330,top=80,width=1400,height=1800;RegionSampler.Pixels moved=(x,y)->x>=left&&x<left+684&&y>=top&&y<top+1536?image.get(x-left,y-top):0xff334455;r=new Run("ad");r.observe(width,height,moved,1000,new int[]{left,top,left+684,top+1536});w=r.observe(width,height,moved,1350,new int[]{left,top,left+684,top+1536});check(w!=null,"desktop-window Close is recognized");float[] p=r.layout.pointRelative(w,r.samples,width,height);check(p!=null&&Math.abs(p[0]-left-73.5f)<3&&Math.abs(p[1]-top-149)<3,"window offset is applied once");check(r.layout.pointRelative(w,r.samples,width+1,height)==null,"old coordinates expire when window dimensions change");
 }
 static void negatives(){
  RegionSampler.Pixels missing=(x,y)->x>=40&&x<110&&y>=135&&y<165?0xffffffff:image.get(x,y);
  RegionSampler.Pixels partial=(x,y)->x>=43&&x<59&&y>=135&&y<165?0xffffffff:image.get(x,y);
  for(RegionSampler.Pixels p:new RegionSampler.Pixels[]{missing,partial}){check(AdCloseWordDetector.find(684,1536,true,p).isEmpty(),"missing/partial word does not authorize Close");Run r=new Run("ad");r.observe(684,1536,p,1000,null);check(r.observe(684,1536,p,1350,null)==null,"install and ad artwork cannot replace Close");}
  Run r=new Run("ad");r.observe(684,1536,image,1000,null);PhaseEngine.Watch w=r.observe(684,1536,image,1350,null);Map<String,float[]> gone=r.layout.sampleRelative(r.e.activeRegions(),684,1536,missing,1400);check(!r.policy.preflight(w,gone,684,1536)&&!r.e.confirmsAction(w,r.e.current,r.e.epoch,gone),"Close disappearing before input cancels the pending action");
 }
 static Map<String,float[]> exits(boolean left,boolean right){Map<String,float[]> s=new HashMap<>();if(left)AdCloseAutomaton.putControl(s,AdCloseAutomaton.LEFT,60,140,684,1536);if(right)AdCloseAutomaton.putControl(s,AdCloseAutomaton.RIGHT,630,140,684,1536);return s;}
 static PhaseEngine.Watch confirm(Run r,Map<String,float[]> a,Map<String,float[]> b,long now){r.policy.filter(r.e,a,684,1536,now,now);r.e.observe(a,now);check(r.e.poll()==null,"first selected exit needs another fresh frame");r.policy.filter(r.e,b,684,1536,now+250,now+250);r.e.observe(b,now+250);return r.e.poll();}
 static void rotation(){
  Run r=new Run("ad");PhaseEngine.Watch w=confirm(r,exits(true,true),exits(true,true),1000);check(w!=null&&w.regionId.equals(AdCloseAutomaton.LEFT),"two genuine exits choose one without contradictory-branch deadlock");r.policy.controlSent(w,1300);r.e.finish(w,1300);
  Map<String,float[]> cooling=exits(true,true);r.policy.filter(r.e,cooling,684,1536,1500,1500);check(cooling.get(AdCloseAutomaton.LEFT)[0]==0&&cooling.get(AdCloseAutomaton.RIGHT)[0]==0,"input completion alone does not cause immediate re-tapping");
  w=confirm(r,exits(true,true),exits(true,true),2200);check(w!=null&&w.regionId.equals(AdCloseAutomaton.RIGHT),"unchanged left exit yields to the untried right exit");r.policy.controlSent(w,2500);r.e.finish(w,2500);
  w=confirm(r,exits(true,true),exits(true,true),3400);check(w!=null&&w.regionId.equals(AdCloseAutomaton.LEFT),"one bounded retry returns to the oldest candidate");r.policy.controlSent(w,3700);r.e.finish(w,3700);
  w=confirm(r,exits(true,true),exits(true,true),4600);check(w!=null&&w.regionId.equals(AdCloseAutomaton.RIGHT),"remaining candidate gets one bounded retry");r.policy.controlSent(w,4900);r.e.finish(w,4900);
  Map<String,float[]> exhausted=exits(true,true);r.policy.filter(r.e,exhausted,684,1536,31000,31000);r.e.observe(exhausted,31000);w=r.e.poll();check(w!=null&&w.action==PhaseEngine.RETURN_APP&&r.policy.choose(r.e,w,exhausted,31000)==PhaseEngine.BACK,"exhausted exits reach Back/app recovery instead of an infinite loop");
  Run other=new Run("ad");w=confirm(other,exits(true,true),exits(true,true),1000);check(w!=null&&w.regionId.equals(AdCloseAutomaton.LEFT),"another display has its own retry budget");
  PhaseEngine.Watch home=r.e.rule(CoinPreset.P+"verify-home");Map<String,float[]> game=new HashMap<>();game.put(home.regionId,home.reference.clone());r.policy.observe(r.e,game);r.e.enter(CoinPreset.P+"ad",32000);w=confirm(r,exits(true,true),exits(true,true),33000);check(w!=null&&w.regionId.equals(AdCloseAutomaton.LEFT),"confirmed game gives the next ad a fresh budget");
 }
 static void sameCornerAndStale(){
  AdExitAttempts attempts=new AdExitAttempts();Map<String,float[]> s=new HashMap<>();AdCloseAutomaton.addNamed(s,AdCloseAutomaton.LEFT,40,140,684,1536);AdCloseAutomaton.addNamed(s,AdCloseAutomaton.LEFT,125,140,684,1536);attempts.select(s,684,1536,1000,1000);float[] first=AdCloseAutomaton.controlPoint(AdCloseAutomaton.LEFT,s,684,1536);attempts.sent(AdCloseAutomaton.LEFT,1000);
  s=new HashMap<>();AdCloseAutomaton.addNamed(s,AdCloseAutomaton.LEFT,40,140,684,1536);AdCloseAutomaton.addNamed(s,AdCloseAutomaton.LEFT,125,140,684,1536);attempts.select(s,684,1536,2000,2000);float[] second=AdCloseAutomaton.controlPoint(AdCloseAutomaton.LEFT,s,684,1536);check(first!=null&&second!=null&&first[0]!=second[0],"a second explicit Close in the same corner can replace a stalled first button");
  Map<String,float[]> fresh=new HashMap<>();AdCloseAutomaton.addNamed(fresh,AdCloseAutomaton.LEFT,40,140,684,1536);check(!attempts.preflight(AdCloseAutomaton.LEFT,fresh,684,1536),"preflight cannot fall back to the previously stalled candidate");
  s=exits(true,true);attempts.select(s,684,1536,3000,5000);check(s.get(AdCloseAutomaton.LEFT)[0]==0&&s.get(AdCloseAutomaton.RIGHT)[0]==0,"stale image cannot authorize either exit");
  Run r=new Run("ad");Map<String,float[]> empty=new HashMap<>();check(AdCloseAutomaton.ownsNamed(r.e,"Close",new float[]{.02f,.08f,.20f,.12f},empty),"BANC delegates named corner Close to the same bounded exit controller");
  check(!AdCloseAutomaton.ownsNamed(r.e,"Install",new float[]{.02f,.08f,.20f,.12f},empty),"exit ownership never grants an Install operation");
  check(!AdCloseAutomaton.ownsNamed(r.e,"閉じる",new float[]{.3f,.5f,.6f,.6f},empty),"ordinary centre-dialog Close remains available to BANC");
  check(!AdCloseAutomaton.ownsNamed(new PhaseEngine(),"Close",new float[]{.02f,.08f,.20f,.12f},empty),"graphs without the exit controller keep their own recovery controls");
 }
 public static void main(String[] args)throws Exception{refs=VirtualPresetProfileTest.canonical("tests/coin-cancel-references.bin");try(DataInputStream in=new DataInputStream(new GZIPInputStream(new FileInputStream("tests/ad-close-6716.bin.gz")))){image=new VirtualPresetProfileTest.Image(in);check(in.read()==-1,"complete supplied screenshot");}screenshot();variants();negatives();rotation();sameCornerAndStale();System.out.println("AdExitSwitchTest: "+checks+" checks passed; actual Close screenshot plus synthetic relocation/resizing, fresh preflight and bounded alternate-exit timelines; Android input untested");}
}
