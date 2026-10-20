package jp.tapmarker;
import java.util.*;
/** Window-local, sparse matching. Coordinates belong to the sampled frame, never a preview. */
class AdaptiveLayout {
 final ContentRelocator relocator=new ContentRelocator();
 GamePopupEvidence popupEvidence;
 void configureTargets(String app,Map<String,float[]> refs){GamePopupEvidence.install(engine,app);if(GamePopupEvidence.supported(app))popupEvidence=new GamePopupEvidence(app,engine,refs);relocator.configure(app,refs);playableAds=new PlayableAdEvidence(refs);if(relocator.enabled&&FarmPreset.APP.equals(app))for(PhaseEngine.Phase phase:engine.phases.values())for(PhaseEngine.Watch watch:phase.watches)if(watch.regionId.equals(FarmPreset.P+"harvest"))watch.runtimeControlEvidence=true;}
 static final String BOUNDS=":layout-bounds",MAIN=":layout-main",VIEWPORT=":layout-viewport",WINDOW=":layout-window";
 static final long SEARCH_MS=1000;
 final PhaseEngine engine;final VirtualPresetProfile profile;final boolean usagi;
 final Map<String,float[]> base=new HashMap<>(),local=new HashMap<>(),pairLayouts=new HashMap<>(),originalBounds=new HashMap<>();
 final Map<String,Long> searched=new HashMap<>();
 final float[] canonicalHome;final float canonicalTolerance;float[] homeHeader,homeButton,homeReference,farmStableHome;float[][] homeVariants;float homeTolerance=.02f;
 float[] transform={1,1,0,0},viewportHomeCandidate;float[] stageZeroReference;float[][] stageZeroReferences;int width,height;long lastHomeSearch=-SEARCH_MS;
 FarmDialogEvidence farmDialogs;UsagiMissionEvidence usagiMissions;UsagiAmountEvidence usagiAmount;UsagiCancelEvidence usagiCancel;PlayableAdEvidence playableAds;boolean located,noticeDefault,relativeEnabled=true,broadSearch=true;boolean forceHomeProbe;long lastPopupHomeProbe=-2000;String status="表示位置を確認中";
 // Virtual-to-original screen transform inferred from the two known Usagi anchors.
 static final float NORMAL_Y=(.78f-.097f)/(.775f-.0246f),NORMAL_TOP=.097f-.0246f*NORMAL_Y;
 AdaptiveLayout(PhaseEngine engine,VirtualPresetProfile profile,boolean usagi,float[] home,float tolerance){this.engine=engine;this.profile=profile;this.usagi=usagi;canonicalHome=home;canonicalTolerance=tolerance;homeHeader=profile.header;homeButton=profile.button;homeReference=profile.reference;}
 static AdaptiveLayout install(PhaseEngine e,String app,Map<String,float[]> canonical,Map<String,float[]> evidence){
  if(PoipoiPreset.APP.equals(app))return PoipoiLayout.install(e,canonical);
  if(BoxPreset.APP.equals(app))return BoxLayout.install(e,canonical);
  if(CoinPreset.APP.equals(app))return CoinLayout.install(e,canonical);
  if(ShoppingPreset.APP.equals(app))return ShoppingLayout.install(e,canonical);
  boolean usagi=UsagiPreset.APP.equals(app),farm=FarmPreset.APP.equals(app);if(!usagi&&!farm)return null;
  String prefix=usagi?UsagiPreset.PREFIX:FarmPreset.P,homeId=prefix+(usagi?"game":"harvest");
  PhaseEngine.Watch home=e.rule(prefix+(usagi?"return":"verify-home"));float[] original=canonical.get(usagi?"game":"harvest");
  if(home==null||original==null||!Arrays.equals(home.reference,original))return null;
  PhaseEngine defaults=new PhaseEngine();if(usagi)UsagiPreset.install(defaults,canonical);else FarmPreset.install(defaults,canonical);
  Set<String> eligible=new HashSet<>();for(PhaseEngine.Region r:e.regions.values()){PhaseEngine.Region d=defaults.regions.get(r.id);if(d==null||!Arrays.equals(r.bounds,d.bounds))continue;boolean originalReference=r.id.equals(UsagiPreset.PREFIX+"mission-touch");for(PhaseEngine.Phase phase:e.phases.values())for(PhaseEngine.Watch w:phase.watches)if(r.id.equals(w.regionId)&&w.reference!=null&&Arrays.equals(w.reference,canonical.get(r.id.substring(prefix.length()))))originalReference=true;if(originalReference)eligible.add(r.id);}
  PhaseEngine originalEngine=e.runtimeCopy();float tolerance=home.threshold;VirtualPresetProfile p=VirtualPresetProfile.install(e,app,evidence);if(p==null)return null;
  AdaptiveLayout layout=new AdaptiveLayout(e,p,usagi,original.clone(),tolerance);if(farm&&evidence.get("farm-layout-anchor")!=null){float[] anchor=evidence.get("farm-layout-anchor");if(anchor.length!=768)throw new IllegalArgumentException("農園の輪郭見本");layout.homeTolerance=.03f;layout.homeButton=new float[]{.42f,.657f,.58f,.707f};layout.homeReference=p.reference.clone();System.arraycopy(anchor,0,layout.homeReference,768,768);}
  if(farm&&evidence.get("farm-layout-home")!=null){float[] variant=evidence.get("farm-layout-home");if(variant.length!=1536)throw new IllegalArgumentException("農園の主画面見本");layout.homeVariants=new float[][]{variant.clone()};}
  if(farm&&evidence.get("farm-stable-home")!=null){float[] variant=evidence.get("farm-stable-home");if(variant.length!=1536)throw new IllegalArgumentException("農園の固定文字見本");layout.farmStableHome=variant.clone();List<float[]> variants=new ArrayList<>();if(layout.homeVariants!=null)variants.addAll(Arrays.asList(layout.homeVariants));variants.add(layout.farmStableHome);float[] pressed=evidence.get("farm-stable-pressed");if(pressed!=null){if(pressed.length!=1536)throw new IllegalArgumentException("農園の押下文字見本");variants.add(pressed.clone());}layout.homeVariants=variants.toArray(new float[variants.size()][]);}
  if(usagi){List<float[]> variants=new ArrayList<>();for(String key:new TreeSet<>(evidence.keySet()))if(key.startsWith("usagi-portable-")){float[] variant=evidence.get(key);if(variant.length!=1536)throw new IllegalArgumentException("増えうさぎの主画面見本");variants.add(variant.clone());}if(!variants.isEmpty())layout.homeVariants=variants.toArray(new float[variants.size()][]);}
  for(String id:new ArrayList<>(p.extra.keySet()))if(!eligible.contains(id)){p.extra.remove(id);e.regions.get(id).bounds=originalEngine.regions.get(id).bounds.clone();for(PhaseEngine.Phase phase:e.phases.values())for(PhaseEngine.Watch w:phase.watches)if(id.equals(w.regionId)){PhaseEngine.Watch old=originalEngine.rule(w.id);w.reference=old.reference;w.pairedReference=old.pairedReference;w.threshold=old.threshold;}}
  for(PhaseEngine.Region r:e.regions.values())if(eligible.contains(r.id)){
   float[] b=r.bounds.clone();if(usagi&&Arrays.equals(b,defaults.regions.get(r.id).bounds)&&!r.id.equals(homeId))b=toVirtual(b);
   layout.base.put(r.id,b);layout.originalBounds.put(r.id,defaults.regions.get(r.id).bounds.clone());
  }
  for(PhaseEngine.Phase phase:e.phases.values())for(PhaseEngine.Watch w:phase.watches){
   if(homeId.equals(w.regionId)){PhaseEngine.Watch old=originalEngine.rule(w.id);if(Arrays.equals(old.reference,original)){w.reference=layout.homeReference.clone();w.pairedReference=true;w.runtimePairedReferences=layout.homeVariants;w.runtimeAlternative=original.clone();w.runtimeAlternativeThreshold=old.threshold;if(old.threshold==.004f)w.threshold=layout.homeTolerance;}else{w.reference=old.reference==null?null:old.reference.clone();w.pairedReference=old.pairedReference;w.threshold=old.threshold;}}
   else if(p.extra.containsKey(w.regionId)&&w.pairedReference){PhaseEngine.Watch old=originalEngine.rule(w.id);w.runtimeAlternative=old.reference.clone();w.runtimeAlternativeThreshold=old.threshold;}
   else if(eligible.contains(w.regionId)&&!p.handles(w.regionId)&&Arrays.equals(w.reference,canonical.get(w.regionId.substring(prefix.length()))))w.runtimeBlocks=true;
  }
  if(usagi&&eligible.contains(UsagiPreset.PREFIX+"mission-notice")){PhaseEngine.Watch notice=e.rule(UsagiPreset.PREFIX+"mission-notice");if(notice!=null){layout.noticeDefault=true;notice.reference=new float[]{1};notice.threshold=0;notice.runtimeBlocks=false;}}
  if(usagi&&eligible.contains(UsagiPreset.PREFIX+"clear")&&evidence.get("usagi-stage-zero")!=null){layout.stageZeroReference=evidence.get("usagi-stage-zero").clone();if(layout.stageZeroReference.length!=768)throw new IllegalArgumentException("増えうさぎの完了見本");for(PhaseEngine.Phase state:e.phases.values())for(PhaseEngine.Watch w:state.watches)if(w.regionId.equals(UsagiPreset.PREFIX+"clear")){w.reference=new float[]{1};w.threshold=0;w.pairedReference=false;w.runtimeBlocks=false;w.runtimeAlternative=null;}}
  if(layout.stageZeroReference!=null){List<float[]> zero=new ArrayList<>();for(String key:new TreeSet<>(evidence.keySet()))if(key.equals("usagi-stage-zero")||key.startsWith("usagi-stage-zero-")){float[] ref=evidence.get(key);if(ref.length!=768)throw new IllegalArgumentException("増えうさぎの完了見本");zero.add(ref.clone());}layout.stageZeroReferences=zero.toArray(new float[zero.size()][]);}
  if(usagi&&eligible.contains(UsagiPreset.PREFIX+"missions")&&eligible.contains(UsagiPreset.PREFIX+"play")&&eligible.contains(UsagiPreset.PREFIX+"get")){layout.usagiMissions=new UsagiMissionEvidence(evidence);if(layout.usagiMissions.enabled())layout.usagiMissions.install(e);}
  if(usagi&&eligible.contains(UsagiPreset.PREFIX+"amount-ready")&&eligible.contains(UsagiPreset.PREFIX+"amount-level"))layout.usagiAmount=new UsagiAmountEvidence(e,evidence);
  if(usagi&&eligible.contains(UsagiPreset.PREFIX+"dialog")&&eligible.contains(UsagiPreset.PREFIX+"cancel")){layout.usagiCancel=new UsagiCancelEvidence(evidence);layout.usagiCancel.install(e);}
  if(farm){layout.farmDialogs=new FarmDialogEvidence(evidence);for(String id:new ArrayList<>(layout.farmDialogs.refs.keySet()))if(!eligible.contains(id))layout.farmDialogs.refs.remove(id);FarmControlEvidence.install(e);}
  return layout;
 }
 static float[] toVirtual(float[] b){return new float[]{b[0],(b[1]-NORMAL_TOP)/NORMAL_Y,b[2],(b[3]-NORMAL_TOP)/NORMAL_Y};}
 static float[] map(float[] b,float[] t){return new float[]{b[0]*t[0]+t[2],b[1]*t[1]+t[3],b[2]*t[0]+t[2],b[3]*t[1]+t[3]};}
 static boolean inside(float[] b){return b[0]>=0&&b[1]>=0&&b[2]<=1&&b[3]<=1&&b[2]>b[0]&&b[3]>b[1];}
 static float[] paired(int width,int height,float[][] bounds,float[] t,RegionSampler.Pixels pixels){float[] a=map(bounds[0],t),b=map(bounds[1],t);if(!inside(a)||!inside(b))return null;float[] first=RegionSampler.sample(width,height,a,pixels),second=RegionSampler.sample(width,height,b,pixels),result=new float[1536];System.arraycopy(first,0,result,0,768);System.arraycopy(second,0,result,768,768);return result;}
 float[] homeSample(float[] t,RegionSampler.Pixels pixels){float[] raw=paired(width,height,new float[][]{homeHeader,homeButton},t,pixels);if(farmStableHome!=null){float[] stable=FarmHomeEvidence.sample(width,height,t,pixels);if(stable!=null&&homeDistance(stable)<homeDistance(raw))raw=stable;}if(usagi&&homeVariants!=null){float[] stable=UsagiHomeEvidence.sample(width,height,t,pixels);if(stable!=null&&homeDistance(stable)<homeDistance(raw))raw=stable;}return raw;}
 float homeDistance(float[] raw){if(raw==null)return Float.POSITIVE_INFINITY;float d=PhaseEngine.blockDistance(homeReference,raw);if(homeVariants!=null)for(float[] variant:homeVariants)d=Math.min(d,PhaseEngine.blockDistance(variant,raw));return d;}
 float homeScore(float[] t,RegionSampler.Pixels pixels){return homeDistance(homeSample(t,pixels));}
 void resetSize(int w,int h){if(width==w&&height==h)return;width=w;height=h;transform=new float[]{1,1,0,0};located=false;local.clear();pairLayouts.clear();searched.clear();lastHomeSearch=-SEARCH_MS;}
 float[] locateHome(RegionSampler.Pixels pixels,long now){
  float[] current=homeSample(transform,pixels);float distance=homeDistance(current);
  if(distance>homeTolerance&&now-lastHomeSearch>=SEARCH_MS){
   lastHomeSearch=now;float[] best=transform.clone();float score=distance;
   List<float[]> candidates=new ArrayList<>();candidates.add(new float[]{1,1,0,0});candidates.add(new float[]{1,NORMAL_Y,0,NORMAL_TOP});
   if(usagi&&homeVariants!=null)candidates.add(UsagiHomeEvidence.F52A_LAYOUT.clone());
   if(viewportHomeCandidate!=null)candidates.add(viewportHomeCandidate.clone());
   // Plausible content insets, not a traversal of image pixels or every screen location.
   for(float side:new float[]{0,.03f})for(float top:new float[]{0,.04f,.08f,.12f})for(float bottom:new float[]{0,.04f,.08f})candidates.add(new float[]{1-2*side,1-top-bottom,side,top});
   for(float[] c:candidates){float s=homeScore(c,pixels);if(s<score){score=s;best=c;}}
   float[] steps={.03f,.02f,.006f,.006f};
   for(int pass=0;pass<5;pass++)for(int axis=0;axis<4;axis++)for(int sign:new int[]{-1,1}){float[] c=best.clone();c[axis]+=sign*steps[axis]/(1<<pass);if(c[0]<.8f||c[0]>1.08f||c[1]<.75f||c[1]>1.15f||Math.abs(c[2])>.10f||Math.abs(c[3])>.15f)continue;float s=homeScore(c,pixels);if(s<score){score=s;best=c;}}
   // Insets and vertical scale must sometimes move together: sub-pixel letter sampling
   // has local minima which changing one coordinate at a time cannot escape.
   if(score>homeTolerance&&score<.12f){List<float[]> seeds=new ArrayList<>();seeds.add(best.clone());seeds.add(transform.clone());seeds.add(new float[]{1,NORMAL_Y,0,NORMAL_TOP});for(float[] seed:seeds)for(int iy=-4;iy<=4;iy++)for(int scale=-4;scale<=4;scale++){float[] c=seed.clone();c[1]+=scale*.0025f;c[3]+=iy*.00125f;float s=homeScore(c,pixels);if(s<score){score=s;best=c;}}}
   if(score<=homeTolerance){if(!Arrays.equals(transform,best)){local.clear();pairLayouts.clear();searched.clear();}transform=best;located=true;current=homeSample(best,pixels);distance=score;status="目印に合わせて表示位置を補正";}
  }
  if(distance<=homeTolerance){located=true;return current;}
  // The original evidence remains available, with its original strict tolerance.
  float[] raw=RegionSampler.sample(width,height,usagi?new float[]{.66f,.082f,.77f,.112f}:new float[]{.75f,.077f,.805f,.092f},pixels);
  float[] normal=usagi?new float[]{1,NORMAL_Y,0,NORMAL_TOP}:new float[]{1,1,0,0};float[] button=map(homeButton,normal);float[] second=inside(button)?RegionSampler.sample(width,height,button,pixels):null;
  if(FlyCircuit.distance(canonicalHome,raw)<=canonicalTolerance&&second!=null&&PhaseEngine.blockDistance(Arrays.copyOfRange(homeReference,768,1536),second)<=homeTolerance){transform=normal;located=true;status="通常画面の見本で表示位置を確認";return raw;}
  status="主画面の目印を再確認中";return current==null?new float[1536]:current;
 }
 List<PhaseEngine.Watch> references(String id){List<PhaseEngine.Watch> result=new ArrayList<>();PhaseEngine.Phase state=engine.phases.get(engine.current);if(state!=null)for(PhaseEngine.Watch w:state.watches)if(id.equals(w.regionId)&&engine.eligible(w)&&w.reference!=null&&(w.condition==PhaseEngine.MATCH||w.condition==PhaseEngine.DISAPPEAR))result.add(w);return result;}
 static float score(float[] raw,List<PhaseEngine.Watch> watches){float best=Float.POSITIVE_INFINITY;for(PhaseEngine.Watch w:watches)best=Math.min(best,w.distance(raw)/w.threshold);return best;}
 void samplePair(String id,RegionSampler.Pixels pixels,long now,Map<String,float[]> result){
  float[][] anchors=profile.extra.get(id);List<PhaseEngine.Watch> refs=references(id);float[] t=pairLayouts.get(id);if(t==null)t=transform.clone();float[] raw=paired(width,height,anchors,t,pixels);float best=score(raw,refs);
  // A prior miss must not suppress the first frame after a dialog transition.
  // These few known layouts are cheap; only fine neighbourhood search is throttled.
  if(best>1&&!refs.isEmpty())for(float[] c:new float[][]{new float[]{1,1,0,0},new float[]{1,NORMAL_Y,0,NORMAL_TOP},transform.clone()}){float[] f=paired(width,height,anchors,c,pixels);float s=score(f,refs);if(s<best){best=s;t=c;raw=f;}}
  if(best<=1)pairLayouts.put(id,t.clone());
  if(broadSearch&&best>1&&!refs.isEmpty()&&now-searched.getOrDefault(id,-SEARCH_MS)>=SEARCH_MS){searched.put(id,now);float[] chosen=t;
   for(float top:new float[]{0,.04f,.08f,.12f})for(float bottom:new float[]{0,.04f,.08f}){float[] c={transform[0],1-top-bottom,transform[2],top};float[] f=paired(width,height,anchors,c,pixels);float s=score(f,refs);if(s<best){best=s;chosen=c;raw=f;}}
   float[] steps={.03f,.02f,.006f,.006f};for(int pass=0;pass<5;pass++)for(int axis=0;axis<4;axis++)for(int sign:new int[]{-1,1}){float[] c=chosen.clone();c[axis]+=sign*steps[axis]/(1<<pass);if(c[0]<.8f||c[0]>1.15f||c[1]<.75f||c[1]>1.2f||Math.abs(c[2])>.1f||Math.abs(c[3])>.15f)continue;float[] f=paired(width,height,anchors,c,pixels);float s=score(f,refs);if(s<best){best=s;chosen=c;raw=f;}}
   if(best<=1){t=chosen;pairLayouts.put(id,t.clone());}
   else{t=transform.clone();raw=paired(width,height,anchors,t,pixels);pairLayouts.remove(id);}
  }
  if(raw!=null)result.put(id,raw);if(raw!=null&&score(raw,refs)<=1){result.put(id+BOUNDS,map(anchors[1],t));return;}
  if(refs.isEmpty())return;
  // Ordinary-layout button evidence remains strict and requires the reward heading too.
  for(float[] candidate:new float[][]{transform,new float[]{1,NORMAL_Y,0,NORMAL_TOP},new float[]{1,1,0,0}}){float[] original=originalBounds.get(id);float[] b=map(usagi?toVirtual(original):original,candidate),title=map(anchors[0],candidate);if(!inside(b)||!inside(title))continue;float[] label=RegionSampler.sample(width,height,b,pixels),heading=RegionSampler.sample(width,height,title,pixels);
   if(score(label,refs)<=1&&PhaseEngine.blockDistance(Arrays.copyOfRange(refs.get(0).reference,0,768),heading)<=.02f){result.put(id,label);result.put(id+BOUNDS,b);return;}
  }
 }
 float[] regionSample(String id,float[] b,RegionSampler.Pixels pixels){float[][] pair=profile.extra.get(id);if(pair!=null){float[] original=map(pair[1],transform);float dx=(b[0]+b[2]-original[0]-original[2])/2,dy=(b[1]+b[3]-original[1]-original[3])/2;float[] t=transform.clone();t[2]+=dx;t[3]+=dy;float[] title=map(pair[0],t);if(!inside(b)||!inside(title))return null;float[] first=RegionSampler.sample(width,height,title,pixels),second=RegionSampler.sample(width,height,b,pixels),raw=new float[1536];System.arraycopy(first,0,raw,0,768);System.arraycopy(second,0,raw,768,768);return raw;}return inside(b)?RegionSampler.sample(width,height,b,pixels):null;}
 Map<String,float[]> sample(List<PhaseEngine.Region> regions,int w,int h,RegionSampler.Pixels pixels,long now){
  resetSize(w,h);broadSearch=true;Map<String,float[]> result=new HashMap<>();
  if(relativeEnabled&&usagiCancel!=null&&usagiCancel.sample(this,w,h,pixels,result))return result;
  if(relativeEnabled&&usagiMissions!=null&&usagiMissions.sample(this,w,h,pixels,result)){
   for(PhaseEngine.Region region:regions)if(AdCloseAutomaton.handles(region.id))result.put(region.id,new float[]{0});
   return result;
  }
  for(PhaseEngine.Region r:regions)if(r.id.equals(profile.region)){result.put(r.id,locateHome(pixels,now));result.put(r.id+BOUNDS,map(homeHeader,transform));float[] p=map(new float[]{.5f,usagi?.775f:.76f,.5f,usagi?.775f:.76f},transform);result.put(MAIN,new float[]{p[0]*w,p[1]*h});break;}
  if(relocator.enabled&&FarmPreset.APP.equals(relocator.app))relocator.farmScene(this,w,h,pixels,now,result);
  PhaseEngine.Phase phase=engine.phases.get(engine.current);
  if(relocator.enabled&&phase!=null&&phase.markersDuring&&ContentRelocator.recognized(this,profile.region,result))broadSearch=false;
  for(PhaseEngine.Region r:regions){if(r.id.equals(profile.region))continue;if(AdCloseAutomaton.handles(r.id))continue;
   if(r.id.equals(FarmPreset.P+"chest-open")&&FarmControlEvidence.namedOnly(engine,r.id)){result.put(r.id,new float[]{0});continue;}
   if(relativeEnabled&&farmDialogs!=null&&farmDialogs.locate(r.id,w,h,pixels,now,result,broadSearch))continue;
   if(noticeDefault&&r.id.equals(UsagiPreset.PREFIX+"mission-notice")){result.put(r.id,new float[]{UsagiNotice.visible(w,h,transform,pixels)?1:0});continue;}
   if(stageZeroReference!=null&&r.id.equals(UsagiPreset.PREFIX+"clear")){result.put(r.id,new float[]{UsagiStageCompletion.visible(w,h,transform,pixels,stageZeroReferences)?1:0});continue;}
   if(profile.extra.containsKey(r.id)){samplePair(r.id,pixels,now,result);continue;}
   float[] baseline=base.get(r.id);if(baseline==null){result.put(r.id,RegionSampler.sample(w,h,r.bounds,pixels));continue;}
   float[] predicted=map(baseline,transform),b=local.get(r.id);if(b==null)b=predicted;float[] raw=regionSample(r.id,b,pixels);List<PhaseEngine.Watch> refs=references(r.id);float best=score(raw,refs);
   if(best>1&&!refs.isEmpty())for(float[] candidate:new float[][]{map(baseline,new float[]{1,NORMAL_Y,0,NORMAL_TOP}),baseline.clone()}){if(!inside(candidate))continue;float[] f=regionSample(r.id,candidate,pixels);float s=score(f,refs);if(s<best){best=s;b=candidate;raw=f;}}
   if(best<=1)local.put(r.id,b.clone());
   // Search only an active preset template's small neighbourhood, once per second on a miss.
   if(broadSearch&&best>1&&!refs.isEmpty()&&(baseline[2]-baseline[0])*(baseline[3]-baseline[1])>=.0005f&&now-searched.getOrDefault(r.id,-SEARCH_MS)>=SEARCH_MS){
    searched.put(r.id,now);float limitY=r.id.endsWith("close")||r.id.endsWith("start")?.10f:.035f;float[] chosen=b;
    for(float scale:new float[]{1,.85f,1.15f})for(int iy=-4;iy<=4;iy++)for(int ix=-1;ix<=1;ix++){
     float cx=(predicted[0]+predicted[2])/2+ix*.015f,cy=(predicted[1]+predicted[3])/2+iy*limitY/4,bw=(predicted[2]-predicted[0])*scale,bh=(predicted[3]-predicted[1])*scale;
     float[] c={cx-bw/2,cy-bh/2,cx+bw/2,cy+bh/2};if(!inside(c))continue;float[] f=regionSample(r.id,c,pixels);float s=score(f,refs);if(s<best){best=s;chosen=c;raw=f;}
    }
    for(int pass=0;pass<5;pass++)for(int axis=0;axis<2;axis++)for(int sign:new int[]{-1,1}){float[] c=chosen.clone();float step=sign*.006f/(1<<pass);c[axis]+=step;c[axis+2]+=step;if(!inside(c))continue;float[] f=regionSample(r.id,c,pixels);float s=score(f,refs);if(s<best){best=s;chosen=c;raw=f;}}
    if(best<=1){b=chosen;local.put(r.id,b);}
    else{b=predicted;raw=regionSample(r.id,b,pixels);local.remove(r.id);}
   }
   if(raw!=null)result.put(r.id,raw);if(raw!=null&&(refs.isEmpty()||score(raw,refs)<=1))result.put(r.id+BOUNDS,b.clone());
  }
  boolean known=AdExitPolicy.gameVisible(engine,result);
  for(PhaseEngine.Region r:regions)if(AdCloseAutomaton.handles(r.id)){if(known)result.put(r.id,new float[]{0});else AdCloseAutomaton.sample(r,w,h,pixels,result);}
  return result;
 }
 Map<String,float[]> sampleRelative(List<PhaseEngine.Region> regions,int w,int h,RegionSampler.Pixels pixels,long now){return sampleRelative(regions,w,h,pixels,now,null);}
 Map<String,float[]> sampleRelative(List<PhaseEngine.Region> regions,int w,int h,RegionSampler.Pixels pixels,long now,int[] window){
  if(popupEvidence!=null){Map<String,float[]> popup=popupEvidence.quick(w,h,pixels,window);if(GamePopupEvidence.fast(popup)){if(forceHomeProbe||now-lastPopupHomeProbe>=2000){lastPopupHomeProbe=now;samplePopupHome(regions,w,h,pixels,now,window,popup);}return popup;}}
  viewportHomeCandidate=null;
  if(!relativeEnabled)return sample(regions,w,h,FrameSampleCache.wrap(w,h,pixels),now);
  ContentViewport v=ContentViewport.locate(w,h,pixels,window);
  // The native portrait evidence includes system-bar space. Convert that finite
  // candidate into the app crop; glyph matching still decides whether it fits.
  if(usagi&&v.left==0&&v.width==w&&(v.top>0||v.height<h)&&v.top<=h*.15f&&h-v.top-v.height<=h*.15f){float[] nativeLayout=UsagiHomeEvidence.F52A_LAYOUT;viewportHomeCandidate=new float[]{nativeLayout[0],nativeLayout[1]*h/v.height,nativeLayout[2],(nativeLayout[3]*h-v.top)/v.height};}
  RegionSampler.Pixels localPixels=v.left==0&&v.top==0&&v.width==w&&v.height==h?pixels:(x,y)->pixels.get(x+v.left,y+v.top);
  RegionSampler.Pixels cached=FrameSampleCache.wrap(v.width,v.height,localPixels);
  relocator.beginFrame();
  Map<String,float[]> result=sample(regions,v.width,v.height,cached,now);
  relocator.adapt(this,regions,v.width,v.height,cached,now,result);
  if(result.containsKey(UsagiCancelEvidence.PAGE))UsagiMissionEvidence.blockHome(result);
  if(usagiAmount!=null&&regions.contains(engine.regions.get(UsagiPreset.PREFIX+"amount-ready")))usagiAmount.sample(this,v.width,v.height,cached,result);
  if(playableAds!=null)playableAds.sample(engine,result,v.width,v.height,cached,w,h);
  result.put(VIEWPORT,v.metadata());if(window!=null)result.put(WINDOW,new float[]{window[0],window[1],window[2],window[3]});return result;
 }
 void samplePopupHome(List<PhaseEngine.Region> regions,int w,int h,RegionSampler.Pixels pixels,long now,int[] window,Map<String,float[]> popup){
  ContentViewport v=ContentViewport.locate(w,h,pixels,window);RegionSampler.Pixels localPixels=(x,y)->pixels.get(x+v.left,y+v.top);
  RegionSampler.Pixels cached=FrameSampleCache.wrap(v.width,v.height,localPixels);List<PhaseEngine.Region> home=new ArrayList<>();
  for(String id:new String[]{profile.region,CoinPreset.P+"fever"}){PhaseEngine.Region region=engine.regions.get(id);if(region!=null&&!home.contains(region))home.add(region);}
  if(!home.isEmpty()){Map<String,float[]> result=sample(home,v.width,v.height,cached,now);for(PhaseEngine.Region region:home)if(result.get(region.id)!=null)popup.put(GamePopupEvidence.HOME_PROBE+region.id,result.get(region.id));}
 }
 float[] pointRelative(PhaseEngine.Watch watch,Map<String,float[]> samples,int w,int h){if(samples.containsKey(watch.regionId+GamePopupEvidence.POINT))return GamePopupEvidence.point(watch.regionId,samples,w,h);if(relocator.ownsAction(watch)){float[] local=samples.get(watch.id+ContentRelocator.ACTION_POINT),viewport=samples.get(VIEWPORT);return ContentViewport.offset(local,viewport,w,h);}if(samples.containsKey(watch.regionId+ContentRelocator.LOCATED)&&watch.action==PhaseEngine.CENTER&&!ContentRelocator.mission(watch)){float[] v=samples.get(VIEWPORT);return v==null?null:ContentViewport.offset(RegionMath.center(samples.get(watch.regionId+BOUNDS),(int)v[2],(int)v[3]),v,w,h);}if(samples.containsKey(watch.regionId+AdCloseAutomaton.CONTROL_POINT))return AdCloseAutomaton.controlPoint(watch.regionId,samples,w,h);if(samples.containsKey(watch.regionId+FarmControlEvidence.POINT))return FarmControlEvidence.point(watch.regionId,samples,w,h);float[] v=samples.get(VIEWPORT);if(v==null)return relativeEnabled?null:point(watch,samples,w,h);return ContentViewport.offset(point(watch,samples,(int)v[2],(int)v[3]),v,w,h);}
 float[] mainPointRelative(Map<String,float[]> samples,int w,int h){float[] p=samples.get(MAIN),v=samples.get(VIEWPORT);return v==null?(relativeEnabled?null:p==null?null:p.clone()):ContentViewport.offset(p,v,w,h);}
 float[] defaultMarkerRelative(float x,float y,int w,int h,Map<String,float[]> samples){
  if(!relativeEnabled)return defaultMarker(x,y,w,h,samples);
  float expected=usagi?.78f:this instanceof CoinLayout?CoinPreset.MAIN_Y:this instanceof ShoppingLayout?ShoppingPreset.MAIN_Y:this instanceof BoxLayout?BoxPreset.MAIN_Y:this instanceof PoipoiLayout?PoipoiPreset.MAIN_Y:.76f;
  if(Math.abs(x/w-.5f)>.035f||Math.abs(y/h-expected)>.035f)return new float[]{x,y};
  return mainPointRelative(samples,w,h);
 }
 float[] point(PhaseEngine.Watch watch,Map<String,float[]> samples,int w,int h){
  if(FarmControlEvidence.namedOnly(watch))return FarmControlEvidence.point(watch.regionId,samples,w,h);
  if(AdCloseAutomaton.handles(watch.regionId))return samples.get(watch.regionId+AdCloseAutomaton.POINT);
  float[] b=samples.get(watch.regionId+BOUNDS);if(b==null){if(base.containsKey(watch.regionId)&&watch.condition==PhaseEngine.MATCH)return null;PhaseEngine.Region r=engine.regions.get(watch.regionId);return r==null?null:RegionMath.center(r.bounds,w,h);}
  if(usagi&&watch.id.equals(UsagiPreset.PREFIX+"ミッション中央をタップ")&&base.containsKey(UsagiPreset.PREFIX+"mission-touch")){
   float[] predicted=map(base.get(watch.regionId),transform),touch=map(base.get(UsagiPreset.PREFIX+"mission-touch"),transform);float dx=(b[0]+b[2]-predicted[0]-predicted[2])/2,dy=(b[1]+b[3]-predicted[1]-predicted[3])/2;
   return new float[]{((touch[0]+touch[2])/2+dx)*w,((touch[1]+touch[3])/2+dy)*h};
  }
  return RegionMath.center(b,w,h);
 }
 float[] defaultMarker(float x,float y,int w,int h,Map<String,float[]> samples){if(Math.abs(x/w-.5f)>.035f||Math.abs(y/h-(usagi?.78f:.76f))>.035f)return new float[]{x,y};float[] p=samples.get(MAIN);return p==null?new float[]{x,y}:p.clone();}
}
