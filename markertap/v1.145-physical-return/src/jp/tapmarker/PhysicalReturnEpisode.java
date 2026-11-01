package jp.tapmarker;

/** Monotonic foreground-return episode. No image grants permission to tap here. */
final class PhysicalReturnEpisode {
 static final long DEADLINE_MS=90000, NORMAL_INTERVAL_MS=5000, SETTLE_MS=30000, FRESH_MS=1500;
 enum Decision { WAIT, RETURN, RESTART, STOP }
 long started=-1, nextAt; private long homeAt=-1,homeSince=-1,homeWindow=-1;private int homeFrames;
 void begin(long now){if(started<0)started=now;}
 long duration(long now){return started<0?0:Math.max(0,now-started);}
 boolean expired(long now){return started>=0&&duration(now)>=DEADLINE_MS;}
 Decision decide(boolean stable,long now,int returns,int restarts,long sharedNext,long lastSent){
  begin(now);
  if(expired(now))return Decision.STOP;
  long due=Math.max(nextAt,sharedNext);
  if(lastSent>0)due=Math.max(due,lastSent+NORMAL_INTERVAL_MS);
  if(now<due)return Decision.WAIT;
  // Let the last charged restart finish settling before declaring exhaustion.
  if(restarts>=2)return Decision.STOP;
  if(!stable)return Decision.WAIT;
  return returns<3?Decision.RETURN:Decision.RESTART;
 }
 void attempted(long now,boolean restart){nextAt=now+(restart?SETTLE_MS:NORMAL_INTERVAL_MS);}
 void unavailable(long now){nextAt=Math.max(nextAt,now+NORMAL_INTERVAL_MS);}
 boolean home(boolean visible,long at,long window,long now){
  if(!visible||at<=0||at>now||now-at>=FRESH_MS){homeAt=homeSince=-1;homeFrames=0;return false;}
  if(at<=homeAt)return false;
  if(homeSince<0||window!=homeWindow){homeSince=at;homeFrames=0;homeWindow=window;}
  homeAt=at;homeFrames++;
  if(homeFrames<2||at-homeSince<350)return false;
  started=-1;nextAt=0;return true;
 }
}
