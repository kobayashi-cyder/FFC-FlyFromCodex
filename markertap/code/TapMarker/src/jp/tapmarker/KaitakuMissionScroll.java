package jp.tapmarker;

import java.util.Map;

/** At most one swipe through a verified daily-mission list before leaving it. */
final class KaitakuMissionScroll {
  static final String RULE=KaitakuPreset.P+"missions-mission-scan";
  static final String REGION=KaitakuPreset.P+"mission-scan";

  static void install(PhaseEngine e){
    if(e.rule(RULE)!=null)return;
    KaitakuPreset.region(e,REGION,"ミッション一覧を下へ確認",new float[]{.30f,.30f,.48f,.82f});
    KaitakuPreset.match(e,"missions","mission-scan",PhaseEngine.CURVE,"missions");
    e.rule(RULE).duration=360;
  }
  static boolean scanned(PhaseEngine e){return RULE.equals(e.lastCompletedRule);}
  static float[] path(Map<String,float[]> samples,int w,int h){
    float[] v=samples.get(AdaptiveLayout.VIEWPORT);
    if(v==null||v.length!=6||v[4]!=w||v[5]!=h)return null;
    float x=v[0]+v[2]*.39f;
    return new float[]{x,v[1]+v[3]*.79f,x,v[1]+v[3]*.31f};
  }
  private KaitakuMissionScroll(){}
}
