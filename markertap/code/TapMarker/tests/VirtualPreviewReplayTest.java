package jp.tapmarker;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;

/** Recorded internal pixels are independent of how large their phone preview is. */
public final class VirtualPreviewReplayTest {
 static int checks;
 static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
 public static void main(String[] args)throws Exception{
  for(int second:new int[]{6,16,26,42,52,64,80,95,105}){
   BufferedImage frame=ImageIO.read(new File("tests/fixtures/video6743/farm-"+second+".png"));
   int width=frame.getWidth(),height=frame.getHeight();
   Map<String,float[]> samples=new HashMap<>();
   FarmAdErrorEvidence.sample(samples,width,height,(x,y)->frame.getRGB(x,y));
   float[] target=FarmAdErrorEvidence.point(samples,width,height);
   check((target!=null)==(second>=52),"recorded dialog at "+second);
   if(target==null)continue;
   FarmAdErrorRecovery left=new FarmAdErrorRecovery(),right=new FarmAdErrorRecovery();
   long time=1000;
   for(int percent:VirtualPreview.PERCENTAGES){
    int[] size=VirtualPreview.size(480,width,height,60,1080,2424,percent,false,280);
    // Include non-matching view proportions to reproduce FIT_CENTER margins.
    int viewWidth=size[0]+32,viewHeight=size[1]-60;
    float[] rect=VirtualPreview.viewport(width,height,viewWidth,viewHeight);
    float x=rect[0]+target[0]/width*(rect[2]-rect[0]);
    float y=rect[1]+target[1]/height*(rect[3]-rect[1]);
    float[] mapped=VirtualPreview.point(10,width,height,width,height,viewWidth,viewHeight,x,y);
    check(mapped!=null&&Math.abs(mapped[0]-target[0])<.02&&Math.abs(mapped[1]-target[1])<.02,"same recorded OK at "+percent+"%");
    left.observe(true,time,time+50);time+=200;
    check(!right.visible(),"another display's recovery remains independent");
   }
   check(left.confirmed(),"preview resize does not reset fresh-frame confirmation");
   int[] minimal=VirtualPreview.size(480,width,height,60,1080,2424,35,true,280);
   check(minimal[1]==61&&FarmAdErrorEvidence.point(samples,width,height)!=null,"title-only preview retains internal evidence");
  }
  System.out.println("VirtualPreviewReplayTest: "+checks+" checks passed on video6743 pixels; device input not tested");
 }
}
