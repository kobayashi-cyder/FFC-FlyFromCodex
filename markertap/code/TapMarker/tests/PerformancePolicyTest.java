package jp.tapmarker;
public class PerformancePolicyTest {
 static int checks;static void check(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
 public static void main(String[] args){
  check(PerformancePolicy.choose(0,true,256,8L<<30,8)==PerformancePolicy.ECO,"low-RAM device is respected even with many cores");
  check(PerformancePolicy.choose(0,false,128,8L<<30,8)==PerformancePolicy.ECO,"restricted heap gets the light profile");
  check(PerformancePolicy.choose(0,false,256,2L<<30,8)==PerformancePolicy.ECO,"limited total RAM gets the light profile");
  check(PerformancePolicy.choose(0,false,256,8L<<30,8)==PerformancePolicy.NORMAL,"unknown devices avoid the aggressive profile");
  check(PerformancePolicy.choose(3,true,64,1L<<30,2)==PerformancePolicy.QUICK,"explicit speed choice is respected");
  for(String model:new String[]{"F-52A","F52A","f-52a"," F_52A "}){
   check(PerformancePolicy.isF52A(model),"model spelling recognized: "+model);
   check(PerformancePolicy.choose(0,false,256,8L<<30,8,model)==PerformancePolicy.F52A,"8GB/eight cores must not hide the F-52A compatibility profile");
   check(PerformancePolicy.choose(2,false,256,8L<<30,8,model)==PerformancePolicy.NORMAL,"explicit standard choice retained on F-52A");
   check(PerformancePolicy.choose(3,false,256,8L<<30,8,model)==PerformancePolicy.QUICK,"explicit fast choice retained on F-52A");
  }
  for(String model:new String[]{null,"","F-52B","F-51A","other F-52A","Pixel 10"})check(!PerformancePolicy.isF52A(model),"other models do not silently get an F-52A policy");
  for(PerformancePolicy.Profile p:new PerformancePolicy.Profile[]{PerformancePolicy.ECO,PerformancePolicy.NORMAL,PerformancePolicy.QUICK,PerformancePolicy.F52A}){
   for(int requested:new int[]{1,3,50,500,1000})check(PerformancePolicy.effectiveTap(requested,p)>=requested,"saved slower pacing is never made faster");
   for(int screens=1;screens<=6;screens++)for(long heap:new long[]{64L<<20,128L<<20,256L<<20}){
    int[] size=PerformancePolicy.geometry(p,1080,2424,420,screens,heap);
    long bytes=(long)size[0]*size[1]*8*screens,budget=Math.max(8L<<20,Math.min(64L<<20,heap/3));
    check(bytes<=budget+screens*8*size[0],"reusable images fit within the reserved heap, allowing one rounded row");
    check(size[0]<=1080&&size[0]<=p.width,"small devices are never rendered at an enlarged width");
    check(Math.abs(size[1]/(double)size[0]-2424/1080.0)<.01,"recognition and input retain the native aspect ratio");
   }
   check(PerformancePolicy.scanInterval(p,600,4,2)>=1200,"slow analysis backs off instead of monopolizing the CPU");
   check(PerformancePolicy.scanInterval(p,0,4,8)==p.scanMs,"idle analysis uses the selected cadence");
  }
  for(int screens=1;screens<=6;screens++){
   int[] size=PerformancePolicy.geometry(PerformancePolicy.F52A,1080,2280,420,screens,256L<<20);
   check(size[0]==540&&size[1]==1140&&size[2]==210,"F-52A maintains 19:9 geometry and logical UI size for "+screens+" windows");
   check(PerformancePolicy.startupDelay(PerformancePolicy.F52A,screens-1)==(screens-1)*650L,"F-52A launch loads are staggered without removing a window");
  }
  check(PerformancePolicy.startupDelay(PerformancePolicy.F52A,0)==0&&PerformancePolicy.startupDelay(PerformancePolicy.NORMAL,3)==0,"first app and other profiles keep immediate launch");
  check(PerformancePolicy.effectiveTap(1,PerformancePolicy.F52A)==1&&PerformancePolicy.effectiveTap(120,PerformancePolicy.F52A)==120,"recognition profiles do not cap fast input and retain slower saved settings");
  boolean rejected=false;try{PerformancePolicy.geometry(PerformancePolicy.ECO,1080,2424,420,50,16L<<20);}catch(IllegalArgumentException lowMemory){rejected=true;}
  check(rejected,"too many screens fail before allocating image buffers");
  System.out.println("PerformancePolicyTest: "+checks+" checks passed");
 }
}
