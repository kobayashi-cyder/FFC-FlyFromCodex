package jp.tapmarker;

/** Presentation-only state; never starts or stops actual automation. */
final class PanelModePolicy {
 static final int FLOAT=0, DOCKED=1, MINIMIZED=2;
 static int normalize(int stored){
  return stored==DOCKED||stored==MINIMIZED?stored:FLOAT;
 }
 static boolean showOverlay(int stored,boolean guarded){
  // In minimized mode a single restore control remains visible.
  return !guarded;
 }
 static boolean movable(int stored){return normalize(stored)==FLOAT;}
 static boolean minimized(int stored){return normalize(stored)==MINIMIZED;}
 static int expanded(int stored){
  return normalize(stored)==DOCKED?DOCKED:FLOAT;
 }
 static int restoredMode(int stored){
  return expanded(stored);
 }
 static int clamp(int number,int minimum,int maximum){
  return Math.max(minimum,Math.min(number,Math.max(minimum,maximum)));
 }
 static int dockY(int displayHeight,int panelHeight,int bottomInset){
  return Math.max(0,displayHeight-Math.max(0,panelHeight)-Math.max(0,bottomInset));
 }
 static String modeLabel(int stored){
  switch(normalize(stored)){
   case DOCKED:return "画面下部に固定中（ドラッグ不可）";
   case MINIMIZED:return "最小化中（戻すで復元）";
   default:return "フロート表示中（ドラッグで移動）";
  }
 }
 private PanelModePolicy(){}
}
