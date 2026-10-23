package jp.tapmarker;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shared, sparse event-graph admission layer for multiple virtual displays.
 *
 * The existing PhaseEngine remains the only authority for recognition and
 * tap decisions. This class never injects gestures or changes game state.
 * Each display has its own frame watermark and counters; only immutable
 * edge definitions and the scheduling policy are shared.
 *
 * Called from SplitPilot's main Handler; synchronized methods also allow
 * deterministic host testing without Android dependencies.
 */
final class ConnectomeMultiplexer {
 static final int FRAME=0, OBSERVATION=1, DECISION=2, WAIT=3, REFRESH=4, FAULT=5;
 static final int[] EDGES={
  1<<OBSERVATION, 1<<DECISION, 0, 1<<REFRESH, 0, 1<<REFRESH
 };
 static final long MAX_FRAME_AGE_MS=1500;
 static final int MAX_LANES=2;

 static final class Decision {
  final boolean admit,requestRefresh;
  final long retryMs;
  Decision(boolean admit,boolean refresh,long retryMs){
   this.admit=admit;this.requestRefresh=refresh;this.retryMs=retryMs;
  }
 }
 static final Decision ADMIT=new Decision(true,false,0);

 static final class Lane {
  final String key;
  int display=-1,quiet;
  long admittedFrame,observedFrame,admissions,deferrals,publications;
  int activated;
  Lane(String key){this.key=key;}
 }

 private final LinkedHashMap<String,Lane> lanes=new LinkedHashMap<>();

 synchronized void start(String[] keys){
  lanes.clear();
  if(keys==null||keys.length==0||keys.length>MAX_LANES)
   throw new IllegalArgumentException("virtual connectome supports 1-2 display lanes");
  for(String key:keys){
   if(key==null||key.isEmpty()||lanes.containsKey(key))
    throw new IllegalArgumentException("invalid/duplicate virtual lane");
   lanes.put(key,new Lane(key));
  }
 }
 synchronized void reset(){lanes.clear();}

 private static int excite(int start){
  int visited=0,frontier=1<<start;
  for(int pass=0;pass<EDGES.length&&frontier!=0;pass++){
   int novel=frontier&~visited;
   if(novel==0)break;
   visited|=novel;
   int next=0;
   for(int node=0;node<EDGES.length;node++)
    if((novel&(1<<node))!=0)next|=EDGES[node];
   frontier=next;
  }
  return visited;
 }
 private static Decision defer(Lane lane,boolean fault){
  lane.deferrals++;
  lane.quiet=Math.min(9,lane.quiet+1);
  lane.activated|=excite(fault?FAULT:WAIT);
  // Bounded non-busy wake; the normal screenshot pipeline remains in charge.
  return new Decision(false,true,Math.min(350,125+lane.quiet*25L));
 }

 synchronized Decision offer(String key,int display,long capturedAt,long consumedAt,long now){
  Lane lane=lanes.get(key);
  if(lane==null)return new Decision(false,false,350); // old/stopped run
  if(display<=0||capturedAt<=0||now<capturedAt
     ||now-capturedAt>MAX_FRAME_AGE_MS)return defer(lane,true);
  if(lane.display!=display){
   lane.display=display;
   lane.admittedFrame=0;
   lane.observedFrame=0;
   lane.quiet=0;
  }
  // A frame cannot be processed twice, including after a failed analysis.
  if(capturedAt<=consumedAt||capturedAt<=lane.admittedFrame)
   return defer(lane,false);
  lane.admittedFrame=capturedAt;
  lane.admissions++;
  lane.quiet=0;
  lane.activated|=1<<FRAME;
  return ADMIT;
 }

 synchronized void published(String key,long frameAt){
  Lane lane=lanes.get(key);
  if(lane==null||frameAt<=0||frameAt>lane.admittedFrame
     ||frameAt<=lane.observedFrame)return;
  lane.observedFrame=frameAt;
  lane.publications++;
  lane.activated|=excite(OBSERVATION);
 }

 synchronized String report(String key){
  Lane lane=lanes.get(key);
  if(lane==null)return "inactive";
  return "display="+lane.display+" admit="+lane.admissions
   +" observe="+lane.publications+" deferred="+lane.deferrals
   +" activeNodes="+Integer.bitCount(lane.activated)
   +" quiet="+lane.quiet;
 }
 synchronized int laneCount(){return lanes.size();}
 synchronized long totalAdmissions(){
  long count=0;for(Map.Entry<String,Lane> entry:lanes.entrySet())count+=entry.getValue().admissions;
  return count;
 }
}
