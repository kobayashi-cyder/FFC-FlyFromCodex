package jp.tapmarker;

/** Recognition may run during main taps. A visible exit, stale image or input action stops them. */
public final class ContinuousTapPolicy {
 public static final long MAX_FRAME_AGE_MS=1500;
 public static boolean allow(PhaseEngine e,boolean confirmed,boolean collectionPending,boolean acting,boolean returning,boolean insideApp,long observedAt,long now){
  return holdReason(e,confirmed,collectionPending,acting,returning,insideApp,observedAt,now).isEmpty();
 }
 public static String holdReason(PhaseEngine e,boolean confirmed,boolean collectionPending,boolean acting,boolean returning,boolean insideApp,long observedAt,long now){
  PhaseEngine.Phase phase=e.phases.get(e.current);
  if(!insideApp)return "対象アプリの表示待ち";
  if(phase==null)return "現在状態が未設定";
  if(phase.terminal)return "終了状態";
  if(!phase.markersDuring)return "連打しない状態（ボタン操作・表示確認）";
  if(returning)return "アプリへの復帰待ち";
  if(acting)return "ボタン操作・操作結果の確認待ち";
  if(collectionPending)return "ミッション・報酬の回収待ち";
  if(!confirmed)return "通常プレイ画面の認識待ち";
  if(now<observedAt||now-observedAt>=MAX_FRAME_AGE_MS)return "認識画像が1.5秒以上古い・新しい画像待ち";
  if(e.awaitingBranch())return "CM・ダイアログ等への分岐確認待ち";
  return "";
 }
 private ContinuousTapPolicy(){}
}
