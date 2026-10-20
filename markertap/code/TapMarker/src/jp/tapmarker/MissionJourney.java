package jp.tapmarker;
/** Keep the mission task across classification, advertisement recovery and popups. */
public final class MissionJourney {
 private boolean pending,opened,playCompleted;
 public void reset(){pending=false;opened=false;playCompleted=false;}
 public boolean blocksMain(){return pending;}
 public void completedPlay(){if(pending&&opened)playCompleted=true;}
 public String route(String next){
  String p=UsagiPreset.PREFIX;
  if(next.equals(p+"clear")||next.equals(p+"bonus")||next.equals(p+"missions")){
   if(!pending){pending=true;opened=false;playCompleted=false;}
   if(next.equals(p+"missions"))opened=true;
  }
  if(!pending&&(next.equals(p+"open-missions")||next.equals(p+"mission-tap")||next.equals(p+"mission-opening")))return p+"verify";
  if(next.equals(p+"main")&&pending){
   if(playCompleted){reset();return next;}
   return p+"open-missions";
  }
  return next;
 }
}
