package jp.tapmarker;
import java.util.*;

/** 6738 is fitted evidence; 6720/6725 are previous real replay frames.
 * Resize, removal, animation masking and window offsets below are synthetic. */
public final class GameInputRepairTest {
 static int checks;
 static Map<String,float[]> targets;
 static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 static final class Run {
  final PhaseEngine e=new PhaseEngine();final AdaptiveLayout layout;final String app;
  Run(String app)throws Exception{
   this.app=app;Map<String,float[]> refs=VirtualPresetProfileTest.canonical(app.equals(CoinPreset.APP)?"tests/video6720-references.bin":"tests/image6727-references.bin");
   if(app.equals(CoinPreset.APP))CoinPreset.install(e,refs);else PoipoiPreset.install(e,refs);
   e.strictRuntime=true;layout=AdaptiveLayout.install(e,app,refs,VirtualRecoveryReplayTest.evidence);layout.configureTargets(app,targets);
   e.enter((app.equals(CoinPreset.APP)?CoinPreset.P:PoipoiPreset.P)+"main",0);
  }
  Map<String,float[]> sample(int w,int h,RegionSampler.Pixels pixels,long at,int[] window){return layout.sampleRelative(e.activeRegions(),w,h,pixels,at,window);}
  boolean playable(Map<String,float[]> samples){return app.equals(CoinPreset.APP)?CoinPreset.playable(e,samples):PoipoiPreset.playable(e,samples);}
 }
 static void coin()throws Exception{
  VirtualPresetProfileTest.Image image=MarkerlessRelocationTest.image("image6738-replay.bin.gz","6738");
  Run r=new Run(CoinPreset.APP);Map<String,float[]> samples=r.sample(image.width,image.height,image,1000,null);
  check(r.playable(samples),"6738 remains an ordinary game, without classifying the stopped panel as an error");
  float[] point=r.layout.mainPointRelative(samples,image.width,image.height);
  check(point!=null&&Math.abs(point[0]-342)<1&&Math.abs(point[1]-1239.552)<2,"6738 reacquires the coin button at 342,1240");
  check(samples.containsKey(AdaptiveLayout.MAIN+ContentRelocator.LOCATED),"current button pixels, not a fallback marker, authorize the point");
  for(int[] size:new int[][]{{360,808},{540,1212},{1080,2424}}){
   int w=size[0],h=size[1];RegionSampler.Pixels scaled=(x,y)->image.get(Math.min(image.width-1,x*image.width/w),Math.min(image.height-1,y*image.height/h));
   r=new Run(CoinPreset.APP);samples=r.sample(w,h,scaled,1000,null);point=r.layout.mainPointRelative(samples,w,h);
   check(r.playable(samples)&&point!=null,"synthetic resized 6738 reacquires scene and button: "+w+"x"+h);
   check(Math.abs(point[0]/w-.5f)<.025f&&Math.abs(point[1]/h-.807f)<.025f,"resized input remains within the large yellow button");
  }
  int left=103,top=111,w=1000,h=1800;RegionSampler.Pixels desktop=(x,y)->x>=left&&x<left+image.width&&y>=top&&y<top+image.height?image.get(x-left,y-top):0xff121212;
  r=new Run(CoinPreset.APP);samples=r.sample(w,h,desktop,1000,new int[]{left,top,left+image.width,top+image.height});point=r.layout.mainPointRelative(samples,w,h);
  check(point!=null&&Math.abs(point[0]-(left+342))<2&&Math.abs(point[1]-(top+1239.552))<2,"selected window offset is applied exactly once");
  r=new Run(CoinPreset.APP);r.sample(image.width,image.height,image,1000,null);
  int[] b=RegionMath.bounds(targets.get(CoinPreset.APP+":main:bounds"),image.width,image.height);
  RegionSampler.Pixels absent=(x,y)->x>=b[0]-5&&x<b[2]+5&&y>=b[1]-5&&y<b[3]+5?0xff334455:image.get(x,y);
  samples=r.sample(image.width,image.height,absent,2600,null);
  check(r.layout.mainPointRelative(samples,image.width,image.height)==null,"missing coin object revokes cached input, even when game headers still match");
 }
 static void poipoi()throws Exception{
  Video6725ReplayTest.load();Run r=new Run(PoipoiPreset.APP);long now=1000;
  for(String key:new String[]{"0.0","16.0","18.0","26.0","28.0","30.0","32.0","34.0","36.0","38.0","46.0","48.0","50.0","52.0","54.0","56.0","58.0","60.0","68.0","70.0","72.0","71.5","96.5","98.5"}){
   VirtualPresetProfileTest.Image im=Video6725ReplayTest.images.get(key);Map<String,float[]> samples=r.sample(im.width,im.height,im,now+=350,null);float[] point=r.layout.mainPointRelative(samples,im.width,im.height);
   check(r.playable(samples)&&point!=null,"normal recorded feeding has a fresh target: "+key);
   check(point[0]>.40f*im.width&&point[0]<.60f*im.width&&point[1]>.56f*im.height&&point[1]<.64f*im.height,"target remains on carrots: "+key);
  }
  VirtualPresetProfileTest.Image im=Video6725ReplayTest.images.get("26.0");
  final VirtualPresetProfileTest.Image feeding=im;
  RegionSampler.Pixels animation=(x,y)->x>=.10f*feeding.width&&x<.89f*feeding.width&&y>=.39f*feeding.height&&y<.535f*feeding.height?0xffee11dd:feeding.get(x,y);
  Map<String,float[]> samples=r.sample(im.width,im.height,animation,now+=2000,null);
  check(r.playable(samples)&&r.layout.mainPointRelative(samples,im.width,im.height)!=null,"synthetic rabbit animation does not remove the stable carrot/plate target");
  int[] box=RegionMath.bounds(targets.get(PoipoiPreset.APP+":main:bounds"),im.width,im.height);
  RegionSampler.Pixels absent=(x,y)->x>=box[0]-5&&x<box[2]+5&&y>=box[1]-5&&y<box[3]+5?0xff334455:feeding.get(x,y);
  samples=r.sample(im.width,im.height,absent,now+=2000,null);
  check(r.layout.mainPointRelative(samples,im.width,im.height)==null,"missing plate revokes a previously cached feeding target");
  for(String key:new String[]{"0.8","13.6","4.2","12.2","84.2","2.2","72.9","74.2","6.8","81.5","8.4","22.4","92.5"}){
   im=Video6725ReplayTest.images.get(key);samples=r.sample(im.width,im.height,im,now+=1600,null);
   check(!r.playable(samples),"dialog/full/mission/ad/HOME cannot authorize feeding: "+key);
  }
  PoipoiDeclineScreenshotTest.load();im=PoipoiDeclineScreenshotTest.dialog;samples=r.sample(im.width,im.height,im,now+2000,null);
  check(!r.playable(samples),"使用しない dialog still holds the feeding loop");
 }
 static void contact(){
  for(String app:new String[]{CoinPreset.APP,PoipoiPreset.APP})for(float screen:new float[]{60,90,120,240}){
   long press=TapContactPolicy.pressMillis("Pixel 10",TapContactPolicy.AUTO,screen,app,1),release=TapContactPolicy.releaseMillis("Pixel 10",TapContactPolicy.AUTO,screen,app);
   check(press>=17&&release>=17,"automatic game profile keeps both edges at high phone refresh rates");
   check(TapContactPolicy.pressMillis("Pixel 10",TapContactPolicy.AUTO,screen,app,60)==60,"explicit 60ms button actions stay intact");
   check(TapContactPolicy.pressMillis("Pixel 10",TapContactPolicy.SHORTEST,screen,app,1)==1&&TapContactPolicy.releaseMillis("Pixel 10",TapContactPolicy.SHORTEST,screen,app)==0,"explicit shortest mode remains selectable");
   for(int phase=0;phase<17;phase++){
    F52AContactTest.Receiver receiver=new F52AContactTest.Receiver(60,phase,release);Tapper tapper=new Tapper(receiver,receiver,receiver,null);
    tapper.update(new Tapper.Command(app,0,"main",new int[]{0,0,540,1200},new float[]{270,900},press,1,5000,true));receiver.until(1000);tapper.pause("CM");receiver.until(1100);
    check(receiver.downs==receiver.ups&&receiver.sampledDowns==receiver.downs&&receiver.sampledUps==receiver.ups,"synthetic 60Hz receiver samples all DOWN/UP edges at screen "+screen+" phase "+phase);
   }
  }
  check(TapContactPolicy.pressMillis("Pixel 10",TapContactPolicy.AUTO,120,FarmPreset.APP,1)==1&&TapContactPolicy.releaseMillis("Pixel 10",TapContactPolicy.AUTO,120,FarmPreset.APP)==0,"other game contact settings do not inherit this profile");
 }
 public static void main(String[] args)throws Exception{
  VirtualRecoveryReplayTest.load();VirtualRecoveryReplayTest.layoutEvidence();targets=VirtualPresetProfileTest.canonical("tests/visual-targets.bin");
  coin();poipoi();contact();System.out.println("GameInputRepairTest: "+checks+" checks passed; fitted 6738, recorded Poipoi, synthetic geometry/occlusion/animation and frame-polled contact; no physical game success claimed");
 }
}
