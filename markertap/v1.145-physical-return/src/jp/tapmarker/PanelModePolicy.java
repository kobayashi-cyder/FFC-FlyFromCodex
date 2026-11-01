package jp.tapmarker;

/** Policy-only mode switch; it must never mutate automation state. */
final class PanelModePolicy {
 static final int FLOAT=0, APP=1;
 static int normalize(int stored){return stored==APP?APP:FLOAT;}
 static boolean showOverlay(int stored,boolean guarded){return normalize(stored)==FLOAT&&!guarded;}
 static String modeLabel(int stored){return normalize(stored)==FLOAT?"フロート表示中":"通常画面のみ（フロート非表示）";}
 private PanelModePolicy(){}
}
