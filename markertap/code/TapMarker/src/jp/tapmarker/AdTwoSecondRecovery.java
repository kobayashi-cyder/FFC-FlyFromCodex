package jp.tapmarker;

/**
 * Independent, fail-closed recovery episode for each game/display.
 * A single CM label, a stale screenshot, or an ad-state name is not enough:
 * two distinct, fresh positive observations must span at least 2 seconds.
 * HOME confirmation must precede force-stop; force-stop confirmation must
 * precede the registered launch Intent.
 */
final class AdTwoSecondRecovery {
  enum Step { OBSERVE, HOME, KILL, INTENT, VERIFY, HOLD }
  static final long AD_MS=2000L, MAX_FRAME_AGE_MS=1500L, MAX_GAP_MS=3000L;
  static final long HOME_DEADLINE_MS=2000L, VERIFY_DEADLINE_MS=15000L;
  static final long COOLDOWN_MS=60000L, WINDOW_MS=900000L;
  private Step step=Step.OBSERVE;
  private long first=-1,last=-1,entered=-1,cooldownUntil=-1,windowStart=-1;
  private int freshFrames,attempts;
  private String target="";
  synchronized Step step(){return step;}
  synchronized int attempts(){return attempts;}
  synchronized void reset(){step=Step.OBSERVE;first=last=entered=-1;freshFrames=0;target="";cooldownUntil=-1;windowStart=-1;attempts=0;}
  private void clearObservation(){first=last=-1;freshFrames=0;}
  synchronized void changedForeground(){if(step==Step.OBSERVE)clearObservation();}
  synchronized boolean observe(String pkg,long capturedAt,long now,boolean positiveAd,boolean gameHome) {
    if(pkg==null||pkg.isEmpty()||now<0)return false;
    if(!pkg.equals(target)){reset();target=pkg;}
    if(windowStart<0||now<windowStart||now-windowStart>=WINDOW_MS){windowStart=now;attempts=0;}
    if(step==Step.HOLD){if(now>=cooldownUntil&&attempts<2){step=Step.OBSERVE;clearObservation();}else return false;}
    if(step!=Step.OBSERVE)return false;
    if(!positiveAd||gameHome){clearObservation();return false;}
    if(capturedAt<=0||capturedAt>now||now-capturedAt>=MAX_FRAME_AGE_MS)return false;
    if(last>=0&&capturedAt<=last)return false;
    if(first<0||last>=0&&capturedAt-last>MAX_GAP_MS){first=capturedAt;freshFrames=0;}
    last=capturedAt;freshFrames++;
    if(freshFrames<2||capturedAt-first<AD_MS)return false;
    if(attempts>=2){step=Step.HOLD;cooldownUntil=windowStart+WINDOW_MS;return false;}
    attempts++;step=Step.HOME;entered=now;clearObservation();return true;
  }
  synchronized boolean confirmedHome(long now,boolean visible){
    if(step!=Step.HOME)return false;
    if(!visible||now<entered||now-entered>HOME_DEADLINE_MS){hold(now);return false;}
    step=Step.KILL;entered=now;return true;
  }
  synchronized boolean confirmedKill(long now,boolean killed) {
    if(step!=Step.KILL)return false;
    if(!killed||now<entered){hold(now);return false;}
    step=Step.INTENT;entered=now;return true;
  }
  synchronized boolean confirmedIntent(long now,boolean dispatched){
    if(step!=Step.INTENT)return false;
    if(!dispatched||now<entered){hold(now);return false;}
    step=Step.VERIFY;entered=now;return true;
  }
  synchronized boolean verifiedGame(long now,boolean home){
    if(step!=Step.VERIFY)return false;
    if(now<entered||now-entered>VERIFY_DEADLINE_MS){hold(now);return false;}
    if(!home)return false;hold(now);return true;
  }
  /** The authorized shell reports all three steps on the owned display. */
  synchronized boolean confirmedRemote(long now,int evidence){
    if(step!=Step.HOME)return false;
    if(evidence!=7||now<entered){hold(now);return false;}
    step=Step.VERIFY;entered=now;return true;
  }
  synchronized boolean expired(long now){
    if(step==Step.HOME&&now-entered>HOME_DEADLINE_MS||step==Step.VERIFY&&now-entered>VERIFY_DEADLINE_MS){hold(now);return true;}
    return false;
  }
  synchronized void failed(long now){hold(now);}
  private void hold(long now){step=Step.HOLD;entered=now;cooldownUntil=Math.max(now,0)+COOLDOWN_MS;clearObservation();}
}
