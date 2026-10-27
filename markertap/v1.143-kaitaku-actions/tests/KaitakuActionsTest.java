package jp.tapmarker;
import java.io.*;import java.util.*;import java.util.zip.*;

/** Recorded positive/negative controls plus end-to-end claim and mini-game graph transitions. */
public final class KaitakuActionsTest {
 static int checks;
 static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 static PhaseEngine engine(){PhaseEngine e=new PhaseEngine();KaitakuPreset.install(e);e.strictRuntime=true;return e;}
 static PhaseEngine.Watch observe(PhaseEngine e,Map<String,float[]> out,long now){e.observe(out,now);check(e.poll()==null,"first frame requires confirmation");e.observe(out,now+350);return e.poll();}
 static Map<String,float[]> named(PhaseEngine e,String context,String... labels){Map<String,float[]> out=new HashMap<>();List<KaitakuNamedControls.Control> controls=new ArrayList<>();for(String label:labels)controls.add(new KaitakuNamedControls.Control(label,new float[]{.4f,.5f,.65f,.56f}));KaitakuNamedControls.apply(e,context,controls,out,540,1212);return out;}
 static final class Frame implements RegionSampler.Pixels {int n,w,h;int[] p;Frame(DataInputStream in)throws Exception{n=in.readInt();w=in.readInt();h=in.readInt();p=new int[w*h];Arrays.fill(p,0xff999999);int count=in.readInt();for(int i=0;i<count;i++){int l=in.readInt(),t=in.readInt(),r=in.readInt(),b=in.readInt();for(int y=t;y<b;y++)for(int x=l;x<r;x++)p[y*w+x]=in.readInt();}}public int get(int x,int y){return p[y*w+x];}}
 public static void main(String[] args)throws Exception{
  Map<String,float[]> refs=KaitakuIncidentTest.refs();Map<Integer,Frame> frames=new HashMap<>();
  try(DataInputStream in=new DataInputStream(new GZIPInputStream(new FileInputStream("tests/kaitaku-action-strips.bin.gz")))){int n=in.readInt();for(int i=0;i<n;i++){Frame f=new Frame(in);frames.put(f.n,f);}check(in.read()==-1,"fixtures complete");}
  PhaseEngine e=engine();check(e.validateStart().isEmpty(),"full graph validates");
  PhaseEngine legacy=new PhaseEngine();KaitakuPreset.installLegacy(legacy);check(KaitakuPreset.isLegacy(legacy),"old factory is eligible for migration");
  legacy.rule(KaitakuPreset.P+"check-home").threshold=.01f;check(!KaitakuPreset.isLegacy(legacy),"changed user graph cannot be migrated");
  legacy=new PhaseEngine();KaitakuPreset.installLegacy(legacy);AdCloseAutomaton.install(legacy,KaitakuPreset.APP);check(KaitakuPreset.isLegacy(legacy),"factory with inherited ad graph can migrate");
  check(!KaitakuPreset.isLegacy(e),"new graph is not repeatedly replaced");
  Frame f=frames.get(144);e.enter(KaitakuPreset.P+"main",0);KaitakuLayout layout=new KaitakuLayout(e,refs);
  layout.sampleRelative(e.activeRegions(),f.w,f.h,f,1000,null);
  Map<String,float[]> out=layout.sampleRelative(e.activeRegions(),f.w,f.h,f,4000,null);
  check(KaitakuActionEvidence.present(out,"upgrade"),"recorded coin-price efficiency button is enabled");
  PhaseEngine.Watch event=observe(e,out,4000);check(event!=null&&event.action==PhaseEngine.CENTER&&event.regionId.equals(KaitakuPreset.P+"upgrade"),"efficiency dispatch leaves main for response verification");
  float[] point=layout.pointRelative(event,out,f.w,f.h);check(point!=null&&point[0]<f.w*.35&&point[1]>f.h*.82&&point[1]<f.h*.87,"efficiency point lies in recorded left button");
  check(e.confirmsAction(event,e.current,e.epoch,out),"current screenshot authorizes upgrade");check(!e.confirmsAction(event,e.current,e.epoch,new HashMap<>()),"missing evidence cancels upgrade");
  e.finish(event,4350);out=layout.sampleRelative(e.activeRegions(),f.w,f.h,f,4500,null);check(!KaitakuPreset.home(out),"input completion alone cannot immediately resume main");
  out=layout.sampleRelative(e.activeRegions(),f.w,f.h,f,6000,null);check(KaitakuPreset.home(out),"main may resume after observed response window");
  e.enter(KaitakuPreset.P+"main",6000);out=layout.sampleRelative(e.activeRegions(),f.w,f.h,f,6500,null);check(!KaitakuActionEvidence.present(out,"upgrade"),"upgrade retry respects cooldown");
  out=layout.sampleRelative(e.activeRegions(),f.w,f.h,f,20000,null);check(KaitakuActionEvidence.present(out,"auto-upgrade"),"right auto upgrade is not starved by repeated left upgrades");
  Frame adButton=frames.get(120);PhaseEngine adHome=engine();adHome.enter(KaitakuPreset.P+"main",0);KaitakuLayout adLayout=new KaitakuLayout(adHome,refs);adLayout.sampleRelative(adHome.activeRegions(),adButton.w,adButton.h,adButton,1000,null);Map<String,float[]> adOut=adLayout.sampleRelative(adHome.activeRegions(),adButton.w,adButton.h,adButton,4000,null);check(KaitakuActionEvidence.present(adOut,"upgrade"),"recorded efficiency CM button has its own paired label evidence");
  // Mask one independent label: a yellow/white region or home match is insufficient.
  e=engine();e.enter(KaitakuPreset.P+"main",0);KaitakuActionEvidence actions=new KaitakuActionEvidence(refs);KaitakuEvidence evidence=new KaitakuEvidence(refs);
  out=new HashMap<>();actions.sample(e,evidence.read(f.w,f.h,f,null),f.w,f.h,f,1000,out);
  int[] title=RegionMath.bounds(KaitakuActionEvidence.EFF_TITLE,f.w,f.h);final Frame main=f;
  RegionSampler.Pixels masked=(x,y)->x>=title[0]&&x<title[2]&&y>=title[1]&&y<title[3]?0xff999999:main.get(x,y);
  out=new HashMap<>();actions.sample(e,evidence.read(f.w,f.h,masked,null),f.w,f.h,masked,4000,out);check(!KaitakuActionEvidence.present(out,"upgrade"),"missing efficiency title prevents left-button input");
  for(Frame ad:frames.values())if(ad.n>=156&&ad.n<=175){e=engine();e.enter(KaitakuPreset.P+"main",0);layout=new KaitakuLayout(e,refs);layout.sampleRelative(e.activeRegions(),ad.w,ad.h,ad,1000,null);out=layout.sampleRelative(e.activeRegions(),ad.w,ad.h,ad,22000,null);for(String key:new String[]{"upgrade","auto-upgrade","mission-open","tap-boost","auto-boost"})check(!KaitakuActionEvidence.present(out,key),"advertisement cannot enable "+key+" at "+ad.n);}
  f=frames.get(176);e=engine();e.enter(KaitakuPreset.P+"main",0);layout=new KaitakuLayout(e,refs);layout.sampleRelative(e.activeRegions(),f.w,f.h,f,1000,null);out=layout.sampleRelative(e.activeRegions(),f.w,f.h,f,12000,null);check(KaitakuActionEvidence.present(out,"mission-open"),"recorded mission header routes to mission entry");
  event=observe(e,out,12000);check(event!=null&&event.nextPhase.equals(KaitakuPreset.P+"mission-opening"),"mission entry wins without conflicting home action");
  // The same label without the independent page title must never claim anything.
  e=engine();out=named(e,"広告","ゲット");check(!KaitakuActionEvidence.present(out,"get")&&!KaitakuActionEvidence.gamePage(out),"bare advertisement Get is ignored");
  out=named(e,"デイリーミッション","ゲット","プレイ","戻る");event=observe(e,out,1000);check(event!=null&&event.action==PhaseEngine.WAIT&&event.nextPhase.equals(KaitakuPreset.P+"missions"),"mission page is observed before claiming");e.finish(event,1350);
  out=named(e,"デイリーミッション","ゲット","プレイ","戻る");event=observe(e,out,1700);check(event!=null&&event.action==PhaseEngine.CENTER&&event.regionId.equals(KaitakuPreset.P+"get"),"Get has precedence over Play and Back");e.finish(event,2050);
  check(!e.phases.get(e.current).markersDuring,"claim response blocks main tapping");
  out=named(e,"報酬獲得","閉じる");event=observe(e,out,2400);check(event!=null&&event.regionId.equals(KaitakuPreset.P+"close"),"confirmed reward is closed");e.finish(event,2750);
  out=named(e,"デイリーミッション","ゲット");event=observe(e,out,3100);check(event!=null&&event.nextPhase.equals(KaitakuPreset.P+"missions"),"next claim is re-observed after closing reward");e.finish(event,3450);
  out=named(e,"デイリーミッション","プレイ","戻る");event=observe(e,out,3800);check(event!=null&&event.regionId.equals(KaitakuPreset.P+"play")&&event.nextPhase.equals(KaitakuPreset.P+"verify"),"no claimable reward returns through verification");
  e=engine();out=named(e,"連打ゲーム","スタート");event=observe(e,out,1000);check(event!=null&&event.regionId.equals(KaitakuPreset.P+"mini-start"),"named mini-game starts through normal dispatch");e.finish(event,1350);
  out=named(e,"連打ゲーム","ここをタップ");check(KaitakuActionEvidence.present(out,"mini-live")&&out.get(AdaptiveLayout.MAIN)!=null,"labeled live surface supplies continuous tap point");check(e.phases.get(e.current).markersDuring,"mini-game uses continuous tapper");
  out=named(e,"連打ゲーム","結果を閉じる");event=observe(e,out,1700);check(event!=null&&event.nextPhase.equals(KaitakuPreset.P+"verify"),"mini-game result exits through verification");
  out=named(e,"インストール","ここをタップ");check(!KaitakuActionEvidence.present(out,"mini-live"),"advertisement tap prompt is not a mini-game");
  out=named(e,"連打ゲーム");check(!KaitakuActionEvidence.present(out,"mini-live"),"unlabeled mini-game surface cannot authorize invented coordinates");
  List<KaitakuNamedControls.Control> banner=Arrays.asList(new KaitakuNamedControls.Control("タップ",new float[]{.4f,.92f,.6f,.97f}));out=new HashMap<>();KaitakuNamedControls.apply(e,"連打ゲーム",banner,out,540,1212);check(!KaitakuActionEvidence.present(out,"mini-live"),"banner area is excluded from mini-game inputs");
  System.out.println("KaitakuActionsTest: "+checks+" checks passed; physical reward credit and unlabeled mini-games unverified");
 }
}
