package jp.tapmarker;
import java.util.*;

/** Ad exits never require playing the advertised game. State and input budget are per runner. */
final class AdExitPolicy {
 private PhaseEngine observedEngine;
 private boolean engaged;private int backs;private long sentAt=-1;
 private volatile long playableSince=-1,playableAt=-1,playableNext;private volatile int playableFrames;
 private volatile int playableReturns,playableRestarts;
 final AdExitAttempts controls=new AdExitAttempts();
 final PopupExitAttempts popupExits=new PopupExitAttempts();
 final PopupActionConfirmation popupReceipt=new PopupActionConfirmation();
 final RecoveryEscalation recovery=new RecoveryEscalation();
 void reset(){resetAd();recovery.reset();popupExits.reset();popupReceipt.reset();}
 /** A replacement display receives one new control budget; the episode restart budget is retained. */
 void newDisplay(){resetAd();popupExits.reset();popupReceipt.reset();}
 private void resetAd(){observedEngine=null;engaged=false;backs=0;sentAt=-1;playableSince=playableAt=-1;playableNext=0;playableFrames=playableReturns=playableRestarts=0;controls.reset();}
 void filter(PhaseEngine e,Map<String,float[]> samples,int width,int height,long capturedAt,long now){
  if(!e.windowRelative){
   popupReceipt.filter(samples,width,height,capturedAt,now);
   popupExits.filter(samples,width,height,capturedAt,now);
   if(popupExits.failed(now)||popupReceipt.failed(now)){
    String recoveryState=AdInterruption.recoveryState(e.current);
    PhaseEngine.Phase phase=e.phases.get(e.current);
    if(phase!=null&&!phase.terminal&&!Arrays.asList("account","error","unknown").contains(e.current.substring(AdInterruption.prefix(e.current).length()))&&!recoveryState.isEmpty()&&e.phases.containsKey(recoveryState)&&!e.current.equals(recoveryState))e.enter(recoveryState,now);
    engaged=true;
   }
  }
  if(!e.windowRelative){recovery.observe(RecoveryEvidence.home(e,samples),capturedAt,now);if(recovery.confirmedHome(now)){popupExits.reset();popupReceipt.reset();}}
  observe(e,samples);observedEngine=e;if(e.windowRelative)return;
  if(gameVisible(e,samples)){for(String id:new String[]{AdCloseAutomaton.LEFT,AdCloseAutomaton.RIGHT}){samples.put(id,new float[]{0});samples.remove(id+AdCloseAutomaton.CONTROL_POINT);samples.remove(id+AdCloseAutomaton.POINT);}return;}
  observePlayable(samples,width,height,capturedAt,now);
  controls.select(samples,width,height,capturedAt,now);
  boolean exit=false;for(String id:new String[]{AdCloseAutomaton.LEFT,AdCloseAutomaton.RIGHT})if(AdCloseAutomaton.controlPoint(id,samples,width,height)!=null){exit=true;break;}
  if(exit){
   // An advertisement can cover a dialog immediately after its Cancel tap.
   // Covered pixels cannot prove the dialog was dismissed. Otherwise its
   // DISAPPEAR branch conflicts with this positively observed ad exit.
   PhaseEngine.Phase phase=e.phases.get(e.current);
   if(phase!=null)for(PhaseEngine.Watch w:phase.watches)if(w.condition==PhaseEngine.DISAPPEAR&&e.eligible(w)){
    samples.remove(w.regionId);e.confirmations.remove(w.id);e.latched.remove(w.id);
   }
  }
 }
 /** Playable identity is independent of state-entry time and cannot restart the
  * deadline when a Back or an ad card returns to verify. Unknown ads retain
  * their existing countdown; visible Close controls retain first priority. */
 void observePlayable(Map<String,float[]> samples,int w,int h,long at,long now){
  if(at<0||at>now||now-at>=1500)return;
  if(!PlayableAdEvidence.visible(samples,w,h)){playableSince=playableAt=-1;playableFrames=0;return;}
  if(at<=playableAt)return;
  if(playableAt<0||at-playableAt>2500){playableSince=at;playableFrames=0;}
  playableAt=at;playableFrames++;engaged=true;
 }
 PhaseEngine.Watch withFallback(PhaseEngine e,PhaseEngine.Watch event,Map<String,float[]> samples,long now){
  if(event!=null||e.windowRelative||controls.selected!=null||controls.cooling(now)||gameVisible(e,samples))return event;
  if(!popupExits.failed(now)&&!popupReceipt.failed(now)&&(playableFrames<2||playableAt<0||now<playableAt||now-playableAt>=1500||now-playableSince<8000||now<playableNext))return event;
  PhaseEngine.Phase phase=e.phases.get(e.current);if(phase==null||phase.terminal)return null;
  for(PhaseEngine.Watch w:phase.watches)if(w.fallback&&w.condition==PhaseEngine.TIMEOUT&&w.action==PhaseEngine.RETURN_APP&&e.eligible(w))return w;
  return null;
 }
 boolean playableCurrent(long now){return playableFrames>=2&&playableAt>=0&&now>=playableAt&&now-playableAt<1500;}
 boolean restartNeeded(long now){return !recovery.exhausted()&&(playableReturns>0&&playableRestarts<2&&playableCurrent(now)&&now>=playableNext||recovery.restartReady(now));}
 boolean restartCurrent(long now){return playableCurrent(now)||recovery.unknownCurrent(now);}
 boolean returnAllowed(long now){return recovery.normalAllowed(now)||restartNeeded(now);}
 void returned(long now){returned(now,false);}
 void returned(long now,boolean restarted){recovery.sent(now,restarted);if(playableAt>=0){if(restarted)playableRestarts++;else playableReturns++;playableNext=now+(restarted?30000:8000);}}
 /** Return preflight must use the same exit budget as the observation loop.
  * A still-drawn, exhausted Close is not proof that relaunch should be cancelled. */
 boolean preferVisibleBranch(PhaseEngine e,Map<String,float[]> samples,int width,int height,long capturedAt,long now){
  if(capturedAt<0||capturedAt>now||now-capturedAt>=ContinuousTapPolicy.MAX_FRAME_AGE_MS)return false;
  filter(e,samples,width,height,capturedAt,now);
  return controls.cooling(now)||RecoveryRequest.visibleBranch(e,samples);
 }
 boolean preflight(PhaseEngine.Watch w,Map<String,float[]> samples,int width,int height){return !AdCloseAutomaton.handles(w.regionId)||(observedEngine==null||!gameVisible(observedEngine,samples))&&controls.preflight(w.regionId,samples,width,height);}
 /** Count a verified dispatch request, including rejected/cancelled inputs and invalidated preflights. */
 void controlSent(PhaseEngine.Watch w,long now){if(w.action==PhaseEngine.CENTER){if(AdCloseAutomaton.handles(w.regionId))controls.sent(w.regionId,now);popupExits.sent(w.regionId,now);popupReceipt.sent(w.regionId,now);}}
 static boolean adState(String state){return state.endsWith("-ad")||state.endsWith("-reward-ad")||state.endsWith("-mission-ad");}
 static boolean gameVisible(PhaseEngine e,Map<String,float[]> samples){
  PhaseEngine.Phase phase=e.phases.get(e.current);if(phase==null)return false;
  for(PhaseEngine.Watch w:phase.watches)if(!AdCloseAutomaton.handles(w.regionId)&&(w.condition==PhaseEngine.MATCH||w.condition==PhaseEngine.DISAPPEAR)&&w.reference!=null&&e.eligible(w)&&w.distance(samples.get(w.regionId))<=w.threshold&&e.guardsMatch(w,samples))return true;
  return false;
 }
 void observe(PhaseEngine e,Map<String,float[]> samples){
  if(gameVisible(e,samples)){resetAd();return;}
  if(adState(e.current))engaged=true;
  for(String id:new String[]{AdCloseAutomaton.LEFT,AdCloseAutomaton.RIGHT}){float[] s=samples.get(id);if(s!=null&&s.length==1&&s[0]==1)engaged=true;}
 }
 static void install(PhaseEngine e){
  for(PhaseEngine.Phase phase:e.phases.values())if(adState(phase.id))for(PhaseEngine.Watch w:phase.watches)
   if(w.condition==PhaseEngine.TIMEOUT&&w.action==PhaseEngine.RETURN_APP&&w.fallback)w.timeoutMs=Math.max(30000,w.timeoutMs);
 }
 int choose(PhaseEngine e,PhaseEngine.Watch w,Map<String,float[]> samples,long now){
  if(w.action!=PhaseEngine.RETURN_APP||w.condition!=PhaseEngine.TIMEOUT||!w.fallback||e.windowRelative||gameVisible(e,samples))return PhaseEngine.RETURN_APP;
  if(controls.cooling(now))return PhaseEngine.WAIT;
  if(recovery.exhausted())return PhaseEngine.WAIT;
  if(playableAt>=0&&playableRestarts>=2)return PhaseEngine.WAIT;
  if(playableAt>=0&&now>=playableAt&&now-playableAt<1500&&now<playableNext)return PhaseEngine.WAIT;
  if(!engaged&&!adState(e.current))return PhaseEngine.RETURN_APP;
  engaged=true;if(sentAt>=0&&now-sentAt<2000)return PhaseEngine.WAIT;
  return backs<2?PhaseEngine.BACK:PhaseEngine.RETURN_APP;
 }
 void sent(long now){engaged=true;backs++;sentAt=now;if(playableAt>=0)playableNext=now+2500;}
 void rejected(){engaged=true;backs=2;}
 String status(PhaseEngine e,Map<String,float[]> samples,long now){
  if(popupExits.failed(now)||popupReceipt.failed(now))return "広告：閉じる入力後も表示が残留 / 戻る・対象アプリへの復帰を確認中";
  if(!gameVisible(e,samples)&&!recovery.status(now).isEmpty())return recovery.status(now);
  if(gameVisible(e,samples)||!engaged&&!adState(e.current))return "";
  if(playableAt>=0&&playableRestarts>=2)return "広告：再起動後も残留 / この画面の新しい表示を監視中";
  for(String id:new String[]{AdCloseAutomaton.LEFT,AdCloseAutomaton.RIGHT}){float[] value=samples.get(id);if(value!=null&&value.length==1&&value[0]==1)return "広告：閉じる・スキップを確認中";}
  PhaseEngine.Phase phase=e.phases.get(e.current);
  if(phase!=null)for(PhaseEngine.Watch w:phase.watches)if(w.fallback&&w.condition==PhaseEngine.TIMEOUT&&w.action==PhaseEngine.RETURN_APP){long remaining=Math.max(0,w.timeoutMs-(now-e.enteredAt));return remaining>0?"広告：終了印待ち / 復帰確認まで"+((remaining+999)/1000)+"秒":"広告：戻る・復帰を確認中";}
  return "広告：終了印を待機";
 }
 static boolean exitLabel(String label){String t=BancAutonomy.normalize(label);return Arrays.asList("閉じる","とじる","広告を閉じる","広告をスキップ","スキップ","close","closead","closeads","dismiss","skip","skipad","skipads","×","x").contains(t);}
 static String corner(String label,float[] b){return corner(label,b,0);}
 static String corner(String label,float[] b,float top){
  if(!exitLabel(label)||!BancAutonomy.validBounds(b))return "";
  String t=BancAutonomy.normalize(label);
  // Back may reveal an explicit ad-dismissal confirmation in the centre.
  if(Arrays.asList("広告を閉じる","closead","closeads").contains(t)&&b[2]-b[0]<=.80f&&b[3]-b[1]<=.25f)return AdCloseAutomaton.RIGHT;
  if(!Float.isFinite(top)||top<0||top>.35f)return "";
  if(b[3]>Math.max(.20f,top+(1-top)*.14f)||b[2]-b[0]>.25f||b[3]-b[1]>.16f)return "";
  float x=(b[0]+b[2])/2;return x<=.22f?AdCloseAutomaton.LEFT:x>=.78f?AdCloseAutomaton.RIGHT:"";
 }
}
