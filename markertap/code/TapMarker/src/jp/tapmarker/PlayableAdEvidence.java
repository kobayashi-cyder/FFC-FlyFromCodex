package jp.tapmarker;
import java.util.*;

/** Positive ad identity only: never presses the playable canvas, joystick or install. */
final class PlayableAdEvidence {
 static final String KEY=":verified-playable-ad";
 final float[] logo,hint,logoReference,hintReference;
 PlayableAdEvidence(Map<String,float[]> refs){
  String p="playable-last-asylum:";logo=refs.get(p+"logo:bounds");hint=refs.get(p+"hint:bounds");
  logoReference=refs.get(p+"logo:reference");hintReference=refs.get(p+"hint:reference");
 }
 void sample(PhaseEngine e,Map<String,float[]> out,int w,int h,RegionSampler.Pixels pixels,int fullWidth,int fullHeight){
  out.remove(KEY);
  if(e.windowRelative||logo==null||hint==null||logoReference==null||hintReference==null||AdExitPolicy.gameVisible(e,out))return;
  for(float[] t:new float[][]{new float[]{1,1,0,0},new float[]{1,AdaptiveLayout.NORMAL_Y,0,AdaptiveLayout.NORMAL_TOP}}){
   VisualLocator.Match a=VisualLocator.verify(w,h,pixels,logoReference,null,AdaptiveLayout.map(logo,t),.008f);
   if(a==null)a=VisualLocator.nearby(w,h,pixels,logoReference,null,AdaptiveLayout.map(logo,t),.008f);
   if(a==null)continue;
   // The separate instruction lettering is matched as white ink, so the
   // moving playable background cannot reset the identity observation.
   RegionSampler.Pixels ink=(x,y)->whiteInk(pixels.get(x,y));
   VisualLocator.Match b=VisualLocator.verify(w,h,ink,hintReference,null,AdaptiveLayout.map(hint,t),.006f);
   if(b==null)b=VisualLocator.nearby(w,h,ink,hintReference,null,AdaptiveLayout.map(hint,t),.006f);
   if(b!=null){out.put(KEY,new float[]{1,fullWidth,fullHeight});return;}
  }
 }
 static int whiteInk(int c){int r=c>>16&255,g=c>>8&255,b=c&255;return Math.min(r,Math.min(g,b))>=215&&Math.max(r,Math.max(g,b))-Math.min(r,Math.min(g,b))<=45?0xffffffff:0xff000000;}
 static boolean visible(Map<String,float[]> samples,int w,int h){float[] a=samples.get(KEY);return a!=null&&a.length==3&&a[0]==1&&a[1]==w&&a[2]==h;}
}
