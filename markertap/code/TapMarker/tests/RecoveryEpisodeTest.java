package jp.tapmarker;
import java.util.*;

/** Reported v1.115 mixed-evidence episode, using the real factory graph and recorded footer. */
public final class RecoveryEpisodeTest {
 static int checks;static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 public static void main(String[] args)throws Exception{
  Video6772ReplayTest.load();Video6772ReplayTest.Run run=new Video6772ReplayTest.Run("coin","verify");
  VirtualPresetProfileTest.Image im=Video6772ReplayTest.frames.get("coin:28.07");RegionSampler.Pixels pixels=FarmDialogReplayTest.resize(im,320,718,true);
  Map<String,float[]> footer=run.layout.popupEvidence.quick(320,718,pixels,null);String id=GamePopupEvidence.closeId(CoinPreset.APP);float[] point=GamePopupEvidence.point(id,footer,320,718);
  check(point!=null&&point[0]==70&&point[1]==712,"reported point reproduced by recorded footer");
  for(int i=0;i<3;i++)run.ad.recovery.sent(i,false);for(int i=0;i<2;i++)run.ad.recovery.sent(10+i,true);
  run.ad.filter(run.e,new HashMap<>(footer),320,718,1000,1000);run.ad.filter(run.e,new HashMap<>(footer),320,718,1400,1400);
  check(run.ad.recovery.exhausted(),"a matching popup cannot replenish recovery attempts");check(!RecoveryEvidence.home(run.e,footer),"a popup is not verified home");
  PopupExitAttempts budget=run.ad.popupExits;budget.sent(id,1400);budget.filter(new HashMap<>(footer),320,718,2400,2400);budget.sent(id,2400);check(budget.count==2,"two footer requests charged");
  for(int i=0;i<30;i++){
   long now=3000+i*1600;run.ad.filter(run.e,new HashMap<>(),320,718,now,now);run.ad.filter(run.e,new HashMap<>(),320,718,now+400,now+400);
   Map<String,float[]> named=new HashMap<>(footer);named.remove(GamePopupEvidence.FOOTER_EXIT);GamePopupEvidence.put(named,id,72,710,320,718);
   run.ad.filter(run.e,named,320,718,now+800,now+800);check(GamePopupEvidence.point(id,named,320,718)==null,"switching detector and missing frames cannot repeat the same footer");check(budget.count==2&&run.ad.recovery.exhausted(),"unresolved episode retains both limits");
  }
  Map<String,float[]> home=new HashMap<>();PhaseEngine.Watch w=run.e.rule(CoinPreset.P+"verify-home");home.put(w.regionId,w.reference.clone());
  run.ad.filter(run.e,new HashMap<>(home),320,718,55000,55000);check(run.ad.recovery.exhausted()&&budget.count==2,"one home observation does not erase history");
  run.ad.filter(run.e,new HashMap<>(home),320,718,55400,55400);check(!run.ad.recovery.exhausted()&&budget.count==0,"two fresh stable home observations permit a later episode");
  Map<String,float[]> next=new HashMap<>(footer);run.ad.filter(run.e,next,320,718,56000,56000);check(GamePopupEvidence.point(id,next,320,718)!=null,"a later footer is actionable after verified recovery");
  Map<String,float[]> probe=run.layout.sampleRelative(run.e.activeRegions(),320,718,pixels,58000);check(!probe.containsKey(CoinPreset.P+"home"),"independent home probe cannot grant game input through a popup");check(probe.containsKey(GamePopupEvidence.HOME_PROBE+CoinPreset.P+"home"),"periodic independent home result remains available for diagnosis");check(RecognitionDiagnostics.describe(run.e,probe).contains("独立確認"),"diagnostics distinguish an independent held probe");
  check(!RecoveryEvidence.home(run.e,probe),"independent pixels do not reset the recovery budget through an overlay");
  System.out.println("RecoveryEpisodeTest: "+checks+" checks; recorded footer, missed detections, named/image switch, finite budgets, independent probes and confirmed-home release; Android delivery untested");
 }
}
