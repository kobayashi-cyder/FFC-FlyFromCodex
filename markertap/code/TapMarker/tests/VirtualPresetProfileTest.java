package jp.tapmarker;
import java.io.*;import java.util.*;
public class VirtualPresetProfileTest {
 static int checks;
 static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
 static final class Image implements RegionSampler.Pixels {
  final int width,height;final int[] rgb;
  Image(DataInputStream in)throws Exception{width=in.readInt();height=in.readInt();rgb=new int[width*height];byte[] bytes=new byte[rgb.length*3];in.readFully(bytes);for(int i=0;i<rgb.length;i++)rgb[i]=0xff000000|(bytes[i*3]&255)<<16|(bytes[i*3+1]&255)<<8|bytes[i*3+2]&255;}
  public int get(int x,int y){return rgb[y*width+x];}
 }
 static Map<String,float[]> samples(PhaseEngine engine,VirtualPresetProfile profile,Image image,RegionSampler.Pixels pixels){Map<String,float[]> result=new HashMap<>();for(PhaseEngine.Region region:engine.activeRegions())result.put(region.id,profile.region.equals(region.id)?profile.sample(image.width,image.height,pixels):RegionSampler.sample(image.width,image.height,region.bounds,pixels));return result;}
 static Map<String,float[]> canonical(String path)throws Exception{try(DataInputStream in=new DataInputStream(new FileInputStream(path))){return UsagiPresetTest.read(in);}}
 public static void main(String[] args)throws Exception{
  Image[] images=new Image[2];Map<String,float[]> evidence=new HashMap<>();
  try(DataInputStream in=new DataInputStream(new FileInputStream("tests/virtual-home-replay.bin"))){check(in.readInt()==2,"two actual visible virtual windows in supplied screenshot");for(int i=0;i<2;i++){images[i]=new Image(in);float[] f=new float[in.readInt()];for(int k=0;k<f.length;k++)f[k]=in.readFloat();evidence.put(i==0?"usagi-home":"farm-home",f);}check(in.read()==-1,"complete fixture read");}
  for(int i=0;i<2;i++){
   final Image image=images[i];String app=i==0?UsagiPreset.APP:FarmPreset.APP,prefix=i==0?UsagiPreset.PREFIX:FarmPreset.P;
   Map<String,float[]> refs=canonical(i==0?"tests/usagi-replay.bin":"tests/farm-replay.bin");PhaseEngine engine=new PhaseEngine();engine.strictRuntime=true;if(i==0)UsagiPreset.install(engine,refs);else FarmPreset.install(engine,refs);
   PhaseEngine.Watch home=engine.rule(i==0?prefix+"return":prefix+"verify-home");PhaseEngine.Region region=engine.regions.get(home.regionId);
   check(FlyCircuit.distance(home.reference,RegionSampler.sample(image.width,image.height,region.bounds,image))>home.threshold,"canonical physical-screen pixels do not match this reduced virtual preview");
   float[] physicalReference=refs.get(i==0?"game":"harvest").clone();
   final VirtualPresetProfile profile=VirtualPresetProfile.install(engine,app,evidence);check(profile!=null,"virtual default profile installed");
   check(Arrays.equals(physicalReference,refs.get(i==0?"game":"harvest")),"original single-screen reference data is not changed");
   float[] actual=profile.sample(image.width,image.height,image);check(actual.length==1536&&home.distance(actual)<.000001f,"real region sampler matches supplied two-anchor evidence");
   float[] oneAnchor=home.reference.clone();for(int k=0;k<768;k++)oneAnchor[k]-=.18f;check(FlyCircuit.distance(home.reference,oneAnchor)<home.threshold&&home.distance(oneAnchor)>home.threshold,"one mismatching anchor cannot be hidden by averaging the other anchor");
   engine.enter(prefix+"verify",0);engine.observe(samples(engine,profile,image,image),10);check(engine.poll()==null,"virtual main screen still needs two frames");engine.observe(samples(engine,profile,image,image),20);PhaseEngine.Watch ready=engine.poll();check(ready!=null&&ready.nextPhase.equals(prefix+"main"),"supplied virtual screen proceeds from verify to the correct game main loop: "+engine.diagnostic);engine.finish(ready,20);
   engine.observe(samples(engine,profile,image,image),30);check(engine.poll()==null,"first main-loop frame does not create false event");engine.observe(samples(engine,profile,image,image),40);check(engine.poll()==null,"current 831 rabbits / farm CM upgrade screen creates no false event: "+engine.diagnostic);
   int[] header=RegionMath.bounds(profile.header,image.width,image.height),button=RegionMath.bounds(profile.button,image.width,image.height);
   for(int part=0;part<2;part++){final int[] missing=part==0?header:button;RegionSampler.Pixels pixels=(x,y)->x>=missing[0]&&x<missing[2]&&y>=missing[1]&&y<missing[3]?0xff000000:image.get(x,y);check(home.distance(profile.sample(image.width,image.height,pixels))>home.threshold,"either missing anchor blocks main-loop admission");}
   RegionSampler.Pixels dim=(x,y)->{int c=image.get(x,y);return 0xff000000|(((c>>16)&255)/2)<<16|(((c>>8)&255)/2)<<8|(c&255)/2;};check(home.distance(profile.sample(image.width,image.height,dim))>home.threshold,"dimmed popup background cannot be treated as ready main screen");
   final Image other=images[1-i];check(home.distance(profile.sample(other.width,other.height,other))>home.threshold,"other game image cannot substitute for this game's home screen");
   int width=720,height=Math.round(width*image.height/(float)image.width);RegionSampler.Pixels scaled=(x,y)->image.get(Math.min(image.width-1,(int)(x*image.width/(float)width)),Math.min(image.height-1,(int)(y*image.height/(float)height)));check(home.distance(profile.sample(width,height,scaled))<home.threshold,"nearest-resized evidence maintains normalized region recognition");
   if(i==0){PhaseEngine.Watch mission=engine.rule(prefix+"ミッション中央をタップ");float[] p=UsagiPreset.tapCenter(engine,mission,image.width,image.height);check(p[0]>image.width*.365f&&p[0]<image.width*.395f&&p[1]>image.height*.014f&&p[1]<image.height*.038f,"mission tap stays inside the observed virtual header icon");}
   PhaseEngine custom=new PhaseEngine();if(i==0)UsagiPreset.install(custom,refs);else FarmPreset.install(custom,refs);custom.regions.get(profile.region).bounds[0]+=.01f;check(VirtualPresetProfile.install(custom,app,evidence)==null,"custom recognition-region edits are not overridden");
  }
  System.out.println("VirtualPresetProfileTest: "+checks+" checks passed; supplied preview pixels plus synthetic negatives/resizing, raw virtual frames and device input not tested");
 }
}
