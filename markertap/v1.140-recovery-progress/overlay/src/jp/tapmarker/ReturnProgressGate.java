package jp.tapmarker;

/** One unresolved return episode survives state re-entry and cancelled requests. */
final class ReturnProgressGate {
 enum Decision { WAIT, CANCEL_RETURN, ALLOW_RETURN, STOP }
 static final long FRESH_MS=1500,CONFIRM_GAP_MS=1500,WAIT_LIMIT_MS=2500,EPISODE_LIMIT_MS=90000;
 static final int MAX_CANCELLATIONS=3;
 private int request=Integer.MIN_VALUE,distinct,cancellations,homeFrames;
 private long waitingAt=-1,lastFrame=-1,episodeAt=-1,epoch=Long.MIN_VALUE,window=Long.MIN_VALUE;
 private long homeAt=-1,homeWindow=Long.MIN_VALUE;
 private String rule="",state="";

 static boolean fresh(long frameAt,long now){return frameAt>0&&now>=frameAt&&now-frameAt<FRESH_MS;}
 void home(boolean confirmed,long frameAt,long windowVersion,long now){
  if(!confirmed||!fresh(frameAt,now)){homeFrames=0;homeAt=-1;return;}
  if(windowVersion!=homeWindow||homeAt<0||frameAt-homeAt>=CONFIRM_GAP_MS){homeFrames=0;homeAt=-1;}
  homeWindow=windowVersion;
  if(frameAt<=homeAt)return;
  homeAt=frameAt;
  if(++homeFrames>=2){episodeAt=-1;cancellations=0;clearCandidate();}
 }
 boolean expired(long now){return episodeAt>=0&&now>=episodeAt&&now-episodeAt>=EPISODE_LIMIT_MS;}
 private void clearCandidate(){distinct=0;lastFrame=-1;rule="";state="";epoch=window=Long.MIN_VALUE;}

 Decision decide(int requestId,String candidate,String current,long stateEpoch,long windowVersion,
                 long frameAt,long now,boolean cooling){
  if(expired(now))return Decision.STOP;
  if(request!=requestId){request=requestId;waitingAt=now;clearCandidate();}
  // Cooldown is a short wait, never a completed return cancellation.
  if(cooling)return now-waitingAt>=WAIT_LIMIT_MS?Decision.ALLOW_RETURN:Decision.WAIT;
  if(!fresh(frameAt,now)||candidate==null||candidate.isEmpty()){
   clearCandidate();return Decision.ALLOW_RETURN;
  }
  if(cancellations>=MAX_CANCELLATIONS)return Decision.ALLOW_RETURN;
  if(!candidate.equals(rule)||!current.equals(state)||epoch!=stateEpoch||window!=windowVersion||
     lastFrame>0&&frameAt-lastFrame>=CONFIRM_GAP_MS){
   clearCandidate();rule=candidate;state=current;epoch=stateEpoch;window=windowVersion;
  }
  if(frameAt>lastFrame){lastFrame=frameAt;distinct++;}
  if(distinct>=2){
   if(episodeAt<0)episodeAt=now;
   cancellations++;clearCandidate();return Decision.CANCEL_RETURN;
  }
  return now-waitingAt>=WAIT_LIMIT_MS?Decision.ALLOW_RETURN:Decision.WAIT;
 }
 String report(long now){return "取り消し "+cancellations+"/"+MAX_CANCELLATIONS+
   " / 未解決 "+(episodeAt<0?0:Math.max(0,now-episodeAt))+"ms / 候補 "+rule+" / 確認 "+distinct+"枚";}
}
