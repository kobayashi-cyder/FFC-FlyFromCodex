package jp.tapmarker;

/**
 * Screenshot-grounded fallback for the user's "コインアップチャンス!!" modal.
 * The November 5 recording places the cream header at y=.285-.35,
 * the golden rewarded-ad button at y=.66-.73, and the separate non-ad
 * "このままはじめる" opt-out at y=.75-.79.
 *
 * The previous probes at y=.30 and y=.65 hit black title glyphs and white
 * card background. They made the detector reject the actual recorded modal.
 * Only the opt-out is returned: never the paid/ad CTA. Caller still requires
 * fresh app-screen evidence and independently authorized gesture delivery.
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

  private static boolean pink(int c){return r(c)>200&&g(c)>155&&g(c)<220&&b(c)>185&&b(c)>g(c)+10;}
  // The 98s Nov 5 recording has a smaller, PINK-headed coin dialog.
  // The previous cream-header detector must remain for earlier layouts.
  private static float[] recordedCoinDialog(int w,int h,RegionSampler.Pixels p){
    if(!pink(pixel(w,h,p,.17f,.28f))||!pink(pixel(w,h,p,.83f,.28f))||
       !white(pixel(w,h,p,.17f,.40f))||!white(pixel(w,h,p,.83f,.40f))||
       !white(pixel(w,h,p,.17f,.52f))||!white(pixel(w,h,p,.83f,.52f))||
       !yellow(pixel(w,h,p,.23f,.57f))||!yellow(pixel(w,h,p,.77f,.57f))||
       !dark(pixel(w,h,p,.30f,.61f))||!dark(pixel(w,h,p,.70f,.61f))||
       !white(pixel(w,h,p,.17f,.63f))||!white(pixel(w,h,p,.83f,.63f)))return null;
    // Below the rewarded-ad CTA, on the separate "このままはじめる" line.
    return new float[]{.29f,.620f,.71f,.652f};
  }
  private static float[] recordedCreamDialog(int w,int h,RegionSampler.Pixels p){
    if(!cream(pixel(w,h,p,.17f,.312f))||!cream(pixel(w,h,p,.83f,.312f))||
       !white(pixel(w,h,p,.17f,.375f))||!white(pixel(w,h,p,.83f,.375f))||
       !white(pixel(w,h,p,.17f,.43f))||!white(pixel(w,h,p,.83f,.43f))||
       !white(pixel(w,h,p,.17f,.58f))||!white(pixel(w,h,p,.83f,.58f))||
       !yellow(pixel(w,h,p,.23f,.658f))||!yellow(pixel(w,h,p,.77f,.658f))||
       !dark(pixel(w,h,p,.29f,.715f))||!dark(pixel(w,h,p,.71f,.715f))||
       !white(pixel(w,h,p,.20f,.746f))||!white(pixel(w,h,p,.80f,.746f)))return null;
    return new float[]{.26f,.727f,.74f,.759f};
  }
  static float[] find(int w,int h,RegionSampler.Pixels p){
    if(p==null||w<200||h<400)return null;
    float[] recorded=recordedCoinDialog(w,h,p);if(recorded!=null)return recorded;
    recorded=recordedCreamDialog(w,h,p);if(recorded!=null)return recorded;
    // Probe undecorated strips, not text glyphs. Independent horizontal
    // strips reject mission cards, ordinary game frames and full-screen ads.
    if(!cream(pixel(w,h,p,.10f,.29f))||!cream(pixel(w,h,p,.90f,.29f))||
       !white(pixel(w,h,p,.12f,.40f))||!white(pixel(w,h,p,.88f,.40f))||
       !white(pixel(w,h,p,.10f,.60f))||!white(pixel(w,h,p,.90f,.60f))||
       !yellow(pixel(w,h,p,.22f,.67f))||!yellow(pixel(w,h,p,.78f,.67f))||
       !dark(pixel(w,h,p,.22f,.73f))||!dark(pixel(w,h,p,.78f,.73f))||
       !white(pixel(w,h,p,.10f,.77f))||!white(pixel(w,h,p,.90f,.77f)))
      return null;
    return new float[]{.245f,.750f,.755f,.790f};
  }
  private KaitakuVideoModalVisual(){}
}
