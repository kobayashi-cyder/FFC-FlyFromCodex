package jp.tapmarker;
import java.util.*;
/** Explicit finite automaton. Only the main state permits marker tapping. */
public final class UsagiPreset {
 public static final String APP="com.kurashiru.fue.usagi",PREFIX="usagi-default-";
 static final float[] PROMO_TITLE={.25f,.298f,.756f,.326f},PROMO_CLOSE={.862f,.261f,.912f,.293f};
 static void region(PhaseEngine e,String id,String name,float... bounds){PhaseEngine.Region r=new PhaseEngine.Region();r.id=PREFIX+id;r.name=name;r.bounds=bounds;e.regions.put(r.id,r);}
 static PhaseEngine.Watch rule(String id,String roi,int condition,int action,String next){PhaseEngine.Watch w=new PhaseEngine.Watch();w.id=PREFIX+id;w.regionId=roi.isEmpty()?"":PREFIX+roi;w.condition=condition;w.action=action;w.nextPhase=next.isEmpty()?"":PREFIX+next;w.waitMs=0;return w;}
 static void state(PhaseEngine e,String id,String name){PhaseEngine.Phase p=new PhaseEngine.Phase();p.id=PREFIX+id;p.name="増えうさぎ："+name;int i=e.phases.size();p.graphX=40+(i%3)*300;p.graphY=80+(i/3)*180;p.markersDuring=id.equals("main");e.phases.put(p.id,p);}
 static PhaseEngine.Watch image(PhaseEngine e,Map<String,float[]> refs,String state,String id,String roi,int condition,int action,String next){PhaseEngine.Watch w=rule(id,roi,condition,action,next);w.name=id;float[] ref=refs.get(roi);w.reference=ref==null?null:ref.clone();e.phases.get(PREFIX+state).watches.add(w);return w;}
 static PhaseEngine.Watch timer(PhaseEngine e,String state,String id,long ms,int action,String next,boolean fallback){PhaseEngine.Watch w=rule(id,"",PhaseEngine.TIMEOUT,action,next);w.name=id;w.timeoutMs=ms;w.fallback=fallback;e.phases.get(PREFIX+state).watches.add(w);return w;}
 static void classify(PhaseEngine e,Map<String,float[]> r,String state){
  image(e,r,state,state+"-offline","offline",PhaseEngine.MATCH,PhaseEngine.WAIT,"offline");
  image(e,r,state,state+"-bonus","bonus",PhaseEngine.MATCH,PhaseEngine.WAIT,"bonus");
  image(e,r,state,state+"-reward","reward",PhaseEngine.MATCH,PhaseEngine.WAIT,"reward");
  image(e,r,state,state+"-missions","missions",PhaseEngine.MATCH,PhaseEngine.WAIT,"missions");
  image(e,r,state,state+"-use","dialog",PhaseEngine.MATCH,PhaseEngine.WAIT,"use");
  image(e,r,state,state+"-clear","clear",PhaseEngine.MATCH,PhaseEngine.WAIT,"clear");
  PhaseEngine.Watch home=image(e,r,state,state.equals("verify")?"return":state+"-game","game",PhaseEngine.MATCH,PhaseEngine.WAIT,"main");home.fallback=true;
 }
 public static boolean install(PhaseEngine e,float[] dialog,float[] game){Map<String,float[]> r=new HashMap<>();r.put("dialog",dialog);r.put("game",game);return install(e,r);}
 public static boolean install(PhaseEngine e,Map<String,float[]> r){if(e.phases.containsKey(PREFIX+"main"))return false;
  region(e,"dialog","使用する（押さない）",.37f,.535f,.63f,.575f);
  region(e,"cancel","使用確認のキャンセル",.37f,.61f,.63f,.635f);
  region(e,"game","主画面の固定見出し",.66f,.082f,.77f,.112f);
  region(e,"clear","残り0匹・ステージクリア",.49f,.159f,.55f,.184f);
  region(e,"mission-button","ミッションボタン",.38f,.087f,.50f,.108f);region(e,"mission-touch","ミッション押下位置（炎アイコン）",.365f,.09f,.395f,.11f);
  region(e,"missions","デイリーミッション見出し",.28f,.164f,.73f,.19f);
  region(e,"get","ステージ報酬ゲット",.70f,.638f,.84f,.666f);
  region(e,"play","ゲームへ戻るプレイ",.70f,.638f,.84f,.666f);
  region(e,"reward","報酬獲得の見出し",.18f,.34f,.82f,.368f);
  region(e,"close","報酬の閉じる",.36f,.548f,.62f,.575f);
  region(e,"offline","オフラインボーナス見出し",.30f,.24f,.70f,.312f);
  region(e,"claim","広告を使わず獲得",.43f,.65f,.57f,.677f);
  region(e,"bonus","このままはじめる",.32f,.62f,.68f,.645f);
  String[] ids={"main","classify","clear","open-missions","missions","claim-wait","reward","offline","bonus","use","ad","verify","error","account","unknown"};
  String[] names={"増やす","画面の判別","クリア演出待ち","ミッションを開く","ミッション確認","報酬表示待ち","報酬を閉じる","オフライン獲得","ボーナスから開始","使用確認をキャンセル","CMから復帰","復帰画面を確認","異常ダイアログ","アカウント操作を阻止","未確認画面で停止"};
  for(int i=0;i<ids.length;i++)state(e,ids[i],names[i]);
  image(e,r,"main","leave-game","game",PhaseEngine.DISAPPEAR,PhaseEngine.WAIT,"classify");
  image(e,r,"main","stage-zero","clear",PhaseEngine.MATCH,PhaseEngine.WAIT,"clear");
  classify(e,r,"classify");timer(e,"classify","未判別はCM復帰へ",7000,PhaseEngine.WAIT,"ad",true);
  image(e,r,"clear","clear-away","clear",PhaseEngine.DISAPPEAR,PhaseEngine.WAIT,"open-missions").fallback=true;
  image(e,r,"clear","clear-popup","bonus",PhaseEngine.MATCH,PhaseEngine.WAIT,"bonus");
  timer(e,"clear","演出待ち上限",3500,PhaseEngine.WAIT,"open-missions",true);
  PhaseEngine.Watch open=image(e,r,"open-missions","主画面確認してミッションへ","game",PhaseEngine.MATCH,PhaseEngine.WAIT,"mission-tap");
  state(e,"mission-tap","ミッションボタンをタップ");image(e,r,"mission-tap","ミッション中央をタップ","mission-button",PhaseEngine.MATCH,PhaseEngine.CENTER,"missions").threshold=.03f;image(e,r,"mission-tap","mission-home-check","game",PhaseEngine.CHANGE,PhaseEngine.WAIT,"").fallback=true;timer(e,"mission-tap","ボタン未確認なら再判別",3000,PhaseEngine.WAIT,"open-missions",true);
  timer(e,"open-missions","主画面が戻らない",5000,PhaseEngine.WAIT,"mission-tap",true);
  image(e,r,"missions","ゲットのみタップ","get",PhaseEngine.MATCH,PhaseEngine.CENTER,"claim-wait");
  image(e,r,"missions","報酬が先に表示","reward",PhaseEngine.MATCH,PhaseEngine.WAIT,"reward");
  image(e,r,"missions","未達成ならゲームへ戻る","play",PhaseEngine.MATCH,PhaseEngine.CENTER,"verify");
  timer(e,"missions","報酬がない場合は戻る",8000,PhaseEngine.WAIT,"classify",true);
  image(e,r,"claim-wait","獲得を確認","reward",PhaseEngine.MATCH,PhaseEngine.WAIT,"reward");
  image(e,r,"claim-wait","claimed-play","play",PhaseEngine.MATCH,PhaseEngine.CENTER,"verify").fallback=true;
  timer(e,"claim-wait","報酬の応答なし",5000,PhaseEngine.WAIT,"classify",true);
  image(e,r,"reward","閉じる中央","close",PhaseEngine.MATCH,PhaseEngine.CENTER,"classify");
  timer(e,"reward","閉じるを確認できない",5000,PhaseEngine.WAIT,"ad",true);
  image(e,r,"offline","通常獲得の中央","claim",PhaseEngine.MATCH,PhaseEngine.CENTER,"verify");
  timer(e,"offline","獲得を確認できない",5000,PhaseEngine.WAIT,"ad",true);
  image(e,r,"bonus","広告を使わず開始","bonus",PhaseEngine.MATCH,PhaseEngine.CENTER,"verify");
  timer(e,"bonus","開始を確認できない",5000,PhaseEngine.WAIT,"ad",true);
  state(e,"use-wait","キャンセル後の画面を確認");
  image(e,r,"use","use","cancel",PhaseEngine.MATCH,PhaseEngine.CENTER,"use-wait");
  image(e,r,"use","use-disappeared","dialog",PhaseEngine.DISAPPEAR,PhaseEngine.WAIT,"classify");
  image(e,r,"use","キャンセル非表示はCM処理","cancel",PhaseEngine.DISAPPEAR,PhaseEngine.WAIT,"ad").fallback=true;
  image(e,r,"use-wait","キャンセルが閉じた","cancel",PhaseEngine.DISAPPEAR,PhaseEngine.WAIT,"classify");
  timer(e,"use-wait","キャンセル再確認",3000,PhaseEngine.WAIT,"use",true);
  timer(e,"use","キャンセル未確認",5000,PhaseEngine.WAIT,"classify",true);
  classify(e,r,"ad");timer(e,"ad","Chrome経由で登録アプリへ",1500,PhaseEngine.RETURN_APP,"verify",true);
  classify(e,r,"verify");timer(e,"verify","復帰未確認は再試行",6000,PhaseEngine.RETURN_APP,"verify",true);
  timer(e,"error","異常から戻る",0,PhaseEngine.BACK,"verify",false);
  timer(e,"account","アカウント画面から戻る",0,PhaseEngine.BACK,"verify",false);
  timer(e,"unknown","未確認のため停止",0,PhaseEngine.STOP,"",false);
  addAmount(e,r);addMissionNotice(e,r);addOpenConfirmation(e,r);addMissionOpening(e,r);configureMissionGate(e,r);addPromo(e,r);e.initial=PREFIX+"main";e.current=e.initial;return true;
 }
 static boolean addPromo(PhaseEngine e,Map<String,float[]> refs){
  if(e.phases.containsKey(PREFIX+"promo")||refs.get("promo")==null||refs.get("promo-close")==null)return false;
  region(e,"promo","期間告知の見出し",PROMO_TITLE.clone());region(e,"promo-close","期間告知の閉じる",PROMO_CLOSE.clone());state(e,"promo","期間告知を閉じる");
  image(e,refs,"promo","期間告知の閉じるをタップ","promo-close",PhaseEngine.MATCH,PhaseEngine.CENTER,"classify");
  image(e,refs,"promo","告知が閉じた","promo",PhaseEngine.DISAPPEAR,PhaseEngine.WAIT,"classify").fallback=true;
  for(String id:new String[]{"classify","ad","verify","offline"})image(e,refs,id,id+"-promo","promo",PhaseEngine.MATCH,PhaseEngine.WAIT,"promo");
  return true;
 }
 public static boolean upgrade(PhaseEngine e){return upgrade(e,(float[])null);}
 public static boolean upgrade(PhaseEngine e,float[] missionReference){boolean changed=false;PhaseEngine.Watch bonus=e.rule(PREFIX+"広告を使わず開始");if(bonus!=null&&bonus.nextPhase.equals(PREFIX+"open-missions")&&e.phases.containsKey(PREFIX+"verify")){bonus.nextPhase=PREFIX+"verify";changed=true;}PhaseEngine.Watch tap=e.rule(PREFIX+"ミッション中央をタップ");if(missionReference!=null&&tap!=null&&tap.condition==PhaseEngine.TIMEOUT&&tap.action==PhaseEngine.CENTER&&tap.regionId.equals(PREFIX+"mission-button")&&tap.nextPhase.equals(PREFIX+"missions")){PhaseEngine.Region roi=e.regions.get(PREFIX+"mission-button");if(roi==null)return changed;if(Arrays.equals(roi.bounds,new float[]{.35f,.08f,.55f,.115f}))roi.bounds=new float[]{.38f,.087f,.50f,.108f};else if(!Arrays.equals(roi.bounds,new float[]{.38f,.087f,.50f,.108f}))return changed;tap.condition=PhaseEngine.MATCH;tap.reference=missionReference.clone();timer(e,"mission-tap","ボタン未確認なら再判別",3000,PhaseEngine.WAIT,"classify",true);changed=true;}return changed;}
 public static boolean upgrade(PhaseEngine e,Map<String,float[]> refs){boolean changed=upgrade(e,refs.get("mission-button"));if(e.phases.containsKey(PREFIX+"main")&&addPromo(e,refs))changed=true;if(e.phases.containsKey(PREFIX+"main")&&!e.regions.containsKey(PREFIX+"mission-touch")){region(e,"mission-touch","ミッション押下位置（炎アイコン）",.365f,.09f,.395f,.11f);changed=true;}PhaseEngine.Watch use=e.rule(PREFIX+"use");if(use!=null&&use.condition==PhaseEngine.MATCH&&use.action==PhaseEngine.BACK&&use.regionId.equals(PREFIX+"dialog")&&use.nextPhase.equals(PREFIX+"classify")&&refs.get("cancel")!=null){if(!e.regions.containsKey(PREFIX+"cancel"))region(e,"cancel","使用確認のキャンセル",.37f,.61f,.63f,.635f);if(!e.phases.containsKey(PREFIX+"use-wait")){state(e,"use-wait","キャンセル後の画面を確認");image(e,refs,"use-wait","キャンセルが閉じた","cancel",PhaseEngine.DISAPPEAR,PhaseEngine.WAIT,"classify");timer(e,"use-wait","キャンセル再確認",3000,PhaseEngine.WAIT,"use",true);}use.regionId=PREFIX+"cancel";use.action=PhaseEngine.CENTER;use.nextPhase=PREFIX+"use-wait";use.reference=refs.get("cancel").clone();timer(e,"use","キャンセル未確認",5000,PhaseEngine.WAIT,"classify",true);image(e,refs,"use","キャンセル非表示はCM処理","cancel",PhaseEngine.DISAPPEAR,PhaseEngine.WAIT,"ad").fallback=true;e.phases.get(PREFIX+"use").name="増えうさぎ：使用確認をキャンセル";changed=true;}PhaseEngine.Watch disappeared=e.rule(PREFIX+"use-disappeared");if(disappeared!=null&&disappeared.condition==PhaseEngine.DISAPPEAR&&disappeared.action==PhaseEngine.RETURN_APP&&disappeared.nextPhase.equals(PREFIX+"verify")){disappeared.action=PhaseEngine.WAIT;disappeared.nextPhase=PREFIX+"classify";disappeared.fallback=false;changed=true;}PhaseEngine.Watch mission=e.rule(PREFIX+"報酬がない場合は戻る");if(mission!=null&&mission.condition==PhaseEngine.TIMEOUT&&mission.action==PhaseEngine.BACK&&mission.nextPhase.equals(PREFIX+"verify")){mission.action=PhaseEngine.WAIT;mission.nextPhase=PREFIX+"classify";changed=true;}if(e.phases.containsKey(PREFIX+"main")&&!e.regions.containsKey(PREFIX+"mission-notice")){addMissionNotice(e,refs);changed=true;}PhaseEngine.Watch oldNotice=e.rule(PREFIX+"mission-notice");if(oldNotice!=null){for(PhaseEngine.Phase phase:e.phases.values())if(phase.watches.remove(oldNotice))changed=true;}PhaseEngine.Watch button=e.rule(PREFIX+"ミッション中央をタップ");if(button!=null&&button.condition==PhaseEngine.MATCH&&button.action==PhaseEngine.CENTER){if(button.regionId.equals(PREFIX+"mission-button")&&button.threshold==.004f){button.threshold=.03f;changed=true;}if(e.rule(PREFIX+"mission-home-check")==null){image(e,refs,"mission-tap","mission-home-check","game",PhaseEngine.CHANGE,PhaseEngine.WAIT,"").fallback=true;changed=true;}}if(e.phases.containsKey(PREFIX+"open-missions")&&e.rule(PREFIX+"already-open-missions")==null){addOpenConfirmation(e,refs);changed=true;}PhaseEngine.Phase main=e.phases.get(PREFIX+"main");if(main!=null){for(java.util.Iterator<PhaseEngine.Watch> it=main.watches.iterator();it.hasNext();)if(it.next().id.equals(PREFIX+"amount-monitor")){it.remove();changed=true;}}PhaseEngine.Watch pendingPlay=e.rule(PREFIX+"claimed-play");if(pendingPlay==null&&e.phases.containsKey(PREFIX+"claim-wait")&&refs.get("play")!=null){image(e,refs,"claim-wait","claimed-play","play",PhaseEngine.MATCH,PhaseEngine.CENTER,"verify").fallback=true;changed=true;}PhaseEngine.Watch opening=e.rule(PREFIX+"主画面が戻らない");if(opening!=null&&opening.nextPhase.equals(PREFIX+"classify")){opening.nextPhase=PREFIX+"mission-tap";opening.timeoutMs=5000;changed=true;}PhaseEngine.Watch tapping=e.rule(PREFIX+"ボタン未確認なら再判別");if(tapping!=null&&tapping.nextPhase.equals(PREFIX+"classify")){tapping.nextPhase=PREFIX+"open-missions";changed=true;}for(String id:new String[]{"未判別はCM復帰へ","報酬がない場合は戻る"}){PhaseEngine.Watch delay=e.rule(PREFIX+id);long minimum=id.equals("未判別はCM復帰へ")?7000:8000;if(delay!=null&&delay.timeoutMs<minimum){delay.timeoutMs=minimum;changed=true;}}PhaseEngine.Watch play=e.rule(PREFIX+"未達成ならゲームへ戻る");if(play!=null&&play.condition==PhaseEngine.MATCH&&play.action==PhaseEngine.BACK&&play.regionId.equals(PREFIX+"play")&&play.nextPhase.equals(PREFIX+"verify")){play.action=PhaseEngine.CENTER;play.name="プレイを押してゲームへ戻る";PhaseEngine.Region playRegion=e.regions.get(play.regionId);if(playRegion!=null)playRegion.name="ゲームへ戻るプレイ";changed=true;}if(e.phases.containsKey(PREFIX+"main")&&!e.phases.containsKey(PREFIX+"amount-wait")&&refs.get("amount-ready")!=null){addAmount(e,refs);changed=true;}PhaseEngine.Watch ad=e.rule(PREFIX+"Chrome経由で登録アプリへ");if(ad!=null&&ad.action==PhaseEngine.RETURN_APP&&e.rule(PREFIX+"ad-game")==null){classify(e,refs,"ad");ad.fallback=true;ad.timeoutMs=1500;changed=true;}if(button!=null&&button.condition==PhaseEngine.MATCH&&button.action==PhaseEngine.CENTER&&button.nextPhase.equals(PREFIX+"missions")){addMissionOpening(e,refs);changed=true;}if(configureMissionGate(e,refs))changed=true;PhaseEngine.Watch homeOpening=e.rule(PREFIX+"主画面確認してミッションへ");if(homeOpening!=null&&!homeOpening.fallback){homeOpening.fallback=true;changed=true;}for(PhaseEngine.Phase state:e.phases.values())for(PhaseEngine.Watch w:state.watches)if(w.id.startsWith(PREFIX)&&w.waitMs==250){w.waitMs=0;changed=true;}return changed;}
 static boolean configureMissionGate(PhaseEngine e,Map<String,float[]> refs){
  PhaseEngine.Watch w=e.rule(PREFIX+"ミッション中央をタップ");PhaseEngine.Region button=e.regions.get(PREFIX+"mission-button");
  if(w==null||w.condition!=PhaseEngine.MATCH||w.action!=PhaseEngine.CENTER||!w.regionId.equals(PREFIX+"mission-button")||button==null||!Arrays.equals(button.bounds,new float[]{.38f,.087f,.50f,.108f})||refs.get("game")==null)return false;
  w.regionId=PREFIX+"game";w.reference=refs.get("game").clone();PhaseEngine.Watch home=e.rule(PREFIX+"return");w.threshold=home==null?.004f:home.threshold;w.fallback=true;w.name="主画面を確認してミッションをタップ";return true;
 }
 public static float[] tapCenter(PhaseEngine e,PhaseEngine.Watch w,int width,int height){
  PhaseEngine.Region r=e.regions.get(w.regionId);
  if(w.id.equals(PREFIX+"ミッション中央をタップ")&&r!=null&&(w.regionId.equals(PREFIX+"game")||Arrays.equals(r.bounds,new float[]{.38f,.087f,.50f,.108f}))){
   PhaseEngine.Region touch=e.regions.get(PREFIX+"mission-touch");if(touch!=null)r=touch;
  }
  return RegionMath.center(r.bounds,width,height);
 }
 static void addMissionOpening(PhaseEngine e,Map<String,float[]> refs){
  if(!e.phases.containsKey(PREFIX+"mission-opening")){
   state(e,"mission-opening","ミッション画面が開いたか確認");
   image(e,refs,"mission-opening","mission-opened","missions",PhaseEngine.MATCH,PhaseEngine.WAIT,"missions");
   image(e,refs,"mission-opening","mission-open-reward","reward",PhaseEngine.MATCH,PhaseEngine.WAIT,"reward");
   image(e,refs,"mission-opening","mission-open-bonus","bonus",PhaseEngine.MATCH,PhaseEngine.WAIT,"bonus");
   image(e,refs,"mission-opening","mission-open-use","cancel",PhaseEngine.MATCH,PhaseEngine.WAIT,"use");
   image(e,refs,"mission-opening","mission-open-retry","game",PhaseEngine.MATCH,PhaseEngine.WAIT,"mission-tap").fallback=true;
   timer(e,"mission-opening","mission-open-unknown",3000,PhaseEngine.WAIT,"classify",true);
  }
  PhaseEngine.Watch tap=e.rule(PREFIX+"ミッション中央をタップ");if(tap!=null&&tap.nextPhase.equals(PREFIX+"missions"))tap.nextPhase=PREFIX+"mission-opening";
  PhaseEngine.Watch home=e.rule(PREFIX+"主画面確認してミッションへ");if(home!=null)home.fallback=true;
 }
 static void addOpenConfirmation(PhaseEngine e,Map<String,float[]> refs){for(String id:new String[]{"open-missions","mission-tap"}){image(e,refs,id,id.equals("open-missions")?"already-open-missions":"already-tapped-missions","missions",PhaseEngine.MATCH,PhaseEngine.WAIT,"missions");image(e,refs,id,id+"-reward-shown","reward",PhaseEngine.MATCH,PhaseEngine.WAIT,"reward");}}
 static void addMissionNotice(PhaseEngine e,Map<String,float[]> refs){region(e,"mission-notice","ミッション通知（完了の根拠には使わない）",.535f,.083f,.540f,.086f);}
 public static Map<String,float[]> missionSamples(PhaseEngine e,Map<String,float[]> samples){return missionSamples(e,samples,false);}
 public static Map<String,float[]> missionSamples(PhaseEngine e,Map<String,float[]> samples,boolean handled){if(handled&&isMain(e.current)){Map<String,float[]> result=new HashMap<>(samples);result.remove(PREFIX+"mission-notice");e.confirmations.remove(PREFIX+"mission-notice");return result;}PhaseEngine.Watch notice=e.rule(PREFIX+"mission-notice");float[] mark=samples.get(PREFIX+"mission-notice");if(!isMain(e.current)||notice==null||notice.reference==null||mark==null||FlyCircuit.distance(mark,notice.reference)>notice.threshold)return samples;Map<String,float[]> result=new HashMap<>(samples);result.remove(PREFIX+"amount-ready");e.confirmations.remove(PREFIX+"amount-tap");return result;}
 static void addAmount(PhaseEngine e,Map<String,float[]> refs){
  region(e,"amount-ready","増える量の必要表示（CMを見るは押さない）",.178f,.859f,.223f,.872f);
  region(e,"amount-level","増える量の現在レベル",.065f,.835f,.16f,.867f);
  state(e,"amount-wait","増える量の変更確認");
  image(e,refs,"main","amount-tap","amount-ready",PhaseEngine.MATCH,PhaseEngine.CENTER,"amount-wait").fallback=true;
  
  image(e,refs,"amount-wait","amount-changed","amount-level",PhaseEngine.DISAPPEAR,PhaseEngine.WAIT,"classify").fallback=true;
  image(e,refs,"amount-wait","amount-game-away","game",PhaseEngine.DISAPPEAR,PhaseEngine.WAIT,"classify");
  timer(e,"amount-wait","増える量の応答なしは再判別",3000,PhaseEngine.WAIT,"classify",true);
 }
 public static void prepareAmount(PhaseEngine e,Map<String,float[]> samples){PhaseEngine.Watch changed=e.rule(PREFIX+"amount-changed");float[] level=samples.get(PREFIX+"amount-level");if(changed!=null&&level!=null)changed.reference=level.clone();}
 public static boolean mayTap(String state,boolean confirmed,boolean checking,boolean acting,boolean returning,boolean insideApp,long observedAt,long now){return isMain(state)&&confirmed&&!checking&&!acting&&!returning&&insideApp&&now>=observedAt&&now-observedAt<1000;}
 public static boolean isMain(String id){return (PREFIX+"main").equals(id);}
 static boolean leavesMissions(PhaseEngine.Watch watch){return watch!=null&&(watch.id.equals(PREFIX+"未達成ならゲームへ戻る")||watch.id.equals(PREFIX+"claimed-play")||watch.id.equals(UsagiMissionEvidence.BACK));}
}
