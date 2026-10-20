package jp.tapmarker;
import java.util.*;

/** Shared input machinery, independent profile evidence and mission state for this package. */
final class PoipoiLayout extends AdaptiveLayout {
 final PoipoiAdHomeEvidence ads;final boolean dynamicGet;final float[] fullInk,playLabel;
 private PoipoiLayout(PhaseEngine e,VirtualPresetProfile p,Map<String,float[]> refs,boolean dynamic){super(e,p,false,refs.get("game"),.012f);ads=new PoipoiAdHomeEvidence(refs);dynamicGet=dynamic;playLabel=refs.get("mission-play");fullInk=refs.get("full-ink");if(fullInk==null||fullInk.length!=768)throw new IllegalArgumentException("100.00%の文字見本がありません");}
 static PoipoiLayout install(PhaseEngine e,Map<String,float[]> refs){
  PhaseEngine.Watch game=e.rule(PoipoiPreset.P+"verify-game");if(e.windowRelative||game==null||!Arrays.equals(game.reference,refs.get("game")))return null;
  VirtualPresetProfile p=new VirtualPresetProfile(PoipoiPreset.P+"game",PoipoiPreset.GAME[0],PoipoiPreset.GAME[1],refs.get("game"));PhaseEngine.Watch get=e.rule(PoipoiPreset.P+"get-tap");PoipoiLayout layout=new PoipoiLayout(e,p,refs,get!=null&&Arrays.equals(get.reference,refs.get("get"))&&Arrays.equals(e.regions.get(get.regionId).bounds,PoipoiPreset.GET_COLUMN));
  for(String key:PoipoiPreset.KEYS){String id=PoipoiPreset.P+key;PhaseEngine.Region r=e.regions.get(id);if(r==null||!Arrays.equals(r.bounds,PoipoiPreset.anchors(key)[1]))continue;boolean factory=true;for(PhaseEngine.Phase state:e.phases.values())for(PhaseEngine.Watch w:state.watches)if(w.regionId.equals(id)&&w.reference!=null&&!Arrays.equals(w.reference,refs.get(key)))factory=false;if(factory){p.extra.put(id,PoipoiPreset.anchors(key));layout.base.put(id,r.bounds.clone());layout.originalBounds.put(id,r.bounds.clone());}}
  PhaseEngine.Watch timeout=e.rule(PoipoiPreset.P+"missions-timeout");if(timeout!=null){timeout.nextPhase=PoipoiPreset.P+"missions";timeout.name="判別不能の報酬一覧を再取得";}
  PhaseEngine.Watch back=e.rule(PoipoiPreset.P+"mission-back");if(back!=null)back.name="表示中の操作がプレイのみと確認して戻る";
  return layout;
 }
 static boolean notice(int w,int h,float[] t,RegionSampler.Pixels pixels){float[] r=map(PoipoiPreset.NOTICE,t);if(!inside(r))return false;int[] b=RegionMath.bounds(r,w,h);int red=0,white=0;for(int y=b[1];y<b[3];y+=2)for(int x=b[0];x<b[2];x+=2){int c=pixels.get(x,y),a=c>>16&255,g=c>>8&255,v=c&255;if(a>150&&g<125&&v<125&&a-g>45)red++;if(a>215&&g>215&&v>215)white++;}return red>=5&&white>=2;}
 @Override Map<String,float[]> sample(List<PhaseEngine.Region> regions,int w,int h,RegionSampler.Pixels pixels,long now){
  resetSize(w,h);Map<String,float[]> result=new HashMap<>();
  for(PhaseEngine.Region r:regions){String id=r.id;if(AdCloseAutomaton.handles(id)||id.equals(PoipoiPreset.P+"get")&&dynamicGet||id.equals(PoipoiPreset.P+"empty")||id.equals(PoipoiPreset.P+"notice"))continue;if(profile.extra.containsKey(id))samplePair(id,pixels,now,result);else result.put(id,RegionSampler.sample(w,h,r.bounds,pixels));}
  String full=PoipoiPreset.P+"full",game=PoipoiPreset.P+"game";if(profile.extra.containsKey(full)&&result.containsKey(full+BOUNDS)){float[] t=pairLayouts.get(game);if(!result.containsKey(game+BOUNDS)||t==null||PoipoiAdHomeEvidence.distance(fullInk,map(PoipoiPreset.FULL[1],t),w,h,pixels)>.08f){result.put(full,new float[1536]);result.remove(full+BOUNDS);}else{pairLayouts.put(full,t.clone());result.put(full+BOUNDS,map(PoipoiPreset.FULL[1],t));}}
  for(String key:new String[]{"decline","missions","reward","offline","bonus","game","full"}){String id=PoipoiPreset.P+key;if(result.containsKey(id+BOUNDS)&&pairLayouts.containsKey(id)){transform=pairLayouts.get(id).clone();break;}}
  boolean missions=result.containsKey(PoipoiPreset.P+"missions"+BOUNDS);MissionGetDetector.Found found=null;
  if(dynamicGet&&missions)search:for(float[] candidate:new float[][]{transform,new float[]{1,1,0,0}})for(float probe:new float[]{.70f,.76f,.82f,.87f}){found=MissionGetDetector.find(w,h,PoipoiPreset.GET_COLUMN,candidate,pixels,engine.rule(PoipoiPreset.P+"get-tap"),PoipoiPreset.MISSION_TITLE,PoipoiPreset.GET_LABEL,probe);if(found!=null)break search;}
  boolean verifiedEmpty=dynamicGet&&missions&&found==null&&(PoipoiMissionEvidence.noVisibleClaim(w,h,transform,pixels,playLabel)||PoipoiMissionEvidence.noVisibleClaim(w,h,new float[]{1,1,0,0},pixels,playLabel));
  if(missions)result.put(PoipoiMissionEvidence.STATUS,new float[]{found!=null?1:verifiedEmpty?2:0});
  for(PhaseEngine.Region r:regions){if(r.id.equals(PoipoiPreset.P+"get")&&dynamicGet){result.put(r.id,found==null?new float[1536]:found.raw);if(found!=null)result.put(r.id+BOUNDS,found.bounds);}if(r.id.equals(PoipoiPreset.P+"empty")){result.put(r.id,new float[]{verifiedEmpty?1:0});if(verifiedEmpty)result.put(r.id+BOUNDS,result.get(PoipoiPreset.P+"missions"+BOUNDS).clone());}if(r.id.equals(PoipoiPreset.P+"notice"))result.put(r.id,new float[]{result.containsKey(PoipoiPreset.P+"game"+BOUNDS)&&notice(w,h,transform,pixels)?1:0});}
  String home=PoipoiPreset.P+"game";if(result.containsKey(home+BOUNDS)&&pairLayouts.containsKey(home)){float[] t=pairLayouts.get(home);result.put(MAIN,new float[]{(.5f*t[0]+t[2])*w,(PoipoiPreset.MAIN_Y*t[1]+t[3])*h});}
  boolean known=AdExitPolicy.gameVisible(engine,result);ads.sample(engine,result,w,h,pixels,known);
  for(PhaseEngine.Region r:regions)if(AdCloseAutomaton.handles(r.id)){
   float[] point=known?null:AdCloseAutomaton.find(w,h,AdCloseAutomaton.corner(r,w,h,pixels),pixels);result.put(AdCloseAutomaton.CORNER_TOP,new float[]{AdCornerViewport.top(w,h,pixels)/(float)h});result.put(r.id,new float[]{point==null?0:1});if(point!=null)result.put(r.id+AdCloseAutomaton.POINT,point);
   // Single arrows in this recording belong to Open Google Play, not to an ad exit.
   if(!known)for(float[] word:AdCloseWordDetector.find(w,h,AdCloseAutomaton.LEFT.equals(r.id),pixels)){AdCloseAutomaton.append(result,r.id+AdCloseAutomaton.PIXEL_CANDIDATES,word[0],word[1],w,h);result.put(r.id,new float[]{1});if(!result.containsKey(r.id+AdCloseAutomaton.POINT))result.put(r.id+AdCloseAutomaton.POINT,word);}
   if(!known)AdCloseAutomaton.sheet(r.id,w,h,pixels,result);
  }return result;
 }
 @Override Map<String,float[]> sampleRelative(List<PhaseEngine.Region> regions,int w,int h,RegionSampler.Pixels pixels,long now,int[] window){Map<String,float[]> result=super.sampleRelative(regions,w,h,pixels,now,window);if(result.containsKey(AdHomeRecovery.EVIDENCE))result.put(AdHomeRecovery.EVIDENCE,new float[]{1,w,h});return result;}
 @Override float[] point(PhaseEngine.Watch watch,Map<String,float[]> samples,int w,int h){if(AdCloseAutomaton.handles(watch.regionId))return samples.get(watch.regionId+AdCloseAutomaton.POINT);if(profile.extra.containsKey(watch.regionId)||watch.regionId.equals(PoipoiPreset.P+"get")&&dynamicGet||watch.regionId.equals(PoipoiPreset.P+"empty")){float[] b=samples.get(watch.regionId+BOUNDS);return b==null?null:RegionMath.center(b,w,h);}return super.point(watch,samples,w,h);}
}
