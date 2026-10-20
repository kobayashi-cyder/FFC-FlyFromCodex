package jp.tapmarker;
import java.util.Map;
/** An intent/retry belongs to one state entry. Observation remains live while it waits. */
final class RecoveryRequest {
 private int generation,run;private String state="";private long epoch;private boolean pending;private PhaseEngine owner;
 int begin(PhaseEngine e,int token){generation++;run=token;owner=e;state=e.current;epoch=e.epoch;pending=true;return generation;}
 boolean pending(){return pending;}
 boolean valid(PhaseEngine e,int token,int request){return pending&&owner==e&&generation==request&&run==token&&state.equals(e.current)&&epoch==e.epoch;}
 boolean current(PhaseEngine e,int token){return valid(e,token,generation);}
 void cancel(){pending=false;generation++;}
 static boolean visibleBranch(PhaseEngine e,Map<String,float[]> samples){
  PhaseEngine.Phase p=e.phases.get(e.current);if(p==null)return false;
  for(PhaseEngine.Watch w:p.watches)if(w.condition==PhaseEngine.MATCH&&w.action!=PhaseEngine.RETURN_APP&&w.reference!=null&&e.eligible(w)&&w.distance(samples.get(w.regionId))<=w.threshold&&e.guardsMatch(w,samples))return true;
  return false;
 }
 static void release(PhaseEngine e){PhaseEngine.Phase p=e.phases.get(e.current);if(p!=null)for(PhaseEngine.Watch w:p.watches)if(w.action==PhaseEngine.RETURN_APP)e.cancelIfCurrent(w,e.current,e.epoch);}
}
