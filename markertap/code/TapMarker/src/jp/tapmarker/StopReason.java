package jp.tapmarker;
/** Human-readable reason plus a code location retained only in the status report. */
final class StopReason {
 static StackTraceElement origin(StackTraceElement[] stack){
  for(StackTraceElement e:stack)if(e.getClassName().startsWith("jp.tapmarker.")&&!e.getClassName().equals(StopReason.class.getName())&&!(e.getClassName().equals("jp.tapmarker.TapService")&&(e.getMethodName().equals("stop")||e.getMethodName().equals("stopAll"))))return e;
  return null;
 }
 static String describe(StackTraceElement e){
  if(e==null)return "停止要求";
  String owner=e.getClassName(),method=e.getMethodName();
  if(owner.equals("jp.tapmarker.VirtualWindows")){
   if(method.equals("createViews"))return "仮想画面の作成準備";
   if(method.equals("remove"))return "仮想画面を閉じた";
   if(method.equals("close")||method.equals("destroy"))return "仮想画面を全て閉じた";
   if(method.equals("backendLost"))return "端末内実行部の接続が切れた";
  }
  if(owner.equals("jp.tapmarker.WirelessAdb"))return "端末内接続の起動準備";
  if(owner.equals("jp.tapmarker.SplitLauncher"))return "対象アプリ・表示方式の変更";
  if(owner.equals("jp.tapmarker.TapService")){
   if(method.equals("switchProfile")||method.equals("lambda$new$"))return "本体の対象アプリ切替";
   if(method.equals("onConfigurationChanged"))return "本体画面の構成変更";
   if(method.equals("close"))return "アプリの実行部終了";
  }
  if(owner.startsWith("jp.tapmarker.Phase")||owner.equals("jp.tapmarker.GraphEditor")||owner.equals("jp.tapmarker.GraphUi")||owner.equals("jp.tapmarker.EasyEditorUi"))return "オートマトンの編集";
  return "操作・設定の変更";
 }
}
