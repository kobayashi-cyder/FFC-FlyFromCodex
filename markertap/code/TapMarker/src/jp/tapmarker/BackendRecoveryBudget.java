package jp.tapmarker;
/** Limit display recreation after repeated backend death; a user start resets it. */
final class BackendRecoveryBudget {
 static final int LIMIT=3;static final long WINDOW_MS=600000;
 private final java.util.ArrayDeque<Long> losses=new java.util.ArrayDeque<>();
 boolean request(long now){while(!losses.isEmpty()&&now-losses.peekFirst()>=WINDOW_MS)losses.removeFirst();if(losses.size()>=LIMIT)return false;losses.addLast(now);return true;}
 void reset(){losses.clear();}
}
