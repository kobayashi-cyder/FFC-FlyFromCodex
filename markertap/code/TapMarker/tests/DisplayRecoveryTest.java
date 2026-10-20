package jp.tapmarker;

public final class DisplayRecoveryTest {
 static int checks;static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 public static void main(String[] args){
  DisplayRecovery a=new DisplayRecovery(),other=new DisplayRecovery();
  check(a.poll(true,1000)==DisplayRecovery.NONE,"settle after exhausting task restarts");
  check(a.poll(true,30999)==DisplayRecovery.NONE,"do not rebuild early");
  check(a.poll(true,31000)==DisplayRecovery.REBUILD,"finite escalation replaces indefinite wait");
  int ticket=a.begin(31000);check(ticket>=0&&a.current(ticket),"attempt is charged before dispatch");
  check(a.begin(31001)<0,"one unresolved episode cannot create overlapping displays");
  check(other.poll(false,90000)==DisplayRecovery.NONE,"other display remains independent");
  check(a.finished(ticket,true,32000)&&!a.pending(),"creation acknowledgement is not home");
  check(a.poll(true,121999)==DisplayRecovery.NONE,"new display gets a bounded recognition period");
  check(a.poll(true,122000)==DisplayRecovery.STOP,"unknown images cannot hold the lane forever");
  a.cancel();check(!a.finished(ticket,true,122001),"late creation callback cannot resume a stopped lane");
  check(a.poll(true,130000)==DisplayRecovery.STOP,"stopping does not replenish the failed episode");
  a.observeHome(true,130001,130001);check(a.poll(true,130001)==DisplayRecovery.STOP,"one home image cannot erase the attempt");
  a.observeHome(true,130001,130400);check(a.poll(true,130400)==DisplayRecovery.STOP,"same image is not a second observation");
  a.observeHome(true,130401,130401);check(a.poll(false,130401)==DisplayRecovery.NONE,"two fresh home images release a later episode");
  a.poll(true,140000);check(a.poll(true,170000)==DisplayRecovery.REBUILD,"a later verified episode may rebuild once");
  ticket=a.begin(170000);check(a.poll(true,199999)==DisplayRecovery.NONE&&a.poll(true,200000)==DisplayRecovery.STOP,"creation timeout is bounded too");
  a.cancel();check(!a.finished(ticket,true,200001),"timed-out request is invalidated");
  DisplayRecovery stale=new DisplayRecovery();stale.observeHome(true,1000,1000);stale.observeHome(true,1400,4000);stale.poll(true,4000);check(stale.poll(true,34000)==DisplayRecovery.REBUILD,"stale home cannot block recovery");
  DisplayRecovery failed=new DisplayRecovery();ticket=failed.begin(0);check(failed.finished(ticket,false,10)&&failed.poll(true,10)==DisplayRecovery.STOP,"creation failure stops only its lane without another attempt");
  System.out.println("DisplayRecoveryTest: "+checks+" checks; bounded rebuild, late callbacks, per-display isolation and verified-home release; Android display creation untested");
 }
}
