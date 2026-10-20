package jp.tapmarker;

/** One display replacement per unresolved episode; only verified home starts another. */
final class DisplayRecovery {
 static final int NONE=0,REBUILD=1,STOP=2;
 static final long SETTLE_MS=30000,OPERATION_MS=30000,VERIFY_MS=90000;
 private long exhaustedSince=-1,startedAt=-1,createdAt=-1,homeAt=-1,homeSince=-1;
 private int homeFrames;private volatile int revision;private boolean issued,inFlight,failed;
 void observeHome(boolean home,long at,long now){
  if(at<0||at>now||now-at>=1500||at<=homeAt)return;
  homeAt=at;if(!home){homeSince=-1;homeFrames=0;return;}
  if(homeSince<0)homeSince=at;
  if(++homeFrames>=2&&at-homeSince>=350)reset();
 }
 int poll(boolean exhausted,long now){
  if(issued){if(failed||inFlight&&now-startedAt>=OPERATION_MS||!inFlight&&createdAt>=0&&now-createdAt>=VERIFY_MS)return STOP;return NONE;}
  if(!exhausted){exhaustedSince=-1;return NONE;}
  if(exhaustedSince<0)exhaustedSince=now;
  if(homeSince>=0&&homeAt<=now&&now-homeAt<1500)return NONE;
  return now-exhaustedSince>=SETTLE_MS?REBUILD:NONE;
 }
 int begin(long now){if(issued)return -1;issued=inFlight=true;failed=false;startedAt=now;return ++revision;}
 boolean current(int ticket){return issued&&inFlight&&revision==ticket;}
 boolean finished(int ticket,boolean success,long now){if(!current(ticket))return false;inFlight=false;failed=!success;createdAt=success?now:-1;return true;}
 void cancel(){revision++;inFlight=false;failed=true;}
 void reset(){revision++;exhaustedSince=startedAt=createdAt=homeSince=-1;homeFrames=0;issued=inFlight=failed=false;}
 boolean pending(){return inFlight;}
 String report(long now){return !issued?exhaustedSince<0?"画面再作成：未要求":"画面再作成：上限後の表示待ち / あと"+Math.max(0,(SETTLE_MS-(now-exhaustedSince)+999)/1000)+"秒":inFlight?"画面再作成：1/1 / 作成中":failed?"画面再作成：1/1 / 失敗・個別停止":"画面再作成：1/1 / 主画面確認待ち / あと"+Math.max(0,(VERIFY_MS-(now-createdAt)+999)/1000)+"秒";}
}
