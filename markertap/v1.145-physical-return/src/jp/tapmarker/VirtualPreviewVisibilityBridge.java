package jp.tapmarker;
import android.view.View;

/**
 * Leave the virtual-display preview view in the layout when collapsed.
 * Only the phone-side preview is hidden; never release Surface, ImageReader,
 * display ID, or a virtual automaton as a side effect of this bridge.
 * INVISIBLE (not GONE) retains the view's measured layout until restoration.
 * This does not purport to fix a game's network connectivity.
 */
final class VirtualPreviewVisibilityBridge {
 static void setVisibility(View view,int requested){
  if(view==null)return;
  view.setVisibility(requested==View.GONE?View.INVISIBLE:requested);
 }
 private VirtualPreviewVisibilityBridge(){}
}
