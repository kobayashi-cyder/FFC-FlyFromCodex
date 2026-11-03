package jp.tapmarker;
import android.view.View;
public final class VirtualPreviewVisibilityBridgeTest {
 public static void main(String[] args){
  View a=new View();VirtualPreviewVisibilityBridge.setVisibility(a,View.GONE);
  if(a.getVisibility()!=View.INVISIBLE)throw new AssertionError("GONE must preserve measured layout");
  VirtualPreviewVisibilityBridge.setVisibility(a,View.VISIBLE);
  if(a.getVisibility()!=View.VISIBLE)throw new AssertionError("restore must make visible");
  VirtualPreviewVisibilityBridge.setVisibility(null,View.GONE);
  System.out.println("VirtualPreviewVisibilityBridgeTest: 3 checks passed");
 }
}
