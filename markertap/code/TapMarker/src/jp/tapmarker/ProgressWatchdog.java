package jp.tapmarker;

import java.util.*;

/** Observes symptoms without changing the automaton or its input decisions. */
final class ProgressWatchdog {
 static final long IMAGE_GRACE_MS=15000,INPUT_GRACE_MS=15000,CONFLICT_GRACE_MS=8000;
 static final class Sample {
  String key="",app="",state="",inputError="",diagnostic="";
  long run,frameAge=-1,analysisMs,inputs,stateAge,expectedWaitMs=120000;
  boolean running=true,terminal,home,expectFrames=true,expectInput;
 }
 static final class Event {
  final String code,app,state,reason;final long duration;
  Event(String code,Sample s,String reason,long duration){this.code=code;app=s.app;state=s.state;this.reason=reason;this.duration=duration;}
 }
 static final class Track {long run,inputs=-1,since,stateSince,noInputSince;boolean inputExpected;String signal="",reported="",state;}
 final Map<String,Track> tracks=new HashMap<>();
 Event observe(Sample s,long now){
  if(!s.running||s.terminal){tracks.remove(s.key);return null;}
  Track t=tracks.get(s.key);if(t==null||t.run!=s.run){t=new Track();t.run=s.run;tracks.put(s.key,t);}
  if(s.home||!s.state.equals(t.state)){t.state=s.state;t.stateSince=now;}
  boolean inputAdvanced=t.inputs>=0&&s.inputs>t.inputs;t.inputs=s.inputs;
  if(!s.expectInput||inputAdvanced||!t.inputExpected)t.noInputSince=now;t.inputExpected=s.expectInput;
  String code="",reason="";long grace=0,duration=0;
  if(s.expectFrames&&(s.frameAge<0||s.frameAge>1500)){code="FRAME_STALE";reason="新しい認識画像を取得できない状態が続いています";grace=IMAGE_GRACE_MS;}
  else if(s.analysisMs>1500){code="ANALYSIS_SLOW";reason="画像判定が1500msを超え、操作に使える結果を返せない状態が続いています";grace=IMAGE_GRACE_MS;}
  else if(!s.inputError.isEmpty()&&!inputAdvanced){code="INPUT_REJECTED";reason="入力拒否・入力未確認が続いています："+AutomationIncident.limit(s.inputError,300);grace=INPUT_GRACE_MS;}
  else if(s.diagnostic.contains("複数の分岐条件が成立")){code="BRANCH_CONFLICT";reason="複数の分岐条件が同時に成立し、操作を選べない状態が続いています";grace=CONFLICT_GRACE_MS;}
  else if(s.expectInput&&now-t.noInputSince>=INPUT_GRACE_MS){code="INPUT_NO_PROGRESS";reason="通常連打を行う状態で、15秒以上入力完了回数が増えていません";duration=now-t.noInputSince;}
  else if(!s.home&&now-t.stateSince>=s.expectedWaitMs){code="STATE_STALL";reason="設定された待機時間と猶予を超えて同じ状態に留まっています";duration=now-t.stateSince;}
  if(code.isEmpty()){t.signal="";t.reported="";return null;}
  if(!code.equals(t.signal)){t.signal=code;t.since=now;t.reported="";}
  if(now-t.since<grace||code.equals(t.reported))return null;
  t.reported=code;return new Event(code,s,reason,duration>0?duration:now-t.since);
 }
 void retain(Set<String> live){tracks.keySet().retainAll(live);}
 static long expectedWait(PhaseEngine e){
  long wait=AdExitPolicy.adState(e.current)?180000:120000;
  PhaseEngine.Phase p=e.phases.get(e.current);if(p==null)return wait;
  for(PhaseEngine.Watch w:p.watches)if(e.eligible(w)){
   long declared=w.condition==PhaseEngine.TIMEOUT?w.timeoutMs:0;
   if(w.action==PhaseEngine.WAIT)declared=Math.max(declared,w.duration);
   if(declared>0)wait=Math.max(wait,declared>Long.MAX_VALUE-30000?Long.MAX_VALUE:declared+30000);
  }
  return wait;
 }
}
