package jp.tapmarker;

import java.text.Normalizer;
import java.util.*;

/** Evidence gate, stall recovery and acquired skills. Does not invent taps or completion. */
public final class BancAutonomy {
 public static final String OVERVIEW="@banc:overview",SCAN="@banc:scan";
 public static final long STALL_MS=2500,SCAN_MS=1000,RESULT_MS=15000;
 public static final int NONE=0,RESYNC=1,CONTROL=2,WAIT=3;
 public static final class Control {
  public final String label,kind,id;public final float[] bounds;
  public Control(String label,String kind,float[] bounds){this.label=normalize(label);this.kind=kind;this.bounds=bounds.clone();this.id=kind+":"+this.label+":"+Math.round(bounds[0]*100)+":"+Math.round(bounds[1]*100);}
 }
 public static final class Ui {
  public final String context;public final List<Control> controls;
  public Ui(String context,List<Control> controls){this.context=normalizeContext(context);this.controls=controls;}
  public static Ui empty(){return new Ui("",Collections.emptyList());}
 }
 public static final class Skill {
  public String id,context,kind,label,destination;public float[] before,bounds;
  public int attempts,successes,failures;public double rewardSum;
  public double reliability(){return successes/(double)Math.max(1,attempts);}
  public double averageReward(){return rewardSum/Math.max(1,attempts);}
  public boolean acquired(){return successes>=2&&reliability()>=.66&&averageReward()>=.05;}
 }
 public static final class Decision {
  public int kind;public String target="",reason="",id="";public Control control;public double activation;
  static Decision none(){return new Decision();}
  static Decision waitFor(String reason){Decision d=new Decision();d.kind=WAIT;d.reason=reason;return d;}
 }
 static final class Evidence {String target,watch;float score;}
 static final class Pending {String phase,historyRule,historyPhase,id,context,result="";long epoch,started,sentAt,confirmedAt,resultWait;int confirmed;Control control;float[] before;boolean sent;Set<String> beforeTargets=new HashSet<>();}
 public final BancSelector selector=new BancSelector();
 public final BancKnowHow knowHow;public final String sourceGame;public boolean transferEnabled=true;public int transferredProposals;
 public final LinkedHashMap<String,Skill> skills=new LinkedHashMap<>();
 private final Set<String> attempted=new HashSet<>();
 private Pending pending;private String phase="",proposal="",stableUi="";
 private long progressAt,lastFrame=-1,proposalAt,uiAt,nextAttempt,lastScan=-SCAN_MS;
 private int proposalCount,uiCount,attemptsInBatch;private float[] priorOverview;
 private String repeatedAction="";private float[] actionOverview;private int unchangedActions;private long normalBlockedUntil;
 public int recoveries,verified,failures,revision;public String status="登録済み条件を優先して観察";
 public BancAutonomy(){this(new BancKnowHow(),"unscoped");}
 public BancAutonomy(BancKnowHow knowHow,String sourceGame){this.knowHow=knowHow;this.sourceGame=sourceGame;}
 public static String normalize(String value){return Normalizer.normalize(value==null?"":value,Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).replaceAll("\\s+","").trim();}
 public static String normalizeContext(String value){return normalize(value).replaceAll("[0-9]+(?:[.,][0-9]+)?","#");}
 public static String safeKind(String label,String context){
  String text=normalize(label),scope=normalize(context);
  for(String forbidden:new String[]{"購入","課金","支払","決済","送金","出金","退会","削除","ログイン","同意","許可","purchase","payment","delete"})if(scope.contains(forbidden)||text.contains(forbidden))return "";
  if(Arrays.asList("閉じる","とじる","close","キャンセル","cancel").contains(text))return "close";
  if(Arrays.asList("このままはじめる","このまま始める","ゲームへ戻る","ゲームに戻る").contains(text))return "resume";
  if(Arrays.asList("宝箱を開ける","宝箱をあける").contains(text))return "chest";
  if(Arrays.asList("ゲット","受け取る","報酬を受け取る","コインを受け取る").contains(text)&&containsAny(scope,"報酬","ミッション","コイン","reward"))return "claim";
  if(Arrays.asList("次のステージ","次のステージへ").contains(text)&&containsAny(scope,"クリア","ステージクリア","clear"))return "next";
  return "";
 }
 static boolean containsAny(String text,String... words){for(String word:words)if(text.contains(word))return true;return false;}
 public synchronized void reset(long now){pending=null;phase="";proposal="";proposalCount=0;stableUi="";uiCount=0;priorOverview=null;lastFrame=-1;progressAt=now;nextAttempt=0;lastScan=-SCAN_MS;attempted.clear();attemptsInBatch=0;repeatedAction="";unchangedActions=0;normalBlockedUntil=0;selector.discardCredit();status="登録済み条件を優先して観察";}
 /** Registered transitions preempt an exploratory action's result wait. */
 public synchronized PhaseEngine.Watch pollRegistered(PhaseEngine e){PhaseEngine.Watch event=e.poll();registered(e,event);return event;}
 public synchronized void registered(PhaseEngine e,PhaseEngine.Watch event){
  if(event!=null&&pending!=null&&(event.action!=PhaseEngine.WAIT||!event.nextPhase.isEmpty()&&!event.nextPhase.equals(e.current))){cancel();status="登録済みの画面遷移を優先・旧操作の結果待ちを終了";}
 }
 public synchronized boolean pending(){return pending!=null;}
 public synchronized void normalProgress(long now){progressAt=now;proposal="";proposalCount=0;}
 /** Bound repeated transition-button sends when their source screen never changes. */
 public synchronized boolean permitNormal(PhaseEngine e,PhaseEngine.Watch w,Map<String,float[]> samples,long now){
  if(w.action!=PhaseEngine.CENTER&&w.action!=PhaseEngine.CURVE||w.nextPhase.isEmpty()||w.nextPhase.equals(e.current))return true;
  float[] raw=samples.get(OVERVIEW);if(raw==null)return true;
  boolean unchanged=w.id.equals(repeatedAction)&&actionOverview!=null&&FlyCircuit.distance(actionOverview,raw)<=.002f;
  if(unchanged&&now<normalBlockedUntil){progressAt=now-STALL_MS;return false;}
  if(unchanged)unchangedActions++;else{repeatedAction=w.id;actionOverview=raw.clone();unchangedActions=1;normalBlockedUntil=0;}
  if(unchangedActions>3){unchangedActions=0;normalBlockedUntil=now+8000;progressAt=now-STALL_MS;status="同じ遷移操作の結果が未確認 / 8秒待機して別の根拠を探索";return false;}return true;
 }
 public synchronized boolean scanDue(PhaseEngine e,long now){return pending!=null||now-progressAt>=STALL_MS;}
 public synchronized boolean sampleScan(PhaseEngine e,long now){if(!scanDue(e,now)||now-lastScan<SCAN_MS)return false;lastScan=now;return true;}
 public static List<PhaseEngine.Region> recoveryRegions(PhaseEngine e){
  LinkedHashMap<String,PhaseEngine.Region> found=new LinkedHashMap<>();
  for(PhaseEngine.Phase p:e.phases.values())if(!p.terminal)for(PhaseEngine.Watch w:p.watches)if(w.condition==PhaseEngine.MATCH&&w.action!=PhaseEngine.STOP&&e.eligible(w)&&w.reference!=null){add(e,found,w.regionId);for(String id:w.guardIds)add(e,found,id);}
  // Bound additional sampling cost. No recovery decisions are made from incomplete evidence.
  ArrayList<PhaseEngine.Region> out=new ArrayList<>(found.values());return out.size()>128?out.subList(0,128):out;
 }
 private static void add(PhaseEngine e,Map<String,PhaseEngine.Region> out,String id){PhaseEngine.Region r=e.regions.get(id);if(r!=null)out.put(id,r);}
 static boolean textured(float[] ref){if(ref==null||ref.length!=768)return false;for(float v:ref)if(!Float.isFinite(v))return false;return ObservationLearner.variance(ref)>=.001f;}
 static boolean separate(float[] a,float[] b){if(a==null||b==null||a.length!=4||b.length!=4)return false;return a[2]<=b[0]||b[2]<=a[0]||a[3]<=b[1]||b[3]<=a[1];}
 /** Two independent anchors or the existing paired-reference contract are required. */
 public static boolean strongMatch(PhaseEngine e,PhaseEngine.Watch w,Map<String,float[]> samples){
  if(w.condition!=PhaseEngine.MATCH||w.reference==null||!e.eligible(w)||w.threshold<0||!Float.isFinite(w.threshold))return false;
  float[] raw=samples.get(w.regionId);float distance=w.distance(raw);if(raw==null||!Float.isFinite(distance)||distance>w.threshold||!e.guardsMatch(w,samples))return false;
  for(float value:raw)if(!Float.isFinite(value))return false;
  for(String id:w.guardIds){float[] guard=samples.get(id);if(guard==null)return false;for(float value:guard)if(!Float.isFinite(value))return false;}
  if(w.pairedReference&&w.reference.length==1536&&raw.length==1536)return textured(Arrays.copyOfRange(w.reference,0,768))&&textured(Arrays.copyOfRange(w.reference,768,1536));
  PhaseEngine.Region primary=e.regions.get(w.regionId);if(primary==null||!textured(w.reference))return false;
  for(int i=0;i<w.guardIds.length&&i<w.guardReferences.length;i++){PhaseEngine.Region guard=e.regions.get(w.guardIds[i]);if(guard!=null&&separate(primary.bounds,guard.bounds)&&textured(w.guardReferences[i]))return true;}
  return false;
 }
 private static boolean visualCurrent(PhaseEngine e,Map<String,float[]> samples){PhaseEngine.Phase p=e.phases.get(e.current);if(p==null)return false;for(PhaseEngine.Watch w:p.watches)if(w.condition==PhaseEngine.MATCH&&!(w.fallback&&w.action==PhaseEngine.WAIT&&w.nextPhase.equals(e.current))&&e.eligible(w)&&w.reference!=null&&w.distance(samples.get(w.regionId))<=w.threshold&&e.guardsMatch(w,samples))return true;return false;}
 static LinkedHashMap<String,Evidence> evidence(PhaseEngine e,Map<String,float[]> samples,Set<String> blocked){
  LinkedHashMap<String,Evidence> candidates=new LinkedHashMap<>();
  for(PhaseEngine.Phase p:e.phases.values())if(!p.terminal)for(PhaseEngine.Watch w:p.watches){
   if(w.action==PhaseEngine.STOP||!strongMatch(e,w,samples))continue;
   String target=w.action==PhaseEngine.WAIT&&!w.nextPhase.isEmpty()?w.nextPhase:p.id;
   PhaseEngine.Phase destination=e.phases.get(target);
   if(destination==null||destination.terminal||blocked.contains(target))continue;
   Evidence ev=new Evidence();ev.target=target;ev.watch=w.id;float distance=w.distance(samples.get(w.regionId));ev.score=w.threshold==0?0:distance/w.threshold;
   Evidence previous=candidates.get(target);if(previous==null||ev.score<previous.score)candidates.put(target,ev);
  }
  return candidates;
 }
 public synchronized Decision review(PhaseEngine e,Map<String,float[]> samples,Ui ui,long capturedAt,long now,Set<String> blocked){
  if(e.recordingReviewRequired||e.terminal()){cancel();return Decision.none();}
  if(capturedAt<=lastFrame||capturedAt>now||now-capturedAt>1500)return pending==null?Decision.none():Decision.waitFor("新しい操作結果の画像を待機");
  lastFrame=capturedAt;
  if(!phase.equals(e.current)){phase=e.current;if(now>=normalBlockedUntil)progressAt=now;proposal="";proposalCount=0;}
  float[] overview=samples.get(OVERVIEW);boolean steady=overview!=null&&overview.length==768&&priorOverview!=null&&FlyCircuit.distance(priorOverview,overview)<=.004f;priorOverview=overview==null?null:overview.clone();
  if(ui.context.equals(stableUi)&&steady){uiCount++;}else{stableUi=ui.context;uiCount=1;uiAt=capturedAt;}
  if(pending!=null)return reviewResult(e,samples,overview,capturedAt,now,blocked);
  if(now-progressAt<STALL_MS||e.awaitingBranch()||e.diagnostic.contains("複数の分岐"))return Decision.none();
  if(samples.containsKey(SCAN)){
   LinkedHashMap<String,Evidence> known=evidence(e,samples,blocked);
   if(known.size()>1){status="複数の登録画面に一致 / 自律復帰を保留";return Decision.none();}
   if(known.size()==1){String target=known.keySet().iterator().next();if(!target.equals(e.current)){
    String key="resync:"+target;if(!confirmProposal(key,capturedAt))return Decision.waitFor("復帰先の独立した目印を再確認");
    BancSelector.Selection selection=selector.select(Collections.singletonList(new BancSelector.Candidate(key,1,.85,1)));
    if(selection==null){status="BANC回路から選択信号がないため待機";return Decision.none();}
    Decision decision=new Decision();decision.kind=RESYNC;decision.target=target;decision.id=key;decision.activation=selection.activation;decision.reason="2領域・2枚の画像で確認した登録状態へ復帰";return decision;
   }}else{proposal="";proposalCount=0;}
  }
  if(visualCurrent(e,samples)&&now>=normalBlockedUntil||!steady||ui.context.isEmpty()||uiCount<2||capturedAt-uiAt<250||now<nextAttempt)return Decision.none();
  if(attemptsInBatch>=3){attemptsInBatch=0;nextAttempt=now+30000;status="未知画面の操作候補を30秒待って再確認";return Decision.none();}
  ArrayList<BancSelector.Candidate> candidates=new ArrayList<>();Map<String,Control> byId=new LinkedHashMap<>();
  Set<String> ambiguous=new HashSet<>();
  for(Control control:ui.controls){String kind=safeKind(control.label,ui.context);if(!kind.equals(control.kind)||kind.isEmpty()||!validBounds(control.bounds))continue;if(byId.containsKey(control.kind)&&!Arrays.equals(byId.get(control.kind).bounds,control.bounds)){ambiguous.add(control.kind);continue;}byId.put(control.kind,control);}
  for(Control control:byId.values()){
   if(ambiguous.contains(control.kind))continue;String attempt=attemptKey(e.current,ui.context,control,overview);
   if(attempted.contains(attempt))continue;Skill skill=matchingSkill(control,ui.context,overview);
   BancKnowHow.Advice advice=transferEnabled?knowHow.forGame(sourceGame,control.kind):new BancKnowHow.Advice();
   double bias=(skill==null?.03:toolBias(skill))+advice.bias();
   candidates.add(new BancSelector.Candidate(control.id,2,.8+bias,skill!=null&&skill.acquired()?.97:.8));
  }
  BancSelector.Selection selected=selector.select(candidates);if(selected==null){status=ui.controls.isEmpty()?"操作を読めない画面 / 実演または録画で新手順を追加できます":"一致する操作候補なし / 画面を監視";return Decision.none();}
  Control control=null;for(Control candidate:byId.values())if(candidate.id.equals(selected.candidate.id))control=candidate;
  if(control==null)return Decision.none();Decision decision=new Decision();decision.kind=CONTROL;decision.control=control;decision.id=control.id;decision.activation=selected.activation;decision.reason="実在するボタンをBANC回路で選択 / 操作後の結果を確認";if(transferEnabled&&knowHow.forGame(sourceGame,control.kind).verified()){decision.reason="他ゲームのノウハウを候補へ適用 / 実行先のボタンと結果を再確認";transferredProposals++;}return decision;
 }
 static boolean validBounds(float[] b){return b!=null&&b.length==4&&Float.isFinite(b[0])&&Float.isFinite(b[1])&&Float.isFinite(b[2])&&Float.isFinite(b[3])&&b[0]>=0&&b[1]>=0&&b[2]<=1&&b[3]<=1&&b[2]>b[0]&&b[3]>b[1];}
 private boolean confirmProposal(String key,long at){if(!key.equals(proposal)){proposal=key;proposalAt=at;proposalCount=1;return false;}proposalCount++;return proposalCount>=2&&at-proposalAt>=250;}
 private Decision reviewResult(PhaseEngine e,Map<String,float[]> samples,float[] overview,long at,long now,Set<String> blocked){
  Pending action=pending;
  if(!action.phase.equals(e.current)||action.epoch!=e.epoch){cancel();status="他の処理へ移ったため旧操作の学習を破棄";return Decision.none();}
  if(now-action.started>=action.resultWait){fail(now,"操作結果を確認できず / 同じ画面の再押下を保留");return Decision.waitFor(status);}
  if(!action.sent||at<=action.sentAt||overview==null||action.before==null||FlyCircuit.distance(action.before,overview)<=.002f)return Decision.waitFor("ボタン送信だけで完了とせず、結果画面を確認中");
  if(!samples.containsKey(SCAN))return Decision.waitFor("操作結果の登録画面を再取得中");
  LinkedHashMap<String,Evidence> known=evidence(e,samples,blocked);
  if(known.size()!=1){action.result="";action.confirmed=0;return Decision.waitFor("操作後の画面が未登録・曖昧 / 監視を継続");}
  String target=known.keySet().iterator().next();
  if(action.beforeTargets.contains(target)){action.result="";action.confirmed=0;return Decision.waitFor("操作前と同じ登録表示 / 背景の動きだけでは成功として学習しません");}
  if(!target.equals(action.result)){action.result=target;action.confirmed=1;action.confirmedAt=at;return Decision.waitFor("操作後の独立した目印を2枚目で確認");}
  if(++action.confirmed<2||at-action.confirmedAt<250)return Decision.waitFor("操作結果の安定を確認中");
  record(action,target,true);if(transferEnabled)knowHow.observe(sourceGame,action.control.kind,true,now-action.sentAt);selector.reinforce(action.id,.2);attempted.remove(attemptKey(action.phase,action.context,action.control,action.before));verified++;revision++;pending=null;progressAt=now;nextAttempt=now+1500;
  Decision decision=new Decision();decision.kind=RESYNC;decision.target=target;decision.id=action.id;decision.reason="結果画面の2領域を確認 / 成功手順を学習";status=decision.reason;return decision;
 }
 public synchronized boolean begin(Decision d,PhaseEngine e,Ui ui,Map<String,float[]> samples,long now){
  if(pending!=null||d.kind!=CONTROL||d.control==null||e.recordingReviewRequired||e.terminal()||!safeKind(d.control.label,ui.context).equals(d.control.kind))return false;
  float[] overview=samples.get(OVERVIEW);if(overview==null||overview.length!=768)return false;
  Pending action=new Pending();action.phase=e.current;action.epoch=e.epoch;action.historyRule=e.lastCompletedRule;action.historyPhase=e.sourcePhase;action.id=d.id;action.control=d.control;action.context=ui.context;action.before=overview.clone();action.started=now;action.resultWait=transferEnabled?knowHow.forGame(sourceGame,d.control.kind).resultWait():RESULT_MS;
  action.beforeTargets.addAll(evidence(e,samples,Collections.emptySet()).keySet());attempted.add(attemptKey(e.current,ui.context,d.control,overview));if(attempted.size()>256){attempted.clear();attempted.add(attemptKey(e.current,ui.context,d.control,overview));}attemptsInBatch++;pending=action;status=d.reason;return true;
 }
 public synchronized void sent(long now){if(pending!=null){pending.sent=true;pending.sentAt=now;}}
 public synchronized void fail(long now,String reason){long retryWait=8000;if(pending!=null){record(pending,"",false);if(transferEnabled){knowHow.observe(sourceGame,pending.control.kind,false,0);retryWait=knowHow.forGame(sourceGame,pending.control.kind).retryWait();}selector.reinforce(pending.id,-.5);failures++;revision++;pending=null;}nextAttempt=now+retryWait;status=reason;}
 public synchronized void cancel(){pending=null;selector.discardCredit();proposal="";proposalCount=0;}
 public synchronized boolean commitResync(Decision d,PhaseEngine e,long now){
  PhaseEngine.Phase destination=e.phases.get(d.target);if(d.kind!=RESYNC||destination==null||destination.terminal||e.terminal()||e.recordingReviewRequired)return false;
  String history=e.sourcePhase,completed=e.lastCompletedRule;e.enter(d.target,now);e.sourcePhase=history;e.lastCompletedRule=completed;
  // Resynchronization is observation, not a rewarded executed action.
  selector.discardCredit();recoveries++;progressAt=now;phase=e.current;proposal="";proposalCount=0;status=d.reason;return true;
 }
 static String attemptKey(String phase,String context,Control control,float[] overview){return phase+"|"+context+"|"+control.kind+"|"+screenHash(overview);}
 static String screenHash(float[] values){if(values==null)return "";int h=1;for(float value:values)h=31*h+Math.round(value*8);return Integer.toHexString(h);}
 private Skill matchingSkill(Control control,String context,float[] before){for(Skill skill:skills.values())if(skill.kind.equals(control.kind)&&skill.label.equals(control.label)&&skill.context.equals(context)&&skill.before!=null&&FlyCircuit.distance(skill.before,before)<=.004f)return skill;return null;}
 private static double toolBias(Skill s){int attempts=Math.max(1,s.attempts);return Math.max(-.18,Math.min(.18,s.averageReward()*.10+(s.reliability()-.5)*.06+.04/Math.sqrt(attempts)));}
 private void record(Pending action,String destination,boolean ok){
  Skill skill=matchingSkill(action.control,action.context,action.before);
  if(skill==null){skill=new Skill();skill.id="skill-"+Integer.toHexString((action.context+action.control.id+screenHash(action.before)).hashCode());skill.context=action.context;skill.kind=action.control.kind;skill.label=action.control.label;skill.before=action.before.clone();skill.bounds=action.control.bounds.clone();skills.put(skill.id,skill);}
  skill.attempts++;if(ok){skill.successes++;skill.destination=destination;}else skill.failures++;skill.rewardSum+=ok?.2:-.5;
  while(skills.size()>128)skills.remove(skills.keySet().iterator().next());
 }
 public synchronized String report(){int learned=0;for(Skill skill:skills.values())if(skill.acquired())learned++;return "BANC888：6集約ノード・16接続\n自律復帰 "+recoveries+"回 / 結果確認 "+verified+"回 / 未確認・失敗 "+failures+"回\n候補手順 "+skills.size()+"件 / 獲得手順 "+learned+"件\n"+(transferEnabled?knowHow.report(sourceGame)+"\n他ゲームのノウハウ利用候補 "+transferredProposals+"回":"ゲーム間のノウハウ共有はOFF")+"\n"+status;}
}
