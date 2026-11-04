package jp.tapmarker;
import java.util.HashMap;
import java.util.Map;

public final class RewardV158ObserverTest {
  static int checks;
  static void yes(boolean cond,String message){
    checks++;if(!cond)throw new AssertionError(message);
  }
  static final class Watch {
    final String regionId;
    final int action=0,condition=1;
    final float[] reference=new float[1536];
    final float threshold=.001f;
    Watch(String region){regionId=region;}
    float distance(float[] value){
      if(value==null||value.length!=reference.length)return Float.POSITIVE_INFINITY;
      for(int i=0;i<value.length;i++)if(value[i]!=reference[i])return 1;
      return 0;
    }
  }
  static final class FakeModel {
    String lastCompletedRule="";
    final Map<String,Watch> rules=new HashMap<>();
    Watch rule(String id){return rules.get(id);}
  }
  static Map<String,float[]> empty(){return new HashMap<>();}
  static void sample(Object owner,String pkg,FakeModel model,long at,
      Map<String,float[]> s,boolean ad,boolean playable){
    RewardV158Observer.observe(owner,pkg,model,s,at,at+40,ad,playable);
  }
  static void balance(Object owner,String pkg,long pt,long at){
    RewardV158Observer.balance(owner,pkg,pt,at,at+60);
  }
  public static void main(String[] args){
    final String COIN="com.kurashiru.coin.drop";
    FakeModel model=new FakeModel();
    model.rules.put("coin-default-get-tap",new Watch("claim"));
    model.rules.put("coin-default-claim-shown",new Watch("reward"));
    Object lane=new Object();
    sample(lane,COIN,model,1000,empty(),false,true);
    balance(lane,COIN,100,1100);
    model.lastCompletedRule="coin-default-get-tap";
    sample(lane,COIN,1800,empty(),false,false);
    balance(lane,COIN,110,1900);
    yes(RewardV158Observer.report(lane).contains("受領確定0回"),"tap alone not reward");
    Map<String,float[]> proof=empty();proof.put("reward",new float[1536]);
    sample(lane,COIN,2200,proof,false,false);
    balance(lane,COIN,110,2300);
    yes(RewardV158Observer.report(lane).contains("受領確定0回"),"single popup not proof");
    sample(lane,COIN,2800,proof,false,false);
    balance(lane,COIN,110,3000);
    yes(RewardV158Observer.report(lane).contains("受領確定0回"),"first balance not enough");
    balance(lane,COIN,110,3500);
    yes(RewardV158Observer.report(lane).contains("受領確定1回"),"two post-proof frames confirm");
    yes(RewardV158Observer.report(lane).contains("実測報酬10"),"actual delta");

    Object untrusted=new Object();
    FakeModel untrustedModel=new FakeModel();
    untrustedModel.rules.put("coin-default-claim-shown",new Watch("reward"));
    sample(untrusted,COIN,1000,empty(),false,true);
    balance(untrusted,COIN,100,1100);
    sample(untrusted,COIN,1600,proof,false,false);
    sample(untrusted,COIN,2200,proof,false,false);
    balance(untrusted,COIN,120,2700);
    balance(untrusted,COIN,120,3200);
    yes(RewardV158Observer.report(untrusted).contains("受領確定0回"),
      "reward popup and balance without completed claim never credited");

    Object restart=new Object(); FakeModel restartModel=new FakeModel();
    restartModel.rules.put("coin-default-get-tap",new Watch("claim"));
    restartModel.rules.put("coin-default-claim-shown",new Watch("reward"));
    sample(restart,COIN,restartModel,1000,empty(),false,true);
    balance(restart,COIN,100,1100);
    restartModel.lastCompletedRule="coin-default-get-tap";
    sample(restart,COIN,restartModel,1700,empty(),false,false);
    RewardV158Observer.invalidate(restart); // HOME/stop/Intent
    sample(restart,COIN,restartModel,2200,proof,false,false);
    sample(restart,COIN,restartModel,2800,proof,false,false);
    balance(restart,COIN,150,3200);
    balance(restart,COIN,150,3800);
    yes(RewardV158Observer.report(restart).contains("受領確定0回"),
      "force-stop invalidates all pending receipts");

    Object duplicate=new Object();
    FakeModel model2=new FakeModel();
    model2.rules.put("coin-default-get-tap",new Watch("claim"));
    model2.rules.put("coin-default-claim-shown",new Watch("reward"));
    sample(duplicate,COIN,model2,1000,empty(),false,true);
    balance(duplicate,COIN,100,1100);
    model2.lastCompletedRule="coin-default-get-tap";
    sample(duplicate,COIN,model2,1600,empty(),false,false);
    balance(duplicate,COIN,101,1700);
    yes(RewardV158Observer.report(duplicate).contains("受領確定0回"),
      "ambiguous concurrent pt source cannot automatically earn reward");
    RewardV158Observer.invalidate(duplicate);

    Object adOnly=new Object();FakeModel adModel=new FakeModel();
    sample(adOnly,COIN,adModel,1000,empty(),true,false);
    sample(adOnly,COIN,adModel,1600,empty(),true,false);
    yes(RewardV158Observer.report(adOnly).contains("受領確定0回"),
      "ad visibility cannot credit a reward");
    System.out.println("RewardV158ObserverTest: "+checks+" checks passed");
  }
}
