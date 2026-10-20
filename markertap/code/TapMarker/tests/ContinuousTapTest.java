package jp.tapmarker;
import java.util.*;

/** Main input across ongoing recognition, interruptions, retries and window changes. */
public final class ContinuousTapTest {
 static int checks;static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
 static PhaseEngine game(){PhaseEngine e=new PhaseEngine();e.strictRuntime=true;PhaseEngine.Phase main=new PhaseEngine.Phase();main.id="main";main.markersDuring=true;PhaseEngine.Phase ad=new PhaseEngine.Phase();ad.id="ad";e.phases.put(main.id,main);e.phases.put(ad.id,ad);PhaseEngine.Watch exit=new PhaseEngine.Watch();exit.id="ad-seen";exit.regionId="ad";exit.condition=PhaseEngine.MATCH;exit.reference=new float[]{1};exit.threshold=0;exit.nextPhase="ad";main.watches.add(exit);e.enter("main",0);return e;}
 static boolean allow(PhaseEngine e,boolean home,long observed,long now){return ContinuousTapPolicy.allow(e,home,false,false,false,true,observed,now);}
 public static void main(String[] args){
  PhaseEngine e=game();ContinuousTapSession session=new ContinuousTapSession();int[] window={0,0,540,1212};float[] point={270,978};long ticket=session.arm(e,window,point);int taps=0;
  // Recognition updates every 400ms, while the input clock advances every 32ms.
  // Pending recognition itself supplies no reason to stop the main input.
  long observed=0;for(int now=0;now<4000;now+=32){if(now%400<32){observed=now;e.observe(Collections.singletonMap("ad",new float[]{0}),now);}if(allow(e,true,observed,now)&&session.current(e,ticket))taps++;}
  check(taps==125,"no 120ms burst deadline or recognition pause interrupts four seconds of ordinary play");
  check(session.arm(e,window,new float[]{271,978})==ticket&&session.point()[0]==271,"same-state refreshed positions retain one input loop");
  e.observe(Collections.singletonMap("ad",new float[]{1}),4100);check(!allow(e,true,4100,4100),"first observed CM stops input before second confirmation");session.pause();check(!session.current(e,ticket),"queued main input is invalidated immediately");
  e.observe(Collections.singletonMap("ad",new float[]{1}),4450);PhaseEngine.Watch exit=e.poll();check(exit!=null,"CM transition still requires its two observations");e.finish(exit,4460);check(!allow(e,true,4450,4500),"confirmed ad cannot use game input");
  e.enter("main",5000);check(!allow(e,false,5000,5000),"returning to a phase without confirmed game pixels cannot resume");long resumed=session.arm(e,window,point);check(resumed!=ticket&&allow(e,true,5100,5100),"confirmed returned game creates a new main-input session");
  check(!allow(e,true,5000,6500)&&!allow(e,true,6000,5000),"missing or future-dated frames do not authorize taps");
  check(!ContinuousTapPolicy.allow(e,true,true,false,false,true,6000,6001),"pending mission collection holds input");check(!ContinuousTapPolicy.allow(e,true,false,true,false,true,6000,6001),"button action holds input");check(!ContinuousTapPolicy.allow(e,true,false,false,true,true,6000,6001),"app recovery holds input");check(!ContinuousTapPolicy.allow(e,true,false,false,false,false,6000,6001),"another app cannot receive main taps");
  long resized=session.arm(e,new int[]{30,40,570,1252},new float[]{300,1018});check(resized!=resumed&&!session.current(e,resumed),"moving a window invalidates the old input ticket");e.enter("main",7000);check(!session.current(e,resized),"same phase re-entry also invalidates in-flight input");
  float[] supplied=session.point();supplied[0]=-1;check(session.point()[0]!=-1,"input coordinates are isolated from caller mutation");
  PhaseEngine other=game();ContinuousTapSession second=new ContinuousTapSession();long secondTicket=second.arm(other,window,point);session.pause();check(second.current(other,secondTicket),"each game has its own input session");
  for(String name:new String[]{"coin","farm","usagi","shopping","learned-mini-game"}){PhaseEngine game=game();game.phases.get("main").name=name;check(allow(game,true,10,20),name+" uses the same continuous-main policy");game.phases.get("main").terminal=true;check(!allow(game,true,10,20),name+" stops at a terminal screen");}
  System.out.println("ContinuousTapTest: "+checks+" checks passed; continuous play, CM interruption, confirmed resumption and per-game input isolation");
 }
}
