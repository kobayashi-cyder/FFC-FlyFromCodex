package jp.tapmarker;
import java.io.*;import java.util.*;
/** Actual 2026-10-13 screenshot, with independent negative and geometry checks. */
public final class FarmCloseScreenshotTest {
 static int checks;static void check(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
 static void input(VirtualPresetProfileTest.Image image,int w,int h,RegionSampler.Pixels pixels,String state)throws Exception{
  FarmDialogReplayTest.Run r=new FarmDialogReplayTest.Run();Map<String,float[]> samples=r.sample(state,w,h,pixels,1000);
  r.e.observe(samples,2000);check(r.e.poll()==null,"first fresh frame waits in "+state);
  r.e.observe(samples,2350);PhaseEngine.Watch event=r.e.poll();check(event!=null&&event.nextPhase.equals(FarmPreset.P+(state.equals("reward")?"check":"reward")),"actual screenshot identifies reward from "+state+" at "+w+"x"+h+" / "+r.e.diagnostic);
  if(state.equals("reward")){float[] point=r.layout.pointRelative(event,samples,w,h);check(event.action==PhaseEngine.CENTER&&point!=null&&point[0]>w*.40f&&point[0]<w*.60f&&point[1]>h*.640f&&point[1]<h*.685f,"tap is inside the screenshot close button, above harvest");check(r.e.confirmsAction(event,r.e.current,r.e.epoch,samples),"input requires current label and heading");check(r.layout.pointRelative(event,samples,w+1,h)==null,"resized frame invalidates old coordinates");}
 }
 public static void main(String[] args)throws Exception{
  FarmDialogReplayTest.load();VirtualPresetProfileTest.Image image;try(DataInputStream in=new DataInputStream(new FileInputStream("tests/farm-close-20261013.bin"))){image=new VirtualPresetProfileTest.Image(in);}
  for(String state:new String[]{"main","check","reward","ad","verify"})input(image,image.width,image.height,image,state);
  for(int[] size:new int[][]{{540,1213},{720,1616},{1080,2280}})for(boolean smooth:new boolean[]{false,true})input(image,size[0],size[1],FarmDialogReplayTest.resize(image,size[0],size[1],smooth),"reward");
  for(float[] box:FarmDialogEvidence.CLOSE){int[] bounds=RegionMath.bounds(box,image.width,image.height);RegionSampler.Pixels erased=(x,y)->x>=bounds[0]&&x<bounds[2]&&y>=bounds[1]&&y<bounds[3]?0xffaaaaaa:image.get(x,y);check(!new FarmDialogEvidence(FarmDialogReplayTest.evidence).locate(FarmPreset.P+"close",image.width,image.height,erased,1000,new HashMap<>()),"erasing either the message or label cancels the close");}
  FarmDialogReplayTest.Run r=new FarmDialogReplayTest.Run();int left=180,top=70,w=image.width+400,h=image.height+100;RegionSampler.Pixels desktop=(x,y)->x>=left&&x<left+image.width&&y>=top&&y<top+image.height?image.get(x-left,y-top):0xff223344;r.e.enter(FarmPreset.P+"reward",0);Map<String,float[]> samples=r.layout.sampleRelative(r.e.activeRegions(),w,h,desktop,1000,new int[]{left,top,left+image.width,top+image.height});r.e.observe(samples,2000);r.e.observe(samples,2350);PhaseEngine.Watch event=r.e.poll();float[] point=event==null?null:r.layout.pointRelative(event,samples,w,h);check(point!=null&&point[0]>left+image.width*.40f&&point[0]<left+image.width*.60f&&point[1]>top+image.height*.640f&&point[1]<top+image.height*.685f,"desktop offset is included exactly once");
  System.out.println("FarmCloseScreenshotTest: "+checks+" checks passed; supplied pixels, resampling, omitted anchors and desktop offset; Android input untested");
 }
}
