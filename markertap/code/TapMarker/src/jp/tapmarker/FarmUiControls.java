package jp.tapmarker;
import android.graphics.Rect;import android.os.Build;import android.view.Display;import android.view.accessibility.*;import java.util.*;
/** Read named controls only from the farm app on the requested display. */
final class FarmUiControls {
 static void augment(TapService s,PhaseEngine e,int display,Rect imageRect,int width,int height,Map<String,float[]> samples){
  if(e.windowRelative||!e.regions.containsKey(FarmPreset.P+"chest-open")||Build.VERSION.SDK_INT<30)return;
  Rect scope=imageRect==null?new Rect(0,0,width,height):imageRect;Map<String,Rect> buttons=new HashMap<>();Set<String> ambiguous=new HashSet<>();StringBuilder context=new StringBuilder();List<Rect> adErrorOk=new ArrayList<>();
  try{List<AccessibilityWindowInfo> windows=s.getWindowsOnAllDisplays().get(display);if(windows==null)return;for(AccessibilityWindowInfo window:windows){if(window.getType()!=AccessibilityWindowInfo.TYPE_APPLICATION||window.getDisplayId()!=display)continue;AccessibilityNodeInfo root=window.getRoot();if(root==null)continue;if(!FarmPreset.APP.contentEquals(root.getPackageName()==null?"":root.getPackageName())){root.recycle();continue;}
   ArrayDeque<AccessibilityNodeInfo> queue=new ArrayDeque<>();queue.add(root);int read=0;try{while(!queue.isEmpty()&&read++<256){AccessibilityNodeInfo n=queue.remove();try{if(!n.isVisibleToUser()||n.getPackageName()!=null&&!FarmPreset.APP.contentEquals(n.getPackageName()))continue;String text=FarmControlEvidence.label(n.getText()==null?"":n.getText().toString()),description=FarmControlEvidence.label(n.getContentDescription()==null?"":n.getContentDescription().toString());context.append(text).append(' ').append(description).append(' ');
   if((text.equalsIgnoreCase("OK")||description.equalsIgnoreCase("OK"))&&n.isEnabled()){
    Rect okBounds=new Rect();n.getBoundsInScreen(okBounds);
    if(!okBounds.isEmpty()&&scope.contains(okBounds)&&okBounds.width()<=scope.width()*.85f&&okBounds.height()<=scope.height()*.18f)adErrorOk.add(okBounds);
   }
   String id=FarmControlEvidence.id(text);if(id.isEmpty())id=FarmControlEvidence.id(description);if(!id.isEmpty()&&n.isEnabled()){Rect b=new Rect();n.getBoundsInScreen(b);if(!b.isEmpty()&&scope.contains(b)&&b.width()<=scope.width()*.8f&&b.height()<=scope.height()*.2f){Rect previous=buttons.get(id);if(previous!=null&&!previous.equals(b)&&Math.abs(previous.centerX()-b.centerX())+Math.abs(previous.centerY()-b.centerY())>8)ambiguous.add(id);else buttons.put(id,b);}}for(int i=0;i<n.getChildCount();i++){AccessibilityNodeInfo child=n.getChild(i);if(child!=null)queue.add(child);}}finally{n.recycle();}}}finally{while(!queue.isEmpty())queue.remove().recycle();}break;
  }}catch(Exception unavailable){return;}
  String heading=context.toString();if(heading.contains("広告を表示できませんでした")&&adErrorOk.size()==1){
   Rect b=adErrorOk.get(0);FarmAdErrorEvidence.named(samples,(b.exactCenterX()-scope.left)*width/scope.width(),(b.exactCenterY()-scope.top)*height/scope.height(),width,height);
  }
  for(String id:buttons.keySet()){if(ambiguous.contains(id))continue;if(id.endsWith("close")&&!heading.contains("コインを獲得")&&!heading.contains("コインゲット"))continue;Rect b=buttons.get(id);FarmControlEvidence.put(samples,id,(b.exactCenterX()-scope.left)*width/scope.width(),(b.exactCenterY()-scope.top)*height/scope.height(),width,height);}
 }
}
