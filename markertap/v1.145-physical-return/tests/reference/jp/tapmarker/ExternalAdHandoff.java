package jp.tapmarker;

/**
 * Accessibility-observed ad -> external app handoff.
 * Never assumes a click completed, never generates an outbound ad tap.
 * Waits for independent, stable foreground observations before permitting
 * the existing safe registered-app launch Intent.
 */
final class ExternalAdHandoff {
 enum Decision { OWN_APP, WAIT, READY }
 static final long MIN_EXTERNAL_DWELL_MS=1200, MAX_OBSERVATION_GAP_MS=2500;
 private String registered="",destination="";
 private long firstAt=-1,lastAt=-1;
 private int observations=0;
 void reset(){registered="";destination="";firstAt=lastAt=-1;observations=0;}
 Decision observe(String target,String foreground,boolean adState,long now){
  if(target==null||target.isEmpty()||now<0){reset();return Decision.WAIT;}
  if(target.equals(foreground)){reset();return Decision.OWN_APP;}
  // Root may disappear for several frames during a browser/Play Store handoff.
  // Never launch an app on "unknown" or transient system-owned windows.
  if(foreground==null||foreground.isEmpty()||
     foreground.equals("com.android.systemui")||
     foreground.equals("jp.tapmarker.lite")){reset();return Decision.WAIT;}
  // Preserve established non-ad external recovery behavior.
  if(!adState){reset();return Decision.READY;}
  if(!target.equals(registered)||!foreground.equals(destination)||
     now<lastAt||lastAt<0||now-lastAt>MAX_OBSERVATION_GAP_MS){
   registered=target;destination=foreground;firstAt=lastAt=now;observations=1;
   return Decision.WAIT;
  }
  if(now>lastAt){lastAt=now;observations++;}
  return observations>=2&&now-firstAt>=MIN_EXTERNAL_DWELL_MS
   ?Decision.READY:Decision.WAIT;
 }
}
