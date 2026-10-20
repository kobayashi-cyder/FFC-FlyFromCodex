package jp.tapmarker;

/** Virtual displays retain the phone's current pixel geometry and density. */
final class VirtualResolutionPolicy {
 // Kept only for compatibility with old callers; no experimental stage is selectable.
 static final int MIN_WIDTH=32,MAX_WIDTH=720;
 static final int[] WIDTHS={};
 static int selection(int saved){return 0;}
 static int initialExperimentSelection(int saved,boolean initialized){return 0;}
 static int nextHigher(int width){return -1;}
 static boolean needsNativeMigration(int saved,boolean migrated){return saved!=0||!migrated;}
 static boolean backendGeometry(int width,int height,int dpi){return width>=32&&width<=4096&&height>=32&&height<=4096&&dpi>=1&&dpi<=1000;}
 static long imageBytes(int width,int height,int count){return (long)width*height*8*count;}
 static int[] geometry(PerformancePolicy.Profile profile,int sw,int sh,int dpi,int count,long heap,int saved){
  if(!backendGeometry(sw,sh,dpi)||count<=0||heap<=0)throw new IllegalArgumentException("invalid display geometry");
  // Reserve half the Java heap for the rest of the application. Never reduce
  // either pixel dimension or density to make an unsupported request fit.
  long budget=Math.max(8L<<20,heap/2);
  if(imageBytes(sw,sh,count)>budget)throw new IllegalArgumentException("端末の通常解像度では画像メモリが不足します。仮想画面の数を減らしてください。");
  return new int[]{sw,sh,dpi};
 }
 private VirtualResolutionPolicy(){}
}
