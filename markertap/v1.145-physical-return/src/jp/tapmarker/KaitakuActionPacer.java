package jp.tapmarker;

import java.util.WeakHashMap;

/**
 * Back off duplicate modal action CANDIDATES without delaying a newly observed
 * opt-out button. This is not proof that Android dispatched or the game accepted
 * a tap; the existing input/observation guards still own that decision.
 *
 * State belongs to a PhaseEngine, so separate game runs cannot suppress each
 * other's actions. A changed target, missing modal, or reset clock starts fresh.
 */
final class KaitakuActionPacer {
  private static final long[] RETRY_MS={350L,750L,1500L,2500L};
  private static final WeakHashMap<Object,Entry> entries=new WeakHashMap<>();
  private static final class Entry {
    final String target;
    long lastCandidate;
    int attempts;
    Entry(String target,long now){this.target=target;lastCandidate=now;attempts=1;}
  }
  private static String target(String kind,float[] b) {
    // ~1% viewport tolerance prevents a one-pixel OCR jitter from resetting backoff.
    return kind+":"+Math.round(b[0]*100f)+":"+Math.round(b[1]*100f)+
      ":"+Math.round(b[2]*100f)+":"+Math.round(b[3]*100f);
  }
  static synchronized boolean allow(Object engine,String kind,float[] b,long nowMs){
    if(engine==null||kind==null||b==null||!KaitakuVideoModalPolicy.inViewport(b))return false;
    String id=target(kind,b);
    Entry old=entries.get(engine);
    if(old==null||!old.target.equals(id)||nowMs<old.lastCandidate) {
      entries.put(engine,new Entry(id,nowMs));
      return true; // first safe opt-out is never delayed
    }
    // A persistent unchanged modal cannot justify endless tap candidates.
    // Existing phase watchdog/recovery owns escalation after this small budget.
    if(old.attempts>=RETRY_MS.length+1)return false;
    long delay=RETRY_MS[Math.min(old.attempts-1,RETRY_MS.length-1)];
    if(nowMs-old.lastCandidate<delay)return false;
    old.lastCandidate=nowMs;
    old.attempts++;
    return true; // finite budget; changed screen resets immediately
  }
  static synchronized void reset(Object engine) {
    if(engine!=null)entries.remove(engine);
  }
  private KaitakuActionPacer(){}
}
