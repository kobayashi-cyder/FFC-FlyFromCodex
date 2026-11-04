package jp.tapmarker;
import java.util.WeakHashMap;

/** At-most-one candidate for the same reward row until observed receipt.
 * Candidate generation is not proof of a dispatched gesture or balance change.
 */
final class KaitakuRewardClaimGate {
  private static final WeakHashMap<Object,State> states=new WeakHashMap<>();
  private static final class State {String target;boolean receiptSeen;}
  static synchronized boolean allow(Object engine,float[] rect){
    if(engine==null||!KaitakuRewardClaimPolicy.safe(rect))return false;
    String id=KaitakuRewardClaimPolicy.target(rect);
    State s=states.get(engine);if(s==null){s=new State();states.put(engine,s);}
    if(id.equals(s.target)&&!s.receiptSeen)return false;
    s.target=id;s.receiptSeen=false;return true;
  }
  static synchronized void receipt(Object engine){
    State s=states.get(engine);if(s!=null&&s.target!=null)s.receiptSeen=true;
  }
  static synchronized void reset(Object engine){if(engine!=null)states.remove(engine);}
  private KaitakuRewardClaimGate(){}
}
