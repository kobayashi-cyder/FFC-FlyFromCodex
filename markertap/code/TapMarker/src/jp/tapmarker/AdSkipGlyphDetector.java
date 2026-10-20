package jp.tapmarker;
import java.util.*;

/** A filled right triangle followed by a separate vertical stop bar, never a Play triangle alone. */
final class AdSkipGlyphDetector {
 static boolean light(int c){int r=c>>16&255,g=c>>8&255,b=c&255;return Math.min(r,Math.min(g,b))>=195&&Math.max(r,Math.max(g,b))-Math.min(r,Math.min(g,b))<=35;}
 static final class Glyph {
  int left,right,top,bottom,count,ideal,leftCount,rightCount;
  int width(){return right-left+1;}int height(){return bottom-top+1;}
  float fill(){return count/(float)(width()*height());}
 }
 static float[] find(int width,int height,float[] region,RegionSampler.Pixels pixels){
  int[] bounds=RegionMath.bounds(region,width,height);int w=bounds[2]-bounds[0],h=bounds[3]-bounds[1];
  boolean[] mask=new boolean[w*h],seen=new boolean[w*h];int[] queue=new int[w*h];List<Glyph> glyphs=new ArrayList<>();
  for(int y=0;y<h;y++)for(int x=0;x<w;x++)mask[y*w+x]=light(pixels.get(bounds[0]+x,bounds[1]+y));
  int min=Math.max(4,Math.round(width*.007f)),max=Math.max(min,Math.round(width*.045f));
  for(int seed=0;seed<mask.length;seed++){
   if(!mask[seed]||seen[seed])continue;int read=0,end=1;queue[0]=seed;seen[seed]=true;Glyph g=new Glyph();g.left=w;g.top=h;
   while(read<end){int p=queue[read++],x=p%w,y=p/w;g.left=Math.min(g.left,x);g.right=Math.max(g.right,x);g.top=Math.min(g.top,y);g.bottom=Math.max(g.bottom,y);
    for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++){int nx=x+dx,ny=y+dy;if(nx<0||ny<0||nx>=w||ny>=h)continue;int next=ny*w+nx;if(mask[next]&&!seen[next]){seen[next]=true;queue[end++]=next;}}
   }
   g.count=end;if(g.height()<min||g.height()>max||g.width()>max)continue;
   // One raster pixel is a large fraction of a small, antialiased badge.
   float rasterTolerance=1f/Math.max(g.width(),g.height());
   for(int i=0;i<end;i++){float x=(queue[i]%w-g.left)/(float)Math.max(1,g.width()-1),y=(queue[i]/w-g.top)/(float)Math.max(1,g.height()-1);if(x<=1.20f+rasterTolerance-2*Math.abs(y-.5f))g.ideal++;if(x<=.25f)g.leftCount++;if(x>=.75f)g.rightCount++;}
   glyphs.add(g);
  }
  float[] result=null;
  for(Glyph triangle:glyphs){float aspect=triangle.width()/(float)triangle.height();
   if(aspect<.40f||aspect>1.20f||triangle.fill()<.40f||triangle.fill()>.82f||triangle.ideal<triangle.count*.85f||triangle.leftCount<triangle.rightCount*1.5f)continue;
   for(Glyph bar:glyphs){int gap=bar.left-triangle.right-1;
    if(bar==triangle||bar.width()>Math.max(2,bar.height()*.30f)||bar.fill()<.60f||bar.height()<triangle.height()*.60f||bar.height()>triangle.height()*1.60f||gap<1||gap>triangle.height()*.70f||Math.abs(bar.top+bar.bottom-triangle.top-triangle.bottom)>triangle.height()*.55f)continue;
    int left=triangle.left,right=bar.right,top=Math.min(triangle.top,bar.top),bottom=Math.max(triangle.bottom,bar.bottom),margin=Math.max(2,triangle.height()/3),ring=0,white=0;
    for(int y=Math.max(0,top-margin);y<=Math.min(h-1,bottom+margin);y++)for(int x=Math.max(0,left-margin);x<=Math.min(w-1,right+margin);x++)if(x<left||x>right||y<top||y>bottom){ring++;if(mask[y*w+x])white++;}
    if(ring<10||white>ring*.30f)continue;
    if(adjacentText(width,height,pixels,bounds[0]+right+1,bounds[1]+top,bounds[1]+bottom))continue;
    float[] point={bounds[0]+(left+right+1)/2f,bounds[1]+(top+bottom+1)/2f};
    if(result!=null&&Math.hypot(result[0]-point[0],result[1]-point[1])>2)return null;result=point;
   }
  }
  return result;
 }
 /** A triangle/bar embedded in a brand label is not an independently identified exit. */
 static boolean adjacentText(int width,int height,RegionSampler.Pixels pixels,int right,int top,int bottom){
  int size=bottom-top+1,start=right+Math.max(2,size/4),end=Math.min(width,right+size*7),columns=0;
  for(int x=start;x<end;x++){int white=0;for(int y=Math.max(0,top-size/4);y<=Math.min(height-1,bottom+size/4);y++)if(light(pixels.get(x,y)))white++;if(white>=Math.max(2,size/7))columns++;}
  return columns>=size*1.5f;
 }
 private AdSkipGlyphDetector(){}
}
