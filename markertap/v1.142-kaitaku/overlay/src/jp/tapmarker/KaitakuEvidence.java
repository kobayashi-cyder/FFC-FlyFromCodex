package jp.tapmarker;

import java.util.*;

/** Sparse, paired control evidence extracted from the user's recording. */
final class KaitakuEvidence {
 static final float[] LEFT={.285f,.70f,.385f,.79f},RIGHT={.615f,.70f,.715f,.79f};
 static final float[] HEADING={.17f,.603f,.83f,.627f},CONTROL={.28f,.736f,.45f,.757f};
 static final float HOME_TOLERANCE=.016f,CONTINUE_TOLERANCE=.004f;
 static final class Result {final boolean home,proceed;final float[] transform;Result(boolean h,boolean c,float[] t){home=h;proceed=c;transform=t;}}
 final Map<String,float[]> refs;
 KaitakuEvidence(Map<String,float[]> refs){this.refs=refs;for(String key:new String[]{"home-left","home-right","continue-title","continue-button"})if(refs.get(key)==null||refs.get(key).length!=768)throw new IllegalArgumentException("開拓うさぎの見本がありません："+key);}
 static float[] map(float[] b,float[] t){return new float[]{b[0]*t[0]+t[2],b[1]*t[1]+t[3],b[2]*t[0]+t[2],b[3]*t[1]+t[3]};}
 static boolean inside(float[] b){return b[0]>=0&&b[1]>=0&&b[2]<=1&&b[3]<=1&&b[2]>b[0]&&b[3]>b[1];}
 float distance(String key,float[] b,float[] t,int w,int h,RegionSampler.Pixels p){float[] r=map(b,t);return inside(r)?PhaseEngine.blockDistance(refs.get(key),RegionSampler.sample(w,h,r,p)):Float.POSITIVE_INFINITY;}
 float score(boolean home,float[] t,int w,int h,RegionSampler.Pixels p){return home?Math.max(distance("home-left",LEFT,t,w,h,p),distance("home-right",RIGHT,t,w,h,p)):Math.max(distance("continue-title",HEADING,t,w,h,p),distance("continue-button",CONTROL,t,w,h,p));}
 Result read(int w,int h,RegionSampler.Pixels p,float[] nativeTransform){
  if(w<144||h<240)return new Result(false,false,new float[]{1,1,0,0});
  List<float[]> layouts=new ArrayList<>();layouts.add(new float[]{1,1,0,0});if(nativeTransform!=null)layouts.add(nativeTransform);
  // Explicit inset candidates; the visible control evidence, rather than a
  // guessed screen coordinate, authorizes input. No full-screen search.
  for(float top:new float[]{0,.025f,.05f,.075f})for(float bottom:new float[]{0,.025f,.05f})layouts.add(new float[]{1,1-top-bottom,0,top});
  float bestH=Float.POSITIVE_INFINITY,bestC=Float.POSITIVE_INFINITY;float[] ht=layouts.get(0),ct=ht;
  for(float[] t:layouts){float d=score(true,t,w,h,p);if(d<bestH){bestH=d;ht=t;}float c=score(false,t,w,h,p);if(c<bestC){bestC=c;ct=t;}}
  boolean proceed=bestC<=CONTINUE_TOLERANCE;
  return new Result(bestH<=HOME_TOLERANCE&&!proceed,proceed,(proceed?ct:ht).clone());
 }
 private KaitakuEvidence(){refs=null;}
}
