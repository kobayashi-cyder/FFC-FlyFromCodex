package jp.tapmarker;
import java.util.*;

/** Identify the page independently of whether a task currently says Get or Play. */
final class UsagiMissionEvidence {
 static final String PAGE=":usagi-mission-page",BACK=UsagiPreset.PREFIX+"mission-back";
 final float[] title,titleReference,play,playReference,get,getReference,back,backReference;
 UsagiMissionEvidence(Map<String,float[]> evidence){
  String p="usagi-mission-small:";
  title=evidence.get(p+"title:bounds");titleReference=evidence.get(p+"title:reference");
  play=evidence.get(p+"play:bounds");playReference=evidence.get(p+"play:reference");
  get=evidence.get(p+"get:bounds");getReference=evidence.get(p+"get:reference");
  back=evidence.get(p+"back:bounds");backReference=evidence.get(p+"back:reference");
 }
 boolean enabled(){return title!=null&&play!=null&&titleReference!=null&&playReference!=null;}
 void install(PhaseEngine engine){
  for(PhaseEngine.Phase phase:engine.phases.values())for(PhaseEngine.Watch watch:phase.watches)
   if(watch.regionId.equals(UsagiPreset.PREFIX+"missions")||watch.regionId.equals(UsagiPreset.PREFIX+"play")||watch.regionId.equals(UsagiPreset.PREFIX+"get"))watch.runtimeControlEvidence=true;
  if(back==null||backReference==null)return;
  // Runtime only: never replace an edited or learned workflow with this fallback.
  PhaseEngine.Phase missions=engine.phases.get(UsagiPreset.PREFIX+"missions");
  if(missions==null||engine.regions.containsKey(BACK))return;
  PhaseEngine.Region region=new PhaseEngine.Region();region.id=BACK;region.name="ミッション一覧の戻る";region.bounds=back.clone();engine.regions.put(BACK,region);
  PhaseEngine.Watch watch=new PhaseEngine.Watch();watch.id=BACK;watch.name="確認済みミッション一覧から戻る";watch.regionId=BACK;
  watch.condition=PhaseEngine.MATCH;watch.action=PhaseEngine.CENTER;watch.nextPhase=UsagiPreset.PREFIX+"verify";watch.waitMs=0;watch.fallback=true;watch.reference=new float[]{1};watch.runtimeControlEvidence=true;
  missions.watches.add(watch);
 }
 static VisualLocator.Match verify(int width,int height,RegionSampler.Pixels pixels,float[] reference,float[] bounds){
  if(reference==null||bounds==null)return null;
  VisualLocator.Match found=VisualLocator.verify(width,height,pixels,reference,null,bounds,.006f);
  return found!=null?found:VisualLocator.nearby(width,height,pixels,reference,null,bounds,.006f);
 }
 static VisualLocator.Match control(int width,int height,RegionSampler.Pixels pixels,float[] reference,float[] bounds,float[] transform){
  if(reference==null||bounds==null)return null;
  // A task card may change label or move down the visible list. Check the
  // label itself in each row; the yellow Open button is not a Play label.
  for(float row:new float[]{0,.168f,.336f}){
   float[] candidate=bounds.clone();candidate[1]+=row;candidate[3]+=row;
   if(candidate[3]>.91f)continue;
   VisualLocator.Match match=verify(width,height,pixels,reference,AdaptiveLayout.map(candidate,transform));
   if(match!=null)return match;
  }
  return null;
 }
 boolean overlayVisible(AdaptiveLayout layout,int width,int height,RegionSampler.Pixels pixels){
  // A modal can leave the mission title and navigation arrow visible behind it.
  // Check registered dialog evidence before admitting any list action.
  for(String key:new String[]{"reward","offline","bonus","dialog","promo"}){
   String id=UsagiPreset.PREFIX+key;PhaseEngine.Watch watch=null;
   for(PhaseEngine.Phase phase:layout.engine.phases.values())for(PhaseEngine.Watch candidate:phase.watches)
    if(watch==null&&candidate.regionId.equals(id)&&candidate.condition==PhaseEngine.MATCH&&candidate.reference!=null)watch=candidate;
   if(watch==null)continue;
   float[][] pair=layout.profile.extra.get(id);float[] box=layout.base.get(id);
   for(float[] t:new float[][]{new float[]{1,1,0,0},layout.transform,new float[]{1,AdaptiveLayout.NORMAL_Y,0,AdaptiveLayout.NORMAL_TOP}}){
    float[] raw=pair!=null?AdaptiveLayout.paired(width,height,pair,t,pixels):box==null?null:AdaptiveLayout.inside(AdaptiveLayout.map(box,t))?RegionSampler.sample(width,height,AdaptiveLayout.map(box,t),pixels):null;
    if(watch.distance(raw)<=watch.threshold)return true;
   }
  }
  return false;
 }
 boolean sample(AdaptiveLayout layout,int width,int height,RegionSampler.Pixels pixels,Map<String,float[]> samples){
  if(!enabled())return false;
  for(float[] transform:new float[][]{new float[]{1,1,0,0},layout.transform,new float[]{1,AdaptiveLayout.NORMAL_Y,0,AdaptiveLayout.NORMAL_TOP}}){
   float[] heading=AdaptiveLayout.map(title,transform);
   if(VisualLocator.verify(width,height,pixels,titleReference,null,heading,.006f)==null)continue;
   if(overlayVisible(layout,width,height,pixels))return false;
   VisualLocator.Match claim=control(width,height,pixels,getReference,get,transform);
   VisualLocator.Match resume=claim==null?control(width,height,pixels,playReference,play,transform):null;
   VisualLocator.Match arrow=verify(width,height,pixels,backReference,back==null?null:AdaptiveLayout.map(back,transform));
   if(claim==null&&resume==null&&arrow==null)continue;
   samples.put(PAGE,new float[]{1});samples.put(UsagiPreset.PREFIX+"missions",new float[]{1});
   put(samples,UsagiPreset.PREFIX+"get",claim);put(samples,UsagiPreset.PREFIX+"play",resume);
   // Do not navigate away while a verified reward or Play control is available.
   put(samples,BACK,claim==null&&resume==null?arrow:null);
   blockHome(samples);layout.status=claim!=null?"デイリーミッションのゲットを確認":resume!=null?"デイリーミッションのプレイを確認":"デイリーミッションの戻るを確認";return true;
  }
  return false;
 }
 static void put(Map<String,float[]> samples,String id,VisualLocator.Match match){
  samples.put(id,new float[]{match==null?0:1});samples.remove(id+AdaptiveLayout.BOUNDS);
  if(match!=null)samples.put(id+AdaptiveLayout.BOUNDS,match.bounds.clone());
 }
 static void blockHome(Map<String,float[]> samples){
  samples.put(UsagiPreset.PREFIX+"game",new float[]{0});samples.remove(UsagiPreset.PREFIX+"game"+AdaptiveLayout.BOUNDS);
  samples.remove(AdaptiveLayout.MAIN);samples.remove(AdaptiveLayout.MAIN+ContentRelocator.LOCATED);
  samples.remove(UsagiPreset.PREFIX+"amount-ready");
 }
}
