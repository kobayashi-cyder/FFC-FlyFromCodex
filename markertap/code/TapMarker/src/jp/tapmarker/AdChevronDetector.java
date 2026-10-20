package jp.tapmarker;
import java.util.*;

/** Two verified white chevrons. Badge colour may vary; countdown glyphs must fail. */
final class AdChevronDetector {
 static boolean white(int c){int r=c>>16&255,g=c>>8&255,b=c&255;return Math.min(r,Math.min(g,b))>205&&Math.max(r,Math.max(g,b))-Math.min(r,Math.min(g,b))<15;}
 static float[] find(int width,int height,float[] region,RegionSampler.Pixels pixels,float[] reference,float[]... alternatives){
  if(reference==null||reference.length!=768)return null;int[] b=RegionMath.bounds(region,width,height);int w=b[2]-b[0],h=b[3]-b[1];boolean[] mask=new boolean[w*h],seen=new boolean[w*h];int[] queue=new int[w*h];ArrayList<int[]> glyphs=new ArrayList<>();
  for(int y=0;y<h;y++)for(int x=0;x<w;x++)mask[y*w+x]=white(pixels.get(b[0]+x,b[1]+y));
  int min=Math.max(4,Math.round(width*.014f)),max=Math.round(width*.045f);
  for(int seed=0;seed<mask.length;seed++){if(!mask[seed]||seen[seed])continue;int count=0,end=1,left=w,right=0,top=h,bottom=0;seen[seed]=true;queue[0]=seed;
   while(count<end){int p=queue[count++],x=p%w,y=p/w;left=Math.min(left,x);right=Math.max(right,x);top=Math.min(top,y);bottom=Math.max(bottom,y);for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++){int nx=x+dx,ny=y+dy;if(nx<0||nx>=w||ny<0||ny>=h)continue;int i=ny*w+nx;if(mask[i]&&!seen[i]){seen[i]=true;queue[end++]=i;}}}
   int cw=right-left+1,ch=bottom-top+1;float aspect=cw/(float)ch,fill=end/(float)(cw*ch);if(ch>=min&&ch<=max&&aspect>=.30f&&aspect<=.85f&&fill>=.15f&&fill<=.70f)glyphs.add(new int[]{left,top,right,bottom});
  }
  float[] result=null;
  for(int[] left:glyphs)for(int[] right:glyphs){int gap=right[0]-left[2]-1,lh=left[3]-left[1]+1,rh=right[3]-right[1]+1;if(gap<1||gap>width*.017f||Math.abs(lh-rh)>Math.max(2,lh*.18f)||Math.abs(left[1]-right[1])>Math.max(2,lh*.15f))continue;
   float[] box={(b[0]+left[0])/(float)width,(b[1]+Math.min(left[1],right[1]))/(float)height,(b[0]+right[2]+1)/(float)width,(b[1]+Math.max(left[3],right[3])+1)/(float)height};
   float[] raw=RegionSampler.sample(width,height,box,(x,y)->white(pixels.get(x,y))?0xffffffff:0xff000000);
   float score=PhaseEngine.blockDistance(reference,raw);for(float[] variant:alternatives)if(variant!=null)score=Math.min(score,PhaseEngine.blockDistance(variant,raw));if(score>.010f)continue;
   float[] point=RegionMath.center(box,width,height);if(result!=null&&Math.abs(result[0]-point[0])>2)return null;result=point;
  }
  return result;
 }
}
