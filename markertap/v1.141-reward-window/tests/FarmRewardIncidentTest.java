package jp.tapmarker;

import java.io.*;
import java.util.*;
import java.util.zip.*;
import org.json.*;

/** Actual heading/label pixels; coordinates, negative masks and state routing. */
public final class FarmRewardIncidentTest {
 static int checks;
 static void check(boolean b,String message){checks++;if(!b)throw new AssertionError(message);}
 static Map<String,float[]> canonical,evidence;
 static final class Image implements RegionSampler.Pixels {
  final int width,height;final int[] pixels;
  Image()throws Exception{try(DataInputStream in=new DataInputStream(new GZIPInputStream(new FileInputStream("tests/farm-reward-20261027-strips.bin.gz")))){
   width=in.readInt();height=in.readInt();pixels=new int[width*height];Arrays.fill(pixels,0xffdddddd);int n=in.readInt();
   for(int i=0;i<n;i++){int l=in.readInt(),t=in.readInt(),r=in.readInt(),b=in.readInt();for(int y=t;y<b;y++)for(int x=l;x<r;x++)pixels[y*width+x]=in.readInt();}
   check(n==2&&in.read()==-1,"only two complete non-identifying strips retained");
  }}
  public int get(int x,int y){return pixels[y*width+x];}
 }
 static Map<String,float[]> read(ZipFile z,String path)throws Exception{
  byte[] data;try(InputStream in=z.getInputStream(z.getEntry(path))){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];for(int n;(n=in.read(b))!=-1;)out.write(b,0,n);data=out.toByteArray();}
  JSONObject object=new JSONObject(new String(data,"UTF-8"));Map<String,float[]> result=new HashMap<>();
  for(Iterator<String> keys=object.keys();keys.hasNext();){String key=keys.next();JSONArray a=object.getJSONArray(key);float[] v=new float[a.length()];for(int i=0;i<v.length;i++)v[i]=(float)a.getDouble(i);result.put(key,v);}return result;
 }
 static final class Run {
  final PhaseEngine engine=new PhaseEngine();final AdaptiveLayout layout;
  Run(String state){FarmPreset.install(engine,canonical);engine.strictRuntime=true;layout=AdaptiveLayout.install(engine,FarmPreset.APP,canonical,evidence);check(layout!=null,"existing runtime layout retained");layout.configureTargets(FarmPreset.APP,evidence);engine.enter(FarmPreset.P+state,0);}
  Map<String,float[]> sample(int w,int h,RegionSampler.Pixels p,int[] bounds,long at){return layout.sampleRelative(engine.activeRegions(),w,h,p,at,bounds);}
 }
 static void route(String state,int w,int h,RegionSampler.Pixels pixels,int[] bounds){
  Run r=new Run(state);Map<String,float[]> first=r.sample(w,h,pixels,bounds,1000);r.engine.observe(first,1000);check(r.engine.poll()==null,"one image cannot trigger "+state);
  Map<String,float[]> next=r.sample(w,h,pixels,bounds,1350);r.engine.observe(next,1350);PhaseEngine.Watch event=r.engine.poll();
  check(event!=null,"supplied reward recognized from "+state+" / "+r.engine.diagnostic);
  if(!state.equals("reward")){check(event.nextPhase.equals(FarmPreset.P+"reward")&&event.action==PhaseEngine.WAIT,"recognition routes to the reward state");r.engine.finish(event,1350);next=r.sample(w,h,pixels,bounds,1700);r.engine.observe(next,1700);check(r.engine.poll()==null,"close requires fresh evidence after state entry");next=r.sample(w,h,pixels,bounds,2050);r.engine.observe(next,2050);event=r.engine.poll();}
  check(event!=null&&event.action==PhaseEngine.CENTER&&event.nextPhase.equals(FarmPreset.P+"check"),"close returns to observation");
  float[] p=r.layout.pointRelative(event,next,w,h);
  check(p!=null&&p[0]>w*.40f&&p[0]<w*.60f&&p[1]>h*.64f&&p[1]<h*.685f,"tap remains inside visible Close, above harvest and banner");
  check(r.engine.confirmsAction(event,r.engine.current,r.engine.epoch,next),"current pair authorizes only its own control");
  check(!r.engine.confirmsAction(event,r.engine.current,r.engine.epoch,new HashMap<>()),"missing current evidence cancels input");
  check(r.layout.pointRelative(event,next,w+1,h)==null,"resize invalidates the old tap point");
 }
 public static void main(String[] args)throws Exception{
  try(ZipFile z=new ZipFile("base/runtime-foundation.zip")){canonical=read(z,"assets/farm-preset.json");evidence=read(z,"assets/virtual-home-preset.json");}
  Image image=new Image();
  for(String state:new String[]{"main","check","ad","verify","reward"})route(state,image.width,image.height,image,null);
  route("ad",image.width,image.height,image,new int[]{0,0,image.width+24,image.height+48});
  route("ad",image.width,image.height,image,new int[]{0,24,image.width,image.height});
  GamePopupEvidence quick=new GamePopupEvidence(FarmPreset.APP,new PhaseEngine(),evidence);
  check(!GamePopupEvidence.present(quick.quick(image.width,image.height,image,new int[]{0,660,image.width,image.height})),"heading outside target window cannot authorize Close");
  RegionSampler.Pixels full=(x,y)->image.get(x/2,y/2);route("reward",image.width*2,image.height*2,full,null);
  for(float[] box:FarmDialogEvidence.CLOSE){int[] b=RegionMath.bounds(box,image.width,image.height);RegionSampler.Pixels omitted=(x,y)->x>=b[0]&&x<b[2]&&y>=b[1]&&y<b[3]?0xffffffff:image.get(x,y);
   check(!new FarmDialogEvidence(evidence).locate(FarmPreset.P+"close",image.width,image.height,omitted,1000,new HashMap<>()),"both heading and label are independently required");
  }
  check(!new FarmDialogEvidence(evidence).locate(FarmPreset.P+"close",image.width,image.height,(x,y)->0xff777777,1000,new HashMap<>()),"a blank screen cannot authorize Close");
  // Same real strips positioned inside a desktop window: add the origin once.
  int left=120,top=48,w=900,h=1500;RegionSampler.Pixels desktop=(x,y)->x>=left&&x<left+image.width&&y>=top&&y<top+image.height?image.get(x-left,y-top):0xffdddddd;
  Run r=new Run("reward");Map<String,float[]> s=r.sample(w,h,desktop,new int[]{left,top,left+image.width,top+image.height},1000);r.engine.observe(s,1000);r.engine.observe(r.sample(w,h,desktop,new int[]{left,top,left+image.width,top+image.height},1350),1350);PhaseEngine.Watch event=r.engine.poll();
  float[] p=event==null?null:r.layout.pointRelative(event,s,w,h);check(p!=null&&Math.abs(p[0]-(left+270))<20&&Math.abs(p[1]-(top+802))<20,"window offset follows actual label position");
  System.out.println("FarmRewardIncidentTest: "+checks+" checks passed; device input and balance credit remain untested");
 }
}
