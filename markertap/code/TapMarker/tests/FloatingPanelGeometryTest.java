package jp.tapmarker;
public final class FloatingPanelGeometryTest {
 static int passed;
 static void check(boolean p,String message){if(!p)throw new AssertionError(message);passed++;}
 public static void main(String[] args){
  check(FloatingPanelGeometry.clamp(-10,1080,70)==0,"left");
  check(FloatingPanelGeometry.clamp(1100,1080,70)==1010,"right");
  check(FloatingPanelGeometry.clamp(40,1080,70)==40,"inside");
  check(FloatingPanelGeometry.clamp(40,50,70)==0,"undersized screen");
  check(FloatingPanelGeometry.clamp(Integer.MAX_VALUE,Integer.MAX_VALUE,70)==Integer.MAX_VALUE-70,"overflow");
  check(FloatingPanelGeometry.isTap(0,0,2,2,8),"tap");
  check(!FloatingPanelGeometry.isTap(0,0,9,0,8),"drag");
  check(!FloatingPanelGeometry.isTap(Float.NaN,0,0,0,8),"invalid touch");
  for(int mini=0;mini<2;mini++)for(int guard=0;guard<2;guard++)for(int modal=0;modal<2;modal++){
   boolean m=mini==1,g=guard==1,v=modal==1;
   boolean panel=FloatingPanelGeometry.panelVisible(m,g,v);
   boolean bubble=FloatingPanelGeometry.bubbleVisible(m,g,v);
   check(!(panel&&bubble),"never two overlays");
   check(g||v?!panel&&!bubble:m?bubble&&!panel:panel&&!bubble,"visibility truth table");
  }
  System.out.println("FloatingPanelGeometryTest: "+passed+" checks passed");
 }
}
