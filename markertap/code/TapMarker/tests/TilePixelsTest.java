package jp.tapmarker;
import java.util.*;
public class TilePixelsTest {
 static int checks;static void check(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
 public static void main(String[] args){
  int w=93,h=145;int[] image=new int[w*h];Random r=new Random(651);for(int i=0;i<image.length;i++)image[i]=r.nextInt();
  RegionSampler.Pixels direct=(x,y)->image[y*w+x];
  TilePixels cached=new TilePixels(w,h,(x,y,cw,ch,p,stride)->{for(int j=0;j<ch;j++)System.arraycopy(image,(y+j)*w+x,p,j*stride,cw);},3);
  for(int i=0;i<6000;i++){int x=r.nextInt(w),y=r.nextInt(h);check(cached.get(x,y)==direct.get(x,y),"LRU reuse and edge tiles preserve every color");}
  for(int i=0;i<30;i++){float a=r.nextFloat()*.6f,b=r.nextFloat()*.6f;float[] roi={a,b,a+.3f,b+.3f};check(Arrays.equals(RegionSampler.sample(w,h,roi,direct),RegionSampler.sample(w,h,roi,cached)),"sampling and thresholds remain bit-identical with caching");}
  TilePixels stable=new TilePixels(w,h,(x,y,cw,ch,p,stride)->{for(int j=0;j<ch;j++)System.arraycopy(image,(y+j)*w+x,p,j*stride,cw);},64);
  float[] roi={.1f,.1f,.3f,.2f};for(int i=0;i<100;i++)RegionSampler.sample(w,h,roi,stable);
  check(stable.reads<=4,"a repeated region needs a few bulk reads instead of 230400 point reads");check(cached.tiles.size()<=3,"pixel cache has a hard allocation bound");
  System.out.println("TilePixelsTest: "+checks+" checks passed; bulk source reads="+stable.reads+" versus 230400 point accesses");
 }
}
