package jp.tapmarker;

/** An embedded Google Play sheet has its own header below the screen corner.
 * Require both the heading and a measured X; never use the installation area. */
final class PlayStoreSheetEvidence {
 // Google Play header ink counts, 32x8 cells with nine samples in each cell.
 // Reference: the first card in supplied recording 6745. Other cards and 6746
 // are validation images. Values describe only the heading, not app content.
 static final int[] HEADER={
  0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,
  0,1,6,5,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1,3,3,0,2,0,0,0,0,0,0,0,
  0,5,1,1,1,0,0,0,0,0,0,0,0,0,0,3,0,0,0,0,3,5,5,2,6,0,0,0,0,0,0,0,
  5,2,0,3,1,1,3,3,0,3,3,1,1,3,2,3,1,3,2,0,3,3,2,3,6,2,3,1,1,1,1,0,
  6,1,0,6,8,3,1,5,6,4,3,3,7,0,4,3,5,2,5,0,3,8,6,1,6,2,4,7,4,3,2,0,
  2,4,0,0,5,3,0,4,8,3,0,4,7,0,3,3,6,5,1,0,3,3,0,0,7,7,4,6,6,6,0,0,
  0,5,7,7,1,4,7,5,1,7,8,2,6,6,6,2,2,7,5,0,4,2,0,0,7,6,6,6,4,5,0,0,
  0,0,0,0,0,0,0,0,0,0,0,0,4,3,3,0,0,0,0,0,0,0,0,0,0,0,0,0,5,2,0,0
 };
 static float headingDistance(int w,int h,float cy,RegionSampler.Pixels p,float dx,float dy){
  float x0=(.055f+dx)*w,x1=(.323f+dx)*w,y0=cy+(-.030f+dy)*w,y1=cy+(.026f+dy)*w;
  if(x0<0||x1>=w||y0<0||y1>=h)return 1;
  int difference=0,energy=0;
  for(int j=0;j<8;j++)for(int i=0;i<32;i++){
   int ink=0;
   for(int sy=0;sy<3;sy++)for(int sx=0;sx<3;sx++){
    int x=(int)(x0+(i+(sx+.5f)/3)*(x1-x0)/32),y=(int)(y0+(j+(sy+.5f)/3)*(y1-y0)/8);
    if(AdCloseDetector.darkGlyph(p.get(x,y)))ink++;
   }
   int ref=HEADER[j*32+i];difference+=Math.abs(ref-ink);energy+=ref+ink;
  }
  return energy==0?1:difference/(float)energy;
 }
 static float[] locate(int w,int h,RegionSampler.Pixels p){
  if(p==null||w<160||h<300)return null;
  float[] x=AdCloseDetector.find(w,h,new float[]{.86f,.14f,1,.45f},p);
  // Resampling may leave a grey gap between the small X's diagonal arms.
  // Only this sheet-specific path softens neutral glyph pixels; the ordinary
  // ad-corner detector and the independent heading check stay unchanged.
  if(x==null)x=AdCloseDetector.findPolarity(w,h,new float[]{.86f,.14f,1,.45f},(px,py)->{
   int c=p.get(px,py),r=c>>16&255,g=c>>8&255,b=c&255,max=Math.max(r,Math.max(g,b)),min=Math.min(r,Math.min(g,b));
   return max<=190&&max-min<=40?0xff000000:c;
  },false);
  if(x==null)return null;
  // A continuous light sheet header and the darker content behind its top.
  int y=(int)x[1],above=y-Math.max(3,(int)(w*.09f));
  if(above<0)return null;
  for(float column:new float[]{.38f,.55f,.72f,.83f})if(!FarmAdErrorEvidence.white(p.get((int)(w*column),y)))return null;
  if(FarmAdErrorEvidence.white(p.get(w/2,above))&&FarmAdErrorEvidence.white(p.get((int)(w*.8f),above)))return null;
  float score=1;
  for(float dx:new float[]{0,-.003f,.003f})for(float dy:new float[]{0,-.004f,.004f})
   score=Math.min(score,headingDistance(w,h,x[1],p,dx,dy));
  return score<=.32f?x:null;
 }
 private PlayStoreSheetEvidence(){}
}
