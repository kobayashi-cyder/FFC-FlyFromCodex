package jp.tapmarker;
import java.io.*;import java.util.*;import java.util.zip.*;

/** Reported F-52A recording: native home pixels and real interruption frames. */
public final class Video6680ReplayTest {
 static int checks;static Map<String,float[]> evidence,usagiRefs,farmRefs;
 static Map<String,VirtualPresetProfileTest.Image> images=new LinkedHashMap<>();
 static final Set<String> USAGI=new HashSet<>(Arrays.asList("0","0.5","1","3","14","17"));
 static final Set<String> FARM=new HashSet<>(Arrays.asList("63","80","168","173"));
 static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 static void load()throws Exception{
  usagiRefs=VirtualPresetProfileTest.canonical("tests/usagi-replay.bin");farmRefs=VirtualPresetProfileTest.canonical("tests/farm-replay.bin");
  try(DataInputStream in=new DataInputStream(new FileInputStream("tests/video6680-references.bin"))){evidence=UsagiPresetTest.read(in);check(in.read()==-1,"complete current layout references");}
  try(DataInputStream in=new DataInputStream(new GZIPInputStream(new FileInputStream("tests/video6680-replay.bin.gz")))){int n=in.readInt(),nativeFrames=0;for(int i=0;i<n;i++){String id=in.readUTF();VirtualPresetProfileTest.Image im=new VirtualPresetProfileTest.Image(in);images.put(id,im);if(im.width==1080&&im.height==2280)nativeFrames++;}check(n==22&&nativeFrames==4&&in.read()==-1,"twenty-two complete frames, four retain native F-52A raster");}
 }
 static final class Run {
  final PhaseEngine e=new PhaseEngine();final AdaptiveLayout layout;final String prefix;final boolean usagi;
  Run(boolean usagi){this.usagi=usagi;prefix=usagi?UsagiPreset.PREFIX:FarmPreset.P;if(usagi)UsagiPreset.install(e,usagiRefs);else FarmPreset.install(e,farmRefs);layout=AdaptiveLayout.install(e,usagi?UsagiPreset.APP:FarmPreset.APP,usagi?usagiRefs:farmRefs,evidence);AdCloseAutomaton.install(e,usagi?UsagiPreset.APP:FarmPreset.APP);e.strictRuntime=true;e.enter(prefix+"verify",0);check(layout!=null,"actual runtime layout exists");}
  Map<String,float[]> sample(VirtualPresetProfileTest.Image im,long now){return layout.sampleRelative(e.activeRegions(),im.width,im.height,im,now);}
  PhaseEngine.Watch home(){return e.rule(prefix+(usagi?"return":"verify-home"));}
 }
 static void scenes(){
  for(boolean usagi:new boolean[]{true,false})for(Map.Entry<String,VirtualPresetProfileTest.Image> row:images.entrySet()){
   Run r=new Run(usagi);Map<String,float[]> s=r.sample(row.getValue(),1000);boolean ready=r.home().distance(s.get(r.home().regionId))<=r.home().threshold;check(ready==(usagi?USAGI:FARM).contains(row.getKey()),"manually inspected game/ad/browser/loading/fading scene: "+usagi+" / "+row.getKey()+" distance="+r.home().distance(s.get(r.home().regionId)));
  }
 }
 static void starts(){
  for(boolean usagi:new boolean[]{true,false})for(String id:usagi?USAGI:FARM){Run r=new Run(usagi);VirtualPresetProfileTest.Image im=images.get(id);Map<String,float[]> s=r.sample(im,1000);r.e.observe(s,1000);check(r.e.poll()==null,"first game observation waits: "+id);s=r.sample(im,1200);r.e.observe(s,1200);PhaseEngine.Watch w=r.e.poll();check(w!=null&&w.nextPhase.equals(r.prefix+"main"),"actual recorded main screen starts the marker state: "+usagi+" / "+id+" / "+r.e.diagnostic);float[] point=r.layout.mainPointRelative(s,im.width,im.height);check(point!=null&&Math.abs(point[0]/im.width-.5f)<.03f&&point[1]/im.height>(usagi?.74f:.71f)&&point[1]/im.height<(usagi?.825f:.83f),"input is inside the actually visible orange or green main button: "+id);float[] marker=r.layout.defaultMarkerRelative(im.width*.5f,im.height*(usagi?.78f:.76f),im.width,im.height,s);check(marker!=null&&Arrays.equals(marker,point),"default marker uses the same recognized frame coordinates");r.e.finish(w,1300);check(r.e.phases.get(r.e.current).markersDuring,"confirmed main state permits its markers");
   if(usagi){check(!UsagiStageCompletion.visible(im.width,im.height,r.layout.transform,im,r.layout.stageZeroReferences),"1586/1585 is not a zero completion: "+id);r.e.observe(r.sample(im,1400),1400);r.e.poll();r.e.observe(r.sample(im,1600),1600);w=r.e.poll();check(w==null||!w.nextPhase.equals(r.prefix+"open-missions")&&!w.nextPhase.equals(r.prefix+"clear")&&!w.nextPhase.equals(r.prefix+"classify"),"unfinished badge neither opens missions nor loses the known main screen: "+id);}
  }
 }
 static void unavailable(){
  VirtualPresetProfileTest.Image im=images.get("0");
  for(int kind=0;kind<3;kind++){final int mode=kind;RegionSampler.Pixels pixels=(x,y)->{int c=im.get(x,y);if(mode==2)return 0xff000000|((c>>16&255)*3/5)<<16|((c>>8&255)*3/5)<<8|(c&255)*3/5;boolean absent=mode==0?x>im.width*.58f&&x<im.width*.84f&&y>im.height*.02f&&y<im.height*.13f:x>im.width*.1f&&x<im.width*.73f&&y>im.height*.66f&&y<im.height*.91f;return absent?0xff333333:c;};Run r=new Run(true);Map<String,float[]> s=r.layout.sampleRelative(r.e.activeRegions(),im.width,im.height,pixels,1000);check(r.home().distance(s.get(r.home().regionId))>r.home().threshold,"heading alone, lower controls alone or a dimmed game cannot start tapping: "+kind);}
 }
 static void geometry(){
  VirtualPresetProfileTest.Image im=images.get("0");
  for(int[] size:new int[][]{{444,994},{540,1140},{720,1520}})for(boolean smooth:new boolean[]{false,true}){Run r=new Run(true);RegionSampler.Pixels pixels=FarmDialogReplayTest.resize(im,size[0],size[1],smooth);Map<String,float[]> s=r.layout.sampleRelative(r.e.activeRegions(),size[0],size[1],pixels,1000);check(r.home().distance(s.get(r.home().regionId))<=r.home().threshold,"real F-52A UI survives scaled sampling: "+Arrays.toString(size)+" smooth="+smooth);float[] p=r.layout.mainPointRelative(s,size[0],size[1]);check(p!=null&&Math.abs(p[0]/size[0]-.5f)<.03f&&p[1]/size[1]>.74f&&p[1]/size[1]<.825f,"resized orange target remains within its actual button");}
  for(boolean letterbox:new boolean[]{false,true}){Run r=new Run(true);int fw=letterbox?1800:1700,fh=letterbox?im.height:2500,left=letterbox?360:340,top=letterbox?0:110;RegionSampler.Pixels pixels=(x,y)->x>=left&&x<left+im.width&&y>=top&&y<top+im.height?im.get(x-left,y-top):letterbox?0xff000000:0xff414141;int[] window=letterbox?null:new int[]{left,top,left+im.width,top+im.height};Map<String,float[]> s=r.layout.sampleRelative(r.e.activeRegions(),fw,fh,pixels,1000,window);check(r.home().distance(s.get(r.home().regionId))<=r.home().threshold,"actual F-52A pixels recognize inside a moved or letterboxed window");float[] p=r.layout.mainPointRelative(s,fw,fh);check(p!=null&&Math.abs(p[0]-left-im.width*.5f)<im.width*.03f&&p[1]>top+im.height*.74f&&p[1]<top+im.height*.825f,"main target includes observed window offset");check(r.layout.mainPointRelative(s,fw+1,fh)==null,"stale frame dimensions invalidate the main target");}
  for(int[] bars:new int[][]{{26,48},{52,48},{52,100}}){Run r=new Run(true);Map<String,float[]> s=r.layout.sampleRelative(r.e.activeRegions(),im.width,im.height,im,1000,new int[]{0,bars[0],im.width,im.height-bars[1]});check(r.home().distance(s.get(r.home().regionId))<=r.home().threshold,"cropping system bars still recognizes the native F-52A main screen: "+Arrays.toString(bars));float[] p=r.layout.mainPointRelative(s,im.width,im.height);check(p!=null&&Math.abs(p[0]-im.width*.5f)<2&&Math.abs(p[1]-im.height*.791875f)<5,"bar crop preserves the actual physical main-button coordinate");check(r.layout.mainPointRelative(s,im.width,im.height+1)==null,"bar-crop coordinates cannot cross a frame-size change");}
 }
 static void previousPixel()throws Exception{
  Video6677ReplayTest.load();Set<String> zero=new HashSet<>(Arrays.asList("0031","0032","0080","0118","0119","0173"));Set<String> blocked=new HashSet<>(Arrays.asList("0008","0033","0081","0086","0120","0175","0190"));
  for(Map.Entry<String,VirtualPresetProfileTest.Image> row:Video6677ReplayTest.images.entrySet()){Run r=new Run(true);VirtualPresetProfileTest.Image im=row.getValue();Map<String,float[]> s=r.sample(im,1000);boolean ready=r.home().distance(s.get(r.home().regionId))<=r.home().threshold;check(ready==!blocked.contains(row.getKey()),"combined F-52A/Pixel reference pool retains previous recorded home decisions: "+row.getKey());check(UsagiStageCompletion.visible(im.width,im.height,new float[]{1,AdaptiveLayout.NORMAL_Y,0,AdaptiveLayout.NORMAL_TOP},im,r.layout.stageZeroReferences)==zero.contains(row.getKey()),"combined reference pool retains original whole-count completion decisions: "+row.getKey());if(ready){float[] p=r.layout.mainPointRelative(s,im.width,im.height);check(p!=null&&Math.abs(p[0]/im.width-.5f)<.03f&&Math.abs(p[1]/im.height-.78f)<.025f,"Pixel main target remains inside its own button layout");}}
 }
 public static void main(String[] args)throws Exception{load();scenes();starts();unavailable();geometry();previousPixel();System.out.println("Video6680ReplayTest: "+checks+" checks passed; native F-52A video, interruptions, main target coordinates and combined Pixel regression; new APK device input not tested");}
}
