package jp.tapmarker;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Event-driven backpressure for virtual capture requests.
 *
 * The Android frame executor has two bounded analysis workers. A producer for
 * each display must not enqueue more work than can be used before freshness
 * expires. This circuit allows at most one outstanding request per display,
 * at most two across all displays, and expires abandoned leases after the
 * supervisor's 10-second deadline. Older callbacks carry their own ticket and
 * cannot release a newer request. Rejections are not action evidence.
 *
 * It does not change game display resolution or ADB transport semantics.
 */
final class CaptureCircuit {
 private static final class Lease {
  final int display;final long id,started;
  Lease(int d,long i,long t){display=d;id=i;started=t;}
 }
 private final Map<Integer,Lease> inFlight=new HashMap<>();
 private final int capacity;private final long expiryMs;
 private long serial,accepted,backpressured,expired,finished;
 CaptureCircuit(int capacity,long expiryMs){
  if(capacity<1||expiryMs<1)throw new IllegalArgumentException("capture limit");
  this.capacity=capacity;this.expiryMs=expiryMs;
 }
 synchronized long acquire(int display,long now){
  if(now<=0)throw new IllegalArgumentException("clock");
  // Reclaim orphaned tickets without allocating an unbounded retry queue.
  Iterator<Lease> it=inFlight.values().iterator();
  while(it.hasNext()){
   Lease old=it.next();if(now<old.started||now-old.started>=expiryMs){it.remove();expired++;}
  }
  if(inFlight.containsKey(display)||inFlight.size()>=capacity){backpressured++;return 0;}
  long id=++serial;inFlight.put(display,new Lease(display,id,now));accepted++;return id;
 }
 synchronized boolean finish(int display,long ticket){
  Lease old=inFlight.get(display);
  if(old==null||old.id!=ticket)return false;
  inFlight.remove(display);finished++;return true;
 }
 /** Every delivered result, including duplicate-frame early returns, ends its lease. */
 Runnable releasing(int display,long ticket,Runnable publication){
  if(publication==null)throw new IllegalArgumentException("publication");
  return ()->{finish(display,ticket);publication.run();};
 }
 synchronized void reset(){inFlight.clear();}
 synchronized int active(){return inFlight.size();}
 synchronized long backpressured(){return backpressured;}
 synchronized long expired(){return expired;}
 synchronized long accepted(){return accepted;}
 synchronized long finished(){return finished;}
}
