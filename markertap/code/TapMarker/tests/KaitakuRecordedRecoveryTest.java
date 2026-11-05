package jp.tapmarker;

import java.util.HashMap;
import java.util.Map;

/** Synthetic color/geometry fixtures. The input recording stays private. */
public final class KaitakuRecordedRecoveryTest {
  private static int checks;
  private static void check(boolean ok,String message){
    checks++;
    if(!ok)throw new AssertionError(message);
  }
  private static KaitakuScreenshots20261105RegressionTest.Frame ad(boolean tall){
    KaitakuScreenshots20261105RegressionTest.Frame f=
      new KaitakuScreenshots20261105RegressionTest.Frame(0);
    float top=tall?.247f:.367f,bottom=tall?.686f:.599f;
    f.fill(0,top,1,bottom,0xffffff);
    f.fill(0,top+.03f,1,top+.11f,0x5fcced);
    f.fill(.035f,.861f,.965f,.919f,0x4b6ac7);
    return f;
  }
  public static void main(String[] args){
    KaitakuScreenshots20261105RegressionTest.Frame f=ad(false);
    check(KaitakuFullScreenAd.visible(f.w,f.h,f),"countdown ad before close mark");
    f=ad(true);
    check(KaitakuFullScreenAd.visible(f.w,f.h,f),"taller end card");
    f.fill(0,.86f,1,.92f,0);
    check(!KaitakuFullScreenAd.visible(f.w,f.h,f),"white and cyan card without CTA");
    f=ad(false);
    f.fill(0,0,1,.28f,0x8bdff9);
    check(!KaitakuFullScreenAd.visible(f.w,f.h,f),"game HUD and bottom banner are not interstitial");
    f=KaitakuScreenshots20261105RegressionTest.mission();
    check(!KaitakuFullScreenAd.visible(f.w,f.h,f),"daily missions are not interstitial");
    f=KaitakuScreenshots20261105RegressionTest.boost();
    check(KaitakuVideoModalVisual.find(f.w,f.h,f)==null,"tap boost must not be skipped as coin chance");

    f=new KaitakuScreenshots20261105RegressionTest.Frame(0x263516);
    f.fill(.11f,.30f,.89f,.78f,0xffffff);
    f.fill(.11f,.30f,.89f,.37f,0xfef3c5);
    f.fill(.16f,.643f,.84f,.722f,0x2d2d2d);
    f.fill(.18f,.65f,.82f,.710f,0xebba08);
    float[] optOut=KaitakuVideoModalVisual.find(f.w,f.h,f);
    check(optOut!=null&&optOut[1]>.722f,"coin-chance fallback only proposes the lower opt-out");

    PhaseEngine e=new PhaseEngine();
    KaitakuPreset.install(e);
    check(e.rule(KaitakuMissionScroll.RULE)==null,"saved stock graph is unchanged");
    KaitakuPreset.installRuntime(e);
    check(e.rule(KaitakuPreset.P+"missions-continue")!=null,"mission phase can dismiss the recorded coin popup");
    e.strictRuntime=true;e.enter(KaitakuPreset.P+"missions",1);
    Map<String,float[]> s=new HashMap<>();
    KaitakuActionEvidence.put(s,"mission-page",null);
    KaitakuActionEvidence.put(s,"mission-scan",null);
    s.put(AdaptiveLayout.VIEWPORT,new float[]{8,10,500,1000,520,1020});
    e.observe(s,100);
    check(e.poll()==null,"one mission frame cannot swipe");
    e.observe(s,300);
    PhaseEngine.Watch scan=e.poll();
    check(scan!=null&&scan.id.equals(KaitakuMissionScroll.RULE),"two mission frames authorize one swipe");
    float[] path=KaitakuMissionScroll.path(s,520,1020);
    check(path!=null&&path[1]>path[3]&&path[0]==path[2],"upward swipe remains in the game viewport");
    check(KaitakuMissionScroll.path(s,510,1020)==null,"reject changed dimensions");
    e.finish(scan,660);
    check(KaitakuMissionScroll.scanned(e),"one successful swipe uses the visit budget");
    e.enter(KaitakuPreset.P+"claim-wait",800);
    e.lastCompletedRule=KaitakuPreset.P+"missions-get";
    check(KaitakuActionEvidence.waitingForReceipt(e),"Get input requires outcome observation");
    e.finish(e.rule(KaitakuPreset.P+"claim-wait-close"),1000);
    check(!KaitakuActionEvidence.waitingForReceipt(e),"after closing the result, allow mission navigation");
    System.out.println("KaitakuRecordedRecoveryTest: "+checks+" checks passed");
  }
}
