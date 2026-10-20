package jp.tapmarker;

import java.util.*;

/** Drafts are independent of saved objects; graph references retain their meaning. */
public final class AutomatonEdits {
 private AutomatonEdits(){}
 public static boolean canDuplicate(PhaseEngine.Watch w){return w.id==null||!AutomatonNormalizer.preset(w.id);}
 public static boolean canDuplicate(PhaseEngine.Phase p){if(AutomatonNormalizer.preset(p.id))return false;for(PhaseEngine.Watch w:p.watches)if(!canDuplicate(w))return false;return true;}
 public static PhaseEngine.Watch draft(PhaseEngine.Watch source){PhaseEngine.Watch w=PhaseEngine.copyWatch(source);if(w.id==null||w.id.isEmpty())w.id=UUID.randomUUID().toString();return w;}
 public static PhaseEngine.Watch duplicateWatch(PhaseEngine.Watch source){if(!canDuplicate(source))throw new IllegalArgumentException("専用制御を持つ標準遷移は複製できません。実画面で操作を学習した設定を複製してください");PhaseEngine.Watch w=draft(source);w.id=UUID.randomUUID().toString();w.name=(source.name.isEmpty()?"遷移":source.name)+" コピー";return w;}
 public static PhaseEngine.Phase duplicatePhase(PhaseEngine e,PhaseEngine.Phase source,String name){
  AutomatonGraph.requirePhase(e,source);if(!canDuplicate(source))throw new IllegalArgumentException("専用制御を持つ標準状態は複製できません。実画面で操作を学習した設定を複製してください");name=name.trim();if(name.isEmpty())throw new IllegalArgumentException("状態名を入力してください");for(PhaseEngine.Phase p:e.phases.values())if(p.name.equals(name))throw new IllegalArgumentException("同じ状態名があります");
  PhaseEngine.Phase copy=new PhaseEngine.Phase();copy.id=UUID.randomUUID().toString();copy.name=name;copy.markersDuring=source.markersDuring;copy.learnedDuring=source.learnedDuring;copy.terminal=source.terminal;copy.graphX=source.graphX<0?-1:source.graphX+50;copy.graphY=source.graphY<0?-1:source.graphY+90;
  Map<String,String> ids=new HashMap<>();for(PhaseEngine.Watch w:source.watches)ids.put(w.id,UUID.randomUUID().toString());
  for(PhaseEngine.Watch w:source.watches){PhaseEngine.Watch a=draft(w);a.id=ids.get(w.id);if(a.nextPhase.equals(source.id))a.nextPhase=copy.id;if(a.historyPhase.equals(source.id))a.historyPhase=copy.id;if(ids.containsKey(a.historyRule))a.historyRule=ids.get(a.historyRule);copy.watches.add(a);}
  e.phases.put(copy.id,copy);e.clearHistory();return copy;
 }
 public static String searchText(PhaseEngine e,PhaseEngine.Phase p,PhaseEngine.Watch w){PhaseEngine.Region r=e.regions.get(w.regionId);PhaseEngine.Phase next=e.phases.get(w.nextPhase);return (p.name+" "+w.name+" "+(r==null?"":r.name)+" "+(next==null?"":next.name)).toLowerCase(Locale.ROOT);}
}
