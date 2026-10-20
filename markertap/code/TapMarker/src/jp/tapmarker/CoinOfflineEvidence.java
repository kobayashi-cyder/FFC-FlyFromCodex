package jp.tapmarker;
import java.util.*;

/** Amount-independent offline title and ordinary Acquire, measured in the current frame. */
final class CoinOfflineEvidence {
 static final float[][] LABELS={{76/268f,156/602f,195/268f,201/602f},{117/268f,455/602f,151/268f,478/602f}};
 final float[] reference;
 CoinOfflineEvidence(Map<String,float[]> refs){reference=refs.get("popup6774-coin-offline");}
 static boolean yellow(int c){int r=c>>16&255,g=c>>8&255,b=c&255;return r>205&&g>155&&b<190&&r-b>45&&g-b>40&&r>=g-5;}
 float[] locate(int w,int h,RegionSampler.Pixels pixels){
  if(reference==null||reference.length!=1536||w<160||h<300)return null;
  int a=(int)(w*.16f),z=(int)(w*.84f);
  for(int y=(int)(h*.12f);y<h*.55f;y++){
   if(!yellow(pixels.get(a,y))||!yellow(pixels.get(z,y)))continue;
   int top=y;while(y<h*.70f&&yellow(pixels.get(a,y))&&yellow(pixels.get(z,y)))y++;
   int bottom=y;if(bottom-top<w*.12f||bottom-top>w*.36f)continue;
   int left=a,right=z,mid=top+Math.max(2,(bottom-top)/12);
   while(left>0&&yellow(pixels.get(left-1,mid)))left--;
   while(right<w-1&&yellow(pixels.get(right+1,mid)))right++;
   right++;int span=right-left;
   if(span<w*.55f||span>w*.97f)continue;
   int body=bottom+Math.round(span*.45f);
   if(body>=h||!GamePopupEvidence.white(pixels.get(left+span/8,body))||!GamePopupEvidence.white(pixels.get(right-1-span/8,body)))continue;
   // The measured header fixes the finite local transform. Neither the reward
   // amount nor the yellow CM multiplier is an input target or text reference.
   for(float change:new float[]{0,-.0075f,.0075f,-.015f,.015f})for(int dy:new int[]{0,-1,1}){
    float scale=span/211f*(1+change),cx=(left+right)/2f;
    float[] t={268*scale/w,602*scale/h,(cx-133.5f*scale)/w,(top-139*scale+dy*scale)/h};
    float[] raw=AdaptiveLayout.paired(w,h,LABELS,t,pixels);
    if(raw==null||PhaseEngine.blockDistance(reference,raw)>.012f)continue;
    return RegionMath.center(AdaptiveLayout.map(LABELS[1],t),w,h);
   }
  }
  return null;
 }
}
