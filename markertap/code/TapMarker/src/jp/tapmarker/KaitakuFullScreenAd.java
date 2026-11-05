package jp.tapmarker;

/** Full-screen interstitial with a white creative strip and a wide blue CTA.
 * The countdown and the close mark are optional. The CTA is never tapped.
 */
final class KaitakuFullScreenAd {
  static final String KEY=":kaitaku-full-screen-ad";

  static boolean visible(int w,int h,RegionSampler.Pixels pixels){
    if(w<144||h<240||w>=h)return false;
    if(!dark(at(w,h,pixels,.08f,.20f))||!dark(at(w,h,pixels,.92f,.20f))||
       !dark(at(w,h,pixels,.08f,.95f))||!dark(at(w,h,pixels,.92f,.95f)))return false;
    // Same CTA in both the short banner and the taller end card.
    if(!blue(at(w,h,pixels,.17f,.872f))||!blue(at(w,h,pixels,.83f,.872f))||
       !blue(at(w,h,pixels,.17f,.901f))||!blue(at(w,h,pixels,.83f,.901f)))return false;
    return creative(w,h,pixels,.383f,.425f,.581f,.63f)||
           creative(w,h,pixels,.258f,.323f,.672f,.688f);
  }
  private static boolean creative(int w,int h,RegionSampler.Pixels p,float header,
                                  float art,float footer,float gap){
    return white(at(w,h,p,.12f,header))&&white(at(w,h,p,.83f,header))&&
      cyan(at(w,h,p,.045f,art))&&cyan(at(w,h,p,.95f,art))&&
      white(at(w,h,p,.13f,footer))&&white(at(w,h,p,.87f,footer))&&
      dark(at(w,h,p,.90f,gap));
  }
  private static int at(int w,int h,RegionSampler.Pixels p,float x,float y){
    return p.get(Math.round(x*(w-1)),Math.round(y*(h-1)));
  }
  private static int r(int c){return c>>>16&255;}
  private static int g(int c){return c>>>8&255;}
  private static int b(int c){return c&255;}
  private static boolean dark(int c){return r(c)<30&&g(c)<30&&b(c)<30;}
  private static boolean white(int c){return r(c)>224&&g(c)>224&&b(c)>224;}
  private static boolean blue(int c){
    return r(c)>=45&&r(c)<=105&&g(c)>=75&&g(c)<=142&&b(c)>=155&&b(c)-g(c)>=55;
  }
  private static boolean cyan(int c){return g(c)>=165&&b(c)>=184&&g(c)-r(c)>=55;}
  private KaitakuFullScreenAd(){}
}
