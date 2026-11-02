package jp.tapmarker;

/**
 * Virtual-display recognition cadence. Keeps the per-display frame/input
 * verification in SplitPilot and VirtualWindows; never sends a tap itself.
 * Two displays remain the resource ceiling, not a reason to duplicate work.
 */
final class VirtualFastWait {
  /** User-requested two-display ceiling, independent of stale saved settings. */
  static int limit(int saved){return 2;}
  static long scanInterval(PerformancePolicy.Profile profile,long lastCost,int windows,int cores) {
    long regular=PerformancePolicy.scanInterval(profile,lastCost,windows,cores);
    // Low-memory devices retain their proven profile; faster scanning is not
    // worth stale-frame and frame-worker queue accumulation on those devices.
    if(profile.mode==PerformancePolicy.LIGHT)return regular;
    long floor=windows>=2?120:90;
    long goal=Math.max(floor,Math.min(300,regular/2));
    // Do not schedule new recognition faster than it can be consumed.
    long work=Math.max(0,lastCost);
    long effectiveWork=Math.min(1200,work*(windows>=2&&cores<=4?2:1)+25);
    return Math.min(1200,Math.max(goal,effectiveWork));
  }
  private VirtualFastWait(){}
}
