package jp.tapmarker;

public final class VirtualPreviewTest {
 static int checks;
 static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
 static void near(float actual,float expected,String label){check(Math.abs(actual-expected)<.02f,label+": "+actual+" vs "+expected);}
 static float[] point(int display,int viewWidth,int viewHeight,float x,float y){return VirtualPreview.point(display,720,1600,270,600,viewWidth,viewHeight,x,y);}
 public static void main(String[] args){
  // A square preview has side bars. The old whole-view division misrouted this tap.
  float[] p=point(10,400,400,128,300);near(p[0],72,"left-side image tap");near(p[1],1200,"vertical image tap");
  check(point(10,400,400,50,300)==null,"left letterbox is not a game target");
  check(point(10,400,400,300,300)==null,"right letterbox is not a game target");
  check(point(10,400,400,290,300)==null,"right image edge excluded");
  p=point(11,720,400,288,80);near(p[0],72,"second display remains local");near(p[1],320,"second display local y");
  // A wide image fitted into a tall view has top/bottom bars instead.
  check(VirtualPreview.point(11,1600,720,600,270,400,400,100,50)==null,"top bar excluded");
  p=VirtualPreview.point(11,1600,720,600,270,400,400,100,200);near(p[0],400,"landscape local x");near(p[1],360,"landscape local y");
  for(int percent:VirtualPreview.PERCENTAGES){
   int[] portrait=VirtualPreview.size(480,720,1600,60,1080,2424,percent,false,280);
   near(portrait[0],Math.round(480*percent/100f),"selected preview size");
   check(portrait[1]<=2424&&portrait[1]>60,"image plus title");
   int[] landscape=VirtualPreview.size(480,720,1600,60,2424,1080,percent,false,280);
   check(landscape[0]<=portrait[0]&&landscape[1]<=1080,"rotation stays on screen");
   int[] restored=VirtualPreview.size(480,720,1600,60,1080,2424,percent,false,280);
   check(restored[0]==portrait[0]&&restored[1]==portrait[1],"rotation restores the original size");
   int[] title=VirtualPreview.size(480,720,1600,60,1080,2424,percent,true,280);
   check(title[0]==280&&title[1]==61,"title-only mode does not depend on percentage");
  }
  for(float bad:new float[]{Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY,-1})
   check(point(10,400,400,bad,100)==null,"invalid touch rejected");
  check(point(0,400,400,200,200)==null,"physical display cannot be substituted");
  check(point(-1,400,400,200,200)==null,"missing display rejected");
  check(point(10,0,400,0,100)==null,"not laid out yet");
  check(VirtualPreview.point(10,720,1600,0,600,400,400,200,200)==null,"missing preview pixels");
  check(VirtualPreview.percent(13)==100,"invalid saved preference defaults to full size");
  // Thumbnail height is rounded: use its actual aspect, not an assumed display ratio.
  p=VirtualPreview.point(10,720,1600,270,601,400,400,200,200);near(p[0],360,"rounded thumbnail centre x");near(p[1],800,"rounded thumbnail centre y");
  System.out.println("VirtualPreviewTest: "+checks+" checks passed");
 }
}
