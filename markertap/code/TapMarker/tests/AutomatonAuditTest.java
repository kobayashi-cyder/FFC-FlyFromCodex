package jp.tapmarker;
import java.io.*;import java.util.*;
/** Inspect shipped graphs and the policies that prevent ambiguous operations. */
public final class AutomatonAuditTest {
 static int checks;
 static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 static boolean predicate(PhaseEngine.Watch a,PhaseEngine.Watch b){
  if(a.condition!=b.condition||a.fallback!=b.fallback||!a.historyRule.equals(b.historyRule)||!a.historyPhase.equals(b.historyPhase)||!Arrays.equals(a.guardIds,b.guardIds)||!Arrays.deepEquals(a.guardReferences,b.guardReferences))return false;
  if(a.condition==PhaseEngine.TIMEOUT)return a.timeoutMs==b.timeoutMs;
  return a.reference!=null&&b.reference!=null&&a.regionId.equals(b.regionId)&&a.threshold==b.threshold&&a.stableMs==b.stableMs&&a.pairedReference==b.pairedReference&&Arrays.equals(a.reference,b.reference);
 }
 static Set<String> reachable(PhaseEngine e){Set<String> seen=new LinkedHashSet<>();ArrayDeque<String> todo=new ArrayDeque<>();todo.add(e.initial);while(!todo.isEmpty()){String id=todo.remove();if(!seen.add(id))continue;PhaseEngine.Phase p=e.phases.get(id);if(p.terminal)continue;for(PhaseEngine.Watch w:p.watches)if(w.action!=PhaseEngine.STOP&&!w.nextPhase.isEmpty())todo.add(w.nextPhase);}return seen;}
 public static void main(String[] args)throws Exception{
  for(String name:new String[]{"usagi","farm","coin","shopping"}){
   Map<String,float[]> refs=VirtualPresetProfileTest.canonical("tests/audit-"+name+"-references.bin");
   PhaseEngine e=new PhaseEngine();if(name.equals("usagi"))UsagiPreset.install(e,refs);else if(name.equals("farm"))FarmPreset.install(e,refs);else if(name.equals("coin"))CoinPreset.install(e,refs);else ShoppingPreset.install(e,refs);
   check(AutomatonGraph.relationships(e).isEmpty(),name+" references valid");
   check(e.validate().isEmpty(),name+" conditions and action inputs complete: "+e.validate());
   Set<String> reached=reachable(e);int edges=0,duplicates=0;
   for(PhaseEngine.Phase p:e.phases.values()){
    for(PhaseEngine.Watch w:p.watches){edges++;check(w.waitMs>=0&&w.timeoutMs>=0&&w.stableMs>=0,name+" nonnegative timing");check(Float.isFinite(w.threshold)&&w.threshold>=0,name+" finite nonnegative comparison tolerance");}
    for(int i=0;i<p.watches.size();i++)for(int j=i+1;j<p.watches.size();j++){
     PhaseEngine.Watch a=p.watches.get(i),b=p.watches.get(j);
     if(predicate(a,b)){duplicates++;check(AutomatonGraph.sameEffect(e,p,a,b),name+" identical predicates cannot request contradictory operations in "+p.name+": "+a.id+" and "+b.id);}
    }
   }
   PhaseEngine edited=e.runtimeCopy();for(PhaseEngine.Phase p:edited.phases.values())for(int i=0;i<p.watches.size();i++)p.watches.set(i,AutomatonEdits.draft(p.watches.get(i)));
   check(AutomatonGraph.sameConfiguration(e,edited),name+" an unchanged editor round trip remains recognizable as a factory configuration");
   ArrayList<String> unreachable=new ArrayList<>();for(PhaseEngine.Phase p:e.phases.values())if(!reached.contains(p.id)){check(!AutomatonGraph.detachedRole(p).isEmpty(),name+" every detached standard state has a defined controller or stop role: "+p.id);unreachable.add(p.id);}
   System.out.println("AUDIT "+name+" states="+e.phases.size()+" arrows="+edges+" reachable="+reached.size()+" identicalPredicates="+duplicates+" unreachable="+unreachable);
  }
  PhaseEngine.Watch binary=new PhaseEngine.Watch();binary.reference=new float[]{1};binary.threshold=0;
  check(binary.distance(new float[]{1})<=binary.threshold,"zero tolerance accepts the exact binary event");
  check(binary.distance(new float[]{0})>binary.threshold,"zero tolerance rejects the absent binary event");
  // Priorities must not turn contradictory observations into an arbitrary button press.
  PhaseEngine e=new PhaseEngine();e.strictRuntime=true;PhaseEngine.Phase p=new PhaseEngine.Phase();p.id="A";p.name="A";e.phases.put(p.id,p);e.initial=e.current=p.id;
  PhaseEngine.Watch a=new PhaseEngine.Watch(),b=new PhaseEngine.Watch();a.id="one";b.id="two";a.condition=b.condition=PhaseEngine.TIMEOUT;a.timeoutMs=b.timeoutMs=0;a.action=PhaseEngine.BACK;b.action=PhaseEngine.RETURN_APP;a.priority=-100;b.priority=100;p.watches.add(a);p.watches.add(b);e.enter("A",0);e.observe(Collections.emptyMap(),1);
  check(e.poll()==null&&e.diagnostic.contains("複数"),"conflicting effects remain held despite different priorities");
  check(!GameTarget.dailyOrderSupported(new String[]{GameTarget.FARM,GameTarget.BALLOON}),"unverified within-host handoff is not authorized by the editor");
  System.out.println("AutomatonAuditTest: "+checks+" checks passed; static graph consistency and conflict policy, not a device success-rate test");
 }
}
