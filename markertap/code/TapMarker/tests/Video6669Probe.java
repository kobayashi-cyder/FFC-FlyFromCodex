package jp.tapmarker;
import java.io.*;
import java.util.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
/** Diagnostic against actual recorded pixels, before and after recognition changes. */
public final class Video6669Probe {
 public static void main(String[] args)throws Exception{
  VirtualRecoveryReplayTest.load();VirtualRecoveryReplayTest.layoutEvidence();
  try(DataInputStream in=new DataInputStream(new FileInputStream("tests/video6669-stable-reference.bin"))){float[] ref=new float[in.readInt()];for(int i=0;i<ref.length;i++)ref[i]=in.readFloat();VirtualRecoveryReplayTest.evidence.put("farm-stable-home",ref);}
  try(DataInputStream in=new DataInputStream(new FileInputStream("tests/video6669-pressed-reference.bin"))){float[] ref=new float[in.readInt()];for(int i=0;i<ref.length;i++)ref[i]=in.readFloat();VirtualRecoveryReplayTest.evidence.put("farm-stable-pressed",ref);}
  Map<String,float[]> refs=VirtualPresetProfileTest.canonical("tests/farm-replay.bin");
  for(String name:args){
   BufferedImage image=ImageIO.read(new File(name));
   PhaseEngine e=new PhaseEngine();FarmPreset.install(e,refs);
   AdaptiveLayout layout=AdaptiveLayout.install(e,FarmPreset.APP,refs,VirtualRecoveryReplayTest.evidence);
   e.strictRuntime=true;e.enter(FarmPreset.P+"verify",0);
   RegionSampler.Pixels pixels=(x,y)->image.getRGB(x,y);
   Map<String,float[]> samples=layout.sample(e.activeRegions(),image.getWidth(),image.getHeight(),pixels,2000);
   PhaseEngine.Watch home=e.rule(FarmPreset.P+"verify-home");
   System.out.println(new File(name).getName()+" home="+FarmPreset.home(e,samples)+" distance="+home.distance(samples.get(home.regionId))+" threshold="+home.threshold+" transform="+Arrays.toString(layout.transform));
  }
 }
}
