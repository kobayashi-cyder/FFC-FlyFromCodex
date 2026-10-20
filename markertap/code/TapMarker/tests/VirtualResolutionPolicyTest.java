package jp.tapmarker;
import java.util.Arrays;
public final class VirtualResolutionPolicyTest {
 static int checks;
 static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 public static void main(String[] args){
  PerformancePolicy.Profile[] profiles={PerformancePolicy.ECO,PerformancePolicy.NORMAL,PerformancePolicy.QUICK,PerformancePolicy.F52A};
  int[] saved={-1,0,32,96,160,320,540,720,Integer.MAX_VALUE};
  for(PerformancePolicy.Profile p:profiles)for(int count=1;count<=4;count++)for(long heap:new long[]{64L<<20,128L<<20,256L<<20})for(int old:saved){
   long required=VirtualResolutionPolicy.imageBytes(1080,2424,count),budget=Math.max(8L<<20,heap/2);
   try{int[] actual=VirtualResolutionPolicy.geometry(p,1080,2424,420,count,heap,old);
    check(required<=budget,"insufficient requests are refused before allocation");
    check(Arrays.equals(actual,new int[]{1080,2424,420}),"saved trials and load profiles never change pixels or density");
    check(Arrays.equals(actual,PerformancePolicy.geometry(p,1080,2424,420,count,heap)),"legacy and current entry points agree");
   }catch(IllegalArgumentException limited){check(required>budget,"only insufficient memory rejects this valid geometry");}
  }
  for(int[] geometry:new int[][]{{1080,2424,420},{2424,1080,420},{480,2400,320},{320,480,160},{1440,3200,560},{4096,2160,640}}){
   int[] actual=VirtualResolutionPolicy.geometry(PerformancePolicy.ECO,geometry[0],geometry[1],geometry[2],1,2L<<30,32);
   check(Arrays.equals(actual,geometry),"portrait, landscape, tall and high-density displays keep their exact geometry");
   float[] point=VirtualPreview.point(10,actual[0],actual[1],actual[0],actual[1],400,600,200,300);
   check(point!=null&&Math.abs(point[0]-actual[0]*.5f)<.02&&Math.abs(point[1]-actual[1]*.5f)<.02,"preview input maps to actual native pixels");
   check(VirtualPreview.point(0,actual[0],actual[1],actual[0],actual[1],400,600,200,300)==null,"normal resolution never authorizes main-display input");
  }
  for(int old:saved){
   check(VirtualResolutionPolicy.selection(old)==0,"every saved low-resolution selection is disabled");
   check(VirtualResolutionPolicy.initialExperimentSelection(old,false)==0&&VirtualResolutionPolicy.initialExperimentSelection(old,true)==0,"opening settings cannot re-enable an experiment");
   check(VirtualResolutionPolicy.nextHigher(old)==-1,"there are no ascending experimental widths");
   check(VirtualResolutionPolicy.needsNativeMigration(old,false),"first use applies the native migration");
   check(VirtualResolutionPolicy.needsNativeMigration(old,true)==(old!=0),"a later legacy value is cleared again");
  }
  check(VirtualResolutionPolicy.WIDTHS.length==0,"no low-resolution stage can be selected");
  for(int[] invalid:new int[][]{{0,2424,420},{1080,0,420},{1080,2424,0},{4097,2424,420}}){
   boolean refused=false;try{VirtualResolutionPolicy.geometry(PerformancePolicy.NORMAL,invalid[0],invalid[1],invalid[2],1,256L<<20,320);}catch(IllegalArgumentException expected){refused=true;}
   check(refused,"invalid native geometry is rejected without replacement dimensions");
  }
  boolean refused=false;try{VirtualResolutionPolicy.geometry(PerformancePolicy.NORMAL,1080,2424,420,4,16L<<20,32);}catch(IllegalArgumentException expected){refused=true;}
  check(refused,"memory pressure never restores the old 32px fallback");
  check(VirtualResolutionPolicy.imageBytes(360,808,2)==4654080L,"both reusable arrays are included in the estimate");
  System.out.println("VirtualResolutionPolicyTest: "+checks+" checks passed; saved-width migration, exact native geometry, memory refusal and preview mapping; Android device not exercised");
 }
}
