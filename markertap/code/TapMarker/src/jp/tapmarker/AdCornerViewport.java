package jp.tapmarker;

/** Locate only leading black letterbox space; game landmarks keep their own viewport. */
final class AdCornerViewport {
 static int top(int w,int h,RegionSampler.Pixels pixels){
  int limit=(int)(h*.24f),top=0;
  while(top<limit&&darkRow(w,top,pixels))top++;
  return top>=limit?0:top;
 }
 static boolean darkRow(int width,int y,RegionSampler.Pixels pixels){
  int dark=0;
  for(float x:new float[]{.02f,.12f,.24f,.36f,.50f,.64f,.76f,.88f,.98f}){
   int c=pixels.get(Math.min(width-1,(int)(width*x)),y);
   if(Math.max(c>>16&255,Math.max(c>>8&255,c&255))<=24)dark++;
  }
  return dark>=8;
 }
 static float[] band(float[] region,int w,int h,RegionSampler.Pixels pixels){
  float inset=top(w,h,pixels)/(float)h;
  return new float[]{region[0],region[1],region[2],Math.max(region[3],inset+(1-inset)*region[3])};
 }
 private AdCornerViewport(){}
}
