package jp.tapmarker;

import java.util.Map;

/**
 * Conservative, optical recognition of the in-game "広告を表示できませんでした" dialog.
 * The dialog is game-owned, not an advertising network close button.
 * No OCR, arbitrary text guessing or tapping based only on old coordinates.
 */
final class FarmAdErrorEvidence {
 static final String KEY = "farm-ad-unavailable-confirmed";
 static final String POINT = KEY + ":point";

 static boolean orange(int c) {
  int r=(c>>>16)&255,g=(c>>>8)&255,b=c&255;
  return r>=228 && g>=106 && g<=180 && b<=78 && r>=g+60;
 }
 static boolean white(int c) {
  int r=(c>>>16)&255,g=(c>>>8)&255,b=c&255;
  return r>=237&&g>=237&&b>=237 && Math.max(r,Math.max(g,b))-Math.min(r,Math.min(g,b))<22;
 }
 static boolean dark(int c) {
  int r=(c>>>16)&255,g=(c>>>8)&255,b=c&255;
  return r<100&&g<100&&b<100;
 }
 static int at(RegionSampler.Pixels p,int w,int h,double x,double y) {
  return p.get(Math.max(0,Math.min(w-1,(int)(x*w))),Math.max(0,Math.min(h-1,(int)(y*h))));
 }
 /** Return the visible dialog's OK centre in this frame's own pixel coordinates. */
 static float[] locate(int w,int h,RegionSampler.Pixels pixels) {
  if(pixels==null||w<160||h<300)return null;
  float[] measured=outlinedDialog(w,h,pixels);
  if(measured!=null)return measured;
  // The vertical position may vary with device aspect ratio and in-game insets.
  for(double center=.33;center<=.52;center+=.008)for(double size:new double[]{.88,1.0,1.12}){
   double white1=center+.085*size,white2=center+.185*size,button=center+.260*size;
   if(button+.024>=.87)continue;
   // Continuous orange title bar with the large white error body underneath.
   if(!orange(at(pixels,w,h,.18,center))||!orange(at(pixels,w,h,.82,center))||
      !orange(at(pixels,w,h,.33,center+.020*size))||!orange(at(pixels,w,h,.78,center+.020*size)))continue;
   if(!white(at(pixels,w,h,.18,white1))||!white(at(pixels,w,h,.82,white1))||
      !white(at(pixels,w,h,.23,white2))||!white(at(pixels,w,h,.73,white2)))continue;
   // A coin-reward popup has the SAME orange/white panel but a large dark
   // "+100" graphic. The ad-error dialog has only a sparse small message.
   int ink=0,checks=0;
   for(int yi=0;yi<13;yi++)for(int xi=0;xi<20;xi++){
    int color=at(pixels,w,h,.245+xi*.026,center+(.090+yi*.008)*size);
    if(dark(color))ink++;checks++;
   }
   if(ink>checks*.12f)continue;
   // Pill-shaped OK is bordered in charcoal, with a light face. Distinguish it
   // from ordinary harvest UI and from orange reward/pop-up buttons.
   if(!dark(at(pixels,w,h,.40,button))||!dark(at(pixels,w,h,.60,button))||
      !white(at(pixels,w,h,.40,button-.018*size))||!white(at(pixels,w,h,.60,button-.018*size)))continue;
   double py=button-.016*size;
   return new float[]{w*.5f,(float)(h*py)};
  }
  return null;
 }
 /** Measure the title/body/button independently. A one-pixel border cannot be
  * found reliably by stepping through a fixed title-to-button distance. */
 static float[] outlinedDialog(int w,int h,RegionSampler.Pixels p){
  for(int y=(int)(h*.22f);y<h*.60f;y++){
   if(!orange(at(p,w,h,.18,y/(double)h))||!orange(at(p,w,h,.82,y/(double)h)))continue;
   int top=y;
   while(y<h*.66f&&orange(at(p,w,h,.18,y/(double)h))&&orange(at(p,w,h,.82,y/(double)h)))y++;
   int bottom=y;
   if(bottom-top<h*.027f||bottom-top>h*.115f)continue;
   if(!white(at(p,w,h,.18,(bottom+h*.012)/h))||!white(at(p,w,h,.82,(bottom+h*.012)/h)))continue;
   for(int edge=bottom+(int)(h*.09f);edge<Math.min(h*.84f,bottom+h*.36f);edge++){
    if(!border(p,w,h,edge))continue;
    int below=edge+Math.max(2,(int)(h*.008f));
    if(!white(at(p,w,h,.35,below/(double)h))||!white(at(p,w,h,.65,below/(double)h)))continue;
    for(int end=edge+(int)(h*.027f);end<=edge+h*.065f;end++){
     if(!border(p,w,h,end))continue;
     int mid=(edge+end)/2;
     if(!white(at(p,w,h,.30,mid/(double)h))||!white(at(p,w,h,.70,mid/(double)h)))continue;
     // The error body has a sparse small message. Reward panels have much
     // larger coin/amount artwork, and a substantially shorter Close pill.
     int ink=0,count=0;
     for(int yy=bottom+Math.max(2,(int)(h*.015f));yy<edge-h*.012f;yy+=Math.max(1,h/150))
      for(int xx=(int)(w*.25f);xx<w*.75f;xx+=Math.max(1,w/80)){if(dark(p.get(xx,yy)))ink++;count++;}
     if(count<20||ink>count*.12f)continue;
     int label=0;
     for(int yy=edge+2;yy<end-1;yy++)for(int xx=(int)(w*.44f);xx<w*.56f;xx++)if(dark(p.get(xx,yy)))label++;
     if(label<Math.max(4,w*h*.00008f))continue;
     return new float[]{w*.5f,mid};
    }
   }
  }
  return null;
 }
 static boolean border(RegionSampler.Pixels p,int w,int h,int y){
  if(y<1||y>=h-1)return false;
  for(double x:new double[]{.34,.40,.60,.66})if(!dark(at(p,w,h,x,y/(double)h)))return false;
  return true;
 }
 static void sample(Map<String,float[]> out,int w,int h,RegionSampler.Pixels pixels){
  float[] p=locate(w,h,pixels);
  if(p==null){out.remove(KEY);out.remove(POINT);} else {out.put(KEY,new float[]{1});out.put(POINT,new float[]{p[0],p[1],w,h});}
 }
 static boolean named(Map<String,float[]> samples,float x,float y,int w,int h){
  if(!Float.isFinite(x)||!Float.isFinite(y)||x<0||y<0||x>=w||y>=h)return false;
  samples.put(KEY,new float[]{1});samples.put(POINT,new float[]{x,y,w,h});return true;
 }
 static float[] point(Map<String,float[]> samples,int w,int h){
  float[] p=samples.get(POINT),v=samples.get(KEY);
  if(v==null||v.length!=1||v[0]!=1||p==null||p.length!=4||p[2]!=w||p[3]!=h)return null;
  if(!Float.isFinite(p[0])||!Float.isFinite(p[1])||p[0]<0||p[1]<0||p[0]>=w||p[1]>=h)return null;
  return new float[]{p[0],p[1]};
 }
}
