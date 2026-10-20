package jp.tapmarker;

import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** Independent recording: ordinary play, pressed harvest, fever, rewards and real ads. */
public final class Video6732ReplayTest {
 static int checks;
 static final Map<String,VirtualPresetProfileTest.Image> images=new LinkedHashMap<>();
 static final Set<String> INTERRUPTIONS=new HashSet<>(Arrays.asList("28","30","60","90","183","192"));
 static void check(boolean ok,String reason){checks++;if(!ok)throw new AssertionError(reason);}
 static void load()throws Exception{
  if(!images.isEmpty())return;
  try(DataInputStream in=new DataInputStream(new GZIPInputStream(new FileInputStream("tests/video6732-replay.bin.gz")))){
   int n=in.readInt();for(int i=0;i<n;i++)images.put(in.readUTF(),new VirtualPresetProfileTest.Image(in));
   check(n==26&&in.read()==-1,"complete native-size frames from recording 6732");
  }
 }
 static final class Run {
  final PhaseEngine e=new PhaseEngine();final AdaptiveLayout layout;
  Run()throws Exception{
   Map<String,float[]> canonical=VirtualPresetProfileTest.canonical("tests/farm-replay.bin");
   FarmPreset.install(e,canonical);
   layout=AdaptiveLayout.install(e,FarmPreset.APP,canonical,VirtualPresetProfileTest.canonical("tests/current-layout-evidence.bin"));
   layout.configureTargets(FarmPreset.APP,VirtualPresetProfileTest.canonical("tests/visual-targets.bin"));
   new UpgradeEngine(e,FarmPreset.APP);e.strictRuntime=true;e.enter(FarmPreset.P+"main",0);
  }
 }
 public static void main(String[] args)throws Exception{
  load();Run run=new Run();long total=0,playReads=0;int playFrames=0;
  for(Map.Entry<String,VirtualPresetProfileTest.Image> row:images.entrySet()){
   VirtualPresetProfileTest.Image im=row.getValue();long[] reads={0};
   TilePixels tile=new TilePixels(im.width,im.height,(x,y,w,h,p,stride)->{for(int dy=0;dy<h;dy++)for(int dx=0;dx<w;dx++)p[dy*stride+dx]=im.get(x+dx,y+dy);},64);
   RegionSampler.Pixels pixels=(x,y)->{reads[0]++;return tile.get(x,y);};
   long now=1000+Long.parseLong(row.getKey())*1000,start=System.nanoTime();
   Map<String,float[]> samples=run.layout.sampleRelative(run.e.activeRegions(),im.width,im.height,pixels,now);
   long elapsed=(System.nanoTime()-start)/1000000;total+=reads[0];
   boolean normal=!INTERRUPTIONS.contains(row.getKey()),home=FarmPreset.home(run.e,samples);float[] point=run.layout.mainPointRelative(samples,im.width,im.height);
   check(home==normal,"only current harvest scenes permit play: "+row.getKey());
   check(ContinuousTapPolicy.allow(run.e,home,false,false,false,true,now,now+100)==normal,"real input policy follows scene evidence: "+row.getKey());
   check(!ContinuousTapPolicy.allow(run.e,home,false,false,false,true,now,now+1500),"no stale image or long lease bypasses recognition: "+row.getKey());
   if(normal){
    playFrames++;playReads+=reads[0];
    check(point!=null&&point[0]>im.width*.35f&&point[0]<im.width*.65f&&point[1]>im.height*.75f&&point[1]<im.height*.86f,"native pressed/released/rainbow button has a fresh point: "+row.getKey());
    check(reads[0]<2000000,"normal input does not wait for an absent control's global scan: "+row.getKey()+" / "+reads[0]);
    run.e.observe(samples,now);check(run.e.poll()==null,"ordinary button animation does not leave the main loop: "+row.getKey());
   }else check(point==null,"ads, chest, reward and confirmation do not retain an input point: "+row.getKey());
   System.out.println("6732 / "+row.getKey()+" home="+FarmPreset.home(run.e,samples)+" target="+Arrays.toString(run.layout.mainPointRelative(samples,im.width,im.height))+" fever="+samples.containsKey(FarmPreset.P+"fever-confirmed")+" reads="+reads[0]+" tileReads="+tile.reads+" ms="+elapsed);
  }
  check(playFrames==20&&playReads<5000000,"twenty real play frames fit the bounded sampling budget");
  // A fresh instance must also locate the pressed target without a previous successful track.
  for(String id:new String[]{"10","14","50"}){Run cold=new Run();VirtualPresetProfileTest.Image im=images.get(id);Map<String,float[]> s=cold.layout.sampleRelative(cold.e.activeRegions(),im.width,im.height,im,1000);check(FarmPreset.home(cold.e,s)&&cold.layout.mainPointRelative(s,im.width,im.height)!=null,"pressed cold start has its own evidence: "+id);}
  System.out.println("Video6732ReplayTest: "+checks+" checks passed; play_frames="+playFrames+" play_pixel_requests="+playReads+" total_pixel_requests="+total+"; actual game touch execution untested");
 }
}
