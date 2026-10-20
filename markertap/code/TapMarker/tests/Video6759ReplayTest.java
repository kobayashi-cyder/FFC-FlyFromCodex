package jp.tapmarker;
import java.io.*;
import java.util.*;
import java.util.zip.*;

/** Recorded claimable mission holdouts, plus explicitly simulated follow-up events. */
public final class Video6759ReplayTest {
 static final String P=UsagiPreset.PREFIX;
 static int checks;
 static final Map<String,VirtualPresetProfileTest.Image> frames=new LinkedHashMap<>();
 static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 static void load()throws Exception{
  UsagiControlReplayTest.load();
  try(DataInputStream in=new DataInputStream(new GZIPInputStream(new FileInputStream("tests/video6759-replay.bin.gz")))){
   int n=in.readInt();for(int i=0;i<n;i++)frames.put(in.readUTF(),new VirtualPresetProfileTest.Image(in));
   check(n==10&&in.read()==-1,"training frame plus nine held-out frames");
  }
 }
 static void page(int width,int height,RegionSampler.Pixels pixels,int[] window,String label){
  int left=window==null?0:window[0],top=window==null?0:window[1],cw=window==null?width:window[2]-left,ch=window==null?height:window[3]-top;
  for(String state:new String[]{"verify","ad","classify","mission-opening","main"}){
   UsagiControlReplayTest.Run r=new UsagiControlReplayTest.Run(state);
   check(r.observe(width,height,pixels,1000,window)==null,"first fresh observation waits: "+label+" / "+state);
   PhaseEngine.Watch w=r.observe(width,height,pixels,1350,window);
   check(w!=null&&w.action==PhaseEngine.WAIT&&w.nextPhase.equals(P+(state.equals("main")?"classify":"missions")),"claimable mission exits recovery: "+label+" / "+state+" / "+r.e.diagnostic);
   check(r.samples.containsKey(UsagiMissionEvidence.PAGE)&&!r.home()&&r.layout.mainPointRelative(r.samples,width,height)==null,"mission page cannot start ordinary taps");
   check(r.side.observe(r.samples,1350,1350,false)==null,"mission page cannot request amount upgrades");
   if(state.equals("main"))continue;
   MissionJourney journey=new MissionJourney();journey.route(w.nextPhase);r.e.finish(w,1400);
   check(r.observe(width,height,pixels,1600,window)==null,"Get needs its own second observation");
   w=r.observe(width,height,pixels,1950,window);
   check(w!=null&&w.id.equals(P+"ゲットのみタップ")&&w.action==PhaseEngine.CENTER&&w.nextPhase.equals(P+"claim-wait"),"Get is distinct from Play and page Back: "+label+" / "+r.e.diagnostic);
   float[] point=r.point(w,width,height);
   check(point!=null&&point[0]>left+cw*.68f&&point[0]<left+cw*.94f&&point[1]>top+ch*.55f&&point[1]<top+ch*.63f,"Get point is inside the observed stage reward button: "+Arrays.toString(point));
   r.sample(width,height,pixels,2050,window);
   check(r.e.confirmsAction(w,r.e.current,r.e.epoch,r.samples)&&r.point(w,width,height)!=null,"dispatch verifies current Get pixels");
   check(!UsagiPreset.leavesMissions(w)&&journey.blocksMain(),"Get acknowledgement cannot finish the mission route");
   r.e.finish(w,2100);check(r.e.current.equals(P+"claim-wait")&&journey.blocksMain(),"input completion waits for actual reward display");
  }
 }
 static void recorded(){
  for(Map.Entry<String,VirtualPresetProfileTest.Image> row:frames.entrySet())if(!row.getKey().equals("0")){
   VirtualPresetProfileTest.Image im=row.getValue();page(im.width,im.height,im,null,row.getKey()+"s");
  }
 }
 static void synthetic(){
  VirtualPresetProfileTest.Image im=frames.get("8");
  for(int[] size:new int[][]{{166,374},{540,1212},{720,1616}})page(size[0],size[1],FarmDialogReplayTest.resize(im,size[0],size[1],true),null,"synthetic resize "+size[0]);
  int width=640,height=900,left=90,top=60;
  RegionSampler.Pixels desktop=(x,y)->x>=left&&y>=top&&x<left+im.width&&y<top+im.height?im.get(x-left,y-top):0xffe0e4e7;
  page(width,height,desktop,new int[]{left,top,left+im.width,top+im.height},"synthetic moved window");
  for(boolean hideTitle:new boolean[]{false,true}){
   RegionSampler.Pixels masked=(x,y)->hideTitle&&x>im.width*.27f&&x<im.width*.74f&&y>im.height*.11f&&y<im.height*.16f?0xffcba0c2:!hideTitle&&x>im.width*.65f&&x<im.width*.96f&&y>im.height*.53f&&y<im.height*.64f?0xffaaaaaa:im.get(x,y);
   UsagiControlReplayTest.Run r=new UsagiControlReplayTest.Run("missions");
   r.sample(im.width,im.height,im,1000,null);PhaseEngine.Watch get=r.e.rule(P+"ゲットのみタップ");
   check(r.e.confirmsAction(get,r.e.current,r.e.epoch,r.samples)&&r.point(get,im.width,im.height)!=null,"original Get is actionable");
   r.sample(im.width,im.height,masked,1400,null);
   check(!r.e.confirmsAction(get,r.e.current,r.e.epoch,r.samples)&&r.point(get,im.width,im.height)==null,"hidden title or Get invalidates queued input");
  }
  RegionSampler.Pixels noLabels=(x,y)->x>im.width*.65f&&y>im.height*.5f&&y<im.height*.92f?0xffcccccc:im.get(x,y);
  UsagiControlReplayTest.Run r=new UsagiControlReplayTest.Run("missions");
  r.observe(im.width,im.height,noLabels,1000,null);PhaseEngine.Watch back=r.observe(im.width,im.height,noLabels,1350,null);
  check(back!=null&&back.id.equals(UsagiMissionEvidence.BACK)&&UsagiPreset.leavesMissions(back),"page with no recognized Get/Play uses its verified navigation arrow");
  float[] p=r.point(back,im.width,im.height);check(p!=null&&p[0]<im.width*.08f&&p[1]>im.height*.11f&&p[1]<im.height*.17f,"Back point stays in the observed header arrow");
  MissionJourney journey=new MissionJourney();journey.route(P+"missions");r.e.finish(back,1400);journey.completedPlay();check(journey.route(P+"main").equals(P+"main")&&!journey.blocksMain(),"verified page exit releases the pending route after home confirmation");
  RegionSampler.Pixels movedGet=(x,y)->{
   float nx=x/(float)im.width,ny=y/(float)im.height;
   if(nx>.70f&&nx<.92f&&ny>.55f&&ny<.62f)return 0xffaaaaaa;
   if(nx>.70f&&nx<.92f&&ny>.718f&&ny<.788f)return im.get(x,Math.max(0,y-Math.round(im.height*.168f)));
   return im.get(x,y);
  };
  r=new UsagiControlReplayTest.Run("missions");r.observe(im.width,im.height,movedGet,1000,null);PhaseEngine.Watch moved=r.observe(im.width,im.height,movedGet,1350,null);
  check(moved!=null&&moved.id.equals(P+"ゲットのみタップ"),"a synthetic Get on the next visible task remains a reward, not Open or Play");
  p=r.point(moved,im.width,im.height);check(p!=null&&p[1]>.71f*im.height&&p[1]<.80f*im.height,"Get coordinates follow the current row");
  for(String id:new String[]{"0006","0008","0033","0086","0190"}){
   VirtualPresetProfileTest.Image other=Video6677ReplayTest.images.get(id);r=new UsagiControlReplayTest.Run("verify");r.sample(other.width,other.height,other,1000,null);
   check(!r.e.confirmsAction(r.e.rule(P+"ゲットのみタップ"),r.e.current,r.e.epoch,r.samples)&&!r.samples.containsKey(P+"get"+AdaptiveLayout.BOUNDS),"ordinary game, CM, modal reward and scrolled Open labels do not reuse current Get: "+id);
  }
  VirtualPresetProfileTest.Image reward=Video6677ReplayTest.images.get("0086");
  RegionSampler.Pixels resized=FarmDialogReplayTest.resize(im,reward.width,reward.height,true);
  RegionSampler.Pixels visibleHeader=(x,y)->y<reward.height*.18f?resized.get(x,y):reward.get(x,y);
  r=new UsagiControlReplayTest.Run("verify");r.observe(reward.width,reward.height,visibleHeader,1000,null);PhaseEngine.Watch modal=r.observe(reward.width,reward.height,visibleHeader,1350,null);
  check(!r.samples.containsKey(UsagiMissionEvidence.PAGE)&&modal!=null&&modal.nextPhase.equals(P+"reward"),"a reward popup wins even when mission title and Back remain visible (synthetic composite)");
 }
 static long step(UsagiControlReplayTest.Run r,VirtualPresetProfileTest.Image im,MissionJourney journey,long now,String next){
  check(r.observe(im.width,im.height,im,now,null)==null,"synthetic journey needs fresh confirmation in "+r.e.current);
  PhaseEngine.Watch watch=r.observe(im.width,im.height,im,now+350,null);
  check(watch!=null&&watch.nextPhase.equals(P+next),"synthetic journey reaches "+next+" / "+r.e.current+" / "+r.e.diagnostic);
  if(watch.action==PhaseEngine.CENTER){r.sample(im.width,im.height,im,now+450,null);check(r.e.confirmsAction(watch,r.e.current,r.e.epoch,r.samples)&&r.point(watch,im.width,im.height)!=null,"journey action rechecks live pixels");}
  r.e.finish(watch,now+500);if(UsagiPreset.leavesMissions(watch))journey.completedPlay();
  String route=journey.route(r.e.current);if(!route.equals(r.e.current))r.e.enter(route,now+500);
  return now+800;
 }
 static void journeys(){
  UsagiControlReplayTest.Run r=new UsagiControlReplayTest.Run("verify");MissionJourney journey=new MissionJourney();long now=1000;
  for(int cycle=0;cycle<3;cycle++){
   if(cycle>0)r.e.enter(P+"verify",now);
   now=step(r,frames.get("8"),journey,now,"missions");
   now=step(r,frames.get("8"),journey,now,"claim-wait");
   check(journey.blocksMain()&&!UsagiPreset.isMain(r.e.current),"Get acknowledgement retains collection intent");
   if(cycle==1){r.e.enter(P+"ad",now);check(journey.blocksMain(),"simulated collection CM retains the mission task");}
   now=step(r,Video6677ReplayTest.images.get("0086"),journey,now,"reward");
   now=step(r,Video6677ReplayTest.images.get("0086"),journey,now,"classify");
   now=step(r,UsagiControlReplayTest.missions.get("8"),journey,now,"missions");
   now=step(r,UsagiControlReplayTest.missions.get("8"),journey,now,"verify");
   VirtualPresetProfileTest.Image home=Video6677ReplayTest.images.get("0006");now=step(r,home,journey,now,"main");
   r.sample(home.width,home.height,home,now,null);
   check(!journey.blocksMain()&&r.home()&&r.layout.mainPointRelative(r.samples,home.width,home.height)!=null,"synthetic cycle "+cycle+" resumes the main loop after Get, reward Close and Play");
  }
 }
 public static void main(String[] args)throws Exception{load();recorded();synthetic();journeys();System.out.println("Video6759ReplayTest: "+checks+" checks passed; nine held-out static-scene preview frames; resize/window/occlusion/acknowledgement and three follow-up reward/home journeys are synthetic; no Android input or completed collection demonstrated");}
}
