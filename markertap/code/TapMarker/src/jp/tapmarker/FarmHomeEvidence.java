package jp.tapmarker;
/** Two independent anchors; harvest letters stay the same during the rainbow animation. */
final class FarmHomeEvidence {
 static final float[] HEADER={.75f,.077f,.805f,.092f};
 static final float[] LABEL={.392f,.802f,.593f,.825f};
 static boolean white(int color){int red=(color>>16)&255,green=(color>>8)&255,blue=color&255;int min=Math.min(red,Math.min(green,blue)),max=Math.max(red,Math.max(green,blue));return min>=222&&max-min<=35;}
 static float[] sample(int width,int height,float[] transform,RegionSampler.Pixels pixels){
  float[] header=AdaptiveLayout.map(HEADER,transform),label=AdaptiveLayout.map(LABEL,transform);
  if(!AdaptiveLayout.inside(header)||!AdaptiveLayout.inside(label))return null;
  float[] first=RegionSampler.sample(width,height,header,pixels);
  float[] second=RegionSampler.sample(width,height,label,(x,y)->white(pixels.get(x,y))?0xffffffff:0xff000000);
  float[] result=new float[1536];System.arraycopy(first,0,result,0,768);System.arraycopy(second,0,result,768,768);return result;
 }
}
