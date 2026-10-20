package jp.tapmarker;

/** The application window and its portrait content are separate from the desktop. */
final class ContentViewport {
 final int left,top,width,height,frameWidth,frameHeight;
 ContentViewport(int l,int t,int w,int h,int fw,int fh){left=l;top=t;width=w;height=h;frameWidth=fw;frameHeight=fh;}
 static ContentViewport locate(int w,int h,RegionSampler.Pixels pixels,int[] window){
  int l=0,t=0,r=w,b=h;
  if(window!=null){if(window.length!=4||window[0]<0||window[1]<0||window[2]>w||window[3]>h||window[2]<=window[0]||window[3]<=window[1])throw new IllegalArgumentException("対象ウインドウの範囲が不正です");l=window[0];t=window[1];r=window[2];b=window[3];}
  int span=r-l;
  // Restrict automatic cropping to paired dark side bars. System bars and dialogs
  // remain in the image; the game's independent landmarks must still confirm it.
  if(span>=240&&b-t>=240&&span/(float)(b-t)>.65f){
   int left=l,right=r;
   while(left<l+span*.44f&&darkColumn(left,t,b,pixels))left++;
   while(right>r-span*.44f&&darkColumn(right-1,t,b,pixels))right--;
   int a=left-l,z=r-right;
   if(a>=4&&z>=4&&Math.abs(a-z)<=Math.max(4,span*.04f)&&right-left>=144&&!darkColumn((left+right)/2,t,b,pixels)){l=left;r=right;}
  }
  return new ContentViewport(l,t,r-l,b-t,w,h);
 }
 static boolean darkColumn(int x,int top,int bottom,RegionSampler.Pixels p){for(float y:new float[]{.16f,.32f,.5f,.68f,.84f}){int c=p.get(x,top+Math.min(bottom-top-1,(int)((bottom-top)*y)));if((c>>16&255)>24||(c>>8&255)>24||(c&255)>24)return false;}return true;}
 float[] metadata(){return new float[]{left,top,width,height,frameWidth,frameHeight};}
 static float[] offset(float[] point,float[] v,int w,int h){if(point==null||v==null||v.length!=6||v[4]!=w||v[5]!=h||point.length!=2||point[0]<0||point[1]<0||point[0]>=v[2]||point[1]>=v[3])return null;return new float[]{point[0]+v[0],point[1]+v[1]};}
 private ContentViewport(){this(0,0,0,0,0,0);}
}
