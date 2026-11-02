package jp.tapmarker;
public final class VirtualFastWaitTest {
 static int checks;
 static void eq(long actual,long expected,String msg){checks++;if(actual!=expected)throw new AssertionError(msg+": "+actual+" instead of "+expected);}
 public static void main(String[] args){
   PerformancePolicy.Profile normal=new PerformancePolicy.Profile(PerformancePolicy.BALANCED,220);
   PerformancePolicy.Profile quick=new PerformancePolicy.Profile(PerformancePolicy.FAST,120);
   PerformancePolicy.Profile light=new PerformancePolicy.Profile(PerformancePolicy.LIGHT,350);
   eq(VirtualFastWait.scanInterval(normal,30,1,8),110,"one display fast scan");
   eq(VirtualFastWait.scanInterval(normal,30,2,8),120,"two displays min cadence");
   eq(VirtualFastWait.scanInterval(quick,40,1,8),90,"quick single");
   eq(VirtualFastWait.scanInterval(normal,500,2,8),525,"expensive analysis never overlapped");
   eq(VirtualFastWait.scanInterval(normal,500,2,4),1025,"four-core constrained scheduling");
   eq(VirtualFastWait.scanInterval(normal,1500,2,8),1200,"max cap");
   eq(VirtualFastWait.scanInterval(light,20,2,4),350,"low ram unchanged");
   eq(VirtualFastWait.scanInterval(normal,0,2,8),120,"nonnegative cost");
   System.out.println("VirtualFastWait: "+checks+" checks passed");
 }
}
