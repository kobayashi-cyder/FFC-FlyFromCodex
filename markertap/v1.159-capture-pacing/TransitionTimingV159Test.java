package jp.tapmarker;
public final class TransitionTimingV159Test {
  private static void check(boolean b,String message){if(!b)throw new AssertionError(message);}
  public static void main(String[] args){
    check(TransitionTiming.SCREENSHOT_INTERVAL_MS==600,"interval");
    check(TransitionTiming.remaining(-1,0)==0,"first request");
    check(TransitionTiming.remaining(1000,1001)==599,"wait");
    check(TransitionTiming.remaining(1000,1599)==1,"one ms left");
    check(TransitionTiming.remaining(1000,1600)==0,"capture allowed");
    System.out.println("PASS v1.159 request pacing host checks");
  }
}
