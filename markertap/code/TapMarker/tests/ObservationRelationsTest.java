package jp.tapmarker;
import java.util.*;
/** Regression cases where the recorded relation used to be replaced or discarded. */
public final class ObservationRelationsTest {
 static int checks,failures;
 static void check(boolean value,String name){checks++;if(!value){failures++;System.out.println("FAIL "+name);}}
 static PhaseEngine.Watch observe(PhaseEngine e,ObservationLearner.Frame f,long now){Map<String,float[]> samples=ObservationLearnerTest.samples(e,f);e.observe(samples,now);e.observe(samples,now+100);return e.poll();}
 public static void main(String[] args){
  ObservationLearner.Frame a=ObservationLearnerTest.frame(0),b=ObservationLearnerTest.frame(1),c=ObservationLearnerTest.frame(2);
  ObservationLearner learner=new ObservationLearner();learner.add(ObservationLearnerTest.step(a,b,.3f,.25f));learner.add(ObservationLearnerTest.step(c,a,.7f,.75f));
  ObservationLearner.Result result=learner.compile(true);PhaseEngine e=result.engine;
  check(result.states==3,"the real intermediate result remains a visual state");
  PhaseEngine.Watch input=observe(e,a,0);e.finish(input,101);PhaseEngine.Watch after=observe(e,b,200);
  check(after!=null,"the operation confirms its own after frame, not the next before frame");
  if(after!=null){e.finish(after,301);check(observe(e,b,400)==null,"intermediate display does not send the next operation");PhaseEngine.Watch passive=observe(e,c,600);check(passive!=null&&passive.action==PhaseEngine.WAIT,"an observed input-free change connects the intermediate display");if(passive!=null){e.finish(passive,701);PhaseEngine.Watch next=observe(e,c,800);check(next!=null&&next.action==PhaseEngine.CENTER,"the next action belongs to its actual source display");}}
  learner=new ObservationLearner();learner.add(ObservationLearnerTest.step(a,b,.3f,.25f));learner.add(ObservationLearnerTest.step(b,a,.7f,.75f));learner.add(ObservationLearnerTest.step(a,b,.3f,.25f));
  boolean conflict=false;try{learner.compile(false);}catch(IllegalStateException ex){conflict=ex.getMessage().contains("停止")||ex.getMessage().contains("完了");}
  check(conflict,"identical repeated result cannot silently discard the final stop");
  e=new PhaseEngine();e.windowRelative=true;PhaseEngine.Region roi=new PhaseEngine.Region();roi.id="r";roi.name="region";roi.bounds=new float[]{.1f,.2f,.2f,.3f};e.regions.put(roi.id,roi);
  PhaseEngine.Phase p=new PhaseEngine.Phase();p.id="a";p.name="A";e.phases.put(p.id,p);e.initial=e.current=p.id;
  PhaseEngine.Watch w=new PhaseEngine.Watch();w.id="tap";w.condition=PhaseEngine.MATCH;w.action=PhaseEngine.CENTER;w.regionId="r";w.reference=new float[768];w.guardIds=new String[]{"r"};w.guardReferences=new float[][]{new float[768]};w.touchPoint=new float[]{.35f,.75f};w.stableMs=240;w.fallback=true;p.watches.add(w);
  PhaseEngine.Watch h=new PhaseEngine.Watch();h.id="legacy";h.condition=PhaseEngine.TIMEOUT;h.action=PhaseEngine.WAIT;h.historyRule="tap";p.watches.add(h);
  PhaseEngine converted=StateMigration.convert(e);PhaseEngine.Watch migrated=converted.phases.get("a").watches.get(0);
  check(converted.windowRelative,"state migration preserves the coordinate system");check(migrated.guardIds.length==1&&migrated.touchPoint.length==2,"state migration preserves the operation and all condition evidence");check(migrated.stableMs==240&&migrated.fallback,"state migration preserves condition timing and fallback semantics");
  check(migrated.reference!=w.reference,"state migration does not share mutable reference images");
  int[] flatA=a.pixels.clone(),flatB=a.pixels.clone();for(int y=a.height/3;y<a.height/2;y++)for(int x=0;x<a.width/4;x++){flatA[y*a.width+x]=0xffcc2200;flatB[y*a.width+x]=0xff00cc22;}
  ObservationLearner.Frame fa=new ObservationLearner.Frame(a.width,a.height,flatA),fb=new ObservationLearner.Frame(a.width,a.height,flatB);learner=new ObservationLearner();learner.add(ObservationLearnerTest.step(fa,fb,.3f,.25f));learner.add(ObservationLearnerTest.step(fb,fa,.7f,.75f));result=learner.compile(true);e=result.engine;input=observe(e,fa,0);e.finish(input,101);after=observe(e,fa,200);check(after==null,"shared texture cannot hide a flat-color state difference");after=observe(e,fb,400);check(after!=null,"a flat but distinctive monitored region confirms the real destination");
  if(failures>0)throw new AssertionError(failures+" of "+checks+" relationship checks failed");System.out.println("PASS "+checks+" observation relationship checks");
 }
}
