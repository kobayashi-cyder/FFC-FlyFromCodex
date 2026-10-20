package jp.tapmarker;
import java.util.*;

/** The recorded feeding/mission loop; optional auto-tap, boost and character ads are never started. */
final class PoipoiPreset {
 static final String APP="com.kurashiru.poipoi.usagi",P="poipoi-default-";
 static final float MAIN_Y=.60f;
 static float[] b(float l,float t,float r,float z){return new float[]{l/360,t/808,r/360,z/808};}
 static final String[] KEYS={"game","full","offline","bonus","missions","reward","decline"};
 static final String DECLINE_NAME="タップ2倍の画像と使用しない";
 // These two parts remain visible below the floating panel in screenshot 6727.
 static final float[][] DECLINE={b(89,342,272,421),b(128,522,234,548)};
 static final float[] SATISFACTION=b(147,132,213,149),MISSION=b(37,631,96,652);
 static final float[] MISSION_TITLE=b(104,134,257,153),BACK=b(8,134,23,156),GET_LABEL=b(263,614,303,636),GET_COLUMN=b(235,222,329,731),NOTICE=b(105,616,124,637);
 static final float[][] GAME={SATISFACTION,MISSION},FULL={SATISFACTION,b(135,157,228,184)},OFFLINE={b(109,188,258,255),b(156,524,204,548)},BONUS={b(67,218,296,245),b(112,499,252,520)},MISSIONS={MISSION_TITLE,BACK},REWARD={b(64,273,297,297),b(147,446,213,468)};
 static float[][] anchors(String key){switch(key){case "game":return GAME;case "full":return FULL;case "offline":return OFFLINE;case "bonus":return BONUS;case "missions":return MISSIONS;case "reward":return REWARD;case "decline":return DECLINE;case "get":return new float[][]{MISSION_TITLE,GET_LABEL};default:throw new IllegalArgumentException(key);}}
 static void region(PhaseEngine e,String id,String name,float[] bounds){PhaseEngine.Region r=new PhaseEngine.Region();r.id=P+id;r.name=name;r.bounds=bounds.clone();e.regions.put(r.id,r);}
 static void state(PhaseEngine e,String id,String name){PhaseEngine.Phase p=new PhaseEngine.Phase();p.id=P+id;p.name="ポイポイうさぎ："+name;p.markersDuring=id.equals("main");int n=e.phases.size();p.graphX=n%3*300;p.graphY=n/3*180;e.phases.put(p.id,p);}
 static PhaseEngine.Watch image(PhaseEngine e,Map<String,float[]> refs,String state,String id,String key,int condition,int action,String next,boolean fallback){PhaseEngine.Watch w=new PhaseEngine.Watch();w.id=P+id;w.name=id;w.regionId=P+key;w.reference=refs.get(key).clone();w.pairedReference=w.reference.length==1536;w.threshold=w.pairedReference?.012f:0;w.condition=condition;w.action=action;w.nextPhase=P+next;w.fallback=fallback;w.waitMs=0;e.phases.get(P+state).watches.add(w);return w;}
 static void timer(PhaseEngine e,String state,long delay,int action,String next){PhaseEngine.Watch w=new PhaseEngine.Watch();w.id=P+state+"-timeout";w.name="画面を再確認";w.condition=PhaseEngine.TIMEOUT;w.timeoutMs=delay;w.action=action;w.nextPhase=P+next;w.fallback=true;w.waitMs=0;e.phases.get(P+state).watches.add(w);}
 static void dialogs(PhaseEngine e,Map<String,float[]> refs,String state){for(String key:new String[]{"offline","bonus","reward","missions"})image(e,refs,state,state+"-"+key,key,PhaseEngine.MATCH,PhaseEngine.WAIT,key,false);}
 static void classify(PhaseEngine e,Map<String,float[]> refs,String state,boolean collecting){dialogs(e,refs,state);if(!collecting){image(e,refs,state,state+"-full","full",PhaseEngine.MATCH,PhaseEngine.WAIT,"mission-tap",false);image(e,refs,state,state+"-notice","notice",PhaseEngine.MATCH,PhaseEngine.WAIT,"mission-tap",false);}image(e,refs,state,state+"-game","game",PhaseEngine.MATCH,PhaseEngine.WAIT,collecting?"mission-tap":"main",true);}
 static boolean install(PhaseEngine e,Map<String,float[]> input){return install(e,input,true);}
 private static boolean install(PhaseEngine e,Map<String,float[]> input,boolean withDecline){
  if(e.phases.containsKey(P+"main"))return false;Map<String,float[]> refs=new HashMap<>(input);refs.put("notice",new float[]{1});refs.put("empty",new float[]{1});
  for(String key:KEYS){if(key.equals("decline")&&!withDecline)continue;if(refs.get(key)==null||refs.get(key).length!=1536)throw new IllegalArgumentException("ポイポイうさぎの見本がありません："+key);region(e,key,key.equals("game")?"満足度の見出しとミッション文字":key.equals("full")?"満足度100.00%":key.equals("offline")?"オフラインボーナスの獲得":key.equals("bonus")?"このままはじめる":key.equals("missions")?"デイリーミッションと戻る":key.equals("decline")?DECLINE_NAME:"獲得コインの閉じる",anchors(key)[1]);}
  if(refs.get("get")==null||refs.get("get").length!=1536)throw new IllegalArgumentException("達成済みゲットの見本がありません");region(e,"get","画面に見えている達成済みゲット",GET_COLUMN);region(e,"notice","ミッションの赤い通知",NOTICE);region(e,"empty","表示中のゲットがなければミッションから戻る",BACK);
  for(String[] s:new String[][]{{"check","画面判別"},{"main","にんじんを与える"},{"offline","通常のオフライン獲得"},{"offline-wait","オフライン獲得後の確認"},{"bonus","そのまま継続"},{"reward","獲得コインを閉じる"},{"missions","達成済みゲットを回収"},{"mission-tap","満腹のミッションを開く"},{"mission-opening","ミッションの表示待ち"},{"claim-wait","ゲット後の報酬・CMを確認"},{"mission-ad","回収中のCMから復帰"},{"mission-verify","回収先の表示を確認"},{"ad","CMの終了・ホーム復帰を確認"},{"verify","通常プレイへの復帰を確認"}})state(e,s[0],s[1]);
  for(String state:new String[]{"check","verify","ad","offline-wait"})classify(e,refs,state,false);
  for(String state:new String[]{"mission-opening","mission-ad","mission-verify","claim-wait"})classify(e,refs,state,true);
  dialogs(e,refs,"main");image(e,refs,"main","main-full","full",PhaseEngine.MATCH,PhaseEngine.WAIT,"mission-tap",false);image(e,refs,"main","main-notice","notice",PhaseEngine.MATCH,PhaseEngine.WAIT,"mission-tap",false);image(e,refs,"main","main-lost","game",PhaseEngine.DISAPPEAR,PhaseEngine.WAIT,"check",true);timer(e,"main",60000,PhaseEngine.WAIT,"mission-tap");
  dialogs(e,refs,"mission-tap");image(e,refs,"mission-tap","mission-button-tap","game",PhaseEngine.MATCH,PhaseEngine.CENTER,"mission-opening",true).name="左下のミッションを開く";
  for(String key:new String[]{"offline","bonus","reward"}){String next=key.equals("offline")?"offline-wait":key.equals("bonus")?"mission-verify":"mission-opening";image(e,refs,key,key+"-tap",key,PhaseEngine.MATCH,PhaseEngine.CENTER,next,false).name=key.equals("offline")?"広告を使わず獲得":key.equals("bonus")?"このままはじめる":"報酬の閉じる";image(e,refs,key,key+"-gone",key,PhaseEngine.DISAPPEAR,PhaseEngine.WAIT,next,true);timer(e,key,5000,PhaseEngine.WAIT,next);}
  image(e,refs,"missions","get-tap","get",PhaseEngine.MATCH,PhaseEngine.CENTER,"claim-wait",false).name="達成済みゲットだけを回収";image(e,refs,"missions","missions-reward","reward",PhaseEngine.MATCH,PhaseEngine.WAIT,"reward",false);image(e,refs,"missions","mission-back","empty",PhaseEngine.MATCH,PhaseEngine.CENTER,"verify",true).name="表示中のゲットがないため戻る";image(e,refs,"missions","missions-anchor","missions",PhaseEngine.MATCH,PhaseEngine.WAIT,"missions",true);
  timer(e,"check",2500,PhaseEngine.WAIT,"ad");timer(e,"offline-wait",5000,PhaseEngine.WAIT,"check");timer(e,"mission-tap",4000,PhaseEngine.WAIT,"mission-ad");timer(e,"mission-opening",6000,PhaseEngine.WAIT,"mission-ad");timer(e,"claim-wait",5000,PhaseEngine.WAIT,"mission-ad");timer(e,"missions",6000,PhaseEngine.WAIT,"mission-ad");timer(e,"mission-ad",30000,PhaseEngine.RETURN_APP,"mission-verify");timer(e,"mission-verify",6000,PhaseEngine.RETURN_APP,"mission-verify");timer(e,"ad",30000,PhaseEngine.RETURN_APP,"verify");timer(e,"verify",6000,PhaseEngine.RETURN_APP,"verify");
  if(withDecline)declines(e,refs);e.initial=e.current=P+"check";return true;
 }
 static void declines(PhaseEngine e,Map<String,float[]> refs){
  region(e,"decline",DECLINE_NAME,DECLINE[1]);
  List<PhaseEngine.Phase> previous=new ArrayList<>(e.phases.values());
  for(PhaseEngine.Phase phase:previous){String id=phase.id.substring(P.length());image(e,refs,id,id+"-decline","decline",PhaseEngine.MATCH,PhaseEngine.WAIT,missionPending(phase.id)?"mission-decline":"decline",false).name="タップ2倍の使用しないを確認";}
  for(String id:new String[]{"decline","mission-decline"}){
   String next=id.startsWith("mission-")?"mission-verify":"verify";
   state(e,id,"タップ2倍を使用しない");state(e,id+"-wait","使用しないの完了待ち");
   image(e,refs,id,id+"-tap","decline",PhaseEngine.MATCH,PhaseEngine.CENTER,id+"-wait",false).name="使用しない";
   image(e,refs,id,id+"-gone","decline",PhaseEngine.DISAPPEAR,PhaseEngine.WAIT,next,true);
   timer(e,id,5000,PhaseEngine.WAIT,next);
   image(e,refs,id+"-wait",id+"-wait-gone","decline",PhaseEngine.DISAPPEAR,PhaseEngine.WAIT,next,true);
   timer(e,id+"-wait",1500,PhaseEngine.WAIT,id);
  }
 }
 static PhaseEngine version191Defaults(Map<String,float[]> refs){PhaseEngine previous=new PhaseEngine();install(previous,refs,false);return previous;}
 static boolean upgrade(PhaseEngine e,Map<String,float[]> refs){if(e.windowRelative||!e.phases.containsKey(P+"main")||e.phases.containsKey(P+"decline")||refs.get("decline")==null||!AutomatonGraph.sameConfiguration(e,version191Defaults(refs)))return false;declines(e,refs);e.clearHistory();return true;}
 static boolean playable(PhaseEngine e,Map<String,float[]> samples){if(!e.current.equals(P+"main"))return false;PhaseEngine.Watch game=e.rule(P+"verify-game");if(game==null||game.distance(samples.get(game.regionId))>game.threshold)return false;for(String key:new String[]{"offline","bonus","reward","missions","full","notice","decline"}){PhaseEngine.Watch w=e.rule(P+"main-"+key);if(w!=null&&w.distance(samples.get(w.regionId))<=w.threshold)return false;}return true;}
 static boolean missionPending(String state){return state.startsWith(P+"mission")||state.equals(P+"claim-wait")||state.equals(P+"bonus")||state.equals(P+"reward");}
 static String recoveryState(String state){return P+(missionPending(state)?"mission-ad":"ad");}
 static String recoveryTarget(String state){return P+(missionPending(state)?"mission-verify":"verify");}
 private PoipoiPreset(){}
}
