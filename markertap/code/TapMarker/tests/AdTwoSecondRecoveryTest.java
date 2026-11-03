package jp.tapmarker;
public final class AdTwoSecondRecoveryTest {
 static int checks;
 static void ok(boolean result,String label){checks++;if(!result)throw new AssertionError(label);}
 static boolean sample(AdTwoSecondRecovery p,long captured,long now,boolean ad,boolean home){return p.observe("com.kurashiru.kaitaku.usagi",captured,now,ad,home);}
 public static void main(String[]args){
  AdTwoSecondRecovery p=new AdTwoSecondRecovery();
  ok(!sample(p,100,100,true,false),"first ad frame is not enough");
  ok(!sample(p,100,1100,true,false),"duplicate screenshot cannot advance");
  ok(!sample(p,1400,1400,true,false),"less than 2 seconds");
  ok(sample(p,2100,2100,true,false),"2 seconds of fresh ad causes recovery");
  ok(p.step()==AdTwoSecondRecovery.Step.HOME,"HOME first");
  ok(!p.confirmedKill(2110,true),"kill cannot precede HOME");
  ok(!p.confirmedHome(2200,false),"HOME unconfirmed must fail closed");
  ok(p.step()==AdTwoSecondRecovery.Step.HOLD,"unconfirmed HOME blocks intent");
  p.reset();sample(p,100,100,true,false);
  ok(!sample(p,150,2300,true,false),"stale frame cannot trigger");
  ok(!sample(p,3000,3000,false,true),"game returned cancels ad timer");
  ok(!sample(p,4000,4000,true,false),"new ad starts new interval");
  ok(sample(p,6200,6200,true,false),"next 2 seconds triggers");
  ok(p.confirmedHome(6300,true),"confirmed launcher allows stop");
  ok(p.step()==AdTwoSecondRecovery.Step.KILL,"kill second");
  ok(!p.confirmedIntent(6301,true),"cannot launch without confirmed stop");
  ok(p.confirmedKill(6400,true),"kill accepted");
  ok(p.step()==AdTwoSecondRecovery.Step.INTENT,"intent third");
  ok(p.confirmedIntent(6500,true),"launch accepted");
  ok(p.step()==AdTwoSecondRecovery.Step.VERIFY,"must still verify game");
  ok(!p.verifiedGame(6600,false),"intent not proof of return");
  ok(p.verifiedGame(6800,true),"fresh home verification ends episode");
  ok(p.step()==AdTwoSecondRecovery.Step.HOLD,"cooldown prevents loop");
  ok(!sample(p,7000,7000,true,false),"cooldown still active");
  AdTwoSecondRecovery other=new AdTwoSecondRecovery();
  sample(other,4000,4000,true,false);
  ok(sample(other,6000,6000,true,false),"independent game lane");
  other.failed(6200);ok(other.step()==AdTwoSecondRecovery.Step.HOLD,"failed backend does not retry infinitely");
  AdTwoSecondRecovery a=new AdTwoSecondRecovery();
  ok(!sample(a,100,100,true,false),"budget one start");
  ok(sample(a,2100,2100,true,false),"budget one triggered");a.failed(2100);
  ok(!sample(a,2200,2200,true,false),"cooldown prevents second instantly");
  ok(!sample(a,70000,70000,true,false),"second needs 2 seconds");
  ok(sample(a,72100,72100,true,false),"second bounded attempt");a.failed(72200);
  ok(!sample(a,140000,140000,true,false),"no third within 15-minute budget");
  System.out.println("AdTwoSecondRecoveryTest: "+checks+" checks passed");
 }
}
