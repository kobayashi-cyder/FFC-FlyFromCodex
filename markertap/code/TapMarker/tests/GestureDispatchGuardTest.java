package jp.tapmarker;
import java.util.*;

/** Runs the shipped callback guard with lost, late, duplicated and cancelled events. */
public final class GestureDispatchGuardTest {
 static int checks;static void check(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
 static final class Event implements Comparable<Event>{final long at,order;final Runnable task;Event(long a,long o,Runnable r){at=a;order=o;task=r;}public int compareTo(Event b){int c=Long.compare(at,b.at);return c==0?Long.compare(order,b.order):c;}}
 static final class Host implements GestureDispatchGuard.Scheduler {
  long now,order;final PriorityQueue<Event> queue=new PriorityQueue<>();final ArrayList<Integer> results=new ArrayList<>();GestureDispatchGuard.Completion callback;
  final GestureDispatchGuard guard=new GestureDispatchGuard(this);
  public void later(Runnable r,long delay){queue.add(new Event(now+delay,order++,r));}
  public void remove(Runnable r){queue.removeIf(e->e.task==r);}
  void until(long end){while(!queue.isEmpty()&&queue.peek().at<=end){Event e=queue.remove();now=e.at;e.task.run();}now=end;}
  boolean start(long duration,long release){return guard.dispatch(duration,release,c->{callback=c;return true;},results::add);}
 }
 static void completedAndRelease(){
  Host h=new Host();check(h.start(17,17),"first gesture accepted");check(h.guard.busy()&&h.queue.size()==1,"one watchdog installed");
  h.until(17);h.callback.finished(true);check(h.results.isEmpty()&&h.guard.busy(),"UP receipt keeps the release interval separate from pressing");
  check(h.queue.size()==1,"successful completion removes its watchdog instead of accumulating delayed tasks");
  check(!h.start(1,0),"a second accessibility gesture cannot cancel the first during its release interval");
  h.until(33);check(h.results.isEmpty(),"minimum release interval is respected");h.until(34);check(h.results.equals(Arrays.asList(0))&&!h.guard.busy(),"caller resumes after release");
  h.callback.finished(false);h.callback.finished(true);h.until(5000);check(h.results.size()==1&&h.queue.isEmpty(),"duplicate completion and its removed timeout have no effect");
  Host fastest=new Host();fastest.start(1,0);fastest.until(1);fastest.callback.finished(true);check(fastest.results.equals(Arrays.asList(0))&&!fastest.guard.busy(),"explicit shortest mode adds no release delay");
 }
 static void lostAndLateCallbacks(){
  Host h=new Host();h.start(1,17);GestureDispatchGuard.Completion old=h.callback;h.until(1499);check(h.results.isEmpty(),"a short gesture has a bounded grace period");
  h.until(1500);check(h.results.equals(Arrays.asList(2))&&!h.guard.busy(),"missing callback releases caller as timeout, not completion");
  check(h.start(60,17),"next confirmed input can start after missing callback");GestureDispatchGuard.Completion current=h.callback;
  old.finished(true);old.finished(false);check(h.guard.busy()&&h.results.size()==1,"late callback cannot clear the new operation or report a false success");
  current.finished(false);check(h.results.equals(Arrays.asList(2,1))&&!h.guard.busy(),"actual cancellation is delivered once");
  Host longGesture=new Host();longGesture.start(60000,0);longGesture.until(61000-1);check(longGesture.results.isEmpty(),"watchdog accounts for the full registered swipe duration");longGesture.until(61000);check(longGesture.results.equals(Arrays.asList(2)),"long gestures also have a finite deadline");
 }
 static void refusalsAndReentrancy(){
  Host h=new Host();check(!h.guard.dispatch(1,0,c->false,h.results::add)&&!h.guard.busy()&&h.queue.isEmpty()&&h.results.isEmpty(),"immediate refusal leaves no callback or pending timeout");
  boolean thrown=false;try{h.guard.dispatch(1,0,c->{throw new IllegalStateException("platform refused");},h.results::add);}catch(IllegalStateException e){thrown=true;}
  check(thrown&&!h.guard.busy()&&h.queue.isEmpty(),"platform exception cannot leave the global input gate locked");
  check(h.guard.dispatch(1,0,c->{c.finished(true);return true;},h.results::add)&&h.results.equals(Arrays.asList(0))&&h.queue.isEmpty(),"synchronous completion also settles exactly once");
  h.guard.dispatch(1,0,c->{h.callback=c;return true;},outcome->{h.results.add(outcome);check(h.start(1,0),"completion may schedule its successor without overlapping the settled ticket");});
  GestureDispatchGuard.Completion first=h.callback;first.finished(true);check(h.guard.busy(),"new operation owns the active gate");first.finished(false);check(h.guard.busy(),"old cancellation cannot release a successor created inside completion");h.callback.finished(true);
 }
 public static void main(String[] args){completedAndRelease();lostAndLateCallbacks();refusalsAndReentrancy();System.out.println("GestureDispatchGuardTest: "+checks+" checks passed; callback loss, timeout, release interval, duplicate/late callback and shared accessibility gate; Android device untested");}
}
