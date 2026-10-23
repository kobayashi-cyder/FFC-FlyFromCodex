package jp.tapmarker;
import android.os.SystemClock;
import java.util.WeakHashMap;

/**
 * A small physical-screen preflight attached to the *existing* AutoPilot
 * DEX entrypoint. The baseline APK contains AutoPilot as bytecode, not an
 * editable Java source file, so PatchRuntime installs this bridge.
 */
final class ExternalAdHandoffBridge {
 private static final class Run {int token;final ExternalAdHandoff gate=new ExternalAdHandoff();}
 private static final WeakHashMap<AutoPilot,Run> runs=new WeakHashMap<>();
 static synchronized boolean defer(AutoPilot pilot,int expected){
  if(pilot==null||!pilot.active||pilot.token!=expected||pilot.phases==null||
     !pilot.phases.enabled||pilot.target==null||pilot.target.isEmpty()){
   if(pilot!=null)runs.remove(pilot);
   return false;
  }
  Run run=runs.get(pilot);
  if(run==null||run.token!=expected){run=new Run();run.token=expected;runs.put(pilot,run);}
  String current=pilot.packageNow();
  if(pilot.target.equals(current)){run.gate.reset();return false;}
  boolean ad=pilot.phases.engine!=null&&AdExitPolicy.adState(pilot.phases.engine.current);
  if(!ad){run.gate.reset();return false;}
  ExternalAdHandoff.Decision decision=run.gate.observe(pilot.target,current,true,SystemClock.uptimeMillis());
  if(decision!=ExternalAdHandoff.Decision.WAIT)return false;
  // No ad clicks or blind back gestures from this preflight. Keep the
  // original intent-return path after external destination stabilizes.
  pilot.s.stopMarkers();
  pilot.phaseOwnedMarkers=false;
  pilot.note("広告の遷移先を確認中 / 外部アプリが安定するまでタップ保留");
  pilot.schedulePhase(expected,350);
  return true;
 }
 private ExternalAdHandoffBridge(){}
}
