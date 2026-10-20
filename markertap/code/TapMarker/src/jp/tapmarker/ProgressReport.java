package jp.tapmarker;

import java.text.SimpleDateFormat;
import java.util.*;

/** Frozen, bounded text paired with a separate local diagnostic archive. */
final class ProgressReport {
 static String build(ProgressWatchdog.Event event,String version,String device,String details,long wallTime){
  String date=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z",Locale.ROOT).format(new Date(wallTime));
  return "MarkerTap 進行不良レポート\n"
   +"以下の診断データをもとに進行不良の原因候補と修正案を調べてください。観測事実と推測を区別してください。\n"
   +"発生日時："+date+"\nバージョン："+AutomationIncident.limit(version,80)
   +"\n端末："+AutomationIncident.limit(device,240)+"\nエラーコード："+event.code
   +"\n対象アプリ："+AutomationIncident.limit(event.app,200)+"\n検出時の状態："+AutomationIncident.limit(event.state,400)
   +"\n観測した症状："+AutomationIncident.limit(event.reason,1000)+"\n継続時間："+event.duration+"ms\n"
   +"--- 検出時点の診断データ ---\n"+AutomationIncident.limit(details,10000)
   +"\n--- 診断データここまで ---\n"
   +"このレポートは端末上で観測した状態です。ゲームの完了・報酬加算・根本原因は未確認です。必要に応じて該当APK・ソース・録画と照合してください。\n";
 }
 private ProgressReport(){}
}
