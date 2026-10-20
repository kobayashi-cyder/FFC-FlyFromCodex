package jp.tapmarker;

/** Dispatch acceptance, target-package presence and playable home are different observations. */
final class RestoreReceipt {
 int revision,requests,restartRequests,accepted,failures,packageConfirmed,homeConfirmed;
 private boolean waiting,acknowledged,packageSeen,homeSeen,currentOwn;
 private long acceptedAt=-1,lastPackage=-1,packageSince=-1,lastHome=-1,homeSince=-1;
 private int packageFrames,homeFrames;
 int begin(boolean restart,long now){requests++;if(restart)restartRequests++;revision++;waiting=true;acknowledged=packageSeen=homeSeen=currentOwn=false;acceptedAt=-1;lastPackage=packageSince=lastHome=homeSince=-1;packageFrames=homeFrames=0;return revision;}
 void accepted(int ticket,long now){if(ticket!=revision||!waiting||acknowledged)return;accepted++;acknowledged=true;acceptedAt=now;}
 void failed(int ticket){if(ticket!=revision||!waiting)return;failures++;waiting=false;acknowledged=false;}
 boolean fresh(long at,long now){return acknowledged&&at>acceptedAt&&at<=now&&now-at<1500;}
 void observePackage(boolean own,long at,long now){
  if(!fresh(at,now)||at<=lastPackage)return;lastPackage=at;currentOwn=own;
  if(!own){packageSince=homeSince=-1;packageFrames=homeFrames=0;return;}
  if(packageSeen)return;if(packageSince<0)packageSince=at;
  if(++packageFrames>=2&&at-packageSince>=350){packageSeen=true;packageConfirmed++;}
 }
 void observeHome(boolean home,long at,long now){
  if(!fresh(at,now)||at<=lastHome)return;lastHome=at;
  if(!home||!currentOwn||now-lastPackage>=1500){homeSince=-1;homeFrames=0;return;}
  if(homeSeen)return;if(homeSince<0)homeSince=at;
  if(++homeFrames>=2&&at-homeSince>=350&&packageSeen){homeSeen=true;homeConfirmed++;waiting=false;}
 }
 String report(){String state=requests==0?"要求なし":!acknowledged?waiting?"受付待ち":"要求失敗":homeSeen?"主画面を確認":packageSeen?"対象アプリ表示を確認・主画面は未確認":"受付済み・表示は未確認";return "要求 "+requests+"回（タスク再起動要求 "+restartRequests+"回） / 受付 "+accepted+"回 / 対象アプリ表示確認 "+packageConfirmed+"回 / 主画面確認 "+homeConfirmed+"回 / 失敗 "+failures+"回 / "+state;}
}
