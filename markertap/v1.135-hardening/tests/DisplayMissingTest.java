package jp.tapmarker;
public final class DisplayMissingTest {
 public static void main(String[] args){
  ProgressWatchdog watchdog=new ProgressWatchdog();
  ProgressWatchdog.Sample s=new ProgressWatchdog.Sample();
  s.key="farm:virtual";s.app="farm";s.run=1;s.state="verify";s.displayMissing=true;s.frameAge=-1;
  if(watchdog.observe(s,1000)!=null)throw new AssertionError("first missing display sample is not an alert");
  ProgressWatchdog.Event event=watchdog.observe(s,6000);
  if(event==null||!"DISPLAY_MISSING".equals(event.code))
   throw new AssertionError("absent virtual display must not be FRAME_STALE");
  s.displayMissing=false;s.frameAge=0;s.analysisMs=0;
  if(watchdog.observe(s,6500)!=null)throw new AssertionError("reappearing display did not clear classification");
  System.out.println("DisplayMissingTest: passed");
 }
}
