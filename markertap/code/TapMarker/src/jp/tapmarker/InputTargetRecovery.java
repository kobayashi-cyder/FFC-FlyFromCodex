package jp.tapmarker;

/** Per-display recovery after a rejected target. Never changes the injection UID. */
final class InputTargetRecovery {
 static final String MARKER="[TARGET_WINDOW_CHANGED]";
 static final int MAX_RESTORES=3;
 static final long SETTLE_MS=750,RECHECK_MS=500,CONFIRM_TIMEOUT_MS=5000,RESTORE_GAP_MS=10000;
 boolean pending,inFlight,restored;int attempts;long nextAt,barrier,confirmUntil,lastRestoreAt=-RESTORE_GAP_MS;int revision;
 private long foreignSince=-1,foreignFrame=-1,ownFrame=-1,ownSince=-1;private int foreignFrames,ownFrames;
 static boolean foreign(String expected,String actual){return expected!=null&&!expected.isEmpty()&&actual!=null&&!actual.isEmpty()&&!expected.equals(actual);}
 static boolean changed(String reason){return reason!=null&&(reason.contains(MARKER)||reason.contains("Targeted input event injection from pid ")&&reason.contains("was not directed at a window owned by uid "));}
 void observePackage(String expected,String actual,long frameAt,long now){
  if(frameAt<=foreignFrame||frameAt>now||now-frameAt>1500)return;
  foreignFrame=frameAt;
  if(!foreign(expected,actual)){foreignSince=-1;foreignFrames=0;return;}
  if(foreignSince<0)foreignSince=frameAt;
  if(++foreignFrames>=2&&frameAt-foreignSince>=SETTLE_MS)rejected(now);
 }
 void rejected(long now){if(pending)return;pending=true;restored=false;barrier=now;ownFrame=ownSince=-1;ownFrames=0;nextAt=Math.max(now+SETTLE_MS,lastRestoreAt+RESTORE_GAP_MS);revision++;}
 int begin(long now){if(!pending||inFlight||attempts>=MAX_RESTORES||now<nextAt||restored&&now<confirmUntil)return -1;attempts++;inFlight=true;restored=false;barrier=now;lastRestoreAt=now;return revision;}
 void finished(int ticket,boolean success,long now){if(!pending||!inFlight||ticket!=revision)return;inFlight=false;restored=success;confirmUntil=now+CONFIRM_TIMEOUT_MS;nextAt=Math.max(now+RECHECK_MS,lastRestoreAt+RESTORE_GAP_MS);}
 boolean confirm(boolean ownPackage,long frameAt,long now){
  if(!pending||inFlight||frameAt>now||frameAt<=barrier||now-frameAt>1500)return false;
  if(!ownPackage){ownSince=-1;ownFrames=0;return false;}
  if(restored){if(now<confirmUntil-CONFIRM_TIMEOUT_MS+RECHECK_MS)return false;}
  else{
   // A store sheet may dismiss itself, even after a failed restore or a held
   // budget. Two new own-app frames let the normal visual engine take over.
   if(frameAt<=ownFrame)return false;ownFrame=frameAt;
   if(ownSince<0)ownSince=frameAt;
   if(++ownFrames<2||frameAt-ownSince<RECHECK_MS||now<barrier+SETTLE_MS)return false;
  }
  pending=false;restored=false;foreignSince=ownSince=-1;foreignFrames=ownFrames=0;revision++;return true;
 }
 boolean held(){return pending&&!inFlight&&attempts>=MAX_RESTORES;}
 void accepted(){reset();}
 void reset(){pending=false;inFlight=false;restored=false;attempts=0;nextAt=barrier=confirmUntil=0;lastRestoreAt=-RESTORE_GAP_MS;foreignSince=foreignFrame=ownFrame=ownSince=-1;foreignFrames=ownFrames=0;revision++;}
 String status(){return !pending?"":inFlight?"対象アプリへ復帰中":held()?"入力先を確認できません / この画面だけ待機（再開始で再試行）":"入力先の切替を検出 / 対象アプリを再確認中";}
}
