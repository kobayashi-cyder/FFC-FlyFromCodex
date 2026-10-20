package jp.tapmarker;
import java.io.*;import java.util.*;import java.util.zip.*;

/** Disjoint video pixels cover CM -> HOME -> browser -> game, optional boosts and stale observations. */
public final class Video6722ReplayTest {
 static int checks;static Map<String,float[]> refs;static final Map<String,VirtualPresetProfileTest.Image> images=new LinkedHashMap<>();
 static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 static void load()throws Exception{
  refs=VirtualPresetProfileTest.canonical("tests/video6722-references.bin");
  try(DataInputStream in=new DataInputStream(new GZIPInputStream(new FileInputStream("tests/video6722-replay.bin.gz")))){int n=in.readInt();for(int i=0;i<n;i++)images.put(in.readUTF(),new VirtualPresetProfileTest.Image(in));check(n==55&&in.read()==-1,"55 complete video holdouts");}
  check(!images.containsKey("1.0")&&!images.containsKey("61.0"),"neither training timestamp is reused as a replay frame");
 }
 static final class Run {
  final PhaseEngine e=new PhaseEngine();final CoinLayout layout;final AdHomeRecovery home=new AdHomeRecovery();Map<String,float[]> samples;long last;
  Run(String state){CoinPreset.install(e,refs);e.strictRuntime=true;layout=CoinLayout.install(e,refs);AdCloseAutomaton.install(e,CoinPreset.APP);e.enter(CoinPreset.P+state,0);}
  Map<String,float[]> sample(VirtualPresetProfileTest.Image im,RegionSampler.Pixels p,long at){last=at;return samples=layout.sampleRelative(e.activeRegions(),im.width,im.height,p,at);}
  Map<String,float[]> sample(String name,long at){VirtualPresetProfileTest.Image im=images.get(name);return sample(im,im,at);}
  boolean observe(String name,long at){VirtualPresetProfileTest.Image im=images.get(name);return home.observe(e,sample(name,at),im.width,im.height,at,at);}
 }
 static void positives(){
  String[] ads={"0","0.6","1.4","2.2","3.0","3.6","4.2","60.8","61.4","61.8","62.2","62.6","1.4-native","61.4-native"};
  for(String state:new String[]{"check","ad","verify","reward-ad","mission-ad"})for(String id:ads){Run r=new Run(state);check(r.sample(id,1000).containsKey(AdHomeRecovery.EVIDENCE),"both actual ad anchors: "+id+" / "+state);check(!r.observe(id,1000),"first ad observation cannot send HOME");check(!r.observe(id,1000),"reusing the same image cannot count as a second observation");check(r.observe(id,1350),"second fresh ad authorizes HOME: "+id+" / "+state);check(!CoinPreset.playable(r.e,r.samples),"CM never permits normal tapping");}
 }
 static void negatives(){
  Set<String> positives=new HashSet<>(Arrays.asList("0","0.6","1.4","2.2","3.0","3.6","4.2","60.8","61.4","61.8","62.2","62.6","1.4-native","61.4-native"));
  for(String id:images.keySet())if(!positives.contains(id)){
   Run r=new Run("ad");check(!r.observe(id,1000)&&!r.observe(id,1350),"game, popup, HOME, browser or loading never authorizes HOME: "+id);
  }
  for(String id:new String[]{"1.4-native","61.4-native"})for(int missing=0;missing<2;missing++){
   VirtualPresetProfileTest.Image im=images.get(id);int index=id.startsWith("1.")?0:1;float[] anchor=missing==0?AdHomeEvidence.HEADERS[index]:AdHomeEvidence.LABELS[index];int[] b=RegionMath.bounds(anchor,im.width,im.height);
   RegionSampler.Pixels absent=(x,y)->x>=b[0]-5&&x<b[2]+5&&y>=b[1]-5&&y<b[3]+5?0xff777777:im.get(x,y);Run r=new Run("ad");check(!r.sample(im,absent,1000).containsKey(AdHomeRecovery.EVIDENCE),"either missing anchor blocks HOME: "+id+" / "+missing);
  }
 }
 static void freshness(){
  Run r=new Run("ad");VirtualPresetProfileTest.Image im=images.get("61.4");Map<String,float[]> s=r.sample("61.4",1000);
  check(!r.home.observe(r.e,s,im.width,im.height,1000,1800),"old ad image rejected");check(!r.home.observe(r.e,s,im.width+1,im.height,1000,1000),"old screen dimensions rejected");check(AdHomeRecovery.visible(s,im.width,im.height),"fresh current ad can pass the HOME preflight");Map<String,float[]> gone=new HashMap<>(s);gone.remove(AdHomeRecovery.EVIDENCE);check(!AdHomeRecovery.visible(gone,im.width,im.height),"ad disappearing at the HOME preflight cancels it");
  check(!r.home.observe(r.e,s,im.width,im.height,1000,1000),"fresh first observation held");r.e.enter(r.e.current,1100);check(!r.home.observe(r.e,s,im.width,im.height,1350,1350),"new state entry requires new confirmation");
  check(!r.home.observe(r.e,s,im.width,im.height,2700,2700),"long gap cannot complete an old pair");
  Map<String,float[]> game=new HashMap<>(s);PhaseEngine.Watch w=r.e.rule(CoinPreset.P+"ad-home");game.put(w.regionId,w.reference.clone());check(!r.home.observe(r.e,game,im.width,im.height,3050,3050),"known game branch blocks HOME even if an ad cue is also present");
  r=new Run("main");check(!r.observe("61.4",1000)&&!r.observe("61.4",1350),"a gameplay state cannot directly authorize HOME");r.e.windowRelative=true;r.e.enter(CoinPreset.P+"ad",1500);check(!r.observe("61.4",2000),"learned/window-local graph cannot send global HOME");
 }
 static void recoveredGameAndBoosts(){
  Run r=new Run("verify");r.e.observe(r.sample("64",1000),1000);check(r.e.poll()==null,"blurred return animation cannot resume");r.e.observe(r.sample("65",1350),1350);check(r.e.poll()==null,"first clear game frame cannot resume");r.e.observe(r.sample("68",1700),1700);PhaseEngine.Watch w=r.e.poll();check(w!=null&&w.nextPhase.equals(CoinPreset.P+"main"),"HOME recovery ends at a confirmed game branch");r.e.finish(w,1750);
  check(CoinPreset.playable(r.e,r.sample("65",1800)),"fresh game after HOME can resume main input");check(!ContinuousTapPolicy.allow(r.e,true,false,false,true,true,1800,1800),"recovery still pending blocks taps");check(!ContinuousTapPolicy.allow(r.e,true,false,false,false,false,1800,1800),"HOME/browser never receives main taps");
  for(String key:CoinNoAdPolicy.BOOSTS){w=r.e.rule(CoinPreset.P+key+"-cm");check(w.action==PhaseEngine.BACK&&!w.nextPhase.endsWith("-ad"),"optional boost never starts CM: "+key);}
 }
 public static void main(String[] args)throws Exception{load();positives();negatives();freshness();recoveredGameAndBoosts();System.out.println("Video6722ReplayTest: "+checks+" checks passed; two-anchor ad pixels, HOME/browser negatives, stale guards and verified game resumption; physical HOME/input untested");}
}
