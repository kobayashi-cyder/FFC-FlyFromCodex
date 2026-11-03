package jp.tapmarker;
/**
 * A preview touch may temporarily suspend one lane. A stale "manual" display
 * must not remain indefinitely after its ten-second input hold expires.
 * No automatic tapping or ADB privilege changes are authorized.
 */
final class ManualPreviewTimeoutPolicy {
 static final long HOLD_CAP_MS = 10_000L;
 static final long RECHECK_MS = 12_000L;
 static boolean expired(long touchedAt,long now,boolean currentTouch,long currentGeneration,long expectedGeneration){
  return touchedAt>0&&now>=touchedAt&&now-touchedAt>=RECHECK_MS
      &&currentGeneration==expectedGeneration&&!currentTouch;
 }
 static boolean recoverable(boolean active,boolean virtual,boolean stopped,boolean closed){
  return active&&virtual&&!stopped&&!closed;
 }
 private ManualPreviewTimeoutPolicy(){}
}
