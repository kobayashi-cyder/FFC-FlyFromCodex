package jp.tapmarker;

/** Pure geometry and visibility rules for the floating minimized control. */
final class FloatingPanelGeometry {
 private FloatingPanelGeometry(){}
 static int clamp(int coordinate,int screenSize,int controlSize){
  long maximum=Math.max(0L,(long)Math.max(0,screenSize)-Math.max(0,controlSize));
  return (int)Math.max(0L,Math.min((long)coordinate,maximum));
 }
 static boolean isTap(float startX,float startY,float endX,float endY,float slop){
  if(!Float.isFinite(startX)||!Float.isFinite(startY)||!Float.isFinite(endX)
      ||!Float.isFinite(endY)||!Float.isFinite(slop)||slop<0)return false;
  return Math.abs(startX-endX)<=slop&&Math.abs(startY-endY)<=slop;
 }
 static boolean panelVisible(boolean minimized,boolean screenGuarded,boolean importActive){
  return !minimized&&!screenGuarded&&!importActive;
 }
 static boolean bubbleVisible(boolean minimized,boolean screenGuarded,boolean importActive){
  return minimized&&!screenGuarded&&!importActive;
 }
}
