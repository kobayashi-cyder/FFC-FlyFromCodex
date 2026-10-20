package jp.tapmarker;
import java.io.*;import java.util.*;import java.util.zip.*;

/** Timestamp-disjoint video pixels validate the observable loop and reject unrelated screens. */
public final class Video6724ReplayTest {
 static int checks;static Map<String,float[]> refs;static final Map<String,VirtualPresetProfileTest.Image> images=new LinkedHashMap<>();
 static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 static void load()throws Exception{
  refs=VirtualPresetProfileTest.canonical("tests/video6724-references.bin");
  try(DataInputStream in=new DataInputStream(new GZIPInputStream(new FileInputStream("tests/video6724-replay.bin.gz")))){int n=in.readInt();for(int i=0;i<n;i++)images.put(in.readUTF(),new VirtualPresetProfileTest.Image(in));check(n>=77&&in.read()==-1,"complete holdout recording");}
  for(String t:new String[]{"14.0","1.6","11.8","38.8","47.3","40.3","4.4","42.4","34.4"})check(!images.containsKey(t),"training timestamp excluded: "+t);
 }
 static final class Run {
  final PhaseEngine e=new PhaseEngine();final BoxLayout layout;final AdHomeRecovery home=new AdHomeRecovery();Map<String,float[]> samples;
  Run(String state){BoxPreset.install(e,refs);e.strictRuntime=true;layout=BoxLayout.install(e,refs);AdCloseAutomaton.install(e,BoxPreset.APP);e.enter(BoxPreset.P+state,0);}
  Map<String,float[]> sample(String id,long at){VirtualPresetProfileTest.Image im=images.get(id);return samples=layout.sampleRelative(e.activeRegions(),im.width,im.height,im,at);}
  PhaseEngine.Watch event(String id,long at){e.observe(sample(id,at),at);check(e.poll()==null,"first frame waits for confirmation: "+id+" / "+e.current);e.observe(sample(id,at+350),at+350);return e.poll();}
 }
 static void classify(){
  String[][] groups={{"notice","2.0","2.1","10.5","11.0"},{"title","12.0","12.3"},{"game","13.0","13.6","13.6-native","20.0","31.5","32.5","48.0","50.5"},{"bonus","38.7","38.9"},{"claim","40.0","40.6","40.6-native"},{"reward","47.0","47.7","47.7-native"}};
  for(String state:new String[]{"check","verify","ad","wait"})for(String[] group:groups)for(int i=1;i<group.length;i++){
   Run r=new Run(state);PhaseEngine.Watch w=r.event(group[i],1000);String target=group[0].equals("game")?"main":group[0];check(w!=null&&w.nextPhase.equals(BoxPreset.P+target),"observed "+group[i]+" -> "+target+" / "+state+" / "+r.e.diagnostic);r.e.finish(w,1400);
   check(BoxPreset.playable(r.e,r.samples)==target.equals("main"),"only ordinary play enables taps: "+group[i]);
  }
 }
 static void controls(){
  for(String[] row:new String[][]{{"notice","2.1"},{"title","12.3"},{"bonus","38.9"},{"claim","40.6"},{"reward","47.7"}}){
   Run r=new Run(row[0]);PhaseEngine.Watch w=r.event(row[1],1000);check(w!=null&&w.action==PhaseEngine.CENTER,"known control is selected: "+row[0]);
   VirtualPresetProfileTest.Image im=images.get(row[1]);float[] point=r.layout.pointRelative(w,r.samples,im.width,im.height);check(point!=null,"current frame supplies touch bounds: "+row[0]);
   float[] b=r.samples.get(w.regionId+AdaptiveLayout.BOUNDS);check(b!=null&&point[0]>=b[0]*im.width&&point[0]<=b[2]*im.width&&point[1]>=b[1]*im.height&&point[1]<=b[3]*im.height,"touch stays in identified control: "+row[0]);
   check(r.e.confirmsAction(w,r.e.current,r.e.epoch,r.samples),"fresh control passes preflight");Map<String,float[]> gone=new HashMap<>();check(!r.e.confirmsAction(w,r.e.current,r.e.epoch,gone),"disappearing control cancels preflight");
   check(!ContinuousTapPolicy.allow(r.e,true,false,false,false,true,1350,1350),"button action state never permits main taps");
   if(row[0].equals("bonus"))check(point[1]>.68f*im.height&&w.nextPhase.equals(BoxPreset.P+"wait"),"continue uses the observed non-ad text below CM offer");
   if(row[0].equals("claim"))check(point[1]<.20f*im.height&&w.nextPhase.equals(BoxPreset.P+"ad"),"100-throw claim is the upper reward button");
  }
 }
 static void mainLoop(){
  Run r=new Run("verify");PhaseEngine.Watch w=r.event("13.6",1000);check(w!=null,"verified game can resume");r.e.finish(w,1400);
  for(String id:new String[]{"15.0","25.0","32.0","48.5","50.5"}){r.sample(id,2000);check(BoxPreset.playable(r.e,r.samples),"different cube piles retain normal tapping: "+id);check(ContinuousTapPolicy.allow(r.e,true,false,false,false,true,2000,2000),"fresh main frame permits taps");r.e.observe(r.samples,2000);check(r.e.poll()==null,"cube animation does not cause a control transition");}
  for(String id:new String[]{"2.1","38.9","40.6","47.7","3.5","42.8","36.0","46.5","51.5"}){r.sample(id,2500);check(!BoxPreset.playable(r.e,r.samples),"popup, ad, HOME or shade suspends throws: "+id);}
  r.sample("13.6",3000);check(!ContinuousTapPolicy.allow(r.e,true,false,false,false,false,3000,3000),"external package receives no normal taps");check(!ContinuousTapPolicy.allow(r.e,true,false,false,true,true,3000,3000),"HOME recovery suspends taps");check(!ContinuousTapPolicy.allow(r.e,true,false,false,false,true,3000,4500),"expired screenshot suspends taps");
 }
 static void homeAds(){
  Set<String> ads=new HashSet<>(Arrays.asList("3.0","4.0","5.0","6.0","7.0","8.0","3.5","8.5","33.5","34.0","35.0","41.6","42.0","43.0","42.8","4.0-native","42.8-native"));
  for(String id:images.keySet()){
   Run r=new Run("ad");VirtualPresetProfileTest.Image im=images.get(id);r.sample(id,1000);check(AdHomeRecovery.visible(r.samples,im.width,im.height)==ads.contains(id),"paired CM evidence / non-ad rejection: "+id);
   check(!r.home.observe(r.e,r.samples,im.width,im.height,1000,1000),"first ad image cannot send HOME");check(!r.home.observe(r.e,r.samples,im.width,im.height,1000,1100),"duplicate image notification does not authorize HOME");r.sample(id,1350);check(r.home.observe(r.e,r.samples,im.width,im.height,1350,1350)==ads.contains(id),"second fresh image authorizes only observed CM: "+id);
  }
  Run r=new Run("ad");VirtualPresetProfileTest.Image im=images.get("42.8");r.sample("42.8",1000);check(!r.home.observe(r.e,r.samples,im.width,im.height,1000,1800),"stale ad evidence rejected");check(!r.home.observe(r.e,r.samples,im.width+1,im.height,1000,1000),"screen-size change invalidates ad evidence");
  for(int family=0;family<3;family++)for(int a=0;a<2;a++){
   final VirtualPresetProfileTest.Image ad=images.get(family==0?"4.0-native":family==1?"42.8-native":"34.0");final int[] b=RegionMath.bounds(BoxAdHomeEvidence.PAIRS[family][a],ad.width,ad.height);RegionSampler.Pixels absent=(x,y)->x>=b[0]-5&&x<b[2]+5&&y>=b[1]-5&&y<b[3]+5?0xff777777:ad.get(x,y);r=new Run("ad");Map<String,float[]> s=r.layout.sampleRelative(r.e.activeRegions(),ad.width,ad.height,absent,1000);check(!s.containsKey(AdHomeRecovery.EVIDENCE),"both separate ad anchors are required: "+family+" / "+a);
  }
  r=new Run("main");r.sample("42.8",1000);check(!r.samples.containsKey(AdHomeRecovery.EVIDENCE),"main state cannot directly request HOME");r.e.windowRelative=true;r.e.enter(BoxPreset.P+"ad",0);check(!AdHomeRecovery.eligible(r.e),"learned window graph cannot request global HOME");
 }
 static void scaled(){
  final VirtualPresetProfileTest.Image im=images.get("13.6-native");
  for(int[] size:new int[][]{{360,808},{540,1200},{720,1600}}){final int w=size[0],h=size[1];RegionSampler.Pixels pixels=(x,y)->im.get(Math.min(im.width-1,x*im.width/w),Math.min(im.height-1,y*im.height/h));Run r=new Run("verify");Map<String,float[]> s=r.layout.sampleRelative(r.e.activeRegions(),w,h,pixels,1000);PhaseEngine.Watch game=r.e.rule(BoxPreset.P+"verify-game");check(game.distance(s.get(game.regionId))<=game.threshold,"normalized gameplay evidence at "+w+"x"+h);float[] p=r.layout.mainPointRelative(s,w,h);check(p!=null&&p[0]>.4f*w&&p[0]<.6f*w&&p[1]>.6f*h&&p[1]<.75f*h,"throw target stays in the tray");}
  for(float[] anchor:BoxPreset.GAME){final int[] b=RegionMath.bounds(anchor,im.width,im.height);RegionSampler.Pixels absent=(x,y)->x>=b[0]-5&&x<b[2]+5&&y>=b[1]-5&&y<b[3]+5?0xff777777:im.get(x,y);Run r=new Run("main");Map<String,float[]> s=r.layout.sampleRelative(r.e.activeRegions(),im.width,im.height,absent,1000);check(!BoxPreset.playable(r.e,s),"either missing gameplay landmark blocks normal input");}
 }
 public static void main(String[] args)throws Exception{load();classify();controls();mainLoop();homeAds();scaled();System.out.println("Video6724ReplayTest: "+checks+" checks passed; timestamp-disjoint box loop, 100-throw claim, safe controls, ad/HOME negatives, resizing and stale-image guards; physical device untested");}
}
