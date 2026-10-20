package jp.tapmarker;
import java.util.*;

/** Heading and control must fit the same display transform. Each frame owns its touch bounds. */
final class BoxLayout extends AdaptiveLayout {
 final BoxAdHomeEvidence ads;
 private BoxLayout(PhaseEngine e,VirtualPresetProfile p,Map<String,float[]> refs){super(e,p,false,refs.get("game"),.012f);ads=new BoxAdHomeEvidence(refs);}
 static BoxLayout install(PhaseEngine e,Map<String,float[]> refs){
  PhaseEngine.Watch game=e.rule(BoxPreset.P+"verify-game");if(e.windowRelative||game==null||!Arrays.equals(game.reference,refs.get("game")))return null;
  VirtualPresetProfile p=new VirtualPresetProfile(BoxPreset.P+"game",BoxPreset.GAME[0],BoxPreset.GAME[1],refs.get("game"));BoxLayout layout=new BoxLayout(e,p,refs);
  for(String key:BoxPreset.KEYS){String id=BoxPreset.P+key;PhaseEngine.Region r=e.regions.get(id);if(r==null||!Arrays.equals(r.bounds,BoxPreset.anchors(key)[1]))continue;boolean factory=true;for(PhaseEngine.Phase phase:e.phases.values())for(PhaseEngine.Watch w:phase.watches)if(w.regionId.equals(id)&&w.reference!=null&&!Arrays.equals(w.reference,refs.get(key)))factory=false;if(factory){p.extra.put(id,BoxPreset.anchors(key));layout.base.put(id,r.bounds.clone());layout.originalBounds.put(id,r.bounds.clone());}}
  return layout;
 }
 @Override Map<String,float[]> sample(List<PhaseEngine.Region> regions,int w,int h,RegionSampler.Pixels pixels,long now){
  resetSize(w,h);Map<String,float[]> result=new HashMap<>();
  for(PhaseEngine.Region r:regions)if(!AdCloseAutomaton.handles(r.id)){if(profile.extra.containsKey(r.id))samplePair(r.id,pixels,now,result);else result.put(r.id,RegionSampler.sample(w,h,r.bounds,pixels));}
  String id=BoxPreset.P+"game";float[] t=pairLayouts.get(id);if(result.containsKey(id+BOUNDS)&&t!=null){transform=t.clone();result.put(MAIN,new float[]{(.5f*t[0]+t[2])*w,(BoxPreset.MAIN_Y*t[1]+t[3])*h});}
  boolean known=AdExitPolicy.gameVisible(engine,result);ads.sample(engine,result,w,h,pixels,known);
  for(PhaseEngine.Region r:regions)if(AdCloseAutomaton.handles(r.id)){if(known)result.put(r.id,new float[]{0});else AdCloseAutomaton.sample(r,w,h,pixels,result);}
  return result;
 }
 @Override Map<String,float[]> sampleRelative(List<PhaseEngine.Region> regions,int w,int h,RegionSampler.Pixels pixels,long now,int[] window){Map<String,float[]> result=super.sampleRelative(regions,w,h,pixels,now,window);if(result.containsKey(AdHomeRecovery.EVIDENCE))result.put(AdHomeRecovery.EVIDENCE,new float[]{1,w,h});return result;}
 @Override float[] point(PhaseEngine.Watch watch,Map<String,float[]> samples,int w,int h){if(AdCloseAutomaton.handles(watch.regionId))return samples.get(watch.regionId+AdCloseAutomaton.POINT);if(profile.extra.containsKey(watch.regionId)){float[] b=samples.get(watch.regionId+BOUNDS);return b==null?null:RegionMath.center(b,w,h);}return super.point(watch,samples,w,h);}
}
