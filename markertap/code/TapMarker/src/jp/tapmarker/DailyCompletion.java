package jp.tapmarker;
import java.util.*;

/** Explicit all-daily-done evidence plus its independent screen heading. */
final class DailyCompletion {
 static final String REGION=":daily-done",HEADING=":daily-heading",CONTINUE=":daily-continue",RULE=":daily-complete",PHASE=":daily-finished";
 final float[] doneBounds,headingBounds,done,heading;
 DailyCompletion(float[] db,float[] hb,float[] d,float[] h){if(db==null||hb==null||!AdaptiveLayout.inside(db)||!AdaptiveLayout.inside(hb)||d==null||h==null||d.length!=768||h.length!=768)throw new IllegalArgumentException("完了条件の2領域を記録してください");if(!informative(d)||!informative(h))throw new IllegalArgumentException("空欄ではなく、完了の文字・印と見出しを囲んでください");float overlap=Math.max(0,Math.min(db[2],hb[2])-Math.max(db[0],hb[0]))*Math.max(0,Math.min(db[3],hb[3])-Math.max(db[1],hb[1]));if(overlap>Math.min((db[2]-db[0])*(db[3]-db[1]),(hb[2]-hb[0])*(hb[3]-hb[1]))*.2f)throw new IllegalArgumentException("完了表示と見出しを別々の範囲で囲んでください");doneBounds=db.clone();headingBounds=hb.clone();done=d.clone();heading=h.clone();}
 static boolean informative(float[] pixels){float min=1,max=0;for(float f:pixels){if(!Float.isFinite(f)||f<0||f>1)return false;min=Math.min(min,f);max=Math.max(max,f);}return max-min>=.18f;}
 static void sample(PhaseEngine e,Map<String,float[]> output,int width,int height,RegionSampler.Pixels pixels,int[] window){if(!e.regions.containsKey(REGION))return;ContentViewport v=ContentViewport.locate(width,height,pixels,window);for(String id:new String[]{REGION,HEADING})output.put(id,RegionSampler.sample(v.width,v.height,e.regions.get(id).bounds,(x,y)->pixels.get(x+v.left,y+v.top)));continuation(e,output);}
 // A completed screen can still contain Play/Get buttons. Hold their rules while
 // the two completion landmarks are being confirmed, including timer fallbacks.
 static void continuation(PhaseEngine e,Map<String,float[]> samples){PhaseEngine.Watch done=e.rule(RULE+":"+e.current);float[] allowed=new float[768];boolean complete=done!=null&&done.distance(samples.get(REGION))<=done.threshold&&e.guardsMatch(done,samples);if(!complete)Arrays.fill(allowed,1);samples.put(CONTINUE,allowed);}
 void install(PhaseEngine e){
  PhaseEngine.Region r=new PhaseEngine.Region();r.id=REGION;r.name="デイリー全達成・受取済みの表示";r.bounds=doneBounds.clone();e.regions.put(r.id,r);
  PhaseEngine.Region guard=new PhaseEngine.Region();guard.id=HEADING;guard.name="デイリーミッションの見出し";guard.bounds=headingBounds.clone();e.regions.put(guard.id,guard);
  PhaseEngine.Region available=new PhaseEngine.Region();available.id=CONTINUE;available.name="全達成の確認中は次のプレイを保留";available.bounds=doneBounds.clone();e.regions.put(available.id,available);float[] allowed=new float[768];Arrays.fill(allowed,1);
  PhaseEngine.Phase finished=new PhaseEngine.Phase();finished.id=PHASE;finished.name="デイリー完了";finished.terminal=true;e.phases.put(finished.id,finished);
  for(PhaseEngine.Phase state:e.phases.values())if(!state.terminal){for(PhaseEngine.Watch existing:state.watches){int count=existing.guardIds.length;existing.guardIds=Arrays.copyOf(existing.guardIds,count+1);existing.guardReferences=Arrays.copyOf(existing.guardReferences,count+1);existing.guardIds[count]=CONTINUE;existing.guardReferences[count]=allowed;}PhaseEngine.Watch w=new PhaseEngine.Watch();w.id=RULE+":"+state.id;w.name="全達成の表示を確認して次のアプリへ";w.condition=PhaseEngine.MATCH;w.action=PhaseEngine.WAIT;w.regionId=REGION;w.reference=done.clone();w.guardIds=new String[]{HEADING};w.guardReferences=new float[][]{heading.clone()};w.threshold=.001f;w.waitMs=0;w.nextPhase=PHASE;state.watches.add(w);}
 }
}
