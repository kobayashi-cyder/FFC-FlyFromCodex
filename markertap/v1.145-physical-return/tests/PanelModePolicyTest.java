package jp.tapmarker;

public final class PanelModePolicyTest {
 static int assertions;
 static void check(boolean value,String message){assertions++;if(!value)throw new AssertionError(message);}
 public static void main(String[] args){
  check(PanelModePolicy.normalize(PanelModePolicy.FLOAT)==PanelModePolicy.FLOAT,"float mode");
  check(PanelModePolicy.normalize(PanelModePolicy.APP)==PanelModePolicy.APP,"app mode");
  check(PanelModePolicy.normalize(-1)==PanelModePolicy.FLOAT,"corrupt preference fallback");
  check(PanelModePolicy.normalize(42)==PanelModePolicy.FLOAT,"unsupported preference fallback");
  check(PanelModePolicy.showOverlay(PanelModePolicy.FLOAT,false),"float visible when unlocked");
  check(!PanelModePolicy.showOverlay(PanelModePolicy.FLOAT,true),"screen lock hides float");
  check(!PanelModePolicy.showOverlay(PanelModePolicy.APP,false),"app mode hides overlay");
  check(!PanelModePolicy.showOverlay(PanelModePolicy.APP,true),"app mode stays hidden on lock");
  check(PanelModePolicy.modeLabel(PanelModePolicy.APP).contains("非表示"),"app mode label");
  check(PanelModePolicy.modeLabel(PanelModePolicy.FLOAT).contains("フロート"),"float mode label");
  int automationRun=81,gestureCount=14;
  int visible=PanelModePolicy.APP;
  check(!PanelModePolicy.showOverlay(visible,false),"overlay removed");
  visible=PanelModePolicy.FLOAT;
  check(PanelModePolicy.showOverlay(visible,false),"overlay restored");
  check(automationRun==81&&gestureCount==14,"presentation policy mutated automation counts");
  System.out.println("PanelModePolicyTest: "+assertions+" assertions passed");
 }
}
