package jp.tapmarker;
import java.util.*;

/** Regression for 2026-11-04 Kaitaku upgrade-wait FRAME_STALE incident. */
public final class KaitakuAdUpgradeRepairTest {
 static int checks;
 static void ok(boolean yes,String msg){checks++;if(!yes)throw new AssertionError(msg);}
 static void upgrades(){
  PhaseEngine editable=new PhaseEngine();
  ok(KaitakuPreset.install(editable),"stock Kaitaku preset is available");
  PhaseEngine e=editable.runtimeCopy();
  e.strictRuntime=true;e.enter(KaitakuPreset.P+"main",10);
  final String left=KaitakuPreset.P+"main-upgrade",right=KaitakuPreset.P+"main-auto-upgrade";
  ok(editable.rule(left)!=null&&editable.rule(right)!=null,"editable actions are preserved");
  PhaseEngine.Watch leftAction=e.rule(left);
  UpgradeEngine side=new UpgradeEngine(e,KaitakuPreset.APP);
  ok(side.enabled(),"Kaitaku uses the one-shot side engine");
  ok(e.rule(left)==null&&e.rule(right)==null,"two upgrades no longer interrupt main");
  ok(!e.phases.containsKey(KaitakuPreset.P+"upgrade-wait"),"dead-end upgrade wait removed at runtime");
  ok(editable.rule(left)!=null&&editable.phases.containsKey(KaitakuPreset.P+"upgrade-wait"),"saved user preset is untouched");
  ArrayList<PhaseEngine.Region> needed=new ArrayList<>(e.activeRegions());
  side.addRegions(needed);
  ok(needed.contains(e.regions.get(KaitakuPreset.P+"upgrade")),"side samples left control");
  ok(needed.contains(e.regions.get(KaitakuPreset.P+"auto-upgrade")),"side samples right control");
  Map<String,float[]> sample=new HashMap<>();
  sample.put(KaitakuPreset.HOME,new float[]{1});
  sample.put(KaitakuPreset.P+"upgrade",new float[]{1});
  sample.put(KaitakuPreset.P+"auto-upgrade",new float[]{0});
  e.observe(sample,100);
  ok(side.observe(sample,100,100,true)==null,"one candidate frame cannot authorize tap");
  e.observe(sample,200);
  UpgradeEngine.Decision d=side.observe(sample,200,200,true);
  ok(d!=null&&d.watch.id.equals(left),"two fresh frames identify tap efficiency");
  ok(side.visible(d,sample),"candidate still visible at preflight");
  ok(!side.visible(d,Collections.emptyMap()),"missing fresh evidence cancels candidate");
  ok(e.current.equals(KaitakuPreset.P+"main")&&!e.awaitingBranch(),"ordinary tapping stays on main");
  side.sent(d,250);
  ok(side.awaitingResult(),"input submission is not effect confirmation");
  Map<String,float[]> after=new HashMap<>(sample);
  after.put(KaitakuPreset.P+"upgrade",new float[]{0});
  side.observe(after,350,350,true);
  ok(!side.awaitingResult()&&side.status().contains("表示変化"),"changed post-action pixels close the one-shot episode");
  ok(side.observe(sample,400,400,true)==null,"button cannot be spammed after the first command");
 }
 static void ads(){
  PhaseEngine e=new PhaseEngine();KaitakuPreset.install(e);
  ok(e.rule("kaitaku-default-ad-"+AdCloseAutomaton.RIGHT)==null,"default stock graph does not guess ad close controls");
  AdCloseAutomaton.install(e,KaitakuPreset.APP);
  ok(e.regions.containsKey(AdCloseAutomaton.LEFT)&&e.regions.containsKey(AdCloseAutomaton.RIGHT),"Kaitaku gains screenshot close regions");
  ok(e.rule("kaitaku-default-ad-"+AdCloseAutomaton.RIGHT)!=null,"ad state routes a verified close");
  ok(e.rule("kaitaku-default-verify-"+AdCloseAutomaton.LEFT)!=null,"ad exit is observable before ad-state timeout");
 }
 static void namedButton(){
  PhaseEngine e=new PhaseEngine();KaitakuPreset.install(e);e.enter(KaitakuPreset.P+"main",10);
  float[] button={.07f,.81f,.30f,.87f};
  List<KaitakuNamedControls.Control> labels=Collections.singletonList(new KaitakuNamedControls.Control("タップ効率",button));
  Map<String,float[]> out=new HashMap<>();out.put(KaitakuPreset.HOME,new float[]{1});
  KaitakuNamedControls.apply(e,"開拓する タップ効率",labels,out,1000,2000);
  ok(KaitakuActionEvidence.present(out,"upgrade"),"named button can authorize efficiency control");
  ok(Arrays.equals(out.get(KaitakuPreset.P+"upgrade"+AdaptiveLayout.BOUNDS),button),"accessibility bounds override guessed center");
  Map<String,float[]> noHome=new HashMap<>();noHome.put(KaitakuPreset.HOME,new float[]{0});
  KaitakuNamedControls.apply(e,"開拓する タップ効率",labels,noHome,1000,2000);
  ok(!KaitakuActionEvidence.present(noHome,"upgrade"),"no game home means no upgrade tap");
  List<KaitakuNamedControls.Control> top=Collections.singletonList(new KaitakuNamedControls.Control("タップ効率",new float[]{.07f,.08f,.30f,.12f}));
  Map<String,float[]> wrong=new HashMap<>();wrong.put(KaitakuPreset.HOME,new float[]{1});
  KaitakuNamedControls.apply(e,"開拓する タップ効率",top,wrong,1000,2000);
  ok(!KaitakuActionEvidence.present(wrong,"upgrade"),"label outside bottom-left control area is rejected");
 }
 public static void main(String[]args){
  upgrades();ads();namedButton();
  System.out.println("KaitakuAdUpgradeRepairTest: "+checks+" checks passed; Android input and live ad not tested");
 }
}
