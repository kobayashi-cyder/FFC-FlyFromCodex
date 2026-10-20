package jp.tapmarker;
import java.util.*;

/** Actual held-out frames feed the same main-input gate used by both Android runners. */
public final class ContinuousCoinReplayTest {
 static int checks;static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 static boolean input(Video6687ReplayTest.Run run,long now){return ContinuousTapPolicy.allow(run.e,CoinPreset.playable(run.e,run.last),run.journey.blocksMain(),false,false,true,now,now);}
 public static void main(String[] args)throws Exception{
  Video6687ReplayTest.load();Video6687ReplayTest.Run run=new Video6687ReplayTest.Run();run.advance("5","10",1000,"main",PhaseEngine.WAIT);
  for(String id:new String[]{"5","10","90","95"}){check(run.observe(id,2000)==null,"ordinary coin count/badge does not select another operation");check(input(run,2000),"held-out ordinary game remains continuously playable: "+id);}
  check(run.observe("25-native",3000)==null&&!input(run,3000),"first boost-dialog frame holds main input");PhaseEngine.Watch w=run.observe("30-native",3350);check(w!=null,"second frame selects boost handling");run.e.finish(w,3400);check(!input(run,3400),"boost phase cannot use coin dropping");
  run.e.enter(CoinPreset.P+"reward-ad",4000);for(String id:new String[]{"35.5-native","36","113","115","155-native"}){run.sample(id,4300);check(!input(run,4300),"ad/browser/loading holds main input: "+id);}
  run.advance("156","157-native",5000,"main",PhaseEngine.WAIT);check(input(run,5350),"confirmed game return resumes continuous coin input");
  run.sample("95.5-native",6000);run.e.observe(run.last,6000);check(!input(run,6000),"first clear-screen observation holds coin dropping");
  run=new Video6687ReplayTest.Run();run.e.enter(CoinPreset.P+"main",0);run.journey.route(CoinPreset.P+"clear");run.sample("10",7000);check(!input(run,7000),"interrupted reward collection cannot silently resume the main loop");
  System.out.println("ContinuousCoinReplayTest: "+checks+" held-out checks passed; normal play, boost, CM, return, stage clear and collection intent");
 }
}
