package jp.tapmarker;
import java.util.*;

public class BancKnowHowTest {
 static int checks;static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 static BancAutonomy.Decision unknown(BancAutonomy core,PhaseEngine e,BancAutonomy.Ui ui,long at){return core.review(e,BancAutonomyTest.samples(false,true),ui,at,at,Collections.emptySet());}
 public static void main(String[] args){
  BancKnowHow pool=new BancKnowHow();pool.observe(GameTarget.FARM,"claim",true,14000);pool.observe(GameTarget.FARM,"claim",true,14500);
  BancKnowHow.Advice coin=pool.forGame(CoinPreset.APP,"claim");check(coin.verified()&&coin.sources==1&&coin.successes==2&&coin.bias()>0,"farm successes guide claim choices in coin game");check(coin.resultWait()>15000&&coin.resultWait()<=30000,"foreign observed slow response informs bounded result wait");
  check(pool.forGame(GameTarget.FARM,"claim").attempts==0,"same-game history is accounted by its own local skill memory");check(pool.forGame(CoinPreset.APP,"chest").attempts==0,"claim knowhow does not become a chest policy");
  check(pool.forGame(GameTarget.BALLOON,"claim").verified(),"farm knowhow is transferable to distinct game in same app");
  PhaseEngine e=BancAutonomyTest.engine();BancAutonomy receiver=new BancAutonomy(pool,CoinPreset.APP);receiver.reset(0);BancAutonomy.Ui ui=BancAutonomyTest.ui();unknown(receiver,e,ui,1);BancAutonomy.Decision d=unknown(receiver,e,ui,3000);
  check(d.kind==BancAutonomy.CONTROL&&receiver.transferredProposals==1&&d.reason.contains("他ゲーム"),"foreign procedure participates in candidate selection");check(receiver.skills.isEmpty(),"foreign successes do not populate target screen memory");check(receiver.begin(d,e,ui,BancAutonomyTest.samples(false,true),3000),"transfer still requires visible target control and local source image");receiver.sent(3010);
  check(unknown(receiver,e,ui,18100).kind==BancAutonomy.WAIT&&receiver.pending(),"borrowed timing waits for slow result without retrying input");
  check(receiver.review(e,BancAutonomyTest.samples(true,true),ui,18500,18500,Collections.emptySet()).kind==BancAutonomy.WAIT&&receiver.skills.isEmpty(),"foreign success never replaces target's first result confirmation");
  d=receiver.review(e,BancAutonomyTest.samples(true,true),ui,18800,18800,Collections.emptySet());check(d.kind==BancAutonomy.RESYNC&&receiver.skills.size()==1&&!receiver.skills.values().iterator().next().acquired(),"target must acquire its own screen-bound skill despite shared knowhow");
  check(pool.forGame(GameTarget.OMISE,"claim").sources==2,"new local verification contributes transferable experience for third game");
  e=BancAutonomyTest.engine();receiver=new BancAutonomy(pool,GameTarget.OMISE);receiver.reset(0);BancAutonomy.Ui empty=BancAutonomy.Ui.empty();unknown(receiver,e,empty,1);check(unknown(receiver,e,empty,3000).kind==BancAutonomy.NONE,"transferred knowledge cannot invent absent target controls");
  receiver=new BancAutonomy(pool,GameTarget.OMISE);receiver.reset(0);unknown(receiver,e,ui,1);receiver.selector.disconnectAll();check(unknown(receiver,e,ui,3000).kind==BancAutonomy.NONE,"transfer does not bypass BANC selection signal");
  receiver=new BancAutonomy(pool,GameTarget.OMISE);receiver.transferEnabled=false;receiver.reset(0);unknown(receiver,e,ui,1);d=unknown(receiver,e,ui,3000);check(d.kind==BancAutonomy.CONTROL&&receiver.transferredProposals==0&&!d.reason.contains("他ゲーム"),"sharing can be disabled without disabling grounded local operation");
  pool.observe(ShoppingPreset.APP,"close",false,0);pool.observe(ShoppingPreset.APP,"close",false,0);BancKnowHow.Advice caution=pool.forGame(CoinPreset.APP,"close");check(!caution.verified()&&caution.bias()<0&&caution.retryWait()>8000,"failed foreign close attempts contribute caution and longer retry wait");
  int size=pool.snapshot().size();pool.observe(GameTarget.OMISE,"purchase",true,1);check(pool.snapshot().size()==size,"unknown/commerce capabilities cannot enter transferable procedures");
  check(BancKnowHow.procedure("claim").contains("2枚")&&BancKnowHow.procedure("purchase").isEmpty(),"shared procedure retains target result verification and scope constraints");
  BancKnowHow restored=new BancKnowHow();restored.restore(pool.snapshot());check(restored.forGame(GameTarget.BALLOON,"claim").sources==pool.forGame(GameTarget.BALLOON,"claim").sources&&restored.forGame(CoinPreset.APP,"close").retryWait()==12000,"procedure and timing knowledge survives persistence roundtrip");
  List<BancKnowHow.Row> copy=pool.snapshot();copy.get(0).successes=9999;check(pool.forGame(CoinPreset.APP,"claim").successes==2,"exported knowledge cannot mutate shared pool");
  restored.restore(copy);check(restored.forGame(CoinPreset.APP,"claim").attempts==0,"invalid persisted success counts rejected");
  check(new BancAutonomy.Ui("報酬50コイン 03:10",Collections.emptyList()).context.equals(new BancAutonomy.Ui("報酬100コイン 03:11",Collections.emptyList()).context),"changing counters and clock do not fragment procedural context");check(BancAutonomy.safeKind("閉じる",BancAutonomy.normalizeContext("100円の購入を確認")).isEmpty(),"normalizing amounts never removes restricted transaction meaning");
  System.out.println("BancKnowHowTest: "+checks+" checks passed; cross-game policy/timing transfer with local grounding, Android input untested");
 }
}
