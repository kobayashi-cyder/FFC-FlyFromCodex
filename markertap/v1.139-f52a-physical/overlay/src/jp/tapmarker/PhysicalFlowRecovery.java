package jp.tapmarker;

/**
 * F-52A real-display main-screen recognition recovery.
 * Never equates a foreground package with a recognized game screen.
 * Two distinct fresh observations are required before reclassification.
 */
final class PhysicalFlowRecovery {
 enum Decision { KEEP_OBSERVING, RECLASSIFY, STOP_WITH_DIAGNOSTIC }
 static final String GAME="com.kurashiru.fue.usagi";
 static final String MAIN="usagi-default-main", VERIFY="usagi-default-verify";
 static final long FRESH_MS=1200, PERSIST_MS=1600, EPISODE_MS=90000;
 private long firstAt=-1, priorFrame=-1, episodeAt=-1;
 private int distinct, attempts;
 void reset(){firstAt=priorFrame=episodeAt=-1;distinct=attempts=0;}
 private void resetCandidate(){firstAt=priorFrame=-1;distinct=0;}
 int attempts(){return attempts;}
 Decision observe(String state,boolean foreground,boolean recognized,
    boolean actionPending,boolean returning,long frameAt,long now) {
  if(recognized){reset();return Decision.KEEP_OBSERVING;}
  if(!foreground||state==null||now<0){
   resetCandidate();return Decision.KEEP_OBSERVING;
  }
  if(!MAIN.equals(state)&&!VERIFY.equals(state)||actionPending||returning){
   resetCandidate();return Decision.KEEP_OBSERVING;
  }
  // Do not advance, classify, or tap on stale/absent screenshots.
  if(frameAt<=0||now<frameAt||now-frameAt>=FRESH_MS){
   resetCandidate();return Decision.KEEP_OBSERVING;
  }
  if(frameAt!=priorFrame){
   if(firstAt<0)firstAt=now;
   priorFrame=frameAt;distinct++;
  }
  if(distinct<2||now-firstAt<PERSIST_MS)return Decision.KEEP_OBSERVING;
  resetCandidate();
  if(episodeAt<0||now<episodeAt||now-episodeAt>EPISODE_MS){
   episodeAt=now;attempts=0;
  }
  if(++attempts>3)return Decision.STOP_WITH_DIAGNOSTIC;
  return Decision.RECLASSIFY;
 }
}
