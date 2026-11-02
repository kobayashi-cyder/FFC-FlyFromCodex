package jp.tapmarker;
import java.util.*;
public final class KaitakuVideoNamedControlsTest {
 static void check(boolean yes,String msg){if(!yes)throw new AssertionError(msg);}
 static Map<String,float[]> run(String text,String label,boolean button){
  Map<String,float[]> out=new HashMap<>();out.put("home",new float[]{1});out.put("main",new float[]{1});
  List<KaitakuNamedControls.Control> list=new ArrayList<>();
  if(button)list.add(new KaitakuNamedControls.Control(label,new float[]{.32f,.50f,.67f,.56f}));
  KaitakuVideoNamedControls.apply(new PhaseEngine(),text,list,out,1080,2424);return out;
 }
 public static void main(String[] args){
  Map<String,float[]> d=run("タップ２倍 １タップで進む量が２倍 使用する キャンセル","キャンセル",true);
  check(d.get("close")[0]==1&&d.get("home")[0]==0&&!d.containsKey("main"),"boost dismiss not exclusive");
  check(d.get("close-bounds")[1]==.50f,"wrong target bounds");
  d=run("コインアップチャンス!! ステージクリア時のコインがアップ このままはじめる","このままはじめる",true);
  check(d.get("close")[0]==1&&d.get("home")[0]==0,"coin chance opt out");
  d=run("コインアップチャンス!! CMを見て30ゲット このままはじめる","このままはじめる",true);
  check(d.get("close")[0]==1&&d.get("home")[0]==0,"coin continuation without body");
  d=run("デイリーミッション コインアップチャンス","このまま始める",true);
  check(d.get("close")[0]==1&&d.get("home")[0]==0,"mission background must not mask modal");
  d=run("コインアップチャンス!! ステージクリア時のコインがアップ CMを見て30ゲット","CMを見て30ゲット",true);
  check(!d.containsKey("close")&&d.get("home")[0]==0&&!d.containsKey("main"),"unknown optout must hold");
  d=run("デイリーミッション タップ2倍を3回使用する プレイ","プレイ",true);
  check(d.containsKey("legacy")&&!d.containsKey("close"),"existing mission classifier not delegated");
  d=run("開拓する タップ2倍","タップ2倍",true);
  check(d.containsKey("legacy")&&d.get("home")[0]==1,"unrecognized screen changed");
  // Same engine and unchanged popup must never produce a close candidate on
  // every fast scan. A different ordinary screen resets that suppression.
  PhaseEngine engine=new PhaseEngine();
  List<KaitakuNamedControls.Control> controls=Arrays.asList(
    new KaitakuNamedControls.Control("このままはじめる",new float[]{.32f,.50f,.67f,.56f}));
  Map<String,float[]> paced=new HashMap<>();
  KaitakuVideoNamedControls.apply(engine,"コインアップチャンス",controls,paced,1080,2424);
  check(paced.containsKey("close"),"first safe close must not wait");
  paced.clear();
  KaitakuVideoNamedControls.apply(engine,"コインアップチャンス",controls,paced,1080,2424);
  check(!paced.containsKey("close")&&paced.get("home")[0]==0,"duplicate popup must be held");
  paced.clear();
  KaitakuVideoNamedControls.apply(engine,"通常画面 開拓する",controls,paced,1080,2424);
  paced.clear();
  KaitakuVideoNamedControls.apply(engine,"コインアップチャンス",controls,paced,1080,2424);
  check(paced.containsKey("close"),"changed screen resets duplicate guard");
  System.out.println("KaitakuVideoNamedControls: 10 integration checks passed");
 }
}
