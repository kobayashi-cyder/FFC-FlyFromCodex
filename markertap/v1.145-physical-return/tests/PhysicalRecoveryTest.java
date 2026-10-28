package jp.tapmarker;
import android.os.SystemClock;
import android.content.Intent;
final class PhysicalRecoveryTest {
 static int checks;static void check(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
 static void cycle(AutoPilot p,long now){SystemClock.now=now;PhysicalRecoveryBridge.defer(p,p.token);}
 static ProgressWatchdog.Sample sample(AutoPilot p,long age){ProgressWatchdog.Sample s=new ProgressWatchdog.Sample();s.app=p.target;s.key=p.target+":0";s.run=p.token;s.state=p.phases.engine.current;s.separateAnalysis=true;s.frameAge=age;s.capturedAt=SystemClock.now-age;s.analysisAt=s.capturedAt;s.analysisAge=age;return s;}
 static void charged(AutoPilot p,int normal,int restart,long now){for(int i=0;i<normal;i++)p.adExit.returned(now,false);for(int i=0;i<restart;i++)p.adExit.returned(now,true);p.lastReturnIntent=now;}
 public static void main(String[] ignored){
  // Reproduce this incident: 3/3 normal, 1/2 restart, 15s-old image, 15s settlement left.
  AutoPilot p=new AutoPilot();charged(p,3,1,85000);cycle(p,100000);long epoch=p.phases.engine.epoch;
  ProgressWatchdog watchdog=new ProgressWatchdog();
  for(long now=100350;now<115000;now+=350){cycle(p,now);check(p.phases.engine.epoch==epoch,"same-state reentry during settlement");check(p.s.sent==0,"launch before shared cooldown");check(PhysicalRecoveryBridge.observe(watchdog,sample(p,15789),now)==null,"false FRAME_STALE during external wait");}
  check(PhysicalRecoveryBridge.append("report",p.target).contains("foreground=com.example.external"),"missing foreground diagnostics");
  cycle(p,115050);check(p.s.sent==1,"bounded external restart must not require unavailable game image");check(p.adExit.recovery.physicalRestarts()==2,"original restart counter not shared");check((p.s.last.flags&Intent.FLAG_ACTIVITY_CLEAR_TASK)!=0,"restart flag absent");
  cycle(p,145049);check(p.active&&p.s.sent==1,"last restart must settle");cycle(p,145050);check(!p.active&&p.s.progressErrors.fatals==1&&p.s.progressErrors.code.equals("FOREGROUND_RETURN_FAILED"),"exhaustion must stop with correct reason");

  // Entire budget: exactly three normal returns and two restarts, with no repeated entry.
  p=new AutoPilot();for(long now=200000;now<290000&&p.active;now+=350)cycle(p,now);
  check(p.s.sent==5,"unexpected attempted launch count");check(p.adExit.recovery.physicalReturns()==3&&p.adExit.recovery.physicalRestarts()==2,"attempt limits bypassed");check(p.phases.engine.epoch==1,"external loop reset state");check(!p.active,"budget exhaustion failed to stop");

  // Unknown/system UI never dispatches a launch and cannot postpone the deadline.
  p=new AutoPilot();p.foreground="";cycle(p,400000);cycle(p,489999);check(p.s.sent==0&&p.active,"unknown package launched");
  ProgressWatchdog.Event e=PhysicalRecoveryBridge.observe(new ProgressWatchdog(),sample(p,90000),490000);
  check(e!=null&&e.code.equals("FOREGROUND_RETURN_FAILED"),"independent 90s foreground deadline absent");check(p.active,"stop invalidated report before saving");p.s.h.drain();check(!p.active,"deadline stop not posted");

  // A returned foreground gets a finite reacquisition grace; actual capture faults still report.
  p=new AutoPilot();cycle(p,600000);p.foreground=p.target;cycle(p,620000);check(p.phaseSampleTakenAt==0,"stale game image retained");
  ProgressWatchdog.Sample old=sample(p,20000);long captured=old.capturedAt;
  check(PhysicalRecoveryBridge.observe(new ProgressWatchdog(),old,620000)==null,"immediate false stale on foreground return");check(old.capturedAt==captured,"fake capture timestamp");
  e=PhysicalRecoveryBridge.observe(new ProgressWatchdog(),sample(p,35000),635000);check(e!=null&&e.code.equals("FRAME_STALE"),"real physical capture stall masked");

  // A newly captured frame still needs analysis; healthy results end the grace immediately.
  ProgressWatchdog.Sample healthy=sample(p,20);healthy.capturedAt=635100;healthy.analysisAt=635100;healthy.frameAge=20;healthy.analysisAge=20;
  check(PhysicalRecoveryBridge.observe(new ProgressWatchdog(),healthy,635120)==null,"fresh results rejected");check(healthy.expectFrames,"fresh results still suppressing watchdog");

  // Guarded screen and a blocking dialog charge no attempts.
  p=new AutoPilot();p.s.guarded=true;cycle(p,700000);cycle(p,702000);check(p.s.sent==0&&p.adExit.recovery.physicalReturns()==0,"guard bypass");p.s.guarded=false;p.s.dialog=true;cycle(p,703000);check(p.s.sent==0,"dialog bypass");p.s.dialog=false;cycle(p,704000);check(p.s.sent==1,"recovery failed after guard cleared");

  // Failed launch is charged once and rate limited, rather than retried every poll.
  p=new AutoPilot();p.s.launchFails=true;cycle(p,800000);cycle(p,802000);cycle(p,802350);check(p.s.sent==1&&p.adExit.recovery.physicalReturns()==1,"failed launch budget not charged");check(PhysicalRecoveryBridge.append("",p.target).contains("起動例外"),"launch failure not recorded");

  // Missing launch intent also has backoff and an absolute deadline.
  p=new AutoPilot();p.s.pm.missing=true;cycle(p,900000);cycle(p,902000);check(p.s.sent==0,"missing Intent dispatched");cycle(p,990000);check(!p.active,"missing launch target waits forever");

  // Other games, custom profiles and virtual sessions retain their original entrypoint.
  for(int mode=0;mode<3;mode++){p=new AutoPilot();if(mode==0)p.target="com.kurashiru.nouen.usagi";if(mode==1)p.phases.customized=true;if(mode==2)p.splitPilot.active=true;cycle(p,1100000);check(p.delegations==1&&p.s.sent==0,"unrelated runner intercepted");}

  // Duplicate/stale home observations cannot reset an episode; two fresh distinct ones can.
  PhysicalReturnEpisode g=new PhysicalReturnEpisode();g.begin(1200000);check(!g.home(true,1200100,1,1200100),"one frame cleared episode");check(!g.home(true,1200100,1,1200500),"duplicate cleared episode");check(!g.home(true,1200500,2,1200500),"changed window cleared episode");check(g.home(true,1200900,2,1200900)&&g.started<0,"fresh same-window pair failed");
  g.begin(1300000);check(!g.home(true,1298000,2,1300000)&&g.started==1300000,"stale home cleared episode");

  // A short foreground bounce never renews the unresolved-episode deadline.
  p=new AutoPilot();cycle(p,1400000);p.foreground=p.target;cycle(p,1440000);p.foreground="com.android.systemui";cycle(p,1489999);check(p.active,"deadline too early");cycle(p,1490000);check(!p.active,"foreground bounce reset deadline");

  // A replaced run must not inherit a previous run's capture suppression.
  p=new AutoPilot();cycle(p,1600000);p.token++;ProgressWatchdog.Sample replacement=sample(p,16000);e=PhysicalRecoveryBridge.observe(new ProgressWatchdog(),replacement,1616000);check(e!=null&&e.code.equals("FRAME_STALE"),"old run suppressed new run watchdog");
  System.out.println("Physical foreground recovery: 12 scenario groups passed; "+checks+" assertions");
 }
}
