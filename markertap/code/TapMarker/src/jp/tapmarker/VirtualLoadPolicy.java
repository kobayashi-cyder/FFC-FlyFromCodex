package jp.tapmarker;

/** Scheduling only: all displays and their input lanes remain alive. */
final class VirtualLoadPolicy {
 static final int DEFAULT_LIMIT=2,MAX_LIMIT=4;
 static int limit(int saved){return saved>=2&&saved<=MAX_LIMIT?saved:DEFAULT_LIMIT;}
 static boolean allows(int count,int saved){return count>0&&count<=limit(saved);}
 static long creationDelay(PerformancePolicy.Profile profile,int index,int count){
  return Math.max(PerformancePolicy.startupDelay(profile,index),Math.max(0,index)*(count>=3?300L:120L));
 }
 static long scanOffset(int index,int count,long interval){
  return Math.max(0,interval)*Math.max(0,Math.min(index,Math.max(1,count)-1))/Math.max(1,count);
 }
 static long decodeInterval(PerformancePolicy.Profile profile,int count,boolean running,boolean minimized){
  if(!running)return minimized?1000:Math.max(300,profile.decodeMs);
  return Math.max(profile.decodeMs,60L*Math.max(1,count));
 }
 /** Next strictly future phase. A late frame skips missed ticks instead of catching up. */
 static long nextDecodeAt(long now,long interval,int index,int count){
  long period=Math.max(1,interval),phase=scanOffset(index,count,period);
  long next=now-now%period+phase;
  return next>now?next:next+period;
 }
 private VirtualLoadPolicy(){}
}
