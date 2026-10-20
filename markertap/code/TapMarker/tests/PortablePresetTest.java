package jp.tapmarker;
import java.util.*;
/** Resample existing recordings; this does not substitute for an Android device test. */
public class PortablePresetTest {
 static int checks;static void check(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
 public static void main(String[] args)throws Exception{
  VirtualRecoveryReplayTest.load();VirtualRecoveryReplayTest.layoutEvidence();
  for(boolean usagi:new boolean[]{true,false})for(int width:new int[]{540,720})for(boolean f52:new boolean[]{false,true}){
   String app=usagi?UsagiPreset.APP:FarmPreset.APP,prefix=usagi?UsagiPreset.PREFIX:FarmPreset.P;
   Map<String,float[]> refs=VirtualPresetProfileTest.canonical(usagi?"tests/usagi-replay.bin":"tests/farm-replay.bin");
   PhaseEngine e=new PhaseEngine();if(usagi)UsagiPreset.install(e,refs);else FarmPreset.install(e,refs);
   AdaptiveLayout layout=AdaptiveLayout.install(e,app,refs,VirtualRecoveryReplayTest.evidence);e.strictRuntime=true;
   VirtualPresetProfileTest.Image source=VirtualRecoveryReplayTest.images.get(usagi?"usagi-main":"farm-main-pressed");
   int height=f52?Math.round(width*2280f/1080):Math.round(width*source.height/(float)source.width);RegionSampler.Pixels scaled=(x,y)->source.get(Math.min(source.width-1,x*source.width/width),Math.min(source.height-1,y*source.height/height));
   e.enter(prefix+"verify",0);
   for(long now:new long[]{2000,2350})e.observe(layout.sample(e.activeRegions(),width,height,scaled,now),now);
   PhaseEngine.Watch result=e.poll();check(result!=null&&result.nextPhase.equals(prefix+"main"),"recorded home recognition at "+width+"x"+height+": "+app+" "+e.diagnostic);
   float[] point=layout.sample(e.activeRegions(),width,height,scaled,2600).get(AdaptiveLayout.MAIN);
   check(point!=null&&point[0]>0&&point[0]<width&&point[1]>0&&point[1]<height,"input coordinates remain within the resized display");
   e.enter(prefix+"verify",0);RegionSampler.Pixels unknown=(x,y)->0xff121212;
   for(long now:new long[]{3000,3350})e.observe(layout.sample(e.activeRegions(),width,height,unknown,now),now);
   PhaseEngine.Watch miss=e.poll();check(miss==null||!miss.nextPhase.equals(prefix+"main"),"unrecognized screen never starts main tapping at reduced resolution");
  }
  System.out.println("PortablePresetTest: "+checks+" checks passed; resampled recorded images, Android rendering untested");
 }
}
