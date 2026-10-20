package jp.tapmarker;
import java.util.*;

/** Pixel evidence is recorded separately from the state-dependent input permit. */
final class RecognitionDiagnostics {
 static String describe(PhaseEngine e,Map<String,float[]> samples){
  PhaseEngine.Phase phase=e.phases.get(e.current);if(phase==null)return "状態なし";
  StringBuilder out=new StringBuilder();
  if(e.current.startsWith(CoinPreset.P))for(String key:new String[]{"home","fever"}){
   PhaseEngine.Watch w=e.rule(CoinPreset.P+"verify-"+key);if(w==null)continue;
   if(out.length()>0)out.append(" / ");out.append(key).append('：').append(distance(w,samples));
  }
  PhaseEngine.Watch best=null;float ratio=Float.POSITIVE_INFINITY;
  for(PhaseEngine.Watch w:phase.watches){
   if(!e.eligible(w)||w.reference==null||AdCloseAutomaton.handles(w.regionId)||(w.condition!=PhaseEngine.MATCH&&w.condition!=PhaseEngine.DISAPPEAR)||samples.get(w.regionId)==null)continue;
   float d=w.distance(samples.get(w.regionId)),r=w.threshold>0?d/w.threshold:d==0?0:Float.POSITIVE_INFINITY;
   if(r<ratio){ratio=r;best=w;}
  }
  if(out.length()>0)out.append(" / ");return out.append(best==null?"有効な画像候補なし":"最寄り "+best.id+"："+distance(best,samples)).toString();
 }
 static String distance(PhaseEngine.Watch w,Map<String,float[]> samples){
  float[] raw=samples.get(w.regionId);boolean probe=raw==null&&samples.get(GamePopupEvidence.HOME_PROBE+w.regionId)!=null;if(probe)raw=samples.get(GamePopupEvidence.HOME_PROBE+w.regionId);if(raw==null)return "未取得";
  float d=w.distance(raw);return (probe?"独立確認・操作保留：":"")+(d<=w.threshold?"一致":"不一致")+String.format(Locale.ROOT,"（距離 %.4f / 閾値 %.4f / ガード %s）",d,w.threshold,w.guardIds.length==0?"なし":"別途確認");
 }
 private RecognitionDiagnostics(){}
}
