package jp.tapmarker;

import java.util.*;

public final class ProgressWatchdogTest {
 static int checks;
 static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 static ProgressWatchdog.Sample sample(String app){ProgressWatchdog.Sample s=new ProgressWatchdog.Sample();s.app=app;s.key=app+":101";s.run=1;s.state="verify";s.frameAge=100;return s;}
 static void transientAndStale(){
  ProgressWatchdog w=new ProgressWatchdog();ProgressWatchdog.Sample s=sample("rabbit");
  s.frameAge=-1;check(w.observe(s,1000)==null,"a missing first frame does not immediately raise an error");
  s.frameAge=3000;check(w.observe(s,15000)==null,"brief capture loss remains within the grace period");
  s.frameAge=50;check(w.observe(s,16000)==null,"fresh frames cancel the pending error");
  s.frameAge=4000;w.observe(s,20000);ProgressWatchdog.Event e=w.observe(s,35000);
  check(e!=null&&e.code.equals("FRAME_STALE")&&e.duration==15000,"sustained capture failure reports its actual observed duration");
  check(w.observe(s,50000)==null,"a continuous outage notifies once");
  s.frameAge=0;w.observe(s,51000);s.frameAge=2000;w.observe(s,52000);check(w.observe(s,67000)!=null,"a recovered display can report a later independent outage");
 }
 static void knownWaits(){
  ProgressWatchdog w=new ProgressWatchdog();ProgressWatchdog.Sample s=sample("farm");s.state="farm-default-ad";s.expectedWaitMs=180000;
  for(long now=1000;now<181000;now+=1000){s.inputs=0;check(w.observe(s,now)==null,"ordinary ad playback without taps is not an error");}
  check(w.observe(s,181000).code.equals("STATE_STALL"),"an ad state persisting beyond its wait budget reports a symptom");
  s.run=2;s.home=true;s.state="main";s.stateAge=3600000;
  for(long now=200000;now<4000000;now+=60000)check(w.observe(s,now)==null,"normal gameplay may stay in its main phase indefinitely");
  s.home=false;check(w.observe(s,4000000)==null,"one unrecognized frame after normal gameplay does not immediately report a stall");
  PhaseEngine e=new PhaseEngine();PhaseEngine.Phase p=new PhaseEngine.Phase();p.id="custom";e.phases.put(p.id,p);e.current=p.id;PhaseEngine.Watch wait=new PhaseEngine.Watch();wait.condition=PhaseEngine.TIMEOUT;wait.timeoutMs=300000;p.watches.add(wait);
  check(ProgressWatchdog.expectedWait(e)==330000,"an explicitly configured five-minute wait takes precedence over the default");
  s=sample("custom");s.run=4;s.expectFrames=false;s.frameAge=100000;s.expectedWaitMs=330000;w.observe(s,5000000);check(w.observe(s,5100000)==null,"an explicit long action may defer physical screenshots without a false frame error");
 }
 static void errorsAndIsolation(){
  ProgressWatchdog w=new ProgressWatchdog();ProgressWatchdog.Sample[] lanes=new ProgressWatchdog.Sample[4];for(int i=0;i<4;i++){lanes[i]=sample("app"+i);lanes[i].home=true;}
  lanes[0].analysisMs=1939;lanes[1].inputError="入力拒否";lanes[2].diagnostic="複数の分岐条件が成立";
  for(ProgressWatchdog.Sample s:lanes)w.observe(s,1000);
  check(w.observe(lanes[2],8999)==null,"transient branch overlap has an observation grace period");
  check(w.observe(lanes[2],9000).code.equals("BRANCH_CONFLICT"),"sustained ambiguous branches are identified separately");
  check(w.observe(lanes[0],16000).code.equals("ANALYSIS_SLOW"),"fresh source frames cannot hide consistently expired recognition results");
  check(w.observe(lanes[1],16000).code.equals("INPUT_REJECTED"),"an independent app reports persistent rejected input");
  check(w.observe(lanes[3],40000)==null,"one failed app cannot generate an error for a healthy app");
  lanes[1].inputs++;check(w.observe(lanes[1],17000)==null,"successful input clears the current input failure timer");
  lanes[0].running=false;check(w.observe(lanes[0],50000)==null&&!w.tracks.containsKey(lanes[0].key),"manual stop cancels monitoring without generating an error");
  lanes[2].terminal=true;check(w.observe(lanes[2],50000)==null,"normal completion is not a stalled state");
  w.retain(Collections.singleton(lanes[3].key));check(w.tracks.size()==1,"closing or replacing displays discards their old timers");
 }
 static void frozenReport(){
  ProgressWatchdog.Sample s=sample("com.kurashiru.fue.usagi");ProgressWatchdog.Event e=new ProgressWatchdog.Event("BRANCH_CONFLICT",s,"複数の分岐条件が成立",8000);
  String body=ProgressReport.build(e,"1.108","test device / API 35","直前の操作：キャンセル\n画像：1939ms\n",1000000);
  s.app="another";s.state="manual-stop";
  check(body.contains("com.kurashiru.fue.usagi")&&body.contains("検出時の状態：verify")&&!body.contains("manual-stop"),"manual changes cannot rewrite the captured incident");
  check(body.contains("BRANCH_CONFLICT")&&body.contains("キャンセル")&&body.contains("1939ms")&&body.contains("1.108"),"a pasted report carries the error, application state, operation, timing and exact version");
  check(body.contains("観測事実と推測を区別")&&body.contains("根本原因は未確認"),"symptoms are not presented as proven root causes");
  StringBuilder huge=new StringBuilder();for(int i=0;i<50000;i++)huge.append('x');check(ProgressReport.build(e,"1.108","test",huge.toString(),1).length()<12000,"export text is bounded even if a diagnostic field grows");
 }
 static void missingInput(){
  ProgressWatchdog w=new ProgressWatchdog();ProgressWatchdog.Sample s=sample("tap-main");s.home=true;s.expectInput=true;s.inputs=400;
  check(w.observe(s,1000)==null&&w.observe(s,15999)==null,"normal main-screen recognition alone does not immediately imply an input fault");
  check(w.observe(s,16000).code.equals("INPUT_NO_PROGRESS"),"a recognized main screen with no expected tap completions is reported");
  s.inputs=401;check(w.observe(s,17000)==null,"renewed input completion clears the outage");
  s.expectInput=false;check(w.observe(s,200000)==null,"an intentional pause or ad wait does not require tap completions");
  s.expectInput=true;check(w.observe(s,201000)==null,"resuming a tap phase starts a fresh input grace period");
  s.running=false;w.observe(s,202000);s.running=true;s.run=2;check(w.observe(s,400000)==null,"restarting cannot inherit a previous run's stalled-input timer");
 }
 public static void main(String[] args){transientAndStale();knownWaits();errorsAndIsolation();missingInput();frozenReport();System.out.println("ProgressWatchdogTest: "+checks+" checks passed; transient/real stalls, ad and declared waits, missing expected taps, four-app isolation, manual stops and immutable copy reports; Android notifications/clipboard untested");}
}
