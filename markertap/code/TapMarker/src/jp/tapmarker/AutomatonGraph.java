package jp.tapmarker;
import java.util.*;

/** Graph edits are scoped to the exact model and keep stable rule identities. */
public final class AutomatonGraph {
 private AutomatonGraph(){}
 static String title(PhaseEngine.Phase p,PhaseEngine.Watch w){return p.name+" / "+(w.name.isEmpty()?w.id:w.name)+"：";}
 public static String relationships(PhaseEngine e){
  Set<String> ids=new HashSet<>();
  for(Map.Entry<String,PhaseEngine.Phase> item:e.phases.entrySet()){
   PhaseEngine.Phase p=item.getValue();if(p.id==null||!item.getKey().equals(p.id))return "状態の識別子が一致していません";
   for(PhaseEngine.Watch w:p.watches){String error=watchError(e,p,w);if(!error.isEmpty())return error;if(!ids.add(w.id))return title(p,w)+"矢印の識別子が重複しています";}
  }
  if(!e.initial.isEmpty()&&!e.phases.containsKey(e.initial))return "開始状態がありません";
  if(!e.current.isEmpty()&&!e.phases.containsKey(e.current))return "現在状態がありません";
  return "";
 }
 static String watchError(PhaseEngine e,PhaseEngine.Phase p,PhaseEngine.Watch w){
  String head=title(p,w);if(w.id==null||w.id.isEmpty())return head+"矢印の識別子がありません";
  if(w.condition<PhaseEngine.CHANGE||w.condition>PhaseEngine.DISAPPEAR||w.action<PhaseEngine.CENTER||w.action>PhaseEngine.RETURN_APP)return head+"条件または操作が無効です";
  if(!w.nextPhase.isEmpty()&&!e.phases.containsKey(w.nextPhase))return head+"遷移先がありません（"+w.nextPhase+"）";
  if((w.condition!=PhaseEngine.TIMEOUT||w.action==PhaseEngine.CENTER)&&!e.regions.containsKey(w.regionId))return head+"監視・タップ領域がありません（"+w.regionId+"）";
  if(w.guardIds.length!=w.guardReferences.length)return head+"補助領域と見本画像の対応数が違います";
  for(String id:w.guardIds)if(!e.regions.containsKey(id))return head+"補助領域がありません（"+id+"）";
  return "";
 }
 public static void requirePhase(PhaseEngine e,PhaseEngine.Phase p){if(p==null||e.phases.get(p.id)!=p)throw new IllegalArgumentException("編集中のアプリ・状態が変わりました。対象アプリで開き直してください");}
 public static void requireRegion(PhaseEngine e,PhaseEngine.Region r){if(r==null||e.regions.get(r.id)!=r)throw new IllegalArgumentException("編集中のアプリ・領域が変わりました。対象アプリで開き直してください");}
 public static void replaceWatch(PhaseEngine e,PhaseEngine.Phase p,PhaseEngine.Watch old,PhaseEngine.Watch value){
  requirePhase(e,p);int index=old==null?-1:p.watches.indexOf(old);if(old!=null&&index<0)throw new IllegalArgumentException("この矢印は更新・削除されています。開き直してください");
  if(old!=null&&!old.id.equals(value.id))throw new IllegalArgumentException("矢印の識別子を変更できません");
  String error=watchError(e,p,value);if(!error.isEmpty())throw new IllegalArgumentException(error);
  for(PhaseEngine.Phase phase:e.phases.values())for(PhaseEngine.Watch w:phase.watches)if(w!=old&&w.id.equals(value.id))throw new IllegalArgumentException("矢印の識別子が重複しています");
  if(index<0)p.watches.add(value);else p.watches.set(index,value);e.clearHistory();
 }
 public static void removeWatch(PhaseEngine e,PhaseEngine.Phase p,PhaseEngine.Watch w){requirePhase(e,p);if(!p.watches.remove(w))throw new IllegalArgumentException("この矢印は更新・削除されています");e.clearHistory();}
 public static void removePhase(PhaseEngine e,PhaseEngine.Phase p){
  requirePhase(e,p);for(PhaseEngine.Phase source:e.phases.values())if(source!=p)for(PhaseEngine.Watch w:source.watches)if(w.nextPhase.equals(p.id))throw new IllegalArgumentException(title(source,w)+"先にこの状態への矢印を変更してください");
  e.phases.remove(p.id);if(e.initial.equals(p.id)){e.initial="";e.current="";}else if(e.current.equals(p.id))e.current=e.phases.containsKey(e.initial)?e.initial:"";e.clearHistory();
 }
 static String target(PhaseEngine.Phase source,PhaseEngine.Watch w){return w.nextPhase.isEmpty()?source.id:w.nextPhase;}
 /** Several observations of the same effect form an OR, not competing decisions. */
 public static boolean sameEffect(PhaseEngine e,PhaseEngine.Phase p,PhaseEngine.Watch a,PhaseEngine.Watch b){
  if(a.action!=b.action||!target(p,a).equals(target(p,b))||a.waitMs!=b.waitMs)return false;
  switch(a.action){
   case PhaseEngine.CENTER:
    if(a.duration!=b.duration)return false;
    if(a.touchPoint.length==2||b.touchPoint.length==2)return (e.windowRelative||RecordedRepairSampling.local(e,a)&&RecordedRepairSampling.local(e,b))&&Arrays.equals(a.touchPoint,b.touchPoint);
    // Built-in gestures can have a separate target keyed by rule id.
    if(a.id.startsWith(UsagiPreset.PREFIX)||b.id.startsWith(UsagiPreset.PREFIX))return a.id.equals(b.id);
    return a.regionId.equals(b.regionId);
   case PhaseEngine.MARKERS:return a.duration==b.duration&&Arrays.equals(a.markerIds,b.markerIds)&&Arrays.equals(a.markerPoints,b.markerPoints);
   case PhaseEngine.CURVE:return a.duration==b.duration&&a.relativeCurve==b.relativeCurve&&Arrays.equals(a.curve,b.curve);
   default:return true;
  }
 }
 /** These nodes are entered by the safety/foreground controller, outside graph arrows. */
 public static String detachedRole(PhaseEngine.Phase p){
  if(p.id.equals(UsagiPreset.PREFIX+"error")||p.id.equals(UsagiPreset.PREFIX+"account"))return "保護表示の検出で入る状態";
  if(p.id.equals(CoinPreset.P+"reward-ad"))return "任意CMの開始経路廃止後に残す、旧設定と広告復帰の互換状態";
  if(p.id.equals(CoinPreset.P+"external")||p.id.equals(ShoppingPreset.P+"external"))return "外部アプリからの復帰で入る状態";
  if(p.id.equals(UsagiPreset.PREFIX+"unknown")||p.id.equals(FarmPreset.P+"unknown")){if(p.watches.size()==1&&p.watches.get(0).action==PhaseEngine.STOP&&p.watches.get(0).condition==PhaseEngine.TIMEOUT)return "停止用に保持する状態";}
  return "";
 }
 public static String report(PhaseEngine e){
  String error=relationships(e);if(!error.isEmpty())return "関係の修正が必要です\n"+error;
  if(e.phases.isEmpty())return "状態がまだありません";if(!e.phases.containsKey(e.initial))return "開始状態を選んでください。状態を勝手に開始状態へ置き換えることはありません。";
  Set<String> reached=new HashSet<>();ArrayDeque<String> todo=new ArrayDeque<>();todo.add(e.initial);
  while(!todo.isEmpty()){String id=todo.remove();if(!reached.add(id))continue;PhaseEngine.Phase p=e.phases.get(id);if(p.terminal)continue;for(PhaseEngine.Watch w:p.watches)if(w.action!=PhaseEngine.STOP&&!w.nextPhase.isEmpty())todo.add(w.nextPhase);}
  int arrows=0;StringBuilder out=new StringBuilder();for(PhaseEngine.Phase p:e.phases.values())arrows+=p.watches.size();out.append("状態・矢印・領域の参照は一致しています\n").append(e.phases.size()).append("状態 / ").append(arrows).append("矢印 / ").append(reached.size()).append("到達可能な状態");
  for(PhaseEngine.Phase p:e.phases.values())if(!reached.contains(p.id)){String role=detachedRole(p);out.append("\n").append(role.isEmpty()?"開始状態から未接続":role).append("：").append(p.name);}
  return out.toString();
 }
 /** Recognize untouched older presets without treating an edited graph as an upgrade template. */
 public static boolean sameConfiguration(PhaseEngine a,PhaseEngine b){
  if(a.windowRelative!=b.windowRelative||!a.initial.equals(b.initial)||!a.regions.keySet().equals(b.regions.keySet())||!a.phases.keySet().equals(b.phases.keySet()))return false;
  for(PhaseEngine.Region r:a.regions.values()){PhaseEngine.Region other=b.regions.get(r.id);if(r.windowLocal!=other.windowLocal||!Objects.equals(r.name,other.name)||!Arrays.equals(r.bounds,other.bounds))return false;}
  for(PhaseEngine.Phase p:a.phases.values()){
   PhaseEngine.Phase other=b.phases.get(p.id);if(!Objects.equals(p.name,other.name)||p.markersDuring!=other.markersDuring||p.terminal!=other.terminal||p.watches.size()!=other.watches.size())return false;
   Map<String,PhaseEngine.Watch> rules=new HashMap<>();for(PhaseEngine.Watch w:other.watches)rules.put(w.id,w);
   for(PhaseEngine.Watch w:p.watches){PhaseEngine.Watch v=rules.get(w.id);if(v==null||!w.name.equals(v.name)||!w.regionId.equals(v.regionId)||!w.nextPhase.equals(v.nextPhase)||!w.historyRule.equals(v.historyRule)||!w.historyPhase.equals(v.historyPhase)||w.condition!=v.condition||w.action!=v.action||w.priority!=v.priority||w.waitMs!=v.waitMs||w.stableMs!=v.stableMs||w.timeoutMs!=v.timeoutMs||w.duration!=v.duration||w.fallback!=v.fallback||w.pairedReference!=v.pairedReference||w.threshold!=v.threshold||w.relativeCurve!=v.relativeCurve||!Arrays.equals(w.reference,v.reference)||!Arrays.equals(w.curve,v.curve)||!Arrays.equals(w.touchPoint,v.touchPoint)||!Arrays.equals(w.markerPoints,v.markerPoints)||!Arrays.equals(w.markerIds,v.markerIds)||!Arrays.equals(w.guardIds,v.guardIds)||!Arrays.deepEquals(w.guardReferences,v.guardReferences))return false;}
  }return true;
 }
}
