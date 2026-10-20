package jp.tapmarker;
/** Three intent attempts form a batch; continued observation resumes during cooldown. */
final class RecoveryBudget {
 static final long COOLDOWN_MS=8000;
 private int attempts;private long next;final int limit;RecoveryBudget(){this(3);}RecoveryBudget(int limit){if(limit<1)throw new IllegalArgumentException("再試行回数");this.limit=limit;}
 void reset(){attempts=0;next=0;}
 boolean acquire(long now){if(now<next)return false;if(attempts>=limit){attempts=0;next=now+COOLDOWN_MS;return false;}attempts++;return true;}
 long remaining(long now){return Math.max(0,next-now);}
}
