package jp.tapmarker;
/** Match the whole centered count, without searching for a zero inside a larger number. */
final class UsagiStageCompletion {
 static final float[] COUNT=AdaptiveLayout.toVirtual(new float[]{.425f,.155f,.612f,.189f});
 static boolean white(int c){int r=c>>16&255,g=c>>8&255,b=c&255;return Math.min(r,Math.min(g,b))>=222&&Math.max(r,Math.max(g,b))-Math.min(r,Math.min(g,b))<=35;}
 static float[] sample(int width,int height,float[] transform,RegionSampler.Pixels pixels){
  float[] bounds=AdaptiveLayout.map(COUNT,transform);if(!AdaptiveLayout.inside(bounds))return null;
  return RegionSampler.sample(width,height,bounds,(x,y)->white(pixels.get(x,y))?0xffffffff:0xff000000);
 }
 static boolean visible(int width,int height,float[] transform,RegionSampler.Pixels pixels,float[]... references){
  float[] count=sample(width,height,transform,pixels);
  if(count==null||references==null)return false;for(float[] reference:references)if(reference!=null&&reference.length==768&&PhaseEngine.blockDistance(reference,count)<=.006f)return true;return false;
 }
 private UsagiStageCompletion(){}
}
