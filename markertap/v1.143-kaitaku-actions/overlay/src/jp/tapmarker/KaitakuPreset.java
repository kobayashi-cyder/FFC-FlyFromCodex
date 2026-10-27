package jp.tapmarker;

import java.util.*;

/** Separate game identity and graph; no Farm/Usagi state or marker is imported. */
public final class KaitakuPreset {
 public static final String APP="com.kurashiru.kaitaku.usagi",P="kaitaku-default-";
 static final String HOME=P+"home",CONTINUE=P+"continue";
 static void region(PhaseEngine e,String id,String name,float[] b){PhaseEngine.Region r=new PhaseEngine.Region();r.id=id;r.name=name;r.bounds=b.clone();e.regions.put(id,r);}
 static void state(PhaseEngine e,String id,String name){PhaseEngine.Phase p=new PhaseEngine.Phase();p.id=P+id;p.name="開拓うさぎ："+name;p.markersDuring=id.equals("main");p.graphX=(e.phases.size()%3)*300;p.graphY=(e.phases.size()/3)*180;e.phases.put(p.id,p);}
 static void image(PhaseEngine e,String state,String id,String region,int condition,int action,String next){PhaseEngine.Watch w=new PhaseEngine.Watch();w.id=P+id;w.name=id;w.regionId=region;w.reference=new float[]{1};w.threshold=0;w.condition=condition;w.action=action;w.nextPhase=P+next;w.waitMs=0;e.phases.get(P+state).watches.add(w);}
 static void timer(PhaseEngine e,String state,long ms,int action,String next){PhaseEngine.Watch w=new PhaseEngine.Watch();w.id=P+state+"-timeout";w.name="新しい画面の確認待ち";w.condition=PhaseEngine.TIMEOUT;w.action=action;w.nextPhase=P+next;w.timeoutMs=ms;w.waitMs=0;w.fallback=true;e.phases.get(P+state).watches.add(w);}
 static boolean installLegacy(PhaseEngine e){
  if(!e.phases.isEmpty()||e.windowRelative)return false;
  region(e,HOME,"開拓するボタンの独立した左右の輪郭",KaitakuEvidence.LEFT);
  region(e,CONTINUE,"このままはじめる",KaitakuEvidence.CONTROL);
  for(String[] s:new String[][]{{"check","開始・画面判別"},{"main","開拓を連打"},{"ad","広告終了・ゲームへの復帰"},{"verify","復帰後の再判別"}})state(e,s[0],s[1]);
  for(String state:new String[]{"check","ad","verify"}){
   image(e,state,state+"-home",HOME,PhaseEngine.MATCH,PhaseEngine.WAIT,"main");
   image(e,state,state+"-continue",CONTINUE,PhaseEngine.MATCH,PhaseEngine.CENTER,"verify");
  }
  image(e,"main","main-home-lost",HOME,PhaseEngine.DISAPPEAR,PhaseEngine.WAIT,"check");
  e.rule(P+"main-home-lost").fallback=true;
  image(e,"main","main-continue",CONTINUE,PhaseEngine.MATCH,PhaseEngine.CENTER,"verify");
  timer(e,"check",5000,PhaseEngine.WAIT,"ad");
  timer(e,"ad",30000,PhaseEngine.RETURN_APP,"verify");
  timer(e,"verify",6000,PhaseEngine.WAIT,"ad");
  e.initial=P+"check";e.current=e.initial;return true;
 }
 static void match(PhaseEngine e,String state,String key,int action,String next){image(e,state,state+"-"+key,P+key,PhaseEngine.MATCH,action,next);}
 static boolean install(PhaseEngine e){
  if(!installLegacy(e))return false;
  for(String key:new String[]{"mission-open","mission-page","upgrade","auto-upgrade","tap-boost","auto-boost","get","play","back","close","mini-start","mini-live","mini-end"})region(e,P+key,key,new float[]{.35f,.4f,.65f,.6f});
  e.regions.get(P+"mission-open").bounds=KaitakuActionEvidence.MISSION.clone();
  e.regions.get(P+"upgrade").bounds=new float[]{.045f,.821f,.34f,.865f};e.regions.get(P+"auto-upgrade").bounds=new float[]{.68f,.821f,.96f,.865f};
  e.regions.get(P+"tap-boost").bounds=KaitakuActionEvidence.TAP_BOOST.clone();e.regions.get(P+"auto-boost").bounds=KaitakuActionEvidence.AUTO_BOOST.clone();
  e.regions.get(P+"mission-page").bounds=new float[]{.016f,.118f,.883f,.94f};
  e.regions.get(P+"close").bounds=new float[]{.18f,.34f,.82f,.575f};
  for(String[] s:new String[][]{{"mission-opening","ミッション表示待ち"},{"missions","ミッション報酬を確認"},{"claim-wait","報酬の反映・次のゲットを確認"},{"upgrade-wait","タップ効率・オート強化の応答待ち"},{"control-wait","追加ボタンの応答待ち"},{"mini","タップミニゲーム"}})state(e,s[0],s[1]);
  e.phases.get(P+"mini").markersDuring=true;
  match(e,"main","mission-open",PhaseEngine.CENTER,"mission-opening");
  match(e,"main","upgrade",PhaseEngine.CENTER,"upgrade-wait");
  match(e,"main","auto-upgrade",PhaseEngine.CENTER,"upgrade-wait");
  match(e,"main","tap-boost",PhaseEngine.CENTER,"control-wait");
  match(e,"main","auto-boost",PhaseEngine.CENTER,"control-wait");
  for(String state:new String[]{"check","main","ad","verify","mission-opening","claim-wait","upgrade-wait","control-wait","mini"})match(e,state,"mission-page",PhaseEngine.WAIT,"missions");
  match(e,"missions","get",PhaseEngine.CENTER,"claim-wait");
  match(e,"missions","play",PhaseEngine.CENTER,"verify");
  match(e,"missions","back",PhaseEngine.CENTER,"verify");
  for(String state:new ArrayList<String>(Arrays.asList("check","main","ad","verify","mission-opening","missions","claim-wait","upgrade-wait","control-wait","mini"))){
   match(e,state,"close",PhaseEngine.CENTER,"claim-wait");
   match(e,state,"mini-start",PhaseEngine.CENTER,"mini");
   match(e,state,"mini-end",PhaseEngine.CENTER,"verify");
   if(!state.equals("mini"))match(e,state,"mini-live",PhaseEngine.WAIT,"mini");
  }
  image(e,"mini","mini-lost",P+"mini-live",PhaseEngine.DISAPPEAR,PhaseEngine.WAIT,"verify");e.rule(P+"mini-lost").fallback=true;
  for(String state:new String[]{"mission-opening","claim-wait","upgrade-wait","control-wait"}){
   image(e,state,state+"-continue",CONTINUE,PhaseEngine.MATCH,PhaseEngine.CENTER,"verify");
   image(e,state,state+"-home",HOME,PhaseEngine.MATCH,PhaseEngine.WAIT,"main");e.rule(P+state+"-home").fallback=true;
   timer(e,state,6000,PhaseEngine.WAIT,"check");
  }
  timer(e,"missions",10000,PhaseEngine.WAIT,"check");timer(e,"mini",45000,PhaseEngine.WAIT,"check");
  return true;
 }
 static boolean isLegacy(PhaseEngine e){
  PhaseEngine old=new PhaseEngine();installLegacy(old);
  if(AutomatonGraph.sameConfiguration(e,old))return true;
  AdCloseAutomaton.install(old,APP);return AutomatonGraph.sameConfiguration(e,old);
 }
 static boolean owns(PhaseEngine e){return e!=null&&e.phases.containsKey(P+"main")&&e.current.startsWith(P);}
 static boolean home(Map<String,float[]> samples){float[] v=samples.get(HOME);return v!=null&&v.length==1&&v[0]==1;}
 private KaitakuPreset(){}
}
