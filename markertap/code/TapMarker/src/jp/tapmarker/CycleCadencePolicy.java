package jp.tapmarker;

/**
 * Adaptive follow-up cadence for two known mini-game automata.
 *
 * Only shorten scanning AFTER entry to a transient, evidence-bearing phase:
 * the established two-distinct-frame rule, receipt validation, HOME/CM
 * escalation windows and input cooldowns are untouched. A slow last scan
 * automatically returns to the legacy rate rather than overload two displays.
 */
final class CycleCadencePolicy {
  static final long FOLLOWUP_WINDOW_MS=4000L;
  private static final String[] USAGI={
    "classify","clear","open-missions","mission-tap","missions",
    "claim-wait","reward","offline","bonus","use","use-wait","verify"
  };
  private static final String[] KAITAKU={
    "check","verify","mission-opening","missions","claim-wait",
    "upgrade-wait","control-wait"
  };

  static boolean followup(String pkg,String state,long enteredAt,long now,boolean proofPending){
    if(pkg==null||state==null||enteredAt<0||now<enteredAt||
       now-enteredAt>FOLLOWUP_WINDOW_MS)return false;
    String[] phases;
    String name;
    if(UsagiPreset.APP.equals(pkg)&&state.startsWith(UsagiPreset.PREFIX)){
      phases=USAGI;name=state.substring(UsagiPreset.PREFIX.length());
    }else if(KaitakuPreset.APP.equals(pkg)&&state.startsWith(KaitakuPreset.P)){
      phases=KAITAKU;name=state.substring(KaitakuPreset.P.length());
    }else return false;
    // Never accelerate advertising, indefinite unknowns or main gameplay.
    if(name.equals("main")||name.equals("ad")||name.equals("unknown")||
       name.equals("error")||name.equals("account"))return false;
    for(String phase:phases)if(phase.equals(name))return true;
    return proofPending&&name.equals("reward");
  }

  static long interval(PerformancePolicy.Profile profile,long lastCost,
                       int windows,int cores,String pkg,String state,
                       long enteredAt,long now,boolean proofPending){
    long normal=PerformancePolicy.scanInterval(profile,lastCost,windows,cores);
    if(!followup(pkg,state,enteredAt,now,proofPending))return normal;
    // Respect the renderer's natural decode interval and one half of each
    // work period for drawing/input. CPU saturation cancels any speedup.
    long floor=Math.max(profile.decodeMs,(profile.scanMs*3L+4L)/5L);
    long workerBound=Math.max(0,lastCost)*2L;
    return Math.min(normal,Math.max(floor,workerBound));
  }

  private CycleCadencePolicy(){}
}
