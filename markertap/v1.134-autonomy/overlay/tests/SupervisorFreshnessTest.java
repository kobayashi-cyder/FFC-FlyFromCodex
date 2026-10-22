package jp.tapmarker;
public final class SupervisorFreshnessTest {
 static int checks;
 static void check(boolean v,String why){checks++;if(!v)throw new AssertionError(why);}
 static void invalidSuccess(){for(long at:new long[]{0,1000,4000}){AutonomySupervisor s=new AutonomySupervisor();int t=s.begin(1000);s.complete(t,at,2501,true);check(s.successCount()==0,"invalid success rejected");check(s.recoveryDue(32501),"invalid success cannot clear outage");}}
 static void repeatedImage(){AutonomySupervisor s=new AutonomySupervisor();int t=s.begin(1000);s.complete(t,1100,1200,true);t=s.begin(1200);s.complete(t,1100,1300,true);check(s.successCount()==1,"duplicate frame rejected");t=s.begin(1300);s.complete(t,1050,1400,true);check(s.successCount()==1,"reordered frame rejected");t=s.begin(1400);s.complete(t,1500,1600,true);check(s.successCount()==2,"fresh distinct frame accepted");}
 static void cachedHome(){AutonomySupervisor s=new AutonomySupervisor();int t=s.begin(1000);s.complete(t,1100,1200,true);t=s.begin(1300);s.complete(t,0,1400,false);for(long now=1500;now<=32000;now+=500)s.observeState("main",now,true);check(s.recoveryDue(31400),"cached home cannot erase outage");check(s.retryDelay()>=400,"failure retained");t=s.begin(32000);s.complete(t,32100,32200,true);check(!s.recoveryDue(70000),"fresh success clears outage");}
 static void futureFrame(){AutonomySupervisor s=new AutonomySupervisor();check(s.tick(1000,5000,false,false)==AutonomySupervisor.REFRESH,"future frame recaptured");}
 public static void main(String[] args){invalidSuccess();repeatedImage();cachedHome();futureFrame();System.out.println("SupervisorFreshnessTest: "+checks+" passed");}
}
