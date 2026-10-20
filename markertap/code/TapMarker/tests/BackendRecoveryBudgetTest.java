package jp.tapmarker;
public final class BackendRecoveryBudgetTest {
 static int checks;static void check(boolean value,String reason){checks++;if(!value)throw new AssertionError(reason);}
 public static void main(String[] args){
  BackendRecoveryBudget p=new BackendRecoveryBudget();
  check(p.request(0),"first backend death can recover");
  check(p.request(1000),"second backend death can recover");
  check(p.request(2000),"third backend death can recover");
  check(!p.request(3000),"repeated crashes cannot recreate all displays indefinitely");
  check(!p.request(599999),"budget holds for the complete time window");
  check(p.request(600000),"only the expired first failure is removed");
  check(!p.request(600001),"recent failures still count after the first expires");
  check(p.request(601000),"the next expired failure frees one attempt");
  p.reset();check(p.request(601001),"an explicit user start resets the budget");
  System.out.println("BackendRecoveryBudgetTest: "+checks+" checks passed; Android process termination not simulated");
 }
}
