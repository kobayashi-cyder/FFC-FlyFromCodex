package jp.tapmarker;

public final class PanelModePolicyTest {
 static int assertions;
 static void check(boolean value,String message){assertions++;if(!value)throw new AssertionError(message);}
 public static void main(String[] args){
  check(PanelModePolicy.normalize(PanelModePolicy.FLOAT)==PanelModePolicy.FLOAT,"float mode");
  check(PanelModePolicy.normalize(PanelModePolicy.DOCKED)==PanelModePolicy.DOCKED,"docked mode");
  check(PanelModePolicy.normalize(PanelModePolicy.MINIMIZED)==PanelModePolicy.MINIMIZED,"minimized mode");
  check(PanelModePolicy.normalize(-1)==PanelModePolicy.FLOAT,"corrupt preference fallback");
  check(PanelModePolicy.normalize(42)==PanelModePolicy.FLOAT,"unsupported preference fallback");
  check(PanelModePolicy.showOverlay(PanelModePolicy.FLOAT,false),"floating visible when unlocked");
  check(PanelModePolicy.showOverlay(PanelModePolicy.DOCKED,false),"fixed panel is VISIBLE, not hidden");
  check(PanelModePolicy.showOverlay(PanelModePolicy.MINIMIZED,false),"restore chip is visible");
  check(!PanelModePolicy.showOverlay(PanelModePolicy.FLOAT,true),"screen lock hides floating overlay");
  check(!PanelModePolicy.showOverlay(PanelModePolicy.DOCKED,true),"screen lock hides docked overlay");
  check(!PanelModePolicy.showOverlay(PanelModePolicy.MINIMIZED,true),"screen lock hides restore control");
  check(PanelModePolicy.movable(PanelModePolicy.FLOAT),"float is draggable");
  check(!PanelModePolicy.movable(PanelModePolicy.DOCKED),"fixed panel is not draggable");
  check(!PanelModePolicy.movable(PanelModePolicy.MINIMIZED),"restore chip cannot be dragged");
  check(PanelModePolicy.modeLabel(PanelModePolicy.DOCKED).contains("固定"),"fixed label");
  check(PanelModePolicy.modeLabel(PanelModePolicy.MINIMIZED).contains("最小化"),"minimized label");
  check(PanelModePolicy.minimized(PanelModePolicy.MINIMIZED),"minimize recognized");
  check(PanelModePolicy.restoredMode(PanelModePolicy.DOCKED)==PanelModePolicy.DOCKED,"restore docked layout");
  check(PanelModePolicy.restoredMode(PanelModePolicy.FLOAT)==PanelModePolicy.FLOAT,"restore floating layout");
  check(PanelModePolicy.restoredMode(PanelModePolicy.MINIMIZED)==PanelModePolicy.FLOAT,"corrupt nested minimize fallback");
  check(PanelModePolicy.modeLabel(PanelModePolicy.FLOAT).contains("フロート"),"float label");
  check(PanelModePolicy.dockY(2400,240,40)==2120,"bottom docking position");
  check(PanelModePolicy.dockY(100,300,10)==0,"oversized panel clamp");
  check(PanelModePolicy.dockY(100,30,20)==50,"safe bottom inset");
  check(PanelModePolicy.clamp(250,0,160)==160,"movement max clamp");
  check(PanelModePolicy.clamp(-10,0,160)==0,"movement min clamp");
  int automationRun=81,gestureCount=14;
  int shown=PanelModePolicy.DOCKED;
  check(PanelModePolicy.showOverlay(shown,false),"dock remains visible");
  shown=PanelModePolicy.MINIMIZED;
  check(PanelModePolicy.showOverlay(shown,false),"minimized button stays visible");
  shown=PanelModePolicy.FLOAT;
  check(PanelModePolicy.showOverlay(shown,false),"return to float remains visible");
  check(automationRun==81&&gestureCount==14,"presentation policy mutated automation counters");
  System.out.println("PanelModePolicyTest: "+assertions+" assertions passed");
 }
}
