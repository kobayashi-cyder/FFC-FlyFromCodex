package jp.tapmarker;
import java.io.*;import java.util.*;import java.util.zip.*;

/** Attached JPEG pixels; input completion and clock progression are simulated. */
public final class Incident66ReplayTest {
 static int checks;static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 static Map<String,VirtualPresetProfileTest.Image> load()throws Exception{
  Map<String,VirtualPresetProfileTest.Image> out=new LinkedHashMap<>();
  try(DataInputStream in=new DataInputStream(new GZIPInputStream(new FileInputStream("tests/incident66-replay.bin.gz")))){int n=in.readInt();for(int i=0;i<n;i++)out.put(in.readUTF(),new VirtualPresetProfileTest.Image(in));check(n==4&&in.read()==-1,"four complete attached images");}return out;
 }
 static PhaseEngine.Watch observe(Video6772ReplayTest.Run r,VirtualPresetProfileTest.Image im,long at){return r.observe(im.width,im.height,im,at);}
 public static void main(String[] args)throws Exception{
  Video6772ReplayTest.load();Map<String,VirtualPresetProfileTest.Image> frames=load();
  VirtualPresetProfileTest.Image before=frames.get("frame-01.jpg"),moved=frames.get("frame-03.jpg"),stuck=frames.get("frame-07.jpg");
  Video6772ReplayTest.Run r=new Video6772ReplayTest.Run("farm","verify");String id=GamePopupEvidence.closeId(FarmPreset.APP);
  for(int i=0;i<3;i++)r.ad.recovery.sent(i,false);for(int i=0;i<2;i++)r.ad.recovery.sent(3+i,true);
  check(observe(r,before,1000)==null,"one position observation cannot dispatch Close");
  PhaseEngine.Watch close=null;for(long now:new long[]{1350,1700,2050}){PhaseEngine.Watch w=observe(r,before,now);if(w!=null&&w.action==PhaseEngine.CENTER){close=w;break;}}
  check(close!=null&&close.regionId.equals(id),"attached announcement exposes its real Close after settling");
  float[] point=GamePopupEvidence.point(id,r.samples,before.width,before.height);check(point!=null&&Math.abs(point[0]-295)<1&&Math.abs(point[1]-151.5)<1,"original measured point reproduced");
  check(r.samples.get(id+GamePopupEvidence.VISUAL_DISTANCE)!=null&&r.samples.get(id+GamePopupEvidence.EVIDENCE_BOUNDS)!=null,"farm promo now retains visual distance and evidence bounds");
  r.ad.controlSent(close,2100);r.e.finish(close,2100);
  observe(r,before,2450);check(GamePopupEvidence.point(id,r.samples,320,718)==null,"dispatch cooldown prevents immediate duplicate tap");
  check(r.ad.popupReceipt.report().contains("入力後も表示あり"),"completion does not claim disappearance");
  observe(r,moved,3200);check(GamePopupEvidence.point(id,r.samples,320,718)==null,"moving X must settle again");
  PhaseEngine.Watch retry=null;long retryAt=0;for(long now:new long[]{3550,3900,4250}){PhaseEngine.Watch w=observe(r,moved,now);if(w!=null&&w.action==PhaseEngine.CENTER){retry=w;retryAt=now;break;}}
  check(retry!=null&&retry.regionId.equals(id),"unchanged announcement permits a bounded retry");
  point=GamePopupEvidence.point(id,r.samples,320,718);check(point!=null&&Math.abs(point[0]-289)<1&&Math.abs(point[1]-159.5)<1,"retry uses new coordinates, not the last dispatch");
  r.ad.controlSent(retry,retryAt);r.e.finish(retry,retryAt);
  observe(r,moved,retryAt+1100);check(GamePopupEvidence.point(id,r.samples,320,718)==null&&r.ad.popupReceipt.failed(retryAt+1100),"two unsuccessful requests cannot loop on the same control");
  observe(r,stuck,retryAt+2000);observe(r,stuck,retryAt+2400);
  check(r.ad.popupReceipt.report().contains("消失を確認")&&!RecoveryEvidence.home(r.e,r.samples),"absence of announcement is distinct from verified home");
  DisplayRecovery recovery=new DisplayRecovery();recovery.poll(r.ad.recovery.exhausted(),10000);check(recovery.poll(true,40000)==DisplayRecovery.REBUILD,"reported exhausted loading frame reaches display replacement");int ticket=recovery.begin(40000);
  check(recovery.finished(ticket,true,41000),"synthetic creation completion");r.ad.newDisplay();check(r.ad.recovery.exhausted(),"display replacement preserves the original restart budget");
  r.e.invalidateObservation();close=null;for(long now:new long[]{42000,42350,42700,43050}){PhaseEngine.Watch w=observe(r,moved,now);if(w!=null&&w.action==PhaseEngine.CENTER){close=w;break;}}
  check(close!=null,"replacement display receives a finite new Close budget");
  check(recovery.poll(true,131000)==DisplayRecovery.STOP,"unresolved replacement reaches explicit lane stop");
  Map<String,float[]> home=new HashMap<>();PhaseEngine.Watch hw=r.e.rule(FarmPreset.P+"verify-home");home.put(hw.regionId,hw.reference.clone());
  r.ad.filter(r.e,new HashMap<>(home),320,718,132000,132000);recovery.observeHome(true,132000,132000);check(r.ad.recovery.exhausted(),"one home match cannot replenish attempts");
  r.ad.filter(r.e,new HashMap<>(home),320,718,132400,132400);recovery.observeHome(true,132400,132400);check(!r.ad.recovery.exhausted()&&recovery.poll(false,132400)==DisplayRecovery.NONE,"verified home starts a later recovery episode");
  System.out.println("Incident66ReplayTest: "+checks+" checks; attached farm popup, animation, control confirmation, bounded display replacement and home verification; device delivery untested");
 }
}
