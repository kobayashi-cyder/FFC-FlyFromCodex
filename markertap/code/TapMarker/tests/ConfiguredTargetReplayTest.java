package jp.tapmarker;
import java.util.*;

/** Timestamp-disjoint replay with the actual markerless template configuration enabled. */
public final class ConfiguredTargetReplayTest {
 static int checks;
 static void check(boolean ok,String label){checks++;if(!ok)throw new AssertionError(label);}
 static boolean playable(MarkerlessRelocationTest.Run r,Map<String,float[]> samples){if(r.app.equals(PoipoiPreset.APP))return PoipoiPreset.playable(r.e,samples);if(r.app.equals(BoxPreset.APP))return BoxPreset.playable(r.e,samples);if(r.app.equals(ShoppingPreset.APP))return ShoppingPreset.playable(r.e,samples);return CoinPreset.playable(r.e,samples);}
 static void group(String app,String refs,String prefix,Map<String,VirtualPresetProfileTest.Image> images,String[] positives,String[] negatives)throws Exception{
  MarkerlessRelocationTest.Run r=new MarkerlessRelocationTest.Run(app,refs,prefix+"main");long now=1000;
  for(String id:positives){VirtualPresetProfileTest.Image im=images.get(id);check(im!=null,"held-out image exists "+id);Map<String,float[]> samples=r.sample(im,im,now);now+=1700;if(!playable(r,samples)){for(PhaseEngine.Watch watch:r.e.phases.get(r.e.current).watches)if(watch.condition==PhaseEngine.MATCH&&watch.distance(samples.get(watch.regionId))<=watch.threshold)System.out.println("unexpected match "+watch.id+" / "+watch.distance(samples.get(watch.regionId))+" / "+Arrays.toString(samples.get(watch.regionId+AdaptiveLayout.BOUNDS)));}check(playable(r,samples),"ordinary frame remains playable with independent matching: "+app+" / "+id);float[] p=r.layout.mainPointRelative(samples,im.width,im.height);check(p!=null,"actual play object available on held-out timestamp: "+app+" / "+id);}
  for(String id:negatives){VirtualPresetProfileTest.Image im=images.get(id);check(im!=null,"negative frame exists "+id);Map<String,float[]> samples=r.sample(im,im,now);now+=1700;check(!playable(r,samples),"CM, reward and confirmation never become ordinary play: "+app+" / "+id);}
  System.out.println("configured replay: "+app+" / "+positives.length+" normal, "+negatives.length+" interruptions");
 }
 public static void main(String[] args)throws Exception{
  VirtualRecoveryReplayTest.load();VirtualRecoveryReplayTest.layoutEvidence();MarkerlessRelocationTest.targets=VirtualPresetProfileTest.canonical("tests/visual-targets.bin");
  Video6725ReplayTest.load();group(PoipoiPreset.APP,"tests/image6727-references.bin",PoipoiPreset.P,Video6725ReplayTest.images,new String[]{"16.0","18.0","28.0","38.0","50.0","60.0","68.0","70.0","72.0","96.5","98.5"},new String[]{"0.8","13.6","4.2","12.2","84.2","2.2","72.9","74.2","6.8","81.5","8.4","22.4","92.5"});Video6725ReplayTest.images.clear();
  Video6724ReplayTest.load();group(BoxPreset.APP,"tests/video6724-references.bin",BoxPreset.P,Video6724ReplayTest.images,new String[]{"15.0","25.0","32.0","48.5","50.5"},new String[]{"2.1","38.9","40.6","47.7","3.5","42.8","36.0","46.5","51.5"});Video6724ReplayTest.images.clear();
  Video6717ReplayTest.load();group(ShoppingPreset.APP,"tests/video6717-references.bin",ShoppingPreset.P,Video6717ReplayTest.images,new String[]{"0","3","44","82","90","120","154","156","164","166","0-native"},new String[]{"80","116","118","170"});Video6717ReplayTest.images.clear();
  Video6720ReplayTest.load();group(CoinPreset.APP,"tests/video6720-references.bin",CoinPreset.P,Video6720ReplayTest.images,new String[]{"15","85","89"},new String[]{"18.5","30.5","36.2","77.3"});Video6720ReplayTest.images.clear();
  System.out.println("ConfiguredTargetReplayTest: "+checks+" checks passed; independent templates on held-out normal frames and interruptions; Android input untested");
 }
}
