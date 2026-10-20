package jp.tapmarker;

import java.util.*;

/** Fresh, independently located controls; cached locations are checked against every new image. */
final class ContentRelocator {
 static final String LOCATED=":content-located",ACTION_POINT=":content-action-point";
 final Map<String,VisualLocator.Match> tracks=new HashMap<>();
 final Map<String,Long> searched=new HashMap<>();
 int budget;long cursor;boolean enabled,mainChecked;VisualLocator.Match frameMain;String app="";Map<String,float[]> targets=Collections.emptyMap();
 void configure(String pkg,Map<String,float[]> templates){app=pkg;targets=templates;enabled=templates.containsKey(pkg+":main:reference");}
 void clear(){tracks.clear();searched.clear();mainChecked=false;frameMain=null;}
 void beginFrame(){budget=2;mainChecked=false;frameMain=null;}
 static boolean recognized(AdaptiveLayout layout,String id,Map<String,float[]> samples){
  float[] raw=samples.get(id);if(raw==null)return false;
  for(PhaseEngine.Phase phase:layout.engine.phases.values())for(PhaseEngine.Watch watch:phase.watches)if(id.equals(watch.regionId)&&watch.reference!=null&&watch.distance(raw)<=watch.threshold)return true;
  return false;
 }
 VisualLocator.Match locate(String key,float[] ref,float[] mask,float[] expected,float tolerance,int w,int h,RegionSampler.Pixels pixels,long now){
  return locate(key,ref,mask,expected,tolerance,w,h,pixels,now,false);
 }
 VisualLocator.Match locate(String key,float[] ref,float[] mask,float[] expected,float tolerance,int w,int h,RegionSampler.Pixels pixels,long now,boolean detailed){
  return locate(key,ref,mask,expected,tolerance,w,h,pixels,now,detailed,null);
 }
 VisualLocator.Match locate(String key,float[] ref,float[] mask,float[] expected,float tolerance,int w,int h,RegionSampler.Pixels pixels,long now,boolean detailed,java.util.function.Predicate<float[]> accepts){
  VisualLocator.Match prior=tracks.get(key),found=prior==null?null:VisualLocator.verify(w,h,pixels,ref,mask,prior.bounds,tolerance,detailed,accepts);
  if(found==null)found=VisualLocator.verify(w,h,pixels,ref,mask,expected,tolerance,detailed,accepts);
  if(found==null&&budget>0&&(now-searched.getOrDefault(key,-2000L)>=1500)){
   budget--;searched.put(key,now);found=VisualLocator.find(w,h,pixels,ref,mask,expected,tolerance,detailed,accepts);
  }
  if(found==null)tracks.remove(key);else tracks.put(key,found);return found;
 }
 static float[] combined(VisualLocator.Match first,VisualLocator.Match second){float[] raw=new float[1536];System.arraycopy(first.raw,0,raw,0,768);System.arraycopy(second.raw,0,raw,768,768);return raw;}
 static float[] transform(float[] original,float[] actual){float sx=(actual[2]-actual[0])/(original[2]-original[0]),sy=(actual[3]-actual[1])/(original[3]-original[1]);return new float[]{sx,sy,actual[0]-original[0]*sx,actual[1]-original[1]*sy};}
 void pair(AdaptiveLayout layout,String id,int w,int h,RegionSampler.Pixels pixels,long now,Map<String,float[]> samples){
  if(FarmControlEvidence.namedOnly(layout.engine,id))return;
  List<PhaseEngine.Watch> watches=layout.references(id);if(watches.isEmpty())return;
  for(PhaseEngine.Watch watch:watches)if(watch.distance(samples.get(id))<=watch.threshold)return;
  float[][] boxes=layout.profile.extra.get(id);if(boxes==null&&id.equals(layout.profile.region))boxes=new float[][]{layout.homeHeader,layout.homeButton};
  if(boxes==null){PhaseEngine.Region region=layout.engine.regions.get(id);if(region==null)return;for(PhaseEngine.Watch watch:watches){if(watch.reference==null||watch.reference.length!=768||watch.threshold<=0)continue;VisualLocator.Match match=locate(id+":single",watch.reference,null,region.bounds,Math.min(.012f,watch.threshold),w,h,pixels,now);if(match!=null&&watch.distance(match.raw)<=watch.threshold){samples.put(id,match.raw);samples.put(id+AdaptiveLayout.BOUNDS,match.bounds.clone());samples.put(id+LOCATED,new float[]{1});return;}}return;}
  for(PhaseEngine.Watch watch:watches){if(!watch.pairedReference||watch.reference==null||watch.reference.length!=1536)continue;
   List<float[]> variants=new ArrayList<>();variants.add(watch.reference);if(watch.runtimePairedReferences!=null)variants.addAll(Arrays.asList(watch.runtimePairedReferences));
   for(int variant=0;variant<variants.size();variant++){
    float[] reference=variants.get(variant);if(reference.length!=1536)continue;
    VisualLocator.Match first=locate(id+":a:"+variant,Arrays.copyOfRange(reference,0,768),null,boxes[0],watch.threshold,w,h,pixels,now);
    if(first==null)continue;
    VisualLocator.Match second=locate(id+":b:"+variant,Arrays.copyOfRange(reference,768,1536),null,boxes[1],watch.threshold,w,h,pixels,now);
    if(second==null)continue;float[] raw=combined(first,second);if(watch.distance(raw)>watch.threshold)continue;
    // Independently found words on a mission list cannot admit the main scene.
    // A relocated Usagi home must also contain its actual play object now.
    if(UsagiPreset.APP.equals(app)&&id.equals(layout.profile.region)&&mainObject(layout,w,h,pixels,now)==null)continue;
    if(layout instanceof PoipoiLayout&&id.equals(PoipoiPreset.P+"full")&&(!samples.containsKey(PoipoiPreset.P+"game"+AdaptiveLayout.BOUNDS)||PoipoiAdHomeEvidence.distance(((PoipoiLayout)layout).fullInk,second.bounds,w,h,pixels)>.08f))continue;
    samples.put(id,raw);samples.put(id+AdaptiveLayout.BOUNDS,second.bounds.clone());samples.put(id+LOCATED,new float[]{1});
    layout.pairLayouts.put(id,transform(boxes[1],second.bounds));layout.status="画面全体から見出しと操作対象を個別に確認";return;
   }
  }
 }
 void adapt(AdaptiveLayout layout,List<PhaseEngine.Region> regions,int w,int h,RegionSampler.Pixels pixels,long now,Map<String,float[]> samples){
  if(!enabled)return;
  if(samples.containsKey(UsagiMissionEvidence.PAGE)||samples.containsKey(UsagiCancelEvidence.PAGE)){UsagiMissionEvidence.blockHome(samples);return;}
  // Two bounded searches per image. Rotation prevents a missing early rule starving other controls.
  if(FarmPreset.APP.equals(app))farmScene(layout,w,h,pixels,now,samples);
  for(PhaseEngine.Region r:regions)if(r.id.equals(layout.profile.region))pair(layout,r.id,w,h,pixels,now,samples);
  boolean hadMain=samples.containsKey(AdaptiveLayout.MAIN)||samples.containsKey(layout.profile.region+AdaptiveLayout.BOUNDS);
  if(hadMain)main(layout,w,h,pixels,now,samples);
  actionTargets(layout,w,h,pixels,now,samples);
  // Fresh play evidence needs no global hunt for absent dialogs. Their current/cached positions are still checked.
  if(samples.containsKey(AdaptiveLayout.MAIN+LOCATED)&&recognized(layout,layout.profile.region,samples))budget=0;
  if(!regions.isEmpty()){int first=(int)(cursor++%regions.size());for(int i=0;i<regions.size();i++){PhaseEngine.Region r=regions.get((first+i)%regions.size());if(!r.id.equals(layout.profile.region)&&!AdCloseAutomaton.handles(r.id))pair(layout,r.id,w,h,pixels,now,samples);}}
  if(!hadMain)main(layout,w,h,pixels,now,samples);
 }
 void main(AdaptiveLayout layout,int w,int h,RegionSampler.Pixels pixels,long now,Map<String,float[]> samples){
  PhaseEngine.Phase phase=layout.engine.phases.get(layout.engine.current);if(phase==null||!phase.markersDuring){samples.remove(AdaptiveLayout.MAIN);return;}
  if(!samples.containsKey(AdaptiveLayout.MAIN)&&!samples.containsKey(layout.profile.region+AdaptiveLayout.BOUNDS)&&!samples.containsKey(CoinPreset.P+"fever"+AdaptiveLayout.BOUNDS))return;
  String key=app+":main";float[] touch=targets.get(key+":point");VisualLocator.Match target=mainObject(layout,w,h,pixels,now);
  // Never retain an inferred old coordinate after the actual play object has disappeared or moved.
  samples.remove(AdaptiveLayout.MAIN);
  if(target!=null&&touch!=null&&touch.length==2){float x=(target.bounds[0]+touch[0]*(target.bounds[2]-target.bounds[0]))*w,y=(target.bounds[1]+touch[1]*(target.bounds[3]-target.bounds[1]))*h;
   if(x>=0&&y>=0&&x<w&&y<h){samples.put(AdaptiveLayout.MAIN,new float[]{x,y});samples.put(AdaptiveLayout.MAIN+LOCATED,target.bounds.clone());}
  }
 }
 VisualLocator.Match mainObject(AdaptiveLayout layout,int w,int h,RegionSampler.Pixels pixels,long now){
  if(mainChecked)return frameMain;mainChecked=true;
  String key=app+":main";float[] ref=targets.get(key+":reference"),box=targets.get(key+":bounds"),mask=targets.get(key+":mask"),expected=AdaptiveLayout.map(box,layout.transform),ink=targets.get(key+":ink");
  // Harvest letters keep their identity while the entire button cycles through fever colours.
  RegionSampler.Pixels targetPixels=ink!=null&&ink.length==1&&ink[0]==1?(x,y)->FarmHomeEvidence.white(pixels.get(x,y))?0xffffffff:0xff000000:pixels;
  List<float[]> variants=new ArrayList<>();variants.add(ref);for(int i=1;targets.containsKey(key+":reference:"+i);i++)variants.add(targets.get(key+":reference:"+i));
  for(int i=0;i<variants.size();i++){
   String id=key+":variant:"+i;VisualLocator.Match prior=tracks.get(id);
   for(float[] b:new float[][]{prior==null?null:prior.bounds,expected,box}){VisualLocator.Match exact=VisualLocator.verify(w,h,targetPixels,variants.get(i),mask,b,.018f);if(exact!=null){tracks.put(id,exact);return frameMain=exact;}}
  }
  for(int i=0;i<variants.size();i++){
   String id=key+":variant:"+i;VisualLocator.Match prior=tracks.get(id);
   for(float[] b:new float[][]{expected,prior==null?null:prior.bounds}){VisualLocator.Match near=VisualLocator.nearby(w,h,targetPixels,variants.get(i),mask,b,.018f);if(near!=null){tracks.put(id,near);return frameMain=near;}}
  }
  for(int i=0;i<variants.size();i++){VisualLocator.Match found=locate(key+":variant:"+i,variants.get(i),mask,expected,.018f,w,h,targetPixels,now);if(found!=null)return frameMain=found;}return null;
 }
 void farmScene(AdaptiveLayout layout,int w,int h,RegionSampler.Pixels pixels,long now,Map<String,float[]> samples){
  if(!enabled)return;
  for(PhaseEngine.Phase phase:layout.engine.phases.values())for(PhaseEngine.Watch watch:phase.watches)if(watch.condition==PhaseEngine.MATCH&&(watch.regionId.equals(FarmPreset.P+"close")||watch.regionId.equals(FarmPreset.P+"start")||watch.regionId.equals(FarmPreset.P+"chest")||watch.regionId.equals(FarmPreset.P+"chest-open"))&&watch.distance(samples.get(watch.regionId))<=watch.threshold){samples.remove(FarmPreset.P+"harvest");samples.remove(AdaptiveLayout.MAIN);return;}
  VisualLocator.Match harvest=mainObject(layout,w,h,pixels,now);if(harvest==null)return;
  // The fixed help control and the actual harvest letters remain visible during normal/pressed/fever play.
  // Both are validated in this image; no timer, stale coordinate or previous scene grants permission.
  String key=app+":fever-banner";float[] ref=targets.get(key+":reference"),box=targets.get(key+":bounds");
  if(layout.farmStableHome!=null){
   float[] header=Arrays.copyOfRange(layout.farmStableHome,0,768);
   VisualLocator.Match help=locate(app+":help",header,null,AdaptiveLayout.map(FarmHomeEvidence.HEADER,layout.transform),.012f,w,h,pixels,now);
   if(help==null)return;
  }else{
   // Compatibility with older reference sets: current caption and harvest must both match.
   if(recognized(layout,layout.profile.region,samples))return;
   if(ref==null||box==null||locate(key,ref,null,box,.012f,w,h,pixels,now,true)==null)return;
  }
  samples.put(FarmPreset.P+"harvest",new float[]{1});samples.put(FarmPreset.P+"harvest"+AdaptiveLayout.BOUNDS,harvest.bounds.clone());layout.status="固定ヘルプと収穫する文字を確認・通常連打を継続";
  if(ref!=null&&box!=null&&VisualLocator.verify(w,h,pixels,ref,null,box,.012f,true)!=null)samples.put(FarmPreset.P+"fever-confirmed",new float[]{1});
 }
 static boolean mission(PhaseEngine.Watch watch){return watch.id.equals(UsagiPreset.PREFIX+"ミッション中央をタップ")||watch.id.equals(CoinPreset.P+"mission-button-tap")||watch.id.equals(ShoppingPreset.P+"mission-button-tap")||watch.id.equals(ShoppingPreset.P+"mission-shop-button-tap");}
 boolean ownsAction(PhaseEngine.Watch watch){return enabled&&mission(watch)&&targets.containsKey(app+":mission:reference");}
 float[] missionMask(String key){
  if(!UsagiPreset.APP.equals(app))return null;
  // The surrounding sky/field and rabbits change after every stage. Match the
  // fixed white pill's inner flame and letters, not that animated background.
  float[] mask=new float[256];for(int y=4;y<12;y++)for(int x=2;x<14;x++)mask[y*16+x]=1;return mask;
 }
 static boolean missionIdentity(float[] reference,float[] raw){return missionIdentity(reference,raw,null);}
 static boolean missionIdentity(float[] reference,float[] raw,float[] mask){int a=0,b=0,overlap=0;for(int i=0;i<256;i++){if(mask!=null&&mask[i]<.5f)continue;int j=i*3;boolean x=reference[j]>.4f&&reference[j]-reference[j+1]>.12f&&reference[j]-reference[j+2]>.10f,y=raw[j]>.4f&&raw[j]-raw[j+1]>.12f&&raw[j]-raw[j+2]>.10f;if(x)a++;if(y)b++;if(x&&y)overlap++;}return a>=3&&overlap>=a*.5f&&overlap/(float)Math.max(1,a+b-overlap)>=.25f;}
 static boolean missionInk(float[] raw,int x,int y){int j=(y*16+x)*3;float hi=Math.max(raw[j],Math.max(raw[j+1],raw[j+2])),lo=Math.min(raw[j],Math.min(raw[j+1],raw[j+2]));return hi<.75f&&hi-lo<.1f;}
 static boolean missionLetters(float[] reference,float[] raw){
  // Thin black strokes become grey in the 16x16 reference. One sample-cell
  // alignment tolerance handles reduced displays without accepting red labels.
  for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++){
   int a=0,b=0,overlap=0;for(int y=4;y<12;y++)for(int x=6;x<14;x++){
    boolean first=missionInk(reference,x,y),second=missionInk(raw,x+dx,y+dy);
    if(first)a++;if(second)b++;if(first&&second)overlap++;
   }
   if(a>=4&&overlap>=a*.5f&&overlap/(float)Math.max(1,a+b-overlap)>=.25f)return true;
  }
  return false;
 }
 void actionTargets(AdaptiveLayout layout,int w,int h,RegionSampler.Pixels pixels,long now,Map<String,float[]> samples){
  PhaseEngine.Phase phase=layout.engine.phases.get(layout.engine.current);if(phase==null)return;
  for(PhaseEngine.Watch watch:phase.watches)if(ownsAction(watch)){
   String key=app+":mission";float[] box=targets.get(key+":bounds"),touch=targets.get(key+":point"),reference=targets.get(key+":reference"),mask=missionMask(key),expected=AdaptiveLayout.map(box,layout.transform);java.util.function.Predicate<float[]> identity=raw->missionIdentity(reference,raw,mask)&&(!UsagiPreset.APP.equals(app)||missionLetters(reference,raw));VisualLocator.Match match=VisualLocator.verify(w,h,pixels,reference,mask,box,.012f,false,identity);
   if(match==null&&UsagiPreset.APP.equals(app)){VisualLocator.Match near=VisualLocator.nearby(w,h,pixels,reference,mask,expected,.012f);if(near!=null&&identity.test(near.raw))match=near;}
   // Preserve the discriminative outline when a control moves independently.
   // Both global searches still require the inner flame and letter identity.
   if(match==null&&UsagiPreset.APP.equals(app))match=locate(key+":outline",reference,null,expected,.012f,w,h,pixels,now,false,identity);
   if(match==null)match=locate(key,reference,mask,expected,.012f,w,h,pixels,now,false,identity);
   if(match!=null&&identity.test(match.raw))samples.put(watch.id+ACTION_POINT,new float[]{(match.bounds[0]+touch[0]*(match.bounds[2]-match.bounds[0]))*w,(match.bounds[1]+touch[1]*(match.bounds[3]-match.bounds[1]))*h});
  }
 }
}
