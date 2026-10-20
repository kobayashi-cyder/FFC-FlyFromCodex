package jp.tapmarker;
import java.util.*;

/** Observed box-merge loop: dismiss, start, throw, continue, collect, recover. */
final class BoxPreset {
 static final String APP=GameTarget.BOX,P="box-default-";
 static final float MAIN_Y=.68f;
 static float[] b(float l,float t,float r,float z){return new float[]{l/360,t/808,r/360,z/808};}
 static final String[] KEYS={"game","notice","title","bonus","reward","claim"};
 static final float[][] GAME={b(190,71,252,87),b(151,721,207,733)};
 static final float[][] NOTICE={b(80,247,285,269),b(311,211,330,237)};
 static final float[][] TITLE={b(104,186,273,263),b(151,648,211,669)};
 static final float[][] BONUS={b(68,280,296,305),b(112,561,253,580)};
 static final float[][] REWARD={b(64,274,297,296),b(148,447,213,468)};
 static final float[][] CLAIM={b(151,721,207,733),b(143,128,234,148)};
 static float[][] anchors(String key){switch(key){case "game":return GAME;case "notice":return NOTICE;case "title":return TITLE;case "bonus":return BONUS;case "reward":return REWARD;case "claim":return CLAIM;default:throw new IllegalArgumentException(key);}}
 static void state(PhaseEngine e,String key,String name){PhaseEngine.Phase p=new PhaseEngine.Phase();p.id=P+key;p.name="ボックスマージ："+name;p.markersDuring=key.equals("main");int i=e.phases.size();p.graphX=i%3*300;p.graphY=i/3*180;e.phases.put(p.id,p);}
 static void image(PhaseEngine e,Map<String,float[]> refs,String state,String id,String key,int condition,int action,String next,boolean fallback){
  PhaseEngine.Watch w=new PhaseEngine.Watch();w.id=P+id;w.name=key.equals("claim")&&action==PhaseEngine.CENTER?"100投の報酬を受け取る":key.equals("bonus")&&action==PhaseEngine.CENTER?"このままはじめる":key.equals("reward")&&action==PhaseEngine.CENTER?"獲得コインを閉じる":key.equals("notice")&&action==PhaseEngine.CENTER?"告知の右上×を閉じる":key.equals("title")&&action==PhaseEngine.CENTER?"プレイを開始":id;
  w.regionId=P+key;w.reference=refs.get(key).clone();w.pairedReference=true;w.threshold=.012f;w.condition=condition;w.action=action;w.nextPhase=P+next;w.waitMs=0;w.fallback=fallback;e.phases.get(P+state).watches.add(w);
 }
 static void timer(PhaseEngine e,String state,long ms,int action,String next){PhaseEngine.Watch w=new PhaseEngine.Watch();w.id=P+state+"-timeout";w.name="画面を再確認";w.condition=PhaseEngine.TIMEOUT;w.timeoutMs=ms;w.action=action;w.nextPhase=P+next;w.waitMs=0;w.fallback=true;e.phases.get(P+state).watches.add(w);}
 static void classify(PhaseEngine e,Map<String,float[]> refs,String state){for(String key:new String[]{"notice","reward","bonus","title","claim"})image(e,refs,state,state+"-"+key,key,PhaseEngine.MATCH,PhaseEngine.WAIT,key,false);image(e,refs,state,state+"-game","game",PhaseEngine.MATCH,PhaseEngine.WAIT,"main",true);}
 static boolean install(PhaseEngine e,Map<String,float[]> refs){
  if(e.phases.containsKey(P+"main"))return false;
  for(String key:KEYS){float[] v=refs.get(key);if(v==null||v.length!=1536)throw new IllegalArgumentException("ボックスマージの見本がありません："+key);PhaseEngine.Region r=new PhaseEngine.Region();r.id=P+key;r.name=key.equals("game")?"ミッション文字とアイテム補充文字":key.equals("notice")?"告知見出しと右上×":key.equals("title")?"ロゴとプレイ":key.equals("bonus")?"コインアップ見出しとこのままはじめる":key.equals("claim")?"アイテム補充文字と報酬を受け取る":"獲得コイン見出しと閉じる";r.bounds=anchors(key)[1].clone();e.regions.put(r.id,r);}
  for(String[] row:new String[][]{{"check","画面判別"},{"main","中央付近へキューブを投下"},{"notice","開始告知を閉じる"},{"title","プレイを開始"},{"bonus","100投後にそのまま継続"},{"reward","獲得コインを閉じる"},{"claim","100投の報酬を受け取る"},{"wait","操作後の表示を確認"},{"ad","CMの終了・ホーム復帰を確認"},{"verify","元ゲームの表示を確認"}})state(e,row[0],row[1]);
  for(String s:new String[]{"check","wait","ad","verify"})classify(e,refs,s);
  timer(e,"check",2500,PhaseEngine.WAIT,"ad");timer(e,"wait",3000,PhaseEngine.WAIT,"check");timer(e,"ad",30000,PhaseEngine.RETURN_APP,"verify");timer(e,"verify",6000,PhaseEngine.RETURN_APP,"verify");
  for(String key:new String[]{"notice","reward","bonus","title","claim"}){image(e,refs,"main","main-"+key,key,PhaseEngine.MATCH,PhaseEngine.WAIT,key,false);image(e,refs,key,key+"-tap",key,PhaseEngine.MATCH,PhaseEngine.CENTER,key.equals("claim")?"ad":"wait",false);image(e,refs,key,key+"-gone",key,PhaseEngine.DISAPPEAR,PhaseEngine.WAIT,"check",true);timer(e,key,3500,PhaseEngine.WAIT,"check");}
  image(e,refs,"main","main-lost","game",PhaseEngine.DISAPPEAR,PhaseEngine.WAIT,"check",true);
  e.initial=e.current=P+"check";return true;
 }
 static boolean playable(PhaseEngine e,Map<String,float[]> samples){
  if(!e.current.equals(P+"main"))return false;PhaseEngine.Watch w=e.rule(P+"verify-game");if(w==null||w.distance(samples.get(P+"game"))>w.threshold)return false;
  for(String key:new String[]{"notice","reward","bonus","title","claim"}){w=e.rule(P+"main-"+key);if(w!=null&&w.distance(samples.get(w.regionId))<=w.threshold)return false;}return true;
 }
 private BoxPreset(){}
}
