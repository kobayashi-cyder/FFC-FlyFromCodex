package jp.tapmarker;
import java.util.*;

/** Shovel/amount upgrades are side events. They never enter a main-engine wait phase. */
public final class UpgradeEngine {
 public static final class Decision {
  public final PhaseEngine.Watch watch;public final String phase;public final long epoch,imageAt;public final boolean advertisement;final float[] before;final String resultRegion;
  Decision(PhaseEngine.Watch watch,String phase,long epoch,long imageAt,String resultRegion,float[] before,boolean advertisement){this.watch=watch;this.phase=phase;this.epoch=epoch;this.imageAt=imageAt;this.resultRegion=resultRegion;this.before=before==null?null:before.clone();this.advertisement=advertisement;}
 }
 private final PhaseEngine owner;private final LinkedHashMap<String,PhaseEngine.Watch> rules=new LinkedHashMap<>();private final Map<String,String> origins=new HashMap<>();
 private long lastFrame=-1,nextAt,sentAt;private int confirmations;private Decision active;private boolean sent;private String status="対象の強化操作なし";
 private boolean confirmingAd,adPending,adAway;private float[] adBefore;private long adSentAt,adResumeAt=-1,nextAdAt;
 public UpgradeEngine(PhaseEngine engine,String pkg){
  owner=engine;String id=pkg.equals(FarmPreset.APP)?FarmPreset.P+"upgrade-ready":pkg.equals(UsagiPreset.APP)?UsagiPreset.PREFIX+"amount-tap":"";
  if(id.isEmpty())return;
  for(PhaseEngine.Phase phase:engine.phases.values())for(Iterator<PhaseEngine.Watch> it=phase.watches.iterator();it.hasNext();){PhaseEngine.Watch w=it.next();if(w.id.equals(id)&&w.action==PhaseEngine.CENTER&&w.condition==PhaseEngine.MATCH&&phase.markersDuring){rules.put(w.id,w);origins.put(w.id,phase.id);it.remove();}}
  if(!rules.isEmpty())status="強化ボタンの確認待ち";
  String wait=pkg.equals(FarmPreset.APP)?FarmPreset.P+"upgrade-wait":UsagiPreset.PREFIX+"amount-wait";
  boolean referenced=engine.initial.equals(wait)||engine.current.equals(wait);for(PhaseEngine.Phase phase:engine.phases.values())for(PhaseEngine.Watch w:phase.watches)if(w.nextPhase.equals(wait))referenced=true;
  if(!referenced&&!rules.isEmpty())engine.phases.remove(wait);
 }
 public boolean enabled(){return !rules.isEmpty();}
 public String status(){return status;}
 public Decision pending(){return active;}
 public boolean awaitingResult(){return adPending||active!=null&&sent;}
 public void addRegions(List<PhaseEngine.Region> regions){for(PhaseEngine.Watch rule:rules.values()){add(regions,rule.regionId);for(String id:rule.guardIds)add(regions,id);if(rule.id.equals(UsagiPreset.PREFIX+"amount-tap"))add(regions,UsagiPreset.PREFIX+"amount-level");}}
 private void add(List<PhaseEngine.Region> regions,String id){PhaseEngine.Region region=owner.regions.get(id);if(region!=null&&!regions.contains(region))regions.add(region);}
 static boolean advertisement(PhaseEngine.Watch watch,Map<String,float[]> samples){float[] flag=samples.get(UsagiAmountEvidence.AD);return watch.id.equals(UsagiPreset.PREFIX+"amount-tap")&&flag!=null&&flag.length==1&&flag[0]==1;}
 public boolean visible(Decision decision,Map<String,float[]> samples){return decision!=null&&owner.current.equals(decision.phase)&&owner.epoch==decision.epoch&&decision.advertisement==advertisement(decision.watch,samples)&&decision.watch.distance(samples.get(decision.watch.regionId))<=decision.watch.threshold&&owner.guardsMatch(decision.watch,samples);}
 public Decision observe(Map<String,float[]> samples,long capturedAt,long now,boolean mainAllowed){
  if(!enabled())return null;
  if(!mainAllowed){interrupt(now,"他イベントのため強化操作を保留");return null;}
  if(capturedAt<=lastFrame||capturedAt>now||now-capturedAt>=1500)return null;lastFrame=capturedAt;
  if(adPending){
   float[] after=samples.get(UsagiPreset.PREFIX+"amount-level");
   if(capturedAt>adSentAt&&adBefore!=null&&after!=null&&FlyCircuit.distance(adBefore,after)>.004f){adPending=false;active=null;sent=false;confirmations=0;nextAt=now+1000;status="強化CM後のレベル表示変化を確認（効果量は未判定）";}
   else if(adAway){if(adResumeAt<0)adResumeAt=now;if(now-adResumeAt>=3000){adPending=false;active=null;sent=false;confirmations=0;nextAt=now+3000;status="強化CM後のレベル変更を未確認・再確認待ち";}}
   else if(now-adSentAt>=10000){adPending=false;active=null;sent=false;confirmations=0;nextAt=now+3000;status="強化CMの開始を未確認・再確認待ち";}
   return null;
  }
  if(active!=null){
   if(sent&&capturedAt>sentAt){float[] after=samples.get(active.resultRegion);if(active.before!=null&&after!=null&&FlyCircuit.distance(active.before,after)>.004f){active=null;sent=false;confirmations=0;nextAt=now+1000;status="強化後の表示変化を確認（効果量は未判定）";}else if(now-sentAt>=3000){active=null;sent=false;confirmations=0;nextAt=now+3000;status="強化後の表示変化を未確認・再確認待ち";}}
   return null;
  }
  if(now<nextAt)return null;
  for(PhaseEngine.Watch rule:rules.values())if(owner.current.equals(origins.get(rule.id))&&owner.eligible(rule)&&rule.distance(samples.get(rule.regionId))<=rule.threshold&&owner.guardsMatch(rule,samples)){
   if(rule.id.equals(UsagiPreset.PREFIX+"amount-tap")&&samples.get(UsagiPreset.PREFIX+"amount-level")==null){confirmations=0;status="増える量のレベル表示を確認中";return null;}
   boolean ad=advertisement(rule,samples);if(ad&&now<nextAdAt){confirmations=0;status="強化CMの再試行待ち（通常連打は継続）";return null;}
   if(confirmations>0&&confirmingAd!=ad)confirmations=0;confirmingAd=ad;
   if(++confirmations<2){status="強化ボタンを再確認中（通常連打は継続）";return null;}
   String result=rule.id.equals(UsagiPreset.PREFIX+"amount-tap")?UsagiPreset.PREFIX+"amount-level":rule.regionId;
   active=new Decision(rule,owner.current,owner.epoch,capturedAt,result,samples.get(result),ad);sent=false;status="強化専用タッパーへ1回の入力を指示";return active;
  }
  confirmations=0;return null;
 }
 public void sent(Decision decision,long now){if(active!=decision)return;sent=true;sentAt=now;if(decision.advertisement){adPending=true;adAway=false;adBefore=decision.before==null?null:decision.before.clone();adSentAt=now;adResumeAt=-1;nextAdAt=now+60000;status="増える量の強化CMを開始・復帰後にレベルを確認";}else status="強化タップ送信完了・表示変化を確認中（通常連打は継続）";}
 public void failed(Decision decision,long now){if(active!=decision)return;active=null;sent=false;confirmations=0;nextAt=now+1500;status="強化入力を受け付けず・再確認待ち";}
 public void interrupt(long now,String reason){active=null;sent=false;confirmations=0;nextAt=Math.max(nextAt,now+100);if(adPending){adAway=true;status="増える量：強化CMの終了・復帰を確認中";}else if(enabled())status=reason;}
}
