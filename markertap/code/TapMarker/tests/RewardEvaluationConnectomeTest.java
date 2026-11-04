package jp.tapmarker;

/** JVM-only regression tests: no Android runtime or external data required. */
public final class RewardEvaluationConnectomeTest {
  private static int checks;
  private static void yes(boolean actual,String reason){
    checks++;if(!actual)throw new AssertionError(reason);
  }
  private static void eq(long want,long got,String reason){
    yes(want==got,reason+" expected="+want+" got="+got);
  }
  public static void main(String[]args){
    RewardEvaluationConnectome fast=new RewardEvaluationConnectome();
    RewardEvaluationConnectome slow=new RewardEvaluationConnectome();
    yes(!fast.claimSent("r1","coin",1000),"claim without a real baseline prohibited");
    fast.observeAd(100,100,true);
    fast.observeAd(1100,1100,true);
    fast.observeAd(2100,2100,false);
    eq(1000,fast.adMillis(),"only confirmed intervals count");
    slow.observeAd(100,100,true);
    slow.observeAd(1100,1100,true);
    slow.observeAd(2100,2100,true);
    slow.observeAd(3100,3100,false);
    yes(fast.score()>slow.score(),"shorter CM improves evaluation");
    double before=fast.score();

    yes(!fast.observeBalance("coin",100,4000,4000),"initial balance is not a receipt");
    yes(fast.claimSent("r1","coin",4100),"open claim with baseline");
    eq(0,fast.cycles(),"tap is not a completed reward cycle");
    eq(0,fast.quantity(),"tap is not a reward");
    yes(!fast.observeBalance("coin",110,4200,4200),"one balance sample is not enough");
    eq(0,fast.cycles(),"no UI proof: no cycle");
    yes(fast.claimAcknowledged("r1",4300),"registered post-claim UI proof");
    yes(!fast.observeBalance("coin",110,4400,4400),"first fresh post-UI balance not enough");
    yes(!fast.observeBalance("coin",110,4400,4401),"duplicate screenshot not evidence");
    yes(fast.observeBalance("coin",110,4500,4500),"second post-UI balance credits once");
    eq(1,fast.cycles(),"one actual credited cycle");
    eq(10,fast.quantity(),"actual verified delta, not arbitrary reward count");
    yes(fast.score()>before,"credited cycle and quantity improve evaluation");
    yes(!fast.claimSent("r1","coin",4600),"same receipt cannot be replayed");
    yes(!fast.observeBalance("coin",110,4700,4700),"no implicit second credit");
    eq(1,fast.cycles(),"repeated frames cannot inflate cycles");

    yes(fast.claimSent("r2","coin",4800),"second independent claim");
    yes(fast.claimAcknowledged("r2",4850),"second UI proof");
    yes(!fast.observeBalance("coin",120,4900,4900),"one new frame");
    fast.invalidatePending(); // HOME -> force-stop -> Intent, loses the claim
    yes(!fast.observeBalance("coin",120,5000,5000),"force-stop cannot pay pending claim");
    eq(1,fast.cycles(),"restart must not award a cycle");
    eq(10,fast.quantity(),"restart must not invent a reward");
    yes(fast.claimSent("r3","coin",5050),"third independent claim");
    yes(!fast.claimAcknowledged("alien",5100),"wrong ID is not proof");
    yes(!fast.observeBalance("coin",130,5200,5200),"balance increase alone prohibited");
    yes(!fast.observeBalance("coin",130,5300,5300),"no UI evidence, no credit");
    eq(1,fast.cycles(),"unverified balance does not count");
    fast.invalidatePending();

    RewardEvaluationConnectome virtual=new RewardEvaluationConnectome();
    virtual.observeBalance("coin",100,100,100);
    yes(virtual.claimSent("v1","coin",150),"virtual lane has independent claim");
    yes(virtual.claimAcknowledged("v1",200),"virtual lane UI");
    yes(!virtual.observeBalance("coin",110,300,300),"one virtual frame");
    yes(virtual.observeBalance("coin",110,400,400),"second virtual frame");
    eq(1,virtual.cycles(),"virtual lane completed");
    eq(1,fast.cycles(),"physical lane not affected");
    yes(!virtual.observeBalance("coin",100000,450,2400),"stale frame ignored");

    // A reward dialog can stay visible across multiple fresh screenshots.
    // A second UI acknowledgment must not erase the first valid time anchor.
    RewardEvaluationConnectome repeated=new RewardEvaluationConnectome();
    repeated.observeBalance("pt",600,3000,3000);
    yes(repeated.claimSent("repeat-ui","pt",3100),"receipt candidate opened");
    yes(repeated.claimAcknowledged("repeat-ui",3200),"first completed popup proof");
    yes(!repeated.observeBalance("pt",610,3300,3300),"post-popup balance frame one");
    yes(repeated.claimAcknowledged("repeat-ui",3350),"persistent popup does not shift original proof");
    yes(repeated.observeBalance("pt",610,3325,3375),"second screenshot after first proof still credits despite later repeated popup");
    eq(1,repeated.cycles(),"one UI episode one cycle");
    eq(10,repeated.quantity(),"only real pt delta counted");

    double after=virtual.score();
    virtual.observeAd(600,600,true);
    virtual.observeAd(1700,1700,true);
    yes(virtual.score()<after,"more CM dwell lowers score without extra reward");
    virtual.reset();
    eq(0,virtual.cycles(),"reset cycle");
    eq(0,virtual.quantity(),"reset quantity");
    eq(0,virtual.adMillis(),"reset CM dwell");
    System.out.println("RewardEvaluationConnectomeTest: "+checks+" checks passed");
  }
}
