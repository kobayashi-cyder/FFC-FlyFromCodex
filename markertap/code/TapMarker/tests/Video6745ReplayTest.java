package jp.tapmarker;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import javax.imageio.ImageIO;

/** Supplied preview pixels, plus explicitly synthetic resizing/occlusion and
 * input-completion sequences. This is not an Android device input test. */
public final class Video6745ReplayTest {
 static int checks;
 static final String ROOT="tests/fixtures/video6745/";
 static final Set<Integer> STORES=new HashSet<>(Arrays.asList(0,8,16,92,100,108,110));
 static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 static final class Image implements RegionSampler.Pixels {
  final int width,height;final int[] data;
  Image(String name)throws Exception{BufferedImage im=ImageIO.read(new File(ROOT+name));width=im.getWidth();height=im.getHeight();data=im.getRGB(0,0,width,height,null,0,width);}
  public int get(int x,int y){return data[y*width+x];}
 }
 static RegionSampler.Pixels scaled(Image image,int w,int h){return FrameSampleCache.wrap(w,h,(x,y)->image.get(Math.min(image.width-1,x*image.width/w),Math.min(image.height-1,y*image.height/h)));}
 static void suppliedPixels()throws Exception{
  File[] files=new File(ROOT).listFiles();check(files!=null&&files.length==48,"all supplied preview fixtures available");
  for(File f:files){Image im=new Image(f.getName());
   float[] error=FarmAdErrorEvidence.locate(im.width,im.height,im);
   check((error!=null)==f.getName().equals("farm-screenshot.png"),"only the actual ad-error dialog is an error: "+f.getName());
   boolean store=f.getName().startsWith("poipoi-")&&(f.getName().contains("screenshot")||STORES.contains(Integer.parseInt(f.getName().replace("poipoi-","").replace(".png",""))));
   float[] point=PlayStoreSheetEvidence.locate(im.width,im.height,im);
   check((point!=null)==store,"only the measured store sheet is an exit: "+f.getName());
   if(point!=null)check(point[0]>.92f*im.width&&point[0]<.95f*im.width&&point[1]>.21f*im.height&&point[1]<.25f*im.height,"point is on the visible sheet X: "+f.getName());
  }
 }
 static void farm()throws Exception{
  Image im=new Image("farm-screenshot.png");Map<String,float[]> refs=VirtualPresetProfileTest.canonical("tests/farm-replay.bin");
  PhaseEngine e=new PhaseEngine();FarmPreset.install(e,refs);e.enter(FarmPreset.P+"main",0);
  AdaptiveLayout layout=AdaptiveLayout.install(e,FarmPreset.APP,refs,VirtualPresetProfileTest.canonical("tests/current-layout-evidence.bin"));
  layout.configureTargets(FarmPreset.APP,VirtualPresetProfileTest.canonical("tests/visual-targets.bin"));FarmControlEvidence.install(e);
  RegionSampler.Pixels pixels=FrameSampleCache.wrap(im.width,im.height,im);
  GamePopupEvidence priority=layout.popupEvidence;layout.popupEvidence=null;
  Map<String,float[]> samples=layout.sampleRelative(e.activeRegions(),im.width,im.height,pixels,1000);
  check(FarmPreset.home(e,samples),"reproduces the old false-positive home under the modal");
  layout.popupEvidence=priority;
  Map<String,float[]> fast=layout.sampleRelative(e.activeRegions(),im.width,im.height,pixels,1100);
  check(!FarmPreset.home(e,fast)&&FarmAdErrorEvidence.point(fast,im.width,im.height)!=null,"priority popup recognition prevents the false home before detailed sampling");
  FarmAdErrorEvidence.sample(samples,im.width,im.height,pixels);
  check(!FarmPreset.home(e,samples),"positive modal evidence overrides the background home landmark");
  float[] p=FarmAdErrorEvidence.point(samples,im.width,im.height);
  check(p!=null&&p[0]>60&&p[0]<219&&p[1]>347&&p[1]<373,"actual error OK is inside its outlined button");
  FarmAdErrorRecovery lane=new FarmAdErrorRecovery(),other=new FarmAdErrorRecovery();
  lane.observe(true,1000,1050);check(!lane.mayTap(1050),"one frame cannot authorize OK");
  lane.observe(true,1200,1250);check(lane.mayTap(1250)&&!other.visible(),"two fresh frames authorize only this lane");lane.dispatched(1250);
  lane.observe(false,1500,1550);check(lane.visible(),"one missing frame cannot claim dismissal");
  lane.observe(false,1700,1750);check(lane.consumeRecovered()&&!lane.visible(),"two new frames confirm dismissal");
  for(int[] size:new int[][]{{280,629},{360,808},{540,1212},{720,1616},{1080,2424}}){
   int w=size[0],h=size[1];RegionSampler.Pixels scaled=scaled(im,w,h);p=FarmAdErrorEvidence.locate(w,h,scaled);
   check(p!=null&&p[0]>.45f*w&&p[0]<.55f*w&&p[1]>.55f*h&&p[1]<.60f*h,"synthetic resize retains actual OK: "+w);
   Map<String,float[]> frame=new HashMap<>();FarmAdErrorEvidence.sample(frame,w,h,scaled);
   check(FarmAdErrorEvidence.point(frame,w+1,h)==null,"another framebuffer geometry cannot reuse OK");
  }
 }
 static final class Run {
  final PhaseEngine e=new PhaseEngine();final AdaptiveLayout layout;final AdExitPolicy exits=new AdExitPolicy();long now=1000;Map<String,float[]> samples;
  Run(String state)throws Exception{Map<String,float[]> refs=VirtualPresetProfileTest.canonical("tests/image6727-references.bin");PoipoiPreset.install(e,refs);layout=PoipoiLayout.install(e,refs);AdCloseAutomaton.install(e,PoipoiPreset.APP);e.strictRuntime=true;e.enter(PoipoiPreset.P+state,0);}
  Map<String,float[]> sample(Image im,long at,List<PhaseEngine.Region> regions){return layout.sampleRelative(regions,im.width,im.height,FrameSampleCache.wrap(im.width,im.height,im),at);}
  PhaseEngine.Watch event(Image im){samples=sample(im,now,e.activeRegions());exits.filter(e,samples,im.width,im.height,now,now);e.observe(samples,now);now+=400;return e.poll();}
 }
 static void actualBranches()throws Exception{
  for(String image:new String[]{"poipoi-92.png","poipoi-screenshot.png"})for(String state:new String[]{"main","ad","mission-ad"}){
   Image im=new Image(image);Run r=new Run(state);
   check(r.event(im)==null,"first sheet image must be confirmed: "+state);
   PhaseEngine.Watch w=r.event(im);
   check(w!=null&&w.action==PhaseEngine.CENTER&&w.regionId.equals(AdCloseAutomaton.RIGHT),"registered X wins over generic recovery: "+image+" "+state);
   String before=r.e.current;long epoch=r.e.epoch,entered=r.e.enteredAt;
   Map<String,float[]> fresh=r.sample(im,r.now,ScanPlan.select(r.e,w.regionId,false));
   check(r.exits.preflight(w,fresh,im.width,im.height)&&r.e.confirmsAction(w,before,epoch,fresh),"minimal fresh preflight rechecks the heading and X");
   float[] p=AdCloseAutomaton.controlPoint(w.regionId,fresh,im.width,im.height);
   check(p!=null&&p[0]>.92f*im.width&&p[1]>.21f*im.height&&p[1]<.25f*im.height,"final dispatch point belongs to this sheet, below the old corner band");
   r.exits.controlSent(w,r.now);check(r.e.finishIfCurrent(w,before,epoch,r.now),"simulated matching input completion advances the branch");
   check(r.e.current.equals(PoipoiPreset.P+(state.equals("mission-ad")?"mission-ad":"ad")),"the prior ordinary/mission journey is retained");
   if(!state.equals("main"))check(r.e.enteredAt==entered,"closing another card retains the recovery deadline");
   check(!r.e.phases.get(r.e.current).markersDuring,"closing X does not prematurely resume game taps");
  }
 }
 static void geometryAndOcclusion()throws Exception{
  Image im=new Image("poipoi-92.png");
  for(int[] size:new int[][]{{280,629},{360,808},{540,1212},{720,1616},{1080,2424}}){
   int w=size[0],h=size[1];RegionSampler.Pixels pixels=scaled(im,w,h);float[] p=PlayStoreSheetEvidence.locate(w,h,pixels);
   check(p!=null&&p[0]>.92f*w&&p[0]<.95f*w&&p[1]>.21f*h&&p[1]<.25f*h,"synthetic framebuffer resize retains sheet X: "+w);
  }
  float[] point=PlayStoreSheetEvidence.locate(im.width,im.height,im);check(point!=null,"known sheet X");
  RegionSampler.Pixels noHeader=(x,y)->x<im.width*.35f&&Math.abs(y-point[1])<im.width*.04f?0xffffffff:im.get(x,y);
  RegionSampler.Pixels noX=(x,y)->x>im.width*.86f&&Math.abs(y-point[1])<im.width*.04f?0xffffffff:im.get(x,y);
  check(PlayStoreSheetEvidence.locate(im.width,im.height,noHeader)==null,"an isolated X without the Google Play heading cannot authorize exit");
  check(PlayStoreSheetEvidence.locate(im.width,im.height,noX)==null,"the heading without an actual X cannot authorize exit");
  check(PlayStoreSheetEvidence.locate(im.width,im.height,(x,y)->0xffffffff)==null,"a blank light panel is not an exit");
  Run r=new Run("ad");r.event(im);PhaseEngine.Watch w=r.event(im);check(w!=null,"preflight test has a pending sheet branch");
  Map<String,float[]> changed=r.layout.sampleRelative(ScanPlan.select(r.e,w.regionId,false),im.width,im.height,FrameSampleCache.wrap(im.width,im.height,noHeader),r.now);
  check(!r.exits.preflight(w,changed,im.width,im.height),"heading disappearing immediately before dispatch cancels X input");
  check(!r.e.finishIfCurrent(w,r.e.current,r.e.epoch+1,r.now),"stale lane completion cannot advance a replacement state");
 }
 public static void main(String[] args)throws Exception{suppliedPixels();farm();actualBranches();geometryAndOcclusion();System.out.println("Video6745ReplayTest: "+checks+" checks passed; 48 supplied preview crops, synthetic resize/occlusion, registered exits and independent dialog gates; Android input untested");}
}
