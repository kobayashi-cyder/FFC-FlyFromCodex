package jp.tapmarker;

/** Contact duration and release time are separate from the requested tap cadence. */
public final class TapContactPolicy {
 public static final int AUTO=0,DISCRETE=1,SHORTEST=2;
 public static boolean discrete(String model,int mode){return mode==DISCRETE||mode==AUTO&&PerformancePolicy.isF52A(model);}
 public static long frameMillis(float refreshRate){
  if(!Float.isFinite(refreshRate)||refreshRate<30||refreshRate>240)refreshRate=60;
  return Math.max(1,(long)Math.ceil(1000.0/refreshRate));
 }
 public static long pressMillis(String model,int mode,float refreshRate,long requested){return Math.max(Math.max(1,requested),discrete(model,mode)?frameMillis(refreshRate):1);}
 public static long releaseMillis(String model,int mode,float refreshRate){return discrete(model,mode)?frameMillis(refreshRate):0;}
 public static long minimumPeriod(String model,int mode,float refreshRate,long requested,long interval){return Math.max(Math.max(1,interval),pressMillis(model,mode,refreshRate,requested)+releaseMillis(model,mode,refreshRate));}
 /** Keep both touch states visible for games that may poll once per frame.
  * 60Hz is a conservative input profile, not a measurement of the game's FPS. */
 public static boolean frameGame(String target){return "com.kurashiru.coin.drop".equals(target)||"com.kurashiru.poipoi.usagi".equals(target);}
 public static long pressMillis(String model,int mode,float refreshRate,String target,long requested){
  long press=pressMillis(model,mode,refreshRate,requested);
  return mode!=SHORTEST&&frameGame(target)?Math.max(press,Math.max(17,frameMillis(refreshRate))):press;
 }
 public static long releaseMillis(String model,int mode,float refreshRate,String target){
  long release=releaseMillis(model,mode,refreshRate);
  return mode!=SHORTEST&&frameGame(target)?Math.max(release,Math.max(17,frameMillis(refreshRate))):release;
 }
 private TapContactPolicy(){}
}
