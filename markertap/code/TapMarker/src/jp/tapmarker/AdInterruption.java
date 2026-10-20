package jp.tapmarker;
import java.util.Map;
import java.util.Arrays;
/** Unknown content must persist across fresh frames before treating it as an interruption.
 * This moves to observation of ad exits; it never authorizes an unobserved tap. */
public final class AdInterruption {
 private String state="";private long epoch=-1,since=-1,last=-1;private int count;
 public void reset(){state="";epoch=-1;since=-1;last=-1;count=0;}
 static String prefix(String state){for(String app:PresetRegistry.APPS){String p=PresetRegistry.prefix(app);if(state.startsWith(p))return p;}return "";}
 static String recoveryState(PhaseEngine e){return recoveryState(e.current);}
 static String recoveryState(String state){
  if(AdExitPolicy.adState(state))return state;
  String p=prefix(state);if(p.isEmpty())return "";
  if(p.equals(CoinPreset.P)&&CoinPreset.missionPending(state))return p+"mission-ad";
  if(p.equals(PoipoiPreset.P))return PoipoiPreset.recoveryState(state);
  if(p.equals(ShoppingPreset.P)&&ShoppingPreset.missionPending(state))return p+"mission-ad";
  return p+"ad";
 }
 public boolean observe(PhaseEngine e,Map<String,float[]> samples,long capturedAt){return observe(e,samples,capturedAt,capturedAt);}
 public boolean observe(PhaseEngine e,Map<String,float[]> samples,long capturedAt,long now){
  if(!state.equals(e.current)||epoch!=e.epoch){reset();state=e.current;epoch=e.epoch;}
  if(capturedAt<0||capturedAt>now||now-capturedAt>=ContinuousTapPolicy.MAX_FRAME_AGE_MS){since=-1;count=0;return false;}
  if(capturedAt<=last)return false;last=capturedAt;
  PhaseEngine.Phase p=e.phases.get(e.current);
  String prefix=prefix(e.current),name=prefix.isEmpty()?"":e.current.substring(prefix.length());
  if(e.windowRelative||p==null||p.terminal||prefix.isEmpty()||AdExitPolicy.adState(e.current)||Arrays.asList("verify","mission-verify","classify","check","external","account","error","unknown").contains(name)){since=-1;count=0;return false;}
  boolean measurable=false,known=false;
  for(PhaseEngine.Watch w:p.watches){if(!e.eligible(w)||w.reference==null||AdCloseAutomaton.handles(w.regionId))continue;if(UsagiPreset.isMain(e.current)&&!w.regionId.equals(UsagiPreset.PREFIX+"game"))continue;float[] sample=samples.get(w.regionId);if(sample==null)continue;measurable=true;if(w.distance(sample)<=w.threshold&&e.guardsMatch(w,samples)){known=true;break;}}
  if(!measurable||known){since=-1;count=0;return false;}
  if(since<0)since=capturedAt;count++;return count>=3&&capturedAt-since>=2500;
 }
}
