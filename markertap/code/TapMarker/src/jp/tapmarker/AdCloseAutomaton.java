package jp.tapmarker;
import java.util.*;

/** Sparse corner checks in nonprotected factory states; completion requires game evidence. */
final class AdCloseAutomaton {
 static final String LEFT="virtual-ad-close-left",RIGHT="virtual-ad-close-right",POINT=":point",CONTROL_POINT=":ad-control-point",CORNER_TOP=":ad-corner-top",PIXEL_CANDIDATES=":ad-pixel-candidates",NAMED_CANDIDATES=":ad-named-candidates";
 static boolean handles(String id){return LEFT.equals(id)||RIGHT.equals(id);}
 static boolean ownsNamed(PhaseEngine e,String label,float[] bounds,Map<String,float[]> samples){
  if(e.windowRelative)return false;float top=0;float[] v=samples.get(AdaptiveLayout.VIEWPORT),raw=samples.get(CORNER_TOP);if(v!=null&&v.length==6)top=cornerTop(samples,(int)v[4],(int)v[5]);else if(raw!=null&&raw.length==1)top=raw[0];
  String id=AdExitPolicy.corner(label,bounds,top);if(!handles(id))return false;for(PhaseEngine.Region r:e.activeRegions())if(r.id.equals(id))return true;return false;
 }
 static void install(PhaseEngine engine,String app){
  boolean usagi=UsagiPreset.APP.equals(app),farm=FarmPreset.APP.equals(app),coin=CoinPreset.APP.equals(app),shopping=ShoppingPreset.APP.equals(app),box=BoxPreset.APP.equals(app),poipoi=PoipoiPreset.APP.equals(app);if(!usagi&&!farm&&!coin&&!shopping&&!box&&!poipoi)return;
  if(engine.windowRelative)return;
  String prefix=PresetRegistry.prefix(app);
  float cornerWidth=coin||shopping||box||poipoi?.13f:.10f;
  for(String id:new String[]{LEFT,RIGHT}){if(engine.regions.containsKey(id))continue;PhaseEngine.Region r=new PhaseEngine.Region();r.id=id;r.name=id.equals(LEFT)?"広告の左上の閉じる・スキップ印":"広告の右上の閉じる・スキップ印";r.bounds=id.equals(LEFT)?new float[]{0,0,cornerWidth,.14f}:new float[]{1-cornerWidth,0,1,.14f};engine.regions.put(id,r);}
  for(PhaseEngine.Phase phase:engine.phases.values()){
   String state=phase.id.startsWith(prefix)?phase.id.substring(prefix.length()):"";
   if(state.isEmpty()||phase.terminal||Arrays.asList("account","error","unknown","external").contains(state))continue;
   for(String id:new String[]{LEFT,RIGHT}){PhaseEngine.Watch rule=new PhaseEngine.Watch();rule.id=phase.id+"-"+id;if(engine.rule(rule.id)!=null)continue;rule.name=engine.regions.get(id).name+"を確認して閉じる";rule.regionId=id;rule.reference=new float[]{1};rule.threshold=0;rule.condition=PhaseEngine.MATCH;rule.action=PhaseEngine.CENTER;rule.nextPhase=AdInterruption.recoveryState(phase.id);rule.waitMs=0;phase.watches.add(rule);}
  }
  AdExitPolicy.install(engine);
 }
 static float[] corner(PhaseEngine.Region region,int w,int h,RegionSampler.Pixels pixels){return AdCornerViewport.band(region.bounds,w,h,pixels);}
 static float[] find(int w,int h,float[] corner,RegionSampler.Pixels pixels){float[] point=AdCloseDetector.find(w,h,corner,pixels);return point==null?AdSkipGlyphDetector.find(w,h,corner,pixels):point;}
 static void sample(PhaseEngine.Region region,int width,int height,RegionSampler.Pixels pixels,Map<String,float[]> samples){float[] point=find(width,height,corner(region,width,height,pixels),pixels);samples.put(CORNER_TOP,new float[]{AdCornerViewport.top(width,height,pixels)/(float)height});samples.put(region.id,new float[]{point==null?0:1});samples.remove(region.id+POINT);if(point!=null)samples.put(region.id+POINT,point);word(region.id,width,height,pixels,samples);}
 static void word(String id,int width,int height,RegionSampler.Pixels pixels,Map<String,float[]> samples){
  sheet(id,width,height,pixels,samples);
  List<float[]> words=new ArrayList<>(AdCloseWordDetector.find(width,height,LEFT.equals(id),pixels));words.addAll(AdPlainArrowDetector.find(width,height,LEFT.equals(id),pixels));if(words.isEmpty())return;
  float[] original=samples.get(id+POINT);if(original!=null)append(samples,id+PIXEL_CANDIDATES,original[0],original[1],width,height);
  for(float[] p:words){append(samples,id+PIXEL_CANDIDATES,p[0],p[1],width,height);if(original==null){samples.put(id+POINT,p);original=p;}}
  samples.put(id,new float[]{1});
 }
 static void sheet(String id,int width,int height,RegionSampler.Pixels pixels,Map<String,float[]> samples){
  if(!RIGHT.equals(id))return;float[] point=PlayStoreSheetEvidence.locate(width,height,pixels);if(point==null)return;
  float[] original=samples.get(id+POINT);if(original!=null)append(samples,id+PIXEL_CANDIDATES,original[0],original[1],width,height);
  append(samples,id+PIXEL_CANDIDATES,point[0],point[1],width,height);samples.put(id,new float[]{1});if(original==null)samples.put(id+POINT,point);
 }
 static void append(Map<String,float[]> samples,String key,float x,float y,int width,int height){float[] old=samples.get(key);if(old==null)old=new float[0];for(int i=0;i<old.length;i+=4)if(Math.abs(old[i]-x)+Math.abs(old[i+1]-y)<5&&old[i+2]==width&&old[i+3]==height)return;if(old.length>=32)return;float[] all=Arrays.copyOf(old,old.length+4);all[old.length]=x;all[old.length+1]=y;all[old.length+2]=width;all[old.length+3]=height;samples.put(key,all);}
 static boolean addNamed(Map<String,float[]> samples,String id,float x,float y,int width,int height){if(!handles(id)||!Float.isFinite(x)||!Float.isFinite(y)||x<0||y<0||x>=width||y>=height)return false;append(samples,id+NAMED_CANDIDATES,x,y,width,height);samples.put(id,new float[]{1});if(!samples.containsKey(id+CONTROL_POINT))putControl(samples,id,x,y,width,height);return true;}
 static List<float[]> candidates(String id,Map<String,float[]> samples,int width,int height){
  List<float[]> result=new ArrayList<>();float[] flag=samples.get(id);if(flag==null||flag.length!=1||flag[0]!=1)return result;
  float[] v=samples.get(AdaptiveLayout.VIEWPORT);
  for(String suffix:new String[]{NAMED_CANDIDATES,PIXEL_CANDIDATES}){float[] packed=samples.get(id+suffix);if(packed==null||packed.length%4!=0)continue;for(int i=0;i<packed.length;i+=4){float[] p={packed[i],packed[i+1]};if(suffix.equals(PIXEL_CANDIDATES)&&v!=null)p=ContentViewport.offset(p,v,width,height);else if(packed[i+2]!=width||packed[i+3]!=height)continue;addPoint(result,p,width,height);}}
  addPoint(result,controlPoint(id,samples,width,height),width,height);
  float[] point=samples.get(id+POINT);if(point!=null){if(v!=null)point=ContentViewport.offset(point,v,width,height);addPoint(result,point,width,height);}return result;
 }
 static void addPoint(List<float[]> list,float[] p,int width,int height){if(p==null||p.length!=2||!Float.isFinite(p[0])||!Float.isFinite(p[1])||p[0]<0||p[1]<0||p[0]>=width||p[1]>=height)return;for(float[] old:list)if(Math.hypot(p[0]-old[0],p[1]-old[1])<5)return;list.add(p.clone());}
 static float cornerTop(Map<String,float[]> samples,int w,int h){float[] top=samples.get(CORNER_TOP);if(top==null||top.length!=1)return 0;float[] v=samples.get(AdaptiveLayout.VIEWPORT);return v!=null&&v.length==6&&v[4]==w&&v[5]==h?(v[1]+top[0]*v[3])/h:top[0];}
 static boolean putControl(Map<String,float[]> samples,String id,float x,float y,int w,int h){if(!handles(id)||!Float.isFinite(x)||!Float.isFinite(y)||x<0||y<0||x>=w||y>=h)return false;samples.put(id,new float[]{1});samples.put(id+CONTROL_POINT,new float[]{x,y,w,h});return true;}
 static float[] controlPoint(String id,Map<String,float[]> samples,int w,int h){float[] p=samples.get(id+CONTROL_POINT),v=samples.get(id);if(p==null||p.length!=4||p[2]!=w||p[3]!=h||v==null||v.length!=1||v[0]!=1||!Float.isFinite(p[0])||!Float.isFinite(p[1])||p[0]<0||p[1]<0||p[0]>=w||p[1]>=h)return null;return new float[]{p[0],p[1]};}
}
