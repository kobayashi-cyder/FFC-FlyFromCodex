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
 static boolean install(PhaseEngine e){
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
 static boolean owns(PhaseEngine e){return e!=null&&e.phases.containsKey(P+"main")&&e.current.startsWith(P);}
 static boolean home(Map<String,float[]> samples){float[] v=samples.get(HOME);return v!=null&&v.length==1&&v[0]==1;}
 private KaitakuPreset(){}
}
