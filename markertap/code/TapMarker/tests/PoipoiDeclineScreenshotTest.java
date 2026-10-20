package jp.tapmarker;
import java.io.*;
import java.util.*;
import java.util.zip.*;

/** Reproduces supplied screenshot 6727; other layouts/occlusions are synthetic. */
public final class PoipoiDeclineScreenshotTest {
 static int checks;
 static Map<String,float[]> refs;
 static VirtualPresetProfileTest.Image dialog;
 static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 static final class Run {
  final PhaseEngine e=new PhaseEngine();final PoipoiLayout layout;
  Map<String,float[]> samples;long clock=1000;
  Run(String state){PoipoiPreset.install(e,refs);e.strictRuntime=true;layout=PoipoiLayout.install(e,refs);AdCloseAutomaton.install(e,PoipoiPreset.APP);e.enter(PoipoiPreset.P+state,0);}
  Map<String,float[]> sample(VirtualPresetProfileTest.Image im,long at){return samples=layout.sampleRelative(e.activeRegions(),im.width,im.height,im,at);}
  PhaseEngine.Watch event(VirtualPresetProfileTest.Image im){e.observe(sample(im,clock),clock);check(e.poll()==null,"first image requires confirmation: "+e.current);clock+=350;e.observe(sample(im,clock),clock);return e.poll();}
  void finish(PhaseEngine.Watch w){check(w!=null,"observed transition exists / "+e.current+" / "+e.diagnostic);e.finish(w,clock+20);clock+=500;}
 }
 static void load()throws Exception{
  refs=VirtualPresetProfileTest.canonical("tests/image6727-references.bin");
  try(DataInputStream in=new DataInputStream(new GZIPInputStream(new FileInputStream("tests/image6727-replay.bin.gz")))){check(in.readInt()==1&&in.readUTF().equals("6727"),"one original screenshot");dialog=new VirtualPresetProfileTest.Image(in);check(dialog.width==684&&dialog.height==1536&&in.read()==-1,"original size and all pixels preserved");}
  Video6725ReplayTest.load();
 }
 static void classifyAndTap(){
  for(String state:new String[]{"check","main","offline","offline-wait","bonus","reward","missions","mission-tap","mission-opening","claim-wait","mission-ad","mission-verify","ad","verify"}){
   Run r=new Run(state);boolean collecting=PoipoiPreset.missionPending(r.e.current);String target=collecting?"mission-decline":"decline";
   PhaseEngine.Watch w=r.event(dialog);
   check(w!=null&&w.action==PhaseEngine.WAIT&&w.nextPhase.equals(PoipoiPreset.P+target),"recognizes optional dialog from "+state+" / "+r.e.diagnostic);
   check(!PoipoiPreset.playable(r.e,r.samples),"dialog blocks ordinary taps: "+state);
   check(!r.samples.containsKey(AdHomeRecovery.EVIDENCE),"in-game banner does not authorize ad HOME: "+state);
   r.finish(w);check(!r.e.phases.get(r.e.current).markersDuring,"decline phase has no continuous input");
   w=r.event(dialog);check(w!=null&&w.id.equals(PoipoiPreset.P+target+"-tap")&&w.action==PhaseEngine.CENTER,"only a confirmed decline can produce input");
   float[] p=r.layout.pointRelative(w,r.samples,dialog.width,dialog.height);
   check(p!=null&&p[0]>=243&&p[0]<=445&&p[1]>=992&&p[1]<=1043,"touch lands on 使用しない, below yellow 使用する");
   Map<String,float[]> fresh=r.layout.sampleRelative(ScanPlan.select(r.e,w.regionId,false),dialog.width,dialog.height,dialog,r.clock+100);
   check(r.e.confirmsAction(w,r.e.current,r.e.epoch,fresh),"minimal preflight independently resamples both dialog parts");
   check(!r.e.confirmsAction(w,r.e.current,r.e.epoch+1,fresh),"stale state epoch blocks input");
   VirtualPresetProfileTest.Image ad=Video6725ReplayTest.images.get("6.8");
   fresh=r.layout.sampleRelative(ScanPlan.select(r.e,w.regionId,false),ad.width,ad.height,ad,r.clock+200);
   check(!r.e.confirmsAction(w,r.e.current,r.e.epoch,fresh),"screen replaced by ad cancels decline input");
  }
 }
 static void negatives(){
  for(int part=0;part<2;part++){
   final int[] b=RegionMath.bounds(PoipoiPreset.DECLINE[part],dialog.width,dialog.height);
   RegionSampler.Pixels absent=(x,y)->x>=b[0]-4&&x<b[2]+4&&y>=b[1]-4&&y<b[3]+4?0xffffffff:dialog.get(x,y);
   Run r=new Run("decline");Map<String,float[]> s=r.layout.sampleRelative(r.e.activeRegions(),dialog.width,dialog.height,absent,1000);
   PhaseEngine.Watch tap=r.e.rule(PoipoiPreset.P+"decline-tap");
   check(tap.distance(s.get(tap.regionId))>tap.threshold,"either missing illustration or missing decline word blocks tap: "+part);
   check(r.layout.pointRelative(tap,s,dialog.width,dialog.height)==null,"no unverified fallback coordinate: "+part);
   check(!r.e.confirmsAction(tap,r.e.current,r.e.epoch,s),"occlusion cancels preflight: "+part);
  }
  Run r=new Run("check");PhaseEngine.Watch w=r.e.rule(PoipoiPreset.P+"check-decline");
  for(Map.Entry<String,VirtualPresetProfileTest.Image> row:Video6725ReplayTest.images.entrySet()){
   VirtualPresetProfileTest.Image im=row.getValue();r.sample(im,2000+checks*1100L);
   check(w.distance(r.samples.get(w.regionId))>w.threshold,"existing video is not this dialog: "+row.getKey());
  }
 }
 static void geometry(){
  for(int[] size:new int[][]{{360,808},{540,1212},{720,1600},{1080,2424}}){
   final int width=size[0],height=size[1];RegionSampler.Pixels pixels=(x,y)->dialog.get(Math.min(dialog.width-1,x*dialog.width/width),Math.min(dialog.height-1,y*dialog.height/height));
   Run r=new Run("decline");Map<String,float[]> s=r.layout.sampleRelative(r.e.activeRegions(),width,height,pixels,1000);PhaseEngine.Watch tap=r.e.rule(PoipoiPreset.P+"decline-tap");
   check(r.e.confirmsAction(tap,r.e.current,r.e.epoch,s),"scaled screenshot recognized: "+width+"x"+height);
   float[] p=r.layout.pointRelative(tap,s,width,height);check(p!=null&&p[0]>.35f*width&&p[0]<.65f*width&&p[1]>.645f*height&&p[1]<.68f*height,"scaled point remains in decline text");
  }
  final int left=150,top=100,width=1000,height=1800;
  RegionSampler.Pixels desktop=(x,y)->x>=left&&x<left+dialog.width&&y>=top&&y<top+dialog.height?dialog.get(x-left,y-top):0xff222222;
  Run r=new Run("decline");Map<String,float[]> s=r.layout.sampleRelative(r.e.activeRegions(),width,height,desktop,1000,new int[]{left,top,left+dialog.width,top+dialog.height});PhaseEngine.Watch tap=r.e.rule(PoipoiPreset.P+"decline-tap");
  check(r.e.confirmsAction(tap,r.e.current,r.e.epoch,s),"dialog in an explicitly selected window is recognized");
  float[] p=r.layout.pointRelative(tap,s,width,height);check(p!=null&&p[0]>left+243&&p[0]<left+445&&p[1]>top+992&&p[1]<top+1043,"window offset is applied to decline point");
  RegionSampler.Pixels newPanel=(x,y)->x>=100&&x<580&&y>=340&&y<640?0xff556677:dialog.get(x,y);
  r=new Run("decline");s=r.layout.sampleRelative(r.e.activeRegions(),dialog.width,dialog.height,newPanel,1000);tap=r.e.rule(PoipoiPreset.P+"decline-tap");
  check(r.e.confirmsAction(tap,r.e.current,r.e.epoch,s),"title and acceptance-button overlay changes do not affect the two visible anchors");
 }
 static void step(Run r,VirtualPresetProfileTest.Image im,String id){PhaseEngine.Watch w=r.event(im);check(w!=null&&w.id.equals(PoipoiPreset.P+id),"flow step "+id+" / "+r.e.current+" / "+r.e.diagnostic);r.finish(w);}
 static void journeys(){
  VirtualPresetProfileTest.Image game=Video6725ReplayTest.images.get("16.0");
  for(boolean collecting:new boolean[]{false,true}){
   Run r=new Run(collecting?"claim-wait":"main");String id=collecting?"mission-decline":"decline";
   step(r,dialog,(collecting?"claim-wait":"main")+"-decline");step(r,dialog,id+"-tap");
   check(r.event(dialog)==null,"still-visible dialog does not immediately tap twice");
   step(r,game,id+"-wait-gone");check(r.e.current.equals(PoipoiPreset.P+(collecting?"mission-verify":"verify")),"disappearance returns to the correct verification state");
   step(r,game,(collecting?"mission-verify":"verify")+"-game");
   check(collecting?r.e.current.equals(PoipoiPreset.P+"mission-tap"):PoipoiPreset.playable(r.e,r.samples),"synthetic post-decline journey resumes prior intent");
  }
  Run r=new Run("decline");step(r,dialog,"decline-tap");long entered=r.e.enteredAt;
  r.e.observe(r.sample(dialog,entered+1499),entered+1499);check(r.e.poll()==null,"wait period cannot send another input");
  r.e.observe(r.sample(dialog,entered+1501),entered+1501);PhaseEngine.Watch retry=r.e.poll();
  check(retry!=null&&retry.action==PhaseEngine.WAIT&&retry.nextPhase.equals(PoipoiPreset.P+"decline"),"still-visible dialog retries classification, not a blind tap");r.clock=entered+1501;r.finish(retry);step(r,dialog,"decline-tap");
 }
 static void migration(){
  PhaseEngine old=PoipoiPreset.version191Defaults(refs);old.current=PoipoiPreset.P+"claim-wait";
  check(PoipoiPreset.upgrade(old,refs),"untouched v1.91 standard graph upgrades automatically");
  PhaseEngine current=new PhaseEngine();PoipoiPreset.install(current,refs);
  check(AutomatonGraph.sameConfiguration(old,current),"upgrade produces the complete current standard graph");
  check(old.current.equals(PoipoiPreset.P+"claim-wait"),"upgrade preserves current mission intent");
  check(!PoipoiPreset.upgrade(old,refs),"repeated upgrade is a no-op");check(old.validate().isEmpty(),"upgraded graph validates");
  for(int change=0;change<6;change++){
   PhaseEngine custom=PoipoiPreset.version191Defaults(refs);
   switch(change){case 0:custom.rule(PoipoiPreset.P+"get-tap").action=PhaseEngine.WAIT;break;case 1:custom.regions.get(PoipoiPreset.P+"game").bounds[0]+=.01f;break;case 2:custom.rule(PoipoiPreset.P+"verify-game").reference[0]+=.01f;break;case 3:custom.phases.get(PoipoiPreset.P+"main").name+="custom";break;case 4:custom.windowRelative=true;break;case 5:custom.rule(PoipoiPreset.P+"get-tap").nextPhase=PoipoiPreset.P+"verify";break;}
   PhaseEngine before=custom.runtimeCopy();check(!PoipoiPreset.upgrade(custom,refs),"custom graph is preserved: "+change);check(AutomatonGraph.sameConfiguration(custom,before),"declined migration changes nothing: "+change);
  }
  for(String key:new String[]{"decline","decline-wait","mission-decline","mission-decline-wait"})check(!current.phases.get(PoipoiPreset.P+key).markersDuring,"all decline states hold continuous taps");
 }
 public static void main(String[] args)throws Exception{load();classifyAndTap();negatives();geometry();journeys();migration();System.out.println("PoipoiDeclineScreenshotTest: "+checks+" checks passed; supplied 6727 reproduction, both anchors, decline-only target, preflight, resize/window, retained mission intent and v1.91 migration; one real screenshot, synthetic variations and post-decline journey, physical input untested");}
}
