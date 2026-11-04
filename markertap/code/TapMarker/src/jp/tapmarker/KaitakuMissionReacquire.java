package jp.tapmarker;
/** Two distinct, fresh mission frames are required before a visual-only re-sync.
 * Does not issue gestures, infer rewards, or replace screenshot safety checks.
 */
final class KaitakuMissionReacquire {
 static final long MAX_FRAME_AGE_MS=1500,MIN_SPACING_MS=150,MAX_SPACING_MS=2500;
 private String state="";private long firstAt=-1,lastAt=-1;
 void reset(){state="";firstAt=lastAt=-1;}
 boolean observe(String phase,boolean gameForeground,boolean missionEvidence,
       boolean homeEvidence,boolean advertisement,boolean unknownOverlay,long capturedAt,long now){
  if(!("kaitaku-default-mission-opening".equals(phase)||"kaitaku-default-verify".equals(phase))||
   !gameForeground||!missionEvidence||homeEvidence||advertisement||unknownOverlay||
   capturedAt<=0||now<capturedAt||now-capturedAt>=MAX_FRAME_AGE_MS){reset();return false;}
  if(!phase.equals(state)||capturedAt<=lastAt||lastAt<0||
     capturedAt-lastAt>MAX_SPACING_MS||now<lastAt){
   state=phase;firstAt=lastAt=capturedAt;return false;
  }
  long spacing=capturedAt-lastAt;lastAt=capturedAt;
  if(spacing<MIN_SPACING_MS||capturedAt-firstAt<MIN_SPACING_MS)return false;
  reset();return true;
 }
}
