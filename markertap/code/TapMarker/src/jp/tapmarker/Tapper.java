package jp.tapmarker;

import java.util.Arrays;
import java.util.function.BooleanSupplier;

/** An input executor. It knows commands and platform results, never game rules or images.
 * All methods and driver callbacks run on the supplied scheduler's serial event loop. */
public final class Tapper {
 public static final int COMPLETED=0,CANCELLED=1,REJECTED=2,INVALID_TARGET=3,BUSY=4;
 public interface Clock {long now();}
 public interface Scheduler {void later(Runnable task,long delay);}
 public interface Result {void finished(int result);}
 public interface Driver {
  boolean ready(Command command);
  void send(Command command,float[] point,BooleanSupplier current,Result result);
 }
 public interface Listener {
  void state(String state);
  void result(Command command,int result);
 }
 public static final class Command {
  public final String target,context;
  public final int displayId,priority;
  public final long pressMillis,periodMillis,expiresAt;
  public final boolean repeat,randomize;
  private final int[] bounds;
  private final float[] points;
  public Command(String target,int displayId,String context,int[] bounds,float[] points,long pressMillis,long periodMillis,long expiresAt,boolean repeat){
   this(target,displayId,context,bounds,points,pressMillis,periodMillis,expiresAt,repeat,0);
  }
  public Command(String target,int displayId,String context,int[] bounds,float[] points,long pressMillis,long periodMillis,long expiresAt,boolean repeat,int priority){
   this(target,displayId,context,bounds,points,pressMillis,periodMillis,expiresAt,repeat,priority,false);
  }
  public Command(String target,int displayId,String context,int[] bounds,float[] points,long pressMillis,long periodMillis,long expiresAt,boolean repeat,int priority,boolean randomize){
   if(target==null||target.isEmpty()||context==null||displayId<0||bounds==null||bounds.length!=4||bounds[0]>=bounds[2]||bounds[1]>=bounds[3]||points==null||points.length==0||points.length%2!=0||pressMillis<1||pressMillis>1000||periodMillis<1)throw new IllegalArgumentException("invalid tap command");
   for(int i=0;i<points.length;i+=2)if(!Float.isFinite(points[i])||!Float.isFinite(points[i+1])||points[i]<bounds[0]||points[i]>=bounds[2]||points[i+1]<bounds[1]||points[i+1]>=bounds[3])throw new IllegalArgumentException("tap outside target");
   this.target=target;this.displayId=displayId;this.context=context;this.bounds=bounds.clone();this.points=DiscreteTapPosition.points(points,bounds);this.pressMillis=pressMillis;this.periodMillis=periodMillis;this.expiresAt=expiresAt;this.repeat=repeat;this.priority=priority;this.randomize=randomize;
  }
  public int[] bounds(){return bounds.clone();}
  public int count(){return points.length/2;}
  public float[] point(int index){int i=(index%count())*2;return new float[]{points[i],points[i+1]};}
  boolean sameRoute(Command other){return other!=null&&target.equals(other.target)&&displayId==other.displayId&&context.equals(other.context)&&Arrays.equals(bounds,other.bounds)&&Arrays.equals(points,other.points)&&pressMillis==other.pressMillis&&periodMillis==other.periodMillis&&repeat==other.repeat&&priority==other.priority&&randomize==other.randomize;}
 }
 private final Clock clock;private final Scheduler scheduler;private final Driver driver;private final Listener listener;private final java.util.Random random;
 private final TapInputPolicy.Retry retry=new TapInputPolicy.Retry();
 private Command command,blocked;private long revision,wakeId,flightId;private boolean queued,inFlight;private int index;private String state="停止中";
 private long sent,completed,cancelled,rejected,rateSince=-1,rateCompletions;private double completionRate;
 public Tapper(Clock clock,Scheduler scheduler,Driver driver,Listener listener){this(clock,scheduler,driver,listener,new java.util.Random());}
 Tapper(Clock clock,Scheduler scheduler,Driver driver,Listener listener,java.util.Random random){this.clock=clock;this.scheduler=scheduler;this.driver=driver;this.listener=listener;this.random=random;}
 public boolean active(){return command!=null;}
 public String state(){return state;}
 public long completed(){return completed;}
 public long sent(){return sent;}
 public String report(){return "タッパー："+state+" / 要求 "+sent+" / 完了 "+completed+" / 中断 "+cancelled+" / 拒否 "+rejected+"\n直近の送信完了速度："+(completionRate>0?String.format(java.util.Locale.ROOT,"%.1f回/秒",completionRate):"測定中（連続実行1秒から表示）")+(command==null?"":"\n指示：押下 "+command.pressMillis+"ms / 間隔 "+command.periodMillis+"ms以上 / "+command.target+" #"+command.displayId);}
 public void update(Command next){
  if(blocked!=null&&blocked.sameRoute(next)&&next.expiresAt<=blocked.expiresAt)return;
  blocked=null;
  if(command!=null&&command.sameRoute(next)&&next.expiresAt<command.expiresAt)return;
  if(next.expiresAt<=clock.now()){pause("指示期限切れ・判断側の更新待ち");return;}
  if(command!=null&&command.sameRoute(next)){
   if(next.expiresAt<command.expiresAt)return;
   command=next;if(!inFlight&&!queued)schedule(0);return;
  }
  command=next;revision++;index=0;retry.completed();rateSince=-1;rateCompletions=0;completionRate=0;cancelWake();show(next.repeat?"連打中":"1回タップ中");if(!inFlight)schedule(0);
 }
 public void pause(String reason){blocked=null;if(command==null&&state.equals(reason))return;command=null;revision++;cancelWake();show(reason);}
 private void block(String reason){Command denied=command;pause(reason);blocked=denied;}
 private void show(String text){if(!state.equals(text)){state=text;if(listener!=null)listener.state(text);}}
 private void cancelWake(){queued=false;wakeId++;}
 private void schedule(long delay){if(queued||command==null)return;queued=true;long id=++wakeId;scheduler.later(()->{if(id!=wakeId)return;queued=false;tick();},Math.max(0,delay));}
 private boolean current(long ticket){return command!=null&&revision==ticket&&clock.now()<command.expiresAt;}
 private void tick(){
  if(command==null||inFlight)return;
  if(clock.now()>=command.expiresAt){pause("指示期限切れ・判断側の更新待ち");return;}
  Command issued=command;if(!driver.ready(issued)){show("入力先の空きを待機");schedule(4);return;}
  long ticket=revision,flight=++flightId,started=clock.now();if(rateSince<0)rateSince=started;inFlight=true;sent++;show(issued.repeat?"連打中":"1回タップ中");
  boolean[] delivered={false};
  Result result=value->{
   if(delivered[0]||flight!=flightId)return;delivered[0]=true;inFlight=false;
   if(value==COMPLETED)completed++;else if(value==CANCELLED)cancelled++;else if(value==REJECTED)rejected++;
   if(!current(ticket)){if(command!=null)schedule(0);return;}
   if(listener!=null)listener.result(issued,value);
   if(!current(ticket)){if(command!=null)schedule(0);return;}
   if(value==COMPLETED){rateCompletions++;long elapsed=clock.now()-rateSince;if(elapsed>=1000){completionRate=rateCompletions*1000.0/elapsed;rateSince=clock.now();rateCompletions=0;}retry.completed();index++;if(!issued.repeat){pause("1回タップ送信完了");return;}schedule(Math.max(0,command.periodMillis-(clock.now()-started)));return;}
   if(value==INVALID_TARGET){block("入力先が変化・判断側の更新待ち");return;}
   if(value==BUSY){show("入力先の空きを待機");schedule(4);return;}
   long delay=retry.failed();if(delay<0){block("入力失敗・3回の再試行後も受付不可");return;}
   show("入力を再確認して再試行 "+retry.failures()+"/3");schedule(delay);
  };
  try{driver.send(issued,issued.point(issued.randomize?random.nextInt(issued.count()):index),()->current(ticket),result);}catch(RuntimeException failure){result.finished(REJECTED);}
 }
}
