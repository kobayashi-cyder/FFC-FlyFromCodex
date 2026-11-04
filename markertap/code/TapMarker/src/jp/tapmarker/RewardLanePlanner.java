package jp.tapmarker;

/**
 * Bounded, evidence-driven lane selection for sequential physical/virtual runs.
 *
 * Uses ONLY confirmed reward cycles/point deltas and observed CM dwell in
 * RewardEvaluationConnectome.score(). Speculative payout, ad closure, a tap,
 * and an unconfirmed reward UI never increase this value.
 *
 * Fail-safe scheduling:
 *  - No learned reward evidence: ordinary fair round-robin observation.
 *  - Unconfirmed receipt: briefly prioritize verification, never credit it.
 *  - Once rewarded: favor better score per observed scan with strict
 *    max-visit skew, so an uncertain lane cannot be starved.
 *  - Disabled/stopped lanes are never chosen.
 *
 * This schedules existing registered automata ONLY. It neither changes game
 * actions nor accelerates, skips or falsifies any required advertisement.
 */
final class RewardLanePlanner {
  private int[] visits=new int[0];
  private int cursor=0;
  private static final int MAX_VISIT_SKEW=2;

  void reset(){visits=new int[0];cursor=0;}

  int select(boolean[] eligible,boolean[] verifying,long[] cycles,double[] objective){
    if(eligible==null||verifying==null||cycles==null||objective==null
        ||eligible.length==0||eligible.length!=verifying.length
        ||eligible.length!=cycles.length||eligible.length!=objective.length)return -1;
    if(visits.length!=eligible.length){visits=new int[eligible.length];cursor=0;}
    int min=Integer.MAX_VALUE,available=0;
    boolean anyProven=false;
    for(int i=0;i<eligible.length;i++){
      if(!eligible[i])continue;
      available++;
      min=Math.min(min,visits[i]);
      if(cycles[i]>0)anyProven=true;
    }
    if(available==0)return -1;
    int chosen=-1;
    double best=Double.NEGATIVE_INFINITY;
    for(int k=0;k<eligible.length;k++){
      int i=(cursor+k)%eligible.length;
      if(!eligible[i]||visits[i]>=min+MAX_VISIT_SKEW)continue;
      // Per-scan confirmed objective, clipped so one outlier cannot
      // dominate observation of a different eligible lane.
      double value=0;
      if(anyProven&&Double.isFinite(objective[i]))
        value=Math.max(-200,Math.min(200,
            objective[i]/Math.max(1,visits[i])));
      value-=200.0*(visits[i]-min);
      if(verifying[i])value+=1000;
      if(value>best){best=value;chosen=i;}
    }
    if(chosen<0)return -1;
    if(visits[chosen]>=Integer.MAX_VALUE-2){
      int floor=min;
      for(int i=0;i<visits.length;i++)visits[i]=Math.max(0,visits[i]-floor);
    }
    visits[chosen]++;
    cursor=(chosen+1)%eligible.length;
    return chosen;
  }
}
