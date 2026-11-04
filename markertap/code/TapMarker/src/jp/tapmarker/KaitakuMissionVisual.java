package jp.tapmarker;

/**
 * Conservative mission PAGE recognition when Canvas text is not exposed to
 * accessibility and a stale "ad" phase keeps waiting after ad exit.
 * Never guesses which of the yellow "プレイ" buttons to press: actionable
 * rows still require their own named or learned visual recognition.
 */
final class KaitakuMissionVisual {
  private static int px(int w,int h,RegionSampler.Pixels p,float x,float y){
    return p.get(Math.max(0,Math.min(w-1,Math.round(x*(w-1)))),
                 Math.max(0,Math.min(h-1,Math.round(y*(h-1)))));
  }
  private static int r(int c){return c>>>16&255;}
  private static int g(int c){return c>>>8&255;}
  private static int b(int c){return c&255;}
  private static boolean gray(int c){return r(c)>=105&&r(c)<=165&&
      Math.abs(r(c)-g(c))<12&&Math.abs(g(c)-b(c))<12;}
  private static boolean white(int c){return r(c)>235&&g(c)>235&&b(c)>235;}
  private static boolean yellow(int c){return r(c)>213&&g(c)>120&&g(c)<215&&b(c)<80;}
  static boolean visible(int w,int h,RegionSampler.Pixels p){
    if(p==null||w<200||h<400)return false;
    int rows=0;
    // The lower three mission cards remain exposed below the floating panel
    // in screenshot 6969. Need at least TWO independently matching rows.
    float[][] candidates={{.50f,.55f},{.66f,.714f},{.83f,.88f}};
    for(float[] row:candidates){
      float progress=row[0],button=row[1];
      if(gray(px(w,h,p,.30f,progress))&&gray(px(w,h,p,.45f,progress))&&
         white(px(w,h,p,.50f,button))&&
         yellow(px(w,h,p,.71f,button-.002f))&&
         yellow(px(w,h,p,.87f,button-.002f)))rows++;
    }
    return rows>=2;
  }
  private KaitakuMissionVisual(){}
}
