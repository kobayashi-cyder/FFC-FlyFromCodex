package jp.tapmarker;

/**
 * Pixel evidence for the Kaitaku "タップ2倍" rewarded-ad confirmation.
 * Anchors are sampled from the 2026-11-05 684x1536 device screenshot.
 *
 * The game header is often obscured by MarkerTap's own floating panel, so
 * only unobscured strips of the modal body and button are inspected. Unlike
 * "コインアップチャンス" (a different dialog), the cream header continues
 * down to y=.39. A candidate is not a tap by itself: the existing phase
 * engine still requires fresh-screen quorum and input preflight.
 */
final class KaitakuTapBoostModalVisual {
  private static int px(int w,int h,RegionSampler.Pixels p,float x,float y) {
    return p.get(Math.max(0,Math.min(w-1,Math.round(x*(w-1)))),
                 Math.max(0,Math.min(h-1,Math.round(y*(h-1)))));
  }
  private static int r(int c){return c>>>16&255;}
  private static int g(int c){return c>>>8&255;}
  private static int b(int c){return c&255;}
  private static boolean cream(int c){return r(c)>238&&g(c)>222&&g(c)<252&&b(c)>150&&b(c)<224;}
  private static boolean white(int c){return r(c)>242&&g(c)>242&&b(c)>242;}
  private static boolean yellow(int c){return r(c)>215&&g(c)>125&&g(c)<210&&b(c)<85;}
  private static boolean dark(int c){return r(c)<100&&g(c)<100&&b(c)<100;}
  static float[] find(int w,int h,RegionSampler.Pixels p){
    if(p==null||w<200||h<400)return null;
    if(!cream(px(w,h,p,.10f,.34f))||!cream(px(w,h,p,.90f,.34f))||
       !cream(px(w,h,p,.10f,.39f))||!cream(px(w,h,p,.90f,.39f))||
       !white(px(w,h,p,.10f,.45f))||!white(px(w,h,p,.90f,.45f))||
       !white(px(w,h,p,.10f,.61f))||!white(px(w,h,p,.90f,.61f))||
       !yellow(px(w,h,p,.21f,.67f))||!yellow(px(w,h,p,.78f,.67f))||
       !dark(px(w,h,p,.20f,.72f))||!dark(px(w,h,p,.80f,.72f))||
       !white(px(w,h,p,.10f,.75f))||!white(px(w,h,p,.90f,.75f)))
      return null;
    // The interior of the actual "使用する" CTA. Never the "キャンセル" text.
    return new float[]{.165f,.648f,.835f,.710f};
  }
  private KaitakuTapBoostModalVisual(){}
}
