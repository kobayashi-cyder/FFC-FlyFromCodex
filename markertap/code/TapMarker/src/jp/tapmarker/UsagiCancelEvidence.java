package jp.tapmarker;
import java.util.*;

/** A current Use label and a separate Cancel label must both be visible. */
final class UsagiCancelEvidence {
 static final String PAGE=":usagi-cancel-page";
 final float[] use,cancel,useReference,cancelReference;
 UsagiCancelEvidence(Map<String,float[]> refs){
  use=refs.get("usagi-cancel:use:bounds");cancel=refs.get("usagi-cancel:cancel:bounds");
  useReference=refs.get("usagi-cancel:use:reference");cancelReference=refs.get("usagi-cancel:cancel:reference");
 }
 boolean enabled(){return use!=null&&cancel!=null&&useReference!=null&&useReference.length==768&&cancelReference!=null&&cancelReference.length==768;}
 void install(PhaseEngine e){if(!enabled())return;for(PhaseEngine.Phase phase:e.phases.values())for(PhaseEngine.Watch w:phase.watches)
  if(w.regionId.equals(UsagiPreset.PREFIX+"dialog")||w.regionId.equals(UsagiPreset.PREFIX+"cancel"))w.runtimeControlEvidence=true;
 }
 static VisualLocator.Match match(int w,int h,RegionSampler.Pixels p,float[] ref,float[] box){
  VisualLocator.Match found=VisualLocator.verify(w,h,p,ref,null,box,.006f);
  return found!=null?found:VisualLocator.nearby(w,h,p,ref,null,box,.006f);
 }
 boolean sample(AdaptiveLayout layout,int w,int h,RegionSampler.Pixels p,Map<String,float[]> out){
  if(!enabled())return false;
  for(float[] t:new float[][]{new float[]{1,1,0,0},layout.transform,new float[]{1,AdaptiveLayout.NORMAL_Y,0,AdaptiveLayout.NORMAL_TOP}}){
   VisualLocator.Match first=match(w,h,p,useReference,AdaptiveLayout.map(use,t));if(first==null)continue;
   float[] expected=AdaptiveLayout.map(use,t),adjusted=t.clone();
   adjusted[2]+=(first.bounds[0]+first.bounds[2]-expected[0]-expected[2])/2;
   adjusted[3]+=(first.bounds[1]+first.bounds[3]-expected[1]-expected[3])/2;
   VisualLocator.Match second=match(w,h,p,cancelReference,AdaptiveLayout.map(cancel,adjusted));if(second==null)continue;
   if(second.bounds[1]<=first.bounds[3]||second.bounds[3]-first.bounds[1]>.17f)continue;
   String prefix=UsagiPreset.PREFIX;
   out.put(PAGE,new float[]{1});out.put(prefix+"dialog",new float[]{1});out.put(prefix+"cancel",new float[]{1});
   out.put(prefix+"cancel"+AdaptiveLayout.BOUNDS,second.bounds.clone());out.put(prefix+"cancel"+ContentRelocator.LOCATED,new float[]{1});
   UsagiMissionEvidence.blockHome(out);
   for(String id:new String[]{AdCloseAutomaton.LEFT,AdCloseAutomaton.RIGHT})out.put(id,new float[]{0});
   layout.status="使用する・キャンセルの2つの文字を確認 / キャンセルへ";return true;
  }
  return false;
 }
}
