package jp.tapmarker;
import java.util.*;

/** Regression coverage for game-owned dialogs independent of detailed recognition. */
public final class GamePopupEvidenceTest {
 static int checks;
 static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 static PhaseEngine farm()throws Exception{PhaseEngine e=new PhaseEngine();FarmPreset.install(e,VirtualPresetProfileTest.canonical("tests/farm-replay.bin"));GamePopupEvidence.install(e,FarmPreset.APP);FarmControlEvidence.install(e);e.strictRuntime=true;return e;}
 static PhaseEngine.Watch confirm(PhaseEngine e,Map<String,float[]> samples,long at){e.observe(samples,at);check(e.poll()==null,"a new popup requires a second observation");e.observe(samples,at+350);return e.poll();}
 static void labels(){
  for(String app:new String[]{UsagiPreset.APP,CoinPreset.APP}){
   check(GamePopupEvidence.supported(app),"recorded footer games participate in the popup fast path");
   check(GamePopupEvidence.named(app,"閉じる","広告",true).equals(GamePopupEvidence.closeId(app)),"Close stays in its own game graph");
   check(!GamePopupEvidence.named(app,"閉じる","",false).isEmpty(),"explicit named Close does not require hidden canvas text");
  }
  check(GamePopupEvidence.named(UsagiPreset.APP,"獲得","オフラインボーナス",true).equals(UsagiPreset.PREFIX+"claim"),"Usagi offline never routes into Poipoi");
  check(GamePopupEvidence.named(PoipoiPreset.APP,"このままはじめる","コインアップチャンス",true).equals(PoipoiPreset.P+"bonus"),"ordinary continuation has its own route");
  check(GamePopupEvidence.named(PoipoiPreset.APP,"CMを見て10ゲット","コインアップチャンス",true).isEmpty(),"bonus ad multiplier is not ordinary continuation");
  check(GamePopupEvidence.named("com.android.vending","閉じる","広告",true).isEmpty(),"store controls cannot enter a game popup graph");
  for(String app:new String[]{FarmPreset.APP,PoipoiPreset.APP,CoinPreset.APP}){
   for(String label:new String[]{"獲得","獲得する","報酬を獲得","受け取る"})check(GamePopupEvidence.named(app,label,"オフライン ボーナス",false).endsWith("offline"),"explicit ordinary offline claim: "+label);
   for(String label:new String[]{"×","✕","✖","❌","閉じる"})check(!GamePopupEvidence.named(app,label,"報酬",false).isEmpty(),"dialog-local close labels");
   for(String label:new String[]{"CMをみて獲得2倍","広告を見て獲得","獲得2倍","プレイ","インストール","OK"})check(GamePopupEvidence.named(app,label,"オフライン ボーナス",true).isEmpty(),"optional or ambiguous input is excluded: "+label);
   check(GamePopupEvidence.named(app,"獲得","ショップ",false).isEmpty(),"claim requires offline context");
   check(GamePopupEvidence.named(app,"×","通常のゲーム",false).isEmpty(),"an arbitrary glyph does not prove a popup");
  }
 }
 static void routing()throws Exception{
  PhaseEngine e=farm();int count=e.phases.size();GamePopupEvidence.install(e,FarmPreset.APP);check(e.phases.size()==count&&e.validate().isEmpty(),"runtime installation is valid and idempotent");
  e.enter(FarmPreset.P+"verify",0);Map<String,float[]> samples=new HashMap<>();GamePopupEvidence.put(samples,FarmPreset.P+"offline",160,470,320,718);
  PhaseEngine.Watch w=confirm(e,samples,100);check(w!=null&&w.nextPhase.equals(FarmPreset.P+"offline")&&w.action==PhaseEngine.WAIT,"offline is recognized from stalled verification");e.finish(w,500);
  w=confirm(e,samples,700);check(w!=null&&w.action==PhaseEngine.CENTER&&w.nextPhase.equals(FarmPreset.P+"check"),"ordinary claim returns to observation, never to assumed success");
  check(e.confirmsAction(w,e.current,e.epoch,samples),"current popup evidence authorizes only its control");check(!e.confirmsAction(w,e.current,e.epoch,new HashMap<>()),"disappearance cancels a pending popup control");
  check(GamePopupEvidence.point(w.regionId,samples,320,718)[1]==470,"input uses the actual dialog button");check(GamePopupEvidence.point(w.regionId,samples,321,718)==null,"size changes invalidate coordinates");
  e.enter(FarmPreset.P+"verify",0);samples=new HashMap<>();GamePopupEvidence.put(samples,FarmPreset.P+"close",265,240,320,718);w=confirm(e,samples,100);check(w!=null&&w.nextPhase.equals(FarmPreset.P+"reward"),"an interior close glyph reaches existing close handling");
  e=new PhaseEngine();e.windowRelative=true;GamePopupEvidence.install(e,FarmPreset.APP);check(e.phases.isEmpty(),"learned automata are unchanged");
 }
 static RegionSampler.Pixels modal(int w,int h,boolean cross,boolean panel){return(x,y)->{
  if(!panel)return 0xff69804a;
  if(x>w*.10f&&x<w*.90f&&y>=h*.30f&&y<h*.40f){int cx=(int)(w*.84f),cy=(int)(h*.35f),radius=Math.max(4,(int)(w*.022f));if(cross&&Math.abs(x-cx)<=radius&&Math.abs(y-cy)<=radius&&(Math.abs((x-cx)-(y-cy))<=1||Math.abs((x-cx)+(y-cy))<=1))return 0xff111111;return 0xffffa132;}
  return x>w*.10f&&x<w*.90f&&y>=h*.40f&&y<h*.70f?0xffffffff:0xff445044;
 };}
 static void optical()throws Exception{
  for(int[] size:new int[][]{{320,718},{540,1211}}){int w=size[0],h=size[1];float[] p=GamePopupEvidence.dialogCross(w,h,modal(w,h,true,true));check(p!=null&&Math.abs(p[0]-w*.84f)<3&&Math.abs(p[1]-h*.35f)<3,"X is located inside the measured popup at "+w);check(GamePopupEvidence.dialogCross(w,h,modal(w,h,false,true))==null,"a plain dialog is not a close button");check(GamePopupEvidence.dialogCross(w,h,modal(w,h,true,false))==null,"gameplay is not a popup");}
  Video6725ReplayTest.load();PhaseEngine e=new PhaseEngine();PoipoiPreset.install(e,Video6725ReplayTest.refs);GamePopupEvidence.install(e,PoipoiPreset.APP);GamePopupEvidence quick=new GamePopupEvidence(PoipoiPreset.APP,e);
  for(String name:new String[]{"0.8","1.2"}){VirtualPresetProfileTest.Image im=Video6725ReplayTest.images.get(name);Map<String,float[]> samples=quick.quick(im.width,im.height,im,null);float[] p=GamePopupEvidence.point(PoipoiPreset.P+"offline",samples,im.width,im.height);check(p!=null&&p[1]>im.height*.64f&&p[1]<im.height*.70f,"real offline popup chooses normal claim below the ad multiplier");}
  for(String name:new String[]{"16.0","4.2","6.8"}){VirtualPresetProfileTest.Image im=Video6725ReplayTest.images.get(name);check(!GamePopupEvidence.present(quick.quick(im.width,im.height,im,null)),"ordinary game, missions and advertising are not offline claims: "+name);}
 }
 public static void main(String[] args)throws Exception{labels();routing();optical();System.out.println("GamePopupEvidenceTest: "+checks+" checks passed; device-owned popup traversal and actual input delivery require device validation");}
}
