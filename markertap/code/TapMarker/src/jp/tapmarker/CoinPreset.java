package jp.tapmarker;
import java.util.*;

/** Only screens and controls present in the supplied coin-drop video. */
public final class CoinPreset {
 public static final String APP="com.kurashiru.coin.drop",P="coin-default-";
 static final float[] HEADER={.388f,.092f,.503f,.106f},BUTTON={.143f,.878f,.258f,.891f};
 static final float[] FEVER={.570f,.284f,.815f,.324f},BONUS={.202f,.340f,.798f,.363f},START={.320f,.774f,.680f,.794f};
 static final float MAIN_Y=.807f;
 static final float[] MISSION_TITLE={.289f,.166f,.715f,.193f},MISSION_BACK={.019f,.168f,.053f,.196f};
 static final float[] GET_LABEL={.729f,.400f,.869f,.425f},GET_COLUMN={.67f,.29f,.925f,.88f};
 static final float[] REWARD_TITLE={.179f,.341f,.824f,.371f},CLOSE={.421f,.556f,.576f,.577f};
 static final float[] RARE_TITLE={.246f,.295f,.763f,.331f},RARE_CM={.267f,.749f,.738f,.775f};
 static final float[] POCKET_TITLE={.13f,.281f,.867f,.314f},POCKET_CM={.256f,.740f,.739f,.769f};
 static final float[] TAP2_BODY={.247f,.580f,.758f,.628f},TAP2_CM={.287f,.740f,.705f,.767f};
 // The supplied screenshot's panel covers the title and its marker covers Cancel's centre.
 // Match the unobscured description plus rightmost Cancel letters; tap those letters.
 static final float[] POT_BODY={145/684f,910/1536f,541/684f,951/1536f};
 static final float[] POT_CANCEL_LABEL={386/684f,1248/1536f,435/684f,1292/1536f};
 static final float[] POT_CANCEL={394/684f,1257/1536f,427/684f,1284/1536f};
 static final float[] NOTICE={.495f,.070f,.540f,.099f};
 static final float[] CLEAR_TOP={.453f,.399f,.548f,.431f},CLEAR_LOGO={.457f,.467f,.557f,.524f};
 static void region(PhaseEngine e,String key,String name,float[] bounds){PhaseEngine.Region r=new PhaseEngine.Region();r.id=P+key;r.name=name;r.bounds=bounds.clone();e.regions.put(r.id,r);}
 static void state(PhaseEngine e,String key,String name,boolean markers){PhaseEngine.Phase p=new PhaseEngine.Phase();p.id=P+key;p.name="コイン落とし："+name;p.markersDuring=markers;int i=e.phases.size();p.graphX=(i%3)*300;p.graphY=(i/3)*180;e.phases.put(p.id,p);}
 static void image(PhaseEngine e,Map<String,float[]> refs,String state,String id,String roi,int condition,int action,String next,boolean fallback){PhaseEngine.Watch w=new PhaseEngine.Watch();w.id=P+id;w.name=id;w.regionId=P+roi;w.reference=refs.get(roi).clone();w.pairedReference=true;w.condition=condition;w.action=action;w.nextPhase=P+next;w.threshold=roi.equals("fever")?.025f:.015f;w.waitMs=0;w.fallback=fallback;e.phases.get(P+state).watches.add(w);}
 static void timer(PhaseEngine e,String state,long ms,int action,String next){PhaseEngine.Watch w=new PhaseEngine.Watch();w.id=P+state+"-timeout";w.name="表示を確認して再判別";w.condition=PhaseEngine.TIMEOUT;w.action=action;w.nextPhase=P+next;w.timeoutMs=ms;w.waitMs=0;w.fallback=true;e.phases.get(P+state).watches.add(w);}
 static void classify(PhaseEngine e,Map<String,float[]> refs,String state){for(String key:new String[]{"missions","reward","boost-rare","boost-pocket"})image(e,refs,state,state+"-"+key,key,PhaseEngine.MATCH,PhaseEngine.WAIT,key,false);image(e,refs,state,state+"-bonus","bonus",PhaseEngine.MATCH,PhaseEngine.WAIT,"bonus",false);image(e,refs,state,state+"-clear","clear",PhaseEngine.MATCH,PhaseEngine.WAIT,"clear",false);image(e,refs,state,state+"-fever","fever",PhaseEngine.MATCH,PhaseEngine.WAIT,"fever",false);image(e,refs,state,state+"-home","home",PhaseEngine.MATCH,PhaseEngine.WAIT,"main",true);}
 public static boolean install(PhaseEngine e,Map<String,float[]> refs){return install(e,refs,true);}
 private static boolean install(PhaseEngine e,Map<String,float[]> refs,boolean noAds){
  if(e.phases.containsKey(P+"main"))return false;
  for(String key:new String[]{"home","home-gold","fever","fever-gold","bonus","clear","missions","get","play","reward","boost-rare","boost-pocket"})if(refs.get(key)==null||refs.get(key).length!=1536)throw new IllegalArgumentException("コイン落としの画像がありません："+key);
  region(e,"home","主画面のミッション文字とレアコイン文字",HEADER);region(e,"fever","フィーバー文字とレアコイン文字",FEVER);region(e,"bonus","コインアップの見出しとこのままはじめる",START);
  region(e,"clear","クリア時の袋の結び目とロゴ",CLEAR_LOGO);
  region(e,"missions","ミッションの見出しと戻る印",MISSION_BACK);region(e,"get","表示中の未回収ゲット（文字を再確認）",GET_COLUMN);region(e,"play","ステージのプレイ",GET_LABEL);region(e,"reward","コイン獲得の閉じる",CLOSE);
  region(e,"boost-rare","レアコイン出現率アップのCMボタン",RARE_CM);region(e,"boost-pocket","ポケット倍率アップのCMボタン",POCKET_CM);region(e,"mission-notice","ミッションの赤い通知",NOTICE);
  state(e,"check","画面判別",false);state(e,"main","コインを投入",true);state(e,"fever","フィーバー中に投入",true);state(e,"bonus","次のステージを開始",false);state(e,"ad","広告を閉じる・登録アプリへ復帰",false);state(e,"verify","復帰先の表示を確認",false);state(e,"external","外部アプリから復帰",false);
  state(e,"clear","ステージクリアの演出を確認",false);
  classify(e,refs,"check");timer(e,"check",2500,PhaseEngine.WAIT,"ad");
  image(e,refs,"main","main-bonus","bonus",PhaseEngine.MATCH,PhaseEngine.WAIT,"bonus",false);image(e,refs,"main","main-fever","fever",PhaseEngine.MATCH,PhaseEngine.WAIT,"fever",false);image(e,refs,"main","main-lost","home",PhaseEngine.DISAPPEAR,PhaseEngine.WAIT,"check",true);
  image(e,refs,"fever","fever-bonus","bonus",PhaseEngine.MATCH,PhaseEngine.WAIT,"bonus",false);image(e,refs,"fever","fever-ended","fever",PhaseEngine.DISAPPEAR,PhaseEngine.WAIT,"check",true);
  image(e,refs,"main","main-clear","clear",PhaseEngine.MATCH,PhaseEngine.WAIT,"clear",false);image(e,refs,"fever","fever-clear","clear",PhaseEngine.MATCH,PhaseEngine.WAIT,"clear",false);
  classify(e,refs,"clear");e.phases.get(P+"clear").watches.removeIf(w->w.nextPhase.equals(P+"clear"));image(e,refs,"clear","clear-gone","clear",PhaseEngine.DISAPPEAR,PhaseEngine.WAIT,"mission-ad",true);timer(e,"clear",2500,PhaseEngine.WAIT,"mission-ad");
  image(e,refs,"bonus","start-tap","bonus",PhaseEngine.MATCH,PhaseEngine.CENTER,"verify",false);image(e,refs,"bonus","bonus-gone","bonus",PhaseEngine.DISAPPEAR,PhaseEngine.WAIT,"check",true);
  for(String state:new String[]{"ad","verify","external"})classify(e,refs,state);
  timer(e,"ad",4000,PhaseEngine.RETURN_APP,"verify");timer(e,"verify",5000,PhaseEngine.RETURN_APP,"verify");timer(e,"external",0,PhaseEngine.RETURN_APP,"verify");
  missions(e,refs);tap2(e,refs);potCancel(e,refs);if(noAds)CoinNoAdPolicy.apply(e);e.rule(P+"clear-home").nextPhase=P+"mission-tap";e.rule(P+"clear-bonus").nextPhase=P+"mission-bonus";e.initial=P+"check";e.current=e.initial;return true;
 }
 public static boolean playable(PhaseEngine e,Map<String,float[]> samples){String key=e.current.equals(P+"fever")?"fever":e.current.equals(P+"main")?"home":"";if(key.isEmpty())return false;PhaseEngine.Watch w=e.rule(P+(key.equals("home")?"verify-home":"verify-fever"));return w!=null&&w.distance(samples.get(P+key))<=w.threshold;}
 static void missionKnown(PhaseEngine e,Map<String,float[]> refs,String state){image(e,refs,state,state+"-missions","missions",PhaseEngine.MATCH,PhaseEngine.WAIT,"missions",false);image(e,refs,state,state+"-reward","reward",PhaseEngine.MATCH,PhaseEngine.WAIT,"reward",false);image(e,refs,state,state+"-bonus","bonus",PhaseEngine.MATCH,PhaseEngine.WAIT,"mission-bonus",false);image(e,refs,state,state+"-home","home",PhaseEngine.MATCH,PhaseEngine.WAIT,"mission-tap",true);}
 static void missions(PhaseEngine e,Map<String,float[]> refs){
  String[][] states={{"missions","未回収ゲットを回収してプレイ"},{"reward","獲得したコインを閉じる"},{"boost-rare","レアコインの強化CMを開始"},{"boost-pocket","ポケット倍率の強化CMを開始"},{"reward-ad","強化CMの閉じる・次へを待つ"},{"mission-tap","ミッションを開く"},{"mission-opening","ミッションの表示を確認"},{"claim-wait","報酬表示を確認"},{"mission-bonus","ミッション途中のステージ開始"},{"mission-verify","プレイ後の主画面を確認"},{"mission-ad","ミッション途中の広告から復帰"}};
  for(String[] item:states)state(e,item[0],item[1],false);
  for(String state:new String[]{"main","fever"})for(String key:new String[]{"missions","reward","boost-rare","boost-pocket"})image(e,refs,state,state+"-"+key,key,PhaseEngine.MATCH,PhaseEngine.WAIT,key,false);
  for(String key:new String[]{"boost-rare","boost-pocket"}){image(e,refs,key,key+"-cm",key,PhaseEngine.MATCH,PhaseEngine.CENTER,"reward-ad",false);image(e,refs,key,key+"-gone",key,PhaseEngine.DISAPPEAR,PhaseEngine.WAIT,"check",true);}
  classify(e,refs,"reward-ad");timer(e,"reward-ad",120000,PhaseEngine.RETURN_APP,"verify");
  image(e,refs,"mission-tap","mission-button-tap","home",PhaseEngine.MATCH,PhaseEngine.CENTER,"mission-opening",true);image(e,refs,"mission-tap","already-open","missions",PhaseEngine.MATCH,PhaseEngine.WAIT,"missions",false);timer(e,"mission-tap",3000,PhaseEngine.WAIT,"mission-ad");
  for(String state:new String[]{"mission-opening","mission-ad"})missionKnown(e,refs,state);timer(e,"mission-opening",5000,PhaseEngine.WAIT,"mission-ad");timer(e,"mission-ad",6000,PhaseEngine.RETURN_APP,"mission-opening");
  image(e,refs,"missions","get-tap","get",PhaseEngine.MATCH,PhaseEngine.CENTER,"claim-wait",false);image(e,refs,"missions","mission-play","play",PhaseEngine.MATCH,PhaseEngine.CENTER,"mission-verify",true);image(e,refs,"missions","mission-anchor","missions",PhaseEngine.MATCH,PhaseEngine.WAIT,"missions",true);image(e,refs,"missions","mission-reward-shown","reward",PhaseEngine.MATCH,PhaseEngine.WAIT,"reward",false);timer(e,"missions",5000,PhaseEngine.WAIT,"mission-ad");
  image(e,refs,"claim-wait","claim-shown","reward",PhaseEngine.MATCH,PhaseEngine.WAIT,"reward",false);timer(e,"claim-wait",5000,PhaseEngine.WAIT,"mission-ad");
  image(e,refs,"reward","reward-close","reward",PhaseEngine.MATCH,PhaseEngine.CENTER,"mission-opening",false);image(e,refs,"reward","reward-gone","reward",PhaseEngine.DISAPPEAR,PhaseEngine.WAIT,"mission-opening",true);
  image(e,refs,"mission-bonus","mission-bonus-start","bonus",PhaseEngine.MATCH,PhaseEngine.CENTER,"mission-opening",false);image(e,refs,"mission-bonus","mission-bonus-gone","bonus",PhaseEngine.DISAPPEAR,PhaseEngine.WAIT,"mission-opening",true);
  classify(e,refs,"mission-verify");timer(e,"mission-verify",5000,PhaseEngine.WAIT,"mission-ad");
 }
 static boolean missionPending(String state){return state.startsWith(P+"mission")||state.equals(P+"clear")||state.equals(P+"claim-wait")||state.equals(P+"reward");}
 static String recoveryState(String state){return missionPending(state)?P+"mission-ad":P+"external";}
 static String recoveryTarget(String state){return missionPending(state)?P+"mission-opening":P+"verify";}
 /** Called only for unedited factory settings; custom and learned graphs stay intact. */
 public static boolean upgrade(PhaseEngine e){
  boolean changed=false;PhaseEngine.Watch notice=e.rule(P+"mission-notice");
  if(notice!=null&&notice.condition==PhaseEngine.MATCH&&notice.action==PhaseEngine.WAIT&&notice.regionId.equals(P+"mission-notice")&&notice.nextPhase.equals(P+"mission-tap")&&Arrays.equals(notice.reference,new float[]{1}))for(PhaseEngine.Phase state:e.phases.values())if(state.watches.remove(notice))changed=true;
  for(String[] row:new String[][]{{"clear-home","main","mission-tap"},{"clear-bonus","bonus","mission-bonus"},{"clear-gone","check","mission-ad"},{"clear-timeout","check","mission-ad"},{"start-tap","bonus-wait","verify"}}){PhaseEngine.Watch w=e.rule(P+row[0]);if(w!=null&&w.nextPhase.equals(P+row[1])){w.nextPhase=P+row[2];changed=true;}}
  if(e.phases.containsKey(P+"bonus-wait")){for(PhaseEngine.Phase phase:e.phases.values())for(PhaseEngine.Watch w:phase.watches)if(w.nextPhase.equals(P+"bonus-wait"))w.nextPhase=P+"verify";e.phases.remove(P+"bonus-wait");if(e.current.equals(P+"bonus-wait"))e.current=P+"verify";changed=true;}
  return CoinNoAdPolicy.apply(e)||changed;
 }
 static void tap2(PhaseEngine e,Map<String,float[]> refs){
  float[] reference=refs.get("boost-tap2");if(reference==null||e.phases.containsKey(P+"boost-tap2"))return;
  if(reference.length!=1536)throw new IllegalArgumentException("タップ2倍の見本がありません");
  region(e,"boost-tap2","タップ2倍の説明とCMボタン",TAP2_CM);state(e,"boost-tap2","タップ2倍の強化CMを開始",false);
  for(String state:new String[]{"check","main","fever","clear","ad","verify","external","reward-ad","mission-verify"})image(e,refs,state,state+"-boost-tap2","boost-tap2",PhaseEngine.MATCH,PhaseEngine.WAIT,"boost-tap2",false);
  image(e,refs,"boost-tap2","boost-tap2-cm","boost-tap2",PhaseEngine.MATCH,PhaseEngine.CENTER,"reward-ad",false);
  image(e,refs,"boost-tap2","boost-tap2-gone","boost-tap2",PhaseEngine.DISAPPEAR,PhaseEngine.WAIT,"check",true);
 }
 static void potCancel(PhaseEngine e,Map<String,float[]> refs){
  float[] reference=refs.get("boost-pot");if(reference==null||e.phases.containsKey(P+"boost-pot"))return;
  if(reference.length!=1536)throw new IllegalArgumentException("ポット増加のキャンセル見本がありません");
  region(e,"boost-pot","ポット増加の説明とキャンセル右端",POT_CANCEL);
  state(e,"boost-pot","ポット増加をキャンセル",false);state(e,"boost-pot-wait","ポット増加の確認画面が閉じるのを確認",false);
  for(String state:new String[]{"check","main","fever","clear","ad","verify","external","reward-ad","mission-verify","mission-opening","mission-ad"})image(e,refs,state,state+"-boost-pot","boost-pot",PhaseEngine.MATCH,PhaseEngine.WAIT,"boost-pot",false);
  image(e,refs,"boost-pot","boost-pot-cancel","boost-pot",PhaseEngine.MATCH,PhaseEngine.CENTER,"boost-pot-wait",false);
  image(e,refs,"boost-pot","boost-pot-gone","boost-pot",PhaseEngine.DISAPPEAR,PhaseEngine.WAIT,"verify",true);
  image(e,refs,"boost-pot-wait","boost-pot-dismissed","boost-pot",PhaseEngine.DISAPPEAR,PhaseEngine.WAIT,"verify",true);
  timer(e,"boost-pot-wait",1500,PhaseEngine.WAIT,"check");
 }
 static boolean upgrade(PhaseEngine e,Map<String,float[]> refs){if(!e.phases.containsKey(P+"main"))return false;boolean changed=upgrade(e),missing=!e.phases.containsKey(P+"boost-tap2")&&refs.containsKey("boost-tap2"),potMissing=!e.phases.containsKey(P+"boost-pot")&&refs.containsKey("boost-pot");if(missing)tap2(e,refs);if(potMissing)potCancel(e,refs);boolean policy=CoinNoAdPolicy.apply(e);return changed||missing||potMissing||policy;}
 static PhaseEngine version179Defaults(Map<String,float[]> refs){PhaseEngine e=new PhaseEngine();install(e,refs,false);return e;}
 static PhaseEngine version178Defaults(Map<String,float[]> refs){Map<String,float[]> legacy=new HashMap<>(refs);legacy.remove("boost-pot");PhaseEngine e=new PhaseEngine();install(e,legacy,false);return e;}
 static PhaseEngine version170Defaults(Map<String,float[]> refs){
  Map<String,float[]> legacy=new HashMap<>(refs);legacy.remove("boost-tap2");legacy.remove("boost-pot");PhaseEngine e=new PhaseEngine();install(e,legacy,false);
  e.rule(P+"start-tap").nextPhase=P+"bonus-wait";e.rule(P+"clear-gone").nextPhase=P+"check";e.rule(P+"clear-timeout").nextPhase=P+"check";
  state(e,"bonus-wait","開始後の表示を確認",false);image(e,legacy,"bonus-wait","started-fever","fever",PhaseEngine.MATCH,PhaseEngine.WAIT,"fever",false);image(e,legacy,"bonus-wait","started-home","home",PhaseEngine.MATCH,PhaseEngine.WAIT,"main",true);timer(e,"bonus-wait",1800,PhaseEngine.WAIT,"check");return e;
 }
 static PhaseEngine previousDefaults(Map<String,float[]> refs){PhaseEngine e=version170Defaults(refs);e.rule(P+"clear-home").nextPhase=P+"main";e.rule(P+"clear-bonus").nextPhase=P+"bonus";PhaseEngine.Watch notice=new PhaseEngine.Watch();notice.id=P+"mission-notice";notice.name="主画面の赤い通知を確認してミッションへ";notice.regionId=P+"mission-notice";notice.reference=new float[]{1};notice.threshold=0;notice.condition=PhaseEngine.MATCH;notice.action=PhaseEngine.WAIT;notice.nextPhase=P+"mission-tap";notice.waitMs=0;notice.fallback=true;e.phases.get(P+"main").watches.add(notice);return e;}

}
