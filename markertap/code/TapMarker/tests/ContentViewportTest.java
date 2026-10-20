package jp.tapmarker;
import java.util.*;

/** Real game pixels embedded in independently positioned desktop windows. */
public final class ContentViewportTest {
 static int checks;static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 static void games()throws Exception{
  VirtualRecoveryReplayTest.load();VirtualRecoveryReplayTest.layoutEvidence();
  for(boolean usagi:new boolean[]{true,false})for(int[] window:new int[][]{{120,48,760,1320},{520,96,1020,1100}}){
   AdaptiveLayoutTest.Runtime run=new AdaptiveLayoutTest.Runtime(usagi);VirtualPresetProfileTest.Image im=VirtualRecoveryReplayTest.images.get(usagi?"usagi-main":"farm-main");int fw=1440,fh=1500,left=window[0],top=window[1],width=window[2]-left,height=window[3]-top;
   RegionSampler.Pixels desktop=(x,y)->x<left||x>=left+width||y<top||y>=top+height?0xff454545:im.get(Math.min(im.width-1,(x-left)*im.width/width),Math.min(im.height-1,(y-top)*im.height/height));
   run.e.enter(run.prefix+"verify",0);Map<String,float[]> s=run.layout.sampleRelative(run.e.activeRegions(),fw,fh,desktop,2000,window);run.e.observe(s,2000);check(run.e.poll()==null,"first window observation holds");s=run.layout.sampleRelative(run.e.activeRegions(),fw,fh,desktop,2200,window);run.e.observe(s,2200);PhaseEngine.Watch w=run.e.poll();check(w!=null&&w.nextPhase.equals(run.prefix+"main"),"window content recognizes recorded app "+usagi);
   float[] p=run.layout.mainPointRelative(s,fw,fh);float expectedY=top+height*(usagi?.775f:.76f);check(p!=null&&Math.abs(p[0]-left-width*.5f)<width*.025f&&Math.abs(p[1]-expectedY)<height*.025f,"tap follows window origin and relative content");check(run.layout.mainPointRelative(s,fw+1,fh)==null,"stale size cannot produce a tap");
   PhaseEngine.Watch center=run.e.rule(usagi?run.prefix+"ミッション中央をタップ":run.prefix+"close-tap");check(run.layout.pointRelative(center,new HashMap<>(),fw,fh)==null,"unknown UI supplies no guessed action coordinates");
  }
 }
 static void letterbox(){
  int w=1440,h=1000,l=500,r=940;RegionSampler.Pixels p=(x,y)->x<l||x>=r?0xff000000:0xff70bd64;ContentViewport v=ContentViewport.locate(w,h,p,null);check(v.left==l&&v.width==r-l&&v.height==h,"paired portrait side bars are removed");float[] point=ContentViewport.offset(new float[]{220,780},v.metadata(),w,h);check(point[0]==720&&point[1]==780,"letterbox offset is shared by input");
  RegionSampler.Pixels dark=(x,y)->0xff000000;v=ContentViewport.locate(w,h,dark,null);check(v.width==w,"blank image is not invented as an app content area");
  RegionSampler.Pixels unpaired=(x,y)->x<l?0xff000000:0xff70bd64;v=ContentViewport.locate(w,h,unpaired,null);check(v.width==w,"unpaired dark game artwork is preserved");
 }
 public static void main(String[] args)throws Exception{games();letterbox();System.out.println("ContentViewportTest: "+checks+" checks passed; synthetic desktop windows around real video pixels, no ChromeOS device execution");}
}
