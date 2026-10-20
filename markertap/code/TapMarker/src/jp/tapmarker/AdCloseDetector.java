package jp.tapmarker;
import java.util.*;

/** Finds a contrasting X of either polarity inside one small, specified corner. */
final class AdCloseDetector {
 static float[] find(int width,int height,float[] region,RegionSampler.Pixels pixels){
  float[] white=findPolarity(width,height,region,pixels,true),dark=findPolarity(width,height,region,pixels,false);
  if(white!=null&&dark!=null&&Math.hypot(white[0]-dark[0],white[1]-dark[1])>width*.015f)return null;
  return white==null?dark:white;
 }
 static float[] findPolarity(int width,int height,float[] region,RegionSampler.Pixels pixels,boolean light){
  int[] b=RegionMath.bounds(region,width,height);int w=b[2]-b[0],h=b[3]-b[1];
  boolean[] bright=new boolean[w*h],seen=new boolean[w*h];
  for(int y=0;y<h;y++)for(int x=0;x<w;x++)bright[y*w+x]=light?bright(pixels.get(b[0]+x,b[1]+y)):darkGlyph(pixels.get(b[0]+x,b[1]+y));
  float[] result=null;int min=Math.max(4,Math.round(width*.014f)),max=Math.max(min,Math.round(width*.075f));
  int[] queue=new int[w*h];
  for(int seed=0;seed<bright.length;seed++){
   if(!bright[seed]||seen[seed])continue;
   int count=0,end=1,left=w,right=0,top=h,bottom=0;queue[0]=seed;seen[seed]=true;
   while(count<end){int point=queue[count++],x=point%w,y=point/w;left=Math.min(left,x);right=Math.max(right,x);top=Math.min(top,y);bottom=Math.max(bottom,y);
    for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++){int nx=x+dx,ny=y+dy;if(nx<0||ny<0||nx>=w||ny>=h)continue;int next=ny*w+nx;if(bright[next]&&!seen[next]){seen[next]=true;queue[end++]=next;}}
   }
   int cw=right-left+1,ch=bottom-top+1;if(cw<min||ch<min||cw>max||ch>max||cw/(float)ch<.7f||cw/(float)ch>1.4f)continue;
   float fill=end/(float)(cw*ch);if(fill<.17f||fill>.70f)continue;
   int diagonal=0;int[] arms=new int[4];
   for(int i=0;i<end;i++){float x=(queue[i]%w-left)/(float)Math.max(1,cw-1),y=(queue[i]/w-top)/(float)Math.max(1,ch-1);if(Math.abs(x-y)<=.22f||Math.abs(x+y-1)<=.22f)diagonal++;if((x<.3f||x>.7f)&&(y<.3f||y>.7f))arms[(x>.5f?1:0)+(y>.5f?2:0)]++;}
   if(diagonal<end*.82f||Arrays.stream(arms).anyMatch(n->n<1))continue;
   int ring=0,dark=0,margin=Math.max(2,cw/5);
   for(int y=top-margin;y<=bottom+margin;y++)for(int x=left-margin;x<=right+margin;x++){
    if(x<0||y<0||x>=w||y>=h||x>=left&&x<=right&&y>=top&&y<=bottom)continue;
    ring++;int c=pixels.get(b[0]+x,b[1]+y);if(light?(dark(c)||neutralCircle(c)):lightBackground(c))dark++;
   }
   if(ring<10||dark<ring*.85f)continue;
   // More than one eligible symbol in one corner is ambiguous.
   if(result!=null)return null;
   result=new float[]{b[0]+(left+right+1)/2f,b[1]+(top+bottom+1)/2f};
  }
  return result;
 }
 static boolean bright(int color){int r=color>>16&255,g=color>>8&255,b=color&255;return Math.min(r,Math.min(g,b))>=155&&Math.max(r,Math.max(g,b))-Math.min(r,Math.min(g,b))<=55;}
 static boolean dark(int color){return Math.max(color>>16&255,Math.max(color>>8&255,color&255))<85;}
 static boolean neutralCircle(int color){int r=color>>16&255,g=color>>8&255,b=color&255;int max=Math.max(r,Math.max(g,b)),min=Math.min(r,Math.min(g,b));return min>=85&&max<=180&&max-min<=20;}
 static boolean darkGlyph(int c){int r=c>>16&255,g=c>>8&255,b=c&255;return Math.max(r,Math.max(g,b))<=150&&Math.max(r,Math.max(g,b))-Math.min(r,Math.min(g,b))<=40;}
 static boolean lightBackground(int c){int r=c>>16&255,g=c>>8&255,b=c&255;return Math.min(r,Math.min(g,b))>=185&&Math.max(r,Math.max(g,b))-Math.min(r,Math.min(g,b))<=35;}
}
