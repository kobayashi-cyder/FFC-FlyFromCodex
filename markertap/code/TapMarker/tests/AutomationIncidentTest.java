package jp.tapmarker;

public final class AutomationIncidentTest {
 static int checks;
 static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 public static void main(String[] args){
  String app=UsagiPreset.APP,state=UsagiPreset.PREFIX+"use-wait",reason="複数の分岐条件が成立 / 領域と見本画像を確認してください";
  String incident=AutomationIncident.remember("",app,state,reason,1000);
  check(incident.contains(app)&&incident.contains(state)&&incident.contains(reason),"the actual target, confirmation state and reason are retained together");
  check(AutomationIncident.remember(incident,app,state,"全停止操作",2000).equals(incident),"manual stop cannot erase the preceding incident");
  check(AutomationIncident.remember(incident,app,"main","通常連打を再開",3000).equals(incident),"successful resumption does not overwrite the incident");
  check(AutomationIncident.remember(incident,app,state,reason,4000).equals(incident),"repeated polling preserves the first time instead of rewriting storage");
  check(AutomationIncident.remember(incident,app,"ad","農園：広告エラー消失 / 主画面の再確認",5000).equals(incident),"a recovery notification is not recorded as a new error");
  String replacement=AutomationIncident.remember(incident,CoinPreset.APP,"coin-default-ad","入力エラー：対象アプリが切り替わりました",6000);
  check(replacement.contains(CoinPreset.APP)&&!replacement.contains(app),"another incident identifies its own application and state");
  check(AutomationIncident.remember(null,app,state,"",7000).isEmpty(),"no fabricated incident before the first failure");
  check(AutomationIncident.remember(incident,app,state,"フェーズ遷移に失敗：missing state",8000).contains("missing state"),"transition exceptions retain their reason");
  StringBuilder huge=new StringBuilder("エラー：");for(int i=0;i<20000;i++)huge.append('x');
  check(AutomationIncident.remember(incident,app,state,huge.toString(),9000).length()<1700,"incident storage stays bounded for a long exception message");
  System.out.println("AutomationIncidentTest: "+checks+" checks passed; bounded incident text, deduplication and retention across manual stop/success; Android preference storage untested");
 }
}
