package jp.tapmarker;

/** One unresolved display episode, independent of state entry and intent acknowledgements. */
final class RecoveryEscalation {
 static final int RETURN_LIMIT=3,RESTART_LIMIT=2;
 static final long STALL_MS=30000,SETTLE_MS=30000,FRESH_MS=1500,CONFIRM_MS=350;
 private long unknownSince=-1,observedAt=-1,knownSince=-1,nextAt;
 private boolean known;private int unknownFrames,knownFrames,returns,restarts;
 void reset(){unknownSince=observedAt=knownSince=-1;nextAt=0;known=false;unknownFrames=knownFrames=returns=restarts=0;}
 void observe(boolean visible,long at,long now){
  if(at<0||at>now||now-at>=FRESH_MS||at<=observedAt)return;
  observedAt=at;known=visible;
  if(visible){
   unknownSince=-1;unknownFrames=0;
   if(knownSince<0){knownSince=at;knownFrames=0;}
   knownFrames++;
   // A transient match revokes dispatch, but cannot replenish the budget.
   if(knownFrames>=2&&at-knownSince>=CONFIRM_MS){returns=restarts=0;nextAt=0;}
  }else{
   knownSince=-1;knownFrames=0;if(unknownSince<0)unknownSince=at;unknownFrames++;
  }
 }
 boolean unknownCurrent(long now){return !known&&unknownFrames>=2&&observedAt>=0&&now>=observedAt&&now-observedAt<FRESH_MS;}
 boolean restartReady(long now){return returns>=RETURN_LIMIT&&restarts<RESTART_LIMIT&&unknownCurrent(now)&&now-unknownSince>=STALL_MS&&now>=nextAt;}
 boolean exhausted(){return restarts>=RESTART_LIMIT;}
 boolean confirmedHome(long now){return known&&knownFrames>=2&&knownSince>=0&&observedAt-knownSince>=CONFIRM_MS&&now>=observedAt&&now-observedAt<FRESH_MS;}
 boolean normalAllowed(long now){return (!known||now>=observedAt&&now-observedAt>=FRESH_MS)&&returns<RETURN_LIMIT&&!exhausted()&&now>=nextAt;}
 /** Charge attempted dispatches, including failed or invalidated callbacks. */
 void sent(long now,boolean restart){if(restart){restarts++;nextAt=now+SETTLE_MS;}else returns++;}
 String status(long now){
  if(returns==0&&restarts==0)return "";
  if(exhausted())return "復帰：再起動の上限 / 新しい表示と閉じる操作を監視中";
  if(now<nextAt)return "復帰：再起動後の表示待ち / あと"+((nextAt-now+999)/1000)+"秒";
  if(returns>=RETURN_LIMIT)return "復帰：通常復帰で変化なし / 新しい画像で再起動を確認中";
  return "復帰：ゲーム画面を再確認中 / 通常復帰 "+returns+"回";
 }
 String report(long now){return "通常復帰要求 "+returns+"/"+RETURN_LIMIT+" / タスク再起動要求 "+restarts+"/"+RESTART_LIMIT+" / 未判別 "+(unknownSince<0?0:Math.max(0,now-unknownSince))+"ms / "+status(now);}
}
