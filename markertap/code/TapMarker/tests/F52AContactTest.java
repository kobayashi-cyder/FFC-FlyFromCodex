package jp.tapmarker;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Contact timing plus the shipped executor against a frame-polled synthetic receiver. */
public final class F52AContactTest {
 static int checks;static void check(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
 static final class Event implements Comparable<Event>{final long at,order;final Runnable task;Event(long a,long o,Runnable r){at=a;order=o;task=r;}public int compareTo(Event b){int c=Long.compare(at,b.at);return c==0?Long.compare(order,b.order):c;}}
 static final class Receiver implements Tapper.Clock,Tapper.Scheduler,Tapper.Driver,DiscreteTouch.Clock,DiscreteTouch.Sink {
  long now,order;double nextFrame;final double frame;final long release;boolean pressed,lastSample;int downs,ups,sampledDowns,sampledUps;final ArrayList<Long> starts=new ArrayList<>();final PriorityQueue<Event> queue=new PriorityQueue<>();
  Receiver(float hz,double phase,long release){frame=1000.0/hz;nextFrame=phase%frame;this.release=release;}
  public long now(){return now;}public void later(Runnable r,long delay){queue.add(new Event(now+delay,order++,r));}
  public boolean ready(Tapper.Command c){return true;}
  public void send(Tapper.Command c,float[] p,BooleanSupplier valid,Tapper.Result result){starts.add(now);later(()->{try{boolean ok=new DiscreteTouch(this,this).tap(p[0],p[1],c.pressMillis,valid);later(()->result.finished(ok?Tapper.COMPLETED:Tapper.CANCELLED),release);}catch(Exception error){result.finished(Tapper.REJECTED);}},0);}
  public boolean send(long down,int action,float x,float y){check(x==270&&y==900,"receiver sees one fixed discrete contact");if(action==0){check(!pressed,"no overlapping DOWN");pressed=true;downs++;}else{check(pressed,"every release has a DOWN");pressed=false;ups++;}return true;}
  public void sleep(long millis){advance(now+millis);}
  void advance(long end){while(nextFrame<=end){if(pressed!=lastSample){if(pressed)sampledDowns++;else sampledUps++;lastSample=pressed;}nextFrame+=frame;}now=end;}
  void until(long end){int loops=0;while(!queue.isEmpty()&&queue.peek().at<=end){if(++loops>50000)throw new AssertionError("loop stalled");Event e=queue.remove();advance(Math.max(now,e.at));e.task.run();}advance(Math.max(now,end));}
 }
 public static void main(String[] args){
  for(String model:new String[]{"F-52A","F52A","f_52a"," F-52A "}){
   check(TapContactPolicy.discrete(model,0),"F-52A auto mode selects discrete contact");check(TapContactPolicy.pressMillis(model,0,60,1)==17&&TapContactPolicy.releaseMillis(model,0,60)==17,"60Hz keeps both pressed and released intervals");
   check(TapContactPolicy.pressMillis(model,0,60,60)==60,"longer explicit button presses are preserved");check(TapContactPolicy.minimumPeriod(model,0,60,1,500)==500,"slow requested cadence remains intact");
   check(TapContactPolicy.pressMillis(model,2,60,1)==1&&TapContactPolicy.releaseMillis(model,2,60)==0,"user can select the previous uncapped shortest input on F-52A");
  }
  check(!TapContactPolicy.discrete("Pixel",0)&&TapContactPolicy.discrete("Pixel",1),"other devices preserve shortest auto mode and may opt in to discrete input");
  for(float invalid:new float[]{Float.NaN,Float.POSITIVE_INFINITY,0,10,500})check(TapContactPolicy.frameMillis(invalid)==17,"unreadable refresh rate has a bounded 60Hz fallback");
  check(TapContactPolicy.frameMillis(90)==12&&TapContactPolicy.frameMillis(120)==9,"release interval follows refresh rate rather than a fixed model floor");
  for(float hz:new float[]{60,90,120})for(int phase=0;phase<17;phase++){
   long press=TapContactPolicy.pressMillis("F-52A",0,hz,1),release=TapContactPolicy.releaseMillis("F-52A",0,hz);Receiver r=new Receiver(hz,phase,release);Tapper tapper=new Tapper(r,r,r,null);
   tapper.update(new Tapper.Command("game",0,"main",new int[]{0,0,540,1200},new float[]{270,900},press,1,5000,true));r.until(1000);tapper.pause("CM");r.until(1100);
   check(r.downs==r.ups,"all issued contacts release before pause completes");check(r.sampledDowns==r.downs&&r.sampledUps==r.ups,"frame-polled receiver observes both edges at every tested frame offset");
   for(int i=1;i<r.starts.size();i++)check(r.starts.get(i)-r.starts.get(i-1)>=press+release,"fastest in discrete mode waits for the release interval");
  }
  Receiver shortest=new Receiver(60,5,0);Tapper fastest=new Tapper(shortest,shortest,shortest,null);fastest.update(new Tapper.Command("game",0,"main",new int[]{0,0,540,1200},new float[]{270,900},1,1,5000,true));shortest.until(100);fastest.pause("stop");shortest.until(200);
  check(shortest.starts.get(1)==1&&shortest.downs>90,"explicit shortest mode retains the previous one-millisecond requested cadence");check(shortest.sampledDowns<shortest.downs,"synthetic frame polling demonstrates why completion counts alone cannot prove game acceptance");
  System.out.println("F52AContactTest: "+checks+" checks passed; contact timing, selectable shortest mode, shipped Tapper/DiscreteTouch and frame-polled model; not a real F-52A/game success rate");
 }
}
