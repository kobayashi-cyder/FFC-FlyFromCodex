package jp.tapmarker;

/**
 * CM検知関数: positive visual evidence -> HOME -> registered Intent.
 * Only if the advertisement is still positively observed for >=2 seconds
 * AFTER that return: HOME -> target-only force-stop -> registered Intent.
 *
 * The policy never credits ad-watching/reward receipt. No stale, duplicate,
 * negative or pre-relaunch frame can authorize escalation. Fail closed.
 * Instantiate one detector per target/display; call from the active scan loop.
 */
final class CmDetectionFunction {
  enum Step { OBSERVE, HOME, INTENT, VERIFY, KILL, HOLD }
  enum Action { NONE, HOME_INTENT, HOME_KILL }
  static final long FRESH_MS=1500L, MAX_GAP_MS=3000L, RESIDUAL_AD_MS=2000L;
  static final long HOME_DEADLINE_MS=2000L, VERIFY_DEADLINE_MS=15000L;
  static final long COOLDOWN_MS=60000L, WINDOW_MS=900000L;
  private Step step=Step.OBSERVE;
  private String target="";
  private long first=-1,last=-1,entered=-1,returnedAt=-1,windowStart=-1,cooldownUntil=-1;
  private int frames,episodes;
  private boolean escalation,killUsed;

  synchronized void reset(){
    step=Step.OBSERVE;target="";first=last=entered=returnedAt=-1;
    windowStart=cooldownUntil=-1;frames=episodes=0;escalation=killUsed=false;
  }
  synchronized Step step(){return step;}
  synchronized boolean requiresKill(){return step==Step.KILL;}
  synchronized boolean escalated(){return escalation;}
  synchronized int episodes(){return episodes;}
  private void clearFrames(){first=last=-1;frames=0;}
  private static boolean fresh(long capturedAt,long now){
    return capturedAt>0&&now>=capturedAt&&now-capturedAt<FRESH_MS;
  }
  private void hold(long now){
    step=Step.HOLD;entered=now;cooldownUntil=now+COOLDOWN_MS;clearFrames();
  }
  private void addFrame(long capturedAt){
    if(last>=0&&capturedAt<=last)return;
    if(first<0||last>0&&capturedAt-last>MAX_GAP_MS){first=capturedAt;frames=0;}
    last=capturedAt;frames++;
  }
  /** Must be called on fresh frames even while VERIFY is active. */
  synchronized Action observe(String pkg,long capturedAt,long now,boolean ad,boolean gameHome){
    if(pkg==null||pkg.isEmpty()||now<=0)return Action.NONE;
    if(!pkg.equals(target)){reset();target=pkg;}
    if(windowStart<0||now<windowStart||now-windowStart>=WINDOW_MS){
      windowStart=now;episodes=0;
    }
    boolean returnedToGame=fresh(capturedAt,now)&&capturedAt>entered&&gameHome&&!ad;
    if((step==Step.HOLD||step==Step.VERIFY)&&returnedToGame){
      step=Step.OBSERVE;episodes=0;escalation=killUsed=false;clearFrames();
      return Action.NONE;
    }
    if(step==Step.HOLD){
      if(now>=cooldownUntil&&episodes<2){step=Step.OBSERVE;clearFrames();}
      else return Action.NONE;
    }
    if(step==Step.VERIFY){
      if(now-entered>VERIFY_DEADLINE_MS){hold(now);return Action.NONE;}
      if(killUsed)return Action.NONE;
      if(!fresh(capturedAt,now)||capturedAt<=returnedAt)return Action.NONE;
      if(!ad){clearFrames();return Action.NONE;}
      addFrame(capturedAt);
      if(frames>=2&&last-first>=RESIDUAL_AD_MS){
        escalation=true;step=Step.HOME;entered=now;clearFrames();
        return Action.HOME_KILL;
      }
      return Action.NONE;
    }
    if(step!=Step.OBSERVE)return Action.NONE;
    if(!ad||gameHome){clearFrames();return Action.NONE;}
    if(!fresh(capturedAt,now))return Action.NONE;
    addFrame(capturedAt);
    // Two distinct positive observations are a CM detection; the two-second
    // threshold applies ONLY after the initial HOME -> Intent return.
    if(frames<2)return Action.NONE;
    if(episodes>=2){hold(now);return Action.NONE;}
    episodes++;escalation=killUsed=false;
    step=Step.HOME;entered=now;clearFrames();
    return Action.HOME_INTENT;
  }
  /** Fresh foreign-app foreground evidence may start observation, but is not
   * a substitute for verified HOME/registered Intent on the target display. */
  synchronized void changedForeground(){if(step==Step.OBSERVE||step==Step.VERIFY)clearFrames();}
  synchronized boolean confirmedHome(long now,boolean visible){
    if(step!=Step.HOME)return false;
    if(!visible||now<entered||now-entered>HOME_DEADLINE_MS){hold(now);return false;}
    step=escalation?Step.KILL:Step.INTENT;entered=now;return true;
  }
  synchronized boolean confirmedKill(long now,boolean killed){
    if(step!=Step.KILL)return false;
    if(!killed||now<entered){hold(now);return false;}
    killUsed=true;step=Step.INTENT;entered=now;return true;
  }
  synchronized boolean confirmedIntent(long now,boolean dispatched){
    if(step!=Step.INTENT)return false;
    if(!dispatched||now<entered){hold(now);return false;}
    step=Step.VERIFY;entered=returnedAt=now;clearFrames();return true;
  }
  /** Shell contract: 5 = verified HOME + Intent, 7 = HOME + kill + Intent. */
  synchronized boolean confirmedRemote(long now,int bits){
    if(step!=Step.HOME||now<entered||bits!=(escalation?7:5)){
      hold(now);return false;
    }
    if(escalation)killUsed=true;
    step=Step.VERIFY;entered=returnedAt=now;clearFrames();return true;
  }
  synchronized boolean expired(long now){
    if(step==Step.HOME&&now-entered>HOME_DEADLINE_MS||
       step==Step.VERIFY&&now-entered>VERIFY_DEADLINE_MS){
      hold(now);return true;
    }
    return false;
  }
  synchronized void failed(long now){hold(now);}
  synchronized String status(){
    return "CM="+step+" / 復帰試行="+episodes+"/2 / 強制停止="+(killUsed?"済":"未実施");
  }
}
