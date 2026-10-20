package jp.tapmarker;
import android.graphics.Rect;
import android.view.accessibility.*;
import java.util.*;

/** Bounded incident-only evidence, including controls clipped outside the target display. */
final class VirtualWindowEvidence {
 static String describe(TapService service,VirtualWindows.Window target){
  if(target==null||target.id<=0)return "表示情報なし";
  StringBuilder out=new StringBuilder();
  try{
   List<AccessibilityWindowInfo> windows=service.getWindowsOnAllDisplays().get(target.id);
   if(windows==null)return "対象画面のウインドウなし";
   int count=0;
   for(AccessibilityWindowInfo window:windows){
    if(window.getDisplayId()!=target.id||count++>=6)continue;
    Rect bounds=new Rect();window.getBoundsInScreen(bounds);AccessibilityNodeInfo root=window.getRoot();
    String owner=root==null?"取得不可":String.valueOf(root.getPackageName());
    out.append("window=").append(window.getId()).append(" / owner=").append(owner).append(" / layer=").append(window.getLayer()).append(" / focused=").append(window.isFocused()).append(" / bounds=").append(bounds.toShortString()).append('\n');
    if(root==null)continue;
    if(!target.pkg.equals(owner)){root.recycle();continue;}
    ArrayDeque<AccessibilityNodeInfo> queue=new ArrayDeque<>();queue.add(root);int visited=0,controls=0;
    try{while(!queue.isEmpty()&&visited++<96&&controls<12&&out.length()<5000){
     AccessibilityNodeInfo node=queue.remove();
     try{
      String pkg=node.getPackageName()==null?"":node.getPackageName().toString();if(!pkg.isEmpty()&&!target.pkg.equals(pkg))continue;
      String label=BancUiControls.label(node);
      if(node.isClickable()||AdExitPolicy.exitLabel(label)){
       Rect control=new Rect();node.getBoundsInScreen(control);Rect visible=new Rect(control);boolean intersects=visible.intersect(new Rect(0,0,target.width,target.height));
       out.append("  control=").append(AutomationIncident.limit(label,90)).append(" / visible=").append(node.isVisibleToUser()).append(" / enabled=").append(node.isEnabled()).append(" / clickable=").append(node.isClickable()).append(" / bounds=").append(control.toShortString()).append(" / inside=").append(intersects?visible.toShortString():"画面外").append('\n');controls++;
      }
      for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo child=node.getChild(i);if(child!=null)queue.add(child);}
     }finally{node.recycle();}
    }}finally{while(!queue.isEmpty())queue.remove().recycle();}
    if(!queue.isEmpty()||visited>=96||controls>=12)out.append("  取得上限あり\n");
   }
  }catch(Exception unavailable){out.append("取得不可：").append(VirtualWindows.brief(unavailable));}
  return AutomationIncident.limit(out.toString(),5500);
 }
 private VirtualWindowEvidence(){}
}
