package jp.tapmarker;
import java.util.*;
public final class RecoveryRequestTest {
 static int checks;static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 static PhaseEngine engine(){PhaseEngine e=new PhaseEngine();for(String name:new String[]{"ad","missions","reward"}){PhaseEngine.Phase p=new PhaseEngine.Phase();p.id=name;p.name=name;e.phases.put(name,p);}e.enter("ad",0);return e;}
 public static void main(String[] args){
  PhaseEngine e=engine(),other=engine();RecoveryRequest a=new RecoveryRequest(),b=new RecoveryRequest();int request=a.begin(e,7),otherRequest=b.begin(other,7);
  check(a.valid(e,7,request),"intent may run in its original state");check(!a.valid(other,7,request),"identical IDs and epochs on another screen never share a request");
  e.enter("missions",100);check(!a.valid(e,7,request),"observed mission cancels queued Chrome handoff and retry");check(b.valid(other,7,otherRequest),"one screen's mission does not cancel another screen's recovery");
  e.enter("ad",200);check(!a.valid(e,7,request),"returning to the same named state cannot revive an old callback");int next=a.begin(e,7);check(a.valid(e,7,next)&&!a.valid(e,7,request),"only the new request may send an intent");
  check(!a.valid(e,8,next),"a restarted run rejects old intent requests");a.cancel();check(!a.valid(e,7,next)&&!a.pending(),"all-stop prevents every delayed retry");
  next=a.begin(e,8);e.enter("ad",300);check(!a.valid(e,8,next),"same-state re-entry invalidates previous action completion");
  PhaseEngine.Region r=new PhaseEngine.Region();r.id="title";r.name="title";r.bounds=new float[]{0,0,.1f,.1f};e.regions.put(r.id,r);
  PhaseEngine.Watch known=new PhaseEngine.Watch();known.id="mission-visible";known.regionId=r.id;known.condition=PhaseEngine.MATCH;known.action=PhaseEngine.WAIT;known.reference=new float[]{.2f};known.threshold=.001f;known.nextPhase="missions";
  PhaseEngine.Watch timer=new PhaseEngine.Watch();timer.id="return";timer.condition=PhaseEngine.TIMEOUT;timer.action=PhaseEngine.RETURN_APP;timer.timeoutMs=0;timer.fallback=true;timer.nextPhase="ad";
  e.phases.get("ad").watches.add(known);e.phases.get("ad").watches.add(timer);e.strictRuntime=true;
  Map<String,float[]> match=new HashMap<>();match.put(r.id,new float[]{.2f});Map<String,float[]> mismatch=new HashMap<>();mismatch.put(r.id,new float[]{.8f});
  check(RecoveryRequest.visibleBranch(e,match),"preflight recognizes an already visible configured screen");check(!RecoveryRequest.visibleBranch(e,mismatch),"different pixels cannot cancel recovery as a known screen");check(!RecoveryRequest.visibleBranch(e,Collections.emptyMap()),"missing pixels are not a match");
  e.observe(mismatch,301);check(e.poll()==timer,"unknown display requests recovery");request=a.begin(e,8);
  check(a.current(e,8),"recovery stays pending without occupying observation");e.observe(match,400);check(e.poll()==null,"one fresh mission frame still needs confirmation");
  e.observe(match,750);PhaseEngine.Watch seen=e.poll();check(seen==known,"mission is recognized while recovery is pending");e.finish(seen,750);check(!a.valid(e,8,request),"mission action takes precedence over the old timer's callback");
  e.enter("ad",800);e.observe(mismatch,801);check(e.poll()==timer,"another unknown entry can request recovery");RecoveryRequest.release(e);e.observe(mismatch,802);check(e.poll()==timer,"canceled preflight releases the intent rule for a later genuine interruption");
  known.guardIds=new String[]{"title"};known.guardReferences=new float[][]{new float[]{.8f}};check(!RecoveryRequest.visibleBranch(e,match),"configured guard still required before canceling a request");
  System.out.println("RecoveryRequestTest: "+checks+" checks passed; live observation, source identity/epoch, cancellation, repeated entry and independent screens");
 }
}
