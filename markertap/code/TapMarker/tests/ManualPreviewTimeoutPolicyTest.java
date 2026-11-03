package jp.tapmarker;
public final class ManualPreviewTimeoutPolicyTest{
 static int checks;
 static void test(boolean ok,String msg){checks++;if(!ok)throw new AssertionError(msg);}
 public static void main(String[]args){
  final long t=20_000;
  test(!ManualPreviewTimeoutPolicy.expired(0,t+20_000,false,1,1),"unstarted");
  test(!ManualPreviewTimeoutPolicy.expired(t,t+11_999,false,1,1),"before recheck");
  test(ManualPreviewTimeoutPolicy.expired(t,t+12_000,false,1,1),"on timeout");
  test(ManualPreviewTimeoutPolicy.expired(t,t+30_000,false,1,1),"well past timeout");
  test(!ManualPreviewTimeoutPolicy.expired(t,t+30_000,true,1,1),"current touch");
  test(!ManualPreviewTimeoutPolicy.expired(t,t+30_000,false,2,1),"different run generation");
  test(!ManualPreviewTimeoutPolicy.expired(t,t-1,false,1,1),"clock reversal");
  test(ManualPreviewTimeoutPolicy.recoverable(true,true,false,false),"active lane");
  test(!ManualPreviewTimeoutPolicy.recoverable(false,true,false,false),"app not running");
  test(!ManualPreviewTimeoutPolicy.recoverable(true,false,false,false),"nonvirtual lane");
  test(!ManualPreviewTimeoutPolicy.recoverable(true,true,true,false),"intentional stop");
  test(!ManualPreviewTimeoutPolicy.recoverable(true,true,false,true),"closed display");
  System.out.println("ManualPreviewTimeoutPolicyTest: "+checks+" checks passed");
 }
}
