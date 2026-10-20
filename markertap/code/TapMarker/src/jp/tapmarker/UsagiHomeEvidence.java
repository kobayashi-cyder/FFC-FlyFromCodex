package jp.tapmarker;

/** Fixed title and fixed upgrade-label glyphs, outside the animated tap button. */
final class UsagiHomeEvidence {
 static final float[] HEADER={.66f,.0096f,.77f,.0396f};
 static final float[] LABEL=AdaptiveLayout.toVirtual(new float[]{.178f,.842f,.308f,.854f});
 // Measured from the independent heading and upgrade label in 6680.mp4.
 static final float[] F52A_LAYOUT={1,.965f,0,.044f};
 static float[] sample(int width,int height,float[] transform,RegionSampler.Pixels pixels){
  float[] header=AdaptiveLayout.map(HEADER,transform),label=AdaptiveLayout.map(LABEL,transform);
  if(!AdaptiveLayout.inside(header)||!AdaptiveLayout.inside(label))return null;
  float[] first=RegionSampler.sample(width,height,header,pixels),second=RegionSampler.sample(width,height,label,(x,y)->FarmHomeEvidence.white(pixels.get(x,y))?0xffffffff:0xff000000),result=new float[1536];
  System.arraycopy(first,0,result,0,768);System.arraycopy(second,0,result,768,768);return result;
 }
 private UsagiHomeEvidence(){}
}
