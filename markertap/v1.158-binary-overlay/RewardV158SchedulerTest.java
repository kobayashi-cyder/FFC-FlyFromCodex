package jp.tapmarker;
import java.util.HashMap;
import java.util.Map;

public final class RewardV158SchedulerTest {
  static int count;
  static void ok(boolean condition,String why){
    count++;if(!condition)throw new AssertionError(why);
  }
  static final class Manual {
    boolean blocked;
    boolean holding(long now){return blocked;}
  }
  static final class Slot {
    boolean stopped,cyclePending;
    final Manual manual=new Manual();
  }
  static final class Split {
    boolean active=true,parallelVirtual;
    int index;
    Slot[] slots={new Slot(),new Slot()};
  }
  static final class Watch {
    final int action=0,condition=1;
    final float[] reference=new float[1536];
    final float threshold=.001f;
    final String regionId;
    Watch(String id){regionId=id;}
    float distance(float[] seen){
      if(seen==null||seen.length!=1536)return Float.POSITIVE_INFINITY;
      for(float f:seen)if(f!=0)return 1f;
      return 0;
    }
  }
  static final class FakeModel {
    String lastCompletedRule="";
    final Map<String,Watch> rules=new HashMap<>();
    Watch rule(String id){return rules.get(id);}
  }
  static Map<String,float[]> none(){return new HashMap<>();}
  public static void main(String[] args){
    Split split=new Split();
    split.index=1;
    RewardV158Scheduler.afterSplit(split,4000);
    ok(split.index==1,"without verified evidence keep original order");
    final String pkg="com.kurashiru.coin.drop";
    FakeModel m=new FakeModel();
    m.rules.put("coin-default-get-tap",new Watch("claim"));
    m.rules.put("coin-default-claim-shown",new Watch("reward"));
    Object first=split.slots[0];
    RewardV158Observer.observe(first,pkg,m,none(),1000,1040,false,true);
    RewardV158Observer.balance(first,pkg,100,1100,1140);
    m.lastCompletedRule="coin-default-get-tap";
    RewardV158Observer.observe(first,pkg,m,none(),1600,1640,false,false);
    Map<String,float[]> proof=none();proof.put("reward",new float[1536]);
    RewardV158Observer.observe(first,pkg,m,proof,2200,2240,false,false);
    RewardV158Observer.observe(first,pkg,m,proof,2800,2840,false,false);
    RewardV158Observer.balance(first,pkg,125,3000,3040);
    RewardV158Observer.balance(first,pkg,125,3500,3540);
    ok(RewardV158Observer.cycles(first)==1,"one measured receipt");
    ok(RewardV158Observer.score(first)>0,"positive confirmed score");
    RewardV158Scheduler.afterSplit(split,4000);
    ok(split.index==0,"reward evidence changes next sequential lane");
    RewardV158Scheduler.afterSplit(split,4200);
    ok(split.index==1,"fairness serves other lane");
    split.parallelVirtual=true;split.index=0;
    RewardV158Scheduler.afterSplit(split,4400);
    ok(split.index==0,"independent parallel virtual lanes remain untouched");
    split.parallelVirtual=false;split.active=false;split.index=1;
    RewardV158Scheduler.afterSplit(split,4600);
    ok(split.index==1,"inactive run left untouched");
    split.active=true;split.slots[0].stopped=true;split.index=0;
    RewardV158Scheduler.afterSplit(split,4800);
    ok(split.index==1,"stopped reward lane never selected");
    System.out.println("RewardV158SchedulerTest: "+count+" checks passed");
  }
}
