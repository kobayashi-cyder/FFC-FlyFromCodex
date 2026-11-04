package jp.tapmarker;

/** Limits repeated mission visits without freezing the next reward cycle.
 * This controls *when to inspect*, not whether to tap or accept a reward.
 */
final class KaitakuMissionCadence {
  static final long INITIAL_MS=10000L;
  static final long AFTER_PAGE_MS=12000L;
  static final long AFTER_CLAIM_MS=6000L;
  static long initial(long now){return now+INITIAL_MS;}
  static long entered(String state,long now,long prior){
    if(state==null)return prior;
    if(state.equals(KaitakuPreset.P+"mission-opening")||
       state.equals(KaitakuPreset.P+"missions"))return now+AFTER_PAGE_MS;
    if(state.equals(KaitakuPreset.P+"claim-wait"))
      return Math.min(prior,now+AFTER_CLAIM_MS);
    return prior;
  }
  private KaitakuMissionCadence(){}
}
