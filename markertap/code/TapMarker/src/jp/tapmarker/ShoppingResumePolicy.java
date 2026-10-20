package jp.tapmarker;
import java.util.*;

/** An observed resume label closes the bonus card without starting its optional CM. */
final class ShoppingResumePolicy {
 static final String REGION=ShoppingPreset.P+"resume-without-cm";
 static final float[] LABEL={.595f,.697f,.725f,.722f};
 static boolean apply(PhaseEngine e,Map<String,float[]> refs){
  if(e.windowRelative||!e.phases.containsKey(ShoppingPreset.P+"main")||!refs.containsKey("shopping-control-6717-resume-native"))return false;
  boolean changed=false;
  if(!e.regions.containsKey(REGION)){PhaseEngine.Region r=new PhaseEngine.Region();r.id=REGION;r.name="CMを見ずこのままはじめる";r.bounds=LABEL.clone();e.regions.put(r.id,r);changed=true;}
  for(PhaseEngine.Phase phase:e.phases.values()){
   if(!phase.id.startsWith(ShoppingPreset.P)||phase.terminal)continue;
   String id=phase.id+"-resume-without-cm";if(e.rule(id)!=null)continue;
   PhaseEngine.Watch w=new PhaseEngine.Watch();w.id=id;w.name="このままはじめるを確認して1回タップ";w.regionId=REGION;w.reference=new float[]{1};w.threshold=0;w.condition=PhaseEngine.MATCH;w.action=PhaseEngine.CENTER;w.nextPhase=ShoppingPreset.P+"verify";w.waitMs=0;phase.watches.add(w);changed=true;
  }
  return changed;
 }
 private ShoppingResumePolicy(){}
}
