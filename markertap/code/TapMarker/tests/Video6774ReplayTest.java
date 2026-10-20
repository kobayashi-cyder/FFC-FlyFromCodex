package jp.tapmarker;
import java.io.*;import java.util.*;import java.util.zip.*;

/** 6774 recorded pixels; generated sizes, translations and input completions are simulations. */
public final class Video6774ReplayTest {
 static int checks;
 static final Map<String,VirtualPresetProfileTest.Image> frames=new LinkedHashMap<>();
 static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 static void load()throws Exception{
  Video6772ReplayTest.load();
  try(DataInputStream in=new DataInputStream(new GZIPInputStream(new FileInputStream("tests/video6774-replay.bin.gz")))){
   int n=in.readInt();for(int i=0;i<n;i++)frames.put(in.readUTF(),new VirtualPresetProfileTest.Image(in));check(n==52&&in.read()==-1,"52 complete recorded preview crops");
  }
 }
 static void recorded(boolean baseline)throws Exception{
  for(Map.Entry<String,VirtualPresetProfileTest.Image> row:frames.entrySet()){
   String[] key=row.getKey().split(":");VirtualPresetProfileTest.Image im=row.getValue();
   if(!key[0].equals("coin")){if(baseline){Video6772ReplayTest.Run r=new Video6772ReplayTest.Run(key[0],"verify");PhaseEngine.Watch w=r.action(im.width,im.height,im);System.out.println(row.getKey()+" -> "+(w==null?"NO_ACTION":w.id));}continue;}
   Video6772ReplayTest.Run r=new Video6772ReplayTest.Run("coin","verify");PhaseEngine.Watch action=r.action(im.width,im.height,im);
   if(baseline){System.out.println(row.getKey()+" -> "+(action==null?"NO_ACTION":action.id));continue;}
   if(key[1].equals("00")||key[1].equals("02")){check(action==null||!action.regionId.equals(CoinPreset.P+"offline"),"covered offline screen never claims through an advertisement");continue;}
   check(action!=null,"recorded offline reward must be actionable: "+row.getKey()+" / "+r.e.diagnostic);
   check(action.regionId.equals(CoinPreset.P+"offline"),"ordinary offline acquisition, not a multiplier: "+row.getKey());
   float[] p=GamePopupEvidence.point(action.regionId,r.samples,im.width,im.height);
   check(p!=null&&p[0]>im.width*.43f&&p[0]<im.width*.58f&&p[1]>im.height*.75f&&p[1]<im.height*.81f,"point is on the visible Acquire label below the yellow CM button");
   check(!r.samples.containsKey(AdaptiveLayout.MAIN),"offline dialog holds ordinary game tapping");
   check(r.e.confirmsAction(action,r.e.current,r.e.epoch,r.samples),"fresh current evidence permits the claim");
   check(!r.e.confirmsAction(action,r.e.current,r.e.epoch,new HashMap<>()),"disappearance cancels pending input");
   check(GamePopupEvidence.point(action.regionId,r.samples,im.width+1,im.height)==null,"resizing invalidates old point");
   r.e.finish(action,4000);check(!r.e.current.equals(CoinPreset.P+"main"),"input completion does not assert reward credit or home");
  }
 }
 static void simulated()throws Exception{
  VirtualPresetProfileTest.Image im=frames.get("coin:06");
  for(int width:new int[]{268,320,540})for(String state:new String[]{"check","main","fever","ad","verify","external","mission-ad","mission-opening","claim-wait","reward"}){
   int height=Math.round(width*im.height/(float)im.width);RegionSampler.Pixels p=FarmDialogReplayTest.resize(im,width,height,true);
   Video6772ReplayTest.Run r=new Video6772ReplayTest.Run("coin",state);int size=r.e.phases.size();GamePopupEvidence.install(r.e,CoinPreset.APP);check(r.e.phases.size()==size&&r.e.validate().isEmpty(),"idempotent runtime installation");
   PhaseEngine.Watch w=r.action(width,height,p);check(w!=null&&w.regionId.equals(CoinPreset.P+"offline"),"normal acquisition at "+width+" / "+state);
   check(w.nextPhase.equals(CoinPreset.recoveryTarget(CoinPreset.P+state)),"mission intent survives an offline interruption");
  }
  Video6772ReplayTest.Run r=new Video6772ReplayTest.Run("coin","verify");GamePopupEvidence quick=new GamePopupEvidence(CoinPreset.APP,r.e,Video6772ReplayTest.targets);
  RegionSampler.Pixels amount=(x,y)->x>im.width*.45f&&x<im.width*.83f&&y>im.height*.38f&&y<im.height*.47f?0xff111111:im.get(x,y);
  check(GamePopupEvidence.point(CoinPreset.P+"offline",quick.quick(im.width,im.height,amount,null),im.width,im.height)!=null,"amount digits are excluded from recognition");
  for(int part=0;part<3;part++){
   final int mask=part;RegionSampler.Pixels p=(x,y)->mask==0&&y>im.height*.24f&&y<im.height*.34f||mask==1&&y>im.height*.74f&&y<im.height*.81f||mask==2&&y>im.height*.34f&&y<im.height*.64f?0xff555555:im.get(x,y);
   check(GamePopupEvidence.point(CoinPreset.P+"offline",quick.quick(im.width,im.height,p,null),im.width,im.height)==null,"occluded title, ordinary control or body refuses claim: "+part);
  }
  for(int color:new int[]{0xff000000,0xffffffff,0xff999999,0xffffe888})check(!GamePopupEvidence.present(quick.quick(320,718,(x,y)->color,null)),"plain colors are not claims");
  for(Map.Entry<String,VirtualPresetProfileTest.Image> row:frames.entrySet())if(!row.getKey().startsWith("coin:")){
   VirtualPresetProfileTest.Image other=row.getValue();check(new CoinOfflineEvidence(Video6772ReplayTest.targets).locate(other.width,other.height,other)==null,"another game's pixels do not authorize Coin Acquire: "+row.getKey());
  }
  for(Map.Entry<String,VirtualPresetProfileTest.Image> row:Video6772ReplayTest.frames.entrySet())if(row.getKey().startsWith("coin:")){
   VirtualPresetProfileTest.Image other=row.getValue();check(new CoinOfflineEvidence(Video6772ReplayTest.targets).locate(other.width,other.height,other)==null,"older Coin advertisements are not offline claims: "+row.getKey());
  }
  int width=320,height=718,fw=400,fh=810,left=37,top=42;RegionSampler.Pixels resized=FarmDialogReplayTest.resize(im,width,height,true);
  RegionSampler.Pixels moved=(x,y)->x>=left&&x<left+width&&y>=top&&y<top+height?resized.get(x-left,y-top):0xffeeeeee;
  Map<String,float[]> s=quick.quick(fw,fh,moved,new int[]{left,top,left+width,top+height});float[] p=GamePopupEvidence.point(CoinPreset.P+"offline",s,fw,fh);
  check(p!=null&&p[0]>left+width*.43f&&p[0]<left+width*.58f&&p[1]>top+height*.75f&&p[1]<top+height*.81f,"point follows measured window offset");
 }
 static PhaseEngine.Watch step(Video6772ReplayTest.Run r,VirtualPresetProfileTest.Image im,long now){return r.observe(im.width,im.height,im,now);}
 static void persistentExit()throws Exception{
  VirtualPresetProfileTest.Image im=frames.get("usagi:06");Video6772ReplayTest.Run r=new Video6772ReplayTest.Run("usagi","verify");
  check(step(r,im,1000)==null,"footer needs a second observation");PhaseEngine.Watch w=step(r,im,1350);check(w!=null&&w.regionId.equals(GamePopupEvidence.closeId(UsagiPreset.APP)),"first observed footer close");r.ad.controlSent(w,1350);r.e.finish(w,1350);
  check(step(r,im,1700)==null,"footer response cooldown");check(step(r,im,2400)==null,"second footer input also needs two observations");w=step(r,im,2750);check(w!=null&&w.action==PhaseEngine.CENTER,"one retry for an unchanged Close");r.ad.controlSent(w,2750);r.e.finish(w,2750);
  for(long now:new long[]{3600,6100,8600}){
   w=step(r,im,now);check(w!=null&&w.action==PhaseEngine.RETURN_APP,"unchanged footer proceeds to bounded ad recovery");
   check(r.e.current.equals(UsagiPreset.PREFIX+"ad"),"ordinary taps remain held in ad recovery");
   int action=r.ad.choose(r.e,w,r.samples,now);check(action==(now<8600?PhaseEngine.BACK:PhaseEngine.RETURN_APP),"two Back requests then target app restoration");
   check(!r.ad.preferVisibleBranch(r.e,new GamePopupEvidence(UsagiPreset.APP,r.e,Video6772ReplayTest.targets).quick(im.width,im.height,im,null),im.width,im.height,now+100,now+100),"exhausted footer cannot cancel restoration indefinitely");
   if(action==PhaseEngine.BACK){r.ad.sent(now);r.e.finish(w,now);}
  }
  check(r.ad.popupExits.count==2,"unchanged footer never dispatches a third tap");
  PopupExitAttempts other=new PopupExitAttempts();check(other.count==0,"another display owns an independent retry counter");
  for(long now:new long[]{9000,9400})r.ad.filter(r.e,new HashMap<>(),im.width,im.height,now,now);
  check(r.ad.popupExits.count==2,"missing detections cannot replenish an unresolved footer episode");
  Map<String,float[]> named=new HashMap<>();String close=GamePopupEvidence.closeId(CoinPreset.APP);GamePopupEvidence.put(named,close,80,710,320,718);GamePopupEvidence.namedFooter(named,close,704,718,0,718);
  check(named.containsKey(GamePopupEvidence.FOOTER_EXIT),"a named clipped footer joins the same response budget");
  PopupExitAttempts budget=new PopupExitAttempts();budget.filter(named,320,718,1000,1000);budget.sent(close,1000);budget.filter(new HashMap<>(named),320,718,2000,2000);budget.sent(close,2000);Map<String,float[]> remaining=new HashMap<>(named);budget.filter(remaining,320,718,3000,3000);
  check(budget.failed(3000)&&!GamePopupEvidence.present(remaining),"the named-control fast path cannot repeat Close indefinitely");
  named.clear();GamePopupEvidence.put(named,CoinPreset.P+"offline",160,710,320,718);GamePopupEvidence.namedFooter(named,CoinPreset.P+"offline",704,718,0,718);check(!named.containsKey(GamePopupEvidence.FOOTER_EXIT),"normal reward acquisition is not charged as an ad exit");
 }
 public static void main(String[] args)throws Exception{load();recorded(args.length>0);if(args.length==0){simulated();persistentExit();}System.out.println("Video6774ReplayTest: "+checks+" checks; recorded preview pixels plus simulated resizes, masks, unchanged-exit recovery and completions; no actual Android input or reward credit verification");}
}
