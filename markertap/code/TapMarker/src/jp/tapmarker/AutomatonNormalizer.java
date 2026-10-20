package jp.tapmarker;
import java.util.*;

/** Conservative graph reduction. Identity-sensitive preset and history nodes stay distinct. */
public final class AutomatonNormalizer {
 private AutomatonNormalizer(){}
 public static final class Result {public int states,rules;public int removed(){return states+rules;}}
 static boolean preset(String id){return id.startsWith(UsagiPreset.PREFIX)||id.startsWith(FarmPreset.P)||id.startsWith(CoinPreset.P)||id.startsWith(ShoppingPreset.P)||id.startsWith(BoxPreset.P)||id.startsWith(PoipoiPreset.P)||id.startsWith("ad-close-");}
 /** Normalize exact evidence after layout/recovery installation. Keep every live
  * identity, timer and observation: external controllers also address rule IDs.
  * No pool is shared with another application or the editable configuration.
  */
 public static void normalizeRuntime(PhaseEngine engine){ReferencePool.share(engine);}
 static boolean equalRule(PhaseEngine.Watch a,PhaseEngine.Watch b){
  return Objects.equals(a.name,b.name)&&a.regionId.equals(b.regionId)&&a.nextPhase.equals(b.nextPhase)
   &&a.historyRule.equals(b.historyRule)&&a.historyPhase.equals(b.historyPhase)
   &&a.condition==b.condition&&a.action==b.action&&a.priority==b.priority&&a.waitMs==b.waitMs
   &&a.timeoutMs==b.timeoutMs&&a.stableMs==b.stableMs&&a.duration==b.duration
   &&a.fallback==b.fallback&&a.pairedReference==b.pairedReference&&a.threshold==b.threshold&&a.relativeCurve==b.relativeCurve
   &&Arrays.equals(a.reference,b.reference)&&Arrays.equals(a.curve,b.curve)&&Arrays.equals(a.touchPoint,b.touchPoint)
   &&Arrays.equals(a.markerPoints,b.markerPoints)&&Arrays.equals(a.markerIds,b.markerIds)
   &&Arrays.equals(a.guardIds,b.guardIds)&&Arrays.deepEquals(a.guardReferences,b.guardReferences)
   &&a.runtimeBlocks==b.runtimeBlocks&&a.runtimeControlEvidence==b.runtimeControlEvidence
   &&a.runtimeAlternativeThreshold==b.runtimeAlternativeThreshold
   &&Arrays.equals(a.runtimeAlternative,b.runtimeAlternative)&&Arrays.deepEquals(a.runtimePairedReferences,b.runtimePairedReferences);
 }
 static boolean safeState(PhaseEngine.Phase p,Set<String> historyStates,Set<String> historyRules){
  if(preset(p.id)||historyStates.contains(p.id))return false;
  for(PhaseEngine.Watch w:p.watches)if(preset(w.id)||historyRules.contains(w.id))return false;
  return true;
 }
 static boolean equalState(PhaseEngine.Phase a,PhaseEngine.Phase b){
  if(!Objects.equals(a.name,b.name)||a.markersDuring!=b.markersDuring||a.learnedDuring!=b.learnedDuring||a.terminal!=b.terminal||a.watches.size()!=b.watches.size())return false;
  for(int i=0;i<a.watches.size();i++){
   PhaseEngine.Watch x=a.watches.get(i),y=b.watches.get(i);
   // Turning a state change into a self-loop would change timer resets and completion epochs.
   if(x.nextPhase.equals(a.id)||x.nextPhase.equals(b.id)||y.nextPhase.equals(a.id)||y.nextPhase.equals(b.id)||!equalRule(x,y))return false;
  }
  return true;
 }
 static int collapseRules(PhaseEngine e,Set<String> historyRules){
  int removed=0;
  for(PhaseEngine.Phase p:e.phases.values())if(!preset(p.id))for(int i=0;i<p.watches.size();i++){
   PhaseEngine.Watch first=p.watches.get(i);if(preset(first.id)||historyRules.contains(first.id))continue;
   for(int j=i+1;j<p.watches.size();){PhaseEngine.Watch other=p.watches.get(j);if(!preset(other.id)&&!historyRules.contains(other.id)&&equalRule(first,other)){p.watches.remove(j);removed++;}else j++;}
  }
  return removed;
 }
 public static Result normalize(PhaseEngine e){
  Result result=new Result();
  // A repeated record with the same identity and payload has one unambiguous meaning.
  // Different payloads or duplicate identities across states still require explicit repair.
  for(PhaseEngine.Phase p:e.phases.values())for(int i=0;i<p.watches.size();i++)for(int j=i+1;j<p.watches.size();){
   PhaseEngine.Watch first=p.watches.get(i),other=p.watches.get(j);
   if(Objects.equals(first.id,other.id)&&equalRule(first,other)){p.watches.remove(j);result.rules++;}else j++;
  }
  if(!AutomatonGraph.relationships(e).isEmpty()){if(result.removed()>0)e.clearHistory();return result;}
  Set<String> historyStates=new HashSet<>(),historyRules=new HashSet<>();
  for(PhaseEngine.Phase p:e.phases.values())for(PhaseEngine.Watch w:p.watches){historyStates.add(w.historyPhase);historyRules.add(w.historyRule);}
  int previous;
  do{previous=result.removed();result.rules+=collapseRules(e,historyRules);
  List<PhaseEngine.Phase> phases=new ArrayList<>(e.phases.values());
  for(int i=0;i<phases.size();i++){
   PhaseEngine.Phase first=phases.get(i);if(!e.phases.containsKey(first.id)||!safeState(first,historyStates,historyRules))continue;
   for(int j=i+1;j<phases.size();j++){
    PhaseEngine.Phase other=phases.get(j);
    if(!e.phases.containsKey(other.id)||other.id.equals(e.initial)||other.id.equals(e.current)||!safeState(other,historyStates,historyRules)||!equalState(first,other))continue;
    for(PhaseEngine.Phase p:e.phases.values())for(PhaseEngine.Watch w:p.watches)if(w.nextPhase.equals(other.id))w.nextPhase=first.id;
    e.phases.remove(other.id);result.states++;
   }
  }
  }while(result.removed()!=previous);
  ReferencePool.share(e);if(result.removed()>0)e.clearHistory();return result;
 }
}
