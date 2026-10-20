package jp.tapmarker;
import java.io.*;import java.util.*;import java.util.zip.GZIPInputStream;

/** Full native-resolution frames; shape references trained only at 12 and 22 seconds. */
public final class Video6669ReplayTest {
 static int checks,holdouts,regressions,frames;static boolean probe;static Map<String,float[]> refs,evidence,legacy;
 static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 static float[] reference(String file)throws Exception{try(DataInputStream in=new DataInputStream(new FileInputStream(file))){float[] data=new float[in.readInt()];for(int i=0;i<data.length;i++)data[i]=in.readFloat();check(in.read()==-1,"complete reference: "+file);return data;}}
 static final class Runtime {
  PhaseEngine e=new PhaseEngine();AdaptiveLayout layout;
  Runtime(Map<String,float[]> images){FarmPreset.install(e,refs);e=e.runtimeCopy();layout=AdaptiveLayout.install(e,FarmPreset.APP,refs,images);AdCloseAutomaton.install(e,FarmPreset.APP);e.strictRuntime=true;e.enter(FarmPreset.P+"verify",0);}
  Map<String,float[]> sample(VirtualPresetProfileTest.Image image,RegionSampler.Pixels pixels,long now){return layout.sample(e.activeRegions(),image.width,image.height,pixels,now);}
 }
 static void frame(String name,VirtualPresetProfileTest.Image image){
  double seconds=Double.parseDouble(name);Runtime before=new Runtime(legacy),after=new Runtime(evidence);
  Map<String,float[]> old=before.sample(image,image,1000),fresh=after.sample(image,image,1000);
  boolean wasHome=FarmPreset.home(before.e,old),home=FarmPreset.home(after.e,fresh);
  PhaseEngine.Watch gate=after.e.rule(FarmPreset.P+"verify-home");
  System.out.println("6669 "+name+"s: home="+home+", legacy="+wasHome+", distance="+gate.distance(fresh.get(gate.regionId)));
  if(probe){frames++;return;}
  boolean held=seconds==17||seconds==18||seconds==24||seconds==54||seconds==55;
  check(home!=held,"recorded stable farm/fever versus ad, browser and window animation at "+name);
  check(image.width==1080&&image.height==2424,"recorded native resolution at "+name);
  if(home){
   if(seconds!=12&&seconds!=22)holdouts++;if(!wasHome)regressions++;
   check(RecoveryRequest.visibleBranch(after.e,fresh),"real main-screen evidence cancels delayed return at "+name);
   after.e.observe(fresh,1000);check(after.e.poll()==null,"one frame cannot enable harvest at "+name);
   fresh=after.sample(image,image,1200);after.e.observe(fresh,1200);PhaseEngine.Watch branch=after.e.poll();
   check(branch!=null&&branch.action==PhaseEngine.WAIT&&branch.nextPhase.equals(FarmPreset.P+"main"),"two recorded observations return to main at "+name);
   after.e.finish(branch,1250);check(after.e.current.equals(FarmPreset.P+"main"),"confirmed branch enters harvest at "+name);
   float[] marker=after.layout.defaultMarker(image.width*.5f,image.height*.76f,image.width,image.height,fresh);
   check(marker[0]/image.width>.46f&&marker[0]/image.width<.54f&&marker[1]/image.height>.74f&&marker[1]/image.height<.79f,"harvest point stays inside the button at "+name);
  }else{
   if(seconds==54)check(!RecoveryRequest.visibleBranch(after.e,fresh),"browser pixels cannot falsely cancel recovery at "+name);
   after.e.enter(FarmPreset.P+"main",0);fresh=after.sample(image,image,1000);after.e.observe(fresh,1000);check(after.e.poll()==null,"one loss frame holds main operations at "+name);after.e.observe(fresh,1200);PhaseEngine.Watch branch=after.e.poll();check(branch!=null&&branch.nextPhase.equals(FarmPreset.P+"check"),"external display leaves harvest after confirmation at "+name);
  }
  if(seconds==8){
   for(boolean removeHeader:new boolean[]{false,true}){
    Runtime missing=new Runtime(evidence);RegionSampler.Pixels pixels=(x,y)->removeHeader?(y<image.height*.25f?0xff111111:image.get(x,y)):(y>image.height*.70f&&y<image.height*.90f?0xff111111:image.get(x,y));
    check(!FarmPreset.home(missing.e,missing.sample(image,pixels,1000)),"both fixed help and harvest letters are necessary; removed "+(removeHeader?"help":"letters"));
   }
  }
  frames++;
 }
 public static void main(String[] args)throws Exception{
  probe=args.length>0&&args[0].equals("--probe");
  VirtualRecoveryReplayTest.load();VirtualRecoveryReplayTest.layoutEvidence();refs=VirtualPresetProfileTest.canonical("tests/farm-replay.bin");legacy=new HashMap<>(VirtualRecoveryReplayTest.evidence);evidence=new HashMap<>(legacy);
  evidence.put("farm-stable-home",reference("tests/video6669-stable-reference.bin"));evidence.put("farm-stable-pressed",reference("tests/video6669-pressed-reference.bin"));
  try(DataInputStream in=new DataInputStream(new GZIPInputStream(new FileInputStream("tests/video6669-replay.bin.gz")))){int count=in.readInt();for(int i=0;i<count;i++)frame(in.readUTF(),new VirtualPresetProfileTest.Image(in));check(in.read()==-1,"complete video replay");}
  if(probe)return;
  check(frames==26&&holdouts>=19,"separate training and holdout frames");check(regressions>=3,"replay reproduces and fixes the old fever mismatch");
  System.out.println("Video6669ReplayTest: "+checks+" checks passed; "+frames+" native frames, "+holdouts+" farm holdouts, "+regressions+" former misses corrected; no Android input dispatch tested");
 }
}
