package jp.tapmarker;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/**
 * Independent, per-display observation supervisor. Only the owner thread (the
 * Android handler thread) mutates this class. No actions or taps are inferred
 * from an unknown frame: this circuit only requests recapture/escalation.
 *
 * This is an engineering circuit inspired by recurrent state memory; it does
 * not claim biological BANC topology. BancRuntime remains the persistent,
 * evidence-gated action learning component.
 */
final class AutonomySupervisor {
 static final int NONE=0, REFRESH=1, CANCEL_ANALYSIS=2;
 static final long CHECK_MS=1000, ANALYSIS_LIMIT_MS=10000, FRAME_STALE_MS=1500;
 static final long FRAME_RETRY_MS=2500, ESCALATE_AFTER_MS=30000;
 static final int HISTORY_LIMIT=24;
 static final class Memory {
  final String state;final long at;final boolean home;
  Memory(String state,long at,boolean home){this.state=state;this.at=at;this.home=home;}
 }
 private final ArrayDeque<Memory> history=new ArrayDeque<>();
 private final Map<String,Integer> failures=new HashMap<>();
 private String lastState="",lastFailure="";
 private long lastStateAt=-1,lastHomeAt=-1,inFlightAt=-1,lastRefreshAt=-1,unhealthySince=-1;
 private long lastFrameAt=-1;private volatile int revision;private int failStreak,successes,transitions,cancellations;
 private volatile boolean inFlight;

 int begin(long now){
  if(inFlight)invalidate(); // stale callback must not consume a new ticket
  inFlight=true;inFlightAt=now;return ++revision;
 }
 boolean current(int ticket){return inFlight&&ticket==revision;}
 /** Invalidates callbacks from superseded work, even when the worker ignores interruption. */
 void invalidate(){inFlight=false;inFlightAt=-1;revision++;}
 void complete(int ticket,long capturedAt,long now,boolean good){
  if(!current(ticket))return;
  invalidate();
  if(good&&AnalysisBudget.fresh(capturedAt,now)&&capturedAt>lastFrameAt){
   // A frozen game frame is valid when the acquisition timestamp advances.
   if(capturedAt>lastFrameAt)lastFrameAt=capturedAt;
   failStreak=0;unhealthySince=-1;lastFailure="";successes++;
  }else fail(good?"invalid-frame":"recognition",now);
 }
 void observeState(String state,long now,boolean home){
  if(state==null)state="";
  if(!state.equals(lastState)){
   lastState=state;lastStateAt=now;transitions++;
   history.addLast(new Memory(state,now,home));
   while(history.size()>HISTORY_LIMIT)history.removeFirst();
  }
  // The timer may still hold yesterday's home flag while recognition is stuck.
  // Only complete() with a distinct, fresh image can clear a failure episode.
  if(home&&AnalysisBudget.fresh(lastFrameAt,now))lastHomeAt=now;
 }
 private void fail(String reason,long now){
  lastFailure=reason;failStreak=Math.min(100,failStreak+1);
  failures.put(reason,Math.min(10000,failures.containsKey(reason)?failures.get(reason)+1:1));
  if(unhealthySince<0)unhealthySince=now;
 }
 /** Called by a separate Handler timer, not by the recognition worker. */
 int tick(long now,long frameAt,boolean paused,boolean waitingForDisplay){
  if(paused||waitingForDisplay){
   // Manual control or display reconstruction is never an analysis failure.
   if(inFlight)invalidate();
   unhealthySince=-1;failStreak=0;return NONE;
  }
  if(inFlight){
   if(now-inFlightAt<ANALYSIS_LIMIT_MS)return NONE;
   invalidate();cancellations++;fail("analysis-timeout",now);
   return CANCEL_ANALYSIS;
  }
  // Monitor frame freshness independently of the automaton phase/wait timer.
  if(!AnalysisBudget.fresh(frameAt,now)){
   if(unhealthySince<0)unhealthySince=now;
   if(lastRefreshAt<0||now-lastRefreshAt>=FRAME_RETRY_MS){lastRefreshAt=now;return REFRESH;}
  }else if(failStreak==0){unhealthySince=-1;}
  return NONE;
 }
 boolean recoveryDue(long now){return unhealthySince>=0&&now-unhealthySince>=ESCALATE_AFTER_MS;
 }
 long retryDelay(){return Math.min(2400,400L*(1L<<Math.min(3,Math.max(0,failStreak-1))));}
 int failures(String reason){Integer n=failures.get(reason);return n==null?0:n;}
 int historySize(){return history.size();}
 int successCount(){return successes;}
 String report(long now){return "監視：成功="+successes+" / 状態遷移="+transitions+" / 解析破棄="+cancellations+" / 連続失敗="+failStreak+" / 履歴="+history.size()+" / 最新状態="+lastState+" / 異常="+(unhealthySince<0?"なし":((now-unhealthySince)/1000)+"秒")+(lastFailure.isEmpty()?"":" / 原因="+lastFailure);}
 void reset(){invalidate();inFlight=false;lastState="";lastStateAt=lastHomeAt=lastFrameAt=lastRefreshAt=unhealthySince=-1;failStreak=0;lastFailure="";history.clear();}
}
