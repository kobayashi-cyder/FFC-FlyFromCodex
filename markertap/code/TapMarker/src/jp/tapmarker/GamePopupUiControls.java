package jp.tapmarker;

import android.graphics.Rect;
import android.os.Build;
import android.view.accessibility.*;
import java.util.*;

/** Examine every game-owned window on this display, including separate dialog roots. */
final class GamePopupUiControls {
 static Map<String,float[]> read(TapService service,String pkg,int display,Rect imageRect,int width,int height){
  Map<String,float[]> result=new HashMap<>();if(Build.VERSION.SDK_INT<30||!GamePopupEvidence.supported(pkg))return result;
  Rect scope=imageRect==null?new Rect(0,0,width,height):imageRect;
  try{
   List<AccessibilityWindowInfo> windows=service.getWindowsOnAllDisplays().get(display);if(windows==null)return result;
   String chosen="";Rect chosenBounds=null;
   for(AccessibilityWindowInfo window:windows){
    if(window.getType()!=AccessibilityWindowInfo.TYPE_APPLICATION||window.getDisplayId()!=display)continue;
    AccessibilityNodeInfo root=window.getRoot();if(root==null)continue;
    if(!pkg.contentEquals(root.getPackageName()==null?"":root.getPackageName())){root.recycle();continue;}
    ArrayDeque<AccessibilityNodeInfo> queue=new ArrayDeque<>();queue.add(root);
    List<String> labels=new ArrayList<>();List<Rect> bounds=new ArrayList<>();StringBuilder context=new StringBuilder();boolean dialog=false,complete=true;int count=0;
    try{while(!queue.isEmpty()&&count++<384){AccessibilityNodeInfo node=queue.remove();try{
     if(!node.isVisibleToUser()||node.getPackageName()!=null&&!pkg.contentEquals(node.getPackageName()))continue;
     String label=BancUiControls.label(node);context.append(label).append(' ');dialog|=String.valueOf(node.getClassName()).contains("Dialog");
     if(node.isEnabled()&&!label.isEmpty()){
      AccessibilityNodeInfo clickable=target(node,pkg,scope,AdExitPolicy.exitLabel(label));
      if(clickable!=null)try{Rect b=new Rect();clickable.getBoundsInScreen(b);if(b.intersect(scope)&&!b.isEmpty()){labels.add(label);bounds.add(b);}}finally{clickable.recycle();}
     }
     for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo child=node.getChild(i);if(child!=null)queue.add(child);}
    }finally{node.recycle();}}
    if(!queue.isEmpty())complete=false;
    }finally{while(!queue.isEmpty())queue.remove().recycle();}
    if(!complete)continue;
    String heading=context.toString();String candidate="";Rect target=null;
    for(int i=0;i<labels.size();i++){
     String id=GamePopupEvidence.named(pkg,labels.get(i),heading,dialog);if(id.isEmpty())continue;
     if(id.endsWith("offline")){if(candidate.endsWith("offline")&&target!=null&&!target.equals(bounds.get(i)))return result;candidate=id;target=bounds.get(i);}
     else if(!candidate.endsWith("offline")){if(target!=null&&(!candidate.equals(id)||!target.equals(bounds.get(i))))return result;candidate=id;target=bounds.get(i);}
    }
    if(target!=null){if(chosenBounds!=null&&(!chosen.equals(candidate)||!chosenBounds.equals(target)))return result;chosen=candidate;chosenBounds=target;}
   }
   if(chosenBounds!=null){GamePopupEvidence.put(result,chosen,chosenBounds.exactCenterX(),chosenBounds.exactCenterY(),width,height);GamePopupEvidence.namedFooter(result,chosen,chosenBounds.top,chosenBounds.bottom,scope.top,scope.bottom);GamePopupEvidence.evidence(result,chosen,1,Float.NaN,new float[]{chosenBounds.left,chosenBounds.top,chosenBounds.right,chosenBounds.bottom});if(chosen.equals(PoipoiPreset.P+"get"))result.put(PoipoiPreset.P+"missions",new float[]{1});if(chosen.equals(UsagiPreset.PREFIX+"claim"))result.put(UsagiPreset.PREFIX+"offline",new float[]{1});if(chosen.equals(UsagiPreset.PREFIX+"cancel"))result.put(UsagiPreset.PREFIX+"dialog",new float[]{1});}
  }catch(Exception unavailable){result.clear();}
  return result;
 }
 /** A named Close may extend below the display; tap only its visible intersection. */
 static AccessibilityNodeInfo target(AccessibilityNodeInfo node,String pkg,Rect scope,boolean clippedClose){
  AccessibilityNodeInfo n=AccessibilityNodeInfo.obtain(node);
  for(int depth=0;depth<4&&n!=null;depth++){
   Rect b=new Rect();n.getBoundsInScreen(b);
   if(!n.isVisibleToUser()||!n.isEnabled()||!pkg.contentEquals(n.getPackageName()==null?"":n.getPackageName())||b.isEmpty()||!Rect.intersects(b,scope)){n.recycle();return null;}
   if(n.isClickable()){
    if(PopupControlBounds.visible(new int[]{b.left,b.top,b.right,b.bottom},new int[]{scope.left,scope.top,scope.right,scope.bottom},clippedClose)!=null)return n;
    n.recycle();return null;
   }
   AccessibilityNodeInfo parent=n.getParent();n.recycle();n=parent;
  }
  if(n!=null)n.recycle();return null;
 }
}
