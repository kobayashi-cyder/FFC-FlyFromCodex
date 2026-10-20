package jp.tapmarker;
import java.io.*;
import java.util.*;
import java.util.zip.*;

/** Reproduce the supplied claimable mission page with the unmodified v1.105 runtime. */
public final class Video6759BaselineProbe {
 public static void main(String[] args)throws Exception{
  UsagiControlReplayTest.load();
  try(DataInputStream in=new DataInputStream(new GZIPInputStream(new FileInputStream("tests/video6759-replay.bin.gz")))){
   int count=in.readInt();
   for(int i=0;i<count;i++){
    String name=in.readUTF();VirtualPresetProfileTest.Image im=new VirtualPresetProfileTest.Image(in);
    UsagiControlReplayTest.Run run=new UsagiControlReplayTest.Run("verify");
    run.observe(im.width,im.height,im,1000,null);
    PhaseEngine.Watch result=run.observe(im.width,im.height,im,1350,null);
    UsagiMissionEvidence evidence=run.layout.usagiMissions;
    float title=VisualLocator.distance(evidence.titleReference,RegionSampler.sample(im.width,im.height,evidence.title,im),null);
    float play=VisualLocator.distance(evidence.playReference,RegionSampler.sample(im.width,im.height,evidence.play,im),null);
    System.out.println(name+"s page="+run.samples.containsKey(UsagiMissionEvidence.PAGE)+" title="+title+" play="+play+" branch="+(result==null?"none":result.id)+" home="+run.home()+" state="+run.e.current);
   }
  }
 }
}
