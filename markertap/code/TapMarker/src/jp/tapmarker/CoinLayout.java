package jp.tapmarker;
import java.util.*;

/** Coin scene templates use their own coordinate system; old game layouts remain separate. */
final class CoinLayout extends AdaptiveLayout {
 boolean wide=true;long searchCursor;int lastWideSearches;
 final List<float[]> cancelReferences;final float[] skip;final float[][] readyNext;final CoinControlEvidence controls;AdHomeEvidence homeAds;boolean getDefault;final Map<String,Long> scaleSearched=new HashMap<>();
 CoinLayout(PhaseEngine e,VirtualPresetProfile p,Map<String,float[]> canonical){super(e,p,false,canonical.get("home"),.015f);cancelReferences=CoinNoAdPolicy.references(canonical);skip=canonical.get("ad-skip").clone();controls=new CoinControlEvidence(canonical);List<float[]> next=new ArrayList<>();for(String key:new TreeSet<>(canonical.keySet()))if(key.startsWith("coin-ad-next-")){float[] ref=canonical.get(key);if(ref.length!=768)throw new IllegalArgumentException("コイン落としの広告の次へ見本");next.add(ref);}readyNext=next.toArray(new float[next.size()][]);}
 static CoinLayout install(PhaseEngine e,Map<String,float[]> refs){
  PhaseEngine.Watch home=e.rule(CoinPreset.P+"verify-home");PhaseEngine.Region homeRegion=e.regions.get(CoinPreset.P+"home");
  if(home==null||homeRegion==null||!Arrays.equals(home.reference,refs.get("home"))||!Arrays.equals(homeRegion.bounds,CoinPreset.HEADER))return null;
  VirtualPresetProfile profile=new VirtualPresetProfile(homeRegion.id,CoinPreset.HEADER,CoinPreset.BUTTON,refs.get("home"));
  CoinLayout layout=new CoinLayout(e,profile,refs);layout.homeAds=new AdHomeEvidence(refs);PhaseEngine defaults=new PhaseEngine();CoinPreset.install(defaults,refs);
  for(String key:new String[]{"home","fever","bonus","clear","missions","play","reward","boost-rare","boost-pocket","boost-tap2","boost-pot"}){
   String id=CoinPreset.P+key;PhaseEngine.Region r=e.regions.get(id),d=defaults.regions.get(id);
   if(r==null||d==null||!Arrays.equals(r.bounds,d.bounds))continue;
   boolean original=false;for(PhaseEngine.Phase p:e.phases.values())for(PhaseEngine.Watch w:p.watches)if(id.equals(w.regionId)&&Arrays.equals(w.reference,refs.get(key)))original=true;
   if(!original)continue;
   float[][] anchors=anchors(key);float[] heading=anchors[0],button=anchors[1];
   profile.extra.put(id,new float[][]{heading.clone(),button.clone()});layout.base.put(id,r.bounds.clone());layout.originalBounds.put(id,r.bounds.clone());
   for(PhaseEngine.Phase p:e.phases.values())for(PhaseEngine.Watch w:p.watches)if(id.equals(w.regionId)&&Arrays.equals(w.reference,refs.get(key))){w.pairedReference=true;if(refs.containsKey(key+"-gold"))w.runtimePairedReferences=new float[][]{refs.get(key+"-gold").clone()};}
   if(layout.controls.controls.containsKey(id))for(PhaseEngine.Phase p:e.phases.values())for(PhaseEngine.Watch w:p.watches){PhaseEngine.Watch originalRule=defaults.rule(w.id);if(id.equals(w.regionId)&&Arrays.equals(w.reference,refs.get(key))&&originalRule!=null&&w.threshold==originalRule.threshold&&w.condition==originalRule.condition&&w.action==originalRule.action&&w.nextPhase.equals(originalRule.nextPhase))w.runtimeControlEvidence=true;}
  }
  PhaseEngine.Region get=e.regions.get(CoinPreset.P+"get");PhaseEngine.Watch getRule=e.rule(CoinPreset.P+"get-tap");
  layout.getDefault=get!=null&&Arrays.equals(get.bounds,CoinPreset.GET_COLUMN)&&getRule!=null&&Arrays.equals(getRule.reference,refs.get("get"));
  if(layout.getDefault){layout.base.put(get.id,get.bounds.clone());layout.originalBounds.put(get.id,get.bounds.clone());}
  PhaseEngine.Watch defaultGet=defaults.rule(CoinPreset.P+"get-tap");
  if(layout.getDefault&&getRule.threshold==defaultGet.threshold&&getRule.condition==defaultGet.condition&&getRule.action==defaultGet.action&&getRule.nextPhase.equals(defaultGet.nextPhase))getRule.runtimeControlEvidence=true;
  Set<String> controlsAllowed=new HashSet<>(profile.extra.keySet());if(layout.getDefault)controlsAllowed.add(get.id);layout.controls.controls.keySet().retainAll(controlsAllowed);layout.controls.identities.keySet().retainAll(controlsAllowed);
  PhaseEngine.Region notice=e.regions.get(CoinPreset.P+"mission-notice");PhaseEngine.Watch noticeRule=e.rule(CoinPreset.P+"mission-notice");
  layout.noticeDefault=notice!=null&&Arrays.equals(notice.bounds,CoinPreset.NOTICE)&&noticeRule!=null&&Arrays.equals(noticeRule.reference,new float[]{1});
  return layout;
 }
 static float[][] anchors(String key){
  switch(key){case "home":return new float[][]{CoinPreset.HEADER,CoinPreset.BUTTON};case "fever":return new float[][]{CoinPreset.FEVER,CoinPreset.BUTTON};case "bonus":return new float[][]{CoinPreset.BONUS,CoinPreset.START};case "clear":return new float[][]{CoinPreset.CLEAR_TOP,CoinPreset.CLEAR_LOGO};case "missions":return new float[][]{CoinPreset.MISSION_TITLE,CoinPreset.MISSION_BACK};case "play":return new float[][]{CoinPreset.MISSION_TITLE,CoinPreset.GET_LABEL};case "reward":return new float[][]{CoinPreset.REWARD_TITLE,CoinPreset.CLOSE};case "boost-rare":return new float[][]{CoinPreset.RARE_TITLE,CoinPreset.RARE_CM};case "boost-pocket":return new float[][]{CoinPreset.POCKET_TITLE,CoinPreset.POCKET_CM};case "boost-tap2":return new float[][]{CoinPreset.TAP2_BODY,CoinPreset.TAP2_CM};case "boost-pot":return new float[][]{CoinPreset.POT_BODY,CoinPreset.POT_CANCEL_LABEL};default:throw new IllegalArgumentException(key);}
 }
 void updateTransform(String id,Map<String,float[]> samples){if(samples.containsKey(id+BOUNDS)){float[] t=pairLayouts.get(id);if(t!=null){transform=t.clone();located=true;status="この画面の目印と操作位置を確認";}}}
 @Override void samplePair(String id,RegionSampler.Pixels pixels,long now,Map<String,float[]> result){
  broadSearch=wide;
  List<PhaseEngine.Watch> active=references(id);boolean originalControl=!active.isEmpty();for(PhaseEngine.Watch w:active)if(!w.runtimeControlEvidence)originalControl=false;
  if(relativeEnabled&&originalControl&&controls.controls.containsKey(id)){float[] t=controls.identify(id,width,height,pixels,now,transform,result,wide);if(t==null)t=controls.locate(id,width,height,pixels,now,transform,result,wide);if(t!=null){pairLayouts.put(id,t.clone());return;}}
  super.samplePair(id,pixels,now,result);
  if(!wide||result.containsKey(id+BOUNDS)||now-scaleSearched.getOrDefault(id,-SEARCH_MS)<SEARCH_MS)return;
  List<PhaseEngine.Watch> refs=references(id);if(refs.isEmpty())return;scaleSearched.put(id,now);float[][] anchors=profile.extra.get(id);float best=score(result.get(id),refs);float[] chosen=transform.clone(),raw=result.get(id);
  // The recorded dialogs grow/shrink about their centre during presentation.
  // Probe a few content scales; both the heading and button must agree.
  if(id.equals(CoinPreset.P+"boost-rare")||id.equals(CoinPreset.P+"boost-pocket")||id.equals(CoinPreset.P+"boost-tap2")||id.equals(CoinPreset.P+"reward")||id.equals(CoinPreset.P+"bonus"))for(float scale:new float[]{.84f,.88f,.90f,.92f,.96f})for(float stretch:new float[]{-.02f,0,.02f})for(float dy:new float[]{-.005f,0,.005f}){float sy=scale+stretch;float[] t={scale*transform[0],sy*transform[1],transform[2]+(1-scale)*transform[0]/2,transform[3]+(1-sy)*transform[1]/2+dy};float[] candidate=paired(width,height,anchors,t,pixels);float s=score(candidate,refs);if(s<best){best=s;chosen=t;raw=candidate;}}
  for(float side:new float[]{.015f,.03f})for(float sy:new float[]{.84f,.86f,.90f,.94f,1})for(float top:new float[]{.04f,.06f,.08f,.12f}){float[] t={1-2*side,sy,side,top};float[] candidate=paired(width,height,anchors,t,pixels);float s=score(candidate,refs);if(s<best){best=s;chosen=t;raw=candidate;}}
  if(id.equals(CoinPreset.P+"clear"))for(float dy:new float[]{-.08f,-.04f,.04f,.06f,.08f,.10f,.12f}){float[] t=transform.clone();t[3]+=dy;float[] candidate=paired(width,height,anchors,t,pixels);float s=score(candidate,refs);if(s<best){best=s;chosen=t;raw=candidate;}}
  float[] steps={.02f,.02f,.004f,.004f};for(int pass=0;pass<5;pass++)for(int axis=0;axis<4;axis++)for(int sign:new int[]{-1,1}){float[] t=chosen.clone();t[axis]+=sign*steps[axis]/(1<<pass);float[] candidate=paired(width,height,anchors,t,pixels);float s=score(candidate,refs);if(s<best){best=s;chosen=t;raw=candidate;}}
  if(best<=1){pairLayouts.put(id,chosen);result.put(id,raw);result.put(id+BOUNDS,map(anchors[1],chosen));}
 }
 @Override Map<String,float[]> sample(List<PhaseEngine.Region> regions,int w,int h,RegionSampler.Pixels pixels,long now){
  if(width!=w||height!=h){scaleSearched.clear();searchCursor=0;}resetSize(w,h);Map<String,float[]> result=new HashMap<>();
  boolean progressive=relativeEnabled&&relocator.enabled;wide=!progressive;lastWideSearches=0;
  // Locate the mission heading before looking for individual visible Get controls.
  for(PhaseEngine.Region r:regions)if(r.id.equals(CoinPreset.P+"missions")&&profile.extra.containsKey(r.id)){samplePair(r.id,pixels,now,result);updateTransform(r.id,result);}
  for(PhaseEngine.Region r:regions){if(AdCloseAutomaton.handles(r.id)||result.containsKey(r.id)||getDefault&&r.id.equals(CoinPreset.P+"get")||noticeDefault&&r.id.equals(CoinPreset.P+"mission-notice"))continue;if(profile.extra.containsKey(r.id))samplePair(r.id,pixels,now,result);else result.put(r.id,RegionSampler.sample(w,h,r.bounds,pixels));}
  // Verify all current/cached positions before rotating expensive miss searches.
  if(progressive){
   List<String> missing=new ArrayList<>();
   for(PhaseEngine.Region r:regions)if(profile.extra.containsKey(r.id)&&!result.containsKey(r.id+BOUNDS)&&!references(r.id).isEmpty()&&!missing.contains(r.id))missing.add(r.id);
   wide=true;
   if(!missing.isEmpty()){int first=(int)(searchCursor%missing.size()),count=Math.min(2,missing.size());for(int i=0;i<count;i++){String id=missing.get((first+i)%missing.size());samplePair(id,pixels,now,result);lastWideSearches++;}searchCursor+=count;}
   wide=false;
  }
  Set<String> factoryBoosts=new HashSet<>();for(String key:CoinNoAdPolicy.BOOSTS){String id=CoinPreset.P+key;List<PhaseEngine.Watch> refs=references(id);boolean factory=!refs.isEmpty();for(PhaseEngine.Watch watch:refs)if(!watch.runtimeControlEvidence)factory=false;if(factory)factoryBoosts.add(id);}controls.disambiguate(result,factoryBoosts);
  for(String key:new String[]{"reward","boost-rare","boost-pocket","boost-tap2","boost-pot","bonus","missions","fever","home"}){String id=CoinPreset.P+key;if(result.containsKey(id+BOUNDS)){updateTransform(id,result);break;}}
  for(PhaseEngine.Region r:regions)if(getDefault&&r.id.equals(CoinPreset.P+"get")){PhaseEngine.Watch watch=engine.rule(CoinPreset.P+"get-tap");MissionGetDetector.Found found=MissionGetDetector.find(w,h,r.bounds,transform,pixels,watch);result.put(r.id,found==null?new float[1536]:found.raw);if(found!=null)result.put(r.id+BOUNDS,found.bounds);else if(relativeEnabled&&watch.runtimeControlEvidence)controls.locate(r.id,w,h,pixels,now,transform,result);}
  for(PhaseEngine.Region r:regions)if(noticeDefault&&r.id.equals(CoinPreset.P+"mission-notice"))result.put(r.id,new float[]{result.containsKey(CoinPreset.P+"home"+BOUNDS)&&notice(w,h,transform,pixels)?1:0});
  if(result.containsKey(CoinPreset.P+"home"+BOUNDS)||result.containsKey(CoinPreset.P+"fever"+BOUNDS)){float[] p=map(new float[]{.5f,CoinPreset.MAIN_Y,.5f,CoinPreset.MAIN_Y},transform);result.put(MAIN,new float[]{p[0]*w,p[1]*h});}
  CoinNoAdPolicy.sample(engine,result,w,h,pixels,pairLayouts,cancelReferences);
  boolean known=RecoveryRequest.visibleBranch(engine,result);
  homeAds.sample(engine,result,w,h,pixels,known);
  for(PhaseEngine.Region r:regions)if(AdCloseAutomaton.handles(r.id)){
   float[] corner=AdCloseAutomaton.corner(r,w,h,pixels);float[] p=known?null:AdCloseAutomaton.find(w,h,corner,pixels);
   if(!known&&p==null)p=AdSkipDetector.find(w,h,corner,pixels,skip);
   if(!known&&p==null)p=AdReadyNextDetector.find(w,h,corner,pixels,readyNext);
   result.put(AdCloseAutomaton.CORNER_TOP,new float[]{AdCornerViewport.top(w,h,pixels)/(float)h});result.put(r.id,new float[]{p==null?0:1});if(p!=null)result.put(r.id+AdCloseAutomaton.POINT,p);if(!known)AdCloseAutomaton.word(r.id,w,h,pixels,result);
  }
  return result;
 }
 static boolean notice(int width,int height,float[] t,RegionSampler.Pixels pixels){float[] roi=map(CoinPreset.NOTICE,t);if(!inside(roi))return false;int[] b=RegionMath.bounds(roi,width,height);int red=0,white=0,dark=0;for(int y=b[1];y<b[3];y+=Math.max(1,(b[3]-b[1])/30))for(int x=b[0];x<b[2];x+=Math.max(1,(b[2]-b[0])/30)){int c=pixels.get(x,y),r=c>>16&255,g=c>>8&255,blue=c&255;if(r>180&&g<130&&blue<110&&r-g>70)red++;if(r>210&&g>210&&blue>190)white++;if(r<85&&g<85&&blue<85)dark++;}return red>=8&&white>=3&&dark>=3;}
 @Override float[] point(PhaseEngine.Watch w,Map<String,float[]> samples,int width,int height){
  if(w.regionId.equals(CoinNoAdPolicy.CANCEL))return CoinNoAdPolicy.point(samples,width,height);
  if(CoinNoAdPolicy.adWatch(w))return null;
  if(AdCloseAutomaton.handles(w.regionId))return samples.get(w.regionId+AdCloseAutomaton.POINT);
  if(w.id.equals(CoinPreset.P+"mission-button-tap")&&profile.extra.containsKey(w.regionId)){if(!samples.containsKey(w.regionId+BOUNDS))return null;float[] t=pairLayouts.get(w.regionId);if(t==null)return null;return new float[]{(.43f*t[0]+t[2])*width,(.098f*t[1]+t[3])*height};}
  if(profile.handles(w.regionId)||getDefault&&w.regionId.equals(CoinPreset.P+"get")){float[] bounds=samples.get(w.regionId+BOUNDS);return bounds==null?null:RegionMath.center(bounds,width,height);}
  PhaseEngine.Region r=engine.regions.get(w.regionId);return r==null?null:RegionMath.center(r.bounds,width,height);
 }
 @Override float[] defaultMarker(float x,float y,int w,int h,Map<String,float[]> samples){if(Math.abs(x/w-.5f)>.035f||Math.abs(y/h-CoinPreset.MAIN_Y)>.035f)return new float[]{x,y};float[] p=samples.get(MAIN);return p==null?new float[]{x,y}:p.clone();}
}
