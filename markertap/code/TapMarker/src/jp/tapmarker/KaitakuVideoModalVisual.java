package jp.tapmarker;

/** Narrow visual fallback for the user's "コインアップチャンス!!" modal.
 * Returns ONLY its non-advertising "このままはじめる" opt-out region.
 * Independent cream header, white card, yellow ad CTA and dark separator
 * are all required. A normal reward popup or mission page does not qualify.
 */
final class KaitakuVideoModalVisual {
  private static int pixel(int w,int h,RegionSampler.Pixels p,float x,float y){
    return p.get(Math.min(w-1,Math.max(0,Math.round(x*(w-1)))),
                 Math.min(h-1,Math.max(0,Math.round(y*(h-1)))));
  }
  private static int r(int c){return c>>>16&255;}
  private static int g(int c){return c>>>8&255;}
  private static int b(int c){return c&255;}
  private static boolean cream(int c){return r(c)>238&&g(c)>225&&g(c)<251&&b(c)>155&&b(c)<219;}
  private static boolean white(int c){return r(c)>242&&g(c)>242&&b(c)>242;}
  private static boolean yellow(int c){return r(c)>219&&g(c)>135&&g(c)<205&&b(c)<70;}
  private static boolean dark(int c){return r(c)<95&&g(c)<95&&b(c)<95;}
  static float[] find(int w,int h,RegionSampler.Pixels p){
    if(p==null||w<200||h<400)return null;
    if(!cream(pixel(w,h,p,.12f,.30f))||!cream(pixel(w,h,p,.85f,.30f))||
       !white(pixel(w,h,p,.12f,.42f))||!white(pixel(w,h,p,.85f,.42f))||
       !white(pixel(w,h,p,.14f,.55f))||!white(pixel(w,h,p,.85f,.55f))||
       !yellow(pixel(w,h,p,.25f,.65f))||!yellow(pixel(w,h,p,.75f,.65f))||
       !dark(pixel(w,h,p,.20f,.715f))||!dark(pixel(w,h,p,.80f,.715f))||
       !white(pixel(w,h,p,.10f,.74f))||!white(pixel(w,h,p,.50f,.74f))||
       !dark(pixel(w,h,p,.05f,.42f)))return null;
    // The opt-out line lies below the large yellow ad CTA.
    return new float[]{.245f,.700f,.755f,.730f};
  }
  private KaitakuVideoModalVisual(){}
}
