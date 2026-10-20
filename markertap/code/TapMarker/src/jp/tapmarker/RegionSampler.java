package jp.tapmarker;
public final class RegionSampler {
 public interface Pixels {int get(int x,int y);}
 public static float[] sample(int width,int height,float[] region,Pixels pixels){if(pixels instanceof FrameSampleCache)return ((FrameSampleCache)pixels).sample(width,height,region);return sampleBounds(RegionMath.bounds(region,width,height),pixels);}
 static float[] sampleBounds(int[] bounds,Pixels pixels){
  float[] f=new float[768];int left=bounds[0],top=bounds[1],w=bounds[2]-left,h=bounds[3]-top;
  int[] xs=new int[48],ys=new int[48];
  for(int cell=0;cell<16;cell++)for(int sub=0;sub<3;sub++){xs[cell*3+sub]=left+Math.min(w-1,(int)((cell+(sub+.5f)/3)*w/16));ys[cell*3+sub]=top+Math.min(h-1,(int)((cell+(sub+.5f)/3)*h/16));}
  for(int y=0;y<16;y++)for(int x=0;x<16;x++){float r=0,g=0,b=0;for(int dy=0;dy<3;dy++)for(int dx=0;dx<3;dx++){int c=pixels.get(xs[x*3+dx],ys[y*3+dy]);r+=(c>>16)&255;g+=(c>>8)&255;b+=c&255;}int i=(y*16+x)*3;f[i]=r/(9*255);f[i+1]=g/(9*255);f[i+2]=b/(9*255);}return f;
 }
}
