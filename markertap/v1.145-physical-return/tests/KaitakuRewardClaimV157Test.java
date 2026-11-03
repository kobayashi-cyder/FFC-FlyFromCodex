package jp.tapmarker;
import java.util.*;
public final class KaitakuRewardClaimV157Test {
 static int checks;
 static void ok(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
 static KaitakuRewardClaimPolicy.Hit hit(String label,float...rect){return new KaitakuRewardClaimPolicy.Hit(label,rect);}
 static KaitakuRewardClaimPolicy.Decision decide(String phase,String text,KaitakuRewardClaimPolicy.Hit... hits){
   return KaitakuRewardClaimPolicy.decide(phase,text,Arrays.asList(hits));
 }
 static Map<String,float[]> ui(PhaseEngine e,String page,String label){
   Map<String,float[]> out=new HashMap<>();out.put("home",new float[]{1});out.put("main",new float[]{1});
   List<KaitakuNamedControls.Control> hits=label==null?Collections.emptyList():
      Arrays.asList(new KaitakuNamedControls.Control(label,new float[]{.7f,.42f,.85f,.46f}));
   KaitakuRewardNamedControls.apply(e,page,hits,out,1080,2400);return out;
 }
 public static void main(String[]args){
   String missions="kaitaku-default-missions";
   float[] b={.7f,.42f,.85f,.46f};
   ok(decide(missions,"デイリーミッション",hit("ゲット",b)).kind==KaitakuRewardClaimPolicy.Kind.CLAIM,"Get allowed");
   ok(decide(missions,"ミッション一覧",hit("一括で受け取る",b)).kind==KaitakuRewardClaimPolicy.Kind.CLAIM,"batch reward");
   ok(decide(missions,"デイリーミッション",hit("受け取る",b)).kind==KaitakuRewardClaimPolicy.Kind.CLAIM,"claim synonym");
   ok(decide(missions,"CMを見て30ゲット デイリーミッション",hit("CMを見て30ゲット",b)).kind!=KaitakuRewardClaimPolicy.Kind.CLAIM,"watch-ad action not Get");
   ok(decide(missions,"コインアップチャンス デイリーミッション",hit("ゲット",b)).kind==KaitakuRewardClaimPolicy.Kind.NONE,"modal priority");
   ok(decide("usagi-default-missions","デイリーミッション",hit("ゲット",b)).kind==KaitakuRewardClaimPolicy.Kind.NONE,"other game isolated");
   ok(decide(missions,"デイリーミッション",hit("ゲット",.7f,.98f,.85f,1f)).kind==KaitakuRewardClaimPolicy.Kind.HOLD,"unsafe footer blocked");
   ok(decide("kaitaku-default-mini","デイリーミッション",hit("ゲット",b)).kind==KaitakuRewardClaimPolicy.Kind.NONE,"mini not mission");
   ok(decide(missions,"報酬獲得",hit("閉じる",b)).kind==KaitakuRewardClaimPolicy.Kind.RECEIPT_CLOSE,"receipt can close");
   PhaseEngine e=new PhaseEngine();e.current=missions;
   Map<String,float[]> out=ui(e,"デイリーミッション ゲット","ゲット");
   ok(out.containsKey("get")&&out.containsKey("mission-page"),"candidate created");
   ok(out.get("home")[0]==0&&!out.containsKey("main"),"main tapping blocked");
   ok(!ui(e,"デイリーミッション ゲット","ゲット").containsKey("get"),"same row not repeated");
   e.current="kaitaku-default-claim-wait";
   out=ui(e,"報酬獲得 30コイン","閉じる");
   ok(out.containsKey("close")&&!out.containsKey("get"),"receipt close not Get");
   e.current=missions;
   ok(ui(e,"デイリーミッション ゲット","ゲット").containsKey("get"),"receipt unlock");
   ok(ui(e,"通常画面",null).containsKey("legacy"),"normal game delegates");
   KaitakuRewardClaimGate.reset(e);
   System.out.println("KaitakuRewardClaimV157Test: "+checks+" checks passed / reward balance still unverified");
 }
}
