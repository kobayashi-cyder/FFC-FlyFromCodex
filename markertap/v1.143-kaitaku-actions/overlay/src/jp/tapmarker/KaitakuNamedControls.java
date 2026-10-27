package jp.tapmarker;
import java.util.*;

/** Exact labels with independent page context; never turns an unnamed surface into a target. */
final class KaitakuNamedControls {
 static final class Control {final String label;final float[] bounds;Control(String s,float[] b){label=s;bounds=b.clone();}}
 static String text(String s){return s==null?"":s.replaceAll("[\\s\\u3000]","");}
 static Control find(List<Control> controls,String... names){for(String name:names)for(Control c:controls)if(text(c.label).equals(name))return c;return null;}
 static void apply(PhaseEngine engine,String context,List<Control> controls,Map<String,float[]> out,int w,int h){
  String scope=text(context);boolean mission=scope.contains("デイリーミッション"),mini=scope.contains("タップゲーム")||scope.contains("連打ゲーム")||scope.contains("タップチャレンジ");
  boolean reward=scope.contains("報酬獲得")||scope.contains("報酬を獲得")||scope.contains("コインゲット");
  Control c=reward?find(controls,"閉じる","とじる"):null;
  if(c!=null){clear(out);KaitakuActionEvidence.put(out,"close",c.bounds);return;}
  if(mini){
   c=find(controls,"結果を閉じる","ゲームに戻る");String key="mini-end";
   if(c==null){c=find(controls,"スタート","はじめる","ゲーム開始");key="mini-start";}
   if(c==null){c=find(controls,"ここをタップ","タップ","連打","TAP");key="mini-live";}
   if(c==null)return;
   // The live surface must lie in the game content, above the bottom banner.
   if(key.equals("mini-live")&&(c.bounds[1]<.20f||c.bounds[3]>.90f))return;
   clear(out);KaitakuActionEvidence.put(out,key,c.bounds);
   if(key.equals("mini-live"))out.put(AdaptiveLayout.MAIN,RegionMath.center(c.bounds,w,h));return;
  }
  if(!mission)return;
  c=find(controls,"ゲット","受け取る","報酬を受け取る","一括受取","一括で受け取る");String key="get";
  if(c==null){c=find(controls,"プレイ","ゲームへ戻る");key="play";}
  if(c==null){c=find(controls,"戻る");key="back";}
  if(c==null)return;
  clear(out);KaitakuActionEvidence.put(out,"mission-page",null);
  if(engine.current.equals(KaitakuPreset.P+"missions"))KaitakuActionEvidence.put(out,key,c.bounds);
 }
 static void clear(Map<String,float[]> out){for(String key:KaitakuActionEvidence.KEYS){out.put(KaitakuPreset.P+key,new float[]{0});out.remove(KaitakuPreset.P+key+AdaptiveLayout.BOUNDS);}KaitakuActionEvidence.blockHome(out);out.put(KaitakuPreset.CONTINUE,new float[]{0});}
}
