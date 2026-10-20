package jp.tapmarker;
import java.io.*;
import java.util.*;
import java.util.zip.*;

/** Recorded holdouts plus explicitly synthetic resize, occlusion and input acknowledgements. */
public final class UsagiControlReplayTest {
 static final String P=UsagiPreset.PREFIX;
 static int checks;
 static Map<String,float[]> refs,evidence,targets;
 static final Map<String,VirtualPresetProfileTest.Image> missions=new LinkedHashMap<>();
 static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 static final class Run {
  final PhaseEngine e=new PhaseEngine();final AdaptiveLayout layout;final UpgradeEngine side;final PhaseEngine.Watch amount;
  Map<String,float[]> samples;
  Run(String state){
   UsagiPreset.install(e,refs);e.strictRuntime=true;
   layout=AdaptiveLayout.install(e,UsagiPreset.APP,refs,evidence);layout.configureTargets(UsagiPreset.APP,targets);
   AdCloseAutomaton.install(e,UsagiPreset.APP);e.enter(P+state,0);
   amount=e.rule(P+"amount-tap");side=new UpgradeEngine(e,UsagiPreset.APP);
  }
  Map<String,float[]> sample(int w,int h,RegionSampler.Pixels pixels,long now,int[] window){
   List<PhaseEngine.Region> regions=e.activeRegions();PhaseEngine.Region home=e.regions.get(P+"game");if(!regions.contains(home))regions.add(home);side.addRegions(regions);
   return samples=layout.sampleRelative(regions,w,h,pixels,now,window);
  }
  PhaseEngine.Watch observe(int w,int h,RegionSampler.Pixels pixels,long now,int[] window){e.observe(sample(w,h,pixels,now,window),now);return e.poll();}
  boolean home(){PhaseEngine.Watch w=e.rule(P+"return");return w.distance(samples.get(w.regionId))<=w.threshold;}
  float[] point(PhaseEngine.Watch watch,int w,int h){return layout.pointRelative(watch,samples,w,h);}
 }
 static void load()throws Exception{
  refs=VirtualPresetProfileTest.canonical("tests/usagi-current-references.bin");evidence=VirtualPresetProfileTest.canonical("tests/current-layout-evidence.bin");targets=VirtualPresetProfileTest.canonical("tests/visual-targets.bin");
  try(DataInputStream in=new DataInputStream(new GZIPInputStream(new FileInputStream("tests/video6753-replay.bin.gz")))){
   int n=in.readInt();for(int i=0;i<n;i++)missions.put(in.readUTF(),new VirtualPresetProfileTest.Image(in));check(n==6&&in.read()==-1,"six held-out 6753 preview frames");
  }
  Video6677ReplayTest.load();
 }
 static void page(int w,int h,RegionSampler.Pixels pixels,int[] window,String label){
  int left=window==null?0:window[0],top=window==null?0:window[1],cw=window==null?w:window[2]-left,ch=window==null?h:window[3]-top;
  for(String state:new String[]{"verify","ad","classify","main"}){
   Run r=new Run(state);check(r.observe(w,h,pixels,1000,window)==null,"first page needs another observation: "+label+" / "+state);
   PhaseEngine.Watch branch=r.observe(w,h,pixels,1350,window);
   check(branch!=null&&branch.action==PhaseEngine.WAIT&&branch.nextPhase.equals(P+(state.equals("main")?"classify":"missions")),"mission page exits recovery without entering main: "+label+" / "+state+" / "+r.e.diagnostic);
   check(!r.home()&&r.layout.mainPointRelative(r.samples,w,h)==null,"mission page never admits ordinary taps: "+label);
   check(r.side.observe(r.samples,1350,1350,false)==null,"mission page cannot request an amount upgrade");
   if(state.equals("main"))continue;
   MissionJourney journey=new MissionJourney();journey.route(branch.nextPhase);r.e.finish(branch,1400);
   check(r.observe(w,h,pixels,1500,window)==null,"Play also needs fresh confirmation");
   PhaseEngine.Watch play=r.observe(w,h,pixels,1850,window);
   check(play!=null&&play.id.equals(P+"未達成ならゲームへ戻る")&&play.action==PhaseEngine.CENTER,"first unmet task returns through its Play control: "+label+" / "+r.e.diagnostic);
   float[] point=r.point(play,w,h);check(point!=null&&point[0]>left+cw*.72f&&point[0]<left+cw*.89f&&point[1]>top+ch*.55f&&point[1]<top+ch*.61f,"Play point lies in the observed first-row button: "+label+" / "+Arrays.toString(point));
   Map<String,float[]> fresh=r.sample(w,h,pixels,1950,window);
   check(r.e.confirmsAction(play,r.e.current,r.e.epoch,fresh)&&r.point(play,w,h)!=null,"dispatch rechecks the page and current coordinate");
   r.e.finish(play,2000);journey.completedPlay();check(r.e.current.equals(P+"verify")&&journey.route(P+"main").equals(P+"main")&&!journey.blocksMain(),"Play acknowledgement still requires home verification and releases the mission route");
  }
 }
 static void missionCases(){
  for(Map.Entry<String,VirtualPresetProfileTest.Image> row:missions.entrySet()){VirtualPresetProfileTest.Image im=row.getValue();page(im.width,im.height,im,null,row.getKey());}
  VirtualPresetProfileTest.Image im=missions.get("8");
  for(int[] size:new int[][]{{360,808},{720,1616}}){int w=size[0],h=size[1];page(w,h,FarmDialogReplayTest.resize(im,w,h,true),null,"synthetic "+w);}
  int left=61,top=47,w=320,h=500;RegionSampler.Pixels moved=(x,y)->x>=left&&x<left+im.width&&y>=top&&y<top+im.height?im.get(x-left,y-top):0xff34384b;
  page(w,h,moved,new int[]{left,top,left+im.width,top+im.height},"selected window offset");
  for(boolean hideTitle:new boolean[]{false,true}){
   RegionSampler.Pixels missing=(x,y)->hideTitle&&x>im.width*.27f&&x<im.width*.74f&&y>im.height*.11f&&y<im.height*.15f?0xffcba0c2:!hideTitle&&x>im.width*.70f&&x<im.width*.90f&&y>im.height*.55f&&y<im.height*.61f?0xffffd200:im.get(x,y);
   Run r=new Run("missions");r.sample(im.width,im.height,im,1000,null);check(r.point(r.e.rule(P+"未達成ならゲームへ戻る"),im.width,im.height)!=null,"occlusion begins with a valid Play point");
   r.sample(im.width,im.height,missing,1400,null);PhaseEngine.Watch play=r.e.rule(P+"未達成ならゲームへ戻る");check(!r.e.confirmsAction(play,r.e.current,r.e.epoch,r.samples)&&r.point(play,im.width,im.height)==null,"missing title or Play revokes the old coordinate");
  }
 }
 static void amounts(){
  for(String id:new String[]{"0006","0079"}){
   VirtualPresetProfileTest.Image im=Video6677ReplayTest.images.get(id);Run r=new Run("main");
   r.sample(im.width,im.height,im,1000,null);check(r.home(),"amount case has independent home evidence: "+id);check(r.side.observe(r.samples,1000,1000,true)==null,"amount waits for its second fresh frame");
   r.sample(im.width,im.height,im,1300,null);UpgradeEngine.Decision d=r.side.observe(r.samples,1300,1300,true);
   check(d!=null&&d.watch==r.amount&&!d.advertisement&&r.side.visible(d,r.samples),"detached amount engine sees the current Necessary label: "+id);
   float[] point=r.point(r.amount,im.width,im.height);check(point!=null&&point[0]>im.width*.04f&&point[0]<im.width*.35f&&point[1]>im.height*.82f&&point[1]<im.height*.89f,"upgrade point stays inside the left amount pill: "+id);
   check(r.e.current.equals(P+"main")&&!r.e.awaitingBranch(),"upgrade does not suspend the main automaton");
   r.side.sent(d,1350);r.side.observe(r.samples,1400,1400,true);check(r.side.awaitingResult(),"input acknowledgement alone does not claim a level increase");
   Map<String,float[]> changed=new HashMap<>(r.samples);float[] level=r.samples.get(P+"amount-level").clone();for(int i=0;i<level.length;i++)level[i]=1-level[i];changed.put(P+"amount-level",level);r.side.observe(changed,1500,1500,true);check(!r.side.awaitingResult()&&r.side.status().contains("表示変化"),"synthetic post-input level change confirms the display only");
   check(r.side.observe(r.samples,1600,1600,true)==null,"level confirmation imposes a cooldown");
  }
  for(String id:new String[]{"0008","0086","0033","0190"}){
   VirtualPresetProfileTest.Image im=Video6677ReplayTest.images.get(id);Run r=new Run("main");
   for(int i=0;i<2;i++){r.sample(im.width,im.height,im,1000+350*i,null);check(r.side.observe(r.samples,1000+350*i,1000+350*i,r.home())==null,"advertisement or modal never produces an upgrade: "+id);}
   check(r.point(r.amount,im.width,im.height)==null,"missing amount eligibility has no stale input point: "+id);
  }
  VirtualPresetProfileTest.Image im=Video6677ReplayTest.images.get("0006");
  for(boolean hideTitle:new boolean[]{false,true}){
   RegionSampler.Pixels missing=(x,y)->x>im.width*.16f&&x<im.width*.33f&&y>im.height*(hideTitle?.837f:.856f)&&y<im.height*(hideTitle?.856f:.878f)?0xffdddddd:im.get(x,y);Run r=new Run("main");
   for(int i=0;i<2;i++){r.sample(im.width,im.height,missing,1000+350*i,null);check(r.side.observe(r.samples,1000+350*i,1000+350*i,r.home())==null,"both amount title and Necessary text are required");}
   check(r.point(r.amount,im.width,im.height)==null,"occluded upgrade has no tap coordinate");
  }
 }
 static UpgradeEngine.Decision adDecision(Run r,VirtualPresetProfileTest.Image im){
  r.sample(im.width,im.height,im,1000,null);check(r.home()&&r.side.observe(r.samples,1000,1000,true)==null,"the actual CM amount control needs two frames");
  r.sample(im.width,im.height,im,1350,null);UpgradeEngine.Decision d=r.side.observe(r.samples,1350,1350,true);
  check(d!=null&&d.advertisement&&r.side.visible(d,r.samples),"held-out 6677/0021 supplies the labelled amount CM request");
  float[] p=r.point(r.amount,im.width,im.height);check(p!=null&&p[0]>.04f*im.width&&p[0]<.35f*im.width&&p[1]>.82f*im.height&&p[1]<.89f*im.height,"CM request targets only the left amount pill");return d;
 }
 static void advertisements(){
  VirtualPresetProfileTest.Image im=Video6677ReplayTest.images.get("0021");Run r=new Run("main");UpgradeEngine.Decision d=adDecision(r,im);
  Map<String,float[]> cost=new HashMap<>(r.samples);cost.remove(UsagiAmountEvidence.AD);check(!r.side.visible(d,cost),"switching between Necessary and CM invalidates the queued operation");
  r.side.sent(d,1400);check(r.side.awaitingResult(),"CM tap delivery starts result confirmation");
  r.side.observe(r.samples,1700,1700,true);check(r.side.awaitingResult(),"unchanged pre-ad home is not a successful upgrade");
  r.e.enter(P+"ad",2000);r.side.observe(Collections.emptyMap(),2000,2000,false);check(r.side.awaitingResult()&&r.side.pending()==null,"ad interruption cancels dispatch but retains the before-level evidence");
  r.e.enter(P+"main",31000);Map<String,float[]> returned=new HashMap<>(r.samples);float[] level=returned.get(P+"amount-level").clone();for(int i=0;i<level.length;i++)level[i]=1-level[i];returned.put(P+"amount-level",level);
  check(r.side.observe(returned,31000,31000,true)==null&&!r.side.awaitingResult()&&r.side.status().contains("表示変化"),"synthetic post-ad level change finishes the display confirmation");
  for(long time:new long[]{33000,34000,40000,50000,60000})check(r.side.observe(r.samples,time,time,true)==null,"another CM request cannot repeat inside 60 seconds: "+time);
  check(r.side.observe(r.samples,61500,61500,true)==null,"later CM request needs a first fresh frame");d=r.side.observe(r.samples,61900,61900,true);check(d!=null&&d.advertisement,"later stable control can authorize one bounded retry");
  r=new Run("main");d=adDecision(r,im);r.side.sent(d,1400);r.side.observe(r.samples,11400,11400,true);check(!r.side.awaitingResult()&&r.side.status().contains("開始を未確認"),"failure to open the CM expires instead of waiting forever");
  check(r.side.observe(r.samples,20000,20000,true)==null&&r.side.observe(r.samples,20500,20500,true)==null,"failed CM start cannot cause immediate repeated advertisements");
  r=new Run("main");d=adDecision(r,im);r.side.sent(d,1400);r.side.interrupt(2000,"CM");r.e.enter(P+"main",31000);r.side.observe(r.samples,31000,31000,true);check(r.side.awaitingResult(),"unchanged post-ad home gets a bounded confirmation window");r.side.observe(r.samples,34000,34000,true);check(!r.side.awaitingResult()&&r.side.status().contains("未確認"),"unchanged post-ad level is not reported as an increase");
  r=new Run("main");d=adDecision(r,im);r.side.interrupt(1390,"取り消し");r.side.sent(d,1400);check(!r.side.awaitingResult(),"late delivery cannot revive a cancelled ad request");
  r=new Run("main");r.sample(im.width,im.height,im,1000,null);r.side.observe(r.samples,1000,1000,true);cost=new HashMap<>(r.samples);cost.remove(UsagiAmountEvidence.AD);check(r.side.observe(cost,1350,1350,true)==null,"changing the upgrade kind resets two-frame confirmation");d=r.side.observe(cost,1700,1700,true);check(d!=null&&!d.advertisement,"two subsequent Necessary frames authorize the currency route only");
 }
 public static void main(String[] args)throws Exception{load();missionCases();amounts();advertisements();System.out.println("UsagiControlReplayTest: "+checks+" checks passed; six held-out 6753 preview frames, archived currency/CM amount controls; resize/offset/occlusion/acknowledgement/ad-result timelines are synthetic; Android delivery and actual upgrade effect untested");}
}
