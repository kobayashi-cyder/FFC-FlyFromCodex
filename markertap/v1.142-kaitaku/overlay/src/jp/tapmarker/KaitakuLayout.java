package jp.tapmarker;

import java.util.*;

/** Uses the same physical snapshot, window fencing and input controller as other games. */
final class KaitakuLayout extends AdaptiveLayout {
 final KaitakuEvidence evidence;
 KaitakuLayout(PhaseEngine e,Map<String,float[]> refs){super(e,new VirtualPresetProfile(KaitakuPreset.HOME,KaitakuEvidence.LEFT,KaitakuEvidence.RIGHT,refs.get("home-left")),false,refs.get("home-left"),KaitakuEvidence.HOME_TOLERANCE);evidence=new KaitakuEvidence(refs);relocator.enabled=true;}
 static AdaptiveLayout choose(PhaseEngine e,String app,Map<String,float[]> refs){
  if(!KaitakuPreset.APP.equals(app)||e.windowRelative||!KaitakuPreset.owns(e))return null;
  PhaseEngine defaults=new PhaseEngine();KaitakuPreset.install(defaults);
  return AutomatonGraph.sameConfiguration(e,defaults)?new KaitakuLayout(e,refs):null;
 }
 @Override void configureTargets(String app,Map<String,float[]> refs){relocator.enabled=true;playableAds=new PlayableAdEvidence(refs);}
 @Override Map<String,float[]> sample(List<PhaseEngine.Region> regions,int w,int h,RegionSampler.Pixels p,long now){return collect(regions,w,h,p,now,null);}
 Map<String,float[]> collect(List<PhaseEngine.Region> regions,int w,int h,RegionSampler.Pixels p,long now,float[] nativeTransform){
  Map<String,float[]> out=new HashMap<>();KaitakuEvidence.Result r=evidence.read(w,h,p,nativeTransform);
  out.put(KaitakuPreset.HOME,new float[]{r.home?1:0});out.put(KaitakuPreset.CONTINUE,new float[]{r.proceed?1:0});
  if(r.home){float[] b=KaitakuEvidence.map(new float[]{.49f,.73f,.51f,.75f},r.transform);out.put(MAIN,RegionMath.center(b,w,h));}
  if(r.proceed)out.put(KaitakuPreset.CONTINUE+BOUNDS,KaitakuEvidence.map(KaitakuEvidence.CONTROL,r.transform));
  for(PhaseEngine.Region region:regions)if(AdCloseAutomaton.handles(region.id)){if(r.home||r.proceed)out.put(region.id,new float[]{0});else AdCloseAutomaton.sample(region,w,h,p,out);}
  status=r.home?"開拓するを左右の目印で確認":r.proceed?"このままはじめるを確認":"開拓うさぎの画面を再判別中";return out;
 }
 @Override Map<String,float[]> sampleRelative(List<PhaseEngine.Region> regions,int w,int h,RegionSampler.Pixels p,long now){return sampleRelative(regions,w,h,p,now,null);}
 @Override Map<String,float[]> sampleRelative(List<PhaseEngine.Region> regions,int w,int h,RegionSampler.Pixels p,long now,int[] window){
  ContentViewport v=ContentViewport.locate(w,h,p,window);RegionSampler.Pixels local=(x,y)->p.get(x+v.left,y+v.top);
  float[] nativeTransform={w/(float)v.width,h/(float)v.height,-v.left/(float)v.width,-v.top/(float)v.height};
  Map<String,float[]> out=collect(regions,v.width,v.height,local,now,nativeTransform);out.put(VIEWPORT,v.metadata());
  if(window!=null)out.put(WINDOW,new float[]{window[0],window[1],window[2],window[3]});
  if(playableAds!=null)playableAds.sample(engine,out,v.width,v.height,local,w,h);return out;
 }
 @Override float[] point(PhaseEngine.Watch watch,Map<String,float[]> samples,int w,int h){if(AdCloseAutomaton.handles(watch.regionId))return samples.get(watch.regionId+AdCloseAutomaton.POINT);float[] b=samples.get(watch.regionId+BOUNDS);return b==null?null:RegionMath.center(b,w,h);}
}
