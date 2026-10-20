package jp.tapmarker;

/** Bounds and paths shared by single-screen, split-screen and virtual replay. */
public final class LearnedInput {
 public static float[] local(PhaseEngine.Watch w,PhaseEngine.Region region,LearnedLayout layout,int width,int height){
  if(w.action==PhaseEngine.CENTER){float[] point=w.touchPoint.length==2?w.touchPoint:new float[]{(region.bounds[0]+region.bounds[2])/2,(region.bounds[1]+region.bounds[3])/2};return layout.pointFor(w.regionId,point,width,height);}
  if(w.action==PhaseEngine.CURVE)return w.relativeCurve?layout.pathFor(w.regionId,w.curve,width,height):w.curve.clone();
  if(w.action==PhaseEngine.MARKERS)return layout.pathFor(w.regionId,w.markerPoints,width,height);
  return null;
 }
 public static float[] offset(float[] points,int left,int top){if(points==null)return null;float[] result=points.clone();for(int i=0;i<result.length;i+=2){result[i]+=left;result[i+1]+=top;}return result;}
 public static boolean inside(float[] points,int left,int top,int width,int height){if(points==null||points.length<2||points.length%2!=0)return false;for(int i=0;i<points.length;i+=2)if(!Float.isFinite(points[i])||!Float.isFinite(points[i+1])||points[i]<left||points[i]>=left+width||points[i+1]<top||points[i+1]>=top+height)return false;return true;}
}
