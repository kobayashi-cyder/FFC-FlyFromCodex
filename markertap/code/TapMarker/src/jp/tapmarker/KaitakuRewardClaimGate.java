package jp.tapmarker;
import java.util.WeakHashMap;

/** A claim candidate is NOT proof of a sent gesture, a receipt, or a balance gain.
 * A missed gesture must not permanently disable Get. The same row may be
 * retried only in bounded, separated windows; a confirmed receipt suppresses
 * all duplicates until an independently visible non-Get mission row occurs.
 */
final class KaitakuRewardClaimGate {
  static final long CONFIRM_NS=2_000_000_000L,RETRY_NS=8_000_000_000L;
  static final int MAX_WINDOWS=3;
  private static final WeakHashMap<Object,State> states=new WeakHashMap<>();
  private static final class State {
    String target;float[] bounds;boolean receiptSeen;
    long candidateAtNs;int windows;
  }
  private static boolean sameRow(float[] a,float[] b){
    return a!=null&&b!=null&&a.length==4&&b.length==4&&
      Math.abs((a[1]+a[3])-(b[1]+b[3]))<.05f&&
      Math.abs((a[0]+a[2])-(b[0]+b[2]))<.24f;
  }
  static synchronized boolean allow(Object engine,float[] rect){
    return allowAt(engine,rect,System.nanoTime());
  }
  // Monotonic time injection makes 2-frame, retry and dedup behavior testable.
  static synchronized boolean allowAt(Object engine,float[] rect,long nowNs){
    if(engine==null||!KaitakuRewardClaimPolicy.safe(rect))return false;
    String id=KaitakuRewardClaimPolicy.target(rect);
    State s=states.get(engine);
    if(s==null){s=new State();states.put(engine,s);}
    if((id.equals(s.target)||sameRow(s.bounds,rect))&&s.target!=null){
      if(s.receiptSeen)return false;
      long elapsed=nowNs-s.candidateAtNs;
      if(elapsed>=0&&elapsed<CONFIRM_NS)return true;
      if(elapsed<RETRY_NS||s.windows>=MAX_WINDOWS)return false;
      s.candidateAtNs=nowNs;s.windows++;return true;
    }
    s.target=id;s.bounds=rect.clone();s.receiptSeen=false;
    s.candidateAtNs=nowNs;s.windows=1;return true;
  }
  /** Called only after a separately recognized reward/coin receipt screen. */
  static synchronized void receipt(Object engine){
    State s=states.get(engine);if(s!=null&&s.target!=null)s.receiptSeen=true;
  }
  /** A positively recognized Play-only mission row clears the old target. */
  static synchronized void absent(Object engine){if(engine!=null)states.remove(engine);}
  static synchronized void reset(Object engine){absent(engine);}
  private KaitakuRewardClaimGate(){}
}
