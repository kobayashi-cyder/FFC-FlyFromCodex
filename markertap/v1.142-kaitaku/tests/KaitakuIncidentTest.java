package jp.tapmarker;
import java.io.*;import java.util.*;import java.util.zip.*;import org.json.*;

/** Recorded controls, negative ad frames, strict state transitions and coordinates. */
public final class KaitakuIncidentTest {
 static int checks;static void check(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
 static final class Image implements RegionSampler.Pixels {
  int time,w,h;int[] data;
  Image(DataInputStream in)throws Exception{time=in.readInt();w=in.readInt();h=in.readInt();data=new int[w*h];Arrays.fill(data,0xff999999);int n=in.readInt();for(int i=0;i<n;i++){int l=in.readInt(),t=in.readInt(),r=in.readInt(),b=in.readInt();for(int y=t;y<b;y++)for(int x=l;x<r;x++)data[y*w+x]=in.readInt();}}
  public int get(int x,int y){return data[y*w+x];}
 }
 static Map<String,float[]> refs()throws Exception{JSONObject j=new JSONObject(new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get("assets/kaitaku-preset.json")),"UTF-8"));Map<String,float[]> out=new HashMap<>();for(Iterator<String> k=j.keys();k.hasNext();){String s=k.next();JSONArray a=j.getJSONArray(s);float[] v=new float[a.length()];for(int i=0;i<v.length;i++)v[i]=(float)a.getDouble(i);out.put(s,v);}return out;}
 public static void main(String[] args)throws Exception{
  Map<String,float[]> refs=refs();KaitakuEvidence evidence=new KaitakuEvidence(refs);Map<Integer,Image> images=new HashMap<>();
  try(DataInputStream in=new DataInputStream(new GZIPInputStream(new FileInputStream("tests/kaitaku-controls.bin.gz")))){int n=in.readInt();for(int i=0;i<n;i++){Image p=new Image(in);images.put(p.time,p);}check(in.read()==-1,"fixtures are complete");}
  Set<Integer> positive=new HashSet<>(Arrays.asList(72,120,144,176));
  for(Image p:images.values()){
   KaitakuEvidence.Result r=evidence.read(p.w,p.h,p,null);System.out.println("frame="+p.time+" home="+r.home+" continue="+r.proceed);
   if(positive.contains(p.time))check(r.home,"visible home recognized at "+p.time);
   if(p.time==0||p.time>=156&&p.time<=174)check(!r.home,"launcher/advertisement/transition cannot be home at "+p.time);
   if(p.time==175)check(r.proceed&&!r.home,"continue dialog recognized independently");
  }
  check(!evidence.read(540,1212,(x,y)->0xffffffff,null).home,"white screen cannot authorize main");
  check(!evidence.read(540,1212,(x,y)->0xff000000,null).proceed,"black screen cannot authorize continue");
  Image main=images.get(144);for(float[] omitted:new float[][]{KaitakuEvidence.LEFT,KaitakuEvidence.RIGHT}){int[] b=RegionMath.bounds(omitted,main.w,main.h);check(!evidence.read(main.w,main.h,(x,y)->x>=b[0]&&x<b[2]&&y>=b[1]&&y<b[3]?0xff999999:main.get(x,y),null).home,"both home landmarks are required");}
  PhaseEngine e=new PhaseEngine();check(KaitakuPreset.install(e),"factory installed");e.strictRuntime=true;check(e.validateStart().isEmpty(),"initial graph validates: "+e.validateStart());
  check(!KaitakuPreset.install(e),"installation cannot overwrite configured graph");
  check(e.phases.size()==4&&e.current.equals(KaitakuPreset.P+"check"),"nonempty independent initial state");
  check(KaitakuLayout.choose(e,KaitakuPreset.APP,refs)!=null,"factory graph uses its own sampler");
  PhaseEngine edited=e.runtimeCopy();edited.rule(KaitakuPreset.P+"check-home").reference=new float[]{.5f};check(KaitakuLayout.choose(edited,KaitakuPreset.APP,refs)==null,"custom graph keeps its existing sampling semantics");
  for(String id:e.phases.keySet())check(!id.startsWith(FarmPreset.P)&&!id.startsWith(UsagiPreset.PREFIX),"no cross-game state");
  AdCloseAutomaton.install(e,KaitakuPreset.APP);check(e.rule(KaitakuPreset.P+"ad-"+AdCloseAutomaton.RIGHT)!=null,"ad exit rules attached");
  KaitakuLayout layout=new KaitakuLayout(e,refs);e.enter(KaitakuPreset.P+"check",0);
  Map<String,float[]> samples=layout.sampleRelative(e.activeRegions(),main.w,main.h,main,1000,null);e.observe(samples,1000);check(e.poll()==null,"one image does not authorize transition");
  e.observe(layout.sampleRelative(e.activeRegions(),main.w,main.h,main,1350,null),1350);PhaseEngine.Watch event=e.poll();check(event!=null&&event.nextPhase.equals(KaitakuPreset.P+"main"),"second home image enters play");e.finish(event,1350);
  check(e.phases.get(e.current).markersDuring,"main uses independent continuous tapper");
  float[] point=layout.mainPointRelative(samples,main.w,main.h);check(point!=null&&point[0]>main.w*.45&&point[0]<main.w*.55&&point[1]>main.h*.72&&point[1]<main.h*.76,"main point inside observed button");
  check(layout.mainPointRelative(samples,main.w+1,main.h)==null,"resize invalidates saved point");
  Map<String,float[]> missing=new HashMap<>();missing.put(KaitakuPreset.HOME,new float[]{0});e.observe(missing,1700);check(e.poll()==null,"first missing image does not invent an event");e.observe(missing,2050);event=e.poll();check(event!=null&&event.nextPhase.equals(KaitakuPreset.P+"check"),"lost home returns to classification");e.finish(event,2050);
  Image dialog=images.get(175);samples=layout.sampleRelative(e.activeRegions(),dialog.w,dialog.h,dialog,2400,null);e.observe(samples,2400);e.observe(layout.sampleRelative(e.activeRegions(),dialog.w,dialog.h,dialog,2750,null),2750);event=e.poll();check(event!=null&&event.action==PhaseEngine.CENTER&&event.regionId.equals(KaitakuPreset.CONTINUE),"continue is the only popup input");
  point=layout.pointRelative(event,samples,dialog.w,dialog.h);check(point!=null&&point[1]>dialog.h*.736&&point[1]<dialog.h*.757,"continue tap below optional ad button");
  check(e.confirmsAction(event,e.current,e.epoch,samples),"popup input requires current evidence");check(!e.confirmsAction(event,e.current,e.epoch,missing),"lost popup evidence cancels input");
  e.finish(event,2750);check(!e.phases.get(e.current).markersDuring,"popup click waits for verification");
  // A desktop/window origin is added exactly once; native-screen coordinates
  // remain a candidate only when their evidence fits inside the current window.
  int ox=120,oy=40,w=900,h=1500;RegionSampler.Pixels desktop=(x,y)->x>=ox&&x<ox+main.w&&y>=oy&&y<oy+main.h?main.get(x-ox,y-oy):0xff999999;
  samples=layout.sampleRelative(e.activeRegions(),w,h,desktop,3100,new int[]{ox,oy,ox+main.w,oy+main.h});point=layout.mainPointRelative(samples,w,h);check(point!=null&&Math.abs(point[0]-(ox+main.w*.5))<3&&Math.abs(point[1]-(oy+main.h*.74))<3,"window offset applied once");
  check(PresetRegistry.supports(KaitakuPreset.APP)&&PresetRegistry.prefix(KaitakuPreset.APP).equals(KaitakuPreset.P),"target registry recognizes package");check(GameTarget.label(KaitakuPreset.APP).equals("クラシル開拓うさぎ"),"target has explicit label");
  System.out.println("KaitakuIncidentTest: "+checks+" checks passed; physical input and reward accounting unverified");
 }
}
