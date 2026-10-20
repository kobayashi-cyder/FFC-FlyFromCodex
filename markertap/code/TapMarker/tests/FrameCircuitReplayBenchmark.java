package jp.tapmarker;

import java.io.*;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;
import java.util.*;
import java.util.zip.*;

/** Identical recognition kernels with legacy per-window snapshots vs the shipped graph.
 * Recorded inputs are resized synthetically; timings are sequential host-JVM timings,
 * and memory numbers cover the Java image arrays, not Android process PSS.
 */
public final class FrameCircuitReplayBenchmark {
 static Map<String,float[]> usagi,farm,evidence,targets;static int checks;
 static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 static final class Scene {final int lane;final String name;final VirtualPresetProfileTest.Image image;Scene(int lane,String name,VirtualPresetProfileTest.Image im){this.lane=lane;this.name=name;image=im;}}
 static final class Input {final Scene scene;final int width,height;final int[] pixels;Input(Scene scene,int width,int height){this.scene=scene;this.width=width;this.height=height;pixels=new int[width*height];RegionSampler.Pixels resized=FarmDialogReplayTest.resize(scene.image,width,height,true);for(int y=0;y<height;y++)for(int x=0;x<width;x++)pixels[y*width+x]=resized.get(x,y);}}
 static final class Outcome {
  String rule;long fingerprint;float[] point;boolean home;Map<String,float[]> features;
  void freeze(){Map<String,float[]> saved=new TreeMap<>();for(Map.Entry<String,float[]> entry:features.entrySet())saved.put(entry.getKey(),entry.getValue().clone());features=saved;}
  public boolean equals(Object other){if(!(other instanceof Outcome))return false;Outcome o=(Outcome)other;if(!rule.equals(o.rule)||fingerprint!=o.fingerprint||!Arrays.equals(point,o.point)||home!=o.home||!features.keySet().equals(o.features.keySet()))return false;for(String key:features.keySet())if(!Arrays.equals(features.get(key),o.features.get(key)))return false;return true;}
 }
 static final class Kernel {
  final PhaseEngine e;final AdaptiveLayout layout;final UpgradeEngine upgrades;final String prefix;final boolean rabbit;
  Kernel(int lane,boolean normalize){rabbit=lane==0;prefix=rabbit?UsagiPreset.PREFIX:FarmPreset.P;PhaseEngine installed=new PhaseEngine();if(rabbit)UsagiPreset.install(installed,usagi);else FarmPreset.install(installed,farm);e=installed.runtimeCopy();layout=AdaptiveLayout.install(e,rabbit?UsagiPreset.APP:FarmPreset.APP,rabbit?usagi:farm,evidence);layout.configureTargets(rabbit?UsagiPreset.APP:FarmPreset.APP,targets);AdCloseAutomaton.install(e,rabbit?UsagiPreset.APP:FarmPreset.APP);e.strictRuntime=true;if(normalize)AutomatonNormalizer.normalizeRuntime(e);upgrades=new UpgradeEngine(e,rabbit?UsagiPreset.APP:FarmPreset.APP);}
  Map<String,float[]> sample(int width,int height,RegionSampler.Pixels pixels,long now){List<PhaseEngine.Region> regions=e.activeRegions();PhaseEngine.Region home=e.regions.get(prefix+(rabbit?"game":"harvest"));if(!regions.contains(home))regions.add(home);upgrades.addRegions(regions);return layout.sampleRelative(regions,width,height,pixels,now);}
  Outcome analyze(int width,int height,RegionSampler.Pixels pixels){
   e.enter(prefix+"verify",0);e.observe(sample(width,height,pixels,1000),1000);e.poll();Map<String,float[]> samples=sample(width,height,pixels,1350);e.observe(samples,1350);PhaseEngine.Watch watch=e.poll();
   if(watch!=null&&watch.action==PhaseEngine.WAIT&&(watch.nextPhase.equals(prefix+"missions")||watch.nextPhase.equals(prefix+"reward"))){e.finish(watch,1400);e.observe(sample(width,height,pixels,1500),1500);e.poll();samples=sample(width,height,pixels,1850);e.observe(samples,1850);watch=e.poll();}
   Outcome result=new Outcome();result.rule=watch==null?"none":watch.id;result.point=watch!=null&&watch.action==PhaseEngine.CENTER?layout.pointRelative(watch,samples,width,height):null;PhaseEngine.Watch home=e.rule(prefix+(rabbit?"return":"verify-home"));result.home=home!=null&&home.distance(samples.get(home.regionId))<=home.threshold;
   long hash=0xcbf29ce484222325L;for(String key:new TreeSet<>(samples.keySet())){hash=(hash^key.hashCode())*0x100000001b3L;for(float value:samples.get(key))hash=(hash^Float.floatToIntBits(value))*0x100000001b3L;}result.fingerprint=hash;result.features=samples;return result;
  }
 }
 static Map<String,VirtualPresetProfileTest.Image> read(String path)throws Exception{
  Map<String,VirtualPresetProfileTest.Image> images=new LinkedHashMap<>();try(DataInputStream in=new DataInputStream(new GZIPInputStream(new FileInputStream(path)))){int n=in.readInt();for(int i=0;i<n;i++)images.put(in.readUTF(),new VirtualPresetProfileTest.Image(in));}return images;
 }
 static List<Scene> scenes()throws Exception{
  usagi=VirtualPresetProfileTest.canonical("tests/usagi-current-references.bin");farm=VirtualPresetProfileTest.canonical("tests/farm-replay.bin");evidence=VirtualPresetProfileTest.canonical("tests/current-layout-evidence.bin");targets=VirtualPresetProfileTest.canonical("tests/visual-targets.bin");
  Map<String,VirtualPresetProfileTest.Image> get=read("tests/video6759-replay.bin.gz"),play=read("tests/video6753-replay.bin.gz");VirtualRecoveryReplayTest.load();List<Scene> result=new ArrayList<>();
  for(String id:new String[]{"2","8","18"})result.add(new Scene(0,"6759/Get/"+id,get.get(id)));
  for(String id:new String[]{"8","24"})result.add(new Scene(0,"6753/Play/"+id,play.get(id)));
  for(String id:new String[]{"usagi-main","usagi-ad-left"})result.add(new Scene(0,"6645/"+id,VirtualRecoveryReplayTest.images.get(id)));
  for(String id:new String[]{"farm-main","farm-main-pressed","farm-reward","farm-reward-holdout","farm-ad-left","farm-ad-right"})result.add(new Scene(1,"6645/"+id,VirtualRecoveryReplayTest.images.get(id)));
  return result;
 }
 static Instrumentation meter(){try{Field field=Class.forName("jp.tapmarker.HeapAgent").getDeclaredField("meter");field.setAccessible(true);return (Instrumentation)field.get(null);}catch(Exception missing){return null;}}
 static long median(List<Long> values){ArrayList<Long> sorted=new ArrayList<>(values);Collections.sort(sorted);return sorted.get(sorted.size()/2);}
 static String ms(long ns){return String.format(Locale.ROOT,"%.4f",ns/1000000.0);}
 public static void main(String[] args)throws Exception{
  List<Scene> scenes=scenes();Instrumentation meter=meter();StringBuilder json=new StringBuilder("{\n  \"method\":\"Sequential OpenJDK replay; real scenes synthetically resized; same recognition kernels; Java image arrays only, not Android PSS\",\n  \"new_circuit_nodes\":8,\n  \"scenes\":13,\n  \"measurement\":\"").append(meter==null?"array payload bytes":"Instrumentation shallow object sizes for actual arrays").append("\",\n  \"sizes\":[\n");int sizes=0,totalMatched=0,meaningful=0;
  for(int[] size:new int[][]{{540,1212},{720,1616}}){
   int width=size[0],height=size[1];List<Input> inputs=new ArrayList<>();for(Scene scene:scenes)inputs.add(new Input(scene,width,height));Kernel[] baseline={new Kernel(0,false),new Kernel(1,false)},graphKernel={new Kernel(0,true),new Kernel(1,true)};int[][] oldSnapshots=new int[2][];FrameCircuit graph=new FrameCircuit(()->1000);List<Long> oldTimes=new ArrayList<>(),graphTimes=new ArrayList<>();Map<String,Outcome> expected=new HashMap<>();
   for(int round=0;round<7;round++){
    boolean oldFirst=round%2==0;
    for(int pass=0;pass<2;pass++){
     boolean legacy=pass==0?oldFirst:!oldFirst;
     for(Input input:inputs){int lane=input.scene.lane;Outcome[] output=new Outcome[1];long started=System.nanoTime();
      if(legacy){if(oldSnapshots[lane]==null)oldSnapshots[lane]=new int[input.pixels.length];System.arraycopy(input.pixels,0,oldSnapshots[lane],0,input.pixels.length);output[0]=baseline[lane].analyze(width,height,new FramePixels.Raster(width,height,oldSnapshots[lane],1000));}
      else graph.run(new FrameCircuit.Request(()->true,workspace->{System.arraycopy(input.pixels,0,workspace.array(input.pixels.length),0,input.pixels.length);return new FrameCircuit.Input(width,height,1000);},frame->{Outcome result=graphKernel[lane].analyze(width,height,frame);return ()->output[0]=result;},Runnable::run,failure->{throw new AssertionError(failure);}));
      long duration=System.nanoTime()-started;check(output[0]!=null,"a result is published for "+input.scene.name);output[0].freeze();
      String key=round+"/"+input.scene.name;Outcome prior=expected.get(key);
      if(prior==null)expected.put(key,output[0]);else{check(output[0].equals(prior),"recorded features, decisions and input coordinates agree: "+key+" / "+width);if(round>=2){totalMatched++;if(!output[0].rule.equals("none"))meaningful++;}}
      if(round>=2){if(legacy)oldTimes.add(duration);else graphTimes.add(duration);}
     }
    }
   }
   long oldPayload=0,oldObjects=0;for(int[] snapshot:oldSnapshots){oldPayload+=(long)snapshot.length*4;oldObjects+=meter==null?(long)snapshot.length*4:meter.getObjectSize(snapshot);}int[] shared=graph.workspace.array(width*height);long graphObject=meter==null?(long)shared.length*4:meter.getObjectSize(shared);long inputObjects=2*(meter==null?(long)width*height*4:meter.getObjectSize(inputs.get(0).pixels));
   check(oldPayload==graph.retainedBytes*2,"one actual shared snapshot replaces two legacy snapshot arrays");
   if(sizes++>0)json.append(",\n");json.append("    {\"width\":").append(width).append(",\"height\":").append(height).append(",\"legacy_snapshot_payload_bytes\":").append(oldPayload).append(",\"circuit_snapshot_payload_bytes\":").append(graph.retainedBytes).append(",\"snapshot_object_bytes_before\":").append(oldObjects).append(",\"snapshot_object_bytes_after\":").append(graphObject).append(",\"four_to_three_image_array_bytes_before\":").append(inputObjects+oldObjects).append(",\"four_to_three_image_array_bytes_after\":").append(inputObjects+graphObject).append(",\"legacy_median_ms\":").append(ms(median(oldTimes))).append(",\"circuit_median_ms\":").append(ms(median(graphTimes))).append(",\"timed_samples_per_mode\":").append(oldTimes.size()).append("}");
   System.out.println("Replay "+width+"x"+height+": legacy snapshots="+oldObjects+" bytes, new graph="+graphObject+" bytes; host median "+ms(median(oldTimes))+" -> "+ms(median(graphTimes))+" ms; results identical");
  }
  json.append("\n  ],\n  \"matched_timed_outputs\":").append(totalMatched).append(",\n  \"nonempty_timed_decisions\":").append(meaningful).append(",\n  \"checks\":").append(checks).append(",\n  \"limits\":[\"No Android device\",\"No process PSS or graphics/game memory measured\",\"Host timings omit Android callbacks and per-window parallel baseline throughput\",\"Confirmation times and resize are synthetic\",\"Only frame orchestration/workspace is newly graph-executed; existing vision and decision kernels remain\"]\n}\n");
  String path=args.length==0?"FRAME-CIRCUIT-BENCHMARK-v1.107.json":args[0];try(Writer out=new OutputStreamWriter(new FileOutputStream(path),"UTF-8")){out.write(json.toString());}System.out.println("FrameCircuitReplayBenchmark: "+checks+" checks; "+totalMatched+" matching measured outputs / "+meaningful+" nonempty decisions. Wrote "+path);
 }
}
