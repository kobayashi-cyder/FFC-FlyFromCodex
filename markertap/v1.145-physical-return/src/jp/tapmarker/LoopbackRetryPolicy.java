package jp.tapmarker;
/**
 * Pure Java guard for auto reconnect to a cached local ADB port.
 * Once two offline attempts have happened in 15 minutes, do not spin
 * autonomously again until the cooldown expires or the user manually retries.
 */
final class LoopbackRetryPolicy {
 static final long WINDOW_MS = 15L*60L*1000L;
 static final int MAX_AUTO_ATTEMPTS = 2;
 static int activeCount(long now,long started,int count) {
  if(count<=0||started<=0||now<started||now-started>=WINDOW_MS)return 0;
  return Math.min(count,MAX_AUTO_ATTEMPTS);
 }
 static boolean autoAllowed(long now,long started,int count) {
  return activeCount(now,started,count)<MAX_AUTO_ATTEMPTS;
 }
 static int nextCount(long now,long started,int count){
  return activeCount(now,started,count)+1;
 }
 private LoopbackRetryPolicy(){}
}
