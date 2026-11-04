package jp.tapmarker;

import java.util.HashMap;
import java.util.Map;

/** Host-only lifecycle acceptance: no device gesture or live reward is claimed. */
public final class KaitakuFullCycleRegressionTest {
  static int checks;
  static void ok(boolean pass,String msg){checks++;if(!pass)throw new AssertionError(msg);}
  static Map<String,float[]> evidence(boolean home,String key){
    Map<String,float[]> m=new HashMap<>();
    m.put(KaitakuPreset.HOME,new float[]{home?1:0});
    m.put(KaitakuPreset.CONTINUE,new float[]{0});
    if(key!=null)m.put(KaitakuPreset.P+key,new float[]{1});
    return m;
  }
  static long act(PhaseEngine e,String key,boolean home,String rule,String next,long start){
    Map<String,float[]> s=evidence(home,key);
    e.observe(s,start);
    ok(e.poll()==null,"first screenshot does not act: "+rule);
    e.observe(s,start+100);
    PhaseEngine.Watch w=e.poll();
    ok(w!=null&&w.id.equals(KaitakuPreset.P+rule),
       "second screenshot chooses "+rule+(w==null?" no action":" got "+w.id));
    e.finish(w,start+110);
    ok(e.current.equals(KaitakuPreset.P+next),"phase after "+rule+": "+e.current);
    return start+500;
  }
  public static void main(String[] args){
    PhaseEngine e=new PhaseEngine();
    ok(KaitakuPreset.install(e),"install stock Kaitaku graph");
    e.strictRuntime=true;
    e.enter(KaitakuPreset.P+"main",0);
    long t=100;
    t=act(e,"mission-open",true,"main-mission-open","mission-opening",t);
    t=act(e,"mission-page",false,"mission-opening-mission-page","missions",t);
    float[] get={.685f,.374f,.912f,.411f};
    ok(KaitakuRewardClaimGate.allowAt(e,get,1_000_000_000L),"candidate reward enabled");
    ok(KaitakuRewardClaimGate.allowAt(e,get,1_100_000_000L),"second-frame reward enabled");
    t=act(e,"get",false,"missions-get","claim-wait",t);
    t=act(e,"close",false,"claim-wait-close","claim-wait",t);
    KaitakuRewardClaimGate.receipt(e);
    ok(!KaitakuRewardClaimGate.allowAt(e,get,10_000_000_000L),"verified reward suppresses duplicate");
    t=act(e,null,true,"claim-wait-home","main",t);
    UpgradeEngine side=new UpgradeEngine(e,KaitakuPreset.APP);
    ok(side.enabled(),"side upgrade controller active");
    Map<String,float[]> left=evidence(true,"upgrade");
    left.put(KaitakuPreset.P+"auto-upgrade",new float[]{0});
    e.observe(left,t);
    ok(side.observe(left,t,t,true)==null,"left upgrade needs two frames");
    e.observe(left,t+100);
    UpgradeEngine.Decision d=side.observe(left,t+100,t+100,true);
    ok(d!=null&&d.watch.id.equals(KaitakuPreset.P+"main-upgrade"),"efficiency action selected");
    ok(side.visible(d,left),"left preflight still sees upgrade");
    side.sent(d,t+110);
    ok(side.awaitingResult(),"gesture submission not effect proof");
    Map<String,float[]> changed=evidence(true,null);
    changed.put(KaitakuPreset.P+"upgrade",new float[]{0});
    changed.put(KaitakuPreset.P+"auto-upgrade",new float[]{0});
    side.observe(changed,t+230,t+230,true);
    ok(!side.awaitingResult(),"left result comparison completes");
    ok(e.current.equals(KaitakuPreset.P+"main"),"main tapping not abandoned");
    Map<String,float[]> right=evidence(true,"auto-upgrade");
    right.put(KaitakuPreset.P+"upgrade",new float[]{0});
    e.observe(right,t+2000);
    ok(side.observe(right,t+2000,t+2000,true)==null,"right upgrade needs two frames");
    e.observe(right,t+2100);
    UpgradeEngine.Decision r=side.observe(right,t+2100,t+2100,true);
    ok(r!=null&&r.watch.id.equals(KaitakuPreset.P+"main-auto-upgrade"),"auto action selected");
    side.sent(r,t+2110);
    side.observe(changed,t+2250,t+2250,true);
    ok(!side.awaitingResult(),"auto result comparison completes");
    ok(e.current.equals(KaitakuPreset.P+"main"),"main loop survives upgrades");
    System.out.println("KaitakuFullCycleRegressionTest: "+checks+
      " checks passed; no physical gesture or credited reward asserted");
  }
}
