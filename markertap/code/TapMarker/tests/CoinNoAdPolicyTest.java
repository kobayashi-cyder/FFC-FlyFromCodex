package jp.tapmarker;
import java.util.*;

/** CM opt-out is tested against shipped evidence and held-out recorded screens. */
public final class CoinNoAdPolicyTest {
 static final String P=CoinPreset.P;static int checks;static Map<String,float[]> refs;
 static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 static final class Run {
  final PhaseEngine e=new PhaseEngine();final CoinLayout layout;Map<String,float[]> samples;
  Run(String state){CoinPreset.install(e,refs);e.strictRuntime=true;layout=CoinLayout.install(e,refs);AdCloseAutomaton.install(e,CoinPreset.APP);check(e.validate().isEmpty()&&layout!=null,"valid shipped no-ad graph");e.enter(P+state,0);}
  Map<String,float[]> sample(int width,int height,RegionSampler.Pixels pixels,long now){return samples=layout.sampleRelative(e.activeRegions(),width,height,pixels,now);}
  PhaseEngine.Watch observe(VirtualPresetProfileTest.Image im,RegionSampler.Pixels pixels,long now){e.observe(sample(im.width,im.height,pixels,now),now);return e.poll();}
  PhaseEngine.Watch pair(VirtualPresetProfileTest.Image im,RegionSampler.Pixels pixels,long now){check(observe(im,pixels,now)==null,"single frame does not commit input");return observe(im,pixels,now+250);}
 }
 static void graph(){
  Run r=new Run("main");for(String key:CoinNoAdPolicy.BOOSTS){PhaseEngine.Watch old=r.e.rule(P+key+"-cm"),cancel=r.e.rule(P+key+"-cancel");check(old!=null&&old.action==PhaseEngine.BACK&&old.fallback&&old.nextPhase.equals(CoinNoAdPolicy.WAIT),"no CM input for "+key);check(cancel!=null&&cancel.action==PhaseEngine.CENTER&&cancel.regionId.equals(CoinNoAdPolicy.CANCEL),"only visible Cancel may supply a tap for "+key);}
  check(!r.e.phases.get(CoinNoAdPolicy.WAIT).markersDuring,"dismissal verification holds main taps");
  check(r.e.rule(P+"start-tap").action==PhaseEngine.CENTER&&r.e.rule(P+"mission-bonus-start").action==PhaseEngine.CENTER,"this-way start remains available without watching CM");
  check(r.e.rule(P+"reward-ad-timeout").timeoutMs==30000,"no legacy 120-second viewing wait");
  check(!CoinNoAdPolicy.apply(r.e),"policy can be applied twice without duplicating graph elements");
  check(!CoinNoAdPolicy.allowsUi(CoinPreset.APP,"ゲット","コイン CMをみてゲット"),"BANC cannot reacquire optional CM through a generic Get label");
  check(CoinNoAdPolicy.allowsUi(CoinPreset.APP,"ゲット","ミッション コイン報酬"),"normal mission claims remain available");
  check(CoinNoAdPolicy.allowsUi(CoinPreset.APP,"このままはじめる","CMをみてゲット"),"non-CM stage start remains available to BANC");
  check(CoinNoAdPolicy.allowsUi(CoinPreset.APP,"キャンセル","CMをみてゲット"),"CM context never blocks Cancel");
  check(CoinNoAdPolicy.allowsUi(FarmPreset.APP,"ゲット","コイン CMをみてゲット"),"coin-only UI policy does not change other games");
 }
 static void actualCancel(){
  for(String id:new String[]{"25-native","30-native","154-native"}){
   VirtualPresetProfileTest.Image im=Video6687ReplayTest.images.get(id);Run r=new Run("boost-tap2");PhaseEngine.Watch w=r.pair(im,im,1000);
   check(w!=null&&w.id.equals(P+"boost-tap2-cancel")&&w.action==PhaseEngine.CENTER,"held-out visible Cancel wins over Back/CM: "+id+" / "+(w==null?r.e.diagnostic:w.id));
   List<PhaseEngine.Region> plan=ScanPlan.select(r.e,w.regionId,false);check(plan.stream().anyMatch(region->region.id.equals(P+"boost-tap2")),"Cancel preflight includes the boost modal evidence");
   Map<String,float[]> fresh=r.layout.sampleRelative(plan,im.width,im.height,im,1400);check(r.e.confirmsAction(w,r.e.current,r.e.epoch,fresh),"fresh preflight rechecks visible Cancel and the modal");
   float[] p=r.layout.pointRelative(w,fresh,im.width,im.height);check(p!=null&&p[0]/im.width>.55f&&p[0]/im.width<.66f&&p[1]/im.height>.80f&&p[1]/im.height<.86f,"Cancel coordinates stay below the CM button and outside centre marker");
   check(r.layout.pointRelative(w,fresh,im.width+1,im.height)==null,"stale size cannot reuse Cancel position");
   r.e.finish(w,1500);check(r.e.current.equals(CoinNoAdPolicy.WAIT)&&!ContinuousTapPolicy.allow(r.e,true,false,false,false,true,1500,1500),"input acknowledgement does not resume main taps");
  }
 }
 static void fallback(){
  VirtualPresetProfileTest.Image notReady=Video6687ReplayTest.images.get("153-native");Run early=new Run("boost-tap2");PhaseEngine.Watch earlyBack=early.pair(notReady,notReady,1000);check(earlyBack!=null&&earlyBack.action==PhaseEngine.BACK,"recorded frame before Cancel appears also cannot start CM");
  VirtualPresetProfileTest.Image im=Video6682ReplayTest.images.get("50.3-native");Run r=new Run("boost-pocket");PhaseEngine.Watch w=r.pair(im,im,1000);
  check(w!=null&&w.action==PhaseEngine.BACK&&w.id.equals(P+"boost-pocket-cm"),"observed pocket dialog without Cancel uses Back, never CM");check(r.layout.pointRelative(w,r.samples,im.width,im.height)==null,"Back cannot expose the old CM coordinates");
  im=Video6687ReplayTest.images.get("30-native");final VirtualPresetProfileTest.Image input=im;int[] cut=RegionMath.bounds(new float[]{.53f,.79f,.69f,.87f},im.width,im.height);
  RegionSampler.Pixels hidden=(x,y)->x>=cut[0]&&x<cut[2]&&y>=cut[1]&&y<cut[3]?0xff554444:input.get(x,y);
  r=new Run("boost-tap2");w=r.pair(im,hidden,1000);check(w!=null&&w.action==PhaseEngine.BACK,"missing Cancel still cannot fall through to CM input");
  check(CoinNoAdPolicy.point(r.samples,im.width,im.height)==null,"missing Cancel clears cached touch position");
 }
 static void verifyAndExits(){
  Run r=new Run("boost-tap2");VirtualPresetProfileTest.Image im=Video6687ReplayTest.images.get("30-native");PhaseEngine.Watch w=r.pair(im,im,1000);r.e.finish(w,1300);
  r.e.observe(Collections.emptyMap(),2000);check(r.e.poll()==null,"dismissal cooldown does not send another action");
  r.e.observe(Collections.emptyMap(),2600);w=r.e.poll();check(w!=null&&w.action==PhaseEngine.WAIT&&w.nextPhase.equals(P+"verify"),"dismissal always returns through screen verification");r.e.finish(w,2600);
  im=Video6687ReplayTest.images.get("10");w=r.pair(im,im,2700);check(w!=null&&w.nextPhase.equals(P+"main"),"verified home resumes play");r.e.finish(w,3000);check(ContinuousTapPolicy.allow(r.e,CoinPreset.playable(r.e,r.samples),false,false,false,true,3000,3000),"main tapping resumes after home confirmation");
  r=new Run("reward-ad");im=Video6687ReplayTest.images.get("35.5-native");w=r.pair(im,im,1000);check(w!=null&&AdCloseAutomaton.handles(w.regionId)&&w.action==PhaseEngine.CENTER,"forced/existing ad can still use its actual exit");
  CoinMissionJourney journey=new CoinMissionJourney();journey.route(P+"clear");journey.route(CoinNoAdPolicy.WAIT);check(journey.blocksMain()&&journey.route(P+"main").equals(P+"mission-tap"),"opting out preserves interrupted mission collection");
 }
 static void geometry(){
  VirtualPresetProfileTest.Image im=Video6687ReplayTest.images.get("30-native");
  for(float[] t:new float[][]{{1,1,0,0},{1,.90f,0,.06f},{.94f,.86f,.03f,.08f}}){Run r=new Run("boost-tap2");int width=720,height=1616;RegionSampler.Pixels pixels=(x,y)->{float nx=(x/(float)width-t[2])/t[0],ny=(y/(float)height-t[3])/t[1];return nx<0||nx>=1||ny<0||ny>=1?0xff142030:im.get(Math.min(im.width-1,(int)(nx*im.width)),Math.min(im.height-1,(int)(ny*im.height)));};
   r.sample(width,height,pixels,1000);PhaseEngine.Watch cancel=r.e.rule(P+"boost-tap2-cancel");float[] point=r.layout.pointRelative(cancel,r.samples,width,height);
   check(r.e.confirmsAction(cancel,r.e.current,r.e.epoch,r.samples)&&point!=null,"resized/inset Cancel can be verified");float expected=(1270/1536f*t[1]+t[3])*height;check(Math.abs(point[1]-expected)<height*.025f,"relative Cancel coordinates follow the modal");
  }
 }
 static void migration()throws Exception{
  for(PhaseEngine old:new PhaseEngine[]{CoinPreset.version179Defaults(refs),CoinPreset.version178Defaults(refs),CoinPreset.version170Defaults(refs)}){PhaseEngine fresh=new PhaseEngine();CoinPreset.install(fresh,refs);check(CoinPreset.upgrade(old,refs)&&AutomatonGraph.sameConfiguration(old,fresh)&&!CoinPreset.upgrade(old,refs),"factory migration enables opt-out completely and idempotently");}
  PhaseEngine saved=CoinPreset.version179Defaults(refs),runtime=saved.runtimeCopy();CoinNoAdPolicy.apply(runtime);check(saved.rule(P+"boost-tap2-cm").action==PhaseEngine.CENTER&&runtime.rule(P+"boost-tap2-cm").action==PhaseEngine.BACK,"runtime policy also covers retained old actions while preserving saved edits");
  PhaseEngine stopped=CoinPreset.version179Defaults(refs);stopped.rule(P+"boost-tap2-cm").action=PhaseEngine.STOP;CoinNoAdPolicy.apply(stopped);check(stopped.rule(P+"boost-tap2-cm").action==PhaseEngine.STOP&&stopped.rule(P+"boost-tap2-cancel")==null,"custom Stop cannot become a new tap or Back action");
  PhaseEngine farm=new PhaseEngine();FarmPreset.install(farm,VirtualPresetProfileTest.canonical("tests/audit-farm-references.bin"));PhaseEngine copy=farm.runtimeCopy();check(!CoinNoAdPolicy.apply(farm)&&AutomatonGraph.sameConfiguration(farm,copy),"other games are unaffected");
 }
 public static void main(String[] args)throws Exception{
  refs=VirtualPresetProfileTest.canonical("tests/coin-cancel-references.bin");Video6687ReplayTest.load();Video6682ReplayTest.load();graph();actualCancel();fallback();verifyAndExits();geometry();migration();
  System.out.println("CoinNoAdPolicyTest: "+checks+" checks passed; held-out cancel/Back, CM exclusion, verification, forced-ad exits, migration and app isolation; device input untested");
 }
}
