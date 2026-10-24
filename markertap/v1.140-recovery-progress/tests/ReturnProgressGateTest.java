package jp.tapmarker;

public final class ReturnProgressGateTest {
 static int checks;
 static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
 static ReturnProgressGate.Decision decide(ReturnProgressGate g,int request,String rule,long epoch,long window,long frame,long now,boolean cooling){
  return g.decide(request,rule,"farm-default-verify",epoch,window,frame,now,cooling);
 }
 public static void main(String[] args){
  ReturnProgressGate g=new ReturnProgressGate();
  check(decide(g,1,"close",1,7,1000,1000,false)==ReturnProgressGate.Decision.WAIT,"single match cannot cancel");
  check(decide(g,1,"close",1,7,1000,1200,false)==ReturnProgressGate.Decision.WAIT,"same cached image cannot confirm");
  check(decide(g,1,"close",1,7,1400,1400,false)==ReturnProgressGate.Decision.CANCEL_RETURN,"two distinct fresh frames confirm");
  check(decide(g,2,"close",2,7,1800,1800,false)==ReturnProgressGate.Decision.WAIT,"state re-entry needs new confirmation");
  check(decide(g,2,"close",2,8,2100,2100,false)==ReturnProgressGate.Decision.WAIT,"window change invalidates the first image");
  check(decide(g,2,"close",2,8,2400,2400,false)==ReturnProgressGate.Decision.CANCEL_RETURN,"new window can be confirmed independently");
  check(decide(g,3,"close",3,8,2800,2800,false)==ReturnProgressGate.Decision.WAIT,"third request waits");
  check(decide(g,3,"close",3,8,3200,3200,false)==ReturnProgressGate.Decision.CANCEL_RETURN,"third cancellation allowed");
  check(decide(g,4,"close",4,8,3600,3600,false)==ReturnProgressGate.Decision.ALLOW_RETURN,"same-state re-entry does not replenish the cancellation budget");
  check(decide(g,4,"close",4,8,94000,94000,false)==ReturnProgressGate.Decision.STOP,"episode deadline survives re-entry");
  g.home(true,94500,8,94500);g.home(true,94500,8,94600);
  check(g.expired(94600),"duplicate home image cannot clear failure");
  g.home(true,94900,8,94900);
  check(!g.expired(94900),"two fresh home observations clear the episode");
  check(decide(g,5,"close",5,8,95200,95200,false)==ReturnProgressGate.Decision.WAIT,"successful return replenishes budget");
  check(decide(g,5,"close",5,8,95600,95600,false)==ReturnProgressGate.Decision.CANCEL_RETURN,"post-recovery controls can work again");
  ReturnProgressGate stale=new ReturnProgressGate();
  check(decide(stale,1,"close",1,1,1000,2500,false)==ReturnProgressGate.Decision.ALLOW_RETURN,"1500ms boundary never permits image-based cancellation");
  check(decide(stale,2,"close",1,1,4000,3000,false)==ReturnProgressGate.Decision.ALLOW_RETURN,"future image rejected");
  ReturnProgressGate flicker=new ReturnProgressGate();
  check(decide(flicker,1,"a",1,1,5000,5000,false)==ReturnProgressGate.Decision.WAIT,"flickering candidate begins wait");
  check(decide(flicker,1,"b",1,1,6000,6000,false)==ReturnProgressGate.Decision.WAIT,"different candidate is not confirmation");
  check(decide(flicker,1,"a",1,1,7500,7500,false)==ReturnProgressGate.Decision.ALLOW_RETURN,"flicker cannot extend wait forever");
  ReturnProgressGate cooldown=new ReturnProgressGate();
  check(decide(cooldown,1,"close",1,1,10000,10000,true)==ReturnProgressGate.Decision.WAIT,"cooldown waits without cancelling");
  check(decide(cooldown,1,"close",1,1,12600,12600,true)==ReturnProgressGate.Decision.ALLOW_RETURN,"cooldown has finite deadline");
  check(decide(cooldown,2,"",1,1,13000,13000,false)==ReturnProgressGate.Decision.ALLOW_RETURN,"no candidate uses original recovery");
  ReturnProgressGate gap=new ReturnProgressGate();
  check(decide(gap,1,"close",1,1,20000,20000,false)==ReturnProgressGate.Decision.WAIT,"new confirmation sequence");
  check(decide(gap,1,"close",1,1,21500,21500,false)==ReturnProgressGate.Decision.WAIT,"expired first image cannot confirm the next image");
  check(decide(gap,1,"close",1,1,21800,21800,false)==ReturnProgressGate.Decision.CANCEL_RETURN,"a fresh replacement pair can confirm");
  System.out.println("ReturnProgressGateTest: "+checks+" regression checks passed");
 }
}
