package jp.tapmarker;
public final class RecoveryGuardReuseTest {
 public static void main(String[] args){
  DisplayRecovery guard=new DisplayRecovery();
  if(guard.poll(true,1000)!=DisplayRecovery.NONE)throw new AssertionError("premature rebuild");
  if(guard.poll(true,31000)!=DisplayRecovery.REBUILD)throw new AssertionError("missing rebuild");
  int ticket=guard.begin(31000);
  if(!guard.finished(ticket,true,32000))throw new AssertionError("no rebuild receipt");
  DisplayRecovery newRunnerOnSameWindow=guard;
  if(newRunnerOnSameWindow.poll(true,62000)!=DisplayRecovery.NONE)
   throw new AssertionError("stop/start must not grant another rebuild");
  if(newRunnerOnSameWindow.poll(true,122000)!=DisplayRecovery.STOP)
   throw new AssertionError("one unresolved episode must stop after bounded verification");
  guard.observeHome(true,122100,122100);
  if(guard.poll(true,122100)!=DisplayRecovery.STOP)throw new AssertionError("one home frame cleared guard");
  guard.observeHome(true,122500,122500);
  if(guard.poll(false,122500)!=DisplayRecovery.NONE)
   throw new AssertionError("two fresh spaced home frames should release guard");
  System.out.println("RecoveryGuardReuseTest: passed");
 }
}
