package jp.tapmarker;

/** True passive payout must include prior playable wallet, payout UI and wallet increase. */
public final class FarmPassiveRewardTest {
  static int count;
  static void yes(boolean ok,String message){count++;if(!ok)throw new AssertionError(message);}
  static void eq(long exp,long actual,String note){yes(exp==actual,note+": "+actual);}
  public static void main(String[]args){
    RewardEvaluationConnectome score=new RewardEvaluationConnectome();
    FarmPassiveReward gate=new FarmPassiveReward();
    yes(gate.observe(false,true,110,100,100,score).isEmpty(),
        "popup alone, no baseline: no passive credit");
    eq(0,score.cycles(),"no initial reward");
    score.observeBalance("pt",100,200,200);
    yes(gate.observe(true,false,100,200,200,score).isEmpty(),"record trusted playable baseline");
    String first=gate.observe(false,true,110,300,300,score);
    yes(!first.isEmpty(),"first reward popup opens a provisional claim");
    yes(!score.observeBalance("pt",110,300,300),"popup and first sample cannot award");
    eq(0,score.cycles(),"one frame no credit");
    yes(first.equals(gate.observe(false,true,110,400,400,score)),"same active receipt");
    yes(!score.observeBalance("pt",110,400,400),"only first post-popup balance");
    eq(0,score.cycles(),"must observe twice");
    yes(gate.observe(false,false,110,500,500,score).equals(first),"claim survives popup close");
    yes(!score.observeBalance("pt",110,500,500),"first strictly post-UI balance is insufficient");
    yes(score.observeBalance("pt",110,600,600),"second strictly post-UI stable balance credits farm payout");
    eq(1,score.cycles(),"exactly one farm cycle");
    eq(10,score.quantity(),"only 10 received pt");
    SharedPointClaimArbiter.release(score,first);
    gate.completed();
    yes(!score.observeBalance("pt",110,650,650),"cannot recount same balance");
    eq(1,score.cycles(),"no repeated credit");
    // Played again but no additional points deposited.
    yes(gate.observe(true,false,110,700,700,score).isEmpty(),"new playable baseline");
    yes(!score.observeBalance("pt",110,700,700),"new gameplay frame records wallet");
    String second=gate.observe(false,true,110,800,800,score);
    yes(!second.isEmpty(),"second candidate may open");
    score.observeBalance("pt",110,800,800);
    gate.observe(false,true,110,900,900,score);
    score.observeBalance("pt",110,900,900);
    gate.observe(false,false,110,1000,1000,score);
    yes(!score.observeBalance("pt",110,1000,1000),"no increase means no credit");
    eq(1,score.cycles(),"no phantom farm cycle");
    SharedPointClaimArbiter.release(score,second);
    score.invalidatePending();gate.reset();
    System.out.println("FarmPassiveRewardTest: "+count+" checks passed");
  }
}
