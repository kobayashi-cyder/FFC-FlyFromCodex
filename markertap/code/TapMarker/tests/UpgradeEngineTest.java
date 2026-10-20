package jp.tapmarker;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Upgrade detection does not become a branch of the shipped ordinary-play engine. */
public final class UpgradeEngineTest {
 static int checks;static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
 static void game(boolean farm)throws Exception{
  String pkg=farm?FarmPreset.APP:UsagiPreset.APP,p=farm?FarmPreset.P:UsagiPreset.PREFIX,id=p+(farm?"upgrade-ready":"amount-tap"),wait=p+(farm?"upgrade-wait":"amount-wait");
  PhaseEngine saved=new PhaseEngine();Map<String,float[]> refs=VirtualPresetProfileTest.canonical(farm?"tests/farm-replay.bin":"tests/usagi-replay.bin");if(farm)FarmPreset.install(saved,refs);else UsagiPreset.install(saved,refs);
  PhaseEngine runtime=saved.runtimeCopy();runtime.strictRuntime=true;runtime.enter(p+"main",0);long epoch=runtime.epoch;PhaseEngine.Watch rule=runtime.rule(id);check(rule!=null,"factory has the requested upgrade");
  UpgradeEngine side=new UpgradeEngine(runtime,pkg);
  check(side.enabled()&&runtime.rule(id)==null&&!runtime.phases.containsKey(wait),"upgrade operation and its wait phase leave the primary flow");
  check(saved.rule(id)!=null&&saved.phases.containsKey(wait),"saved editable automaton is preserved");
  check(runtime.validate().isEmpty(),"remaining primary graph is valid");
  ArrayList<PhaseEngine.Region> regions=new ArrayList<>(runtime.activeRegions());side.addRegions(regions);check(regions.contains(runtime.regions.get(rule.regionId)),"detached control is still sampled by the side engine");
  Map<String,float[]> samples=new HashMap<>();samples.put(rule.regionId,rule.reference.clone());if(!farm)samples.put(p+"amount-level",new float[768]);
  PhaseEngine.Watch home=runtime.rule(p+(farm?"verify-home":"return"));samples.put(p+(farm?"harvest":"game"),home.reference.clone());
  runtime.observe(samples,100);check(side.observe(samples,100,100,true)==null,"first upgrade observation does not send a tap");
  check(!runtime.awaitingBranch()&&ContinuousTapPolicy.allow(runtime,true,false,false,false,true,100,100),"upgrade availability never holds ordinary tapping");
  runtime.observe(samples,200);UpgradeEngine.Decision decision=side.observe(samples,200,200,true);check(decision!=null&&decision.watch.id.equals(id),"second fresh observation creates one side operation");
  check(side.visible(decision,samples)&&!side.visible(decision,Collections.emptyMap()),"queued upgrade still requires its visible control immediately before sending");
  check(runtime.current.equals(p+"main")&&runtime.epoch==epoch&&!runtime.awaitingBranch(),"side command changes no primary phase, epoch or branch latch");
  check(side.observe(samples,200,201,true)==null&&side.observe(samples,250,250,true)==null,"duplicate frames and an outstanding send cannot duplicate the upgrade");
  side.sent(decision,260);check(side.awaitingResult()&&ContinuousTapPolicy.allow(runtime,true,false,false,false,true,250,270),"result confirmation retains ordinary tapping");
  check(side.observe(samples,300,300,true)==null&&side.awaitingResult(),"Android completion alone is not reported as an upgrade effect");
  Map<String,float[]> changed=new HashMap<>(samples);float[] after=decision.before.clone();for(int i=0;i<after.length;i++)after[i]=after[i]<.5f?1:0;changed.put(decision.resultRegion,after);
  side.observe(changed,400,400,true);check(!side.awaitingResult()&&side.pending()==null&&side.status().contains("表示変化"),"new post-input evidence confirms display change without claiming an effect amount");
  check(side.observe(samples,500,500,true)==null,"held enabled button is not repeatedly tapped at ordinary-play speed");
  side.observe(samples,1500,1500,true);UpgradeEngine.Decision again=side.observe(samples,1600,1600,true);check(again!=null,"later fresh observations can authorize another independent upgrade");
  PhaseEngine.Watch event=null;for(PhaseEngine.Watch candidate:runtime.phases.get(runtime.current).watches)if(candidate.condition==PhaseEngine.MATCH&&candidate.reference!=null&&!candidate.nextPhase.isEmpty()&&!candidate.nextPhase.equals(runtime.current)){event=candidate;break;}
  check(event!=null,"ordinary event remains registered");Map<String,float[]> interrupted=new HashMap<>(samples);interrupted.put(event.regionId,event.reference.clone());runtime.observe(interrupted,1700);
  check(runtime.awaitingBranch()&&!ContinuousTapPolicy.allow(runtime,true,false,false,false,true,1700,1700),"reward or another non-upgrade event still holds ordinary tapping immediately");
  side.observe(interrupted,1700,1700,false);check(side.pending()==null,"other events also invalidate a queued side upgrade");
  side.sent(again,1800);check(!side.awaitingResult(),"a late side completion cannot revive a cancelled operation");
 }
 static void inputInterleave(){
  TapperTest.Device d=new TapperTest.Device();InputPriority priority=new InputPriority();Object owner=new Object();
  Tapper.Driver normalDriver=new Tapper.Driver(){public boolean ready(Tapper.Command c){return priority.allows(c.displayId,false)&&d.pending.getOrDefault(c.displayId,0)==0;}public void send(Tapper.Command c,float[] p,BooleanSupplier valid,Tapper.Result result){d.send(c,p,valid,result);}};
  Tapper.Driver sideDriver=new Tapper.Driver(){public boolean ready(Tapper.Command c){priority.request(c.displayId,owner);return d.pending.getOrDefault(c.displayId,0)==0;}public void send(Tapper.Command c,float[] p,BooleanSupplier valid,Tapper.Result result){d.send(c,p,valid,value->{priority.release(c.displayId,owner);result.finished(value);});}};
  Tapper normal=new Tapper(d,d,normalDriver,d),side=new Tapper(d,d,sideDriver,d),other=new Tapper(d,d,normalDriver,d);
  normal.update(TapperTest.command("normal",5000,1,1,true));other.update(new Tapper.Command("other",2,"other",new int[]{0,0,540,1200},new float[]{100,100},1,1,5000,true));d.until(10);
  side.update(new Tapper.Command("game",0,"shovel",new int[]{0,0,540,1200},new float[]{100,100},60,1,1500,false,1));d.until(50);
  check(normal.active()&&side.active()&&other.completed()==50,"ordinary tapper stays ON while a one-shot upgrade uses the input slot; another display keeps tapping");
  long before=normal.completed();d.until(150);
  check(normal.active()&&normal.completed()>before&&!side.active()&&side.completed()==1,"ordinary execution continues on the same command after the one-shot without restart");
  check(d.maxPending==1&&priority.allows(0,false),"normal and upgrade pointers do not overlap and the input slot is released");
  normal.pause("CM");side.pause("CM");long sent=normal.sent();d.until(300);check(normal.sent()==sent&&other.active(),"CM pauses the affected ordinary tapper while other games remain independent");
  priority.request(0,owner);Object second=new Object();priority.request(0,second);priority.release(0,owner);check(!priority.allows(0,false)&&priority.allows(0,true),"one side owner cannot release another owner's input reservation");priority.release(0,second);check(priority.allows(0,false),"all side reservations are released independently");
 }
 public static void main(String[] args)throws Exception{game(true);game(false);inputInterleave();System.out.println("UpgradeEngineTest: "+checks+" checks passed; side upgrades retain ordinary input; other events hold it; Android input not tested");}
}
