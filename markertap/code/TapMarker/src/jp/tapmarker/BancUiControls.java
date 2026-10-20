package jp.tapmarker;

import android.graphics.Rect;
import android.os.Build;
import android.view.accessibility.*;
import java.util.*;

/** Visible, enabled, named click actions from exactly one app on one display. */
final class BancUiControls {
 static AccessibilityNodeInfo root(TapService s,String pkg,int display,Rect scope){
  if(Build.VERSION.SDK_INT<30||scope==null||scope.isEmpty())return null;
  AccessibilityNodeInfo selected=null;
  try{List<AccessibilityWindowInfo> windows=s.getWindowsOnAllDisplays().get(display);if(windows==null)return null;
   for(AccessibilityWindowInfo window:windows){if(window.getType()!=AccessibilityWindowInfo.TYPE_APPLICATION||window.getDisplayId()!=display)continue;Rect bounds=new Rect();window.getBoundsInScreen(bounds);if(!Rect.intersects(bounds,scope))continue;AccessibilityNodeInfo root=window.getRoot();if(root==null)continue;
    if(!pkg.contentEquals(root.getPackageName()==null?"":root.getPackageName())){root.recycle();continue;}
    if(selected!=null){root.recycle();selected.recycle();return null;}selected=root;
   }
  }catch(Exception unavailable){if(selected!=null)selected.recycle();return null;}return selected;
 }
 static AccessibilityNodeInfo clickable(AccessibilityNodeInfo node,String pkg,Rect scope){
  AccessibilityNodeInfo target=AccessibilityNodeInfo.obtain(node);
  for(int depth=0;depth<4&&target!=null;depth++){
   Rect b=new Rect();target.getBoundsInScreen(b);
   if(!target.isVisibleToUser()||!target.isEnabled()||!pkg.contentEquals(target.getPackageName()==null?"":target.getPackageName())||b.isEmpty()||!scope.contains(b)){target.recycle();return null;}
   if(target.isClickable()&&b.width()<=scope.width()*.8f&&b.height()<=scope.height()*.25f)return target;
   AccessibilityNodeInfo parent=target.getParent();target.recycle();target=parent;
  }
  if(target!=null)target.recycle();return null;
 }
 static String label(AccessibilityNodeInfo n){String text=n.getText()==null?"":n.getText().toString();return text.isEmpty()&&n.getContentDescription()!=null?n.getContentDescription().toString():text;}
 static float[] relative(Rect b,Rect scope){return new float[]{(b.left-scope.left)/(float)scope.width(),(b.top-scope.top)/(float)scope.height(),(b.right-scope.left)/(float)scope.width(),(b.bottom-scope.top)/(float)scope.height()};}
 static BancAutonomy.Ui read(TapService s,String pkg,int display,Rect scope){
  AccessibilityNodeInfo root=root(s,pkg,display,scope);if(root==null)return BancAutonomy.Ui.empty();
  ArrayDeque<AccessibilityNodeInfo> queue=new ArrayDeque<>();queue.add(root);ArrayList<String> context=new ArrayList<>(),labels=new ArrayList<>();ArrayList<float[]> bounds=new ArrayList<>();int read=0;boolean complete=true;
  try{while(!queue.isEmpty()&&read++<256){AccessibilityNodeInfo n=queue.remove();try{
    if(!n.isVisibleToUser()||!pkg.contentEquals(n.getPackageName()==null?"":n.getPackageName()))continue;
    String text=label(n);Rect visible=new Rect();n.getBoundsInScreen(visible);
    if(!text.isEmpty()&&text.length()<=128&&Rect.intersects(scope,visible)){context.add(text);AccessibilityNodeInfo target=clickable(n,pkg,scope);if(target!=null){try{Rect b=new Rect();target.getBoundsInScreen(b);labels.add(text);bounds.add(relative(b,scope));}finally{target.recycle();}}}
    for(int i=0;i<n.getChildCount();i++){AccessibilityNodeInfo child=n.getChild(i);if(child!=null)queue.add(child);}
   }finally{n.recycle();}}
   if(!queue.isEmpty())complete=false;
  }catch(Exception unavailable){complete=false;}finally{while(!queue.isEmpty())queue.remove().recycle();}
  if(!complete)return BancAutonomy.Ui.empty();Collections.sort(context);String joined=String.join(" ",context);ArrayList<BancAutonomy.Control> controls=new ArrayList<>();Set<String> seen=new HashSet<>();
  for(int i=0;i<labels.size();i++){String kind=BancAutonomy.safeKind(labels.get(i),joined);if(kind.isEmpty())continue;BancAutonomy.Control c=new BancAutonomy.Control(labels.get(i),kind,bounds.get(i));String key=c.id+Arrays.toString(c.bounds);if(seen.add(key))controls.add(c);}
  return new BancAutonomy.Ui(joined,controls);
 }
 /** Rebuild the target immediately before ACTION_CLICK. Acceptance is not task success. */
 static boolean activate(TapService s,String pkg,int display,Rect scope,BancAutonomy.Ui before,BancAutonomy.Control desired){
  BancAutonomy.Ui fresh=read(s,pkg,display,scope);if(!fresh.context.equals(before.context)||!BancAutonomy.safeKind(desired.label,fresh.context).equals(desired.kind))return false;
  int matches=0;for(BancAutonomy.Control c:fresh.controls)if(c.label.equals(desired.label)&&c.kind.equals(desired.kind)&&Arrays.equals(c.bounds,desired.bounds))matches++;
  if(matches!=1)return false;AccessibilityNodeInfo root=root(s,pkg,display,scope);if(root==null)return false;
  ArrayDeque<AccessibilityNodeInfo> queue=new ArrayDeque<>();queue.add(root);int read=0;
  try{while(!queue.isEmpty()&&read++<256){AccessibilityNodeInfo n=queue.remove();try{
   if(!n.isVisibleToUser()||!pkg.contentEquals(n.getPackageName()==null?"":n.getPackageName()))continue;
   if(BancAutonomy.normalize(label(n)).equals(desired.label)&&n.refresh()&&BancAutonomy.normalize(label(n)).equals(desired.label)){AccessibilityNodeInfo target=clickable(n,pkg,scope);if(target!=null){try{if(!target.refresh()||!target.isEnabled()||!target.isVisibleToUser())continue;Rect b=new Rect();target.getBoundsInScreen(b);if(Arrays.equals(relative(b,scope),desired.bounds))return target.performAction(AccessibilityNodeInfo.ACTION_CLICK);}finally{target.recycle();}}}
   for(int i=0;i<n.getChildCount();i++){AccessibilityNodeInfo child=n.getChild(i);if(child!=null)queue.add(child);}
  }finally{n.recycle();}}}catch(Exception unavailable){return false;}finally{while(!queue.isEmpty())queue.remove().recycle();}return false;
 }
}
