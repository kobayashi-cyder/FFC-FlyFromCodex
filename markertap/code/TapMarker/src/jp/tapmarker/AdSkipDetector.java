package jp.tapmarker;
import java.util.*;

/** A ready yellow-ring double-chevron from this video, searched inside an active ad corner. */
final class AdSkipDetector {
 static float[] find(int width,int height,float[] region,RegionSampler.Pixels pixels,float[] reference){
  int[] b=RegionMath.bounds(region,width,height);int w=b[2]-b[0],h=b[3]-b[1];boolean[] yellow=new boolean[w*h],seen=new boolean[w*h];
  for(int y=0;y<h;y++)for(int x=0;x<w;x++){int c=pixels.get(b[0]+x,b[1]+y);yellow[y*w+x]=(c>>16&255)>190&&(c>>8&255)>150&&(c&255)<110;}
  int[] queue=new int[w*h];float[] result=null;int min=Math.max(8,Math.round(width*.03f)),max=Math.round(width*.10f);
  for(int seed=0;seed<yellow.length;seed++){
   if(!yellow[seed]||seen[seed])continue;int count=0,end=1,left=w,right=0,top=h,bottom=0;seen[seed]=true;queue[0]=seed;
   while(count<end){int p=queue[count++],x=p%w,y=p/w;left=Math.min(left,x);right=Math.max(right,x);top=Math.min(top,y);bottom=Math.max(bottom,y);
    for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++){int nx=x+dx,ny=y+dy;if(nx<0||nx>=w||ny<0||ny>=h)continue;int i=ny*w+nx;if(yellow[i]&&!seen[i]){seen[i]=true;queue[end++]=i;}}
   }
   int cw=right-left+1,ch=bottom-top+1;float fill=end/(float)(cw*ch);if(cw<min||ch<min||cw>max||ch>max||cw/(float)ch<.8f||cw/(float)ch>1.25f||fill<.08f||fill>.45f)continue;
   float[] box={(b[0]+left)/(float)width,(b[1]+top)/(float)height,(b[0]+right+1)/(float)width,(b[1]+bottom+1)/(float)height};
   if(PhaseEngine.blockDistance(reference,RegionSampler.sample(width,height,box,pixels))>.012f)continue;
   if(result!=null)return null;result=new float[]{b[0]+(left+right+1)/2f,b[1]+(top+bottom+1)/2f};
  }
  return result;
 }
}
