package jp.tapmarker;
import java.util.*;

/** Per runner: rotate positively observed exits, bound retries and retain the selected preflight target. */
final class AdExitAttempts {
 static final long COOLDOWN_MS=800;static final int MAX_PER_CONTROL=2,MAX_TOTAL=8;
 static final class Attempt {final String id;final float x,y;int count,missingFrames;long at=-1,missingSince=-1,lastMissing=-1;Attempt(String id,float x,float y){this.id=id;this.x=x;this.y=y;}}
 final List<Attempt> attempts=new ArrayList<>();Attempt selected;long lastFrame=-1,nextAt;int total;boolean visible;
 void reset(){attempts.clear();selected=null;lastFrame=-1;nextAt=0;total=0;visible=false;}
 Attempt match(String id,float[] p,int w,int h){float x=p[0]/w,y=p[1]/h;for(Attempt a:attempts)if(a.id.equals(id)&&Math.abs(a.x-x)<=.025f&&Math.abs(a.y-y)<=.025f)return a;Attempt a=new Attempt(id,x,y);attempts.add(a);return a;}
 void select(Map<String,float[]> samples,int width,int height,long capturedAt,long now){
  Map<String,List<float[]>> candidates=new LinkedHashMap<>();for(String id:new String[]{AdCloseAutomaton.LEFT,AdCloseAutomaton.RIGHT})candidates.put(id,AdCloseAutomaton.candidates(id,samples,width,height));
  visible=false;for(List<float[]> list:candidates.values())visible|=!list.isEmpty();
  for(String id:candidates.keySet()){samples.put(id,new float[]{0});samples.remove(id+AdCloseAutomaton.CONTROL_POINT);}
  selected=null;if(capturedAt<=lastFrame||capturedAt>now||now-capturedAt>=1500)return;lastFrame=capturedAt;
  // A later ad card may place its Close at the same coordinates. Two fresh
  // observations of its disappearance separate appearances; the episode's
  // total input cap remains in force, including across repeated flicker.
  for(Attempt a:attempts){boolean present=false;for(float[] p:candidates.get(a.id))if(Math.abs(a.x-p[0]/width)<=.025f&&Math.abs(a.y-p[1]/height)<=.025f){present=true;break;}
   if(present){if(a.missingFrames>=2&&a.lastMissing-a.missingSince>=300)a.count=0;a.missingFrames=0;a.missingSince=a.lastMissing=-1;}
   else if(a.count>0&&capturedAt>a.at){if(a.missingSince<0)a.missingSince=capturedAt;a.lastMissing=capturedAt;a.missingFrames++;}
  }
  if(now<nextAt||total>=MAX_TOTAL)return;
  Attempt best=null;float[] point=null;
  for(Map.Entry<String,List<float[]>> row:candidates.entrySet())for(float[] p:row.getValue()){Attempt a=match(row.getKey(),p,width,height);if(a.count>=MAX_PER_CONTROL)continue;if(best==null||a.count<best.count||a.count==best.count&&a.at<best.at){best=a;point=p;}}
  if(best==null)return;selected=best;AdCloseAutomaton.putControl(samples,best.id,point[0],point[1],width,height);
 }
 boolean preflight(String id,Map<String,float[]> samples,int width,int height){
  if(selected==null||!selected.id.equals(id))return false;
  for(float[] p:AdCloseAutomaton.candidates(id,samples,width,height))if(Math.abs(selected.x-p[0]/width)<=.025f&&Math.abs(selected.y-p[1]/height)<=.025f){AdCloseAutomaton.putControl(samples,id,p[0],p[1],width,height);return true;}
  samples.put(id,new float[]{0});samples.remove(id+AdCloseAutomaton.CONTROL_POINT);return false;
 }
 void sent(String id,long now){if(selected==null||!selected.id.equals(id))return;selected.count++;selected.at=now;total++;nextAt=now+COOLDOWN_MS;selected=null;}
 boolean cooling(long now){return visible&&now<nextAt;}
}
