package jp.tapmarker;

import java.util.*;

public final class KaitakuVideoModalPolicyTest {
 static int checks;
 static void check(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
 static KaitakuVideoModalPolicy.Hit hit(String label,float x,float y,float a,float b){return new KaitakuVideoModalPolicy.Hit(label,new float[]{x,y,a,b});}
 static KaitakuVideoModalPolicy.Decision decide(String text,KaitakuVideoModalPolicy.Hit... hits){return KaitakuVideoModalPolicy.decide(text,Arrays.asList(hits));}
 public static void main(String[] args){
  String coin="コインアップチャンス!! ステージクリア時のコインがアップ CMを見て30ゲット このままはじめる";
  String boost="タップ２倍 １タップで進む量が２倍！ 使用する キャンセル";
  KaitakuVideoModalPolicy.Decision d=decide(coin,hit("このままはじめる",.3f,.49f,.7f,.53f));
  check(d.result==KaitakuVideoModalPolicy.Result.DISMISS&&d.kind.equals("coin-chance"),"coin opt-out");
  check(d.bounds[0]==.3f,"bounds retention");
  d.bounds[0]=0;check(decide(coin,hit("このままはじめる",.3f,.49f,.7f,.53f)).bounds[0]==.3f,"defensive bounds");
  check(decide(boost,hit("キャンセル",.38f,.55f,.67f,.60f)).result==KaitakuVideoModalPolicy.Result.DISMISS,"boost opt-out");
  check(decide(boost).result==KaitakuVideoModalPolicy.Result.HOLD,"boost missing button must hold");
  check(decide(coin,hit("CMを見て30ゲット",.2f,.43f,.75f,.47f)).result==KaitakuVideoModalPolicy.Result.HOLD,"cannot authorize ad");
  check(decide("デイリーミッション タップ2倍を3回使用する キャンセル").result==KaitakuVideoModalPolicy.Result.NONE,"mission task is not a modal");
  check(decide("タップ2倍",hit("キャンセル",.4f,.6f,.6f,.65f)).result==KaitakuVideoModalPolicy.Result.NONE,"home boost badge cannot block normal play");
  check(decide(coin,hit("このままはじめる",.3f,.90f,.7f,.95f)).result==KaitakuVideoModalPolicy.Result.HOLD,"unsafe geometry");
  check(decide(coin,hit("このままはじめる",Float.NaN,.49f,.7f,.53f)).result==KaitakuVideoModalPolicy.Result.HOLD,"nan geometry");
  check(decide(coin,hit("このままはじめる",.3f,.49f,2f,.53f)).result==KaitakuVideoModalPolicy.Result.HOLD,"offscreen geometry");
  check(decide("通常画面　開拓する",hit("このままはじめる",.3f,.49f,.7f,.53f)).result==KaitakuVideoModalPolicy.Result.NONE,"no popup evidence");
  check(decide("コインアップチャンス",hit("このままはじめる",.3f,.49f,.7f,.53f)).result==KaitakuVideoModalPolicy.Result.NONE,"need two independent text anchors");
  check(decide(boost,hit("使用する",.1f,.5f,.8f,.6f)).result==KaitakuVideoModalPolicy.Result.HOLD,"no autoplay rewarded ads");
  System.out.println("KaitakuVideoModalPolicy: "+checks+" checks passed");
 }
}
