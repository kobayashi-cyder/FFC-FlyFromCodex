package jp.tapmarker;
import java.util.*;

/** Only the two supplied ad families: separate ad attribution and install/store evidence. */
final class BoxAdHomeEvidence {
 static final float[][][] PAIRS={
  {BoxPreset.b(143,452,220,472),BoxPreset.b(133,729,225,747)},
  {BoxPreset.b(30,761,83,777),BoxPreset.b(231,777,325,793)},
  {BoxPreset.b(30,761,83,777),BoxPreset.b(231,777,325,793)}
 };
 final Map<String,float[]> refs;
 BoxAdHomeEvidence(Map<String,float[]> refs){this.refs=refs;}
 static int ink(int c,int family,int part){int r=c>>16&255,g=c>>8&255,b=c&255;if(family==0&&part==0)return Math.max(r,Math.max(g,b))<=180?0xffffffff:0xff000000;if(family>=1&&part==0)return Math.min(r,Math.min(g,b))>=130&&Math.max(r,Math.max(g,b))-Math.min(r,Math.min(g,b))<=45?0xffffffff:0xff000000;return AdHomeEvidence.white(c);}
 static float distance(float[] ref,float[] bounds,int w,int h,RegionSampler.Pixels pixels,int family,int part){
  float energy=PhaseEngine.blockDistance(ref,new float[768]);if(energy<.001f)return Float.POSITIVE_INFINITY;float best=Float.POSITIVE_INFINITY;
  for(float dx:new float[]{0,-.0015f,.0015f})for(float dy:new float[]{0,-.001f,.001f}){float[] b=bounds.clone();b[0]+=dx;b[2]+=dx;b[1]+=dy;b[3]+=dy;if(AdaptiveLayout.inside(b))best=Math.min(best,PhaseEngine.blockDistance(ref,RegionSampler.sample(w,h,b,(x,y)->ink(pixels.get(x,y),family,part)))/energy);}return best;
 }
 void sample(PhaseEngine e,Map<String,float[]> out,int w,int h,RegionSampler.Pixels pixels,boolean known){
  out.remove(AdHomeRecovery.EVIDENCE);if(known||!AdHomeRecovery.eligible(e))return;
  for(int i=0;i<PAIRS.length;i++){
   float[] a=refs.get("ad-"+i+"-a"),b=refs.get("ad-"+i+"-b");if(a==null||b==null)continue;
   if(distance(a,PAIRS[i][0],w,h,pixels,i,0)<=.45f&&distance(b,PAIRS[i][1],w,h,pixels,i,1)<=.45f){out.put(AdHomeRecovery.EVIDENCE,new float[]{1,w,h});return;}
  }
 }
}
