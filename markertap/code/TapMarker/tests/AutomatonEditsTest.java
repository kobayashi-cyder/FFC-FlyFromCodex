package jp.tapmarker;
import java.util.*;
public final class AutomatonEditsTest {
 static int checks;
 static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 static void rejects(Runnable task){try{task.run();throw new AssertionError("should reject");}catch(IllegalArgumentException expected){checks++;}}
 public static void main(String[] args){
  PhaseEngine e=new PhaseEngine();e.windowRelative=true;
  PhaseEngine.Region r=new PhaseEngine.Region();r.id="button";r.name="閉じる";r.bounds=new float[]{.1f,.2f,.3f,.4f};e.regions.put(r.id,r);
  PhaseEngine.Phase p=new PhaseEngine.Phase();p.id="reward";p.name="報酬";p.markersDuring=true;p.graphX=10;p.graphY=50;e.phases.put(p.id,p);e.initial=e.current=p.id;
  PhaseEngine.Phase next=new PhaseEngine.Phase();next.id="main";next.name="主画面";e.phases.put(next.id,next);
  PhaseEngine.Watch w=new PhaseEngine.Watch();w.id="close";w.name="閉じるを押す";w.regionId=r.id;w.condition=PhaseEngine.MATCH;w.action=PhaseEngine.CENTER;w.priority=-15;w.waitMs=400;w.timeoutMs=9000;w.stableMs=1300;w.threshold=.0015f;w.duration=63;w.nextPhase=p.id;w.historyRule="close";w.historyPhase=p.id;w.pairedReference=true;w.fallback=true;
  w.reference=new float[1536];w.reference[1]=.7f;w.guardIds=new String[]{r.id};w.guardReferences=new float[][]{new float[768]};w.touchPoint=new float[]{.55f,.7f};w.markerIds=new String[]{"m1"};w.markerPoints=new float[]{.2f,.6f};w.curve=new float[]{.1f,.2f,.3f,.4f};w.relativeCurve=true;p.watches.add(w);
  PhaseEngine.Watch external=new PhaseEngine.Watch();external.id="external";external.regionId=r.id;external.nextPhase=next.id;external.condition=PhaseEngine.TIMEOUT;external.action=PhaseEngine.WAIT;p.watches.add(external);
  PhaseEngine.Watch draft=AutomatonEdits.draft(w);
  check(draft!=w&&draft.id.equals(w.id),"existing rule id remains stable");
  check(draft.priority==-15&&draft.waitMs==400&&draft.timeoutMs==9000&&draft.stableMs==1300&&draft.duration==63,"all numeric fields survive a draft");
  check(draft.historyRule.equals(w.historyRule)&&draft.historyPhase.equals(w.historyPhase)&&draft.pairedReference&&draft.fallback,"hidden history and pairing fields survive");
  draft.reference[1]=0;draft.touchPoint[0]=.8f;draft.guardReferences[0][0]=1;draft.curve[0]=.9f;
  check(w.reference[1]==.7f&&w.touchPoint[0]==.55f&&w.guardReferences[0][0]==0&&w.curve[0]==.1f,"canceling a draft cannot change saved arrays");
  check(p.watches.get(0)==w,"opening or canceling never replaces the saved rule");
  PhaseEngine.Watch copy=AutomatonEdits.duplicateWatch(w);
  check(!copy.id.equals(w.id)&&copy.name.endsWith("コピー"),"rule duplication creates a new identity");
  check(copy.regionId.equals(w.regionId)&&copy.priority==w.priority&&Arrays.equals(copy.reference,w.reference),"duplication preserves predicate and fields");
  check(e.rule(copy.id)==null&&p.watches.size()==2,"duplicate is a draft until saved");
  AutomatonGraph.replaceWatch(e,p,null,copy);check(e.rule(copy.id)==copy&&p.watches.get(0)==w,"saving a duplicate retains the original");
  p.watches.remove(copy);
  PhaseEngine.Phase duplicated=AutomatonEdits.duplicatePhase(e,p,"報酬 F52A");
  check(!duplicated.id.equals(p.id)&&e.phases.get(p.id)==p,"state duplication retains the original");
  check(e.initial.equals(p.id)&&e.current.equals(p.id),"duplicating never silently changes the start");
  check(duplicated.markersDuring&&duplicated.graphX==60&&duplicated.graphY==140,"state flags and drawing position are preserved");
  PhaseEngine.Watch dc=duplicated.watches.get(0);
  check(!dc.id.equals(w.id)&&dc.nextPhase.equals(duplicated.id),"explicit self-transition targets the duplicate");
  check(dc.historyRule.equals(dc.id)&&dc.historyPhase.equals(duplicated.id),"internal history references map to new identities");
  check(duplicated.watches.get(1).nextPhase.equals(next.id),"external target is preserved");
  check(dc.regionId.equals(r.id)&&dc.reference!=w.reference,"regions remain shared while image arrays are independent");
  check(AutomatonGraph.relationships(e).isEmpty(),"duplicated graph relationships are valid");
  check(AutomatonEdits.searchText(e,p,w).contains("報酬")&&AutomatonEdits.searchText(e,p,w).contains("閉じる"),"search includes state and region");
  rejects(()->AutomatonEdits.duplicatePhase(e,p,"報酬"));rejects(()->AutomatonEdits.duplicatePhase(e,p," "));
  PhaseEngine other=new PhaseEngine();rejects(()->AutomatonEdits.duplicatePhase(other,p,"他アプリ"));
  check(AutomatonEdits.draft(new PhaseEngine.Watch()).id!=null,"new drafts receive ids before saving");
  // Saving an unchanged form preserves every authored field, including absolute curves.
  PhaseEngine baseline=e.runtimeCopy();PhaseEngine.Watch unchanged=AutomatonEdits.draft(w);AutomatonGraph.replaceWatch(e,p,w,unchanged);
  check(AutomatonGraph.sameConfiguration(e,baseline),"round trip does not lose authored configuration");
  PhaseEngine.Watch special=new PhaseEngine.Watch();special.id=UsagiPreset.PREFIX+"ミッション中央をタップ";
  check(!AutomatonEdits.canDuplicate(special),"identity-sensitive preset gestures are not offered as generic clones");
  rejects(()->AutomatonEdits.duplicateWatch(special));
  PhaseEngine.Phase standard=new PhaseEngine.Phase();standard.id=FarmPreset.P+"main";standard.name="標準";e.phases.put(standard.id,standard);
  check(!AutomatonEdits.canDuplicate(standard),"identity-sensitive preset state is not offered as generic clone");
  rejects(()->AutomatonEdits.duplicatePhase(e,standard,"標準 コピー"));
  System.out.println("AutomatonEditsTest: "+checks+" checks passed");
 }
}
