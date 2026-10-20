package jp.tapmarker;
public class MissionJourneyTest {
 static int count;static String p=UsagiPreset.PREFIX;
 static void check(boolean b){if(!b)throw new AssertionError("mission journey check "+count);count++;}
 public static void main(String[] args){
 MissionJourney j=new MissionJourney();check(j.route(p+"main").equals(p+"main"));check(!j.blocksMain());
 for(String start:new String[]{"clear","bonus"}){
  j.reset();j.route(p+start);check(j.blocksMain());
  for(String interruption:new String[]{"classify","ad","verify","bonus","reward","use"}){check(j.route(p+interruption).equals(p+interruption));check(j.route(p+"main").equals(p+"open-missions"));check(j.blocksMain());}
  j.completedPlay();check(j.route(p+"main").equals(p+"open-missions"));
  j.route(p+"missions");check(j.route(p+"main").equals(p+"open-missions"));
  j.route(p+"reward");j.route(p+"classify");check(j.blocksMain());
  j.completedPlay();check(j.route(p+"verify").equals(p+"verify"));check(j.blocksMain());
  check(j.route(p+"main").equals(p+"main"));check(!j.blocksMain());
 }
 for(String premature:new String[]{"open-missions","mission-tap","mission-opening"}){j.reset();check(j.route(p+premature).equals(p+"verify"));check(!j.blocksMain());check(j.route(p+"main").equals(p+"main"));}
 j.route(p+"missions");check(j.blocksMain());j.reset();check(!j.blocksMain());
 System.out.println("PASS "+count+" mission journey checks");
 }
}
