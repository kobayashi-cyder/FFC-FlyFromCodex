package jp.tapmarker;
import java.util.*;

/** Only the active state's paired landmarks and known shelf/button candidates are sampled. */
final class ShoppingLayout extends AdaptiveLayout {
 final Map<String,float[]> canonical;final Map<String,Long> scaleSearched=new HashMap<>();final ShoppingControlEvidence controls;
 boolean getDefault,emptyDefault,customerDefault,boostDefault,resumeDefault;
 ShoppingLayout(PhaseEngine e,VirtualPresetProfile p,Map<String,float[]> refs){super(e,p,false,refs.get("home"),.015f);canonical=refs;controls=new ShoppingControlEvidence(refs);}
 static ShoppingLayout install(PhaseEngine e,Map<String,float[]> refs){
  PhaseEngine.Watch home=e.rule(ShoppingPreset.P+"verify-home");PhaseEngine.Region region=e.regions.get(ShoppingPreset.P+"home");
  if(home==null||region==null||!Arrays.equals(home.reference,refs.get("home"))||!Arrays.equals(region.bounds,ShoppingPreset.HOME_BUTTON))return null;
  VirtualPresetProfile p=new VirtualPresetProfile(region.id,ShoppingPreset.HOME_TITLE,ShoppingPreset.HOME_BUTTON,refs.get("home"));ShoppingLayout layout=new ShoppingLayout(e,p,refs);PhaseEngine defaults=new PhaseEngine();ShoppingPreset.install(defaults,refs);
  for(String key:new String[]{"home","shop","stock","carrot","apple","milk","fish","meat","shift","summon","boost","boost-ready","arrival","arrival-claim","clear","missions","play","reward"}){
   String id=ShoppingPreset.P+key;PhaseEngine.Region r=e.regions.get(id),d=defaults.regions.get(id);if(r==null||!Arrays.equals(r.bounds,d.bounds))continue;
   boolean original=false;for(PhaseEngine.Phase state:e.phases.values())for(PhaseEngine.Watch w:state.watches)if(id.equals(w.regionId)&&Arrays.equals(w.reference,refs.get(key)))original=true;if(!original)continue;
   float[][] a=ShoppingPreset.anchors(key);p.extra.put(id,new float[][]{a[0].clone(),a[1].clone()});layout.base.put(id,r.bounds.clone());layout.originalBounds.put(id,r.bounds.clone());
   ArrayList<float[]> variants=new ArrayList<>();for(int i=2;refs.containsKey(key+"-v"+i);i++)variants.add(refs.get(key+"-v"+i));
   if(!variants.isEmpty())for(PhaseEngine.Phase state:e.phases.values())for(PhaseEngine.Watch w:state.watches)if(id.equals(w.regionId)&&Arrays.equals(w.reference,refs.get(key)))w.runtimePairedReferences=variants.toArray(new float[0][]);
   if(layout.controls.controls.containsKey(id))for(PhaseEngine.Phase state:e.phases.values())for(PhaseEngine.Watch w:state.watches){PhaseEngine.Watch originalRule=defaults.rule(w.id);if(id.equals(w.regionId)&&Arrays.equals(w.reference,refs.get(key))&&originalRule!=null&&w.threshold==originalRule.threshold&&w.condition==originalRule.condition&&w.action==originalRule.action&&w.nextPhase.equals(originalRule.nextPhase))w.runtimeControlEvidence=true;}
  }
  layout.getDefault=original(e,defaults,refs,"get","get-tap");layout.emptyDefault=original(e,defaults,refs,"empty","empty-tap");layout.customerDefault=original(e,defaults,refs,"customer","customer-tap");layout.boostDefault=original(e,defaults,refs,"boost-ready","boost-button");
  PhaseEngine.Watch get=e.rule(ShoppingPreset.P+"get-tap"),defaultGet=defaults.rule(ShoppingPreset.P+"get-tap");if(layout.getDefault&&get.threshold==defaultGet.threshold&&get.action==defaultGet.action&&get.condition==defaultGet.condition&&get.nextPhase.equals(defaultGet.nextPhase))get.runtimeControlEvidence=true;
  Set<String> allowed=new HashSet<>(p.extra.keySet());if(layout.getDefault)allowed.add(ShoppingPreset.P+"get");PhaseEngine.Region resume=e.regions.get(ShoppingResumePolicy.REGION);
  layout.resumeDefault=resume!=null&&Arrays.equals(resume.bounds,ShoppingResumePolicy.LABEL)&&layout.controls.controls.containsKey(resume.id)&&factoryResumeReferences(e);if(layout.resumeDefault)allowed.add(resume.id);layout.controls.controls.keySet().retainAll(allowed);
  if(layout.customerDefault){PhaseEngine.Watch w=e.rule(ShoppingPreset.P+"customer-tap");w.runtimePairedReferences=new float[][]{refs.get("customer-normal"),refs.get("customer-gold")};}
  PhaseEngine.Watch notice=e.rule(ShoppingPreset.P+"mission-notice");layout.noticeDefault=notice!=null&&Arrays.equals(notice.reference,new float[]{1})&&Arrays.equals(e.regions.get(notice.regionId).bounds,ShoppingPreset.NOTICE);
  return layout;
 }
 static boolean original(PhaseEngine e,PhaseEngine defaults,Map<String,float[]> refs,String key,String rule){PhaseEngine.Region r=e.regions.get(ShoppingPreset.P+key),d=defaults.regions.get(ShoppingPreset.P+key);PhaseEngine.Watch w=e.rule(ShoppingPreset.P+rule);return r!=null&&d!=null&&Arrays.equals(r.bounds,d.bounds)&&w!=null&&Arrays.equals(w.reference,refs.get(key));}
 static boolean factoryResumeReferences(PhaseEngine e){boolean found=false;for(PhaseEngine.Phase phase:e.phases.values())for(PhaseEngine.Watch watch:phase.watches)if(watch.regionId.equals(ShoppingResumePolicy.REGION)){if(!Arrays.equals(watch.reference,new float[]{1})||watch.threshold!=0)return false;found=true;}return found;}
 @Override void samplePair(String id,RegionSampler.Pixels pixels,long now,Map<String,float[]> result){
  List<PhaseEngine.Watch> active=references(id);boolean own=!active.isEmpty();for(PhaseEngine.Watch watch:active)if(!watch.runtimeControlEvidence)own=false;
  boolean homeConfirmed=result.containsKey(ShoppingPreset.P+"home"+BOUNDS);
  if(relativeEnabled&&own&&controls.controls.containsKey(id)&&(!homeConfirmed||id.equals(ShoppingPreset.P+"home")||id.equals(ShoppingPreset.P+"play"))){float[] t=controls.locate(id,width,height,pixels,now,transform,result);if(t!=null){pairLayouts.put(id,t.clone());return;}}
  super.samplePair(id,pixels,now,result);if(result.containsKey(id+BOUNDS)||now-scaleSearched.getOrDefault(id,-SEARCH_MS)<SEARCH_MS)return;
  List<PhaseEngine.Watch> refs=references(id);if(refs.isEmpty())return;scaleSearched.put(id,now);float[][] a=profile.extra.get(id);float best=score(result.get(id),refs);float[] chosen=transform.clone(),raw=result.get(id);
  for(float side:new float[]{.015f,.03f})for(float sy:new float[]{.84f,.86f,.90f,.94f,1})for(float top:new float[]{.04f,.06f,.08f,.12f}){float[] t={1-2*side,sy,side,top};float[] f=paired(width,height,a,t,pixels);float s=score(f,refs);if(s<best){best=s;chosen=t;raw=f;}}
  if(!id.equals(ShoppingPreset.P+"home")&&!id.equals(ShoppingPreset.P+"shop"))for(float scale:new float[]{.60f,.64f,.68f,.72f,.76f,.80f,.84f,.88f,.92f,.96f})for(float dx:new float[]{-.015f,0,.015f})for(float dy:new float[]{-.01f,0,.01f}){float[] t={scale*transform[0],scale*transform[1],transform[2]+(1-scale)*transform[0]/2+dx,transform[3]+(1-scale)*transform[1]/2+dy};float[] f=paired(width,height,a,t,pixels);float s=score(f,refs);if(s<best){best=s;chosen=t;raw=f;}}
  float[] steps={.02f,.02f,.004f,.004f};for(int pass=0;pass<5;pass++)for(int axis=0;axis<4;axis++)for(int sign:new int[]{-1,1}){float[] t=chosen.clone();t[axis]+=sign*steps[axis]/(1<<pass);float[] f=paired(width,height,a,t,pixels);float s=score(f,refs);if(s<best){best=s;chosen=t;raw=f;}}
  if(best<=1){pairLayouts.put(id,chosen);result.put(id,raw);result.put(id+BOUNDS,map(a[1],chosen));}
 }
 void transformFrom(String id,Map<String,float[]> samples){if(samples.containsKey(id+BOUNDS)){float[] t=pairLayouts.get(id);if(t!=null){transform=t.clone();located=true;status="この画面の表示位置と操作ボタンを確認";}}}
 boolean dynamic(String id){return resumeDefault&&id.equals(ShoppingResumePolicy.REGION)||getDefault&&id.equals(ShoppingPreset.P+"get")||emptyDefault&&id.equals(ShoppingPreset.P+"empty")||customerDefault&&id.equals(ShoppingPreset.P+"customer")||noticeDefault&&id.equals(ShoppingPreset.P+"mission-notice");}
 @Override Map<String,float[]> sample(List<PhaseEngine.Region> regions,int w,int h,RegionSampler.Pixels pixels,long now){
  if(width!=w||height!=h)scaleSearched.clear();resetSize(w,h);Map<String,float[]> result=new HashMap<>();
  for(String key:new String[]{"missions","home","shop"})for(PhaseEngine.Region r:regions)if(r.id.equals(ShoppingPreset.P+key)&&profile.extra.containsKey(r.id)){samplePair(r.id,pixels,now,result);transformFrom(r.id,result);}
  for(PhaseEngine.Region r:regions){if(result.containsKey(r.id)||AdCloseAutomaton.handles(r.id)||dynamic(r.id))continue;if(profile.extra.containsKey(r.id))samplePair(r.id,pixels,now,result);else result.put(r.id,RegionSampler.sample(w,h,r.bounds,pixels));}
  for(String key:new String[]{"reward","stock","shift","summon","boost","arrival-claim","arrival","missions","home","shop"})if(result.containsKey(ShoppingPreset.P+key+BOUNDS)){transformFrom(ShoppingPreset.P+key,result);break;}
  for(PhaseEngine.Region r:regions){
   if(resumeDefault&&r.id.equals(ShoppingResumePolicy.REGION)){result.put(r.id,new float[]{0});if(!result.containsKey(ShoppingPreset.P+"home"+BOUNDS))controls.locate(r.id,w,h,pixels,now,transform,result);}
   if(getDefault&&r.id.equals(ShoppingPreset.P+"get")){PhaseEngine.Watch watch=engine.rule(ShoppingPreset.P+"get-tap");MissionGetDetector.Found found=MissionGetDetector.find(w,h,r.bounds,transform,pixels,watch,ShoppingPreset.MISSION_TITLE,ShoppingPreset.GET_LABEL,.712f);result.put(r.id,found==null?new float[1536]:found.raw);if(found!=null)result.put(r.id+BOUNDS,found.bounds);else if(relativeEnabled&&watch.runtimeControlEvidence&&result.containsKey(ShoppingPreset.P+"missions"+BOUNDS))controls.get(w,h,pixels,transform,result);}
   if(emptyDefault&&r.id.equals(ShoppingPreset.P+"empty"))empty(w,h,pixels,result);
   if(customerDefault&&r.id.equals(ShoppingPreset.P+"customer"))customer(w,h,pixels,result);
   if(noticeDefault&&r.id.equals(ShoppingPreset.P+"mission-notice"))result.put(r.id,new float[]{result.containsKey(ShoppingPreset.P+"home"+BOUNDS)&&notice(w,h,pixels)?1:0});
  }
  if(boostDefault&&result.containsKey(ShoppingPreset.P+"boost-ready")&&activeBoost(w,h,pixels)){result.put(ShoppingPreset.P+"boost-ready",new float[1536]);result.remove(ShoppingPreset.P+"boost-ready"+BOUNDS);}
  if(result.containsKey(ShoppingPreset.P+"home"+BOUNDS)){float[] t=pairLayouts.get(ShoppingPreset.P+"home");result.put(MAIN,new float[]{(.5f*t[0]+t[2])*w,(ShoppingPreset.MAIN_Y*t[1]+t[3])*h});}
  boolean known=RecoveryRequest.visibleBranch(engine,result);
  for(PhaseEngine.Region r:regions)if(AdCloseAutomaton.handles(r.id)){float[] corner=AdCloseAutomaton.corner(r,w,h,pixels);float[] p=known?null:AdCloseAutomaton.find(w,h,corner,pixels);if(!known&&p==null)p=AdChevronDetector.find(w,h,corner,pixels,canonical.get("ad-chevron"),canonical.get("ad-chevron-small"),canonical.get("ad-chevron-dark"),canonical.get("ad-chevron-dark-small"));result.put(AdCloseAutomaton.CORNER_TOP,new float[]{AdCornerViewport.top(w,h,pixels)/(float)h});result.put(r.id,new float[]{p==null?0:1});if(p!=null)result.put(r.id+AdCloseAutomaton.POINT,p);if(!known)AdCloseAutomaton.word(r.id,w,h,pixels,result);}
  return result;
 }
 void empty(int w,int h,RegionSampler.Pixels pixels,Map<String,float[]> samples){String id=ShoppingPreset.P+"empty";samples.put(id,new float[1536]);if(!samples.containsKey(ShoppingPreset.P+"shop"+BOUNDS))return;PhaseEngine.Watch rule=engine.rule(ShoppingPreset.P+"empty-tap");
  // Five possible shelves, each tested with its own 商品入荷 bubble and empty tray.
  for(float[] offset:new float[][]{{-.566f,0},{0,0},{-.283f,.106f},{-.566f,.212f},{0,.212f}}){float[][] a={ShoppingPreset.EMPTY_TITLE.clone(),ShoppingPreset.EMPTY_TRAY.clone()};for(float[] b:a){b[0]+=offset[0];b[2]+=offset[0];b[1]+=offset[1];b[3]+=offset[1];}float[] raw=paired(w,h,a,transform,pixels);if(rule.distance(raw)<=rule.threshold){samples.put(id,raw);samples.put(id+BOUNDS,map(a[1],transform));return;}}
 }
 void customer(int w,int h,RegionSampler.Pixels pixels,Map<String,float[]> samples){String id=ShoppingPreset.P+"customer";samples.put(id,new float[1536]);if(!samples.containsKey(ShoppingPreset.P+"shop"+BOUNDS))return;PhaseEngine.Watch rule=engine.rule(ShoppingPreset.P+"customer-tap");for(float[] face:ShoppingPreset.CUSTOMERS)for(float dy:new float[]{0,-.006f,.006f}){float[] b=face.clone();b[1]+=dy;b[3]+=dy;float[] raw=paired(w,h,new float[][]{ShoppingPreset.SHOP_BUTTON,b},transform,pixels);if(rule.distance(raw)<=rule.threshold){samples.put(id,raw);samples.put(id+BOUNDS,map(b,transform));return;}}}
 boolean notice(int w,int h,RegionSampler.Pixels pixels){float[] b=map(ShoppingPreset.NOTICE,transform);if(!inside(b))return false;int[] box=RegionMath.bounds(b,w,h);int red=0,white=0;for(int y=box[1];y<box[3];y+=2)for(int x=box[0];x<box[2];x+=2){int c=pixels.get(x,y),r=c>>16&255,g=c>>8&255,blue=c&255;if(r>170&&g<120&&blue<130&&r-g>65)red++;if(r>210&&g>210&&blue>190)white++;}return red>=6&&white>=2;}
 boolean activeBoost(int w,int h,RegionSampler.Pixels pixels){float[][] a=ShoppingPreset.anchors("boost-active");float[] raw=paired(w,h,a,transform,pixels);if(PhaseEngine.blockDistance(canonical.get("boost-active"),raw)<=.035f)return true;float[] area=map(new float[]{.80f,.234f,.967f,.287f},transform);if(!inside(area))return false;int[] b=RegionMath.bounds(area,w,h);int blue=0,dark=0,total=0;for(int y=b[1];y<b[3];y+=3)for(int x=b[0];x<b[2];x+=3){int c=pixels.get(x,y),r=c>>16&255,g=c>>8&255,v=c&255;total++;if(v>60&&v>r*1.2f&&v>g*1.15f)blue++;if(r<80&&g<80&&v<100)dark++;}return total>0&&(blue>total*.14f||dark>total*.22f);}
 @Override float[] point(PhaseEngine.Watch rule,Map<String,float[]> samples,int w,int h){
  if(AdCloseAutomaton.handles(rule.regionId))return samples.get(rule.regionId+AdCloseAutomaton.POINT);
  if(rule.id.equals(ShoppingPreset.P+"mission-button-tap")||rule.id.equals(ShoppingPreset.P+"mission-shop-button-tap")){if(!samples.containsKey(rule.regionId+BOUNDS))return null;float[] t=pairLayouts.get(rule.regionId);return t==null?null:new float[]{(.778f*t[0]+t[2])*w,(.075f*t[1]+t[3])*h};}
  if(profile.handles(rule.regionId)||dynamic(rule.regionId)){float[] b=samples.get(rule.regionId+BOUNDS);return b==null?null:RegionMath.center(b,w,h);}
  PhaseEngine.Region r=engine.regions.get(rule.regionId);return r==null?null:RegionMath.center(r.bounds,w,h);
 }
 @Override float[] defaultMarker(float x,float y,int w,int h,Map<String,float[]> samples){if(Math.abs(x/w-.5f)>.035f||Math.abs(y/h-ShoppingPreset.MAIN_Y)>.035f)return new float[]{x,y};float[] p=samples.get(MAIN);return p==null?new float[]{x,y}:p.clone();}
}
