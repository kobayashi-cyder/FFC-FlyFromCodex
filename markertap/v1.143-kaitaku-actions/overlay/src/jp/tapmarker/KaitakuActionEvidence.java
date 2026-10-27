package jp.tapmarker;
import java.util.*;

/** Frame-local controls. Only one mutually exclusive action is exposed per frame. */
final class KaitakuActionEvidence {
 static final float[] MISSION={.34f,.083f,.50f,.116f};
 static final float[] EFF_TITLE={.163f,.823f,.302f,.840f},EFF_COST={.15f,.845f,.185f,.857f},EFF_AD={.145f,.843f,.303f,.859f};
 static final float[] AUTO_TITLE={.702f,.823f,.86f,.840f},AUTO_COST={.71f,.845f,.745f,.857f},AUTO_AD={.706f,.843f,.864f,.859f};
 static final float[] TAP_BOOST={.05f,.759f,.23f,.778f},AUTO_BOOST={.77f,.759f,.95f,.778f};
 static final String[] KEYS={"mission-open","mission-page","upgrade","auto-upgrade","tap-boost","auto-boost","get","play","back","close","mini-start","mini-live","mini-end"};
 final Map<String,float[]> refs;String state="";long stateAt,firstAt=-1,nextMission,nextUpgrade,nextBoost;int boostTurn,upgradeTurn;
 KaitakuActionEvidence(Map<String,float[]> r){refs=r;}
 static boolean present(Map<String,float[]> out,String key){float[] v=out.get(KaitakuPreset.P+key);return v!=null&&v.length==1&&v[0]==1;}
 static boolean gamePage(Map<String,float[]> out){return present(out,"mission-page")||present(out,"close")||present(out,"mini-start")||present(out,"mini-live")||present(out,"mini-end");}
 static void put(Map<String,float[]> out,String key,float[] bounds){out.put(KaitakuPreset.P+key,new float[]{1});if(bounds!=null)out.put(KaitakuPreset.P+key+AdaptiveLayout.BOUNDS,bounds.clone());}
 static void blockHome(Map<String,float[]> out){out.put(KaitakuPreset.HOME,new float[]{0});out.remove(AdaptiveLayout.MAIN);}
 float distance(String key,float[] bounds,float[] t,int w,int h,RegionSampler.Pixels p){float[] r=refs.get(key),b=KaitakuEvidence.map(bounds,t);return r==null||!KaitakuEvidence.inside(b)?Float.POSITIVE_INFINITY:PhaseEngine.blockDistance(r,RegionSampler.sample(w,h,b,p));}
 boolean match(String key,float[] bounds,float[] t,int w,int h,RegionSampler.Pixels p){for(String suffix:new String[]{"","-dim","-bright"})if(distance(key+suffix,bounds,t,w,h,p)<=.004f)return true;return false;}
 float[] control(String title,float[] titleBounds,String cost,float[] costBounds,String ad,float[] adBounds,float[] home,int w,int h,RegionSampler.Pixels p){
  for(float[] t:new float[][]{home,{1,1,0,0}})if(match(title,titleBounds,t,w,h,p)&&(match(cost,costBounds,t,w,h,p)||match(ad,adBounds,t,w,h,p)))return t;return null;
 }
 float[] located(String key,float[] bounds,float[] home,int w,int h,RegionSampler.Pixels p){for(float[] t:new float[][]{home,{1,1,0,0}})if(match(key,bounds,t,w,h,p))return KaitakuEvidence.map(bounds,t);return null;}
 float[] shared(String name,float[] t,int w,int h,RegionSampler.Pixels p){
  float[] bounds=refs.get("usagi-mission-small:"+name+":bounds"),ref=refs.get("usagi-mission-small:"+name+":reference");if(bounds==null||ref==null)return null;
  for(float shift:name.equals("get")||name.equals("play")?new float[]{0,.168f,.336f}:new float[]{0}){float[] b=bounds.clone();b[1]+=shift;b[3]+=shift;b=KaitakuEvidence.map(b,t);if(KaitakuEvidence.inside(b)&&PhaseEngine.blockDistance(ref,RegionSampler.sample(w,h,b,p))<=.006f)return b;}return null;
 }
 boolean mission(PhaseEngine e,int w,int h,RegionSampler.Pixels p,Map<String,float[]> out){
  for(float[] t:new float[][]{{1,1,0,0},{1,.9101813f,0,.07460955f}}){
   if(shared("title",t,w,h,p)==null)continue;
   float[] get=shared("get",t,w,h,p),play=get==null?shared("play",t,w,h,p):null,back=get==null&&play==null?shared("back",t,w,h,p):null;
   if(get==null&&play==null&&back==null)continue;
   put(out,"mission-page",null);blockHome(out);
   if(e.current.equals(KaitakuPreset.P+"missions")){if(get!=null)put(out,"get",get);else if(play!=null)put(out,"play",play);else put(out,"back",back);}
   return true;
  }return false;
 }
 boolean reward(int w,int h,RegionSampler.Pixels p,Map<String,float[]> out){
  float[] a=refs.get("shared-reward-title"),b=refs.get("shared-reward-close");if(a==null||b==null)return false;
  for(float[] t:new float[][]{{1,1,0,0},{1,.9101813f,0,.07460955f}}){float[] title=KaitakuEvidence.map(new float[]{.18f,.34f,.82f,.368f},t),close=KaitakuEvidence.map(new float[]{.36f,.548f,.62f,.575f},t);
   if(PhaseEngine.blockDistance(a,RegionSampler.sample(w,h,title,p))<=.004f&&PhaseEngine.blockDistance(b,RegionSampler.sample(w,h,close,p))<=.004f){put(out,"close",close);blockHome(out);return true;}
  }return false;
 }
 void sample(PhaseEngine e,KaitakuEvidence.Result home,int w,int h,RegionSampler.Pixels p,long now,Map<String,float[]> out){
  for(String key:KEYS)out.put(KaitakuPreset.P+key,new float[]{0});
  if(firstAt<0){firstAt=now;nextMission=now+10000;nextUpgrade=now+2000;nextBoost=now+20000;}
  if(!state.equals(e.current)){state=e.current;stateAt=now;
   if(state.equals(KaitakuPreset.P+"mission-opening")||state.equals(KaitakuPreset.P+"missions"))nextMission=now+60000;
   if(state.equals(KaitakuPreset.P+"upgrade-wait")){nextUpgrade=now+15000;upgradeTurn++;}
   if(state.equals(KaitakuPreset.P+"control-wait")){nextBoost=now+120000;boostTurn++;}
  }
  if(home.proceed)return;
  if(!home.home){if(!reward(w,h,p,out))mission(e,w,h,p,out);return;}
  if((state.equals(KaitakuPreset.P+"upgrade-wait")||state.equals(KaitakuPreset.P+"control-wait"))&&now-stateAt<1200){blockHome(out);return;}
  if(!state.equals(KaitakuPreset.P+"main"))return;
  float[] t=home.transform;
  float[] mission=now>=nextMission?located("action-mission",MISSION,t,w,h,p):null;
  if(mission!=null){put(out,"mission-open",mission);return;}
  if(now>=nextUpgrade){
   float[] left=control("action-eff-title",EFF_TITLE,"action-eff-cost",EFF_COST,"action-eff-ad",EFF_AD,t,w,h,p);
   float[] right=control("action-auto-title",AUTO_TITLE,"action-auto-cost",AUTO_COST,"action-auto-ad",AUTO_AD,t,w,h,p);
   if(left!=null&&(upgradeTurn%2==0||right==null)){put(out,"upgrade",KaitakuEvidence.map(new float[]{.045f,.821f,.34f,.865f},left));return;}
   if(right!=null){put(out,"auto-upgrade",KaitakuEvidence.map(new float[]{.68f,.821f,.96f,.865f},right));return;}
  }
  if(now>=nextBoost){String key=boostTurn%2==0?"tap-boost":"auto-boost";float[] bounds=boostTurn%2==0?TAP_BOOST:AUTO_BOOST;
   float[] found=located("action-"+key,bounds,t,w,h,p);if(found!=null)put(out,key,found);
  }
 }
}
