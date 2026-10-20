package jp.tapmarker;
public class TargetedInputTest {
 static int checks;
 static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
 interface Attempt {void run()throws Exception;}
 static void rejected(Attempt run,String message)throws Exception{boolean rejected=false;try{run.run();}catch(IllegalArgumentException e){rejected=true;}check(rejected,message);}
 public static class Event {int id=-1;public int getDisplayId(){return id;}public void setDisplayId(int id){this.id=id;}}
 public static class BrokenEvent extends Event {public void setDisplayId(int id){}}
 public static class Manager {
  static final Manager INSTANCE=new Manager();int calls,untargetedCalls,lastUid,lastMode,lastDisplay;String failure;
  public static Manager getInstance(){return INSTANCE;}
  public boolean injectInputEvent(Event e,int mode,int uid){calls++;lastUid=uid;lastMode=mode;lastDisplay=e.id;if(failure!=null)throw new IllegalArgumentException(failure);return uid==10000+e.id;}
  public boolean injectInputEvent(Event e,int mode){untargetedCalls++;throw new AssertionError("untargeted input must never be used");}
 }
 public static class LegacyManager {static final LegacyManager INSTANCE=new LegacyManager();int calls,lastDisplay,lastMode;public static LegacyManager getInstance(){return INSTANCE;}public boolean injectInputEvent(Event e,int mode){calls++;lastDisplay=e.id;lastMode=mode;return true;}}
 public static void main(String[] args)throws Exception{
  TargetedInput input=new TargetedInput(Manager.class,Event.class);Manager manager=Manager.INSTANCE;
  for(int id=1;id<=24;id++){
   Event event=new Event();input.display(event,id);check(event.id==id,"virtual display is applied and read back");
   check(input.inject(event,id,10000+id),"targeted adapter passes expected UID");
   check(manager.lastDisplay==id&&manager.lastUid==10000+id&&manager.lastMode==2,"display, UID and completion mode retained for every independent lane");
  }
  int before=manager.calls;Event wrong=new Event();wrong.id=8;
  rejected(()->input.inject(wrong,9,10009),"wrong display ID is rejected before injection");
  rejected(()->input.inject(wrong,0,10008),"physical display target is rejected");
  rejected(()->input.display(new Event(),0),"cannot configure physical display input");
  rejected(()->input.display(new Event(),-1),"cannot leave the display unspecified");
  rejected(()->input.display(new BrokenEvent(),9),"failed display assignment cannot silently reach the phone");
  rejected(()->input.inject(wrong,8,-1),"invalid UID cannot request unrestricted injection");
  rejected(()->input.inject(wrong,8,0),"root UID cannot be used as an app target");
  check(manager.calls==before,"invalid targets never reach input manager");
  check(!input.inject(wrong,8,10009),"target mismatch result is propagated without retrying unrestricted input");
  check(manager.untargetedCalls==0,"untargeted overload was not invoked");
  boolean unsupported=false;try{new TargetedInput(LegacyManager.class,Event.class);}catch(NoSuchMethodException e){unsupported=true;}
  check(unsupported,"missing UID-target overload is an explicit unsupported result");
  int[] live={7,10007},guardCalls={0};
  TargetedInput legacy=new TargetedInput(LegacyManager.class,Event.class,(id,uid)->{guardCalls[0]++;return id==live[0]&&uid==live[1];});
  LegacyManager old=LegacyManager.INSTANCE;Event event=new Event();legacy.display(event,7);
  check(legacy.legacy&&legacy.inject(event,7,10007),"Android 11/12 overload works only with a supplied live guard");
  check(old.lastDisplay==7&&old.lastMode==2&&guardCalls[0]==1,"legacy injection retains positive display ID and completion mode");
  live[1]=10008;check(!legacy.inject(event,7,10007)&&old.calls==1,"changed app identity denies the next event");
  live[0]=8;check(!legacy.inject(event,7,10007)&&old.calls==1,"changed display ownership denies the next event");
  int guarded=guardCalls[0];rejected(()->legacy.inject(event,0,10007),"legacy input cannot target the phone");rejected(()->legacy.inject(event,7,0),"legacy input requires an app UID");
  check(guardCalls[0]==guarded&&old.calls==1,"invalid legacy events stop before querying or injecting");
  TargetedInput brokenGuard=new TargetedInput(LegacyManager.class,Event.class,(id,uid)->{throw new IllegalStateException("task query unavailable");});
  boolean guardFailed=false;try{brokenGuard.inject(event,7,10007);}catch(IllegalStateException expected){guardFailed=true;}
  check(guardFailed&&old.calls==1,"unreadable task state cannot cause an unguarded retry");
  TargetedInput modern=new TargetedInput(Manager.class,Event.class,(id,uid)->{throw new AssertionError("modern API must use atomic UID targeting");});
  check(!modern.legacy&&modern.inject(event,7,10007),"modern overload is always preferred even when a legacy guard is supplied");
  check(!modern.inject(event,7,10008)&&manager.untargetedCalls==0,"modern rejection never retries the old overload");
  before=manager.calls;manager.failure="Targeted input event injection from pid 14305 was not directed at a window owned by uid 10469.";
  boolean changed=false;try{modern.inject(event,7,10007);}catch(IllegalStateException expected){changed=InputTargetRecovery.changed(expected.getMessage())&&expected.getMessage().contains("display=7 uid=10007")&&expected.getCause() instanceof IllegalArgumentException;}
  check(changed,"real Android exception becomes a recoverable target-change with display and UID diagnostics");
  check(manager.calls==before+1&&manager.untargetedCalls==0,"UID mismatch never retries another UID or unrestricted overload");
  manager.failure="bad coordinates";boolean original=false;try{modern.inject(event,7,10007);}catch(java.lang.reflect.InvocationTargetException expected){original="bad coordinates".equals(expected.getCause().getMessage());}
  check(original,"non-target exceptions retain their original failure");manager.failure=null;
  check(modern.inject(event,7,10007),"same targeted adapter can send again after the target has returned");
  System.out.println("TargetedInputTest: "+checks+" checks passed; actual reflective adapter with stub input manager, Android injection not tested");
 }
}
