package jp.tapmarker;
import java.util.*;

/** Ad sequence counter and Meta attribution must both be present, never an install-button tap. */
final class PoipoiAdHomeEvidence {
 static final float[][][] PAIRS={
  {PoipoiPreset.b(27,751,86,771),PoipoiPreset.b(32,779,106,794)},
  {PoipoiPreset.b(27,751,86,771),PoipoiPreset.b(32,779,106,794)},
  {PoipoiPreset.b(27,751,86,771),PoipoiPreset.b(32,779,106,794)}
 };
 final Map<String,float[]> refs;
 PoipoiAdHomeEvidence(Map<String,float[]> refs){this.refs=refs;}
 static int ink(int c){int r=c>>16&255,g=c>>8&255,b=c&255;return Math.min(r,Math.min(g,b))>=180&&Math.max(r,Math.max(g,b))-Math.min(r,Math.min(g,b))<=45?0xffffffff:0xff000000;}
 static float distance(float[] ref,float[] bounds,int w,int h,RegionSampler.Pixels pixels){float energy=PhaseEngine.blockDistance(ref,new float[768]);if(energy<.001f)return Float.POSITIVE_INFINITY;float best=Float.POSITIVE_INFINITY;for(float dx:new float[]{0,-.0015f,.0015f})for(float dy:new float[]{0,-.001f,.001f}){float[] b=bounds.clone();b[0]+=dx;b[2]+=dx;b[1]+=dy;b[3]+=dy;if(AdaptiveLayout.inside(b))best=Math.min(best,PhaseEngine.blockDistance(ref,RegionSampler.sample(w,h,b,(x,y)->ink(pixels.get(x,y))))/energy);}return best;}
 void sample(PhaseEngine e,Map<String,float[]> out,int w,int h,RegionSampler.Pixels pixels,boolean known){out.remove(AdHomeRecovery.EVIDENCE);if(known||!AdHomeRecovery.eligible(e))return;for(int i=0;i<PAIRS.length;i++){float[] a=refs.get("ad-"+i+"-a"),b=refs.get("ad-"+i+"-b");if(a!=null&&b!=null&&distance(a,PAIRS[i][0],w,h,pixels)<=.45f&&distance(b,PAIRS[i][1],w,h,pixels)<=.45f){out.put(AdHomeRecovery.EVIDENCE,new float[]{1,w,h});return;}}}
}
