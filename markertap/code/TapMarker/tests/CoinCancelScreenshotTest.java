package jp.tapmarker;
import java.io.*;import java.util.*;import java.util.zip.*;

/** Actual screenshot recognition, negative images and synthetic dismissal/retry timelines. */
public final class CoinCancelScreenshotTest {
 static final String P=CoinPreset.P;static int checks;static Map<String,float[]> refs;
 static VirtualPresetProfileTest.Image screenshot;
 static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
 static final class Run {
  final PhaseEngine e=new PhaseEngine();final CoinLayout layout;Map<String,float[]> samples;
  Run(String state){CoinPreset.install(e,refs);e.strictRuntime=true;layout=CoinLayout.install(e,refs);check(layout!=null&&e.validate().isEmpty(),"valid complete coin graph");e.enter(P+state,0);}
  Map<String,float[]> sample(int width,int height,RegionSampler.Pixels pixels,long now){return samples=layout.sampleRelative(e.activeRegions(),width,height,pixels,now);}
  PhaseEngine.Watch observe(RegionSampler.Pixels pixels,long now){e.observe(sample(screenshot.width,screenshot.height,pixels,now),now);return e.poll();}
  PhaseEngine.Watch pair(RegionSampler.Pixels pixels,long now){check(observe(pixels,now)==null,"first fresh observation holds input");return observe(pixels,now+200);}
 }
 static void original(){
  for(String state:new String[]{"main","fever","check","verify","ad","external","reward-ad","mission-verify","mission-opening","mission-ad"}){
   Run r=new Run(state);PhaseEngine.Watch w=r.pair(screenshot,1000);
   check(w!=null&&w.action==PhaseEngine.WAIT&&w.nextPhase.equals(P+"boost-pot"),"supplied screenshot must reach pot cancellation from "+state+": "+(w==null?r.e.diagnostic:w.id));
   check(!ContinuousTapPolicy.allow(r.e,false,false,false,false,true,1200,1200),"dialog holds ordinary taps");
   r.e.finish(w,1250);w=r.pair(screenshot,1300);
   check(w!=null&&w.id.equals(P+"boost-pot-cancel")&&w.action==PhaseEngine.CENTER,"cancel is the only pot input");
   check(r.e.confirmsAction(w,r.e.current,r.e.epoch,r.sample(684,1536,screenshot,1550)),"fresh preflight confirms both visible landmarks");
   float[] point=r.layout.pointRelative(w,r.samples,684,1536);
   check(point!=null&&point[0]>390&&point[0]<435&&point[1]>1248&&point[1]<1292,"tap hits visible cancel letters outside the orange marker and above banner");
   check(point[1]>1220,"tap is below the CM button");
   r.e.finish(w,1600);check(r.e.current.equals(P+"boost-pot-wait"),"input acknowledgement enters dismissal verification");
   check(r.pair(screenshot,1700)==null,"unchanged modal cannot resume play or repeat input immediately");
   check(!ContinuousTapPolicy.allow(r.e,true,false,false,false,true,1900,1900),"even an unrelated confirmed flag cannot resume during dismissal wait");
  }
 }
 static void negatives()throws Exception{
  float[][] anchors={{145/684f,910/1536f,541/684f,951/1536f},{386/684f,1248/1536f,435/684f,1292/1536f}};
  for(int part=0;part<3;part++){
   final int kind=part;int[] cut=RegionMath.bounds(anchors[part%2],684,1536);
   RegionSampler.Pixels pixels=(x,y)->{int c=screenshot.get(x,y);if(kind==2)return 0xff000000|((c>>16&255)/2)<<16|((c>>8&255)/2)<<8|(c&255)/2;return x>=cut[0]-12&&x<cut[2]+12&&y>=cut[1]-12&&y<cut[3]+12?0xff808080:c;};
   Run r=new Run("boost-pot");r.sample(684,1536,pixels,1000);PhaseEngine.Watch w=r.e.rule(P+"boost-pot-cancel");
   check(!r.e.confirmsAction(w,r.e.current,r.e.epoch,r.samples)&&r.layout.pointRelative(w,r.samples,684,1536)==null,"missing description/cancel or dimmed modal rejects stale cancel point: "+part);
  }
  Video6687ReplayTest.load();
  for(String id:new String[]{"10","25-native","35.5-native","36","103-native","111.8-native","113","115","154-native","155-native","157-native"}){
   VirtualPresetProfileTest.Image im=Video6687ReplayTest.images.get(id);Run r=new Run("check");r.sample(im.width,im.height,im,1000);
   PhaseEngine.Watch pot=r.e.rule(P+"check-boost-pot");
   check(pot.distance(r.samples.get(P+"boost-pot"))>pot.threshold,"other home/boost/ad/mission/loading frames reject pot cancel: "+id);
  }
 }
 static void geometry(){
  for(float[] t:new float[][]{{1,1,0,0},{1,.90f,0,.06f},{.94f,.86f,.03f,.08f}})for(int width:new int[]{342,720}){
   int height=Math.round(width*1536/684f);RegionSampler.Pixels pixels=(x,y)->{float nx=(x/(float)width-t[2])/t[0],ny=(y/(float)height-t[3])/t[1];return nx<0||nx>=1||ny<0||ny>=1?0xff142030:screenshot.get(Math.min(683,(int)(nx*684)),Math.min(1535,(int)(ny*1536)));};
   Run r=new Run("boost-pot");r.sample(width,height,pixels,1000);PhaseEngine.Watch w=r.e.rule(P+"boost-pot-cancel");
   float[] point=r.layout.pointRelative(w,r.samples,width,height);
   check(r.e.confirmsAction(w,r.e.current,r.e.epoch,r.samples)&&point!=null,"synthetic resized/inset modal preserves two-anchor match");
   float expectedX=(410.5f/684*t[0]+t[2])*width,expectedY=(1270.5f/1536*t[1]+t[3])*height;
   check(Math.abs(point[0]-expectedX)<width*.018f&&Math.abs(point[1]-expectedY)<height*.018f,"cancel coordinates follow actual modal transform");
   check(r.layout.pointRelative(w,r.samples,width+1,height)==null,"changed frame size invalidates cached point");
  }
 }
 static void dismissalAndRetry()throws Exception{
  Run r=new Run("boost-pot-wait");r.e.enter(P+"boost-pot-wait",1000);
  check(r.pair(screenshot,1100)==null,"visible pot blocks dismissal transition");
  r.e.observe(Collections.emptyMap(),2600);PhaseEngine.Watch retry=r.e.poll();
  check(retry!=null&&retry.action==PhaseEngine.WAIT&&retry.nextPhase.equals(P+"check"),"unacknowledged dismissal reclassifies after bounded delay without guessing another input");
  r.e.finish(retry,2600);check(r.pair(screenshot,2700)!=null,"persistent modal is detectable again after retry delay");
  r=new Run("boost-pot-wait");VirtualPresetProfileTest.Image home=Video6687ReplayTest.images.get("10");
  for(long now:new long[]{100,300}){r.e.observe(r.sample(home.width,home.height,home,now),now);PhaseEngine.Watch w=r.e.poll();if(now==100)check(w==null,"first gone frame does not release markers");else{check(w!=null&&w.nextPhase.equals(P+"verify"),"two gone frames enter home verification");r.e.finish(w,now);}}
  for(long now:new long[]{400,600}){r.e.observe(r.sample(home.width,home.height,home,now),now);PhaseEngine.Watch w=r.e.poll();if(now==400)check(w==null,"first home frame still holds markers");else{check(w!=null&&w.nextPhase.equals(P+"main"),"two confirmed home frames resume ordinary main state");r.e.finish(w,now);}}
  check(ContinuousTapPolicy.allow(r.e,CoinPreset.playable(r.e,r.samples),false,false,false,true,600,600),"normal tapping resumes only after verified home");
  CoinMissionJourney journey=new CoinMissionJourney();journey.route(P+"clear");journey.route(P+"boost-pot");journey.route(P+"boost-pot-wait");check(journey.blocksMain()&&journey.route(P+"main").equals(P+"mission-tap"),"cancel preserves interrupted mission collection intent");
 }
 static void migration(){
  PhaseEngine old=CoinPreset.version178Defaults(refs),fresh=new PhaseEngine();CoinPreset.install(fresh,refs);
  check(!old.phases.containsKey(P+"boost-pot"),"legacy factory graph excludes the new dialog");
  check(CoinPreset.upgrade(old,refs)&&AutomatonGraph.sameConfiguration(old,fresh)&&!CoinPreset.upgrade(old,refs),"v178 upgrade is complete and idempotent");
  old=CoinPreset.version170Defaults(refs);check(CoinPreset.upgrade(old,refs)&&AutomatonGraph.sameConfiguration(old,fresh),"older factory graph upgrades to all current states");
  check(AutomatonGraph.relationships(fresh).isEmpty()&&AutomatonAuditTest.reachable(fresh).contains(P+"boost-pot-wait"),"new state references and reachability are valid");
 }
 public static void main(String[] args)throws Exception{
  refs=VirtualPresetProfileTest.canonical("tests/coin-cancel-references.bin");try(DataInputStream in=new DataInputStream(new GZIPInputStream(new FileInputStream("tests/coin-cancel-screenshot.bin.gz")))){screenshot=new VirtualPresetProfileTest.Image(in);check(in.read()==-1,"complete supplied screenshot fixture");}
  original();negatives();geometry();dismissalAndRetry();migration();
  System.out.println("CoinCancelScreenshotTest: "+checks+" checks passed; one actual screenshot plus synthetic negatives, geometry and dismissal timelines; physical input untested");
 }
}
