package jp.tapmarker;
public final class TransitionTiming {
 public static final long SCREENSHOT_INTERVAL_MS=350;
 public static long remaining(long lastRequest,long now){return lastRequest<0?0:Math.max(0,SCREENSHOT_INTERVAL_MS-Math.max(0,now-lastRequest));}
}
