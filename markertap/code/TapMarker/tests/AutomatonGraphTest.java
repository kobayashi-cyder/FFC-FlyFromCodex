package jp.tapmarker;
import java.util.*;
public final class AutomatonGraphTest {
 static int checks;static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
 static PhaseEngine.Phase phase(PhaseEngine e,String id){PhaseEngine.Phase p=new PhaseEngine.Phase();p.id=id;p.name=id;e.phases.put(id,p);return p;}
 static PhaseEngine.Watch timer(String id,String next){PhaseEngine.Watch w=new PhaseEngine.Watch();w.id=id;w.action=PhaseEngine.WAIT;w.condition=PhaseEngine.TIMEOUT;w.timeoutMs=0;w.waitMs=0;w.nextPhase=next;return w;}
 static void rejects(Runnable task,String message){try{task.run();throw new AssertionError(message);}catch(IllegalArgumentException expected){checks++;}}
 static PhaseEngine model(){PhaseEngine e=new PhaseEngine();phase(e,"A");phase(e,"B");phase(e,"C");e.initial=e.current="A";return e;}
 public static void main(String[] args){
  PhaseEngine e=model();PhaseEngine.Phase a=e.phases.get("A");PhaseEngine.Watch old=timer("first","B"),second=timer("second","C");a.watches.add(old);a.watches.add(second);final PhaseEngine first=e;final PhaseEngine.Phase firstA=a;
  PhaseEngine.Watch changed=PhaseEngine.copyWatch(old);changed.nextPhase="C";AutomatonGraph.replaceWatch(e,a,old,changed);
  check(a.watches.get(0)==changed&&a.watches.get(1)==second,"editing preserves both stable identity and sibling arrows");
  check(e.rule(old.id)==changed&&AutomatonGraph.relationships(e).isEmpty(),"stable rule lookup resolves the edited arrow");
  PhaseEngine other=model();rejects(()->AutomatonGraph.replaceWatch(other,firstA,changed,PhaseEngine.copyWatch(changed)),"a same-named state in another app cannot accept this edit");
  check(other.phases.get("A").watches.isEmpty()&&a.watches.size()==2,"a stale app callback changes neither graph");
  rejects(()->AutomatonGraph.replaceWatch(first,firstA,old,PhaseEngine.copyWatch(old)),"a replaced arrow cannot be edited through its old object");
  PhaseEngine.Watch missing=PhaseEngine.copyWatch(changed);missing.nextPhase="deleted";rejects(()->AutomatonGraph.replaceWatch(first,firstA,changed,missing),"a missing target does not become a self-loop");check(a.watches.get(0)==changed&&changed.nextPhase.equals("C"),"a failed edit retains the previous relationship");
  PhaseEngine.Watch duplicate=timer("second","B");rejects(()->AutomatonGraph.replaceWatch(first,firstA,null,duplicate),"rule identities remain globally unique inside one app");
  rejects(()->AutomatonGraph.removePhase(first,first.phases.get("C")),"referenced destination cannot be removed");
  check(AutomatonGraph.report(e).contains("未接続：B")&&e.phases.containsKey("B"),"unconnected states are visible and preserved");
  e.strictRuntime=true;e.enter("A",0);e.observe(Collections.emptyMap(),0);PhaseEngine.Watch pending=e.poll();check(pending!=null,"equal-output branches form one deterministic decision");
  long epoch=e.epoch;AutomatonGraph.removeWatch(e,a,pending);check(!e.finishIfCurrent(pending,"A",epoch,1),"deleted arrow completion cannot transition");
  e=model();a=e.phases.get("A");a.watches.add(timer("one","B"));a.watches.add(timer("two","C"));e.strictRuntime=true;e.enter("A",0);e.observe(Collections.emptyMap(),0);check(e.poll()==null&&e.diagnostic.contains("複数"),"different destinations continue to hold as conflicting branches");
  e=model();AutomatonGraph.removePhase(e,e.phases.get("A"));check(e.initial.isEmpty()&&e.current.isEmpty(),"removing the start cannot select an unrelated state automatically");check(AutomatonGraph.report(e).contains("開始状態を選んで"),"the user is told to select the intended new start");
  e=model();a=e.phases.get("A");a.watches.add(timer("bad","missing"));check(AutomatonGraph.relationships(e).contains("A / bad：遷移先"),"relationship errors identify the affected source and arrow");
  e=model();a=e.phases.get("A");a.watches.add(timer("shared","B"));e.phases.get("B").watches.add(timer("shared","C"));check(AutomatonGraph.relationships(e).contains("重複"),"loaded duplicate identities cannot silently bind to the first arrow");
  e=model();a=e.phases.get("A");PhaseEngine.Region r=new PhaseEngine.Region();r.id="primary";r.name="primary";r.bounds=new float[]{.1f,.1f,.2f,.2f};e.regions.put(r.id,r);PhaseEngine.Watch guarded=timer("guarded","B");guarded.guardIds=new String[]{r.id};guarded.guardReferences=new float[0][];a.watches.add(guarded);check(AutomatonGraph.relationships(e).contains("対応数"),"guard-to-image correspondence is verified");
  PhaseEngine defaults=new PhaseEngine();UsagiPreset.install(defaults,new HashMap<String,float[]>());PhaseEngine authored=defaults.runtimeCopy();
  check(AutomatonGraph.sameConfiguration(defaults,authored),"an untouched older preset can be recognized");authored.phases.get(authored.initial).graphX+=100;authored.current=UsagiPreset.PREFIX+"verify";check(AutomatonGraph.sameConfiguration(defaults,authored),"drawing placement and current execution do not change logical ownership");
  authored.rule(UsagiPreset.PREFIX+"stage-zero").nextPhase=UsagiPreset.PREFIX+"missions";check(!AutomatonGraph.sameConfiguration(defaults,authored),"an edited relationship is protected from automatic preset upgrades");
  authored=defaults.runtimeCopy();authored.clearTransitions();check(!AutomatonGraph.sameConfiguration(defaults,authored),"cleared arrows are authored data, not missing preset content");
  authored=defaults.runtimeCopy();authored.initial=UsagiPreset.PREFIX+"missions";check(!AutomatonGraph.sameConfiguration(defaults,authored),"an explicitly changed start is retained");
  e=model();a=e.phases.get("A");e.windowRelative=true;PhaseEngine.Watch x=timer("x","B"),y=timer("y","B");x.action=y.action=PhaseEngine.CENTER;x.touchPoint=new float[]{.3f,.4f};y.touchPoint=new float[]{.7f,.4f};check(!AutomatonGraph.sameEffect(e,a,x,y),"equal target states do not merge different physical actions");
  y.touchPoint=x.touchPoint.clone();check(AutomatonGraph.sameEffect(e,a,x,y),"equivalent observed conditions can share the identical physical operation");y.action=PhaseEngine.STOP;check(!AutomatonGraph.sameEffect(e,a,x,y),"stop and continue cannot be collapsed");
  System.out.println("PASS "+checks+" graph identity and relationship checks");
 }
}
