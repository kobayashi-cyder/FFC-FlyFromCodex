package jp.tapmarker;

/** Returning from missions requires positively recognized Play labels, not a failed Get search. */
final class PoipoiMissionEvidence {
 static final String STATUS="poipoi-mission-status";
 static boolean button(int c){int r=c>>16&255,g=c>>8&255,b=c&255;return r>210&&g>70&&g<230&&b<115&&r-g>25;}
 static boolean noVisibleClaim(int w,int h,float[] t,RegionSampler.Pixels pixels,float[] play){
  if(play==null||play.length!=768)return false;
  int[] box=RegionMath.bounds(AdaptiveLayout.map(PoipoiPreset.GET_COLUMN,t),w,h);
  int start=-1,labels=0;
  for(int y=box[1];y<=box[3];y++){
   boolean visible=false;
   if(y<box[3])for(float probe:new float[]{.70f,.72f,.87f,.89f}){int x=Math.min(w-1,Math.max(0,(int)((probe*t[0]+t[2])*w)));visible|=button(pixels.get(x,y));}
   if(visible&&start<0)start=y;if(visible||start<0)continue;
   float size=(y-start)/(float)h,center=(start+y)/(2f*h);start=-1;
   if(size<.018f*t[1]||size>.075f*t[1])continue;
   int leftProbe=Math.min(w-1,Math.max(0,(int)((.60f*t[0]+t[2])*w)));
   if(button(pixels.get(leftProbe,Math.min(h-1,(int)(center*h)))))continue; // Wide progress bars are not buttons.
   boolean matched=false;
   for(float dy:new float[]{0,-.003f,.003f,-.006f,.006f}){
    float[] label=AdaptiveLayout.map(PoipoiPreset.GET_LABEL,t);float shift=center-(label[1]+label[3])/2+dy*t[1];label[1]+=shift;label[3]+=shift;
    if(AdaptiveLayout.inside(label)&&PhaseEngine.blockDistance(play,RegionSampler.sample(w,h,label,pixels))<=.012f){matched=true;break;}
   }
   if(!matched)return false;labels++;
  }
  return labels>=2;
 }
}
