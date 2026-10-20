package jp.tapmarker;
import java.util.*;

/** Two fresh positive ad observations authorize HOME; an unknown screen alone never does. */
final class AdHomeRecovery {
 static final String EVIDENCE=":coin-ad-home-evidence";
 private PhaseEngine owner;private long epoch=-1,first=-1,last=-1;private int width,height;
 void reset(){owner=null;epoch=-1;first=last=-1;width=height=0;}
 static boolean eligible(PhaseEngine e){
  if(e.windowRelative)return false;
  for(String suffix:new String[]{"check","ad","verify","mission-ad","mission-verify","claim-wait"})if(e.current.equals(PoipoiPreset.P+suffix))return true;
  for(String suffix:new String[]{"check","ad","verify"})if(e.current.equals(BoxPreset.P+suffix))return true;
  for(String suffix:new String[]{"check","ad","verify","reward-ad","mission-ad"})if(e.current.equals(CoinPreset.P+suffix))return true;
  return false;
 }
 static boolean visible(Map<String,float[]> samples,int w,int h){float[] v=samples.get(EVIDENCE);return v!=null&&v.length==3&&v[0]==1&&v[1]==w&&v[2]==h;}
 boolean observe(PhaseEngine e,Map<String,float[]> samples,int w,int h,long capturedAt,long now){
  if(!eligible(e)||AdExitPolicy.gameVisible(e,samples)||!visible(samples,w,h)||capturedAt<0||now<capturedAt||now-capturedAt>750){reset();return false;}
  if(owner!=e||epoch!=e.epoch||width!=w||height!=h||last<0||capturedAt<last||capturedAt-last>1000){owner=e;epoch=e.epoch;width=w;height=h;first=last=capturedAt;return false;}
  if(capturedAt==last)return false;
  last=capturedAt;return capturedAt-first>=300;
 }
}
