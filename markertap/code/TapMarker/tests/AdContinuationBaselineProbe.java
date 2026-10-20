package jp.tapmarker;
import java.util.*;

/** Run against the pre-fix jar to reproduce the old decision path without Android. */
public final class AdContinuationBaselineProbe {
 static Map<String,float[]> exit(){Map<String,float[]> s=new HashMap<>();AdCloseAutomaton.addNamed(s,AdCloseAutomaton.RIGHT,507.6f,72.72f,540,1212);return s;}
 public static void main(String[] args)throws Exception{
  PhaseEngine e=new PhaseEngine();CoinPreset.install(e,VirtualPresetProfileTest.canonical("tests/video6720-references.bin"));AdCloseAutomaton.install(e,CoinPreset.APP);e.strictRuntime=true;e.enter(CoinPreset.P+"ad",0);AdExitPolicy policy=new AdExitPolicy();
  for(long t:new long[]{1000,2000}){Map<String,float[]> s=exit();policy.filter(e,s,540,1212,t,t);policy.controls.sent(AdCloseAutomaton.RIGHT,t);}
  Map<String,float[]> raw=exit();System.out.println("Pre-return raw exhausted Close cancels recovery: "+RecoveryRequest.visibleBranch(e,raw));policy.filter(e,raw,540,1212,4000,4000);System.out.println("Observation-loop filtered exhausted Close cancels recovery: "+RecoveryRequest.visibleBranch(e,raw));
  policy.controls.select(new HashMap<>(),540,1212,4400,4400);policy.controls.select(new HashMap<>(),540,1212,4800,4800);raw=exit();policy.controls.select(raw,540,1212,5200,5200);System.out.println("Same-corner Close after observed card transition is available: "+(raw.get(AdCloseAutomaton.RIGHT)[0]==1));
  for(String app:new String[]{CoinPreset.APP,PoipoiPreset.APP}){
   e=new PhaseEngine();Map<String,float[]> refs=VirtualPresetProfileTest.canonical(app.equals(CoinPreset.APP)?"tests/video6720-references.bin":"tests/image6727-references.bin");if(app.equals(CoinPreset.APP))CoinPreset.install(e,refs);else PoipoiPreset.install(e,refs);String p=app.equals(CoinPreset.APP)?CoinPreset.P:PoipoiPreset.P;e.enter(p+"claim-wait",0);Map<String,float[]> s=new HashMap<>();
   for(PhaseEngine.Watch w:e.phases.get(e.current).watches)if(w.reference!=null){float[] data=new float[w.reference.length];for(int i=0;i<data.length;i++)data[i]=w.reference[i]<.5f?1:0;s.put(w.regionId,data);}
   AdInterruption interruption=new AdInterruption();interruption.observe(e,s,1000);interruption.observe(e,s,1800);System.out.println(app+" persistent CM detected in claim-wait: "+interruption.observe(e,s,3700));
  }
 }
}
