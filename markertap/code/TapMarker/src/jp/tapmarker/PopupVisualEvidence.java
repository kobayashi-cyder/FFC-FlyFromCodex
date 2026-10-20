package jp.tapmarker;
import java.util.*;

/** Sparse controls observed in 6772; no full-screen search or unconfirmed coordinates. */
final class PopupVisualEvidence {
 static final float[][] FOOTER={{.03f,.978f,.43f,1},{.55f,.980f,.84f,1}};
 static final float[][] PROMO={{.125f,.224f,.83f,.267f},{.125f,.292f,.86f,.332f}};
 final String app;final float[] footer,promo;
 float lastDistance=Float.NaN;float[] lastBounds;
 PopupVisualEvidence(String app,Map<String,float[]> refs){this.app=app;footer=refs.get("popup6772-footer");promo=refs.get("popup6772-farm-promo");}
 float[] locate(int w,int h,RegionSampler.Pixels p){
  lastDistance=Float.NaN;lastBounds=null;
  if(w<160||h<300)return null;
  if((UsagiPreset.APP.equals(app)||CoinPreset.APP.equals(app))&&footer!=null&&footer.length==1536){
   for(float dy:new float[]{0,-.0015f,-.003f}){
    float[] t={1,1,0,dy};float[] raw=AdaptiveLayout.paired(w,h,FOOTER,t,p);
    float distance=PhaseEngine.blockDistance(footer,raw);if(distance>.009f)continue;
    int x=Math.round(w*.22f),y=h-Math.max(4,Math.round(w*.020f));
    // Choose the exposed upper part of the button, strictly inside the current image.
    int c=p.get(x,y);int lo=Math.min(c>>16&255,Math.min(c>>8&255,c&255)),hi=Math.max(c>>16&255,Math.max(c>>8&255,c&255));
    if(lo>=105&&hi<=205&&hi-lo<22&&GamePopupEvidence.white(p.get(Math.round(w*.70f),y))){lastDistance=distance;lastBounds=new float[]{w*.03f,h*(.978f+dy),w*.43f,h};return new float[]{x,y};}
   }
  }
  if(FarmPreset.APP.equals(app)&&promo!=null&&promo.length==1536){
   for(float sy:new float[]{1,.96f,1.04f})for(float dy:new float[]{0,-.005f,.005f,-.01f,.01f,-.015f,.015f,-.02f,.02f}){
    float[] t={1,sy,0,(1-sy)/2+dy};float distance=PhaseEngine.blockDistance(promo,AdaptiveLayout.paired(w,h,PROMO,t,p));if(distance>.012f)continue;
    float[] cross=AdCloseDetector.find(w,h,new float[]{.80f,.13f,.99f,.34f},p);
    if(cross!=null){lastDistance=distance;lastBounds=new float[]{w*.125f,h*(PROMO[0][1]*sy+t[3]),w*.86f,h*(PROMO[1][3]*sy+t[3])};return cross;}
   }
  }
  return null;
 }
}
