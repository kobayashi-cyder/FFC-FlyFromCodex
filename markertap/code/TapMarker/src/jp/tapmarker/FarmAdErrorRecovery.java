package jp.tapmarker;

/** One dialog episode per app lane; bounding repeated OK dispatches is essential. */
final class FarmAdErrorRecovery {
 static final long MIN_CONFIRM_MS=140,RETRY_COOLDOWN_MS=2700;
 static final int MAX_ATTEMPTS=3;
 private long firstSeen=-1,lastFrame=-1,lastSent=-1;
 private int consecutive,missing,attempts;
 private boolean visible,recovered;
 void reset(){firstSeen=lastFrame=lastSent=-1;consecutive=missing=attempts=0;visible=recovered=false;}
 void observe(boolean match,long frameAt,long now){
  if(frameAt<=lastFrame||frameAt>now||now-frameAt>1500)return;
  lastFrame=frameAt;
  if(match){if(!visible){firstSeen=frameAt;consecutive=0;}visible=true;missing=0;consecutive++;return;}
  if(visible){if(++missing<2)return;recovered=true;}
  visible=false;firstSeen=-1;consecutive=0;missing=0;attempts=0;lastSent=-1;
 }
 boolean visible(){return visible;}
 boolean confirmed(){return visible&&consecutive>=2&&firstSeen>=0&&lastFrame-firstSeen>=MIN_CONFIRM_MS;}
 boolean mayTap(long now){return confirmed()&&attempts<MAX_ATTEMPTS&&(lastSent<0||now-lastSent>=RETRY_COOLDOWN_MS);}
 boolean exhausted(long now){return confirmed()&&attempts>=MAX_ATTEMPTS&&lastSent>=0&&now-lastSent>=RETRY_COOLDOWN_MS;}
 void dispatched(long now){if(!mayTap(now))throw new IllegalStateException("farm dialog not confirmed or retry budget exhausted");attempts++;lastSent=now;}
 int attempts(){return attempts;}
 boolean consumeRecovered(){boolean result=recovered;recovered=false;return result;}
}
