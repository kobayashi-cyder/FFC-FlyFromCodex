package jp.tapmarker;

import java.util.*;

/** Real policy and preview conversion: low-DPI trials must reach the shell unchanged. */
public final class VirtualResolutionPolicyTest {
 static int checks;
 static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 public static void main(String[] args){
  for(PerformancePolicy.Profile p:new PerformancePolicy.Profile[]{PerformancePolicy.ECO,PerformancePolicy.NORMAL,PerformancePolicy.QUICK,PerformancePolicy.F52A}){
   for(int count=1;count<=4;count++)for(long heap:new long[]{64L<<20,128L<<20,256L<<20}){
    check(Arrays.equals(PerformancePolicy.geometry(p,1080,2424,420,count,heap),VirtualResolutionPolicy.geometry(p,1080,2424,420,count,heap,0)),"automatic mode preserves the existing geometry and 72-dpi floor");
   }
   for(int width:VirtualResolutionPolicy.WIDTHS){
    int[] g=VirtualResolutionPolicy.geometry(p,1080,2424,420,2,256L<<20,width);
    check(g[0]==width,"explicit width is independent of the recognition-load profile");
    check(Math.abs(g[1]/(double)g[0]-2424/1080.0)<=.5/g[0],"aspect ratio is preserved within half a rounded row");
    check(Math.abs(g[2]-420*width/1080.0)<=.5,"DPI follows the same pixel scale");
    check(VirtualResolutionPolicy.backendGeometry(g[0],g[1],g[2]),"the shell accepts every trial, including widths below 240 and DPI below 72");
    check(Math.abs(g[0]*160.0/g[2]-1080*160.0/420)<=1080*160.0/420*.5/g[2]+.001,"logical UI width changes only within density-rounding error");
    float[] point=VirtualPreview.point(10,g[0],g[1],g[0],g[1],400,600,200,300);
    check(point!=null&&Math.abs(point[0]-g[0]*.5f)<.02&&Math.abs(point[1]-g[1]*.5f)<.02,"actual virtual pixels determine preview taps at each resolution");
    check(VirtualPreview.point(0,g[0],g[1],g[0],g[1],400,600,200,300)==null,"a trial never authorizes input to the main display");
   }
  }
  int[] low=VirtualResolutionPolicy.geometry(PerformancePolicy.NORMAL,1080,2424,420,2,256L<<20,96);
  check(low[2]<72,"experiments bypass the legacy density floor rather than changing the whole logical layout");
  check(VirtualResolutionPolicy.nextHigher(0)==32&&VirtualResolutionPolicy.nextHigher(32)==48&&VirtualResolutionPolicy.nextHigher(166)==200&&VirtualResolutionPolicy.nextHigher(720)==-1,"ascending trials start at the minimum and never wrap to automatic mode at the ceiling");
  for(int saved:new int[]{0,32,160,507,720}){
   check(VirtualResolutionPolicy.initialExperimentSelection(saved,false)==32,"first use starts at the minimum even after a descending-trial preference");
   check(VirtualResolutionPolicy.initialExperimentSelection(saved,true)==saved,"later visits retain the selected trial or explicit automatic mode");
  }
  int at=0;for(int width:VirtualResolutionPolicy.WIDTHS){check(width>at&&VirtualResolutionPolicy.nextHigher(at)==width,"every selectable stage is reached in strictly increasing order");at=width;}
  check(VirtualResolutionPolicy.nextHigher(at)==-1,"maximum width does not silently return to a lower stage");
  for(int invalid:new int[]{-1,1,31,721,Integer.MAX_VALUE})check(VirtualResolutionPolicy.selection(invalid)==0,"invalid saved widths return to the existing default");
  for(int custom:new int[]{32,166,719,720})check(VirtualResolutionPolicy.selection(custom)==custom,"an exact previously working width can be restored");
  check(!VirtualResolutionPolicy.backendGeometry(31,72,12)&&!VirtualResolutionPolicy.backendGeometry(64,31,24)&&!VirtualResolutionPolicy.backendGeometry(64,144,0)&&!VirtualResolutionPolicy.backendGeometry(4097,144,24),"invalid shell geometry is rejected");
  boolean refused=false;try{VirtualResolutionPolicy.geometry(PerformancePolicy.NORMAL,1080,2424,420,4,16L<<20,720);}catch(IllegalArgumentException insufficient){refused=true;}
  check(refused,"explicit resolution is rejected before allocation rather than silently changed by the memory budget");
  check(VirtualResolutionPolicy.geometry(PerformancePolicy.NORMAL,1080,2424,420,4,16L<<20,240)[0]==240,"a lower trial can proceed after the high-resolution request exceeded the budget");
  check(VirtualResolutionPolicy.imageBytes(360,808,2)==4654080L,"image-array estimate accounts for both arrays and both displays");
  System.out.println("VirtualResolutionPolicyTest: "+checks+" checks passed; geometry, low DPI, backend limits, memory budget and actual-pixel preview input; Android launch not exercised");
 }
}
