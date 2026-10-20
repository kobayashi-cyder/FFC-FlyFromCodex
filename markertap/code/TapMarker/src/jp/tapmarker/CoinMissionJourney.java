package jp.tapmarker;

/** Collection intent survives generic boost/ad classification until confirmed Play input. */
final class CoinMissionJourney {
 private boolean pending;
 void reset(){pending=false;}
 boolean blocksMain(){return pending;}
 String route(String state){
  String p=CoinPreset.P;
  if(state.equals(p+"clear")||state.equals(p+"missions")||state.equals(p+"reward")||state.equals(p+"mission-tap"))pending=true;
  if(pending&&(state.equals(p+"main")||state.equals(p+"fever")))return p+"mission-tap";
  if(pending&&state.equals(p+"bonus"))return p+"mission-bonus";
  return state;
 }
 void completed(PhaseEngine e,PhaseEngine.Watch watch,long now){if(watch.id.equals(CoinPreset.P+"mission-play")&&watch.action==PhaseEngine.CENTER)pending=false;reconcile(e,now);}
 void reconcile(PhaseEngine e,long now){String next=route(e.current);if(!next.equals(e.current))e.enter(next,now);}
}
