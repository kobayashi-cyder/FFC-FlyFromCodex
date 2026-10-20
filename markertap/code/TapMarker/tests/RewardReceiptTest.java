package jp.tapmarker;
import java.util.*;
public final class RewardReceiptTest {
 static int checks;static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 public static void main(String[] args)throws Exception{
  PhaseEngine e=new PhaseEngine();PoipoiPreset.install(e,VirtualPresetProfileTest.canonical("tests/image6727-references.bin"));GamePopupEvidence.install(e,PoipoiPreset.APP);RewardReceipt r=new RewardReceipt();
  check(r.started(e.rule(PoipoiPreset.P+"get-tap"),1000)&&r.pending,"input completion starts an unconfirmed receipt");
  Map<String,float[]> samples=new HashMap<>();samples.put(PoipoiPreset.P+"game",e.rule(PoipoiPreset.P+"verify-game").reference.clone());r.observe(e,samples,1200,1200);check(r.pending,"main-screen recognition is not receipt evidence");
  samples.clear();samples.put(PoipoiPreset.P+"reward",new float[]{1});r.observe(e,samples,1300,1300);check(r.pending,"a generic popup close is not receipt evidence");
  samples.put(PoipoiPreset.P+"reward",e.rule(PoipoiPreset.P+"check-reward").reference.clone());check(r.observe(e,samples,900,1400).isEmpty()&&r.pending,"a frame before the input cannot prove its outcome");
  check(r.observe(e,samples,1500,1500).isEmpty()&&r.pending,"one reward frame awaits confirmation");check(r.observe(e,samples,1500,1600).isEmpty()&&r.pending,"duplicate frame is not a second observation");check(!r.observe(e,samples,1850,1850).isEmpty()&&!r.pending,"two subsequent reward frames confirm display outcome");check(r.status.contains("加算額は未確認"),"display confirmation never invents money credited");
  r.started(e.rule(PoipoiPreset.P+"get-tap"),2000);samples.clear();samples.put(PoipoiMissionEvidence.STATUS,new float[]{0});r.observe(e,samples,2200,2200);check(r.pending,"unrecognized reward list stays unconfirmed");samples.put(PoipoiMissionEvidence.STATUS,new float[]{2});r.observe(e,samples,2400,4000);check(r.pending,"stale positive evidence cannot resolve a receipt");r.observe(e,samples,4100,4100);check(!r.observe(e,samples,4450,4450).isEmpty()&&!r.pending,"positive list change can confirm the displayed outcome");r.reset();check(!r.pending&&r.status.isEmpty(),"runner restart clears prior-game receipt state");
  System.out.println("RewardReceiptTest: "+checks+" checks passed");
 }
}
