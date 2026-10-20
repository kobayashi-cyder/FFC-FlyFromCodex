package jp.tapmarker;
import java.util.*;

public final class AutomatonNormalizationTest {
 static int checks;static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 static PhaseEngine.Phase state(PhaseEngine e,String id,String name){PhaseEngine.Phase p=new PhaseEngine.Phase();p.id=id;p.name=name;e.phases.put(id,p);return p;}
 static PhaseEngine.Watch timer(PhaseEngine e,String state,String id,String next,long delay){PhaseEngine.Watch w=new PhaseEngine.Watch();w.id=id;w.name="表示を確認";w.condition=PhaseEngine.TIMEOUT;w.action=PhaseEngine.WAIT;w.waitMs=0;w.timeoutMs=delay;w.nextPhase=next;e.phases.get(state).watches.add(w);return w;}
 static PhaseEngine model(){PhaseEngine e=new PhaseEngine();state(e,"start","開始");state(e,"one","待機");state(e,"copy","待機");state(e,"done","完了").terminal=true;e.initial=e.current="start";timer(e,"start","start-copy","copy",0);timer(e,"one","one-done","done",1000);timer(e,"copy","copy-done","done",1000);return e;}
 static String trace(PhaseEngine e,long second){e.strictRuntime=true;e.enter("start",0);e.observe(Collections.emptyMap(),0);PhaseEngine.Watch w=e.poll();check(w!=null,"initial route exists");e.finish(w,0);e.observe(Collections.emptyMap(),second);w=e.poll();if(w!=null)e.finish(w,second);return e.phases.get(e.current).name;}
 static void graphCases(){
  PhaseEngine original=model(),e=original.runtimeCopy();AutomatonNormalizer.Result r=AutomatonNormalizer.normalize(e);
  check(r.states==1&&e.phases.size()==3&&e.rule("start-copy").nextPhase.equals("one"),"identical named states merge and incoming links follow the surviving state");
  check(AutomatonGraph.relationships(e).isEmpty(),"all references remain valid");check(trace(original.runtimeCopy(),999).equals(trace(e.runtimeCopy(),999)),"merge preserves the pre-timeout state");check(trace(original.runtimeCopy(),1000).equals(trace(e.runtimeCopy(),1000)),"merge preserves the completed result");
  check(AutomatonNormalizer.normalize(e).removed()==0,"normalization is idempotent");
  e=model();e.rule("copy-done").timeoutMs=1001;check(AutomatonNormalizer.normalize(e).states==0,"a different timeout keeps a separate state");
  e=model();e.rule("copy-done").waitMs=1;check(AutomatonNormalizer.normalize(e).states==0,"a different post-action delay keeps a separate state");
  e=model();e.rule("copy-done").priority++;check(AutomatonNormalizer.normalize(e).states==0,"different rule priority is retained");
  e=model();e.rule("start-copy").historyPhase="copy";check(AutomatonNormalizer.normalize(e).states==0,"history-visible state identities survive");
  e=model();e.rule("start-copy").historyRule="copy-done";check(AutomatonNormalizer.normalize(e).states==0,"history-visible rule identities survive");
  e=model();e.rule("one-done").nextPhase="copy";e.rule("copy-done").nextPhase="copy";check(AutomatonNormalizer.normalize(e).states==0,"a state change is never converted into a timer-resetting self-loop");
  e=model();e.current="copy";check(AutomatonNormalizer.normalize(e).states==0,"current editor state stays available");
  e=model();PhaseEngine.Watch first=timer(e,"start","duplicate-a","done",2000),other=PhaseEngine.copyWatch(first);other.id="duplicate-b";e.phases.get("start").watches.add(other);r=AutomatonNormalizer.normalize(e);check(r.rules==1&&e.rule("duplicate-a")!=null&&e.rule("duplicate-b")==null,"identical rules in one state retain one stable representative");
  e=model();first=timer(e,"start","duplicate-a","done",2000);other=PhaseEngine.copyWatch(first);other.id="duplicate-b";other.stableMs=40;e.phases.get("start").watches.add(other);check(AutomatonNormalizer.normalize(e).rules==0,"different stability conditions are not deduplicated");
  e=model();e.rule("start-copy").nextPhase="missing";r=AutomatonNormalizer.normalize(e);check(r.removed()==0&&e.phases.size()==4,"invalid graphs stay unchanged for explicit repair");
  e=model();e.phases.get("start").watches.add(PhaseEngine.copyWatch(e.rule("start-copy")));r=AutomatonNormalizer.normalize(e);check(r.rules==1&&AutomatonGraph.relationships(e).isEmpty(),"repeated records of one identical identity are safely repaired");
  e=model();PhaseEngine.Watch conflicting=PhaseEngine.copyWatch(e.rule("start-copy"));conflicting.nextPhase="done";e.phases.get("start").watches.add(conflicting);r=AutomatonNormalizer.normalize(e);check(r.removed()==0&&AutomatonGraph.relationships(e).contains("重複"),"one identity with conflicting behavior is never silently rebound");
 }
 static long referenceBytes(PhaseEngine e,boolean unique){Set<float[]> seen=Collections.newSetFromMap(new IdentityHashMap<float[],Boolean>());long size=0;for(PhaseEngine.Phase p:e.phases.values())for(PhaseEngine.Watch w:p.watches){if(w.reference!=null&&(!unique||seen.add(w.reference)))size+=w.reference.length*4;for(float[] ref:w.guardReferences)if(ref!=null&&(!unique||seen.add(ref)))size+=ref.length*4;}return size;}
 static void presetCases()throws Exception{
  for(String app:new String[]{UsagiPreset.APP,FarmPreset.APP,CoinPreset.APP,ShoppingPreset.APP}){
   String fixture=app.equals(UsagiPreset.APP)?"usagi":app.equals(FarmPreset.APP)?"farm":app.equals(CoinPreset.APP)?"coin":"shopping";
   String file=fixture.equals("coin")?"video6662-references.bin":fixture.equals("shopping")?"video6666-references.bin":fixture+"-replay.bin";
   Map<String,float[]> refs=VirtualPresetProfileTest.canonical("tests/"+file);PhaseEngine e=new PhaseEngine();
   if(app.equals(UsagiPreset.APP))UsagiPreset.install(e,refs);else if(app.equals(FarmPreset.APP))FarmPreset.install(e,refs);else if(app.equals(CoinPreset.APP))CoinPreset.install(e,refs);else ShoppingPreset.install(e,refs);
   PhaseEngine before=e.runtimeCopy();long raw=referenceBytes(e,false);AutomatonNormalizer.Result result=AutomatonNormalizer.normalize(e);
   check(result.removed()==0&&AutomatonGraph.sameConfiguration(before,e),"identity-sensitive preset graph remains equivalent: "+fixture);
   long shared=referenceBytes(e,true);check(shared<raw,"repeated image evidence occupies one array: "+fixture);
   PhaseEngine runtime=e.runtimeCopy();PhaseEngine.Watch source=null;for(PhaseEngine.Phase p:e.phases.values())for(PhaseEngine.Watch w:p.watches)if(w.reference!=null){source=w;break;}
   PhaseEngine.Watch copy=runtime.rule(source.id);check(copy.reference!=source.reference&&Arrays.equals(copy.reference,source.reference),"runtime arrays are separate from editable saved configuration: "+fixture);
   check(referenceBytes(runtime,true)==shared,"runtime copying does not recreate duplicated arrays: "+fixture);
   copy.reference=new float[]{42};check(source.reference.length!=1||source.reference[0]!=42,"replacing runtime evidence leaves configured evidence intact: "+fixture);
   AdCloseAutomaton.install(runtime,app);PhaseEngine once=runtime.runtimeCopy();AdCloseAutomaton.install(runtime,app);check(AutomatonGraph.sameConfiguration(once,runtime)&&AutomatonGraph.relationships(runtime).isEmpty(),"reinstalling recovery automata cannot duplicate arrows: "+fixture);
   System.out.println("ReferencePool "+fixture+": "+raw+" -> "+shared+" bytes, preset identities retained");
  }
  ReferencePool pool=new ReferencePool();check(pool.store(null)==-1&&pool.values.isEmpty(),"missing images are not silently materialized");check(pool.store(new float[]{.1f,.2f})==pool.store(new float[]{.1f,.2f}),"equal vectors use one stored reference key");check(pool.store(new float[]{.1f,.20001f})!=0,"close but different images retain their precision");
 }
 static List<PhaseEngine.Watch> watches(PhaseEngine e){List<PhaseEngine.Watch> result=new ArrayList<>();for(PhaseEngine.Phase p:e.phases.values())result.addAll(p.watches);return result;}
 static long runtimeBytes(PhaseEngine e){Set<float[]> seen=Collections.newSetFromMap(new IdentityHashMap<float[],Boolean>());for(PhaseEngine.Watch w:watches(e)){seen.add(w.reference);seen.add(w.runtimeAlternative);Collections.addAll(seen,w.guardReferences);if(w.runtimePairedReferences!=null)Collections.addAll(seen,w.runtimePairedReferences);}long result=0;for(float[] a:seen)if(a!=null)result+=a.length*4L;return result;}
 static void runtimeCases()throws Exception{
  for(String pkg:new String[]{UsagiPreset.APP,FarmPreset.APP}){
   boolean rabbit=pkg.equals(UsagiPreset.APP);Map<String,float[]> refs=VirtualPresetProfileTest.canonical(rabbit?"tests/usagi-current-references.bin":"tests/farm-replay.bin");PhaseEngine source=new PhaseEngine();if(rabbit)UsagiPreset.install(source,refs);else FarmPreset.install(source,refs);
   PhaseEngine e=source.runtimeCopy();AdaptiveLayout layout=AdaptiveLayout.install(e,pkg,refs,VirtualPresetProfileTest.canonical("tests/current-layout-evidence.bin"));layout.configureTargets(pkg,VirtualPresetProfileTest.canonical("tests/visual-targets.bin"));AdCloseAutomaton.install(e,pkg);
   List<PhaseEngine.Watch> before=watches(e);List<float[]> primary=new ArrayList<>(),alternative=new ArrayList<>();List<float[][]> paired=new ArrayList<>();
   for(PhaseEngine.Watch w:before){primary.add(w.reference);alternative.add(w.runtimeAlternative);paired.add(w.runtimePairedReferences);}
   e.enter(e.initial,123);e.lastCompletedRule=before.get(0).id;e.sourcePhase=e.initial;e.confirmations.put(before.get(0).id,1);long epoch=e.epoch;int phases=e.phases.size();long bytes=runtimeBytes(e);
   AutomatonNormalizer.normalizeRuntime(e);
   check(watches(e).equals(before)&&e.phases.size()==phases,"live rule/state identities and ordering survive normalization: "+pkg);
   check(e.epoch==epoch&&e.enteredAt==123&&e.lastCompletedRule.equals(before.get(0).id)&&e.sourcePhase.equals(e.initial)&&e.confirmations.get(before.get(0).id)==1,"normalization preserves timers, history and confirmations: "+pkg);
   check(runtimeBytes(e)<bytes,"post-layout exact reference sharing reduces actual retained vectors: "+pkg);
   for(int i=0;i<before.size();i++){PhaseEngine.Watch w=before.get(i);check(Arrays.equals(primary.get(i),w.reference)&&Arrays.equals(alternative.get(i),w.runtimeAlternative)&&Arrays.deepEquals(paired.get(i),w.runtimePairedReferences),"every runtime evidence value is exact: "+w.id);}
   ReferencePool independent=new ReferencePool();for(PhaseEngine.Watch w:before)if(w.reference!=null){check(independent.copy(w.reference)!=w.reference,"a separate runtime pool owns separate vectors");break;}
   long once=runtimeBytes(e);AutomatonNormalizer.normalizeRuntime(e);check(runtimeBytes(e)==once,"runtime normalization is idempotent: "+pkg);
  }
  PhaseEngine e=model();PhaseEngine.Watch first=e.rule("one-done"),other=e.rule("copy-done");first.runtimeAlternative=new float[]{1};other.runtimeAlternative=new float[]{2};check(AutomatonNormalizer.normalize(e).states==0,"different runtime alternatives retain separate states");
  e=model();e.rule("copy-done").runtimeBlocks=true;check(AutomatonNormalizer.normalize(e).states==0,"different runtime distance metrics are not merged");
  e=model();e.rule("copy-done").runtimeControlEvidence=true;check(AutomatonNormalizer.normalize(e).states==0,"control evidence is distinct from image evidence");
  e=model();e.rule("copy-done").runtimePairedReferences=new float[][]{new float[]{1}};check(AutomatonNormalizer.normalize(e).states==0,"different paired variants remain distinct");
  e=model();first=e.rule("one-done");other=e.rule("copy-done");float[] ref={.1f,.2f};float[][] external={ref};first.reference=ref;first.guardReferences=external;other.guardReferences=external;first.runtimeAlternative=ref.clone();first.runtimePairedReferences=external;AutomatonNormalizer.normalizeRuntime(e);
  check(first.reference==first.guardReferences[0]&&first.reference==first.runtimeAlternative&&first.reference==first.runtimePairedReferences[0],"equal vectors share across primary, guard and alternative roles");
  check(first.reference!=ref&&first.guardReferences!=external&&first.guardReferences!=other.guardReferences&&first.runtimePairedReferences!=external,"source vectors and mutable row containers stay isolated");first.guardReferences[0]=new float[]{42};check(other.guardReferences[0][0]==.1f&&external[0]==ref,"replacing one watch's guard does not edit another watch");
  e=model();first=e.rule("one-done");other=e.rule("copy-done");first.reference=new float[]{.1f};other.reference=new float[]{.100001f};AutomatonNormalizer.normalizeRuntime(e);check(first.reference!=other.reference,"nearly equal vectors keep their original precision");
 }
 public static void main(String[] args)throws Exception{graphCases();presetCases();runtimeCases();System.out.println("AutomatonNormalizationTest: "+checks+" checks passed");}
}
