package jp.tapmarker;
import java.util.*;

/** Named farm controls must still obey observation, cancellation and daily routing. */
public final class FarmControlEvidenceTest {
 static int checks;
 static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 static PhaseEngine engine()throws Exception{PhaseEngine e=new PhaseEngine();FarmPreset.install(e,VirtualPresetProfileTest.canonical("tests/farm-replay.bin"));FarmControlEvidence.install(e);e.strictRuntime=true;return e;}
 static Map<String,float[]> button(String id){Map<String,float[]> s=new HashMap<>();check(FarmControlEvidence.put(s,FarmControlEvidence.id(id),230,700,540,1140),"visible label contributes frame-local coordinates");return s;}
 static float[] image(){float[] p=new float[768];for(int i=0;i<p.length;i++)p[i]=(i%7)/6f;return p;}
 public static void main(String[] args)throws Exception{
  check(FarmControlEvidence.id(" 宝箱を\n開ける ").equals(FarmPreset.P+"chest-open"),"line-wrapped treasure label");
  check(FarmControlEvidence.id("このまま始める").equals(FarmPreset.P+"start"),"alternate start spelling");
  for(String text:new String[]{"CMを見て宝箱を開ける","広告を見て宝箱を開ける","閉じる広告","宝箱","はじめる"})check(FarmControlEvidence.id(text).isEmpty(),"ad or partial label does not authorize input: "+text);
  PhaseEngine e=engine();Map<String,float[]> s=button("宝箱を開ける");e.enter(FarmPreset.P+"check",0);e.observe(s,5100);check(e.poll()==null,"one chest observation suppresses expired recovery without tapping");e.observe(s,5300);PhaseEngine.Watch w=e.poll();check(w!=null&&w.action==PhaseEngine.WAIT&&w.nextPhase.equals(FarmPreset.P+"chest"),"confirmed opening control enters chest state");e.finish(w,5400);
  e.observe(s,7000);check(e.poll()==null,"chest input needs fresh confirmation rather than expired timer");e.observe(s,7200);w=e.poll();check(w!=null&&w.action==PhaseEngine.CENTER&&w.regionId.equals(FarmPreset.P+"chest-open"),"explicit treasure tap wins over timer");
  check(e.confirmsAction(w,e.current,e.epoch,s),"visible opening button passes input preflight");check(!e.confirmsAction(w,e.current,e.epoch,new HashMap<>()),"disappearing button cancels pending input");
  float[] p=FarmControlEvidence.point(w.regionId,s,540,1140);check(p!=null&&p[0]==230&&p[1]==700,"tap uses observed button rather than the lock or full-image center");check(FarmControlEvidence.point(w.regionId,s,720,1520)==null,"resize invalidates old button point");
  Map<String,float[]> noPoint=new HashMap<>();noPoint.put(w.regionId,new float[]{1});check(FarmControlEvidence.point(w.regionId,noPoint,540,1140)==null,"label evidence alone never invents treasure coordinates");e.finish(w,7300);check(e.current.equals(FarmPreset.P+"check"),"treasure input returns to display verification");
  for(String[] row:new String[][]{{"reward","閉じる","check"},{"bonus","このままはじめる","verify"}}){e=engine();e.enter(FarmPreset.P+row[0],0);s=button(row[1]);e.observe(s,100);check(e.poll()==null,"first named button observation waits");e.observe(s,300);w=e.poll();check(w!=null&&w.action==PhaseEngine.CENTER&&w.nextPhase.equals(FarmPreset.P+row[2]),"named ordinary dialog follows its intended action");}
  for(String state:new String[]{"reward","bonus","chest"}){e=engine();new DailyCompletion(new float[]{.1f,.2f,.3f,.25f},new float[]{.1f,.08f,.4f,.12f},image(),image()).install(e);e.enter(FarmPreset.P+state,0);s=button(state.equals("reward")?"閉じる":state.equals("bonus")?"このままはじめる":"宝箱を開ける");s.put(DailyCompletion.REGION,image());s.put(DailyCompletion.HEADING,image());DailyCompletion.continuation(e,s);e.observe(s,7000);check(e.poll()==null,"daily proof holds named input and expired timer: "+state);e.observe(s,7200);w=e.poll();check(w!=null&&w.nextPhase.equals(DailyCompletion.PHASE)&&w.action==PhaseEngine.WAIT,"daily completion wins over each named farm button: "+state);}
  e=engine();e.regions.remove(FarmPreset.P+"chest-open");for(PhaseEngine.Phase state:e.phases.values())state.watches.removeIf(rule->rule.regionId.equals(FarmPreset.P+"chest-open"));check(FarmPreset.upgrade(e),"v1.67 graph gains missing treasure input");check(!FarmPreset.upgrade(e)&&e.validate().isEmpty(),"factory graph migration is valid and idempotent");
  System.out.println("FarmControlEvidenceTest: "+checks+" checks passed; named-input routing and daily priority; Android accessibility discovery and device input not tested");
 }
}
