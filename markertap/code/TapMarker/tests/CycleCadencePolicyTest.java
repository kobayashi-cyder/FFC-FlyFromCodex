package jp.tapmarker;

/** Pure host regression: faster cycle observation, no reduced evidence rules. */
public final class CycleCadencePolicyTest {
  static int checks=0;
  static void ok(boolean value,String reason){
    checks++;if(!value)throw new AssertionError(reason);
  }
  static void cadence(){
    long now=10000L;
    String usagi=UsagiPreset.APP, kaitaku=KaitakuPreset.APP;
    PerformancePolicy.Profile standard=PerformancePolicy.NORMAL;
    long baseline=PerformancePolicy.scanInterval(standard,20,2,8);
    long usagiFollow=CycleCadencePolicy.interval(standard,20,2,8,usagi,
      UsagiPreset.PREFIX+"missions",9000,now,false);
    long kaitakuFollow=CycleCadencePolicy.interval(standard,20,2,8,kaitaku,
      KaitakuPreset.P+"claim-wait",9000,now,true);
    ok(baseline==220,"stable standard 2-display scan is 220ms");
    ok(usagiFollow==140,"Usagi mission evidence follow-up runs at renderer floor 140ms");
    ok(kaitakuFollow==140,"Kaitaku claim follow-up runs at renderer floor 140ms");
    ok(usagiFollow<baseline&&kaitakuFollow<baseline,"both games gain faster transition scans");
    ok(CycleCadencePolicy.interval(standard,20,2,8,usagi,
      UsagiPreset.PREFIX+"main",9000,now,false)==baseline,
      "main tapping and ordinary scan remain at stable cadence");
    ok(CycleCadencePolicy.interval(standard,20,2,8,kaitaku,
      KaitakuPreset.P+"ad",9000,now,false)==baseline,
      "ad observation and explicit CM recovery deadlines are unchanged");
    ok(CycleCadencePolicy.interval(standard,20,2,8,usagi,
      UsagiPreset.PREFIX+"missions",5000,now,false)==baseline,
      "slow or stalled phase returns to fair normal cadence after 4s");
    ok(CycleCadencePolicy.interval(standard,20,2,8,"com.unknown.app",
      "unknown-missions",9000,now,false)==baseline,
      "unknown applications never get ungrounded fast polling");
    ok(CycleCadencePolicy.interval(standard,20,2,8,usagi,
      UsagiPreset.PREFIX+"verify",9000,now,false)==140,
      "Usagi verify transitions fast");
    ok(CycleCadencePolicy.interval(standard,20,2,8,kaitaku,
      KaitakuPreset.P+"mission-opening",9000,now,false)==140,
      "Kaitaku mission-opening transitions fast");
    ok(CycleCadencePolicy.interval(PerformancePolicy.ECO,20,2,8,usagi,
      UsagiPreset.PREFIX+"missions",9000,now,false)>=PerformancePolicy.ECO.decodeMs,
      "light mode does not beat renderer rate");
    ok(CycleCadencePolicy.interval(PerformancePolicy.QUICK,20,2,8,kaitaku,
      KaitakuPreset.P+"missions",9000,now,false)>=PerformancePolicy.QUICK.decodeMs,
      "fast mode respects its renderer");
    ok(CycleCadencePolicy.interval(standard,300,2,8,usagi,
      UsagiPreset.PREFIX+"missions",9000,now,false)>=600,
      "CPU-bound worker cancels too-fast followups");
    ok(!CycleCadencePolicy.followup(usagi,UsagiPreset.PREFIX+"missions",11000,now,false),
      "clock rollback cannot authorize acceleration");
    ok(!CycleCadencePolicy.followup(kaitaku,KaitakuPreset.P+"unknown",9000,now,true),
      "unknown state never polls aggressively");
  }
  static void mission(){
    long first=KaitakuMissionCadence.initial(1000);
    ok(first==11000,"initial mission inspection after ten seconds");
    long page=KaitakuMissionCadence.entered(KaitakuPreset.P+"missions",5000,first);
    ok(page==17000,"mission page no longer incurs 60-second wait");
    long claim=KaitakuMissionCadence.entered(KaitakuPreset.P+"claim-wait",6000,page);
    ok(claim==12000,"post-claim next inspection can be in six seconds");
    ok(KaitakuMissionCadence.entered(KaitakuPreset.P+"main",7000,claim)==claim,
      "normal tapping does not rearm deadline every frame");
    ok(KaitakuMissionCadence.entered(KaitakuPreset.P+"ad",7000,claim)==claim,
      "CM processing does not authorize premature mission retap");
    ok(KaitakuMissionCadence.entered(KaitakuPreset.P+"mission-opening",20000,claim)==32000,
      "new mission view disarms next repeated inspection for 12s");
    ok(KaitakuMissionCadence.entered(KaitakuPreset.P+"claim-wait",28000,32000)==32000,
      "claim follow-up never extends an earlier deadline");
  }
  public static void main(String[] args){
    cadence();mission();
    System.out.println("CycleCadencePolicyTest: "+checks+" checks passed");
  }
}
