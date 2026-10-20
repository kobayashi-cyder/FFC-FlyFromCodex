package jp.tapmarker;
import java.util.*;

/** Real rainbow-fever frames and interruptions; no simulated Android gesture completion. */
public final class FarmFeverTapTest {
 static int checks;
 static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 static final class Run {
  final PhaseEngine e=new PhaseEngine();final AdaptiveLayout layout;
  Run(Map<String,float[]> evidence)throws Exception{FarmPreset.install(e,VirtualPresetProfileTest.canonical("tests/farm-replay.bin"));layout=AdaptiveLayout.install(e,FarmPreset.APP,VirtualPresetProfileTest.canonical("tests/farm-replay.bin"),evidence);layout.configureTargets(FarmPreset.APP,VirtualPresetProfileTest.canonical("tests/visual-targets.bin"));new UpgradeEngine(e,FarmPreset.APP);e.strictRuntime=true;e.enter(FarmPreset.P+"main",0);}
  Map<String,float[]> sample(VirtualPresetProfileTest.Image image,RegionSampler.Pixels pixels,long at){return layout.sampleRelative(e.activeRegions(),image.width,image.height,pixels,at);}
 }
 static void fever(Run r,VirtualPresetProfileTest.Image image,String name)throws Exception{
  for(int i=0;i<8;i++){long at=1000+i*350;Map<String,float[]> samples=r.sample(image,image,at);check(FarmPreset.home(r.e,samples),"fever remains a recognized harvest scene: "+name);float[] point=r.layout.mainPointRelative(samples,image.width,image.height);if(point==null)System.out.println("fever target missing "+name+" / "+i+" transform="+Arrays.toString(r.layout.transform)+" tracks="+r.layout.relocator.tracks.keySet()+" keys="+samples.keySet());check(point!=null&&point[0]>image.width*.35f&&point[0]<image.width*.67f&&point[1]>image.height*.72f&&point[1]<image.height*.88f,"changing fever colours keep a current point inside harvest: "+name);r.e.observe(samples,at);PhaseEngine.Watch event=r.e.poll();check(event==null,"fever alone does not enter chest, reward, verification or ad wait: "+name+" / "+(event==null?"":event.id));check(ContinuousTapPolicy.allow(r.e,true,false,false,false,true,at,at+20),"actual continuous policy permits each fresh fever frame: "+name);}
  Map<String,float[]> stale=r.sample(image,image,5000);check(!ContinuousTapPolicy.allow(r.e,FarmPreset.home(r.e,stale),false,false,false,true,5000,6600),"fever does not bypass image freshness");
 }
 public static void main(String[] args)throws Exception{
  FarmDialogReplayTest.load();fever(new Run(FarmDialogReplayTest.evidence),FarmDialogReplayTest.images.get("20"),"Pixel recording 6612, 20s");
  Video6680ReplayTest.load();fever(new Run(Video6680ReplayTest.evidence),Video6680ReplayTest.images.get("168"),"F-52A recording 6680, 168s");
  for(String id:new String[]{"42","44","66.2","68","70.2"}){Run r=new Run(FarmDialogReplayTest.evidence);VirtualPresetProfileTest.Image im=FarmDialogReplayTest.images.get(id);Map<String,float[]> samples=r.sample(im,im,1000);r.e.observe(samples,1000);check(!ContinuousTapPolicy.allow(r.e,FarmPreset.home(r.e,samples),false,false,false,true,1000,1020),"chest, dimmed reward, CM and confirmation still hold main taps: "+id);}
  System.out.println("FarmFeverTapTest: "+checks+" checks passed; actual Pixel/F-52A fever pixels, continuous policy and interruption/freshness checks; physical input untested");
 }
}
