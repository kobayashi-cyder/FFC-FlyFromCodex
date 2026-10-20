package jp.tapmarker;
import java.util.*;

/** Missing/occluded Get text must not become an empty reward list. */
public final class PoipoiMissionVerificationTest {
 static int checks;
 static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 static void test()throws Exception{
  Video6725ReplayTest.load();
  for(String name:new String[]{"4.2","75.5"}){
   Video6725ReplayTest.Run run=new Video6725ReplayTest.Run("missions");VirtualPresetProfileTest.Image im=Video6725ReplayTest.images.get(name);Map<String,float[]> samples=run.sample(name,1000);
   PhaseEngine.Watch get=run.e.rule(PoipoiPreset.P+"get-tap");check(get.distance(samples.get(get.regionId))<=get.threshold,"real eligible reward is recognized: "+name);check(samples.get(PoipoiPreset.P+"empty")[0]==0,"an available reward blocks return");
   final int[] label=RegionMath.bounds(samples.get(get.regionId+AdaptiveLayout.BOUNDS),im.width,im.height);
   RegionSampler.Pixels obscured=(x,y)->x>=label[0]-2&&x<label[2]+2&&y>=label[1]-2&&y<label[3]+2?0xffed8525:im.get(x,y);
   samples=run.layout.sampleRelative(run.e.activeRegions(),im.width,im.height,obscured,2000);
   check(samples.get(PoipoiPreset.P+"empty")[0]==0,"unreadable reward text is unknown, not received");
   run.e.observe(samples,7000);run.e.poll();run.e.observe(samples,7350);PhaseEngine.Watch next=run.e.poll();check(next==null||next.action!=PhaseEngine.CENTER&&next.action!=PhaseEngine.RETURN_APP,"unknown list cannot go back or restart on timeout");
  }
  for(String name:new String[]{"87.5","85.2","86.2"}){
   Video6725ReplayTest.Run run=new Video6725ReplayTest.Run("missions");Map<String,float[]> samples=run.sample(name,1000);check(samples.get(PoipoiPreset.P+"empty")[0]==1,"independent Play-only display positively permits return: "+name);
  }
  for(int w:new int[]{320,360,540}){
   final VirtualPresetProfileTest.Image im=Video6725ReplayTest.images.get("75.5");int h=Math.round(w*718f/320);RegionSampler.Pixels scaled=(x,y)->im.get(Math.min(im.width-1,x*im.width/w),Math.min(im.height-1,y*im.height/h));
   Video6725ReplayTest.Run run=new Video6725ReplayTest.Run("missions");Map<String,float[]> samples=run.layout.sampleRelative(run.e.activeRegions(),w,h,scaled,1000);PhaseEngine.Watch get=run.e.rule(PoipoiPreset.P+"get-tap");check(get.distance(samples.get(get.regionId))<=get.threshold,"Get remains detectable at "+w+"px");check(samples.get(PoipoiPreset.P+"empty")[0]==0,"resizing cannot mark an eligible reward empty");
  }
  Map<String,float[]> missing=new HashMap<>(Video6725ReplayTest.refs);missing.remove("mission-play");PhaseEngine e=new PhaseEngine();PoipoiPreset.install(e,missing);PoipoiLayout layout=PoipoiLayout.install(e,missing);e.enter(PoipoiPreset.P+"missions",0);VirtualPresetProfileTest.Image im=Video6725ReplayTest.images.get("87.5");check(layout.sampleRelative(e.activeRegions(),im.width,im.height,im,1000).get(PoipoiPreset.P+"empty")[0]==0,"missing positive evidence fails closed without inventing completion");
 }
 public static void main(String[] args)throws Exception{test();System.out.println("PoipoiMissionVerificationTest: "+checks+" checks passed; visible-page claim eligibility is separate from daily completion and actual reward credit");}
}
