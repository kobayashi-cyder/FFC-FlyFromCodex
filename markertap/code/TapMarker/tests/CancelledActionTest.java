package jp.tapmarker;
import java.util.*;
public class CancelledActionTest {
 static int checks;
 static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
 static PhaseEngine engine(int condition){
  PhaseEngine e=new PhaseEngine();e.strictRuntime=true;
  PhaseEngine.Region r=new PhaseEngine.Region();r.id="r";r.name="button";r.bounds=new float[]{0,0,1,1};e.regions.put(r.id,r);
  PhaseEngine.Phase source=new PhaseEngine.Phase();source.id="source";source.name="source";
  PhaseEngine.Phase target=new PhaseEngine.Phase();target.id="target";target.name="target";target.terminal=true;
  PhaseEngine.Watch w=new PhaseEngine.Watch();w.id="tap";w.regionId="r";w.condition=condition;w.action=PhaseEngine.CENTER;w.nextPhase="target";w.reference=new float[]{0};w.threshold=.01f;w.timeoutMs=0;
  source.watches.add(w);e.phases.put(source.id,source);e.phases.put(target.id,target);e.initial=source.id;e.enter(source.id,0);return e;
 }
 static Map<String,float[]> sample(float value){Map<String,float[]> s=new HashMap<>();s.put("r",new float[]{value});return s;}
 public static void main(String[] args){
  for(int condition:new int[]{PhaseEngine.MATCH,PhaseEngine.DISAPPEAR,PhaseEngine.CHANGE,PhaseEngine.TIMEOUT}){
   PhaseEngine e=engine(condition);float value=condition==PhaseEngine.MATCH?0:1;
   if(condition==PhaseEngine.CHANGE){e.observe(sample(0),1);check(e.poll()==null,"initial image does not count as a change");}
   e.observe(sample(value),10);
   if(condition!=PhaseEngine.TIMEOUT)check(e.poll()==null,"visual action requires two observations");
   e.observe(sample(value),20);PhaseEngine.Watch w=e.poll();check(w!=null,"action selected before rejection");
   long epoch=e.epoch;check(e.latched.contains(w.id),"selected action is latched");
   check(e.cancelIfCurrent(w,"source",epoch),"rejected current action unlocks its retry");
   check(e.current.equals("source")&&e.lastCompletedRule.isEmpty()&&e.epoch==epoch,"rejection records no completed action or transition");
   e.observe(sample(value),30);
   if(condition!=PhaseEngine.TIMEOUT){check(e.poll()==null,"retry requires fresh confirmation");e.observe(sample(value),40);}
   PhaseEngine.Watch retry=e.poll();check(retry==w,"same visible condition can be retried without disappearing");
   check(e.finishIfCurrent(retry,"source",epoch,50)&&e.current.equals("target")&&e.lastCompletedRule.equals("tap"),"accepted retry alone commits transition");
   check(!e.cancelIfCurrent(w,"source",epoch),"late rejection from an old phase cannot reopen it");
  }
  PhaseEngine pending=engine(PhaseEngine.MATCH);pending.observe(sample(0),10);pending.observe(sample(0),20);
  PhaseEngine.Watch queued=pending.rule("tap");check(pending.cancelIfCurrent(queued,"source",pending.epoch)&&pending.poll()==null,"queued rejected action cannot leak through before new confirmation");
  System.out.println("CancelledActionTest: "+checks+" checks passed; real state engine rejected-input recovery, Android taps not tested");
 }
}
