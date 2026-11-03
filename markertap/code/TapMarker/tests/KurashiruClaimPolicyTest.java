package jp.tapmarker;

import java.util.HashMap;
import java.util.Map;

/** Minimal JVM test double: the real PhaseEngine is Android-project code. */
final class PhaseEngine {
  static final int MATCH=1;
  static final class Watch {
    String regionId;float[] reference;float threshold;int condition;
    float distance(float[] actual){
      if(actual==null||reference==null||actual.length!=reference.length)return Float.MAX_VALUE;
      float sum=0;for(int i=0;i<actual.length;i++)sum+=Math.abs(actual[i]-reference[i]);
      return sum/reference.length;
    }
  }
  final Map<String,Watch> rules=new HashMap<>();
  Watch rule(String id){return rules.get(id);}
}

public final class KurashiruClaimPolicyTest {
  static int checks;
  static void ok(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
  static void checkClaim(String pkg,String id){
    ok(KurashiruClaimPolicy.isClaim(pkg,id,true),"known reward claim: "+id);
    ok(!KurashiruClaimPolicy.isClaim(pkg,id,false),"unexecuted claim: "+id);
  }
  public static void main(String[]args){
    checkClaim("com.kurashiru.fue.usagi","usagi-default-ゲットのみタップ");
    checkClaim("com.kurashiru.fue.usagi","usagi-default-通常獲得の中央");
    checkClaim("com.kurashiru.coin.drop","coin-default-get-tap");
    checkClaim("com.kurashiru.shopping.usagi","shopping-default-get-tap");
    checkClaim("com.kurashiru.shopping.usagi","shopping-default-arrival-collect");
    checkClaim("com.kurashiru.box.merge","box-default-claim-tap");
    checkClaim("com.kurashiru.poipoi.usagi","poipoi-default-get-tap");
    checkClaim("com.kurashiru.poipoi.usagi","poipoi-default-offline-tap");
    ok(!KurashiruClaimPolicy.isClaim("com.kurashiru.coin.drop","coin-default-mission-button-tap",true),"mission open is not reward");
    ok(!KurashiruClaimPolicy.isClaim("com.kurashiru.poipoi.usagi","poipoi-default-reward-tap",true),"close is not reward");
    ok(!KurashiruClaimPolicy.isClaim("com.kurashiru.nouen.usagi","farm-default-reward-shown",true),"farm popup is not a user claim");

    PhaseEngine engine=new PhaseEngine();
    PhaseEngine.Watch w=new PhaseEngine.Watch();
    w.regionId="coin-default-reward";w.reference=new float[1536];w.threshold=.02f;
    w.condition=PhaseEngine.MATCH;engine.rules.put("coin-default-claim-shown",w);
    Map<String,float[]> samples=new HashMap<>();
    ok(!KurashiruClaimPolicy.rewardVisible("com.kurashiru.coin.drop",engine,samples),"no image no reward");
    samples.put(w.regionId,new float[1536]);
    ok(KurashiruClaimPolicy.rewardVisible("com.kurashiru.coin.drop",engine,samples),"fresh matching reward UI");
    java.util.Arrays.fill(samples.get(w.regionId),1f);
    ok(!KurashiruClaimPolicy.rewardVisible("com.kurashiru.coin.drop",engine,samples),"disagreeing reward UI");
    KurashiruRewardProof proof=new KurashiruRewardProof();
    ok(!proof.observe(true,100,100),"one matching frame does not prove reward");
    ok(!proof.observe(true,100,101),"duplicate frame ignored");
    ok(proof.observe(true,200,200),"second independent positive frame");
    proof.reset();
    ok(!proof.observe(true,1000,1000),"new episode begins");
    ok(!proof.observe(false,1100,1100),"missing reward resets episode");
    ok(!proof.observe(true,1200,1200),"one later frame no proof");
    ok(!proof.observe(true,1300,4000),"stale screenshot ignored");
    ok(proof.observe(true,1400,1400),"new second fresh matching frame");
    System.out.println("KurashiruClaimPolicyTest: "+checks+" checks passed");
  }
}
