package jp.tapmarker;
import java.io.*;import java.util.*;import java.util.zip.*;

/** Recorded preview crops; subsequent observations, sizes, masks and completions are simulated. */
public final class Video6772ReplayTest {
 static int checks;static boolean baseline;
 static Map<String,float[]> targets,evidence;
 static final Map<String,VirtualPresetProfileTest.Image> frames=new LinkedHashMap<>();
 static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 static String pkg(String app){return app.equals("usagi")?UsagiPreset.APP:app.equals("farm")?FarmPreset.APP:app.equals("coin")?CoinPreset.APP:PoipoiPreset.APP;}
 static void load()throws Exception{
  targets=VirtualPresetProfileTest.canonical("tests/visual-targets.bin");evidence=VirtualPresetProfileTest.canonical("tests/current-layout-evidence.bin");
  try(DataInputStream in=new DataInputStream(new GZIPInputStream(new FileInputStream("tests/video6772-replay.bin.gz")))){int n=in.readInt();for(int i=0;i<n;i++)frames.put(in.readUTF(),new VirtualPresetProfileTest.Image(in));check(n==48&&in.read()==-1,"48 complete recorded preview crops");}
 }
 static final class Run {
  final PhaseEngine e=new PhaseEngine();final AdaptiveLayout layout;final AdExitPolicy ad=new AdExitPolicy();final String app;Map<String,float[]> samples;
  Run(String name,String state)throws Exception{
   app=pkg(name);Map<String,float[]> refs=VirtualPresetProfileTest.canonical(name.equals("usagi")?"tests/usagi-current-references.bin":name.equals("farm")?"tests/farm-replay.bin":name.equals("coin")?"tests/video6687-references.bin":"tests/image6727-references.bin");
   if(name.equals("usagi"))UsagiPreset.install(e,refs);else if(name.equals("farm"))FarmPreset.install(e,refs);else if(name.equals("coin"))CoinPreset.install(e,refs);else PoipoiPreset.install(e,refs);
   CoinNoAdPolicy.apply(e);layout=AdaptiveLayout.install(e,app,refs,evidence);layout.configureTargets(app,targets);AdCloseAutomaton.install(e,app);e.strictRuntime=true;e.enter(PresetRegistry.prefix(app)+state,0);
  }
  PhaseEngine.Watch observe(int w,int h,RegionSampler.Pixels pixels,long at){
   List<PhaseEngine.Region> regions=e.activeRegions();PhaseEngine.Region home=e.regions.get(layout.profile.region);if(!regions.contains(home))regions.add(home);
   samples=layout.sampleRelative(regions,w,h,pixels,at);ad.filter(e,samples,w,h,at,at);e.observe(samples,at);return ad.withFallback(e,e.poll(),samples,at);
  }
  PhaseEngine.Watch action(int w,int h,RegionSampler.Pixels pixels){
   for(long at=1000;at<=3500;at+=350){PhaseEngine.Watch event=observe(w,h,pixels,at);if(event==null)continue;if(event.action==PhaseEngine.CENTER)return event;check(event.action==PhaseEngine.WAIT,"popup never starts Back, relaunch or an ad");e.finish(event,at);}
   return null;
  }
 }
 static boolean positive(String app,String time){return app.equals("usagi")||app.equals("poipoi")||app.equals("farm")&&Arrays.asList("21.84","24.96","28.07","31.19","34.31").contains(time)||app.equals("coin")&&Arrays.asList("24.96","28.07","31.19","34.31").contains(time);}
 static void recorded()throws Exception{
  for(Map.Entry<String,VirtualPresetProfileTest.Image> row:frames.entrySet()){
   String[] key=row.getKey().split(":");if(!positive(key[0],key[1]))continue;VirtualPresetProfileTest.Image im=row.getValue();Run r=new Run(key[0],"verify");
   PhaseEngine.Watch action=r.action(im.width,im.height,im);
   if(baseline){System.out.println(row.getKey()+" -> "+(action==null?"NO_ACTION":action.id));continue;}
   check(action!=null,"recorded popup is actionable: "+row.getKey()+" / "+r.e.diagnostic);
   boolean advert=key[0].equals("poipoi")&&key[1].equals("18.72");
   String id=advert?AdCloseAutomaton.RIGHT:key[0].equals("poipoi")?PoipoiPreset.P+"bonus":GamePopupEvidence.closeId(r.app);
   check(action.regionId.equals(id),"only ordinary continuation or the measured Close: "+row.getKey()+" / "+action.id);
   float[] p=advert?AdCloseAutomaton.controlPoint(id,r.samples,im.width,im.height):GamePopupEvidence.point(id,r.samples,im.width,im.height);check(p!=null,"current frame supplies a size-tagged point");
   if(advert)check(p[0]>.9f*im.width&&p[1]<.05f*im.height,"intervening advertisement retains its top-right X");
   else if(key[0].equals("poipoi"))check(p[0]>.3f*im.width&&p[0]<.7f*im.width&&p[1]>.57f*im.height&&p[1]<.62f*im.height,"normal continuation is below the yellow ad button");
   else if(key[0].equals("farm"))check(p[0]>.86f*im.width&&p[0]<.98f*im.width&&p[1]>.16f*im.height&&p[1]<.29f*im.height,"announcement uses the actual circular X");
   else check(p[0]>.05f*im.width&&p[0]<.43f*im.width&&p[1]>.97f*im.height&&p[1]<im.height-1,"footer tap is on the visible button, inside the frame");
   check(!r.samples.containsKey(AdaptiveLayout.MAIN),"popup suppresses continuous taps");
   check(r.e.confirmsAction(action,r.e.current,r.e.epoch,r.samples),"action is justified by current pixels");
   check(GamePopupEvidence.point(id,r.samples,im.width+1,im.height)==null,"resizing invalidates old coordinates");
   r.e.finish(action,4000);check(!r.e.current.endsWith("main"),"input completion alone does not declare home restored");
  }
 }
 static void simulated()throws Exception{
  for(String name:new String[]{"usagi","poipoi","farm","coin"}){
   VirtualPresetProfileTest.Image im=frames.get(name+":28.07");
   for(int width:new int[]{320,540})for(String state:new String[]{"ad","verify","main"}){
    int height=Math.round(width*im.height/(float)im.width);RegionSampler.Pixels p=FarmDialogReplayTest.resize(im,width,height,true);Run r=new Run(name,state);
    for(int i=0;i<3;i++)r.ad.recovery.sent(i,false);for(int i=0;i<2;i++)r.ad.recovery.sent(3+i,true);
    check(r.ad.recovery.exhausted(),"synthetic recovery budget is exhausted before the popup");
    PhaseEngine.Watch action=r.action(width,height,p);check(action!=null,"an observed Close/continuation still works after recovery limits: "+name+" / "+width+" / "+state);
   }
   for(boolean control:new boolean[]{false,true}){
    RegionSampler.Pixels missing=(x,y)->control&&y>im.height*.94f||!control&&name.equals("farm")&&y>im.height*.22f&&y<im.height*.34f||!control&&name.equals("poipoi")&&y>im.height*.21f&&y<im.height*.28f?0xff555555:im.get(x,y);
    if(name.equals("poipoi")&&control)missing=(x,y)->y>im.height*.57f&&y<im.height*.63f?0xff555555:im.get(x,y);
    if(name.equals("farm")&&control)missing=(x,y)->x>im.width*.85f&&y>im.height*.16f&&y<im.height*.29f?0xff555555:im.get(x,y);
    Run r=new Run(name,"verify");GamePopupEvidence quick=new GamePopupEvidence(r.app,r.e,targets);Map<String,float[]> s=quick.quick(im.width,im.height,missing,null);
    if(control||name.equals("poipoi")||name.equals("farm"))check(!GamePopupEvidence.present(s),"missing title or control cannot reuse the previous point: "+name+" / "+control);
   }
  }
  for(String name:new String[]{"usagi","coin"}){
   Run r=new Run(name,"verify");GamePopupEvidence quick=new GamePopupEvidence(r.app,r.e,targets);
   for(int color:new int[]{0xff000000,0xffffffff,0xff999999})check(!GamePopupEvidence.present(quick.quick(320,718,(x,y)->color,null)),"plain colors cannot mimic a clipped footer");
  }
 }
 public static void main(String[] args)throws Exception{baseline=args.length>0;load();recorded();if(!baseline)simulated();System.out.println("Video6772ReplayTest: "+checks+" checks; recorded preview crops and simulated repeats/resizes/masks/completions only; Android input delivery and rewards unverified");}
}
