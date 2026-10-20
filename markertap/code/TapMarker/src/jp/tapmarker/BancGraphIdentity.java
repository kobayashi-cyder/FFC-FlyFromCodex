package jp.tapmarker;
import java.security.MessageDigest;import java.util.*;

/** Memory is invalidated when any executable graph contract changes. */
public final class BancGraphIdentity {
 public static String of(String scope,PhaseEngine e){
  try{MessageDigest digest=MessageDigest.getInstance("SHA-256");put(digest,"BANC888-native-1|"+scope+"|"+e.windowRelative+"|"+e.initial+"|"+e.recordingReviewRequired);
   for(PhaseEngine.Region r:e.regions.values()){put(digest,r.id+"|"+r.windowLocal);vector(digest,r.bounds);}
   for(PhaseEngine.Phase p:e.phases.values()){put(digest,p.id+"|"+p.terminal+"|"+p.markersDuring+"|"+p.learnedDuring);for(PhaseEngine.Watch w:p.watches){put(digest,w.id+"|"+w.condition+"|"+w.action+"|"+w.regionId+"|"+w.nextPhase+"|"+w.historyRule+"|"+w.historyPhase+"|"+w.threshold+"|"+w.timeoutMs+"|"+w.pairedReference+"|"+w.priority+"|"+w.stableMs+"|"+w.waitMs+"|"+w.duration+"|"+w.fallback+"|"+w.relativeCurve);vector(digest,w.reference);vector(digest,w.touchPoint);vector(digest,w.curve);vector(digest,w.markerPoints);for(String id:w.markerIds)put(digest,id);for(int i=0;i<w.guardIds.length;i++){put(digest,w.guardIds[i]);if(i<w.guardReferences.length)vector(digest,w.guardReferences[i]);}}}
   StringBuilder result=new StringBuilder();for(byte value:digest.digest())result.append(String.format(Locale.ROOT,"%02x",value&255));return result.toString();
  }catch(Exception unavailable){throw new IllegalStateException("学習記憶の対象を識別できません",unavailable);}
 }
 static void put(MessageDigest d,String s){try{d.update(s.getBytes("UTF-8"));d.update((byte)0);}catch(Exception impossible){throw new IllegalStateException(impossible);}}
 static void vector(MessageDigest d,float[] values){if(values==null){d.update((byte)0xff);return;}int length=values.length;for(int shift=24;shift>=0;shift-=8)d.update((byte)(length>>>shift));for(float value:values){int bits=Float.floatToIntBits(value);for(int shift=24;shift>=0;shift-=8)d.update((byte)(bits>>>shift));}}
}
