package jp.tapmarker;

public class InputTargetRecoveryTest {
 static int checks;
 static void check(boolean ok,String text){checks++;if(!ok)throw new AssertionError(text);}
 static final String REPORT="IllegalStateException：仮想画面：IllegalArgumentException / Targeted input event injection from pid 14305 was not directed at a window owned by uid 10469.";
 public static void main(String[] args){
  check(InputTargetRecovery.foreign(PoipoiPreset.APP,"com.android.vending"),"store appearance starts recovery before an input rejection");
  check(!InputTargetRecovery.foreign(PoipoiPreset.APP,PoipoiPreset.APP)&&!InputTargetRecovery.foreign(PoipoiPreset.APP,"")&&!InputTargetRecovery.foreign(PoipoiPreset.APP,null)&&!InputTargetRecovery.foreign("","com.android.vending"),"missing window information or the original game cannot trigger recovery");
  check(InputTargetRecovery.changed(REPORT),"recognizes the exact real-device report through Binder wrapping");
  check(InputTargetRecovery.changed(InputTargetRecovery.MARKER+" display=152 / 表示アプリ=com.other"),"foreign observed package uses the same per-display recovery");
  check(!InputTargetRecovery.changed(null)&&!InputTargetRecovery.changed("SecurityException: permission denied")&&!InputTargetRecovery.changed("IllegalArgumentException: bad coordinates"),"unrelated exceptions do not trigger an app relaunch");
  InputTargetRecovery a=new InputTargetRecovery(),b=new InputTargetRecovery();
  a.rejected(1000);check(a.pending&&!b.pending,"only rejected display is suspended");
  check(a.begin(1749)<0,"waits for a window transition before requesting restore");
  int ticket=a.begin(1750);check(ticket>=0&&a.inFlight&&a.attempts==1,"one bounded restore request is claimed");
  check(a.begin(2000)<0,"duplicate callbacks cannot relaunch concurrently");
  a.finished(ticket,true,2200);
  check(!a.confirm(true,1700,2700),"a frame captured before restore cannot confirm the target");
  check(!a.confirm(false,2500,2700),"another visible package cannot confirm the target");
  check(!a.confirm(true,2500,2699),"settling delay cannot be bypassed");
  check(!a.confirm(true,2500,4001),"stale frame cannot resume input");
  check(a.confirm(true,2500,2700)&&!a.pending&&a.attempts==1,"matching fresh post-restore frame permits reclassification without erasing failed-input budget");
  a.rejected(2800);check(a.begin(11000)<0,"a second rejection retains the ten-second relaunch limit");
  ticket=a.begin(11750);a.finished(ticket,false,12000);
  check(a.attempts==2&&!a.confirm(true,12300,13000),"failed restore does not pretend the game has returned");
  check(a.begin(21749)<0,"failed relaunch still has a cooldown");
  ticket=a.begin(21750);a.finished(ticket,false,22000);
  check(a.held()&&a.begin(100000)<0&&a.attempts==3,"third failure holds only this display instead of endlessly relaunching");
  b.rejected(30000);check(b.begin(30750)>=0,"other display has an independent retry budget");
  int old=b.revision;b.reset();b.finished(old,true,31000);
  check(!b.pending&&!b.restored&&!b.inFlight&&b.attempts==0,"stop invalidates an in-flight completion");
  b.rejected(32000);int fresh=b.begin(32750);b.finished(old,true,33000);
  check(b.inFlight,"an old completion cannot confirm a replacement attempt");
  b.finished(fresh,true,33000);check(b.confirm(true,33100,33500),"new attempt can resume after real confirmation");
  b.accepted();check(b.attempts==0&&!b.pending,"an accepted input resets the rejection budget");
  a.reset();check(a.begin(100001)<0&&!a.held(),"restart clears a held display without scheduling an unrequested restore");
  System.out.println("InputTargetRecoveryTest: "+checks+" checks passed; exact report and independent bounded recovery, Android launch not exercised");
 }
}
