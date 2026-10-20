package jp.tapmarker;

/** One accessibility gesture globally, one terminal callback, and a bounded wait.
 * Called only on its scheduler's serial event loop. A timed-out operation cannot
 * later release another operation or turn a cancellation into a completion.
 */
public final class GestureDispatchGuard {
 public static final int COMPLETED=0,CANCELLED=1,TIMED_OUT=2;
 public interface Scheduler {void later(Runnable task,long delay);void remove(Runnable task);}
 public interface Completion {void finished(boolean completed);}
 public interface Dispatch {boolean send(Completion completion);}
 public interface Result {void finished(int outcome);}
 private final Scheduler scheduler;private Ticket active;
 public GestureDispatchGuard(Scheduler scheduler){this.scheduler=scheduler;}
 public boolean busy(){return active!=null;}
 public static long timeoutMillis(long duration){return Math.max(1500,Math.max(1,duration)+1000);}
 public boolean dispatch(long duration,long release,Dispatch platform,Result result){
  if(active!=null)return false;
  Ticket ticket=new Ticket(Math.max(0,release),result);active=ticket;
  boolean accepted;
  try{accepted=platform.send(completed->ticket.settle(completed?COMPLETED:CANCELLED));}
  catch(RuntimeException error){ticket.abandon();throw error;}
  if(!accepted){ticket.abandon();return false;}
  if(!ticket.settled)scheduler.later(ticket.timeout,timeoutMillis(duration));
  return true;
 }
 private final class Ticket {
  final long release;final Result result;boolean settled,delivered;
  final Runnable timeout=()->settle(TIMED_OUT);
  Ticket(long release,Result result){this.release=release;this.result=result;}
  void abandon(){settled=true;delivered=true;scheduler.remove(timeout);if(active==this)active=null;}
  void settle(int outcome){
   if(settled||active!=this)return;settled=true;scheduler.remove(timeout);
   Runnable deliver=()->{if(delivered||active!=this)return;delivered=true;active=null;result.finished(outcome);};
   if(release>0&&outcome==COMPLETED)scheduler.later(deliver,release);else deliver.run();
  }
 }
}
