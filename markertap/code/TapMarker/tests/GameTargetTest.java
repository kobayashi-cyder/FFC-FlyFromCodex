package jp.tapmarker;
import java.util.*;
/** Two games in one host retain separate settings and finite daily completion. */
public final class GameTargetTest {
 static int checks;static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 public static void main(String[] args){
  check(GameTarget.key(GameTarget.USAPO,"farm").equals(GameTarget.FARM),"embedded farm has a stable scoped identity");
  check(GameTarget.key(GameTarget.USAPO,"balloon").equals(GameTarget.BALLOON),"balloon has a different identity in the same host");
  check(!GameTarget.FARM.equals(FarmPreset.APP)&&!GameTarget.BALLOON.equals(GameTarget.FARM),"standalone farm, embedded farm and balloon never share settings");
  check(!GameTarget.OMISE.equals(ShoppingPreset.APP),"Omise is not ShoppingUsagi");
  for(String target:new String[]{GameTarget.FARM,GameTarget.BALLOON}){check(GameTarget.packageName(target).equals(GameTarget.USAPO),"Android launch uses actual host package");check(GameTarget.key(GameTarget.USAPO,GameTarget.mode(target)).equals(target),"mode round trip keeps exact game identity");}
  check(Arrays.equals(GameTarget.profiles(GameTarget.USAPO),new String[]{GameTarget.FARM,GameTarget.BALLOON}),"route chooser exposes both games");
  check(GameTarget.key(FarmPreset.APP,"farm").equals(FarmPreset.APP)&&GameTarget.key(GameTarget.OMISE,"balloon").equals(GameTarget.OMISE),"other app storage remains stable");
  check(GameTarget.key(GameTarget.USAPO,"").equals(GameTarget.USAPO),"existing common Usapo settings keep their old identity");
  Map<String,String> configurations=new HashMap<>();configurations.put(GameTarget.FARM,"farm tap/close");configurations.put(GameTarget.BALLOON,"punch tap/mission");configurations.put(FarmPreset.APP,"standalone farm");check(configurations.size()==3&&configurations.get(GameTarget.FARM).equals("farm tap/close"),"saving another game cannot replace farm data");
  boolean invalid=false;try{GameTarget.packageName(GameTarget.USAPO+"#unknown");}catch(IllegalArgumentException expected){invalid=true;}check(invalid,"unknown mode cannot be passed as an Android package");
  DailyRoute r=new DailyRoute();long run=r.start(new String[]{GameTarget.FARM,GameTarget.BALLOON,GameTarget.OMISE,GameTarget.BOX,GameTarget.FARM});check(r.size()==4&&r.current().equals(GameTarget.FARM),"same-host games survive route deduplication");
  check(!r.complete(GameTarget.USAPO,run)&&!r.complete(GameTarget.BALLOON,run),"host-only or wrong-game completion does not skip farm");
  check(r.complete(GameTarget.FARM,run)&&r.current().equals(GameTarget.BALLOON),"farm completion advances to balloon in the same APK");
  check(!r.complete(GameTarget.FARM,run)&&r.complete(GameTarget.BALLOON,run),"late farm result cannot complete balloon");
  check(r.current().equals(GameTarget.OMISE)&&r.complete(GameTarget.OMISE,run)&&r.current().equals(GameTarget.BOX),"distinct extra apps continue in order");
  r.stop();check(!r.complete(GameTarget.BOX,run),"stopped input cannot advance the route");long next=r.start(new String[]{GameTarget.BOX});check(!r.complete(GameTarget.BOX,run)&&r.complete(GameTarget.BOX,next)&&!r.active(),"only current run completes final game");
  check(!GameTarget.dailyOrderSupported(new String[]{GameTarget.FARM,GameTarget.BALLOON}),"unverified same-host game handoff cannot silently run");
  check(GameTarget.dailyOrderSupported(new String[]{GameTarget.FARM,GameTarget.OMISE,GameTarget.BOX})&&GameTarget.dailyOrderSupported(new String[]{GameTarget.BALLOON,FarmPreset.APP}),"one embedded game can continue to different physical apps");
  check(GameTarget.dailyDefault(GameTarget.FARM,true,GameTarget.FARM)&&!GameTarget.dailyDefault(GameTarget.BALLOON,true,GameTarget.FARM),"saved completion profiles cannot preselect both host games");
  check(GameTarget.dailyDefault(GameTarget.BOX,true,GameTarget.FARM)&&!GameTarget.dailyDefault(GameTarget.FARM,false,GameTarget.FARM),"other configured apps remain selected; missing completion stays unselected");
  PhaseEngine entry=new PhaseEngine();PhaseEngine.Phase initial=new PhaseEngine.Phase();initial.id="hub";entry.phases.put(initial.id,initial);PhaseEngine.Watch action=new PhaseEngine.Watch();action.id="enter-game";action.action=PhaseEngine.CENTER;initial.watches.add(action);
  check(!GameTarget.entryCompleted(entry),"previous game's completion screen is not armed at startup");entry.lastCompletedRule=action.id;check(GameTarget.entryCompleted(entry),"confirmed learned entry input allows game completion checking");action.action=PhaseEngine.WAIT;check(!GameTarget.entryCompleted(entry),"classification-only transition cannot arm completion");entry.lastCompletedRule="stale-missing-rule";check(!GameTarget.entryCompleted(entry),"stale completion callback cannot arm another game");
  System.out.println("GameTargetTest: "+checks+" checks passed; scoped identities and same-host daily route; target game UI/input not tested");
 }
}
