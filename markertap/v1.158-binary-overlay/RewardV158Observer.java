package jp.tapmarker;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Binary-only v1.158 score observer. Never drives taps, force-stops, or
 * changes PanelModeController. Existing v1.157 recovery is the authority.
 *
 * No cycle or pt credits are synthesized without verified reward evidence;
 * this first binary graft only measures positive-ad frame dwell.
 */
final class RewardV158Observer {
  private static final Map<Object,RewardEvaluationConnectome> LANES=
      Collections.synchronizedMap(new WeakHashMap<Object,RewardEvaluationConnectome>());
  static void observe(Object lane,long capturedAt,long now,boolean positiveAd){
    if(lane==null)return;
    RewardEvaluationConnectome e;
    synchronized(LANES){
      e=LANES.get(lane);
      if(e==null){e=new RewardEvaluationConnectome();LANES.put(lane,e);}
    }
    e.observeAd(capturedAt,now,positiveAd);
  }
  static String report(Object lane){
    if(lane==null)return "評価なし";
    synchronized(LANES){
      RewardEvaluationConnectome e=LANES.get(lane);
      return e==null?"未観測":e.report();
    }
  }
  private RewardV158Observer(){}
}
