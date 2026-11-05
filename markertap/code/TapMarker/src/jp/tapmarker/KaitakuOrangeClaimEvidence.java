package jp.tapmarker;

/**
 * Narrow, visual-only fallback for the Kurashiru daily-mission layout.
 * Finds a real orange claim pill immediately below a filled yellow progress bar.
 * A colored button alone, a yellow Play pill, or an advertisement never qualifies.
 * This class only proposes visible bounds; the runtime's fresh-frame, package,
 * foreground and gesture preflight remains authoritative.
 */
final class KaitakuOrangeClaimEvidence {
  private static int rgb(int w,int h,RegionSampler.Pixels pixels,float x,float y){
    return pixels.get(Math.max(0,Math.min(w-1,Math.round(x*(w-1)))),
                      Math.max(0,Math.min(h-1,Math.round(y*(h-1)))));
  }
  private static int r(int c){return (c>>>16)&255;}
  private static int g(int c){return (c>>>8)&255;}
  private static int b(int c){return c&255;}
  private static boolean white(int c){return r(c)>230&&g(c)>230&&b(c)>230;}
  private static boolean yellow(int c){return r(c)>190&&g(c)>140&&b(c)<90&&r(c)>=g(c);}
  private static boolean orange(int c){return r(c)>185&&g(c)>=75&&g(c)<=170&&b(c)<115&&r(c)-g(c)>60&&g(c)-b(c)>30;}
  private static boolean orangeSides(int w,int h,RegionSampler.Pixels pixels,int y){
    float fy=y/(float)(h-1);
    return orange(rgb(w,h,pixels,.710f,fy))&&orange(rgb(w,h,pixels,.880f,fy));
  }
  private static float[] candidate(int w,int h,RegionSampler.Pixels pixels,int start,int end){
    int height=end-start,cy=(start+end)/2;
    if(height<h*.018f||height>h*.065f)return null;
    float mid=cy/(float)(h-1);
    if(!white(rgb(w,h,pixels,.36f,mid))||!white(rgb(w,h,pixels,.53f,mid)))return null;
    // The immediately preceding bar must be FILLED, not a gray 0/1 task.
    int matches=0;
    for(int y=Math.max(0,cy-(int)(h*.10f));y<cy-(int)(h*.025f);y+=Math.max(2,h/400)){
      float fy=y/(float)(h-1);
      if(yellow(rgb(w,h,pixels,.12f,fy))&&yellow(rgb(w,h,pixels,.27f,fy))&&
         yellow(rgb(w,h,pixels,.83f,fy)))matches++;
      if(matches>=2)break;
    }
    if(matches<2)return null;
    // Small inset excludes the button's black frame, with a center on the live pill.
    return new float[]{.685f,Math.max(0,(start-4)/(float)h),.912f,Math.min(.93f,(end+5)/(float)h)};
  }
  static float[] find(int w,int h,RegionSampler.Pixels pixels){
    if(pixels==null||w<200||h<400)return null;
    // Independent page-layout anchors. Reject orange advertisements and game overlays.
    boolean legacy=KaitakuMissionVisual.legacyHeader(w,h,pixels);
    // The supplied recording has an OS/fullscreen pink mission header;
    // older cropped evidence used a beige header. Retain both.
    if(!legacy&&!KaitakuMissionVisual.modernHeader(w,h,pixels))return null;
    int first=(int)(h*.32f),last=(int)(h*.92f),step=Math.max(1,h/850),start=-1,end=-1;
    for(int y=first;y<=last;y+=step){
      if(orangeSides(w,h,pixels,y)){
        if(start<0)start=y;
        end=y;
      }else if(start>=0){
        float[] detected=candidate(w,h,pixels,start,end);
        if(detected!=null)return detected;
        start=end=-1;
      }
    }
    return start<0?null:candidate(w,h,pixels,start,end);
  }
  private KaitakuOrangeClaimEvidence(){}
}
