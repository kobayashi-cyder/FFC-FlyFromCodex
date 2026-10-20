package jp.tapmarker;

import java.util.*;
import java.util.function.BooleanSupplier;

/** Runs the shipped executor with an asynchronous fake input device and virtual time. */
public final class TapperTest {
 static int checks;static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
 static final class Event implements Comparable<Event>{final long at,order;final Runnable task;Event(long at,long order,Runnable task){this.at=at;this.order=order;this.task=task;}public int compareTo(Event other){int c=Long.compare(at,other.at);return c==0?Long.compare(order,other.order):c;}}
 static final class Device implements Tapper.Clock,Tapper.Scheduler,Tapper.Driver,Tapper.Listener {
  long now,sequence,extraLatency;boolean ready=true,throwsInput;int outcome=Tapper.COMPLETED,maxPending;final PriorityQueue<Event> events=new PriorityQueue<>();
  final ArrayList<Long> starts=new ArrayList<>();final ArrayList<String> delivered=new ArrayList<>(),states=new ArrayList<>();final ArrayList<float[]> points=new ArrayList<>();final Map<Integer,Integer> pending=new HashMap<>(),byDisplay=new HashMap<>();Tapper.Result lastResult;
  public long now(){return now;}
  public void later(Runnable task,long delay){events.add(new Event(now+Math.max(0,delay),sequence++,task));}
  public boolean ready(Tapper.Command command){return ready;}
  public void send(Tapper.Command command,float[] point,BooleanSupplier current,Tapper.Result result){
   if(throwsInput)throw new IllegalStateException("fake driver rejection");
   check(current.getAsBoolean(),"queued obsolete commands never reach the input device");starts.add(now);points.add(point);byDisplay.put(command.displayId,byDisplay.getOrDefault(command.displayId,0)+1);
   int active=pending.getOrDefault(command.displayId,0)+1;pending.put(command.displayId,active);maxPending=Math.max(maxPending,active);lastResult=result;int value=outcome;
   later(()->{pending.put(command.displayId,pending.get(command.displayId)-1);result.finished(value);},command.pressMillis+extraLatency);
  }
  public void state(String state){states.add(state);}
  public void result(Tapper.Command command,int result){delivered.add(command.context+":"+result);}
  Tapper tapper(){return new Tapper(this,this,this,this);}
  void until(long end){int iterations=0;while(!events.isEmpty()&&events.peek().at<=end){if(++iterations>50000)throw new AssertionError("input loop did not yield");Event event=events.remove();now=event.at;event.task.run();}now=end;}
 }
 static Tapper.Command command(String context,long expires,long press,long period,boolean repeat){return new Tapper.Command("game",0,context,new int[]{0,0,540,1200},new float[]{270,900},press,period,expires,repeat);}
 static void fastestAndBackpressure(){
  Device d=new Device();Tapper tapper=d.tapper();tapper.update(command("play",2000,1,1,true));d.until(500);
  check(tapper.completed()==500&&tapper.sent()==501,"one-millisecond fake device is driven immediately on every completion without a 32ms or 60ms floor");
  check(d.maxPending==1,"fastest execution never overlaps down/up sequences on the same device");
  check(d.starts.get(1)==1&&d.starts.get(500)==500,"no extra post-completion sleep is added");
  Device slow=new Device();slow.extraLatency=79;Tapper input=slow.tapper();input.update(command("play",5000,1,1,true));slow.until(400);
  check(input.completed()==5&&slow.maxPending==1&&slow.starts.get(1)==80,"slow devices set the actual pace through completion backpressure");
  Device spaced=new Device();Tapper paced=spaced.tapper();paced.update(command("play",5000,1,50,true));spaced.until(200);
  check(spaced.starts.equals(Arrays.asList(0L,50L,100L,150L,200L)),"explicit slower intervals retain start-to-start timing");
 }
 static void logicAndExpiry(){
  Device d=new Device();Tapper tapper=d.tapper();tapper.update(command("play",1500,1,1,true));d.until(1000);
  check(tapper.completed()==1000,"the executor does not call image recognition or need a per-tap approval");
  d.until(1700);check(!tapper.active()&&tapper.sent()==1500&&tapper.state().contains("期限"),"a missing decision update expires instead of reusing coordinates forever");
  tapper.update(command("return",4000,1,1,true));d.until(1800);long before=tapper.sent();tapper.pause("CM");d.until(2000);
  check(tapper.sent()==before&&!tapper.active(),"an explicit CM pause cancels all future taps, including queued completions");
  Device renewed=new Device();Tapper r=renewed.tapper();r.update(command("play",100,1,10,true));renewed.until(50);r.update(command("play",500,1,10,true));r.update(command("play",40,1,10,true));renewed.until(200);
  check(r.active()&&renewed.starts.size()==21&&renewed.maxPending==1,"fresh leases continue one loop and a late expired lease cannot revoke the newer command");
 }
 static void staleCallbacksAndChanges(){
  Device d=new Device();d.extraLatency=49;Tapper tapper=d.tapper();tapper.update(command("old",5000,1,1,true));d.until(10);tapper.pause("stop");
  tapper.update(new Tapper.Command("game",0,"new",new int[]{0,0,540,1200},new float[]{100,100},1,1,5000,true));d.until(50);
  check(d.delivered.isEmpty()&&d.starts.equals(Arrays.asList(0L,50L)),"old completion cannot advance the new command and the new target waits for release");
  check(d.points.get(1)[0]==100&&d.maxPending==1,"coordinate changes discard queued old positions without overlapping input");
  Tapper.Result callback=d.lastResult;d.until(100);long sent=tapper.sent();callback.finished(Tapper.COMPLETED);check(tapper.sent()==sent,"a duplicated callback cannot create a second clock");
  tapper.pause("stop");d.until(1000);check(d.starts.size()==3,"stopping rejects all delayed callbacks from a previous run");
 }
 static void failuresAndOneShot(){
  Device d=new Device();d.outcome=Tapper.CANCELLED;Tapper tapper=d.tapper();tapper.update(command("play",5000,1,1,true));d.until(3000);
  check(d.starts.equals(Arrays.asList(0L,251L,752L,1753L))&&!tapper.active(),"cancelled input retries three times with backoff and then reports failure");
  check(tapper.state().contains("3回"),"retry exhaustion is visible to the decision side");
  Device bad=new Device();bad.throwsInput=true;Tapper rejected=bad.tapper();rejected.update(command("play",5000,1,1,true));bad.until(3000);check(!rejected.active()&&rejected.sent()==4,"driver exceptions follow the same bounded rejection path");
  rejected.update(command("play",5000,1,1,true));bad.until(3100);check(rejected.sent()==4,"repeating the same failed decision cannot reset its retry budget");
  rejected.update(command("play",7000,1,1,true));bad.until(3101);check(rejected.sent()==5,"a newly confirmed decision can retry after the failed lease");
  Device target=new Device();target.outcome=Tapper.INVALID_TARGET;Tapper guarded=target.tapper();guarded.update(command("play",5000,1,1,true));target.until(1000);check(target.starts.size()==1&&!guarded.active(),"a changed input target is never blindly retried");
  Device once=new Device();Tapper test=once.tapper();test.update(command("test",1500,60,1,false));once.until(500);check(test.completed()==1&&once.starts.size()==1&&!test.active(),"the 60ms diagnostic tap is a separate one-shot command");
  Device busy=new Device();busy.ready=false;Tapper waiting=busy.tapper();waiting.update(command("play",5000,1,1,true));busy.until(50);check(waiting.sent()==0,"a busy backend creates no overlapping gesture or rejection storm");busy.ready=true;busy.until(60);check(waiting.sent()>0&&busy.maxPending==1,"the same command resumes once its backend is ready");
  Device stopped=new Device();final Tapper[] ref=new Tapper[1];ref[0]=new Tapper(stopped,stopped,stopped,new Tapper.Listener(){public void state(String state){}public void result(Tapper.Command command,int result){ref[0].pause("decision revoked inside result callback");}});ref[0].update(command("play",5000,1,1,true));stopped.until(100);check(stopped.starts.size()==1&&!ref[0].active(),"decision-side revocation inside a result callback cannot schedule another tap or dereference a removed command");
 }
 static void isolationAndImmutability(){
  Device d=new Device();Tapper a=d.tapper(),b=d.tapper();a.update(new Tapper.Command("a",1,"a-play",new int[]{0,0,540,1200},new float[]{100,100},1,1,1000,true));b.update(new Tapper.Command("b",2,"b-play",new int[]{0,0,540,1200},new float[]{200,200},1,1,1000,true));d.until(100);a.pause("CM in a");long first=a.sent(),second=b.sent();d.until(200);
  check(a.sent()==first&&b.sent()>second&&d.maxPending==1,"each display has its own command, clock and cancellation state");
  int[] bounds={0,0,540,1200};float[] points={50,60,100,200};Tapper.Command command=new Tapper.Command("game",0,"play",bounds,points,1,1,1000,true);bounds[2]=1;points[0]=-1;command.point(0)[0]=-2;command.bounds()[2]=1;
  check(command.point(0)[0]==50&&command.bounds()[2]==540&&command.point(1)[0]==100,"callers cannot mutate active coordinates or window bounds");
  boolean rejected=false;try{new Tapper.Command("game",0,"play",new int[]{0,0,540,1200},new float[]{Float.NaN,10},1,1,1000,true);}catch(IllegalArgumentException expected){rejected=true;}check(rejected,"non-finite positions are rejected before reaching the driver");
 }
 public static void main(String[] args){fastestAndBackpressure();logicAndExpiry();staleCallbacksAndChanges();failuresAndOneShot();isolationAndImmutability();System.out.println("TapperTest: "+checks+" checks passed; shipped executor with asynchronous fake device; not an Android throughput measurement");}
}
