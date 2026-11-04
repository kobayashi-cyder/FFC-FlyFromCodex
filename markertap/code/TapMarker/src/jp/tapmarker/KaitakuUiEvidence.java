package jp.tapmarker;
import android.os.SystemClock;
import android.graphics.Rect;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.*;

/** Remote tree reads are bounded background probes. All inputs still use the screenshot preflight. */
final class KaitakuUiEvidence {
 static final class Page {final String text;final List<KaitakuNamedControls.Control> controls;final long started;Page(String s,List<KaitakuNamedControls.Control> c,long at){text=s;controls=c;started=at;}}
 static final BoundedProbeCache<Page> probes=new BoundedProbeCache<>(SystemClock::uptimeMillis,250,250);
 static int cachedToken=-1;static long cachedGeometry=-1;static TapService cachedService;
 static void scan(AccessibilityNodeInfo node,StringBuilder text,List<KaitakuNamedControls.Control> controls,int[] budget,int w,int h,ContentViewport v){
  if(node==null||budget[0]--<=0||!node.isVisibleToUser())return;
  CharSequence pkg=node.getPackageName();if(pkg!=null&&!KaitakuPreset.APP.contentEquals(pkg))return;
  CharSequence label=node.getText();if(label==null||label.length()==0)label=node.getContentDescription();
  if(label!=null){text.append(label).append(' ');Rect r=new Rect();node.getBoundsInScreen(r);
   if(node.isEnabled()&&!r.isEmpty()&&r.left>=v.left&&r.top>=v.top&&r.right<=v.left+v.width&&r.bottom<=v.top+v.height)controls.add(new KaitakuNamedControls.Control(label.toString(),new float[]{(r.left-v.left)/(float)v.width,(r.top-v.top)/(float)v.height,(r.right-v.left)/(float)v.width,(r.bottom-v.top)/(float)v.height}));
  }
  for(int i=0;i<node.getChildCount()&&budget[0]>0;i++){AccessibilityNodeInfo child=node.getChild(i);if(child!=null)try{scan(child,text,controls,budget,w,h,v);}finally{child.recycle();}}
 }
 static synchronized void sample(PhaseEngine e,Map<String,float[]> out,int w,int h,ContentViewport v,long now){
  TapService s=TapService.instance;if(s==null||s.pilot==null||!s.pilot.active||s.pilot.phases.engine!=e||!KaitakuPreset.APP.equals(s.pilot.target)||s.pilot.splitPilot.active)return;
  int token=s.pilot.token;long geometry=s.inputTargetVersion;
  if(cachedService!=s||cachedToken!=token||cachedGeometry!=geometry){probes.clear();cachedService=s;cachedToken=token;cachedGeometry=geometry;}
  String key=e.current+":"+geometry+":"+v.left+":"+v.top+":"+v.width+":"+v.height;
  BoundedProbeCache.Result<Page> result=probes.get(key,token,0,w,h,()->{
   long started=SystemClock.uptimeMillis();AccessibilityNodeInfo root=s.getRootInActiveWindow();if(root==null)return null;
   try{if(!KaitakuPreset.APP.contentEquals(root.getPackageName()==null?"":root.getPackageName()))return null;
    StringBuilder text=new StringBuilder();List<KaitakuNamedControls.Control> controls=new ArrayList<>();scan(root,text,controls,new int[]{180},w,h,v);
    if(!s.pilot.active||s.pilot.token!=token||s.inputTargetVersion!=geometry)return null;return new Page(text.toString(),controls,started);
   }finally{root.recycle();}
  });
  if(result==null||now<result.value.started||now-result.value.started>350)return;
  KaitakuNamedControls.apply(e,result.value.text,result.value.controls,out,v.width,v.height);
 }
 private KaitakuUiEvidence(){}
}
