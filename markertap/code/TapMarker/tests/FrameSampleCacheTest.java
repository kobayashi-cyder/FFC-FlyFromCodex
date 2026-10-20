package jp.tapmarker;
import java.util.*;

/** Check exact original sampling, independent output arrays, eviction and frame isolation. */
public final class FrameSampleCacheTest {
 static int checks;static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
 static float[] original(int width,int height,float[] region,RegionSampler.Pixels pixels){
  float[] f=new float[768];int[] b=RegionMath.bounds(region,width,height);int left=b[0],top=b[1],w=b[2]-left,h=b[3]-top;
  for(int y=0;y<16;y++)for(int x=0;x<16;x++){float r=0,g=0,blue=0;for(int dy=0;dy<3;dy++)for(int dx=0;dx<3;dx++){int px=Math.min(w-1,(int)((x+(dx+.5f)/3)*w/16)),py=Math.min(h-1,(int)((y+(dy+.5f)/3)*h/16));int c=pixels.get(left+px,top+py);r+=c>>16&255;g+=c>>8&255;blue+=c&255;}int i=(y*16+x)*3;f[i]=r/(9*255);f[i+1]=g/(9*255);f[i+2]=blue/(9*255);}return f;
 }
 public static void main(String[] args){
  Random random=new Random(176);for(int trial=0;trial<250;trial++){int w=1+random.nextInt(1300),h=1+random.nextInt(2600);RegionSampler.Pixels image=(x,y)->0xff000000|(x*991+y*337)&0xffffff;FrameSampleCache cache=new FrameSampleCache(w,h,image,8);
   float a=random.nextFloat()*1.4f-.2f,b=random.nextFloat()*1.4f-.2f;float[] roi={a,b,a+random.nextFloat(),b+random.nextFloat()};float[] expected=original(w,h,roi,image);
   check(Arrays.equals(expected,RegionSampler.sample(w,h,roi,cache)),"optimized sampler remains bit identical for clamped/thin/tiny/large regions");
   float[] mutable=RegionSampler.sample(w,h,roi,cache);Arrays.fill(mutable,-1);check(Arrays.equals(expected,RegionSampler.sample(w,h,roi,cache)),"a caller cannot mutate cached evidence");check(cache.misses==1&&cache.hits==2,"same pixel bounds are read only once");
  }
  int w=240,h=480;long[] calls={0};RegionSampler.Pixels first=(x,y)->{calls[0]++;return 0xff345678;};FrameSampleCache cache=new FrameSampleCache(w,h,first,2);float[] roi={.1f,.1f,.4f,.3f};
  for(int i=0;i<100;i++)RegionSampler.sample(w,h,roi,cache);check(calls[0]==2304&&cache.hits==99,"100 identical requests perform exactly one original pixel sample");
  for(int i=0;i<30;i++)RegionSampler.sample(w,h,new float[]{i/40f,.1f,(i+1)/40f,.3f},cache);check(cache.size()==2,"cache memory stays bounded");
  check(RegionSampler.sample(w,h,roi,new FrameSampleCache(w,h,(x,y)->0xffabcdef,2))[0]!=RegionSampler.sample(w,h,roi,cache)[0],"a new frame cannot inherit old evidence");
  check(Arrays.equals(original(120,240,roi,first),RegionSampler.sample(120,240,roi,cache)),"different dimensions never reuse a cached rectangle");
  System.out.println("FrameSampleCacheTest: "+checks+" checks passed; identical evidence and bounded per-frame storage");
 }
}
