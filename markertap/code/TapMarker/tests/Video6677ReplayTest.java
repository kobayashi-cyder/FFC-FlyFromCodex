package jp.tapmarker;
import java.io.*;import java.util.*;import java.util.zip.*;

/** Held-out gameplay, zero, ads and reward frames from the supplied 6677 video. */
public final class Video6677ReplayTest {
 static int checks;static Map<String,float[]> refs,evidence;
 static Map<String,VirtualPresetProfileTest.Image> images=new LinkedHashMap<>();
 static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 static void load()throws Exception{
  try(DataInputStream in=new DataInputStream(new FileInputStream("tests/video6677-references.bin"))){refs=UsagiPresetTest.read(in);evidence=UsagiPresetTest.read(in);check(in.read()==-1,"complete current reference data");}
  try(DataInputStream in=new DataInputStream(new GZIPInputStream(new FileInputStream("tests/video6677-replay.bin.gz")))){int n=in.readInt();for(int i=0;i<n;i++)images.put(in.readUTF(),new VirtualPresetProfileTest.Image(in));check(in.read()==-1,"complete recorded RGB fixture");}
 }
 static final class Run {
  final PhaseEngine e=new PhaseEngine();final AdaptiveLayout layout;
  Run(){UsagiPreset.install(e,refs);e.strictRuntime=true;layout=AdaptiveLayout.install(e,UsagiPreset.APP,refs,evidence);check(layout!=null,"runtime layout exists");}
  Map<String,float[]> sample(String id,long now){VirtualPresetProfileTest.Image im=images.get(id);return layout.sampleRelative(e.activeRegions(),im.width,im.height,im,now);}
 }
 static void gameplay(){
  for(String id:new String[]{"0001","0006","0021","0059","0079","0151"}){
   Run r=new Run();r.e.enter(UsagiPreset.PREFIX+"verify",0);
   Map<String,float[]> s=r.sample(id,1000);r.e.observe(s,1000);check(r.e.poll()==null,"first frame holds "+id);
   s=r.sample(id,1200);r.e.observe(s,1200);PhaseEngine.Watch w=r.e.poll();check(w!=null&&w.nextPhase.equals(UsagiPreset.PREFIX+"main"),"held-out moving/pressed game starts tapping "+id+" / "+r.e.diagnostic);
   VirtualPresetProfileTest.Image im=images.get(id);float[] p=r.layout.mainPointRelative(s,im.width,im.height);check(p!=null&&Math.abs(p[0]/im.width-.5f)<.02f&&Math.abs(p[1]/im.height-.78f)<.025f,"actual game tap target "+id);
   r.e.finish(w,1300);r.e.observe(r.sample(id,1400),1400);r.e.poll();r.e.observe(r.sample(id,1600),1600);w=r.e.poll();check(w==null||!w.nextPhase.equals(UsagiPreset.PREFIX+"open-missions")&&!w.nextPhase.equals(UsagiPreset.PREFIX+"clear"),"unfinished count and red badge never open missions "+id);
  }
 }
 static void completion(){
  Set<String> zero=new HashSet<>(Arrays.asList("0031","0032","0080","0118","0119","0173"));
  for(Map.Entry<String,VirtualPresetProfileTest.Image> row:images.entrySet()){
   VirtualPresetProfileTest.Image im=row.getValue();check(UsagiStageCompletion.visible(im.width,im.height,new float[]{1,AdaptiveLayout.NORMAL_Y,0,AdaptiveLayout.NORMAL_TOP},im,evidence.get("usagi-stage-zero"))==zero.contains(row.getKey()),"whole zero count agrees with manually labeled frame "+row.getKey());
  }
  for(String id:new String[]{"0031","0080","0118","0173"}){Run r=new Run();r.e.enter(UsagiPreset.PREFIX+"main",0);r.e.observe(r.sample(id,1000),1000);check(r.e.poll()==null,"zero needs a second fresh observation "+id);r.e.observe(r.sample(id,1200),1200);PhaseEngine.Watch w=r.e.poll();check(w!=null&&w.nextPhase.equals(UsagiPreset.PREFIX+"clear")&&w.action==PhaseEngine.WAIT,"real stage completion wins "+id);}
 }
 static void negatives(){
  for(String id:new String[]{"0008","0033","0081","0086","0120","0175","0190"}){Run r=new Run();r.e.enter(UsagiPreset.PREFIX+"verify",0);Map<String,float[]> s=r.sample(id,1000);PhaseEngine.Watch home=r.e.rule(UsagiPreset.PREFIX+"return");check(home.distance(s.get(home.regionId))>home.threshold,"ad, dimmed bonus, reward or missions cannot start main taps "+id);}
  Run r=new Run();VirtualPresetProfileTest.Image im=images.get("0021");r.e.enter(UsagiPreset.PREFIX+"verify",0);RegionSampler.Pixels missing=(x,y)->y>im.height*.83f&&y<im.height*.87f&&x<im.width*.33f?0xff101820:im.get(x,y);Map<String,float[]> s=r.layout.sampleRelative(r.e.activeRegions(),im.width,im.height,missing,1000);PhaseEngine.Watch home=r.e.rule(UsagiPreset.PREFIX+"return");check(home.distance(s.get(home.regionId))>home.threshold,"header alone cannot confirm a changed app UI");
  check(r.e.rule(UsagiPreset.PREFIX+"広告を使わず開始").nextPhase.equals(UsagiPreset.PREFIX+"verify"),"bonus action returns through visual verification");check(r.e.rule(UsagiPreset.PREFIX+"mission-notice")==null,"no badge-triggered mission branch exists");
 }
 static void smallFrames(){
  for(int[] size:new int[][]{{540,1211},{540,1140},{720,1520}})for(String id:new String[]{"0021","0059","0031","0080","0118","0173","0008","0086"}){
   Run r=new Run();VirtualPresetProfileTest.Image im=images.get(id);int width=size[0],height=size[1];RegionSampler.Pixels resized=(x,y)->im.get(Math.min(im.width-1,x*im.width/width),Math.min(im.height-1,y*im.height/height));r.e.enter(UsagiPreset.PREFIX+"verify",0);Map<String,float[]> s=r.layout.sampleRelative(r.e.activeRegions(),width,height,resized,1000);PhaseEngine.Watch home=r.e.rule(UsagiPreset.PREFIX+"return");boolean playable=!id.equals("0008")&&!id.equals("0086");check((home.distance(s.get(home.regionId))<=home.threshold)==playable,"recorded scene survives small/aspect-scaled sampling "+id+" "+Arrays.toString(size));
   if(playable){float[] point=r.layout.mainPointRelative(s,width,height);check(point!=null&&Math.abs(point[0]/width-.5f)<.03f&&Math.abs(point[1]/height-.78f)<.03f,"scaled main tap remains in button "+id);}
   boolean zero=Arrays.asList("0031","0080","0118","0173").contains(id);check(UsagiStageCompletion.visible(width,height,r.layout.transform,resized,r.layout.stageZeroReferences)==zero,"small frame does not change zero decision "+id+" "+Arrays.toString(size));
  }
 }
 public static void main(String[] args)throws Exception{load();gameplay();completion();negatives();smallFrames();System.out.println("Video6677ReplayTest: "+checks+" checks passed; held-out video pixels, completion gate and input coordinates; real device execution not tested");}
}
