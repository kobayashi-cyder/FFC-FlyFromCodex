package jp.tapmarker;

/** Pure host regression: UI turnover metrics cannot be mistaken for rewards. */
public final class CycleLatencyMeterTest {
  static int checks;
  static void ok(boolean value,String why){
    checks++;if(!value)throw new AssertionError(why);
  }
  public static void main(String[] args){
    CycleLatencyMeter m=new CycleLatencyMeter();
    m.observe("usagi-default-main",100,100);
    m.observe("usagi-default-clear",300,300);
    m.observe("usagi-default-open-missions",500,500);
    m.observe("usagi-default-missions",800,800);
    m.observe("usagi-default-verify",1100,1100);
    m.observe("usagi-default-main",1500,1500);
    ok(m.loops()==1,"one confirmed UI visit cycle");
    ok(m.lastLoopMs()==1200,"cycle duration grounded in observation timestamp");
    ok(m.maxPhaseMs()==400,"longest state interval tracked");
    m.observe("usagi-default-main",1500,1510);
    ok(m.loops()==1,"duplicate frame cannot inflate cycle");
    m.observe("usagi-default-ad",1600,1600);
    m.observe("usagi-default-main",2000,2000);
    ok(m.loops()==1,"CM recovery is not a rewarded mission visit");
    m.observe("usagi-default-missions",2100,4000);
    ok(m.loops()==1,"stale capture cannot create transition");
    m.reset();
    ok(m.loops()==0&&m.lastLoopMs()==-1,"reset clears old run metrics");
    m.observe("kaitaku-default-main",5000,5000);
    m.observe("kaitaku-default-mission-opening",5200,5200);
    m.observe("kaitaku-default-missions",5600,5600);
    m.observe("kaitaku-default-claim-wait",5900,5900);
    m.observe("kaitaku-default-verify",6200,6200);
    m.observe("kaitaku-default-main",6600,6600);
    ok(m.loops()==1&&m.lastLoopMs()==1400,"Kaitaku independently measures mission roundtrip");
    ok(m.report().contains("報酬未検証"),"UI cycle never claims credited reward");
    System.out.println("CycleLatencyMeterTest: "+checks+" checks passed");
  }
}
