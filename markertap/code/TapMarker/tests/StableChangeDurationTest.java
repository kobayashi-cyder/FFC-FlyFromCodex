package jp.tapmarker;
import java.util.*;
public class StableChangeDurationTest {
 static int n;static void check(boolean b,String m){if(!b)throw new AssertionError(m);n++;}
 public static void main(String[] args){PhaseEngine e=new PhaseEngine();e.strictRuntime=true;PhaseEngine.Phase p=new PhaseEngine.Phase();p.id="ad";e.phases.put(p.id,p);PhaseEngine.Watch w=new PhaseEngine.Watch();w.id="close";w.regionId="corner";w.stableMs=2000;p.watches.add(w);e.enter(p.id,0);Map<String,float[]> s=new HashMap<>();s.put("corner",new float[]{0});e.observe(s,0);check(e.poll()==null,"baseline does not tap");s.put("corner",new float[]{1});e.observe(s,500);e.observe(s,1100);check(e.poll()==null,"two frames insufficient for duration");s.put("corner",new float[]{.5f});e.observe(s,1500);e.observe(s,2500);check(e.poll()==null,"countdown restarts duration");e.observe(s,3499);check(e.poll()==null,"duration boundary");e.observe(s,3500);check(e.poll()==w,"stable change executes");e.finish(w,3501);check(e.current.equals("ad"),"tap preserves ad state");e.observe(s,4000);check(e.poll()==null,"same display not retapped");System.out.println("PASS "+n+" stability duration checks");}
}
