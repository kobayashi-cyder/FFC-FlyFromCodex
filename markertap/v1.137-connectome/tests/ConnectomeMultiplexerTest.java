package jp.tapmarker;

public final class ConnectomeMultiplexerTest {
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 public static void main(String[] args){
  ConnectomeMultiplexer c=new ConnectomeMultiplexer();
  c.start(new String[]{"usagi","farm"});
  check(c.laneCount()==2,"two independently registered lanes");
  check(c.offer("usagi",16,1000,0,1150).admit,"first frame admitted");
  check(c.offer("farm",17,1020,0,1150).admit,"other display not blocked");
  check(!c.offer("usagi",16,1000,0,1200).admit,"duplicate refused");
  check(c.offer("usagi",16,1000,0,1200).requestRefresh,"duplicate requests new frame");
  check(c.offer("usagi",16,1300,1000,1350).admit,"new frame admitted");
  c.published("usagi",1300);
  check(c.report("usagi").contains("observe=1"),"publication propagated");
  check(c.report("farm").contains("observe=0"),"state not leaked");
  check(!c.offer("farm",17,1100,0,2800).admit,"expired frame rejected");
  check(!c.offer("farm",17,3000,0,2800).admit,"future frame rejected");
  check(c.offer("farm",18,2000,0,2050).admit,"recreated display resets watermark");
  check(c.totalAdmissions()==4,"admission accounting");
  for(int i=0;i<20;i++){
   ConnectomeMultiplexer.Decision d=c.offer("farm",18,2000,0,2150);
   check(!d.admit&&d.retryMs<=350&&d.retryMs>=125,"bounded wake on stalled lane");
  }
  check(!c.offer("unknown",18,2000,0,2150).admit,"unregistered lane must fail closed");
  c.reset();check(c.laneCount()==0,"stop clears state");
  c.start(new String[]{"usagi"});
  check(c.offer("usagi",21,100,0,200).admit,"new run not poisoned by prior run");
  boolean failed=false;
  try{c.start(new String[]{"same","same"});}catch(IllegalArgumentException expected){failed=true;}
  check(failed,"duplicate lane rejected");
  System.out.println("ConnectomeMultiplexerTest: 13 invariants passed");
 }
}
