package jp.tapmarker;
public class AutomationRouterTest {
 static int checks;
 static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 static final class Session implements AutomationRouter.Host {
  String foreground="com.google.android.apps.nexuslauncher";
  boolean virtual=true,active;int stops,virtualStarts,physicalReads;
  VirtualPreparation preparation=new VirtualPreparation(2,false);
  public boolean automationRunning(){return active;}
  public boolean virtualSession(){return virtual;}
  public void stopAutomation(){stops++;active=false;preparation.cancel();}
  public void startVirtualAutomation(){virtualStarts++;preparation.requestRun();settle();}
  public void startSingleAutomation(){physicalReads++;if(virtual)throw new AssertionError("physical app consulted while virtual displays exist: "+foreground);active=true;}
  void frame(int index){check(preparation.begin(index),"independent virtual display starts");preparation.launched(index);preparation.confirm(index);settle();}
  void settle(){if(preparation.shouldRun())active=true;}
 }
 public static void main(String[] args){
  // The supplied screenshot has two minimized displays, a launcher profile, and a foreground chat app.
  for(String foreground:new String[]{"com.google.android.apps.nexuslauncher","com.openai.chatgpt",UsagiPreset.APP,FarmPreset.APP}){
   Session s=new Session();s.foreground=foreground;s.frame(0);s.frame(1);
   check(!s.active,"display restoration without a start request remains stopped");
   AutomationRouter.control(s);
   check(s.active&&s.virtualStarts==1&&s.physicalReads==0,"existing virtual session starts without opening the game on the phone");
   AutomationRouter.start(s,false);
   check(s.active&&s.virtualStarts==1&&s.stops==0,"repeated start does not reset running automata");
   AutomationRouter.control(s);
   check(s.active&&s.stops==0,"virtual control is start-only even when pressed again");
   s.stopAutomation();
   check(!s.active&&s.stops==1&&s.physicalReads==0,"explicit full-stop control stops the virtual session");
   s.settle();check(!s.active,"late readiness after stop cannot resume autonomy");
  }
  Session explicit=new Session();explicit.frame(0);explicit.frame(1);AutomationRouter.start(explicit,true);
  check(explicit.active&&explicit.virtualStarts==1,"new explicit start/stop button starts virtual displays");
  AutomationRouter.start(explicit,true);check(!explicit.active&&explicit.stops==1,"button labelled stop actually stops a running virtual session");
  explicit.settle();check(!explicit.active,"readiness callbacks cannot undo the explicit stop");
  Session pending=new Session();pending.frame(0);
  AutomationRouter.start(pending,false);
  check(!pending.active&&pending.stops==0,"start during preparation waits instead of canceling the other display");
  pending.frame(1);check(pending.active&&pending.physicalReads==0,"second confirmed display completes the deferred start");
  Session stopped=new Session();stopped.frame(0);AutomationRouter.start(stopped,false);stopped.stopAutomation();
  stopped.preparation.confirm(1);stopped.settle();check(!stopped.active&&!stopped.preparation.requestRun(),"stop rejects late callbacks and an obsolete start gate");
  Session physical=new Session();physical.virtual=false;AutomationRouter.start(physical,false);
  check(physical.active&&physical.physicalReads==1&&physical.virtualStarts==0,"single-app behavior is retained when no virtual session is selected");
  AutomationRouter.control(physical);check(!physical.active&&physical.stops==1,"physical single-app toggle remains available");
  System.out.println("AutomationRouterTest: "+checks+" checks passed; screenshot routing/preparation regression, Android UI not tested");
 }
}
