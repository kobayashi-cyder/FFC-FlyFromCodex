package jp.tapmarker;
import java.util.*;

/** Two unobstructed landmarks, bounded layouts, and a point from the same frame. */
final class ShoppingControlEvidence {
 static final String PREFIX="shopping-control-6717-";
 static final float[] HOME_LABEL={.159f,.777f,.266f,.794f},REWARD_LABEL={.42f,.547f,.585f,.568f},MISSION_LABEL={.776f,.398f,.861f,.416f};
 static final class Control {
  final String key;final float[][] anchors,references;final float[] touch;
  Control(String key,List<float[]> refs){this.key=key;anchors=anchors(key);references=refs.toArray(new float[0][]);touch=key.equals("home")?ShoppingPreset.HOME_BUTTON:key.equals("resume")?ShoppingResumePolicy.LABEL:key.equals("reward")?REWARD_LABEL:anchors[1];}
 }
 final Map<String,Control> controls=new HashMap<>();private final Map<String,float[]> cached=new HashMap<>();private final Map<String,Long> searched=new HashMap<>();private int width,height;
 ShoppingControlEvidence(Map<String,float[]> refs){for(String key:new String[]{"home","reward","resume","boost","get","play"}){List<float[]> found=new ArrayList<>();for(String name:new TreeSet<>(refs.keySet()))if(name.startsWith(PREFIX+key+"-")){float[] ref=refs.get(name);if(ref.length!=1536)throw new IllegalArgumentException("買い物うさぎの操作見本");found.add(ref);}if(!found.isEmpty())controls.put(key.equals("resume")?ShoppingResumePolicy.REGION:ShoppingPreset.P+key,new Control(key,found));}}
 static float[][] anchors(String key){
  if(key.equals("home"))return new float[][]{ShoppingPreset.HOME_TITLE,HOME_LABEL};
  if(key.equals("resume"))return new float[][]{{.318f,.551f,.690f,.589f},ShoppingResumePolicy.LABEL};
  if(key.equals("reward"))return new float[][]{{.28f,.45f,.435f,.485f},REWARD_LABEL};
  if(key.equals("boost"))return new float[][]{{.235f,.523f,.780f,.572f},ShoppingPreset.anchors("boost")[1]};
  if(key.equals("get")||key.equals("play"))return new float[][]{ShoppingPreset.MISSION_TITLE,MISSION_LABEL};
  throw new IllegalArgumentException(key);
 }
 static float score(Control control,float[] sample){float best=Float.POSITIVE_INFINITY;if(sample!=null)for(float[] reference:control.references)best=Math.min(best,PhaseEngine.blockDistance(reference,sample));return best;}
 boolean get(int w,int h,RegionSampler.Pixels pixels,float[] predicted,Map<String,float[]> output){
  String id=ShoppingPreset.P+"get";Control control=controls.get(id);if(control==null)return false;
  for(float[] t:new float[][]{predicted,new float[]{1,1,0,0},new float[]{1,AdaptiveLayout.NORMAL_Y,0,AdaptiveLayout.NORMAL_TOP}})for(float[] reference:control.references){
   PhaseEngine.Watch watch=new PhaseEngine.Watch();watch.reference=reference;watch.pairedReference=true;watch.runtimePairedReferences=control.references;watch.threshold=.015f;
   MissionGetDetector.Found found=MissionGetDetector.find(w,h,new float[]{.68f,.37f,.97f,.81f},t,pixels,watch,control.anchors[0],control.anchors[1],.87f);
   if(found!=null){output.put(id,new float[]{1});output.put(id+AdaptiveLayout.BOUNDS,found.bounds);return true;}
  }
  return false;
 }
 float[] locate(String id,int w,int h,RegionSampler.Pixels pixels,long now,float[] predicted,Map<String,float[]> output){
  Control control=controls.get(id);if(control==null)return null;
  if(width!=w||height!=h){width=w;height=h;cached.clear();searched.clear();}
  float[] best=cached.getOrDefault(id,predicted).clone();float distance=score(control,AdaptiveLayout.paired(w,h,control.anchors,best,pixels));
  for(float[] t:new float[][]{predicted,new float[]{1,1,0,0},new float[]{1,AdaptiveLayout.NORMAL_Y,0,AdaptiveLayout.NORMAL_TOP}}){float d=score(control,AdaptiveLayout.paired(w,h,control.anchors,t,pixels));if(d<distance){distance=d;best=t.clone();}}
  if(distance>.015f&&now-searched.getOrDefault(id,-1000L)>=1000){
   searched.put(id,now);
   if(control.key.equals("home")){
    for(float sx:new float[]{.94f,1})for(float sy:new float[]{.86f,.90f,.94f,1,1.04f})for(float dy:new float[]{-.02f,0,.02f,.04f,.08f}){float[] t={sx,sy,(1-sx)/2,(1-sy)/2+dy};float d=score(control,AdaptiveLayout.paired(w,h,control.anchors,t,pixels));if(d<distance){distance=d;best=t;}}
   }else{
    for(float scale:new float[]{.54f,.56f,.58f,.60f,.64f,.68f,.72f,.76f,.80f,.84f,.88f,.92f,.96f,1,1.04f})for(float stretch:new float[]{-.02f,0,.02f})for(float dy:new float[]{-.02f,-.01f,0,.01f,.02f}){float sy=scale+stretch;float[] t={scale,sy,(1-scale)/2,(1-sy)/2+dy};float d=score(control,AdaptiveLayout.paired(w,h,control.anchors,t,pixels));if(d<distance){distance=d;best=t;}}
   }
   for(int pass=0;pass<4;pass++)for(int axis=0;axis<4;axis++)for(int sign:new int[]{-1,1}){float[] t=best.clone();t[axis]+=sign*(axis<2?.02f:.004f)/(1<<pass);if(t[0]<.5f||t[0]>1.10f||t[1]<.5f||t[1]>1.15f||Math.abs(t[2])>.27f||Math.abs(t[3])>.29f)continue;float d=score(control,AdaptiveLayout.paired(w,h,control.anchors,t,pixels));if(d<distance){distance=d;best=t;}}
  }
  if(distance>.015f)return null;
  cached.put(id,best.clone());output.put(id,new float[]{1});output.put(id+AdaptiveLayout.BOUNDS,AdaptiveLayout.map(control.touch,best));return best;
 }
}
