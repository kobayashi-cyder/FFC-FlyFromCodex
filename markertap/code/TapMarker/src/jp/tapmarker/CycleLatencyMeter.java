package jp.tapmarker;

import java.util.Locale;

/**
 * Observation-only latency meter. A mission UI round trip is NOT evidence
 * of a collected reward: only RewardEvaluationConnectome may credit payout.
 */
final class CycleLatencyMeter {
  private String prior="";
  private long lastFrame=-1,phaseAt=-1,loopAt=-1,latestLoopMs=-1,maxPhaseMs=-1;
  private String slowestPhase="";
  private int loops;
  private boolean missionSeen;

  synchronized void reset(){
    prior="";lastFrame=phaseAt=loopAt=latestLoopMs=maxPhaseMs=-1;
    slowestPhase="";loops=0;missionSeen=false;
  }
  static boolean main(String phase){
    return phase!=null&&phase.endsWith("-main");
  }
  static boolean mission(String phase){
    if(phase==null)return false;
    return phase.contains("-mission")||phase.contains("-claim")||
      phase.endsWith("-clear")||phase.endsWith("-reward");
  }
  synchronized void observe(String phase,long capturedAt,long now){
    if(phase==null||phase.isEmpty()||capturedAt<=lastFrame||capturedAt<=0||
       now<capturedAt||now-capturedAt>=1500)return;
    lastFrame=capturedAt;
    if(phase.equals(prior))return;
    if(!prior.isEmpty()&&phaseAt>=0&&capturedAt>=phaseAt){
      long elapsed=capturedAt-phaseAt;
      if(elapsed>maxPhaseMs){maxPhaseMs=elapsed;slowestPhase=prior;}
    }
    if(main(prior)&&!main(phase)){
      loopAt=capturedAt;missionSeen=mission(phase);
    }else if(loopAt>0&&mission(phase)){
      missionSeen=true;
    }else if(main(phase)&&!main(prior)){
      if(loopAt>0&&missionSeen&&capturedAt>=loopAt){
        loops++;
        latestLoopMs=capturedAt-loopAt;
      }
      loopAt=-1;missionSeen=false;
    }
    prior=phase;phaseAt=capturedAt;
  }
  synchronized int loops(){return loops;}
  synchronized long lastLoopMs(){return latestLoopMs;}
  synchronized long maxPhaseMs(){return maxPhaseMs;}
  synchronized String slowestPhase(){return slowestPhase;}
  synchronized String report(){
    return String.format(Locale.ROOT,
      "画面巡回（報酬未検証）=%d回 / 直近=%s / 最長状態滞在=%s%s",
      loops,latestLoopMs<0?"未計測":latestLoopMs+"ms",
      maxPhaseMs<0?"未計測":maxPhaseMs+"ms",
      slowestPhase.isEmpty()?"":" / "+slowestPhase);
  }
}
