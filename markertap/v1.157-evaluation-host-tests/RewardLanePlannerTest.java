package jp.tapmarker;

public final class RewardLanePlannerTest {
  private static int checks=0;
  static void ok(boolean value,String reason){
    checks++;if(!value)throw new AssertionError(reason);
  }
  static int choose(RewardLanePlanner p,boolean a,boolean b,
                    boolean verifyA,boolean verifyB,
                    long cyclesA,long cyclesB,double scoreA,double scoreB){
    return p.select(new boolean[]{a,b},new boolean[]{verifyA,verifyB},
        new long[]{cyclesA,cyclesB},new double[]{scoreA,scoreB});
  }
  public static void main(String[] args){
    RewardLanePlanner fair=new RewardLanePlanner();
    int[] served={0,0};
    for(int t=0;t<60;t++){
      int choice=choose(fair,true,true,false,false,0,0,0,0);
      ok(choice>=0&&choice<2,"eligible lane");
      served[choice]++;
      ok(Math.abs(served[0]-served[1])<=1,"ordinary fair discovery");
    }
    ok(served[0]==30&&served[1]==30,"no priority without proven reward");
    RewardLanePlanner reward=new RewardLanePlanner();
    ok(choose(reward,true,true,false,false,1,0,170,-4)==0,"confirmed objective preference");
    ok(choose(reward,true,true,false,false,1,0,170,-4)==1,"starvation bound");
    RewardLanePlanner ad=new RewardLanePlanner();
    ok(choose(ad,true,true,false,false,1,1,-120,100)==1,"less measured CM preferred");
    RewardLanePlanner proof=new RewardLanePlanner();
    ok(choose(proof,true,true,false,true,1,0,180,0)==1,"verification urgency");
    ok(choose(proof,true,true,false,true,1,0,180,0)==1,"bounded repeat verification");
    ok(choose(proof,true,true,false,true,1,0,180,0)==0,"verification cannot starve another lane");
    RewardLanePlanner disabled=new RewardLanePlanner();
    ok(choose(disabled,false,true,false,false,20,0,10000,0)==1,"stopped lane never selected");
    ok(choose(disabled,false,false,true,true,20,0,10000,0)==-1,"no eligible lane");
    ok(disabled.select(new boolean[]{true},new boolean[]{true,true},
        new long[]{0},new double[]{0})==-1,"malformed input fails closed");
    fair.reset();
    ok(choose(fair,true,true,false,false,0,0,0,0)==0,"reset restores deterministic baseline");
    System.out.println("RewardLanePlannerTest: "+checks+" checks passed");
  }
}
