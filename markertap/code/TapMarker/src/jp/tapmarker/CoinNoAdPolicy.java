package jp.tapmarker;
import java.util.*;

/** Optional coin boosts are dismissed. Mandatory-ad exits remain available. */
final class CoinNoAdPolicy {
 static final String CANCEL=CoinPreset.P+"optional-cancel",WAIT=CoinPreset.P+"optional-cancel-wait",POINT=":coin-cancel-point";
 static final String[] BOOSTS={"boost-rare","boost-pocket","boost-tap2"};
 static boolean optional(String phase){for(String key:BOOSTS)if(phase.equals(CoinPreset.P+key))return true;return false;}
 static boolean adWatch(PhaseEngine.Watch w){for(String key:BOOSTS)if(w.id.equals(CoinPreset.P+key+"-cm"))return true;return false;}
 static boolean allowsUi(String app,String label,String context){
  if(!CoinPreset.APP.equals(app))return true;
  String kind=BancAutonomy.safeKind(label,context),text=BancAutonomy.normalize(label),scope=BancAutonomy.normalize(context);
  if(kind.equals("close")||kind.equals("resume"))return true;
  if(text.contains("cm")||text.contains("広告")||text.contains("動画"))return false;
  return !kind.equals("claim")||!scope.contains("cm")&&!scope.contains("広告")&&!scope.contains("動画");
 }
 static boolean apply(PhaseEngine e){
  if(!e.phases.containsKey(CoinPreset.P+"main"))return false;
  boolean changed=false;
  if(!e.regions.containsKey(CANCEL)){CoinPreset.region(e,"optional-cancel","CMを見ずにキャンセル",CoinPreset.POT_CANCEL_LABEL);changed=true;}
  if(!e.phases.containsKey(WAIT)){CoinPreset.state(e,"optional-cancel-wait","CMを見ずに閉じた表示を再確認",false);CoinPreset.timer(e,"optional-cancel-wait",1200,PhaseEngine.WAIT,"verify");changed=true;}
  PhaseEngine.Phase priorAd=e.phases.get(CoinPreset.P+"reward-ad");
  if(priorAd!=null&&!priorAd.name.equals("コイン落とし：既に表示された広告から復帰")){priorAd.name="コイン落とし：既に表示された広告から復帰";changed=true;}
  PhaseEngine.Watch priorWait=e.rule(CoinPreset.P+"reward-ad-timeout");if(priorWait!=null&&priorWait.timeoutMs>30000){priorWait.timeoutMs=30000;changed=true;}
  for(String key:BOOSTS){
   PhaseEngine.Phase phase=e.phases.get(CoinPreset.P+key);if(phase==null)continue;
   PhaseEngine.Watch old=e.rule(CoinPreset.P+key+"-cm");
   if(old==null||old.action!=PhaseEngine.CENTER&&old.action!=PhaseEngine.BACK)continue;
   String name="コイン落とし："+key+"のCMを見ずにキャンセル";
   if(!phase.name.equals(name)){phase.name=name;changed=true;}
   if(old!=null&&(old.action!=PhaseEngine.BACK||!old.nextPhase.equals(WAIT)||!old.fallback)){
    old.action=PhaseEngine.BACK;old.nextPhase=WAIT;old.fallback=true;old.name="任意CMを開始せず戻る（キャンセル文字未検出時）";changed=true;
   }
   String id=CoinPreset.P+key+"-cancel";
   if(e.rule(id)==null){PhaseEngine.Watch w=new PhaseEngine.Watch();w.id=id;w.name="CMを見ずにキャンセル文字をタップ";w.regionId=CANCEL;w.reference=new float[]{1};w.threshold=0;w.condition=PhaseEngine.MATCH;w.action=PhaseEngine.CENTER;w.nextPhase=WAIT;w.waitMs=0;phase.watches.add(w);changed=true;}
  }
  return changed;
 }
 static List<float[]> references(Map<String,float[]> refs){
  List<float[]> out=new ArrayList<>();for(String key:new TreeSet<>(refs.keySet()))if(key.equals("boost-pot")||key.startsWith("coin-control-boost-pot-")){float[] raw=refs.get(key);if(raw.length==1536)out.add(Arrays.copyOfRange(raw,768,1536));}return out;
 }
 static void sample(PhaseEngine e,Map<String,float[]> samples,int width,int height,RegionSampler.Pixels pixels,Map<String,float[]> transforms,List<float[]> references){
  if(!e.regions.containsKey(CANCEL))return;samples.put(CANCEL,new float[]{0});samples.remove(CANCEL+POINT);
  if(!optional(e.current)||references.isEmpty())return;
  String key=e.current.substring(CoinPreset.P.length());PhaseEngine.Watch dialog=e.rule(CoinPreset.P+key+"-cm");
  if(dialog==null||dialog.distance(samples.get(dialog.regionId))>dialog.threshold)return;
  float[] t=transforms.get(dialog.regionId);if(t==null)return;
  float best=Float.POSITIVE_INFINITY;float[] found=null;
  // Only the expected Cancel neighbourhood, well below the CM button, is inspected.
  for(float dx:new float[]{0,-.008f,.008f})for(float dy:new float[]{0,-.008f,.008f,-.016f,.016f,-.024f,.024f}){
   float[] b=AdaptiveLayout.map(CoinPreset.POT_CANCEL_LABEL,t);b[0]+=dx;b[2]+=dx;b[1]+=dy;b[3]+=dy;
   if(!AdaptiveLayout.inside(b))continue;float[] raw=RegionSampler.sample(width,height,b,pixels);
   for(float[] ref:references){float score=PhaseEngine.blockDistance(ref,raw);if(score<best){best=score;found=b;}}
  }
  if(found==null||best>.012f)return;
  float[] p=RegionMath.center(found,width,height);samples.put(CANCEL,new float[]{1});samples.put(CANCEL+POINT,new float[]{p[0],p[1],width,height});
 }
 static float[] point(Map<String,float[]> samples,int width,int height){
  float[] value=samples.get(CANCEL),point=samples.get(CANCEL+POINT);
  return value!=null&&value.length==1&&value[0]==1&&point!=null&&point.length==4&&point[2]==width&&point[3]==height?new float[]{point[0],point[1]}:null;
 }
 private CoinNoAdPolicy(){}
}
