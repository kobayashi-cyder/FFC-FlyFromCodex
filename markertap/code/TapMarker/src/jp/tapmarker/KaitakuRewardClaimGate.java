package jp.tapmarker;
import java.util.WeakHashMap;

/** At-most-one candidate for the same reward row until observed receipt.
 * Candidate generation is not proof of a dispatched gesture or balance change.
 */
final class KaitakuRewardClaimGate {
  private static final WeakHashMap<Object,State> states=new WeakHashMap<>();
  private static final class State {String target;boolean receiptSeen;long candidateAtNs;}
  static synchronized boolean allow(Object engine,float[] rect){
    if(engine==null||!KaitakuRewardClaimPolicy.safe(rect))return false;
    String id=KaitakuRewardClaimPolicy.target(rect);
    State s=states.get(engine);if(s==null){s=new State();states.put(engine,s);}
    long now=System.nanoTime();
    // Strict runtime needs two distinct fresh frames. Suppressing the second
    // observation prevented any actual Get gesture from becoming eligible.
    // One bounded observation window, then hold until an independent receipt.
    if(id.equals(s.target)&&!s.receiptSeen)
      return now-s.candidateAtNs>=0&&now-s.candidateAtNs<2_000_000_000L;
    s.target=id;s.receiptSeen=false;s.candidateAtNs=now;return true;
  }
  static synchronized void receipt(Object engine){
    State s=states.get(engine);if(s!=null&&s.target!=null)s.receiptSeen=true;
  }
  static synchronized void reset(Object engine){if(engine!=null)states.remove(engine);}
  private KaitakuRewardClaimGate(){}
}
