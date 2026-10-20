package jp.tapmarker;
import java.util.*;
/** Runtime-only sparse evidence for default virtual-window layouts. */
final class VirtualPresetProfile {
 final String region;final float[] header,button,reference;
 final Map<String,float[][]> extra=new HashMap<>();
 VirtualPresetProfile(String region,float[] header,float[] button,float[] reference){this.region=region;this.header=header;this.button=button;this.reference=reference.clone();}
 static VirtualPresetProfile install(PhaseEngine engine,String app,Map<String,float[]> evidence){
  boolean usagi=UsagiPreset.APP.equals(app),farm=FarmPreset.APP.equals(app);if(!usagi&&!farm)return null;
  String id=usagi?UsagiPreset.PREFIX+"game":FarmPreset.P+"harvest";
  PhaseEngine.Region primary=engine.regions.get(id);float[] expected=usagi?new float[]{.66f,.082f,.77f,.112f}:new float[]{.75f,.077f,.805f,.092f};
  if(primary==null||!Arrays.equals(primary.bounds,expected))return null;
  float[] header=usagi?new float[]{.66f,.0096f,.77f,.0396f}:expected.clone();
  float[] button=usagi?new float[]{.38f,.758f,.64f,.792f}:new float[]{.60f,.765f,.64f,.789f};
  float[] reference=evidence.get(usagi?"usagi-home":"farm-home");if(reference==null||reference.length!=1536)throw new IllegalArgumentException("仮想画面の主画面見本がありません");
  VirtualPresetProfile profile=new VirtualPresetProfile(id,header,button,reference);primary.bounds=header.clone();
  for(PhaseEngine.Phase phase:engine.phases.values())for(PhaseEngine.Watch w:phase.watches)if(id.equals(w.regionId)&&(w.condition==PhaseEngine.MATCH||w.condition==PhaseEngine.DISAPPEAR)){w.reference=reference.clone();w.pairedReference=true;if(w.threshold==.004f)w.threshold=.02f;}
  if(usagi){
   replace(engine,UsagiPreset.PREFIX+"mission-touch",new float[]{.365f,.09f,.395f,.11f},new float[]{.365f,.014f,.395f,.038f});
   replace(engine,UsagiPreset.PREFIX+"clear",new float[]{.49f,.159f,.55f,.184f},new float[]{.49f,.092f,.55f,.117f});
   // Each dialog has its own heading and control. They can locate a cold-start
   // mission/dialog without first finding the game's main screen.
   pair(engine,profile,evidence,"missions",new float[]{.28f,.164f,.73f,.19f},new float[]{.29f,.167f,.715f,.188f},new float[]{.08f,.549f,.455f,.574f});
   pair(engine,profile,evidence,"get",new float[]{.70f,.638f,.84f,.666f},new float[]{.29f,.167f,.715f,.188f},new float[]{.72f,.643f,.865f,.666f});
   pair(engine,profile,evidence,"play",new float[]{.70f,.638f,.84f,.666f},new float[]{.29f,.167f,.715f,.188f},new float[]{.72f,.643f,.865f,.666f});
   pair(engine,profile,evidence,"reward",new float[]{.18f,.34f,.82f,.368f},new float[]{.605f,.342f,.819f,.366f},new float[]{.42f,.553f,.575f,.574f});
   pair(engine,profile,evidence,"close",new float[]{.36f,.548f,.62f,.575f},new float[]{.605f,.342f,.819f,.366f},new float[]{.42f,.553f,.575f,.574f});
   pair(engine,profile,evidence,"bonus",new float[]{.32f,.62f,.68f,.645f},new float[]{.605f,.276f,.814f,.298f},new float[]{.32f,.620f,.68f,.640f});
   pair(engine,profile,evidence,"offline",new float[]{.30f,.24f,.70f,.312f},new float[]{.30f,.243f,.70f,.308f},new float[]{.45f,.650f,.555f,.675f});
   pair(engine,profile,evidence,"claim",new float[]{.43f,.65f,.57f,.677f},new float[]{.30f,.243f,.70f,.308f},new float[]{.45f,.650f,.555f,.675f});
   pair(engine,profile,evidence,"promo",UsagiPreset.PROMO_TITLE,UsagiPreset.PROMO_TITLE,UsagiPreset.PROMO_CLOSE);
   pair(engine,profile,evidence,"promo-close",UsagiPreset.PROMO_CLOSE,UsagiPreset.PROMO_TITLE,UsagiPreset.PROMO_CLOSE);
  }else{
   PhaseEngine.Region close=engine.regions.get(FarmPreset.P+"close");float[] reward=evidence.get("farm-reward");
   if(close!=null&&Arrays.equals(close.bounds,new float[]{.40f,.648f,.60f,.676f})&&reward!=null&&reward.length==1536){
    float[] label=new float[]{.405f,.568f,.595f,.603f};close.bounds=label.clone();profile.extra.put(close.id,new float[][]{new float[]{.29f,.338f,.75f,.377f},label});
    for(PhaseEngine.Phase phase:engine.phases.values())for(PhaseEngine.Watch w:phase.watches)if(close.id.equals(w.regionId)&&(w.condition==PhaseEngine.MATCH||w.condition==PhaseEngine.DISAPPEAR)){w.reference=reward.clone();w.pairedReference=true;if(w.threshold==.004f)w.threshold=.02f;}
   }
  }
  return profile;
 }
 static void pair(PhaseEngine engine,VirtualPresetProfile profile,Map<String,float[]> evidence,String key,float[] expected,float[] heading,float[] button){
  String id=UsagiPreset.PREFIX+key;PhaseEngine.Region r=engine.regions.get(id);float[] reference=evidence.get("usagi-"+key);
  if(r==null||!Arrays.equals(r.bounds,expected)||reference==null||reference.length!=1536)return;
  float[] label=AdaptiveLayout.toVirtual(button);r.bounds=label.clone();profile.extra.put(id,new float[][]{AdaptiveLayout.toVirtual(heading),label});
  for(PhaseEngine.Phase phase:engine.phases.values())for(PhaseEngine.Watch w:phase.watches)if(id.equals(w.regionId)&&(w.condition==PhaseEngine.MATCH||w.condition==PhaseEngine.DISAPPEAR)){w.reference=reference.clone();w.pairedReference=true;if(w.threshold==.004f)w.threshold=.012f;}
 }
 static void replace(PhaseEngine engine,String id,float[] expected,float[] updated){PhaseEngine.Region r=engine.regions.get(id);if(r!=null&&Arrays.equals(r.bounds,expected))r.bounds=updated;}
 float[] sample(int width,int height,RegionSampler.Pixels pixels){float[] first=RegionSampler.sample(width,height,header,pixels),second=RegionSampler.sample(width,height,button,pixels),combined=new float[first.length+second.length];System.arraycopy(first,0,combined,0,first.length);System.arraycopy(second,0,combined,first.length,second.length);return combined;}
 boolean handles(String id){return region.equals(id)||extra.containsKey(id);}
 float[] sample(String id,int width,int height,RegionSampler.Pixels pixels){if(region.equals(id))return sample(width,height,pixels);float[][] regions=extra.get(id);if(regions==null)throw new IllegalArgumentException("仮想領域の見本がありません");float[] a=RegionSampler.sample(width,height,regions[0],pixels),b=RegionSampler.sample(width,height,regions[1],pixels),result=new float[a.length+b.length];System.arraycopy(a,0,result,0,a.length);System.arraycopy(b,0,result,a.length,b.length);return result;}
}
