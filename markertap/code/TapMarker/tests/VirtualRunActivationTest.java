package jp.tapmarker;
public class VirtualRunActivationTest {
 static int checks;
 static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
 static final class Session implements VirtualRunActivation.Host {
  final VirtualPreparation preparation=new VirtualPreparation(2,true);final DisplayLane first=new DisplayLane(),second=new DisplayLane();
  boolean active,physical=true,canArm=true;int physicalStops,arms,activations;
  Session(){for(int i=0;i<2;i++){preparation.begin(i);preparation.launched(i);preparation.confirm(i);}}
  public boolean running(){return active;}
  public void preparePhysical(){physical=false;physicalStops++;}
  public boolean arm(){arms++;return canArm;}
  public void activate(){active=true;activations++;}
  void fullStop(){active=false;preparation.cancel();first.cancel();second.cancel();}
 }
 public static void main(String[] args){
  Session session=new Session();long a=session.first.enter(),b=session.second.enter();
  check(VirtualRunActivation.start(session),"prepared session starts");
  check(session.active&&!session.physical&&session.activations==1&&session.arms==1,"physical operation stops before virtual activation");
  check(session.preparation.shouldRun(),"starting must not cancel the all-displays-ready gate");
  check(session.first.valid(a)&&session.second.valid(b),"starting must not run the full-stop input cancellation path");
  for(int i=0;i<20;i++)check(VirtualRunActivation.start(session),"repeated start is idempotent");
  check(session.physicalStops==1&&session.activations==1&&session.arms==1,"repeated start cannot stop or reset both games");
  check(!VirtualRunActivation.manualAllowed(true,false),"preview touches during autonomy cannot call global stop");
  check(!VirtualRunActivation.manualAllowed(false,true),"preview touches cannot cancel another game's preparation");
  check(!VirtualRunActivation.manualAllowed(true,true),"running/preparing state consumes preview touch");
  check(VirtualRunActivation.manualAllowed(false,false),"manual preview input remains available after explicit stop");
  session.fullStop();check(!session.active&&!session.preparation.shouldRun()&&!session.first.valid(a)&&!session.second.valid(b),"explicit full stop still cancels every lane and late preparation");
  Session failure=new Session();failure.canArm=false;
  check(!VirtualRunActivation.start(failure)&&!failure.active&&failure.activations==0,"input preparation failure never claims autonomy is running");
  check(failure.preparation.shouldRun(),"failed arming does not destroy visible displays' readiness");
  failure.canArm=true;check(VirtualRunActivation.start(failure)&&failure.active,"arming may be retried without cancelling display setup");
  System.out.println("VirtualRunActivationTest: "+checks+" checks passed; real preparation/input lanes, Android UI not tested");
 }
}
