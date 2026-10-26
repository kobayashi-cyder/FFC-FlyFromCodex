package jp.tapmarker;

public final class PhysicalCaptureWatchdogTest {
 static int checks;
 static void check(boolean b,String message){checks++;if(!b)throw new AssertionError(message);}
 static ProgressWatchdog.Sample sample(){ProgressWatchdog.Sample s=new ProgressWatchdog.Sample();s.key="farm:0";s.app=FarmPreset.APP;s.state="farm-default-ad";s.run=187;s.separateAnalysis=true;s.frameAge=100;s.analysisAge=100;s.expectedWaitMs=180000;return s;}
 public static void main(String[] args){
  ProgressWatchdog monitor=new ProgressWatchdog();ProgressWatchdog.Sample s=sample();
  // The incident had a 1763ms-old accepted result. Poll cadence cannot turn
  // continuously advancing captures/results into a fictitious 15s outage.
  for(long now=100000;now<=180000;now+=1000){s.capturedAt=now-1763;s.analysisAt=now-1763;s.frameAge=1763;s.analysisAge=1763;check(monitor.observe(s,now)==null,"distinct images between polls are healthy");}
  s.frameAge=15800;s.analysisAge=15800;ProgressWatchdog.Event e=monitor.observe(s,200000);
  check(e!=null&&e.code.equals("FRAME_STALE")&&e.duration==15800,"real capture outage uses actual receipt age");
  check(monitor.observe(s,201000)==null,"continuous capture outage reports once");
  s.frameAge=100;s.analysisAge=100;check(monitor.observe(s,202000)==null,"fresh capture and analysis clear outage");
  s.frameAge=100;s.analysisAge=16200;e=monitor.observe(s,220000);
  check(e!=null&&e.code.equals("ANALYSIS_UNAVAILABLE")&&e.duration==16200,"fresh captures with rejected geometry are a distinct fault");
  check(monitor.observe(s,221000)==null,"repeated raw captures cannot duplicate an unresolved analysis report");
  s.analysisAge=100;check(monitor.observe(s,222000)==null,"accepted analysis clears that fault");
  s=sample();s.frameAge=-1;s.analysisAge=-1;s.run=188;
  check(monitor.observe(s,300000)==null,"a new run waits for first screenshot");
  e=monitor.observe(s,315000);check(e!=null&&e.code.equals("FRAME_STALE"),"missing initial callback is still detected");
  s=sample();s.key="farm:analysis-start";s.analysisAge=-1;
  check(monitor.observe(s,400000)==null,"first analysis has bounded startup grace");
  e=monitor.observe(s,415000);check(e!=null&&e.code.equals("ANALYSIS_UNAVAILABLE"),"missing first accepted result is detected");
  s=sample();s.key="farm:unknown";s.expectedWaitMs=5000;
  check(monitor.observe(s,500000)==null,"unrecognized but newly sampled screen initially waits");
  e=monitor.observe(s,505000);check(e!=null&&e.code.equals("STATE_STALL"),"unrecognized screens are not mislabeled capture failures");
  s=sample();s.key="farm:legacy";s.separateAnalysis=false;s.frameAge=4000;
  check(monitor.observe(s,600000)==null,"legacy virtual polling retains its grace");
  e=monitor.observe(s,615000);check(e!=null&&e.code.equals("FRAME_STALE"),"legacy virtual outage detection retained");
  check(!AnalysisBudget.fresh(700000-1501,700000),"expired images remain forbidden for actions");
  check(AnalysisBudget.fresh(700000-1500,700000),"original action freshness boundary retained");
  System.out.println("PhysicalCaptureWatchdogTest: "+checks+" checks passed");
 }
}
