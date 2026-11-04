package jp.tapmarker;

/** Supplemental, screenshot-grounded upgrade pod detection for the known Kaitaku layout.
 * A single red CM pill is never enough: the blue game background and framed
 * white pod must both exist. Main-screen evidence and a second fresh frame
 * are still required by KaitakuActionEvidence and UpgradeEngine.
 */
final class KaitakuControlVisual {
  private static int pixel(int w,int h,RegionSampler.Pixels p,float x,float y) {
    return p.get(Math.min(w-1,Math.max(0,Math.round(x*(w-1)))),
                 Math.min(h-1,Math.max(0,Math.round(y*(h-1)))));
  }
  private static int r(int c){return (c>>16)&255;}
  private static int g(int c){return (c>>8)&255;}
  private static int b(int c){return c&255;}
  private static boolean dark(int c){return r(c)<105&&g(c)<105&&b(c)<105;}
  private static boolean white(int c){return r(c)>222&&g(c)>222&&b(c)>222;}
  private static boolean sky(int c){return r(c)>70&&r(c)<180&&g(c)>175&&b(c)>210&&g(c)>r(c)+35;}
  static boolean visible(int w,int h,RegionSampler.Pixels p,boolean right) {
    if(p==null||w<200||h<400)return false;
    // Independent blank blue separators disqualify dialogs, darkened overlays,
    // mission lists, and full-screen ads.
    for(float x:new float[]{.37f,.42f,.63f})
      if(!sky(pixel(w,h,p,x,.83f)))return false;
    float x=right?.80f:.20f;
    return dark(pixel(w,h,p,x,.82f))&&white(pixel(w,h,p,x,.825f))&&
      white(pixel(w,h,p,x,.84f))&&dark(pixel(w,h,p,x,.865f));
  }
  private KaitakuControlVisual(){}
}
