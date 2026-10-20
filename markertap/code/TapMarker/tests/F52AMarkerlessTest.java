package jp.tapmarker;
import java.util.*;

/** The exact shipped layout references, native F-52A pixels and the new markerless targets. */
public final class F52AMarkerlessTest {
 static int checks;
 static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
 public static void main(String[] args)throws Exception{
  VirtualRecoveryReplayTest.evidence.clear();VirtualRecoveryReplayTest.evidence.putAll(VirtualPresetProfileTest.canonical("tests/current-layout-evidence.bin"));MarkerlessRelocationTest.targets=VirtualPresetProfileTest.canonical("tests/visual-targets.bin");Video6680ReplayTest.load();
  for(boolean usagi:new boolean[]{true,false}){String app=usagi?UsagiPreset.APP:FarmPreset.APP,prefix=usagi?UsagiPreset.PREFIX:FarmPreset.P;MarkerlessRelocationTest.Run r=new MarkerlessRelocationTest.Run(app,usagi?"tests/usagi-replay.bin":"tests/farm-replay.bin",prefix+"main");new UpgradeEngine(r.e,app);long now=1000;
   for(String name:usagi?new String[]{"0","0.5","1","3","14","17"}:new String[]{"63","80","168","173"}){VirtualPresetProfileTest.Image im=Video6680ReplayTest.images.get(name);Map<String,float[]> samples=null;float[] point=null;for(int retry=0;retry<3;retry++){samples=r.sample(im,im,now);now+=1700;point=r.layout.mainPointRelative(samples,im.width,im.height);if(point!=null)break;}
    PhaseEngine.Watch home=r.e.rule(prefix+(usagi?"return":"verify-home"));check(home.distance(samples.get(home.regionId))<=home.threshold,"shipped references recognize F-52A "+app+" / "+name);check(point!=null,"F-52A main point without markers: "+app+" / "+name+" / "+r.layout.status);check(point[0]>im.width*.28f&&point[0]<im.width*.73f&&point[1]>im.height*.70f&&point[1]<im.height*.88f,"detected point remains inside the real main button: "+name);r.e.observe(samples,now);check(r.e.poll()==null,"main/fever frame adds no blocking transition: "+name);}
  }
  System.out.println("F52AMarkerlessTest: "+checks+" checks passed; exact shipped references and existing native/scaled F-52A recording pixels; physical touch input untested");
 }
}
