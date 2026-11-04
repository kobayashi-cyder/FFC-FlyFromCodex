package jp.tapmarker;

/**
 * Supplemental visual evidence for the Kaitaku efficiency/auto-upgrade pods.
 *
 * The game board expands as stages advance: the former prerequisite that
 * three center-bottom pixels are blue sky rejected real, valid level frames.
 * Use three independent game-UI anchors plus the exact pod's white center
 * and dark bottom frame. This is only a candidate; the runtime retains its
 * fresh-frame, foreground and two-frame action gates.
 */
final class KaitakuControlVisual {
  private static int pixel(int w,int h,RegionSampler.Pixels p,float x,float y) {
    return p.get(Math.min(w-1,Math.max(0,Math.round(x*(w-1)))),
                 Math.min(h-1,Math.max(0,Math.round(y*(h-1)))));
  }
  private static int r(int c){return (c>>16)&255;}
  private static int g(int c){return (c>>8)&255;}
  private static int b(int c){return c&255;}
  private static boolean dark(int c){return r(c)<95&&g(c)<95&&b(c)<95;}
  private static boolean white(int c){return r(c)>222&&g(c)>222&&b(c)>222;}
  private static boolean gold(int c){return r(c)>220&&g(c)>175&&g(c)<225&&b(c)<70;}

  static boolean visible(int w,int h,RegionSampler.Pixels p,boolean right) {
    if(p==null||w<200||h<400)return false;
    // Anchors: white points HUD, golden central "開拓する", white TIPS panel.
    // Mission lists, ads and darkened modal overlays do not pass all three.
    if(!white(pixel(w,h,p,.20f,.10f))||
       !white(pixel(w,h,p,.30f,.10f))||
       !gold(pixel(w,h,p,.50f,.73f))||
       !gold(pixel(w,h,p,.50f,.75f))||
       !white(pixel(w,h,p,.50f,.90f)))return false;
    float x=right?.80f:.20f;
    // The top edge varies with the animated counter; the center and bottom
    // outline stay stable when the stage floor changes from sky to terrain.
    return white(pixel(w,h,p,x,.825f))&&
           white(pixel(w,h,p,x,.840f))&&
           dark(pixel(w,h,p,x,.865f));
  }
  private KaitakuControlVisual(){}
}
