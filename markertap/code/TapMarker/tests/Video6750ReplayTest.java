package jp.tapmarker;

import java.io.*;
import java.util.*;
import java.util.zip.*;

/** Unmodified recording pixels and old shipped templates. Resizing, occlusion,
 * window offsets and input completion below are explicitly synthetic. */
public final class Video6750ReplayTest {
 static final String P=UsagiPreset.PREFIX,TAP=P+"ミッション中央をタップ";
 static int checks;
 static Map<String,float[]> refs,evidence,targets;
 static final Map<String,VirtualPresetProfileTest.Image> images=new LinkedHashMap<>();
 static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 static void load()throws Exception{
  refs=VirtualPresetProfileTest.canonical("tests/usagi-replay.bin");
  evidence=VirtualPresetProfileTest.canonical("tests/current-layout-evidence.bin");
  targets=VirtualPresetProfileTest.canonical("tests/visual-targets.bin");
  try(DataInputStream in=new DataInputStream(new GZIPInputStream(new FileInputStream("tests/video6750-replay.bin.gz")))){
   int n=in.readInt();for(int i=0;i<n;i++)images.put(in.readUTF(),new VirtualPresetProfileTest.Image(in));
   check(n==27&&in.read()==-1,"all original recording frames are present");
  }
 }
 static final class Run {
  final PhaseEngine e=new PhaseEngine();final AdaptiveLayout layout;Map<String,float[]> samples;
  Run(){UsagiPreset.install(e,refs);e.strictRuntime=true;layout=AdaptiveLayout.install(e,UsagiPreset.APP,refs,evidence);layout.configureTargets(UsagiPreset.APP,targets);e.enter(P+"mission-tap",0);}
  Map<String,float[]> sample(int w,int h,RegionSampler.Pixels pixels,long now,int[] window,List<PhaseEngine.Region> regions){return samples=layout.sampleRelative(regions,w,h,pixels,now,window);}
  PhaseEngine.Watch observe(VirtualPresetProfileTest.Image im,long now){sample(im.width,im.height,im,now,null,e.activeRegions());e.observe(samples,now);return e.poll();}
  float[] point(int w,int h){return layout.pointRelative(e.rule(TAP),samples,w,h);}
 }
 static void recordedControls(){
  for(String id:new String[]{"1.5","10.5","42","44","46","55","58","61","64","66","74","78","80","82","88","90","92","80-native"}){
   VirtualPresetProfileTest.Image im=images.get(id);Run r=new Run();
   check(r.observe(im,1000)==null,"mission branch requires two observations: "+id);
   PhaseEngine.Watch w=r.observe(im,1350);
   check(w!=null&&w.id.equals(TAP)&&w.action==PhaseEngine.CENTER,"visible game requests its actual mission control: "+id);
   float[] point=r.point(im.width,im.height);
   check(point!=null,"the real mission control has a fresh coordinate: "+id);
   check(point[0]>.35f*im.width&&point[0]<.55f*im.width&&point[1]>.085f*im.height&&point[1]<.115f*im.height,"point lies within the observed white mission pill: "+id);
   String before=r.e.current;long epoch=r.e.epoch;
   Map<String,float[]> fresh=r.sample(im.width,im.height,im,1450,null,ScanPlan.select(r.e,w.regionId,true));
   check(r.e.confirmsAction(w,before,epoch,fresh)&&r.point(im.width,im.height)!=null,"minimal dispatch preflight retains the independent control: "+id);
   check(r.e.finishIfCurrent(w,before,epoch,1500)&&r.e.current.equals(P+"mission-opening"),"input acknowledgement only begins screen confirmation: "+id);
   check(!r.e.phases.get(r.e.current).markersDuring,"mission confirmation holds normal tapping: "+id);
   check(r.observe(im,1600)==null,"an unchanged game needs fresh confirmation before retry: "+id);
   w=r.observe(im,1950);check(w!=null&&w.nextPhase.equals(P+"mission-tap"),"a game that did not open missions retries instead of claiming success: "+id);
  }
 }
 static void missingOrInterrupted(){
  VirtualPresetProfileTest.Image home=images.get("80");Run r=new Run();r.observe(home,1000);r.observe(home,1350);check(r.point(home.width,home.height)!=null,"occlusion case starts with a usable coordinate");
  RegionSampler.Pixels absent=(x,y)->x>=home.width*.32f&&x<home.width*.58f&&y>=home.height*.075f&&y<home.height*.13f?0xff88cd66:home.get(x,y);
  r.sample(home.width,home.height,absent,3000,null,r.e.activeRegions());check(r.point(home.width,home.height)==null,"a disappeared control revokes the previous coordinate even though game anchors remain: "+Arrays.toString(r.point(home.width,home.height))+" / "+(r.layout.relocator.tracks.get(UsagiPreset.APP+":mission")==null?"nearby":Arrays.toString(r.layout.relocator.tracks.get(UsagiPreset.APP+":mission").bounds)));
  RegionSampler.Pixels noFlame=(x,y)->x>=home.width*.35f&&x<home.width*.405f&&y>=home.height*.086f&&y<home.height*.112f?0xffffffff:home.get(x,y);
  r=new Run();r.sample(home.width,home.height,noFlame,1000,null,r.e.activeRegions());check(r.point(home.width,home.height)==null,"mission letters without their flame cannot substitute for the control");
  RegionSampler.Pixels noLetters=(x,y)->x>=home.width*.412f&&x<home.width*.548f&&y>=home.height*.087f&&y<home.height*.111f?0xffffffff:home.get(x,y);
  r=new Run();r.sample(home.width,home.height,noLetters,1000,null,r.e.activeRegions());check(r.point(home.width,home.height)==null,"an isolated flame without the mission letters cannot substitute for the control");
  for(String id:new String[]{"18","28","50","54","72","84.5","85.5","72-native"}){
   VirtualPresetProfileTest.Image im=images.get(id);r=new Run();r.observe(im,1000);PhaseEngine.Watch event=r.observe(im,1350);
   check(r.point(im.width,im.height)==null,"advertisement, modal or mission page has no main-header input: "+id);
   check(event==null||!event.id.equals(TAP),"interruption cannot dispatch a mission-header tap: "+id);
  }
 }
 static void geometry(){
  VirtualPresetProfileTest.Image nativeImage=images.get("80-native");
  for(int[] size:new int[][]{{280,629},{360,808},{720,1616}})for(boolean smooth:new boolean[]{false,true}){
   Run r=new Run();int w=size[0],h=size[1];RegionSampler.Pixels resized=FarmDialogReplayTest.resize(nativeImage,w,h,smooth);
   r.sample(w,h,resized,1000,null,r.e.activeRegions());float[] p=r.point(w,h);
   check(p!=null&&p[0]>.35f*w&&p[0]<.55f*w&&p[1]>.085f*h&&p[1]<.115f*h,"fresh control survives synthetic framebuffer resizing: "+w+" / "+smooth+" / "+Arrays.toString(p)+" / "+Arrays.toString(r.layout.transform));
  }
  VirtualPresetProfileTest.Image im=images.get("80");int left=103,top=71,w=900,h=1500;
  RegionSampler.Pixels moved=(x,y)->x>=left&&x<left+im.width&&y>=top&&y<top+im.height?im.get(x-left,y-top):0xff34384b;
  Run r=new Run();r.sample(w,h,moved,1000,new int[]{left,top,left+im.width,top+im.height},r.e.activeRegions());float[] p=r.point(w,h);
  check(p!=null&&p[0]>left+.35f*im.width&&p[0]<left+.55f*im.width&&p[1]>top+.085f*im.height&&p[1]<top+.115f*im.height,"selected-window offset is applied once");
 }
 public static void main(String[] args)throws Exception{
  load();recordedControls();missingOrInterrupted();geometry();
  System.out.println("Video6750ReplayTest: "+checks+" checks passed; 27 held-out recording frames, unchanged shipped templates, mission preflight and retry, synthetic missing controls/resizing/window offsets; Android delivery and actual rewards untested");
 }
}
