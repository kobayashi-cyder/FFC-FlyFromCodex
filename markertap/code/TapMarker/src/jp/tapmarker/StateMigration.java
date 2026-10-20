package jp.tapmarker;
import java.util.*;
/** Converts history guards into explicit reachable finite states without discarding branches. */
public final class StateMigration {
 static class Key {String state,last,origin;Key(String s,String l,String o){state=s;last=l;origin=o;}String id(){return state+"|"+last+"|"+origin;}}
 public static PhaseEngine convert(PhaseEngine old){boolean needed=false;for(PhaseEngine.Phase p:old.phases.values())for(PhaseEngine.Watch w:p.watches)needed|=!w.historyRule.isEmpty()||!w.historyPhase.isEmpty();if(!needed)return old;
  PhaseEngine out=new PhaseEngine();out.windowRelative=old.windowRelative;out.strictRuntime=old.strictRuntime;out.regions.putAll(old.runtimeCopy().regions);ArrayDeque<Key> queue=new ArrayDeque<>();Map<String,String> ids=new LinkedHashMap<>();
  for(PhaseEngine.Phase p:old.phases.values()){Key k=new Key(p.id,"","");ids.put(k.id(),p.id);queue.add(k);}out.initial=old.initial;out.current=old.current;
  while(!queue.isEmpty()){Key key=queue.remove();PhaseEngine.Phase base=old.phases.get(key.state);PhaseEngine.Phase p=new PhaseEngine.Phase();p.id=ids.get(key.id());p.name=base.name;p.markersDuring=base.markersDuring;p.learnedDuring=base.learnedDuring;p.terminal=base.terminal;p.graphX=base.graphX;p.graphY=base.graphY;if(!key.last.isEmpty()||!key.origin.isEmpty()){PhaseEngine.Watch last=old.rule(key.last);PhaseEngine.Phase origin=old.phases.get(key.origin);p.name+="［"+(last==null?"開始":last.name.isEmpty()?last.id.substring(0,Math.min(6,last.id.length())):last.name)+"後"+(origin==null?"":"・"+origin.name+"から")+"］";p.graphX=-1;p.graphY=-1;}
   for(PhaseEngine.Watch w:base.watches){if(!w.historyRule.isEmpty()&&!w.historyRule.equals(key.last)||!w.historyPhase.isEmpty()&&!w.historyPhase.equals(key.origin))continue;String destination=w.nextPhase.isEmpty()?key.state:w.nextPhase;if(!old.phases.containsKey(destination))throw new IllegalArgumentException("旧設定の遷移先がありません");Key next=new Key(destination,w.id,w.nextPhase.isEmpty()?key.origin:key.state);String target=ids.get(next.id());if(target==null){target=UUID.randomUUID().toString();ids.put(next.id(),target);queue.add(next);}PhaseEngine.Watch copy=PhaseEngine.copyWatch(w);copy.id=UUID.randomUUID().toString();copy.historyRule="";copy.historyPhase="";copy.nextPhase=next.id().equals(key.id())?"":target;p.watches.add(copy);}out.phases.put(p.id,p);
  }return out;
 }
}
