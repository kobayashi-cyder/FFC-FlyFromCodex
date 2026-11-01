package jp.tapmarker;

/** Presentation-only state; operation/automation must be independent of this mode. */
final class PanelModePolicy {
 static final int FLOAT=0, DOCKED=1;
 static int normalize(int stored){return stored==DOCKED?DOCKED:FLOAT;}
 static boolean showOverlay(int stored,boolean guarded){return !guarded;}
 static boolean movable(int stored){return normalize(stored)==FLOAT;}
 static int clamp(int number,int minimum,int maximum){return Math.max(minimum,Math.min(number,Math.max(minimum,maximum)));}
 static int dockY(int displayHeight,int panelHeight,int bottomInset){
  return Math.max(0,displayHeight-Math.max(0,panelHeight)-Math.max(0,bottomInset));
 }
 static String modeLabel(int stored){
  return normalize(stored)==FLOAT?"フロート表示中（ドラッグで移動）":"画面下部に固定中（ドラッグ不可）";
 }
 private PanelModePolicy(){}
}
