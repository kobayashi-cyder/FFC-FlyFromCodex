package jp.tapmarker;

import java.util.*;

/** Frame-local game dialogs. They run before detailed home/layout searches. */
final class GamePopupEvidence {
 static final String POINT=":game-popup-point",HOME_PROBE=":independent-home-probe:";
 static final String FOOTER_EXIT=":game-popup-footer-exit";
 static final String EVIDENCE_BOUNDS=":game-popup-evidence-bounds",DETECTOR=":game-popup-detector",VISUAL_DISTANCE=":game-popup-pixel-distance";
 static String detectorName(int detector){return detector==1?"accessibility-named-control":detector==2?"recorded-footer-or-promo":detector==3?"dialog-cross-scan":detector==4?"ordinary-offline-reward":"game-popup";}
 static void evidence(Map<String,float[]> out,String id,int detector,float distance,float[] bounds){out.put(id+DETECTOR,new float[]{detector});if(Float.isFinite(distance))out.put(id+VISUAL_DISTANCE,new float[]{distance});if(bounds!=null)out.put(id+EVIDENCE_BOUNDS,bounds.clone());}
 final String app;final PhaseEngine engine;final PopupVisualEvidence visual;final CoinOfflineEvidence coinOffline;
 private float[] bonusLayout;
 GamePopupEvidence(String app,PhaseEngine engine){this(app,engine,Collections.emptyMap());}
 GamePopupEvidence(String app,PhaseEngine engine,Map<String,float[]> references){this.app=app;this.engine=engine;visual=new PopupVisualEvidence(app,references);coinOffline=CoinPreset.APP.equals(app)?new CoinOfflineEvidence(references):null;}
 static boolean supported(String app){return UsagiPreset.APP.equals(app)||FarmPreset.APP.equals(app)||CoinPreset.APP.equals(app)||PoipoiPreset.APP.equals(app);}
 static String closeId(String app){return PresetRegistry.prefix(app)+"popup-close";}
 static void install(PhaseEngine e,String app){
  if(e.windowRelative||!supported(app))return;
  if(FarmPreset.APP.equals(app)&&!e.phases.containsKey(FarmPreset.P+"offline")){
   FarmPreset.region(e,"offline","オフライン報酬の通常獲得",0,0,1,1);
   FarmPreset.state(e,"offline","オフライン報酬を獲得");
   for(PhaseEngine.Phase p:e.phases.values())if(!p.terminal&&!p.id.equals(FarmPreset.P+"unknown")){
    PhaseEngine.Watch w=new PhaseEngine.Watch();w.id=p.id+"-offline-popup";w.name="オフライン報酬の獲得を確認";w.regionId=FarmPreset.P+"offline";w.reference=new float[]{1};w.threshold=0;w.condition=PhaseEngine.MATCH;w.action=p.id.equals(FarmPreset.P+"offline")?PhaseEngine.CENTER:PhaseEngine.WAIT;w.nextPhase=FarmPreset.P+(w.action==PhaseEngine.CENTER?"check":"offline");w.waitMs=0;p.watches.add(w);
   }
  }
  String prefix=PresetRegistry.prefix(app),close=closeId(app);
  if(CoinPreset.APP.equals(app)){
   String offline=prefix+"offline";
   if(!e.regions.containsKey(offline))CoinPreset.region(e,"offline","オフライン報酬の通常獲得",new float[]{0,0,1,1});
   for(PhaseEngine.Phase p:e.phases.values()){
    String name=p.id.startsWith(prefix)?p.id.substring(prefix.length()):"";
    if(name.isEmpty()||p.terminal||Arrays.asList("account","error","unknown").contains(name)||e.rule(p.id+"-offline-popup")!=null)continue;
    PhaseEngine.Watch w=new PhaseEngine.Watch();w.id=p.id+"-offline-popup";w.name="オフライン報酬を広告なしで獲得";w.regionId=offline;w.reference=new float[]{1};w.threshold=0;w.condition=PhaseEngine.MATCH;w.action=PhaseEngine.CENTER;w.nextPhase=RecoveryRoute.target(app,p.id,false,false,CoinPreset.missionPending(p.id));w.waitMs=0;w.runtimeControlEvidence=true;p.watches.add(w);
   }
  }
  if(!e.regions.containsKey(close)){PhaseEngine.Region r=new PhaseEngine.Region();r.id=close;r.name="表示中のポップアップの閉じる";r.bounds=new float[]{0,0,1,1};e.regions.put(close,r);}
  for(PhaseEngine.Phase p:e.phases.values()){
   String name=p.id.startsWith(prefix)?p.id.substring(prefix.length()):"";
   if(name.isEmpty()||p.terminal||Arrays.asList("account","error","unknown","external").contains(name)||e.rule(p.id+"-popup-close")!=null)continue;
   PhaseEngine.Watch w=new PhaseEngine.Watch();w.id=p.id+"-popup-close";w.name="確認したポップアップの閉じるを押す";w.regionId=close;w.reference=new float[]{1};w.threshold=0;w.condition=PhaseEngine.MATCH;w.action=PhaseEngine.CENTER;w.nextPhase=RecoveryRoute.target(app,p.id,false,false,CoinPreset.missionPending(p.id));w.priority=0;w.waitMs=0;w.runtimeControlEvidence=true;p.watches.add(w);
  }
  for(PhaseEngine.Phase p:e.phases.values())for(PhaseEngine.Watch w:p.watches)
   if(w.regionId.equals(prefix+"offline")||w.regionId.equals(prefix+"bonus")||w.regionId.equals(prefix+(FarmPreset.APP.equals(app)?"close":"reward"))||UsagiPreset.APP.equals(app)&&Arrays.asList(prefix+"claim",prefix+"dialog",prefix+"cancel").contains(w.regionId)||w.regionId.equals(PoipoiPreset.P+"get")||w.regionId.equals(PoipoiPreset.P+"missions"))w.runtimeControlEvidence=true;
 }
 static boolean put(Map<String,float[]> out,String id,float x,float y,int w,int h){
  if(!Float.isFinite(x)||!Float.isFinite(y)||x<0||y<0||x>=w||y>=h)return false;
  out.put(id,new float[]{1});out.put(id+POINT,new float[]{x,y,w,h});return true;
 }
 static boolean fast(Map<String,float[]> samples){return present(samples)||samples.containsKey(FarmAdErrorEvidence.KEY);}
 static void namedFooter(Map<String,float[]> samples,String id,float top,float bottom,float scopeTop,float scopeBottom){
  if((id.equals(closeId(UsagiPreset.APP))||id.equals(closeId(CoinPreset.APP)))&&scopeBottom>scopeTop&&top>=scopeTop+(scopeBottom-scopeTop)*.85f&&bottom>=scopeBottom-2&&samples.containsKey(id+POINT))samples.put(FOOTER_EXIT,new float[]{1});
 }
 static boolean present(Map<String,float[]> samples){for(String id:samples.keySet())if(id.endsWith(POINT))return true;return false;}
 static float[] point(String id,Map<String,float[]> samples,int w,int h){
  float[] p=samples.get(id+POINT),v=samples.get(id);
  if(p==null||p.length!=4||p[2]!=w||p[3]!=h||v==null||v.length!=1||v[0]!=1||!Float.isFinite(p[0])||!Float.isFinite(p[1])||p[0]<0||p[1]<0||p[0]>=w||p[1]>=h)return null;
  return new float[]{p[0],p[1]};
 }
 static String named(String app,String label,String context,boolean dialog){
  if(!supported(app))return "";
  String text=BancAutonomy.normalize(label).replace("\ufe0f","").replace("\ufe0e",""),scope=BancAutonomy.normalize(context);
  String prefix=PresetRegistry.prefix(app);
  boolean offline=scope.contains("オフライン"),popup=dialog||offline||scope.contains("ボーナス")||scope.contains("コインゲット")||scope.contains("報酬")||scope.contains("お知らせ")||scope.contains("広告")||scope.contains("チャンス");
  if(offline&&Arrays.asList("獲得","獲得する","報酬を獲得","受け取る","報酬を受け取る").contains(text))return UsagiPreset.APP.equals(app)?prefix+"claim":prefix+"offline";
  if(popup&&Arrays.asList("このままはじめる","そのままはじめる").contains(text)&&(PoipoiPreset.APP.equals(app)||UsagiPreset.APP.equals(app)))return prefix+"bonus";
  if(UsagiPreset.APP.equals(app)&&scope.contains("使用する")&&text.equals("キャンセル"))return prefix+"cancel";
  if(!popup&&Arrays.asList("閉じる","とじる","close","広告を閉じる").contains(text))return closeId(app);
  if(popup&&Arrays.asList("×","✕","✖","❌","x","閉じる","とじる","close","広告を閉じる").contains(text))return FarmPreset.APP.equals(app)?FarmPreset.P+"close":PoipoiPreset.APP.equals(app)?PoipoiPreset.P+"reward":closeId(app);
  if(PoipoiPreset.APP.equals(app)&&scope.contains("デイリーミッション")&&text.equals("ゲット"))return PoipoiPreset.P+"get";
  return "";
 }
 Map<String,float[]> quick(int w,int h,RegionSampler.Pixels pixels,int[] window){
  Map<String,float[]> out=new HashMap<>();if(!supported(app))return out;
  ContentViewport v=ContentViewport.locate(w,h,pixels,window);
  RegionSampler.Pixels local=(x,y)->pixels.get(x+v.left,y+v.top);
  if(coinOffline!=null){float[] claim=coinOffline.locate(v.width,v.height,local);if(claim!=null){put(out,CoinPreset.P+"offline",v.left+claim[0],v.top+claim[1],w,h);evidence(out,CoinPreset.P+"offline",4,Float.NaN,null);return out;}}
  if(PoipoiPreset.APP.equals(app)){
   float[] bonus=bonus(v.width,v.height,local);
   if(bonus!=null){put(out,PoipoiPreset.P+"bonus",v.left+bonus[0],v.top+bonus[1],w,h);return out;}
   PhaseEngine.Watch offline=engine.rule(PoipoiPreset.P+"check-offline");
   if(offline!=null)for(float sy:new float[]{1,.96f,1.04f})for(float dy:new float[]{0,-.025f,.025f}){
    float[] t={1,sy,0,(1-sy)/2+dy};float[] raw=AdaptiveLayout.paired(v.width,v.height,PoipoiPreset.OFFLINE,t,local);
    if(offline.distance(raw)<=offline.threshold){float[] b=AdaptiveLayout.map(PoipoiPreset.OFFLINE[1],t);put(out,PoipoiPreset.P+"offline",v.left+(b[0]+b[2])*v.width/2,v.top+(b[1]+b[3])*v.height/2,w,h);return out;}
   }
  }
  if(FarmPreset.APP.equals(app)){FarmAdErrorEvidence.sample(out,w,h,pixels);if(out.containsKey(FarmAdErrorEvidence.KEY))return out;}
  float[] exit=visual.locate(v.width,v.height,local);
  if(exit!=null){put(out,closeId(app),exit[0]+v.left,exit[1]+v.top,w,h);float[] b=visual.lastBounds==null?null:visual.lastBounds.clone();if(b!=null){b[0]+=v.left;b[2]+=v.left;b[1]+=v.top;b[3]+=v.top;}evidence(out,closeId(app),2,visual.lastDistance,b);if(UsagiPreset.APP.equals(app)||CoinPreset.APP.equals(app))out.put(FOOTER_EXIT,new float[]{1});return out;}
  float[] cross=dialogCross(v.width,v.height,local);
  if(cross!=null){String id=FarmPreset.APP.equals(app)?FarmPreset.P+"close":PoipoiPreset.APP.equals(app)?PoipoiPreset.P+"reward":closeId(app);put(out,id,cross[0]+v.left,cross[1]+v.top,w,h);evidence(out,id,3,Float.NaN,null);}
  return out;
 }
 /** Two independent text regions; only the ordinary continuation label supplies a point. */
 private float[] bonus(int w,int h,RegionSampler.Pixels pixels){
  PhaseEngine.Watch watch=engine.rule(PoipoiPreset.P+"bonus-tap");if(watch==null)return null;
  if(bonusLayout!=null&&watch.distance(AdaptiveLayout.paired(w,h,PoipoiPreset.BONUS,bonusLayout,pixels))<=watch.threshold)return RegionMath.center(AdaptiveLayout.map(PoipoiPreset.BONUS[1],bonusLayout),w,h);
  boolean title=false;for(int y=(int)(h*.13f);y<h*.43f;y++)if(header(pixels.get(w/4,y))&&header(pixels.get(w*3/4,y))){title=true;break;}if(!title){bonusLayout=null;return null;}
  for(float sy:new float[]{1,.96f,1.04f})for(int i=0;i<=64;i++)for(float dx:new float[]{0,-.005f,.005f}){
   int step=i==0?0:(i+1)/2*(i%2==1?-1:1);float[] t={1,sy,dx,(1-sy)/2+step*.0025f};
   float[] raw=AdaptiveLayout.paired(w,h,PoipoiPreset.BONUS,t,pixels);
   if(watch.distance(raw)<=watch.threshold){bonusLayout=t;return RegionMath.center(AdaptiveLayout.map(PoipoiPreset.BONUS[1],t),w,h);}
  }
  bonusLayout=null;return null;
 }
 static boolean header(int c){int r=c>>16&255,g=c>>8&255,b=c&255;return r>175&&r-g>24&&(g>65||b>100);}
 static boolean white(int c){return Math.min(c>>16&255,Math.min(c>>8&255,c&255))>225;}
 /** A close glyph inside a measured colored-title/white-body dialog, not a screen corner. */
 static float[] dialogCross(int w,int h,RegionSampler.Pixels p){
  if(w<160||h<300)return null;
  for(int y=(int)(h*.20f);y<h*.62f;y++){
   if(!header(p.get(w/4,y))||!header(p.get(w*3/4,y)))continue;
   int top=y;while(y<h*.72f&&header(p.get(w/4,y))&&header(p.get(w*3/4,y)))y++;
   int bottom=y;if(bottom-top<h*.035f||bottom-top>h*.17f)continue;
   int body=bottom+Math.max(2,(int)(h*.015f));if(body>=h||!white(p.get(w/4,body))||!white(p.get(w*3/4,body)))continue;
   // Measure above the title text and X; their dark pixels must not shorten the panel.
   int left=w/4,right=w*3/4;int mid=top+Math.max(1,(bottom-top)/12);
   while(left>0&&header(p.get(left-1,mid)))left--;
   while(right<w-1&&header(p.get(right+1,mid)))right++;
   if(right-left<w*.50f||right-left>w*.97f)continue;
   final RegionSampler.Pixels ink=(x,z)->AdCloseDetector.darkGlyph(p.get(x,z))?0xff000000:0xffffffff;
   float[] band={(left+(right-left)*.68f)/w,top/(float)h,(right+1)/(float)w,bottom/(float)h};
   float[] found=AdCloseDetector.find(w,h,band,ink);if(found!=null)return found;
  }
  return null;
 }
}
