package jp.tapmarker;
import java.util.*;

/** Only positively observed home pixels can replenish an unresolved recovery episode. */
final class RecoveryEvidence {
 static boolean home(PhaseEngine e,Map<String,float[]> samples){
  if(e.windowRelative||GamePopupEvidence.present(samples)||samples.containsKey(GamePopupEvidence.FOOTER_EXIT))return false;
  float[] error=samples.get(FarmAdErrorEvidence.KEY);if(error!=null&&error.length==1&&error[0]==1)return false;
  for(String id:new String[]{AdCloseAutomaton.LEFT,AdCloseAutomaton.RIGHT}){float[] value=samples.get(id);if(value!=null&&value.length==1&&value[0]==1)return false;}
  String prefix=AdInterruption.prefix(e.current);if(prefix.isEmpty())return false;
  String[] homes=prefix.equals(UsagiPreset.PREFIX)?new String[]{"return"}:prefix.equals(CoinPreset.P)?new String[]{"verify-home","verify-fever"}:prefix.equals(BoxPreset.P)||prefix.equals(PoipoiPreset.P)?new String[]{"verify-game"}:new String[]{"verify-home"};
  boolean found=false;for(String id:homes){PhaseEngine.Watch w=e.rule(prefix+id);if(w!=null&&w.condition==PhaseEngine.MATCH&&w.reference!=null&&samples.get(w.regionId)!=null&&w.distance(samples.get(w.regionId))<=w.threshold&&e.guardsMatch(w,samples)){found=true;break;}}
  if(!found)return false;
  String[] dialogs=prefix.equals(PoipoiPreset.P)?new String[]{"offline","bonus","reward","missions","full","notice","decline"}:prefix.equals(BoxPreset.P)?new String[]{"notice","reward","bonus","title","claim"}:new String[0];
  for(String key:dialogs){PhaseEngine.Watch w=e.rule(prefix+"main-"+key);if(w!=null&&w.reference!=null&&samples.get(w.regionId)!=null&&w.distance(samples.get(w.regionId))<=w.threshold&&e.guardsMatch(w,samples))return false;}
  return true;
 }
 private RecoveryEvidence(){}
}
