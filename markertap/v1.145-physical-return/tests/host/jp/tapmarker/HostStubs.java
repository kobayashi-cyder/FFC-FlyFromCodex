package jp.tapmarker;
import java.util.*;
import android.content.Intent;
final class PhaseEngine {
 static final int TIMEOUT=2,WAIT=0;String current="kaitaku-default-main";long epoch,enteredAt;
 boolean windowRelative;Map<String,Phase> phases=new HashMap<>();
 PhaseEngine(){for(String id:new String[]{"kaitaku-default-main","kaitaku-default-ad","kaitaku-default-verify"})phases.put(id,new Phase());}
 void enter(String s,long at){current=s;enteredAt=at;epoch++;}
 void invalidateObservation(){}
 boolean eligible(Watch w){return true;}
 static final class Phase {List<Watch> watches=new ArrayList<>();}
 static final class Watch {int condition,action;long timeoutMs,duration;}
}
final class AppPhases {PhaseEngine engine=new PhaseEngine();boolean enabled=true,customized;List<String> history=new ArrayList<>();void record(String s){history.add(s);}}
final class AdExitPolicy {
 final RecoveryEscalation recovery=new RecoveryEscalation();
 void returned(long now,boolean restart){recovery.sent(now,restart);}
 static boolean adState(String s){return s.endsWith("-ad");}
}
final class HostHandler {List<Runnable> queued=new ArrayList<>();void post(Runnable r){queued.add(r);}void drain(){for(Runnable r:new ArrayList<>(queued))r.run();queued.clear();}}
final class HostPackageManager {boolean missing;Intent getLaunchIntentForPackage(String p){return missing?null:new Intent();}}
final class TapService {
 long inputTargetVersion=1;boolean guarded,dialog,running,launchFails;int sent,stops;Intent last;
 final HostHandler h=new HostHandler();final HostPackageManager pm=new HostPackageManager();ProgressErrors progressErrors=new ProgressErrors();
 boolean isScreenGuarded(){return guarded;}boolean guardDialog(){return dialog;}
 HostPackageManager getPackageManager(){return pm;}
 void startActivity(Intent i){sent++;last=i;if(launchFails)throw new IllegalStateException("fixture");}
 void stopMarkers(){running=false;stops++;}
}
final class ProgressErrors {int fatals;String code="";void fatal(String p,PhaseEngine e,String c,String reason){fatals++;code=c;}}
final class SplitPilot {boolean active;}
final class AutoPilot {
 boolean active=true,actionPending,phaseOwnedMarkers=true,returning,windowCurrent=true;
 int token=1,delegations,cancels,schedules;String target="com.kurashiru.kaitaku.usagi",foreground="com.example.external";
 final AppPhases phases=new AppPhases();final TapService s=new TapService();final SplitPilot splitPilot=new SplitPilot();final AdExitPolicy adExit=new AdExitPolicy();
 Map<String,float[]> phaseSamples=new HashMap<>();long phaseSampleTakenAt,phaseWindowVersion=1,lastReturnIntent;Object phaseWindowScope=new Object();
 String packageNow(){return foreground;}boolean snapshotWindowCurrent(Object scope,long version){return windowCurrent;}
 void cancelRegisteredReturn(){returning=false;cancels++;}void schedulePhase(int t,long delay){schedules++;}
 void note(String text){}void stopSingle(boolean announce){active=false;token++;}
}
final class KaitakuStartup {static boolean running(AutoPilot p){return p.target.equals("com.kurashiru.kaitaku.usagi")&&!p.phases.customized&&!p.phases.engine.windowRelative;}}
final class KaitakuPreset {static boolean home(Map<String,float[]> samples){return samples.containsKey("home");}}
final class ExternalAdHandoffBridge {static boolean defer(AutoPilot p,int t){if(p!=null)p.delegations++;return false;}}
final class AutomationIncident {static String limit(String s,int n){return s.substring(0,Math.min(s.length(),n));}}
