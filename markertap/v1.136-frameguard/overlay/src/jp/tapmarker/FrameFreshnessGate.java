package jp.tapmarker;
/** Independent per-display decode admission. Keeps the recognition deadline unchanged. */
final class FrameFreshnessGate {
 static final long REFRESH_AGE_MS=750;
 private FrameFreshnessGate(){}
 static boolean shouldDecode(long now,long frameAt,long nextDue,boolean analysisPending){
  if(frameAt<=0)return true;
  if(now>=nextDue)return true;
  // A frame nearing the 1500ms recognition deadline must be replaced early,
  // but never steal time from an analysis already using this display's frame.
  return !analysisPending&&now>=frameAt&&now-frameAt>=REFRESH_AGE_MS;
 }
}
