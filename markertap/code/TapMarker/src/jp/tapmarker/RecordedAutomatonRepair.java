package jp.tapmarker;
import java.util.*;import java.security.MessageDigest;

/** Compare a recording, preserve prior graph, and compile reviewable incremental repairs. */
public final class RecordedAutomatonRepair {
 public static final class Issue {
  public int state;public long at;public String kind,detail;
  Issue(int state,long at,String kind,String detail){this.state=state;this.at=at;this.kind=kind;this.detail=detail;}
 }
 public static final class Audit {
  public final List<Issue> issues=new ArrayList<>();public final Map<Integer,String> mapped=new LinkedHashMap<>();public int recognized,unknown,ambiguous,inherited;
  public String report(){StringBuilder s=new StringBuilder("既存設定との比較：一致 "+recognized+"状態 / 未登録・条件不一致 "+unknown+"状態 / 曖昧 "+ambiguous+"状態\n");for(Issue issue:issues)s.append(String.format(Locale.JAPAN,"%.1f秒 / 録画状態%d：%s — %s\n",issue.at/1000d,issue.state+1,issue.kind,issue.detail));if(issues.isEmpty())s.append("この録画の範囲で新しい修正箇所は見つかりませんでした。\n");return s.toString();}
 }
 public static final class Result {
  public PhaseEngine engine;public Audit before,after;public int addedStates,reusedStates,changedRules,pending;public String recordingStart="",explanation="";
 }
 static Map<String,float[]> samples(PhaseEngine e,ObservationLearner.Frame f){Map<String,float[]> out=new HashMap<>();for(PhaseEngine.Region r:e.regions.values())if((e.windowRelative||r.windowLocal)&&BancAutonomy.validBounds(r.bounds)&&ObservationLearner.usable(f,r.bounds))out.put(r.id,f.sample(r.bounds));return out;}
 static Set<String> matches(PhaseEngine e,RecordedAutomatonGenerator.State state){
  Map<String,float[]> a=samples(e,state.frame),b=samples(e,state.peer);Set<String> matches=new LinkedHashSet<>();
  for(PhaseEngine.Phase p:e.phases.values())for(PhaseEngine.Watch w:p.watches)if(BancAutonomy.strongMatch(e,w,a)&&BancAutonomy.strongMatch(e,w,b)){
   String target=w.action==PhaseEngine.WAIT&&!w.nextPhase.isEmpty()?w.nextPhase:p.id;if(e.phases.containsKey(target))matches.add(target);
  }return matches;
 }
 static boolean reaches(PhaseEngine e,String from,String to){
  if(from.equals(to))return true;PhaseEngine.Phase p=e.phases.get(from);if(p==null)return false;for(PhaseEngine.Watch w:p.watches){if(w.nextPhase.equals(to))return true;PhaseEngine.Phase next=e.phases.get(w.nextPhase);if(next!=null)for(PhaseEngine.Watch confirmation:next.watches)if(confirmation.action==PhaseEngine.WAIT&&confirmation.condition==PhaseEngine.MATCH&&confirmation.nextPhase.equals(to))return true;}return false;
 }
 public static Audit analyze(PhaseEngine e,RecordedAutomatonGenerator.Model model){
  if(e==null||model==null)throw new IllegalArgumentException("既存設定と録画が必要です");Audit audit=new Audit();
  for(RecordedAutomatonGenerator.State state:model.states){Set<String> candidates=matches(e,state);if(candidates.size()==1){audit.mapped.put(state.index,candidates.iterator().next());audit.recognized++;}else if(candidates.isEmpty()){audit.unknown++;audit.issues.add(new Issue(state.index,state.firstMs,"表示条件の修正候補","登録状態が見つからない、または録画の範囲・見本画像が一致しない"));}else{audit.ambiguous++;audit.issues.add(new Issue(state.index,state.firstMs,"状態の区別が必要",String.join("、",candidates)));}
   if(state.visits==1&&state.lastMs-state.firstMs>=5000)audit.issues.add(new Issue(state.index,state.firstMs,"長時間同じ表示","操作結果・待機時間を確認。録画だけではタップ失敗と断定しません"));
  }
  for(RecordedAutomatonGenerator.Edge edge:model.edges){String from=audit.mapped.get(edge.from),to=audit.mapped.get(edge.to);if(from!=null&&to!=null&&!reaches(e,from,to))audit.issues.add(new Issue(edge.from,edge.firstMs,"分岐の追加候補",from+" → "+to+" が既存の操作・結果確認に見つからない"));}
  for(RecordedAutomatonGenerator.State state:model.states){String mapped=audit.mapped.get(state.index);if(mapped==null||state.action==RecordedAutomatonGenerator.UNREVIEWED||state.action==PhaseEngine.WAIT)continue;PhaseEngine.Phase p=e.phases.get(mapped);Map<String,float[]> observed=samples(e,state.frame);boolean matching=false;for(PhaseEngine.Watch w:p.watches)if(BancAutonomy.strongMatch(e,w,observed)&&sameInput(e,w,state))matching=true;if(!matching)audit.issues.add(new Issue(state.index,state.firstMs,"操作の修正候補","確認した操作種類・位置が既存の操作に一致しない"));}
  return audit;
 }
 static boolean sameInput(PhaseEngine e,PhaseEngine.Watch w,RecordedAutomatonGenerator.State state){
  if(w.action!=state.action)return false;if(w.action==PhaseEngine.CENTER)return w.touchPoint.length==2&&distance(w.touchPoint,state.path)<.0004f;
  return w.action==PhaseEngine.CURVE&&w.relativeCurve&&w.curve.length==state.path.length&&distance(w.curve,state.path)<.0004f;
 }
 static float distance(float[] a,float[] b){return a.length==b.length?FlyCircuit.distance(a,b):Float.POSITIVE_INFINITY;}
 static boolean sameContract(PhaseEngine.Watch a,PhaseEngine.Watch b){return a.id.equals(b.id)&&a.regionId.equals(b.regionId)&&a.nextPhase.equals(b.nextPhase)&&a.historyRule.equals(b.historyRule)&&a.historyPhase.equals(b.historyPhase)&&a.condition==b.condition&&a.action==b.action&&a.threshold==b.threshold&&a.duration==b.duration&&a.waitMs==b.waitMs&&a.stableMs==b.stableMs&&a.timeoutMs==b.timeoutMs&&a.relativeCurve==b.relativeCurve&&Arrays.equals(a.touchPoint,b.touchPoint)&&Arrays.equals(a.curve,b.curve)&&Arrays.equals(a.reference,b.reference)&&Arrays.equals(a.guardIds,b.guardIds)&&Arrays.deepEquals(a.guardReferences,b.guardReferences);}
 /** Reuse only existing reviewed, unambiguous window-local operations with observed destinations. */
 public static int inheritReviewed(PhaseEngine base,RecordedAutomatonGenerator.Model model){
  if(base.recordingReviewRequired)return 0;Audit audit=analyze(base,model);int inherited=0;
  for(RecordedAutomatonGenerator.State state:model.states){if(state.action!=RecordedAutomatonGenerator.UNREVIEWED||model.destinations(state.index).length==0)continue;String source=audit.mapped.get(state.index);if(source==null)continue;PhaseEngine.Phase p=base.phases.get(source);Map<String,float[]> a=samples(base,state.frame),b=samples(base,state.peer);PhaseEngine.Watch selected=null;boolean ambiguous=false;
   for(PhaseEngine.Watch w:p.watches){PhaseEngine.Region region=base.regions.get(w.regionId);if(region==null||!base.windowRelative&&!region.windowLocal||!BancAutonomy.strongMatch(base,w,a)||!BancAutonomy.strongMatch(base,w,b)||w.action!=PhaseEngine.CENTER&&w.action!=PhaseEngine.CURVE)continue;if(w.action==PhaseEngine.CENTER&&w.touchPoint.length!=2||w.action==PhaseEngine.CURVE&&!w.relativeCurve)continue;if(selected!=null&&!AutomatonGraph.sameEffect(base,p,selected,w))ambiguous=true;else selected=w;}
   if(selected==null||ambiguous)continue;int destination=-1;for(int next:model.destinations(state.index)){String known=audit.mapped.get(next);if(known!=null&&reaches(base,selected.nextPhase,known)){if(destination>=0){destination=-1;break;}destination=next;}}
   if(destination<0)continue;model.annotate(state.index,selected.action,selected.action==PhaseEngine.CENTER?selected.touchPoint:selected.curve,Math.max(1,Math.min(10000,selected.duration)),destination);inherited++;
  }return inherited;
 }
 static String prefix(RecordedAutomatonGenerator.Model model){
  try{MessageDigest digest=MessageDigest.getInstance("SHA-256");for(RecordedAutomatonGenerator.State s:model.states){BancGraphIdentity.put(digest,s.index+"|"+s.action+"|"+s.destination+"|"+s.duration);BancGraphIdentity.vector(digest,s.path);for(int c:s.frame.pixels){digest.update((byte)(c>>>16));digest.update((byte)(c>>>8));digest.update((byte)c);}}for(RecordedAutomatonGenerator.Edge edge:model.edges)BancGraphIdentity.put(digest,edge.from+"|"+edge.to);StringBuilder hash=new StringBuilder();for(byte v:digest.digest())hash.append(String.format(Locale.ROOT,"%02x",v&255));return "repair:"+hash.substring(0,20)+":";}catch(Exception failure){throw new IllegalStateException(failure);}
 }
 public static Result compile(PhaseEngine base,RecordedAutomatonGenerator.Model model,boolean startAtRecording){
  if(base.phases.isEmpty())throw new IllegalArgumentException("改修する既存オートマトンがありません。先に新規生成してください");
  Result result=new Result();result.before=analyze(base,model);if(model.states.size()<2||model.edges.isEmpty()){result.engine=base.runtimeCopy();result.after=analyze(base,model);result.explanation=result.before.report()+"\n操作前後の遷移が録画にありません。結果のない操作を生成せず、既存設定を保持します。操作後まで含む次の録画で改修を続けられます。";return result;}RecordedAutomatonGenerator.Result generated=RecordedAutomatonGenerator.compile(model);result.pending=generated.pending;PhaseEngine out=base.runtimeCopy();out.strictRuntime=true;String prefix=prefix(model);Map<String,String> phases=new HashMap<>(),regions=new HashMap<>();
  for(RecordedAutomatonGenerator.State state:model.states){String record=RecordedAutomatonGenerator.PREFIX+"state:"+state.index,known=result.before.mapped.get(state.index);phases.put(record,known==null?prefix+record:known);if(known!=null)result.reusedStates++;}
  for(PhaseEngine.Phase p:generated.engine.phases.values())if(!phases.containsKey(p.id))phases.put(p.id,prefix+p.id);
  for(PhaseEngine.Region r:generated.engine.regions.values()){String id=prefix+r.id;regions.put(r.id,id);if(!out.regions.containsKey(id)){PhaseEngine.Region copy=new PhaseEngine.Region();copy.id=id;copy.name="録画改修 / "+r.name;copy.bounds=r.bounds.clone();copy.windowLocal=!base.windowRelative;out.regions.put(id,copy);}}
  for(PhaseEngine.Phase p:generated.engine.phases.values()){String id=phases.get(p.id);if(out.phases.containsKey(id))continue;PhaseEngine.Phase copy=new PhaseEngine.Phase();copy.id=id;copy.name="録画改修 / "+p.name;copy.graphX=p.graphX;copy.graphY=p.graphY;copy.terminal=false;out.phases.put(id,copy);result.addedStates++;}
  for(PhaseEngine.Phase p:generated.engine.phases.values()){
   PhaseEngine.Phase target=out.phases.get(phases.get(p.id));
   for(PhaseEngine.Watch w:p.watches){PhaseEngine.Watch copy=PhaseEngine.copyWatch(w);copy.id=prefix+w.id;copy.name="録画改修 / "+w.name;copy.regionId=regions.get(w.regionId);for(int i=0;i<copy.guardIds.length;i++)copy.guardIds[i]=regions.get(copy.guardIds[i]);copy.nextPhase=w.nextPhase.isEmpty()?"":phases.get(w.nextPhase);
    if(target.watches.stream().anyMatch(existing->existing.id.equals(copy.id)))continue;
    // Replace a conflicting explicitly reviewed portable input, retaining its identity/history.
    boolean unchanged=false;if(w.action==PhaseEngine.CENTER||w.action==PhaseEngine.CURVE){int stateIndex=-1;for(RecordedAutomatonGenerator.State state:model.states)if((RecordedAutomatonGenerator.PREFIX+"state:"+state.index).equals(p.id))stateIndex=state.index;
     if(stateIndex>=0&&result.before.mapped.containsKey(stateIndex)){RecordedAutomatonGenerator.State state=model.states.get(stateIndex);Map<String,float[]> observed=samples(base,state.frame);PhaseEngine.Phase original=base.phases.get(result.before.mapped.get(stateIndex));for(PhaseEngine.Watch old:original.watches){PhaseEngine.Region r=base.regions.get(old.regionId);if(r!=null&&(base.windowRelative||r.windowLocal)&&BancAutonomy.strongMatch(base,old,observed)&&old.action!=PhaseEngine.STOP){copy.id=old.id;copy.historyRule=old.historyRule;copy.historyPhase=old.historyPhase;if(sameContract(old,copy)){unchanged=true;break;}target.watches.removeIf(existing->existing.id.equals(old.id));break;}}}
    }
    if(unchanged)continue;target.watches.add(copy);result.changedRules++;
    // A reviewed input can have another recorded outcome; verify it after sending input too.
    if(w.action==PhaseEngine.WAIT&&w.id.startsWith(RecordedAutomatonGenerator.PREFIX+"edge:"))for(PhaseEngine.Watch operation:p.watches)if(operation.action==PhaseEngine.CENTER||operation.action==PhaseEngine.CURVE){PhaseEngine.Phase verification=out.phases.get(phases.get(operation.nextPhase));if(verification!=null){PhaseEngine.Watch alternative=PhaseEngine.copyWatch(copy);alternative.id=copy.id+":after-input";alternative.name="録画で観察した別の結果を確認";if(verification.watches.stream().noneMatch(existing->existing.id.equals(alternative.id))){verification.watches.add(alternative);result.changedRules++;}}}
   }
   // The end of a recording means continued observation, never inferred daily completion.
   if(p.terminal&&target.watches.isEmpty()){PhaseEngine.Watch signature=null;for(PhaseEngine.Phase source:generated.engine.phases.values())for(PhaseEngine.Watch w:source.watches)if(w.nextPhase.equals(p.id)){signature=w;break;}
    if(signature!=null){PhaseEngine.Watch hold=PhaseEngine.copyWatch(signature);hold.id=prefix+"hold:"+p.id;hold.name="録画終端の表示を監視 / 完了は別に判定";hold.action=PhaseEngine.WAIT;hold.fallback=true;hold.nextPhase=target.id;hold.regionId=regions.get(signature.regionId);for(int i=0;i<hold.guardIds.length;i++)hold.guardIds[i]=regions.get(hold.guardIds[i]);target.watches.add(hold);result.changedRules++;}
   }
  }
  result.recordingStart=phases.get(generated.engine.initial);if(startAtRecording)out.initial=result.recordingStart;out.current=out.initial;out.recordingReviewRequired=base.recordingReviewRequired||result.pending>0;out.clearHistory();String error=out.validate();if(!error.isEmpty())throw new IllegalStateException("修正候補の検証："+error);ReferencePool.share(out);result.engine=out;result.after=analyze(out,model);
  result.explanation=result.before.report()+"\n修正候補：追加状態 "+result.addedStates+" / 既存状態の再利用 "+result.reusedStates+" / 追加・修正ルール "+result.changedRules+"\n未確認の操作 "+result.pending+"状態\n\n同じ録画で再検証：\n"+result.after.report()+"\n録画にない既存状態・分岐は保持します。録画終端は完了扱いにしません。入力位置が分からない箇所は操作確認が必要です。";return result;
 }
}
