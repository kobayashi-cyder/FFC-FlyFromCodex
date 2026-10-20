package jp.tapmarker;
import java.util.*;

/** A measured Close must settle; dispatch is followed by fresh disappearance evidence. */
final class PopupActionConfirmation {
 static final int LIMIT=2;static final long STABLE_MS=200,RETRY_MS=800;
 private String id="",candidate="";private float x,y;private int width,height,stableFrames,missingFrames,attempts;
 private long frameAt=-1,stableSince=-1,sentAt=-1,missingSince=-1;
 private boolean visible,pending,disappeared;
 static boolean close(String id){return id.endsWith("-popup-close")||id.equals(FarmPreset.P+"close")||id.equals(PoipoiPreset.P+"reward");}
 void filter(Map<String,float[]> samples,int w,int h,long at,long now){
  String found="";float[] point=null;
  for(String key:samples.keySet())if(key.endsWith(GamePopupEvidence.POINT)){
   String region=key.substring(0,key.length()-GamePopupEvidence.POINT.length());
   if(close(region)&&(point=GamePopupEvidence.point(region,samples,w,h))!=null){found=region;break;}
  }
  if(at>=0&&at<=now&&now-at<1500&&at>frameAt){
   frameAt=at;visible=point!=null;
   if(point!=null){
    if(!found.equals(candidate)||w!=width||h!=height||Math.hypot(point[0]-x,point[1]-y)>Math.max(2,w*.01f)){
     candidate=found;stableSince=at;stableFrames=0;
    }
    x=point[0];y=point[1];width=w;height=h;stableFrames++;
    missingFrames=0;missingSince=-1;disappeared=false;
   }else{
    candidate="";stableFrames=0;stableSince=-1;
    if(pending&&at>sentAt){if(missingSince<0)missingSince=at;if(++missingFrames>=2&&at-missingSince>=350){pending=false;disappeared=true;}}
   }
  }
  if(point==null)return;
  // Footer controls retain their existing episode budget and sampling behavior.
  boolean footer=samples.containsKey(GamePopupEvidence.FOOTER_EXIT)||point[1]>=h*.94f;
  if(!footer&&(stableFrames<2||stableSince<0||at-stableSince<STABLE_MS)||attempts>=LIMIT||pending&&(at<=sentAt||now-sentAt<RETRY_MS)){
   samples.remove(found);samples.remove(found+GamePopupEvidence.POINT);
  }
 }
 void sent(String region,long now){if(close(region)&&visible&&candidate.equals(region)&&attempts<LIMIT){id=region;attempts++;sentAt=now;pending=true;disappeared=false;missingFrames=0;missingSince=-1;}}
 boolean failed(long now){return pending&&visible&&attempts>=LIMIT&&sentAt>=0&&now-sentAt>=RETRY_MS&&frameAt>sentAt&&frameAt<=now&&now-frameAt<1500;}
 void reset(){id=candidate="";x=y=0;width=height=stableFrames=missingFrames=attempts=0;frameAt=stableSince=sentAt=missingSince=-1;visible=pending=disappeared=false;}
 String report(){return "閉じる操作 "+attempts+"/"+LIMIT+" / "+(disappeared?"消失を確認・次画面を判定":pending?visible?"入力後も表示あり・新しい位置で再確認":"入力後の消失を確認中":visible?"位置の安定を確認中":"未操作")+(id.isEmpty()?"":" / "+id);}
}
