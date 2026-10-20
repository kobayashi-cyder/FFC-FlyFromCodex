package jp.tapmarker;

/** Input timing is independent of image quality and the selected performance profile. */
public final class TapInputPolicy {
 public static long pressMillis(String model,long requested){return Math.max(1,requested);}
 public static long nextDelay(String model,long interval,long held){return Math.max(0,Math.max(1,interval)-held);}
 public static final class Retry {
  private int failures;
  public void completed(){failures=0;}
  public long failed(){return ++failures<=3?250L<<(failures-1):-1;}
  public int failures(){return failures;}
 }
 private TapInputPolicy(){}
}
