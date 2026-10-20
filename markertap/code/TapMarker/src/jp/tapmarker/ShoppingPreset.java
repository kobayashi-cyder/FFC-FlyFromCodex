package jp.tapmarker;
import java.util.*;

/** Shopping and warehouse are separate states with separate visible controls. */
public final class ShoppingPreset {
 public static final String APP="com.kurashiru.shopping.usagi",P="shopping-default-";
 static final float MAIN_Y=.725f;
 static final float[] SHOP_TITLE={.562f,.069f,.633f,.081f},SHOP_BUTTON={.392f,.774f,.603f,.797f};
 static final float[] HOME_TITLE={.118f,.130f,.246f,.144f},HOME_BUTTON={.431f,.707f,.575f,.738f};
 static final float[] EMPTY_TITLE={.711f,.303f,.854f,.321f},EMPTY_TRAY={.72f,.373f,.85f,.400f};
 static final float[][] CUSTOMERS={{.166f,.399f,.286f,.423f},{.473f,.506f,.585f,.535f},{.173f,.604f,.287f,.640f}};
 static final float[] STOCK_TITLE={.405f,.245f,.596f,.275f},STOCK_CLOSE={.440f,.811f,.560f,.835f};
 static final float[] MISSION_TITLE={.289f,.165f,.719f,.192f},MISSION_BACK={.027f,.166f,.058f,.194f};
 static final float[] GET_LABEL={.725f,.398f,.863f,.422f},GET_COLUMN={.68f,.37f,.92f,.81f},PLAY_LABEL={.712f,.398f,.860f,.422f};
 static final float[] NOTICE={.855f,.047f,.902f,.067f};
 static float[][] anchors(String key){switch(key){
  case "shop":return new float[][]{SHOP_TITLE,SHOP_BUTTON};case "home":return new float[][]{HOME_TITLE,HOME_BUTTON};
  case "empty":return new float[][]{EMPTY_TITLE,EMPTY_TRAY};
  case "stock":return new float[][]{STOCK_TITLE,STOCK_CLOSE};
  case "carrot":return new float[][]{STOCK_TITLE,new float[]{.362f,.340f,.539f,.364f}};
  case "apple":return new float[][]{STOCK_TITLE,new float[]{.362f,.425f,.493f,.448f}};
  case "milk":return new float[][]{STOCK_TITLE,new float[]{.361f,.509f,.491f,.533f}};
  case "fish":return new float[][]{STOCK_TITLE,new float[]{.361f,.593f,.494f,.617f}};
  case "meat":return new float[][]{STOCK_TITLE,new float[]{.361f,.678f,.493f,.703f}};
  case "shift":return new float[][]{new float[]{.244f,.364f,.755f,.426f},new float[]{.326f,.637f,.742f,.664f}};
  case "summon":return new float[][]{new float[]{.337f,.244f,.660f,.264f},new float[]{.463f,.539f,.536f,.557f}};
  case "boost":return new float[][]{new float[]{.304f,.292f,.697f,.324f},new float[]{.458f,.608f,.546f,.630f}};
  case "boost-ready":return new float[][]{HOME_TITLE,new float[]{.813f,.707f,.943f,.726f}};
  case "boost-active":return new float[][]{new float[]{.820f,.235f,.955f,.249f},new float[]{.824f,.256f,.947f,.275f}};
  case "arrival":return new float[][]{new float[]{.235f,.232f,.736f,.259f},new float[]{.670f,.312f,.850f,.335f}};
  case "arrival-claim":return new float[][]{new float[]{.235f,.232f,.736f,.259f},new float[]{.342f,.532f,.656f,.554f}};
  case "clear":return new float[][]{new float[]{.354f,.419f,.488f,.444f},new float[]{.330f,.597f,.499f,.623f}};
  case "missions":return new float[][]{MISSION_TITLE,MISSION_BACK};case "get":return new float[][]{MISSION_TITLE,GET_LABEL};case "play":return new float[][]{MISSION_TITLE,PLAY_LABEL};
  case "reward":return new float[][]{new float[]{.175f,.340f,.825f,.371f},new float[]{.424f,.555f,.572f,.579f}};
  default:throw new IllegalArgumentException(key);
 }}
 static void region(PhaseEngine e,String key,String name,float[] b){PhaseEngine.Region r=new PhaseEngine.Region();r.id=P+key;r.name=name;r.bounds=b.clone();e.regions.put(r.id,r);}
 static void state(PhaseEngine e,String key,String name){PhaseEngine.Phase p=new PhaseEngine.Phase();p.id=P+key;p.name="買い物うさぎ："+name;p.markersDuring=key.equals("main");int i=e.phases.size();p.graphX=(i%4)*320;p.graphY=(i/4)*180;e.phases.put(p.id,p);}
 static void image(PhaseEngine e,Map<String,float[]> refs,String state,String id,String roi,int condition,int action,String next,boolean fallback){PhaseEngine.Watch w=new PhaseEngine.Watch();w.id=P+id;w.name=e.regions.get(P+roi).name+(action==PhaseEngine.CENTER?"をタップ":"を確認");w.regionId=P+roi;w.reference=refs.get(roi).clone();w.pairedReference=true;w.condition=condition;w.action=action;w.nextPhase=P+next;w.threshold=roi.startsWith("arrival")?.027f:roi.equals("clear")?.026f:.015f;w.waitMs=0;w.fallback=fallback;e.phases.get(P+state).watches.add(w);}
 static void timer(PhaseEngine e,String state,long ms,int action,String next){PhaseEngine.Watch w=new PhaseEngine.Watch();w.id=P+state+"-timeout";w.name="表示を再確認";w.condition=PhaseEngine.TIMEOUT;w.action=action;w.nextPhase=P+next;w.timeoutMs=ms;w.waitMs=0;w.fallback=true;e.phases.get(P+state).watches.add(w);}
 static void dialogs(PhaseEngine e,Map<String,float[]> refs,String state){for(String key:new String[]{"missions","reward","stock","shift","summon","boost","arrival","clear"})image(e,refs,state,state+"-"+key,key,PhaseEngine.MATCH,PhaseEngine.WAIT,key,false);}
 static void classify(PhaseEngine e,Map<String,float[]> refs,String state){dialogs(e,refs,state);image(e,refs,state,state+"-home","home",PhaseEngine.MATCH,PhaseEngine.WAIT,"main",true);image(e,refs,state,state+"-shop","shop",PhaseEngine.MATCH,PhaseEngine.WAIT,"shop",true);}
 static void pending(PhaseEngine e,Map<String,float[]> refs,String state){image(e,refs,state,state+"-missions","missions",PhaseEngine.MATCH,PhaseEngine.WAIT,"missions",false);image(e,refs,state,state+"-reward","reward",PhaseEngine.MATCH,PhaseEngine.WAIT,"reward",false);for(String key:new String[]{"home","shop"})image(e,refs,state,state+"-"+key,key,PhaseEngine.MATCH,PhaseEngine.WAIT,"mission-tap",true);}
 public static boolean install(PhaseEngine e,Map<String,float[]> refs){boolean added=install182(e,refs);if(added)ShoppingResumePolicy.apply(e,refs);return added;}
 static PhaseEngine version182Defaults(Map<String,float[]> refs){PhaseEngine e=new PhaseEngine();install182(e,refs);return e;}
 static boolean upgrade(PhaseEngine e,Map<String,float[]> refs){return !e.windowRelative&&AutomatonGraph.sameConfiguration(e,version182Defaults(refs))&&ShoppingResumePolicy.apply(e,refs);}
 private static boolean install182(PhaseEngine e,Map<String,float[]> refs){
  if(e.phases.containsKey(P+"main"))return false;
  String[][] regions={{"shop","売り場のバイトする"},{"home","倉庫の運ぶ"},{"stock","品出しの表示"},{"carrot","にんじん15"},{"apple","りんご50"},{"milk","ミルク100"},{"fish","お魚150"},{"meat","お肉300"},{"shift","深夜シフトの予約"},{"summon","うさぎを呼ぶ・はい"},{"boost","タップ2倍・はい"},{"boost-ready","倉庫のタップ2倍"},{"arrival","来店うさぎの獲得表示"},{"arrival-claim","コインをもらう"},{"clear","倉庫クリアの演出"},{"missions","デイリーミッション"},{"play","商品を運ぶプレイ"},{"reward","コイン獲得・閉じる"}};
  for(String[] r:regions){if(refs.get(r[0])==null||refs.get(r[0]).length!=1536)throw new IllegalArgumentException("買い物うさぎの画像がありません："+r[0]);region(e,r[0],r[1],anchors(r[0])[1]);}
  region(e,"empty","商品入荷が表示された個別の空き棚",EMPTY_TRAY);region(e,"customer","個別の来店うさぎ",CUSTOMERS[0]);region(e,"get","個別の未回収ゲット",GET_COLUMN);region(e,"mission-notice","ミッションの赤い通知",NOTICE);
  String[][] states={{"check","画面判別"},{"verify","復帰表示の確認"},{"shop","空き棚を補充"},{"shop-customers","来店うさぎを回収してバイトへ"},{"stock-opening","品出し表示を確認"},{"stock","お肉を品出し・不足時はにんじん"},{"stock-wait","品出し後の売り場を確認"},{"work-opening","倉庫への切替を確認"},{"main","倉庫で運ぶ連打"},{"clear","クリア後ミッションへ"},{"arrival","来店うさぎの獲得処理"},{"arrival-wait","獲得後の表示を確認"},{"shift","深夜シフトを予約"},{"summon","うさぎを呼ぶ"},{"boost","タップ2倍を開始"},{"boost-opening","タップ2倍の確認画面を待つ"},{"reward-ad","報酬CMの終了待ち"},{"ad","不定期CMから復帰"},{"external","外部画面から復帰"},{"mission-tap","ミッションボタンを押す"},{"mission-opening","ミッションの表示確認"},{"missions","ゲットを回収してプレイ"},{"claim-wait","ミッション報酬の表示待ち"},{"reward","獲得ダイアログを閉じる"},{"mission-verify","プレイ後の倉庫を確認"},{"mission-ad","ミッション途中のCMから復帰"}};
  for(String[] s:states)state(e,s[0],s[1]);
  state(e,"shop-reward","来店・強化のコイン獲得を閉じる");
  for(String s:new String[]{"check","verify","ad","external","work-opening","stock-opening","stock-wait","arrival-wait","boost-opening","reward-ad","mission-verify"})classify(e,refs,s);
  for(String s:new String[]{"arrival-wait","stock-wait","reward-ad"})e.rule(P+s+"-reward").nextPhase=P+"shop-reward";
  timer(e,"check",3000,PhaseEngine.WAIT,"ad");timer(e,"verify",6000,PhaseEngine.RETURN_APP,"verify");timer(e,"ad",6000,PhaseEngine.RETURN_APP,"verify");timer(e,"external",0,PhaseEngine.RETURN_APP,"verify");timer(e,"reward-ad",120000,PhaseEngine.RETURN_APP,"verify");
  for(String s:new String[]{"stock-opening","stock-wait","work-opening","arrival-wait","boost-opening"})timer(e,s,2500,PhaseEngine.WAIT,"check");
  for(String s:new String[]{"shop","shop-customers","main"})dialogs(e,refs,s);
  image(e,refs,"shop","empty-tap","empty",PhaseEngine.MATCH,PhaseEngine.CENTER,"stock-opening",false);
  image(e,refs,"shop","shop-stock-done","shop",PhaseEngine.MATCH,PhaseEngine.WAIT,"shop-customers",true);
  image(e,refs,"shop-customers","customer-tap","customer",PhaseEngine.MATCH,PhaseEngine.CENTER,"arrival-wait",false);
  image(e,refs,"shop-customers","work-tap","shop",PhaseEngine.MATCH,PhaseEngine.CENTER,"work-opening",true);
  for(String s:new String[]{"shop","shop-customers"})image(e,refs,s,s+"-lost","shop",PhaseEngine.DISAPPEAR,PhaseEngine.WAIT,"check",true);
  image(e,refs,"stock","stock-meat","meat",PhaseEngine.MATCH,PhaseEngine.CENTER,"stock-wait",false);image(e,refs,"stock","stock-carrot","carrot",PhaseEngine.MATCH,PhaseEngine.CENTER,"stock-wait",true);image(e,refs,"stock","stock-gone","stock",PhaseEngine.DISAPPEAR,PhaseEngine.WAIT,"check",true);timer(e,"stock",4000,PhaseEngine.WAIT,"check");
  image(e,refs,"main","main-lost","home",PhaseEngine.DISAPPEAR,PhaseEngine.WAIT,"check",true);
  image(e,refs,"main","boost-button","boost-ready",PhaseEngine.MATCH,PhaseEngine.CENTER,"boost-opening",true);
  PhaseEngine.Watch notice=new PhaseEngine.Watch();notice.id=P+"mission-notice";notice.name="赤い通知からミッションへ";notice.regionId=P+"mission-notice";notice.reference=new float[]{1};notice.threshold=0;notice.condition=PhaseEngine.MATCH;notice.action=PhaseEngine.WAIT;notice.nextPhase=P+"mission-tap";notice.waitMs=0;e.phases.get(P+"main").watches.add(notice);
  image(e,refs,"clear","clear-missions","missions",PhaseEngine.MATCH,PhaseEngine.WAIT,"missions",false);image(e,refs,"clear","clear-hold","clear",PhaseEngine.MATCH,PhaseEngine.WAIT,"clear",false);image(e,refs,"clear","clear-home","home",PhaseEngine.MATCH,PhaseEngine.WAIT,"mission-tap",true);timer(e,"clear",5000,PhaseEngine.WAIT,"mission-ad");
  image(e,refs,"arrival","arrival-collect","arrival-claim",PhaseEngine.MATCH,PhaseEngine.CENTER,"arrival-wait",false);image(e,refs,"arrival","arrival-gone","arrival",PhaseEngine.DISAPPEAR,PhaseEngine.WAIT,"check",true);timer(e,"arrival",5000,PhaseEngine.WAIT,"check");
  for(String key:new String[]{"shift","summon","boost"}){image(e,refs,key,key+"-yes",key,PhaseEngine.MATCH,PhaseEngine.CENTER,"reward-ad",false);image(e,refs,key,key+"-gone",key,PhaseEngine.DISAPPEAR,PhaseEngine.WAIT,"check",true);}
  for(String key:new String[]{"home","shop"})image(e,refs,"mission-tap",key.equals("home")?"mission-button-tap":"mission-shop-button-tap",key,PhaseEngine.MATCH,PhaseEngine.CENTER,"mission-opening",true);
  image(e,refs,"mission-tap","mission-already-open","missions",PhaseEngine.MATCH,PhaseEngine.WAIT,"missions",false);timer(e,"mission-tap",3000,PhaseEngine.WAIT,"mission-ad");
  for(String state:new String[]{"mission-opening","mission-ad","claim-wait"})pending(e,refs,state);
  timer(e,"mission-opening",5000,PhaseEngine.WAIT,"mission-ad");timer(e,"claim-wait",5000,PhaseEngine.WAIT,"mission-ad");timer(e,"mission-ad",6000,PhaseEngine.RETURN_APP,"mission-opening");
  image(e,refs,"missions","get-tap","get",PhaseEngine.MATCH,PhaseEngine.CENTER,"claim-wait",false);image(e,refs,"missions","mission-play","play",PhaseEngine.MATCH,PhaseEngine.CENTER,"mission-verify",true);image(e,refs,"missions","mission-anchor","missions",PhaseEngine.MATCH,PhaseEngine.WAIT,"missions",true);image(e,refs,"missions","mission-reward","reward",PhaseEngine.MATCH,PhaseEngine.WAIT,"reward",false);timer(e,"missions",5000,PhaseEngine.WAIT,"mission-ad");
  image(e,refs,"reward","reward-close","reward",PhaseEngine.MATCH,PhaseEngine.CENTER,"mission-opening",false);image(e,refs,"reward","reward-gone","reward",PhaseEngine.DISAPPEAR,PhaseEngine.WAIT,"mission-opening",true);timer(e,"mission-verify",5000,PhaseEngine.WAIT,"mission-ad");
  image(e,refs,"shop-reward","shop-reward-close","reward",PhaseEngine.MATCH,PhaseEngine.CENTER,"check",false);image(e,refs,"shop-reward","shop-reward-gone","reward",PhaseEngine.DISAPPEAR,PhaseEngine.WAIT,"check",true);
  e.initial=P+"check";e.current=e.initial;return true;
 }
 static boolean playable(PhaseEngine e,Map<String,float[]> samples){PhaseEngine.Watch w=e.rule(P+"verify-home");return e.current.equals(P+"main")&&w!=null&&w.distance(samples.get(P+"home"))<=w.threshold;}
 static boolean missionPending(String state){return state.startsWith(P+"mission")||state.equals(P+"missions")||state.equals(P+"claim-wait")||state.equals(P+"reward")||state.equals(P+"clear");}
 static String recoveryState(String state){return missionPending(state)?P+"mission-ad":state.equals(P+"reward-ad")?state:P+"external";}
 static String recoveryTarget(String state){return missionPending(state)?P+"mission-opening":state.equals(P+"reward-ad")?state:P+"verify";}
}
