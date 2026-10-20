package jp.tapmarker;
import java.util.*;

/** Pixel reads are measured; host timing is not advertised as Android timing. */
public final class CoinSearchBudgetTest {
 static int checks;static Map<String,float[]> refs,targets,evidence;
 static void check(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
 static CoinLayout layout(boolean progressive){
  PhaseEngine e=new PhaseEngine();CoinPreset.install(e,refs);e.strictRuntime=true;CoinLayout layout=(CoinLayout)AdaptiveLayout.install(e,CoinPreset.APP,refs,evidence);layout.configureTargets(CoinPreset.APP,targets);layout.relocator.enabled=progressive;AdCloseAutomaton.install(e,CoinPreset.APP);e.enter(CoinPreset.P+"verify",0);return layout;
 }
 static final class Count implements RegionSampler.Pixels {long reads;public int get(int x,int y){reads++;return 0xff555555;}}
 static void bounded(){
  CoinLayout full=layout(false),limited=layout(true);Count baseline=new Count(),progressive=new Count();full.sample(full.engine.activeRegions(),320,718,FrameSampleCache.wrap(320,718,baseline),1000);
  Map<String,float[]> samples=limited.sample(limited.engine.activeRegions(),320,718,FrameSampleCache.wrap(320,718,progressive),1000);
  check(limited.lastWideSearches==2,"two missing-scene deep searches per frame");check(progressive.reads<baseline.reads*.70,"reduced miss pixel reads: "+progressive.reads+" / "+baseline.reads);
  for(int i=1;i<12;i++){samples=limited.sample(limited.engine.activeRegions(),320,718,new Count(),1000+i*2000);check(limited.lastWideSearches<=2,"per-frame deep-search cap");}
  for(PhaseEngine.Region r:limited.engine.activeRegions())if(limited.profile.extra.containsKey(r.id)&&!limited.references(r.id).isEmpty())check(limited.scaleSearched.containsKey(r.id),"rotation reaches absent candidate "+r.id);
  check(!AdExitPolicy.gameVisible(limited.engine,samples),"uniform screen is not a game");System.out.println("320px unknown-scene pixel reads: full="+baseline.reads+", bounded="+progressive.reads);
 }
 static void scenes()throws Exception{
  String[][] rows={{"17","home"},{"77.3","missions"},{"80.5","reward"},{"19.5","boost-rare"},{"30.5","boost-pocket"},{"24.8","fever"},{"36.5","clear"},{"66","bonus"}};
  for(String[] row:rows){
   CoinLayout layout=layout(true);VirtualPresetProfileTest.Image im=Video6720ReplayTest.images.get(row[0]);int w=320,h=Math.round(w*im.height/(float)im.width);RegionSampler.Pixels pixels=FarmDialogReplayTest.resize(im,w,h,true);PhaseEngine.Watch rule=layout.engine.rule(CoinPreset.P+"verify-"+row[1]);Map<String,float[]> samples=null;boolean found=false;
   for(int i=0;i<12;i++){samples=layout.sampleRelative(layout.engine.activeRegions(),w,h,pixels,1000+i*500);check(layout.lastWideSearches<=2,"current-position checks precede bounded wide search");if(rule.distance(samples.get(rule.regionId))<=rule.threshold){found=true;break;}}
   check(found,"held-out 320px scene "+Arrays.toString(row));samples=layout.sampleRelative(layout.engine.activeRegions(),w,h,new Count(),9000);check(rule.distance(samples.get(rule.regionId))>rule.threshold,"cached "+row[1]+" still needs current pixels");
  }
 }
 public static void main(String[] args)throws Exception{
  Video6720ReplayTest.load();refs=Video6720ReplayTest.refs;targets=VirtualPresetProfileTest.canonical("tests/visual-targets.bin");evidence=VirtualPresetProfileTest.canonical("tests/current-layout-evidence.bin");bounded();scenes();System.out.println("CoinSearchBudgetTest: "+checks+" checks; bounded miss work, fair rotation, eight synthetic 320px scenes and fresh evidence; Android analysis time unmeasured");
 }
}
