package jp.tapmarker;
import java.io.*;import java.util.*;import java.util.zip.*;
/** New video pixels, including holdouts; current shipped references and runtime layout path. */
public final class Video6651ReplayTest {
 static final String P=UsagiPreset.PREFIX;static int checks;static Map<String,float[]> refs,evidence;static Map<String,VirtualPresetProfileTest.Image> frames=new LinkedHashMap<>();
 static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 static void load()throws Exception{
  try(DataInputStream in=new DataInputStream(new FileInputStream("tests/video6651-references.bin"))){refs=UsagiPresetTest.read(in);evidence=UsagiPresetTest.read(in);check(in.read()==-1,"all shipped template data read");}
  try(DataInputStream in=new DataInputStream(new GZIPInputStream(new FileInputStream("tests/video6651-replay.bin.gz")))){int n=in.readInt();for(int i=0;i<n;i++)frames.put(in.readUTF(),new VirtualPresetProfileTest.Image(in));check(in.read()==-1,"complete recorded RGB fixture");}
 }
 static final class Runtime {
  PhaseEngine saved=new PhaseEngine(),e;AdaptiveLayout layout;Map<String,float[]> last;long clock;
  Runtime(){UsagiPreset.install(saved,refs);e=saved.runtimeCopy();e.strictRuntime=true;layout=AdaptiveLayout.install(e,UsagiPreset.APP,refs,evidence);check(layout!=null,"shipped normal/virtual layout adapter installed");AdCloseAutomaton.install(e,UsagiPreset.APP);check(e.validate().isEmpty(),"all runtime graph links and references are valid");}
  Map<String,float[]> sample(String name,long now){clock=Math.max(clock,now);VirtualPresetProfileTest.Image im=frames.get(name);last=layout.sample(e.activeRegions(),im.width,im.height,im,now);return last;}
  PhaseEngine.Watch event(String state,String first,String second,long now){now=Math.max(now,clock+350);e.enter(P+state,0);e.observe(UsagiPreset.missionSamples(e,sample(first,now)),now);check(e.poll()==null,"first real frame cannot commit a visual transition: "+state+" / "+first);e.observe(UsagiPreset.missionSamples(e,sample(second,now+350)),now+350);return e.poll();}
  PhaseEngine.Watch expect(String state,String first,String second,String target,int action,long now){PhaseEngine.Watch w=event(state,first,second,now);check(w!=null&&w.nextPhase.equals(P+target)&&w.action==action,"recorded branch "+state+" / "+first+" -> "+target+" actual="+(w==null?e.diagnostic:w.id));return w;}
  void point(PhaseEngine.Watch w,String frame,float[] expected){VirtualPresetProfileTest.Image im=frames.get(frame);Map<String,float[]> samples=sample(frame,12000);check(e.confirmsAction(w,e.current,e.epoch,samples),"fresh preflight confirms the observed control: "+frame);float[] p=layout.point(w,samples,im.width,im.height);check(p!=null&&p[0]/im.width>expected[0]&&p[0]/im.width<expected[2]&&p[1]/im.height>expected[1]&&p[1]/im.height<expected[3],"tap lies inside the individually observed button: "+frame+" / "+Arrays.toString(p));}
 }
 static void coldStarts(){
  for(String state:new String[]{"classify","ad","verify"})for(String[] row:new String[][]{{"mission-get","mission-get-later","missions"},{"mission-play","mission-play-later","missions"},{"reward","reward-later","reward"},{"bonus","bonus-later","bonus"},{"offline","offline-later","offline"},{"promo","promo-later","promo"}}){Runtime r=new Runtime();r.expect(state,row[0],row[1],row[2],PhaseEngine.WAIT,8000);check(!RecoveryRequest.visibleBranch(r.e,Collections.emptyMap()),"no missing-pixel match");check(RecoveryRequest.visibleBranch(r.e,r.last),"known dialog suppresses an expired recovery timer");}
 }
 static void actions(){
  for(String[] row:new String[][]{{"missions","mission-get","mission-get-later","claim-wait"},{"missions","mission-play","mission-play-later","verify"},{"reward","reward","reward-later","classify"},{"bonus","bonus","bonus-later","verify"},{"offline","offline","offline-later","verify"},{"promo","promo","promo-later","classify"}}){Runtime r=new Runtime();PhaseEngine.Watch w=r.expect(row[0],row[1],row[2],row[3],PhaseEngine.CENTER,100);float[] bounds=row[0].equals("missions")?new float[]{.68f,.630f,.90f,.680f}:row[0].equals("reward")?new float[]{.23f,.540f,.77f,.604f}:row[0].equals("bonus")?new float[]{.30f,.614f,.72f,.654f}:row[0].equals("offline")?new float[]{.42f,.644f,.60f,.687f}:new float[]{.850f,.250f,.935f,.302f};r.point(w,row[2],bounds);check(!RecoveryRequest.visibleBranch(r.e,r.sample("ad",14000)),"ad cannot substitute for a game button: "+row[0]);}
  Runtime nativeRun=new Runtime();PhaseEngine.Watch close=nativeRun.expect("reward","reward-native","reward-native-later","classify",PhaseEngine.CENTER,100);nativeRun.point(close,"reward-native-later",new float[]{.23f,.540f,.77f,.604f});
 }
 static void journey(){
  Runtime r=new Runtime();MissionJourney journey=new MissionJourney();journey.route(P+"clear");
  PhaseEngine.Watch w=r.expect("mission-opening","mission-get","mission-get-later","missions",PhaseEngine.WAIT,100);r.e.finish(w,500);journey.route(r.e.current);check(journey.blocksMain(),"confirmed mission blocks premature main-loop return");
  w=r.expect("missions","mission-get","mission-get-later","claim-wait",PhaseEngine.CENTER,100);r.e.finish(w,500);journey.route(r.e.current);
  w=r.expect("claim-wait","reward","reward-later","reward",PhaseEngine.WAIT,100);r.e.finish(w,500);journey.route(r.e.current);
  w=r.expect("reward","reward","reward-later","classify",PhaseEngine.CENTER,100);r.e.finish(w,500);journey.route(r.e.current);check(journey.blocksMain(),"closing reward does not start the main loop");
  w=r.expect("classify","mission-play","mission-play-later","missions",PhaseEngine.WAIT,100);r.e.finish(w,500);journey.route(r.e.current);
  w=r.expect("missions","mission-play","mission-play-later","verify",PhaseEngine.CENTER,100);r.e.finish(w,500);journey.completedPlay();journey.route(r.e.current);
  w=r.expect("verify","main-no-badge","main-stopped","main",PhaseEngine.WAIT,100);r.e.finish(w,500);check(journey.route(r.e.current).equals(P+"main")&&!journey.blocksMain(),"only observed Play and the next main screen permit the main loop");
 }
 static void negativesAndCalibration(){
  for(String name:new String[]{"ad","ad-skip","ad-store","chrome","reward-loading","reward-transition"}){Runtime r=new Runtime();PhaseEngine.Watch w=r.event("classify",name,name,100);check(w==null,"different/covered/loading image cannot match a known dialog or perform input: "+name);check(!RecoveryRequest.visibleBranch(r.e,r.last),"unknown image not claimed as recovered: "+name);}
  Runtime r=new Runtime();r.e.enter(P+"verify",0);r.sample("main-no-badge",1000);r.e.enter(P+"classify",2000);PhaseEngine.Watch w=r.expect("classify","reward","reward-later","reward",PhaseEngine.WAIT,2100);check(w!=null,"reward recognition also survives a previous main-screen calibration");
  r=new Runtime();w=r.event("main","main-badge","main-badge",100);check(w==null||!w.nextPhase.equals(P+"open-missions"),"badge on unfinished stage never opens missions");r=new Runtime();check(r.event("main","main-no-badge","main-stopped",100)==null,"no badge, no completed stage and no available amount upgrade creates no false branch");
  r=new Runtime();r.e.enter(P+"missions",0);r.sample("mission-get",1000);PhaseEngine.Watch get=r.e.rule(P+"ゲットのみタップ");VirtualPresetProfileTest.Image im=frames.get("mission-get");int[] cut=RegionMath.bounds(new float[]{.28f,.16f,.74f,.20f},im.width,im.height);RegionSampler.Pixels missing=(x,y)->x>=cut[0]&&x<cut[2]&&y>=cut[1]&&y<cut[3]?0xff101820:im.get(x,y);Map<String,float[]> sample=r.layout.sample(r.e.activeRegions(),im.width,im.height,missing,3000);check(get.distance(sample.get(get.regionId))>get.threshold,"Get label without its mission heading cannot authorize a tap");check(r.layout.point(get,sample,im.width,im.height)==null,"unmatched button provides no guessed tap position");
  for(String phase:new String[]{"main","missions","reward","bonus","offline","promo"}){r=new Runtime();r.e.enter(P+phase,0);float area=0;for(PhaseEngine.Region region:r.e.activeRegions()){float[] b=region.bounds;if(AdCloseAutomaton.handles(region.id))check((b[2]-b[0])*(b[3]-b[1])<=.019f,"ad interruption corners remain sparse");else area+=(b[2]-b[0])*(b[3]-b[1]);}check(area<.04f,"only current-state sparse regions are selected: "+phase);}
 }
 public static void main(String[] args)throws Exception{load();coldStarts();actions();journey();negativesAndCalibration();System.out.println("Video6651ReplayTest: "+checks+" checks passed; supplied native/downscaled RGB video pixels, held-out timestamps, cold/warm dialog starts, action coordinates and reward/Play journey; Android device input not tested");}
}
