package jp.tapmarker;
import java.util.*;

/** Recorded ready symbols in ad corners; empty disks/countdowns are not ready. */
final class AdReadyNextDetector {
 static final int[] DX={1,-1,0,0},DY={0,0,1,-1};
 static float[] find(int width,int height,float[] region,RegionSampler.Pixels pixels,float[][] references){
  if(references.length==0)return null;
  int[] b=RegionMath.bounds(region,width,height);int w=b[2]-b[0],h=b[3]-b[1];boolean[] neutral=new boolean[w*h],seen=new boolean[w*h];
  for(int y=0;y<h;y++)for(int x=0;x<w;x++){int c=pixels.get(b[0]+x,b[1]+y),r=c>>16&255,g=c>>8&255,blue=c&255;neutral[y*w+x]=Math.min(r,Math.min(g,blue))>=185&&Math.max(r,Math.max(g,blue))-Math.min(r,Math.min(g,blue))<=25;}
  int[] queue=new int[w*h];float[] result=null;int min=Math.max(8,Math.round(width*.04f)),max=Math.round(width*.09f);
  for(int seed=0;seed<neutral.length;seed++){
   if(!neutral[seed]||seen[seed])continue;int count=0,end=1,left=w,right=0,top=h,bottom=0;seen[seed]=true;queue[0]=seed;
   while(count<end){int p=queue[count++],x=p%w,y=p/w;left=Math.min(left,x);right=Math.max(right,x);top=Math.min(top,y);bottom=Math.max(bottom,y);
    for(int dir=0;dir<4;dir++){int nx=x+DX[dir],ny=y+DY[dir];if(nx<0||nx>=w||ny<0||ny>=h)continue;int i=ny*w+nx;if(neutral[i]&&!seen[i]){seen[i]=true;queue[end++]=i;}}
   }
   int cw=right-left+1,ch=bottom-top+1;float fill=end/(float)(cw*ch);if(cw<min||ch<min||cw>max||ch>max||cw/(float)ch<.88f||cw/(float)ch>1.14f||fill<.55f||fill>.88f)continue;
   float[] box={(b[0]+left)/(float)width,(b[1]+top)/(float)height,(b[0]+right+1)/(float)width,(b[1]+bottom+1)/(float)height};float[] sample=RegionSampler.sample(width,height,box,pixels);float best=Float.POSITIVE_INFINITY;
   for(float[] reference:references)best=Math.min(best,PhaseEngine.blockDistance(reference,sample));if(best>.008f)continue;
   if(result!=null)return null;result=new float[]{b[0]+(left+right+1)/2f,b[1]+(top+bottom+1)/2f};
  }
  float[] glyph=findLightGlyph(width,height,region,pixels,references);
  if(result!=null&&glyph!=null&&Math.hypot(result[0]-glyph[0],result[1]-glyph[1])>width*.025f)return null;
  return result==null?glyph:result;
 }
 static float[] findLightGlyph(int width,int height,float[] region,RegionSampler.Pixels pixels,float[][] references){
  int[] b=RegionMath.bounds(region,width,height);int w=b[2]-b[0],h=b[3]-b[1];boolean[] light=new boolean[w*h],seen=new boolean[w*h];int[] queue=new int[w*h];List<int[]> parts=new ArrayList<>();
  for(int y=0;y<h;y++)for(int x=0;x<w;x++){int c=pixels.get(b[0]+x,b[1]+y),r=c>>16&255,g=c>>8&255,blue=c&255;light[y*w+x]=Math.min(r,Math.min(g,blue))>=185&&Math.max(r,Math.max(g,blue))-Math.min(r,Math.min(g,blue))<=25;}
  for(int seed=0;seed<light.length;seed++){
   if(!light[seed]||seen[seed])continue;int count=0,end=1,left=w,right=0,top=h,bottom=0;queue[0]=seed;seen[seed]=true;
   while(count<end){int p=queue[count++],x=p%w,y=p/w;left=Math.min(left,x);right=Math.max(right,x);top=Math.min(top,y);bottom=Math.max(bottom,y);for(int d=0;d<4;d++){int nx=x+DX[d],ny=y+DY[d];if(nx<0||nx>=w||ny<0||ny>=h)continue;int i=ny*w+nx;if(light[i]&&!seen[i]){seen[i]=true;queue[end++]=i;}}}
   if(end>=5&&right-left+1<=width*.055f&&bottom-top+1<=width*.055f)parts.add(new int[]{left,top,right+1,bottom+1});
  }
  float[] found=null;
  for(int[] part:parts){
   int[] box=part.clone();for(int[] other:parts)if(other!=part&&Math.abs((other[1]+other[3]-part[1]-part[3])/2f)<=width*.012f&&other[0]-box[2]<=width*.016f&&box[0]-other[2]<=width*.016f){box[0]=Math.min(box[0],other[0]);box[1]=Math.min(box[1],other[1]);box[2]=Math.max(box[2],other[2]);box[3]=Math.max(box[3],other[3]);}
   int cw=box[2]-box[0],ch=box[3]-box[1];if(cw<width*.015f||ch<width*.015f||cw>width*.055f||ch>width*.055f||cw/(float)ch<.70f||cw/(float)ch>1.35f)continue;
   float[] bounds={(box[0]+b[0])/(float)width,(box[1]+b[1])/(float)height,(box[2]+b[0])/(float)width,(box[3]+b[1])/(float)height};float[] sample=RegionSampler.sample(width,height,bounds,pixels);float best=Float.POSITIVE_INFINITY;for(float[] reference:references)best=Math.min(best,PhaseEngine.blockDistance(reference,sample));if(best>.008f)continue;
   float[] point={b[0]+(box[0]+box[2])/2f,b[1]+(box[1]+box[3])/2f};if(found!=null&&Math.hypot(point[0]-found[0],point[1]-found[1])>width*.012f)return null;found=point;
  }
  return found;
 }
}
