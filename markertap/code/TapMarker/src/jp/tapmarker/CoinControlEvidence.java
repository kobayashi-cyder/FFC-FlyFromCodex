package jp.tapmarker;
import java.util.*;

/** Two visible landmarks per dialog, outside the panel and marker in the supplied recording. */
final class CoinControlEvidence {
 static final String PREFIX="coin-control-",IDENTITY=":coin-identity";
 static final class Control {
  final float[][] anchors;final float[] touch;final float[][] references;
  Control(float[][] anchors,float[] touch,List<float[]> references){this.anchors=anchors;this.touch=touch;this.references=references.toArray(new float[references.size()][]);}
 }
 final Map<String,Control> controls=new HashMap<>();
 final Map<String,Control> identities=new HashMap<>();
 final Map<String,float[]> cached=new HashMap<>();final Map<String,Long> searched=new HashMap<>();int width,height;
 CoinControlEvidence(Map<String,float[]> evidence){
  for(String key:new String[]{"fever","clear","missions","bonus","reward","boost-rare","boost-pocket","boost-tap2","boost-pot","get","play"}){
   List<float[]> references=new ArrayList<>();
   for(String name:new TreeSet<>(evidence.keySet()))if(name.startsWith(PREFIX+key+"-")){float[] reference=evidence.get(name);if(reference.length!=1536)throw new IllegalArgumentException("コイン落としのボタン見本");references.add(reference);}
   if(!references.isEmpty())controls.put(CoinPreset.P+key,new Control(anchors(key),touch(key),references));
  }
  for(String key:new String[]{"boost-rare","boost-pocket"}){
   List<float[]> references=new ArrayList<>();for(String name:new TreeSet<>(evidence.keySet()))if(name.startsWith("coin-identity-"+key+"-")){float[] reference=evidence.get(name);if(reference.length!=1536)throw new IllegalArgumentException("コイン落としの強化種類の見本");references.add(reference);}
   if(!references.isEmpty())identities.put(CoinPreset.P+key,new Control(identityAnchors(key),touch(key),references));
  }
 }
 static float[][] identityAnchors(String key){return new float[][]{key.equals("boost-rare")?CoinPreset.TAP2_BODY:new float[]{.272f,.577f,.745f,.609f},new float[]{.34f,.40f,.68f,.54f}};}
 float[] identify(String id,int w,int h,RegionSampler.Pixels pixels,long now,float[] predicted,Map<String,float[]> output){
  return identify(id,w,h,pixels,now,predicted,output,true);
 }
 float[] identify(String id,int w,int h,RegionSampler.Pixels pixels,long now,float[] predicted,Map<String,float[]> output,boolean wide){
  float[] t=locate(identities.get(id),id+IDENTITY,w,h,pixels,now,predicted,output,wide);if(t==null)return null;
  output.remove(id+IDENTITY+AdaptiveLayout.BOUNDS);output.put(id+IDENTITY,new float[]{1});output.put(id,new float[]{1});output.put(id+AdaptiveLayout.BOUNDS,AdaptiveLayout.map(identities.get(id).touch,t));return t;
 }
 void disambiguate(Map<String,float[]> output,Set<String> factory){
  String found=null;for(String id:identities.keySet())if(output.containsKey(id+IDENTITY)){if(found!=null)return;found=id;}
  if(found==null)return;for(String key:CoinNoAdPolicy.BOOSTS){String id=CoinPreset.P+key;if(id.equals(found)||!factory.contains(id))continue;output.put(id,new float[]{0});output.remove(id+AdaptiveLayout.BOUNDS);}
 }
 static float[][] anchors(String key){
  switch(key){
   case "fever":return new float[][]{{.104f,.286f,.322f,.330f},{.442f,.560f,.555f,.578f}};
   case "clear":return new float[][]{{.350f,.585f,.650f,.630f},{.450f,.530f,.560f,.575f}};
   case "missions":return new float[][]{{.289f,.166f,.715f,.181f},CoinPreset.MISSION_BACK};
   case "bonus":return new float[][]{{.265f,.574f,.735f,.589f},{.595f,.775f,.675f,.797f}};
   case "reward":return new float[][]{{.179f,.350f,.824f,.368f},CoinPreset.CLOSE};
   case "boost-rare":return new float[][]{CoinPreset.TAP2_BODY,CoinPreset.RARE_CM};
   case "boost-pocket":return new float[][]{{.272f,.577f,.745f,.609f},CoinPreset.POCKET_CM};
   case "boost-tap2":return new float[][]{CoinPreset.TAP2_BODY,CoinPreset.TAP2_CM};
   case "boost-pot":return new float[][]{CoinPreset.POT_BODY,CoinPreset.POT_CANCEL_LABEL};
   case "get":return new float[][]{CoinPreset.MISSION_TITLE,new float[]{.759f,.400f,.864f,.418f}};
   case "play":return new float[][]{{.289f,.166f,.715f,.181f},CoinPreset.GET_LABEL};
   default:throw new IllegalArgumentException(key);
  }
 }
 // The centre of Start can be covered by the user's marker. Its rightmost
 // verified letters are still part of Start and remain below the CM button.
 static float[] touch(String key){return key.equals("bonus")?anchors("bonus")[1]:key.equals("fever")?CoinPreset.BUTTON:key.equals("clear")?CoinPreset.CLEAR_LOGO:key.equals("missions")?CoinPreset.MISSION_BACK:key.equals("reward")?CoinPreset.CLOSE:key.equals("boost-rare")?CoinPreset.RARE_CM:key.equals("boost-tap2")?CoinPreset.TAP2_CM:key.equals("boost-pot")?CoinPreset.POT_CANCEL:key.equals("get")?new float[]{.759f,.400f,.864f,.418f}:key.equals("play")?CoinPreset.GET_LABEL:CoinPreset.POCKET_CM;}
 static float[] sample(int w,int h,String key,float[] t,RegionSampler.Pixels pixels){return AdaptiveLayout.paired(w,h,anchors(key),t,pixels);}
 float score(Control control,float[] sample){float best=Float.POSITIVE_INFINITY;if(sample!=null)for(float[] reference:control.references)best=Math.min(best,PhaseEngine.blockDistance(reference,sample));return best;}
 float[] locate(String id,int w,int h,RegionSampler.Pixels pixels,long now,float[] predicted,Map<String,float[]> output){
  return locate(id,w,h,pixels,now,predicted,output,true);
 }
 float[] locate(String id,int w,int h,RegionSampler.Pixels pixels,long now,float[] predicted,Map<String,float[]> output,boolean wide){
  return locate(controls.get(id),id,w,h,pixels,now,predicted,output,wide);
 }
 float[] locate(Control control,String id,int w,int h,RegionSampler.Pixels pixels,long now,float[] predicted,Map<String,float[]> output){
  return locate(control,id,w,h,pixels,now,predicted,output,true);
 }
 float[] locate(Control control,String id,int w,int h,RegionSampler.Pixels pixels,long now,float[] predicted,Map<String,float[]> output,boolean wide){
  if(control==null)return null;
  if(width!=w||height!=h){width=w;height=h;cached.clear();searched.clear();}
  float[] best=cached.getOrDefault(id,predicted).clone();float distance=score(control,AdaptiveLayout.paired(w,h,control.anchors,best,pixels));
  for(float[] candidate:new float[][]{predicted,new float[]{1,1,0,0},new float[]{1,AdaptiveLayout.NORMAL_Y,0,AdaptiveLayout.NORMAL_TOP}}){float d=score(control,AdaptiveLayout.paired(w,h,control.anchors,candidate,pixels));if(d<distance){distance=d;best=candidate.clone();}}
  if(wide&&distance>.015f&&now-searched.getOrDefault(id,-1000L)>=1000){
   searched.put(id,now);
   for(float sx:new float[]{.94f,1})for(float sy:new float[]{.86f,.90f,.94f,.98f,1,1.04f})for(float dy:new float[]{-.04f,-.02f,-.01f,0,.01f,.02f,.04f}){float[] t={sx,sy,(1-sx)/2,(1-sy)/2+dy};float d=score(control,AdaptiveLayout.paired(w,h,control.anchors,t,pixels));if(d<distance){distance=d;best=t;}}
   for(int pass=0;pass<4;pass++)for(int axis=0;axis<4;axis++)for(int sign:new int[]{-1,1}){float[] t=best.clone();t[axis]+=sign*(axis<2?.02f:.004f)/(1<<pass);if(t[0]<.85f||t[0]>1.10f||t[1]<.8f||t[1]>1.15f||Math.abs(t[2])>.1f||Math.abs(t[3])>.15f)continue;float d=score(control,AdaptiveLayout.paired(w,h,control.anchors,t,pixels));if(d<distance){distance=d;best=t;}}
  }
  if(distance>.015f)return null;
  cached.put(id,best.clone());output.put(id,new float[]{1});output.put(id+AdaptiveLayout.BOUNDS,AdaptiveLayout.map(control.touch,best));return best;
 }
}
