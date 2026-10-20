package jp.tapmarker;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** A bounded text incident survives later manual actions; no captured pixels. */
final class AutomationIncident {
 static boolean notable(String detail){
  if(detail==null||detail.contains("エラー消失")||detail.contains("エラーが消え")||detail.contains("エラー解消"))return false;
  return detail.contains("複数の分岐条件が成立")||detail.contains("失敗")||detail.contains("エラー")||detail.contains("Exception");
 }
 static String limit(String text,int length){if(text==null)return "";return text.length()<=length?text:text.substring(0,length);}
 static String remember(String previous,String app,String state,String detail,long wallTime){
  if(previous==null)previous="";if(!notable(detail))return previous;
  String body="対象："+limit(app,200)+"\n状態："+limit(state,400)+"\n理由："+limit(detail,1000);
  if(previous.endsWith("\n"+body))return previous;
  return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.ROOT).format(new Date(wallTime))+"\n"+body;
 }
 private AutomationIncident(){}
}
