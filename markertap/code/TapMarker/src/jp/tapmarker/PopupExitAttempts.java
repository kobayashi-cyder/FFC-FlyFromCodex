package jp.tapmarker;
import java.util.*;

/** A missing detection does not establish a new advertisement or replenish its input budget. */
final class PopupExitAttempts {
 static final long COOLDOWN_MS=800;
 static final int MAX_ATTEMPTS=2;
 String id="";float x,y;int count,missing;long lastFrame=-1,sentAt=-1,missingSince=-1;boolean visible;
 void reset(){id="";x=y=0;count=missing=0;lastFrame=sentAt=missingSince=-1;visible=false;}
 void filter(Map<String,float[]> samples,int w,int h,long capturedAt,long now){
  String current="";float[] point=null;
  for(String key:samples.keySet())if(key.endsWith("-popup-close"+GamePopupEvidence.POINT)){
   String candidate=key.substring(0,key.length()-GamePopupEvidence.POINT.length());float[] p=GamePopupEvidence.point(candidate,samples,w,h);if(p!=null&&(samples.containsKey(GamePopupEvidence.FOOTER_EXIT)||p[1]>=h*.94f||candidate.equals(id))){current=candidate;point=p;break;}
  }
  if(capturedAt>lastFrame&&capturedAt<=now&&now-capturedAt<1500){
   if(point!=null){
    id=current;x=point[0]/w;y=point[1]/h;
    visible=true;missing=0;missingSince=-1;
   }else if(!samples.containsKey(GamePopupEvidence.FOOTER_EXIT)){
    visible=false;if(missingSince<0)missingSince=capturedAt;missing++;
   }
   lastFrame=capturedAt;
  }
  if(point!=null&&!id.isEmpty()&&(count>=MAX_ATTEMPTS||sentAt>=0&&now-sentAt<COOLDOWN_MS)){
   samples.remove(id);samples.remove(id+GamePopupEvidence.POINT);
  }
 }
 void sent(String region,long now){if(visible&&id.equals(region)&&count<MAX_ATTEMPTS){count++;sentAt=now;}}
 boolean failed(long now){return visible&&count>=MAX_ATTEMPTS&&sentAt>=0&&now>=sentAt+COOLDOWN_MS&&lastFrame<=now&&now-lastFrame<1500;}
 String report(){return "下端の閉じる "+count+"/"+MAX_ATTEMPTS+" / 表示 "+visible+" / 検出なし "+missing+"回 / 回数解除は主画面確認後";}
}
