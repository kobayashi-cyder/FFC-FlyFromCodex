package jp.tapmarker;
import java.util.*;

/** Existing six game families at synthetic 320px; device rendering is separate. */
public final class LowResolutionGamesTest {
 static int checks;
 static void check(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
 static boolean home(MarkerlessRelocationTest.Run r,Map<String,float[]> samples){
  if(r.app.equals(UsagiPreset.APP)){PhaseEngine.Watch watch=r.e.rule(UsagiPreset.PREFIX+"return");return watch.distance(samples.get(watch.regionId))<=watch.threshold;}
  if(r.app.equals(FarmPreset.APP))return FarmPreset.home(r.e,samples);
  return ConfiguredTargetReplayTest.playable(r,samples);
 }
 public static void main(String[] args)throws Exception{
  VirtualRecoveryReplayTest.load();VirtualRecoveryReplayTest.layoutEvidence();MarkerlessRelocationTest.targets=VirtualPresetProfileTest.canonical("tests/visual-targets.bin");
  String[][] rows={{UsagiPreset.APP,"tests/usagi-replay.bin","virtual-recovery-replay.bin","usagi-main"},{FarmPreset.APP,"tests/farm-replay.bin","virtual-recovery-replay.bin","farm-main"},{CoinPreset.APP,"tests/video6720-references.bin","video6720-replay.bin.gz","17"},{ShoppingPreset.APP,"tests/video6717-references.bin","video6717-replay.bin.gz","40"},{BoxPreset.APP,"tests/video6724-references.bin","video6724-replay.bin.gz","17.0"},{PoipoiPreset.APP,"tests/image6727-references.bin","video6725-replay.bin.gz","26.0"}};
  for(String[] row:rows){
   MarkerlessRelocationTest.Run r=new MarkerlessRelocationTest.Run(row[0],row[1],PresetRegistry.prefix(row[0])+"main");VirtualPresetProfileTest.Image im=MarkerlessRelocationTest.image(row[2],row[3]);int w=320,h=Math.round(w*im.height/(float)im.width);RegionSampler.Pixels pixels=FarmDialogReplayTest.resize(im,w,h,true);Map<String,float[]> samples=null;float[] point=null;
   for(int i=0;i<4;i++){samples=r.layout.sampleRelative(r.e.activeRegions(),w,h,pixels,1000+i*1700);point=r.layout.mainPointRelative(samples,w,h);if(point!=null&&home(r,samples))break;}
   check(home(r,samples),"320px game identity: "+row[0]);check(point!=null&&point[0]>0&&point[0]<w&&point[1]>0&&point[1]<h,"320px current play target: "+row[0]);
   check(!samples.containsKey(PlayableAdEvidence.KEY),"ordinary game is not a playable advertisement: "+row[0]);
   check(ContinuousTapPolicy.allow(r.e,true,false,false,false,true,7000,7050),"320px home can authorize continuous game input: "+row[0]);
   System.out.println("320px main replay: "+row[0]);
  }
  System.out.println("LowResolutionGamesTest: "+checks+" checks; six synthetic 320px main scenes, no Android input or all-state guarantee");
 }
}
