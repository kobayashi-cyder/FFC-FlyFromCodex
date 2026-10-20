package jp.tapmarker;

/** Startup-only pixel geometry. Explicit experiments never inherit the 72-dpi floor. */
final class VirtualResolutionPolicy {
 static final int MIN_WIDTH=32,MAX_WIDTH=720;
 static final int[] WIDTHS={32,48,64,96,128,160,200,240,280,320,360,400,480,540,640,720};
 static int selection(int saved){return saved==0||saved>=MIN_WIDTH&&saved<=MAX_WIDTH?saved:0;}
 static int initialExperimentSelection(int saved,boolean initialized){return initialized?selection(saved):MIN_WIDTH;}
 static int nextHigher(int width){for(int candidate:WIDTHS)if(candidate>width)return candidate;return -1;}
 static boolean backendGeometry(int width,int height,int dpi){return width>=MIN_WIDTH&&width<=4096&&height>=MIN_WIDTH&&height<=4096&&dpi>=1&&dpi<=1000;}
 static long imageBytes(int width,int height,int count){return (long)width*height*8*count;}
 static int[] geometry(PerformancePolicy.Profile profile,int sw,int sh,int dpi,int count,long heap,int saved){
  int requested=selection(saved);
  if(requested==0)return PerformancePolicy.geometry(profile,sw,sh,dpi,count,heap);
  if(sw<=0||sh<=0||dpi<=0||count<=0)throw new IllegalArgumentException("invalid display geometry");
  int width=Math.min(requested,sw);
  double aspect=Math.max(1,Math.min(3.5,sh/(double)sw));
  int height=Math.max(width,(int)Math.round(width*aspect));
  int density=Math.max(1,(int)Math.round(dpi*width/(double)sw));
  long budget=Math.max(8L<<20,Math.min(64L<<20,heap/3));
  if(imageBytes(width,height,count)>budget)throw new IllegalArgumentException("この幅では画像メモリの予算を超えます。幅か画面数を減らしてください。");
  return new int[]{width,height,density};
 }
 private VirtualResolutionPolicy(){}
}
