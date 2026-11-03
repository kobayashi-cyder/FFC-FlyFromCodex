package jp.tapmarker;
public final class LoopbackRetryPolicyTest {
 private static int n;
 private static void check(boolean b,String reason){n++;if(!b)throw new AssertionError(reason);}
 public static void main(String[] args){
  long now=1_000_000L,start=now-10_000L;
  check(LoopbackRetryPolicy.activeCount(now,0,0)==0,"no attempts");
  check(LoopbackRetryPolicy.autoAllowed(now,start,0),"first attempt allowed");
  check(LoopbackRetryPolicy.autoAllowed(now,start,1),"second allowed");
  check(!LoopbackRetryPolicy.autoAllowed(now,start,2),"stop repeated attempt");
  check(!LoopbackRetryPolicy.autoAllowed(now,start,99),"count bounded");
  check(LoopbackRetryPolicy.activeCount(now,start,99)==2,"saturating");
  check(LoopbackRetryPolicy.activeCount(now,start,1)==1,"first counted");
  check(LoopbackRetryPolicy.activeCount(now,now+1,1)==0,"time moved backward");
  check(LoopbackRetryPolicy.activeCount(now,0,1)==0,"no start");
  check(LoopbackRetryPolicy.activeCount(now,now-LoopbackRetryPolicy.WINDOW_MS,2)==0,"expires exactly at window");
  check(LoopbackRetryPolicy.autoAllowed(now,now-LoopbackRetryPolicy.WINDOW_MS,2),"retry after window");
  check(LoopbackRetryPolicy.nextCount(now,start,0)==1,"first increment");
  check(LoopbackRetryPolicy.nextCount(now,start,1)==2,"second increment");
  check(LoopbackRetryPolicy.nextCount(now,start,2)==3,"cannot bypass threshold by rollover");
  check(LoopbackRetryPolicy.nextCount(now,start,99)==3,"clamp post-limit");
  check(LoopbackRetryPolicy.nextCount(now,now-LoopbackRetryPolicy.WINDOW_MS,2)==1,"restart budget after window");
  System.out.println("LoopbackRetryPolicyTest: "+n+" checks passed");
 }
}
