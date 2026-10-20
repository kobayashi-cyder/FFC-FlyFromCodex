package jp.tapmarker;
public class VirtualPreparationTest {
 static int checks;
 static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 public static void main(String[] args){
  // Reproduce the screenshot: first display refuses launch before the second surface is ready.
  VirtualPreparation start=new VirtualPreparation(2);
  check(start.begin(0),"first surface accepted");start.fail(0);
  check(start.result()==VirtualPreparation.IN_PROGRESS,"failure must keep remaining surfaces eligible");
  check(start.begin(1),"second surface still initializes after first launch failure");
  check(start.launched(1),"second launch completes");start.confirm(1);
  check(start.result()==VirtualPreparation.FINISHED_WITH_ERRORS,"mixed result settles without automatic input");
  check(start.count(VirtualPreparation.CONFIRMED)==1&&start.count(VirtualPreparation.FAILED)==1,"both outcomes retained");
  // Each possible failed position in 2..32 displays must settle independent of callback order.
  for(int size=2;size<=32;size++)for(int bad=0;bad<size;bad++){
   VirtualPreparation group=new VirtualPreparation(size);check(group.begin(bad),"failed display starts");group.fail(bad);
   for(int i=size-1;i>=0;i--)if(i!=bad){check(group.begin(i),"remaining callback accepted");check(group.launched(i),"remaining launch completed");group.confirm(i);}
   check(group.result()==VirtualPreparation.FINISHED_WITH_ERRORS,"all callbacks finish");
   check(group.count(VirtualPreparation.CONFIRMED)==size-1,"successful windows preserved");
  }
  VirtualPreparation success=new VirtualPreparation(4);
  for(int i=0;i<4;i++){check(success.begin(i),"unique creation");check(!success.begin(i),"duplicate callback rejected");check(success.launched(i),"launch accepted");check(success.result()==VirtualPreparation.IN_PROGRESS,"launch request alone must not start autonomy");}
  for(int i=3;i>=0;i--)success.confirm(i);
  check(success.result()==VirtualPreparation.ALL_READY,"all application displays confirmed");
  VirtualPreparation stopped=new VirtualPreparation(3);stopped.begin(0);stopped.begin(1);stopped.launched(1);stopped.cancel();
  check(!stopped.launched(0)&&!stopped.begin(2),"stop rejects queued launch and delayed surface");stopped.confirm(1);
  check(stopped.result()==VirtualPreparation.CANCELLED&&stopped.count(VirtualPreparation.CONFIRMED)==0,"stale frame cannot restart autonomy");
  VirtualPreparation timeout=new VirtualPreparation(2);timeout.begin(0);timeout.begin(1);timeout.launched(1);timeout.fail(0);timeout.fail(1);
  check(!timeout.launched(0),"launch finishing after timeout rejected");timeout.confirm(1);
  check(timeout.result()==VirtualPreparation.FINISHED_WITH_ERRORS,"late frame cannot revive failed window");
  System.out.println("VirtualPreparationTest: "+checks+" checks passed; device display policy not tested");
 }
}
