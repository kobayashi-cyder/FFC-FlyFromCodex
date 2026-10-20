package jp.tapmarker;
/** Locate verified Get text in a mission's visible button column using a sparse vertical line. */
final class MissionGetDetector {
 static final class Found {float[] raw,bounds;Found(float[] raw,float[] bounds){this.raw=raw;this.bounds=bounds;}}
 static boolean orange(int c){int r=c>>16&255,g=c>>8&255,b=c&255;return r>210&&g>70&&g<170&&b<110&&r-g>65;}
 static Found find(int width,int height,float[] column,float[] transform,RegionSampler.Pixels pixels,PhaseEngine.Watch watch){
  return find(width,height,column,transform,pixels,watch,CoinPreset.MISSION_TITLE,CoinPreset.GET_LABEL,.715f);
 }
 static Found find(int width,int height,float[] column,float[] transform,RegionSampler.Pixels pixels,PhaseEngine.Watch watch,float[] titleBounds,float[] labelBounds,float probeX){
  float[] title=AdaptiveLayout.map(titleBounds,transform);if(!AdaptiveLayout.inside(title))return null;
  float[] heading=RegionSampler.sample(width,height,title,pixels);
  if(PhaseEngine.blockDistance(java.util.Arrays.copyOfRange(watch.reference,0,768),heading)>watch.threshold)return null;
  float[] box=AdaptiveLayout.map(column,transform);int[] limit=RegionMath.bounds(box,width,height);int x=Math.min(width-1,Math.max(0,(int)((probeX*transform[0]+transform[2])*width))),start=-1,step=Math.max(1,Math.round(height*.0015f));
  for(int y=limit[1];y<=limit[3];y+=step){boolean match=y<limit[3]&&orange(pixels.get(x,y));if(match&&start<0)start=y;if(match||start<0)continue;
   float size=(y-start)/(float)height,center=(y+start)/(2f*height);start=-1;if(size<.022f*transform[1]||size>.060f*transform[1])continue;
   // Top-to-bottom redemption; each candidate still needs the actual white Get label.
   for(float dy:new float[]{0,-.003f,.003f,-.006f,.006f}){float[] label=AdaptiveLayout.map(labelBounds,transform);float shift=center-(label[1]+label[3])/2+dy*transform[1];label[1]+=shift;label[3]+=shift;if(!AdaptiveLayout.inside(label))continue;
    float[] raw=new float[1536],text=RegionSampler.sample(width,height,label,pixels);System.arraycopy(heading,0,raw,0,768);System.arraycopy(text,0,raw,768,768);
    if(watch.distance(raw)<=watch.threshold)return new Found(raw,label);
   }
  }
  return null;
 }
}
