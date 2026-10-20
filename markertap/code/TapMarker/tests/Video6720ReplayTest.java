package jp.tapmarker;
import java.io.*;import java.util.*;import java.util.zip.*;

/** Timestamp-disjoint pixels exercise the obstructed coin UI and safe input path. */
public final class Video6720ReplayTest {
 static final String P=CoinPreset.P;static int checks;static Map<String,float[]> refs;
 static final Map<String,VirtualPresetProfileTest.Image> images=new LinkedHashMap<>();
 static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 static void load()throws Exception{
  refs=VirtualPresetProfileTest.canonical("tests/video6720-references.bin");
  try(DataInputStream in=new DataInputStream(new GZIPInputStream(new FileInputStream("tests/video6720-replay.bin.gz")))){
   int count=in.readInt();for(int i=0;i<count;i++)images.put(in.readUTF(),new VirtualPresetProfileTest.Image(in));
   check(count==47&&in.read()==-1,"all holdout frames loaded");
  }
  for(String training:new String[]{"19","25","31","35.8","65","77","80","82"})check(!images.containsKey(training),"training timestamp excluded: "+training);
 }
 static final class Run {
  final PhaseEngine e=new PhaseEngine();final CoinLayout layout;final CoinMissionJourney journey=new CoinMissionJourney();Map<String,float[]> last;
  Run(){CoinPreset.install(e,refs);e.strictRuntime=true;layout=CoinLayout.install(e,refs);AdCloseAutomaton.install(e,CoinPreset.APP);check(layout!=null&&e.validate().isEmpty(),"valid runtime factory graph");e.enter(P+"check",0);}
  Map<String,float[]> sample(String id,long at){VirtualPresetProfileTest.Image im=images.get(id);return last=layout.sampleRelative(e.activeRegions(),im.width,im.height,im,at);}
  PhaseEngine.Watch event(String first,String second,long at){e.observe(sample(first,at),at);check(e.poll()==null,"first frame holds "+e.current+" / "+first);e.observe(sample(second,at+350),at+350);return e.poll();}
  PhaseEngine.Watch expect(String first,String second,long at,String next,int action){
   PhaseEngine.Watch w=event(first,second,at);check(w!=null&&w.nextPhase.equals(P+next)&&w.action==action,"observed "+e.current+" / "+first+" -> "+next+" / "+(w==null?e.diagnostic:w.id));
   if(action==PhaseEngine.CENTER){VirtualPresetProfileTest.Image im=images.get(second);check(e.confirmsAction(w,e.current,e.epoch,sample(second,at+400)),"input preflight rechecks its own evidence");check(layout.pointRelative(w,last,im.width,im.height)!=null,"confirmed action has coordinates");}
   return w;
  }
  void finish(PhaseEngine.Watch w,long at){e.finish(w,at);journey.completed(e,w,at);}
  void advance(String first,String second,long at,String next,int action){finish(expect(first,second,at,next,action),at+450);}
 }
 static void scenes(){
  for(String state:new String[]{"check","verify","main"})for(String[] row:new String[][]{{"18.5","19.5","boost-rare"},{"30.5","31.5","boost-pocket"},{"24","24.8","fever"},{"36.2","36.5","clear"},{"77.3","78","missions"}}){
   Run r=new Run();r.e.enter(P+state,0);r.expect(row[0],row[1],1000,row[2],PhaseEngine.WAIT);
  }
  Run r=new Run();r.e.enter(P+"boost-rare",0);PhaseEngine.Watch w=r.expect("18.5","19.5",1000,"optional-cancel-wait",PhaseEngine.BACK);check(r.layout.pointRelative(w,r.last,540,1212)==null,"rare boost never supplies a CM target");
  r=new Run();r.e.enter(P+"bonus",0);w=r.expect("66","69",1000,"verify",PhaseEngine.CENTER);VirtualPresetProfileTest.Image im=images.get("69");float[] point=r.layout.pointRelative(w,r.last,im.width,im.height);
  check(point[0]/im.width>.595f&&point[0]/im.width<.675f&&point[1]/im.height>.775f&&point[1]/im.height<.797f,"resume uses visible right letters below CM, away from marker");
  check(Math.hypot(point[0]-.5f*im.width,point[1]-CoinPreset.MAIN_Y*im.height)>.10f*im.width,"resume remains outside the central marker");
  check(r.layout.pointRelative(w,r.last,im.width+1,im.height)==null,"old image size invalidates input coordinates");
 }
 static void chronology(){
  Run r=new Run();r.advance("15","17",1000,"main",PhaseEngine.WAIT);
  r.advance("18.5","19.5",2000,"boost-rare",PhaseEngine.WAIT);r.advance("18.5","19.5",3000,"optional-cancel-wait",PhaseEngine.BACK);
  r.e.enter(P+"verify",3500);r.advance("24","24.8",4000,"fever",PhaseEngine.WAIT);check(CoinPreset.playable(r.e,r.last),"confirmed fever permits coin input while its heading is partly covered");
  r.e.enter(P+"check",4500);r.advance("36.2","36.5",5000,"clear",PhaseEngine.WAIT);check(r.journey.blocksMain(),"clear retains mission collection intent");
  r.advance("38","39",6000,"mission-bonus",PhaseEngine.WAIT);r.advance("38","39",7000,"mission-opening",PhaseEngine.CENTER);
  r.advance("75","85",8000,"mission-tap",PhaseEngine.WAIT);r.advance("75","85",9000,"mission-opening",PhaseEngine.CENTER);
  r.advance("77.3","78",10000,"missions",PhaseEngine.WAIT);PhaseEngine.Watch get=r.expect("77.3","78",11000,"claim-wait",PhaseEngine.CENTER);check(get.id.equals(P+"get-tap"),"unclaimed Get wins over Play");r.finish(get,11450);
  r.advance("80.5","81",12000,"reward",PhaseEngine.WAIT);r.advance("80.5","81",13000,"mission-opening",PhaseEngine.CENTER);
  r.advance("82.3","82.6",14000,"missions",PhaseEngine.WAIT);PhaseEngine.Watch play=r.expect("82.3","82.6",15000,"mission-verify",PhaseEngine.CENTER);check(play.id.equals(P+"mission-play"),"after claim, actual Play restarts the game");r.finish(play,15450);check(!r.journey.blocksMain(),"Play acknowledgement releases collection intent");
  r.advance("85","89",16000,"main",PhaseEngine.WAIT);check(CoinPreset.playable(r.e,r.last),"bright home re-enables marker loop");
 }
 static void negatives(){
  for(String id:new String[]{"0","5","44","50","55","61","64","79","100","111"}){
   Run r=new Run();PhaseEngine.Watch w=r.event(id,id,1000);check(w==null||AdCloseAutomaton.handles(w.regionId),"ad/countdown/dim frame is never a game action: "+id);check(!CoinPreset.playable(r.e,r.last)&&!r.last.containsKey(AdaptiveLayout.MAIN),"unconfirmed pixels cannot enable markers: "+id);
  }
  for(String key:new String[]{"fever","boost-rare","missions","play"})for(int anchor=0;anchor<2;anchor++){
   String id=key.equals("fever")?"24.8-native":key.equals("boost-rare")?"19.5-native":key.equals("play")?"82.3-native":"77.3-native";VirtualPresetProfileTest.Image im=images.get(id);Run r=new Run();r.e.enter(P+(key.equals("play")?"missions":key),0);
   int[] box=RegionMath.bounds(CoinControlEvidence.anchors(key)[anchor],im.width,im.height);int[] icon=key.equals("boost-rare")&&anchor==1?RegionMath.bounds(CoinControlEvidence.identityAnchors(key)[1],im.width,im.height):box;RegionSampler.Pixels missing=(x,y)->(x>=box[0]-3&&x<box[2]+3&&y>=box[1]-3&&y<box[3]+3)||(x>=icon[0]-3&&x<icon[2]+3&&y>=icon[1]-3&&y<icon[3]+3)?0xff303030:im.get(x,y);
   Map<String,float[]> s=r.layout.sampleRelative(r.e.activeRegions(),im.width,im.height,missing,1000);String rule=key.equals("fever")?"verify-fever":key.equals("boost-rare")?"boost-rare-cm":key.equals("play")?"mission-play":"mission-anchor";PhaseEngine.Watch w=r.e.rule(P+rule);
   check(w.distance(s.get(P+key))>w.threshold,"both independent landmarks required: "+key+" / "+anchor);
  }
  Run r=new Run();r.e.enter(P+"bonus",0);PhaseEngine.Watch w=r.expect("38","39",1000,"verify",PhaseEngine.CENTER);Map<String,float[]> changed=r.sample("100",1800);check(!r.e.confirmsAction(w,r.e.current,r.e.epoch,changed)&&r.layout.pointRelative(w,changed,540,1212)==null,"newly appeared ad cancels pending Start");
 }
 static void scanAndOwnership(){
  for(String id:new String[]{"24.8-native","36.5-native","77.3-native","80.5-native","82.3-native","64"}){
   Run r=new Run();VirtualPresetProfileTest.Image im=images.get(id);List<PhaseEngine.Region> regions=r.e.activeRegions();for(PhaseEngine.Region region:BancAutonomy.recoveryRegions(r.e))if(!regions.contains(region))regions.add(region);
   RegionSampler.Pixels strict=FrameSampleCache.wrap(im.width,im.height,new TilePixels(im.width,im.height,(x,y,w,h,p,stride)->{for(int iy=0;iy<h;iy++)for(int ix=0;ix<w;ix++){if(x+ix<0||x+ix>=im.width||y+iy<0||y+iy>=im.height)throw new IndexOutOfBoundsException();p[iy*stride+ix]=im.get(x+ix,y+iy);}},64));
   Map<String,float[]> s=r.layout.sampleRelative(regions,im.width,im.height,strict,1000);check(s!=null&&!s.isEmpty(),"expanded recovery scan with production tile cache completes: "+id);
  }
  Run r=new Run();PhaseEngine edited=r.e.runtimeCopy();edited.rule(P+"start-tap").threshold=.001f;CoinLayout custom=CoinLayout.install(edited,refs);check(!edited.rule(P+"start-tap").runtimeControlEvidence,"edited threshold cannot accept new factory evidence");
  check(custom!=null&&!CoinNoAdPolicy.allowsUi(CoinPreset.APP,"CMを見て10ゲット","コインアップチャンス"),"UI autonomy excludes optional CM");
 }
 public static void main(String[] args)throws Exception{load();scenes();System.out.println("6720 scene checks complete");chronology();System.out.println("6720 chronological checks complete");negatives();scanAndOwnership();System.out.println("Video6720ReplayTest: "+checks+" checks passed; physical device input and device-only exception cause remain unverified");}
}
