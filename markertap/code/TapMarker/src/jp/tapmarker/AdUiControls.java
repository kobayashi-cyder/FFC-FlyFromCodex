package jp.tapmarker;
import android.graphics.Rect;import android.view.accessibility.*;import java.util.*;

/** Collect all explicitly named, enabled exits; the runner chooses and rechecks one. */
final class AdUiControls {
 static void augment(TapService s,PhaseEngine e,String pkg,int display,Rect imageRect,int width,int height,Map<String,float[]> samples){
  if(e.windowRelative||AdExitPolicy.gameVisible(e,samples))return;
  Set<String> active=new HashSet<>();for(PhaseEngine.Region r:e.activeRegions())if(AdCloseAutomaton.handles(r.id))active.add(r.id);if(active.isEmpty())return;
  Rect scope=imageRect==null?new Rect(0,0,width,height):imageRect;
  AccessibilityNodeInfo root=BancUiControls.root(s,pkg,display,scope);if(root==null)return;
  ArrayDeque<AccessibilityNodeInfo> queue=new ArrayDeque<>();queue.add(root);Map<String,List<Rect>> found=new LinkedHashMap<>();boolean complete=true;int read=0;
  try{while(!queue.isEmpty()&&read++<256){AccessibilityNodeInfo node=queue.remove();try{
   if(!node.isVisibleToUser()||!pkg.contentEquals(node.getPackageName()==null?"":node.getPackageName()))continue;
   String label=BancUiControls.label(node);if(AdExitPolicy.exitLabel(label)){
    AccessibilityNodeInfo target=BancUiControls.clickable(node,pkg,scope);if(target!=null)try{Rect b=new Rect();target.getBoundsInScreen(b);String id=AdExitPolicy.corner(label,BancUiControls.relative(b,scope),AdCloseAutomaton.cornerTop(samples,width,height));if(active.contains(id))found.computeIfAbsent(id,k->new ArrayList<>()).add(b);}finally{target.recycle();}
   }
   for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo child=node.getChild(i);if(child!=null)queue.add(child);}
  }finally{node.recycle();}}
  if(!queue.isEmpty())complete=false;
  }catch(Exception unavailable){complete=false;}finally{while(!queue.isEmpty())queue.remove().recycle();}
  if(!complete)return;
  for(Map.Entry<String,List<Rect>> row:found.entrySet())for(Rect b:row.getValue())AdCloseAutomaton.addNamed(samples,row.getKey(),(b.exactCenterX()-scope.left)*width/scope.width(),(b.exactCenterY()-scope.top)*height/scope.height(),width,height);
 }
}
