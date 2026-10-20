package jp.tapmarker;
import java.util.*;
/** Compare fresh models before/after late runtime evidence normalization. */
public final class MemoryFootprintProbe {
 static final class Model {
  final PhaseEngine engine;final AdaptiveLayout layout;final UpgradeEngine upgrades;
  Model(String pkg,Map<String,float[]> refs,Map<String,float[]> evidence,Map<String,float[]> targets,boolean normalized){
   PhaseEngine installed=new PhaseEngine();if(pkg.equals(UsagiPreset.APP))UsagiPreset.install(installed,refs);else FarmPreset.install(installed,refs);
   engine=installed.runtimeCopy();layout=AdaptiveLayout.install(engine,pkg,refs,evidence);layout.configureTargets(pkg,targets);AdCloseAutomaton.install(engine,pkg);
   if(normalized)AutomatonNormalizer.normalizeRuntime(engine);
   upgrades=new UpgradeEngine(engine,pkg);
  }
 }
 public static void main(String[] args)throws Exception{
  Map<String,float[]> evidence=VirtualPresetProfileTest.canonical("tests/current-layout-evidence.bin"),targets=VirtualPresetProfileTest.canonical("tests/visual-targets.bin");
  for(String pkg:new String[]{UsagiPreset.APP,FarmPreset.APP}){
   Map<String,float[]> refs=VirtualPresetProfileTest.canonical(pkg.equals(UsagiPreset.APP)?"tests/usagi-current-references.bin":"tests/farm-replay.bin");
   for(boolean normalized:new boolean[]{false,true}){
    Model m=new Model(pkg,refs,evidence,targets,normalized);int rules=0;for(PhaseEngine.Phase state:m.engine.phases.values())rules+=state.watches.size();
    System.out.println(pkg+" "+(normalized?"AFTER":"BEFORE")+" phases="+m.engine.phases.size()+" rules="+rules+" "+HeapAgent.size(m.engine,m.layout,m.upgrades));
   }
  }
  System.out.println("Method: OpenJDK Instrumentation; fresh engine+layout+upgrade object graph, identical fixtures. Not Android PSS or app/game/graphics memory; independent per-app roots, shared cross-app objects may be counted twice.");
 }
}
