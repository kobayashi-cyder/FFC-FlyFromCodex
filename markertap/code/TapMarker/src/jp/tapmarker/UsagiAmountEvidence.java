package jp.tapmarker;
import java.util.*;

/** Amount title and Necessary text belong to the same left-hand upgrade control. */
final class UsagiAmountEvidence {
 static final String AD=":usagi-amount-ad";
 final float[] title,titleReference,necessary,necessaryReference,ad,adReference,level,touch;
 final PhaseEngine.Watch watch;
 UsagiAmountEvidence(PhaseEngine engine,Map<String,float[]> evidence){
  String p="usagi-amount:";
  title=evidence.get(p+"title:bounds");titleReference=evidence.get(p+"title:reference");
  necessary=evidence.get(p+"necessary:bounds");necessaryReference=evidence.get(p+"necessary:reference");
  ad=evidence.get(p+"ad:bounds");adReference=evidence.get(p+"ad:reference");
  level=evidence.get(p+"level:bounds");touch=evidence.get(p+"touch:bounds");
  watch=engine.rule(UsagiPreset.PREFIX+"amount-tap");
  if(enabled())watch.runtimeControlEvidence=true;
 }
 boolean enabled(){return watch!=null&&title!=null&&titleReference!=null&&necessary!=null&&necessaryReference!=null&&level!=null&&touch!=null;}
 void sample(AdaptiveLayout layout,int width,int height,RegionSampler.Pixels pixels,Map<String,float[]> samples){
  if(!enabled())return;
  String id=UsagiPreset.PREFIX+"amount-ready";
  samples.remove(id);samples.remove(id+AdaptiveLayout.BOUNDS);samples.remove(id+ContentRelocator.LOCATED);samples.remove(AD);
  PhaseEngine.Watch home=layout.engine.rule(UsagiPreset.PREFIX+"return");
  if(samples.containsKey(UsagiMissionEvidence.PAGE)||home==null||home.distance(samples.get(home.regionId))>home.threshold)return;
  for(float[] transform:new float[][]{layout.transform,new float[]{1,AdaptiveLayout.NORMAL_Y,0,AdaptiveLayout.NORMAL_TOP},new float[]{1,1,0,0}}){
   float[] heading=AdaptiveLayout.map(title,transform);
   VisualLocator.Match found=VisualLocator.verify(width,height,pixels,titleReference,null,heading,.008f);
   if(found==null)found=VisualLocator.nearby(width,height,pixels,titleReference,null,heading,.008f);
   if(found==null||found.bounds[2]>.46f)continue;
   float[] adjusted=transform.clone();adjusted[2]+=(found.bounds[0]+found.bounds[2]-heading[0]-heading[2])/2;adjusted[3]+=(found.bounds[1]+found.bounds[3]-heading[1]-heading[3])/2;
   float[] currentLevel=AdaptiveLayout.map(level,adjusted);if(!AdaptiveLayout.inside(currentLevel))continue;
   samples.put(UsagiPreset.PREFIX+"amount-level",RegionSampler.sample(width,height,currentLevel,pixels));
   float[] required=AdaptiveLayout.map(necessary,adjusted);
   VisualLocator.Match ready=VisualLocator.verify(width,height,pixels,necessaryReference,null,required,.006f);
   if(ready==null)ready=VisualLocator.nearby(width,height,pixels,necessaryReference,null,required,.006f);
   boolean advertisement=false;
   if(ready==null&&ad!=null&&adReference!=null){float[] advertised=AdaptiveLayout.map(ad,adjusted);ready=VisualLocator.verify(width,height,pixels,adReference,null,advertised,.006f);if(ready==null)ready=VisualLocator.nearby(width,height,pixels,adReference,null,advertised,.006f);advertisement=ready!=null;}
   if(ready==null)continue;
   float[] bounds=AdaptiveLayout.map(touch,adjusted);if(!AdaptiveLayout.inside(bounds))continue;
   samples.put(id,new float[]{1});samples.put(id+AdaptiveLayout.BOUNDS,bounds);samples.put(id+ContentRelocator.LOCATED,new float[]{1});if(advertisement)samples.put(AD,new float[]{1});return;
  }
 }
}
