package jp.tapmarker;
public final class RestoreReceiptTest {
 static int checks;static void check(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
 public static void main(String[] args){
  RestoreReceipt r=new RestoreReceipt();int ticket=r.begin(true,1000);check(r.requests==1&&r.restartRequests==1&&r.accepted==0,"dispatch is only a request");
  r.observePackage(true,1100,1100);r.observeHome(true,1100,1100);check(r.packageConfirmed==0&&r.homeConfirmed==0,"unacknowledged requests prove nothing");
  r.accepted(ticket,1200);check(r.accepted==1&&r.packageConfirmed==0,"acknowledgement is not an app response");
  r.observePackage(true,1199,1300);r.observeHome(true,1199,1300);check(r.packageConfirmed==0,"pre-request pixels are rejected");
  r.observePackage(true,1300,1300);r.observePackage(true,1300,1650);check(r.packageConfirmed==0,"duplicate frame does not confirm the package");
  r.observePackage(false,1650,1650);r.observePackage(true,2000,2000);check(r.packageConfirmed==0,"foreign foreground resets confirmation");
  r.observePackage(true,2350,2350);check(r.packageConfirmed==1&&r.homeConfirmed==0,"two own-app frames can still show an unresolved popup");
  r.observeHome(false,2350,2350);r.observeHome(true,2700,2700);r.observeHome(true,3050,3050);check(r.homeConfirmed==1,"only stable home evidence confirms visual recovery");
  int newer=r.begin(false,4000);r.failed(ticket);r.accepted(ticket,4100);check(r.accepted==1&&r.failures==0,"old callbacks cannot complete a newer request");
  r.accepted(newer,4100);r.observePackage(true,4200,6000);check(r.packageConfirmed==1,"expired pixels do not replenish confirmation");
  r.failed(newer);check(r.failures==1&&!r.report().contains("主画面を確認"),"failed latest dispatch is reported explicitly");
  System.out.println("RestoreReceiptTest: "+checks+" checks passed; no restart is inferred from an intent acknowledgement");
 }
}
