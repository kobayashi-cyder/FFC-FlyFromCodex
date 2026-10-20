package jp.tapmarker;
import java.util.*;

/** The supplied ad's feedback symbol AND Advertising label. Neither button nor countdown is tapped. */
final class AdHomeEvidence {
 static final float[][] HEADERS={
  {48/1080f,230/2424f,112/1080f,298/2424f},
  {48/1080f,230/2424f,112/1080f,298/2424f}
 };
 static final float[][] LABELS={
  {46/1080f,2348/2424f,105/1080f,2379/2424f},
  {16/1080f,2023/2424f,74/1080f,2057/2424f}
 };
 final List<float[]> headers=new ArrayList<>(),labels=new ArrayList<>();
 AdHomeEvidence(Map<String,float[]> refs){
  for(int i=0;i<HEADERS.length;i++){
   float[] a=refs.get("coin-ad-home-"+i+"-header"),b=refs.get("coin-ad-home-"+i+"-label");
   headers.add(a!=null&&a.length==768?a.clone():null);labels.add(b!=null&&b.length==768?b.clone():null);
  }
 }
 static int white(int c){int r=c>>16&255,g=c>>8&255,b=c&255;return Math.min(r,Math.min(g,b))>=230&&Math.max(r,Math.max(g,b))-Math.min(r,Math.min(g,b))<=35?0xffffffff:0xff000000;}
 static float distance(float[] reference,float[] region,int w,int h,RegionSampler.Pixels pixels){
  float best=Float.POSITIVE_INFINITY;
  for(float dx:new float[]{0,-.0015f,.0015f})for(float dy:new float[]{0,-.001f,.001f}){
   float[] box=region.clone();box[0]+=dx;box[2]+=dx;box[1]+=dy;box[3]+=dy;
   if(AdaptiveLayout.inside(box))best=Math.min(best,PhaseEngine.blockDistance(reference,RegionSampler.sample(w,h,box,(x,y)->white(pixels.get(x,y)))));
  }
  return best;
 }
 void sample(PhaseEngine e,Map<String,float[]> samples,int w,int h,RegionSampler.Pixels pixels,boolean gameKnown){
  samples.remove(AdHomeRecovery.EVIDENCE);
  if(gameKnown||!AdHomeRecovery.eligible(e))return;
  for(int i=0;i<headers.size();i++)if(headers.get(i)!=null&&labels.get(i)!=null&&distance(headers.get(i),HEADERS[i],w,h,pixels)<=.020f&&distance(labels.get(i),LABELS[i],w,h,pixels)<=.028f){samples.put(AdHomeRecovery.EVIDENCE,new float[]{1,w,h});return;}
 }
}
