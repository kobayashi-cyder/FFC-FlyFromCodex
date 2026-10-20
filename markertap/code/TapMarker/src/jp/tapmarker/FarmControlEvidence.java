package jp.tapmarker;
import java.util.*;
/** Explicit dialog labels; their points belong to one captured frame. */
final class FarmControlEvidence {
 static final String POINT=":farm-control-point";
 static String label(String text){return text==null?"":text.replaceAll("[\\s\\u3000]","");}
 static String id(String text){String t=label(text);return t.equals("閉じる")?FarmPreset.P+"close":t.equals("このままはじめる")||t.equals("このまま始める")?FarmPreset.P+"start":t.equals("宝箱を開ける")?FarmPreset.P+"chest-open":"";}
 static boolean handles(String id){return id.equals(FarmPreset.P+"close")||id.equals(FarmPreset.P+"start")||id.equals(FarmPreset.P+"chest-open");}
 static void install(PhaseEngine e){for(PhaseEngine.Phase p:e.phases.values())for(PhaseEngine.Watch w:p.watches)if(handles(w.regionId))w.runtimeControlEvidence=true;}
 static boolean namedOnly(PhaseEngine.Watch w){return w.regionId.equals(FarmPreset.P+"chest-open")&&w.reference!=null&&w.reference.length==1&&w.reference[0]==1;}
 static boolean namedOnly(PhaseEngine e,String id){PhaseEngine.Phase state=e.phases.get(e.current);if(state!=null)for(PhaseEngine.Watch w:state.watches)if(w.regionId.equals(id)&&namedOnly(w))return true;return false;}
 static boolean put(Map<String,float[]> samples,String id,float x,float y,int width,int height){if(!handles(id)||!Float.isFinite(x)||!Float.isFinite(y)||x<0||y<0||x>=width||y>=height)return false;samples.put(id,new float[]{1});samples.put(id+POINT,new float[]{x,y,width,height});return true;}
 static float[] point(String id,Map<String,float[]> samples,int width,int height){float[] p=samples.get(id+POINT),e=samples.get(id);if(p==null||p.length!=4||p[2]!=width||p[3]!=height||e==null||e.length!=1||e[0]!=1||!Float.isFinite(p[0])||!Float.isFinite(p[1])||p[0]<0||p[1]<0||p[0]>=width||p[1]>=height)return null;return new float[]{p[0],p[1]};}
}
