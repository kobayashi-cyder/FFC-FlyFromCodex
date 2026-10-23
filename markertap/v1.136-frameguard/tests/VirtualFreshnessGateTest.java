package jp.tapmarker;
public final class VirtualFreshnessGateTest {
 static void check(boolean expected,boolean actual,String reason){if(expected!=actual)throw new AssertionError(reason);}
 public static void main(String[] args){
  check(true,FrameFreshnessGate.shouldDecode(1000,0,3000,false),"initial frame");
  check(false,FrameFreshnessGate.shouldDecode(1000,700,2000,false),"recent frame");
  check(true,FrameFreshnessGate.shouldDecode(1500,700,2000,false),"refresh before 1500ms deadline");
  check(false,FrameFreshnessGate.shouldDecode(1500,700,2000,true),"active analysis owns its frame");
  check(true,FrameFreshnessGate.shouldDecode(2100,700,2000,true),"scheduled decode takes priority");
  check(false,FrameFreshnessGate.shouldDecode(700,900,2000,false),"future frame clock mismatch");
  System.out.println("VirtualFreshnessGateTest: passed");
 }
}
