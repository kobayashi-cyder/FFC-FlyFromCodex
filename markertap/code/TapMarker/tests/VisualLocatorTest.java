package jp.tapmarker;

import java.util.*;

public final class VisualLocatorTest {
 static int checks;
 static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
 static final int W=240,H=440;
 static final float[] A={.15f,.12f,.45f,.23f};
 static int pattern(float u,float v){int r=40+(int)(u*120),g=60+(int)(v*140),b=65;if((u>.15&&u<.28&&v>.18&&v<.82)||(u>.4&&u<.85&&v>.4&&v<.55)){r=240;g=230;b=110;}if((u-.73)*(u-.73)+(v-.23)*(v-.23)<.022){r=30;g=45;b=180;}return 0xff000000|r<<16|g<<8|b;}
 static RegionSampler.Pixels screen(float[]... boxes){return (x,y)->{for(float[] b:boxes){float u=(x/(float)W-b[0])/(b[2]-b[0]),v=(y/(float)H-b[1])/(b[3]-b[1]);if(u>=0&&u<1&&v>=0&&v<1)return pattern(u,v);}return 0xff18202c;};}
 public static void main(String[] args){
  float[] ref=RegionSampler.sample(W,H,A,screen(A));long started=System.nanoTime();
  for(float[] b:new float[][]{{.62f,.71f,.92f,.82f},{.05f,.52f,.29f,.608f},{.44f,.35f,.82f,.49f}}){VisualLocator.Match match=VisualLocator.find(W,H,screen(b),ref,null,A,.012f);check(match!=null,"finds distant object with scale change "+Arrays.toString(b));float[] p=RegionMath.center(match.bounds,W,H);check(Math.abs(p[0]/W-(b[0]+b[2])/2)<.025&&Math.abs(p[1]/H-(b[1]+b[3])/2)<.018,"point belongs to detected object");}
  check(VisualLocator.find(W,H,screen(new float[]{.08f,.48f,.38f,.59f},new float[]{.60f,.75f,.90f,.86f}),ref,null,A,.012f)==null,"two indistinguishable controls cannot authorize a guessed target");
  check(VisualLocator.find(W,H,screen(),ref,null,A,.012f)==null,"missing object stays absent");float[] uniform=new float[768];Arrays.fill(uniform,.7f);check(VisualLocator.find(W,H,screen(),uniform,null,A,.012f)==null,"uniform background is not a visual identity");check(VisualLocator.verify(W,H,(x,y)->0xffb3b3b3,uniform,null,A,.012f)==null,"an old expected coordinate cannot admit an uninformative template");
  float[] mask=new float[256];Arrays.fill(mask,1);for(int y=6;y<10;y++)for(int x=6;x<10;x++)mask[y*16+x]=0;
  RegionSampler.Pixels clean=screen(A),overlay=(x,y)->x>W*.27f&&x<W*.33f&&y>H*.162f&&y<H*.19f?0xffff4400:clean.get(x,y);
  check(VisualLocator.distance(ref,RegionSampler.sample(W,H,A,overlay),mask)<.006,"ignored tap overlay cannot become visual evidence");
  System.out.println("VisualLocatorTest: "+checks+" checks passed / "+((System.nanoTime()-started)/1000000)+"ms; synthetic distant, scaled, ambiguous and missing controls");
 }
}
