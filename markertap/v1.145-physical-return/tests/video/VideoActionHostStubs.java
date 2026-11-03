package jp.tapmarker;
import java.util.*;
final class PhaseEngine {String current="kaitaku-default-main";}
final class KaitakuNamedControls {
 static final class Control {
  final String label; final float[] bounds;
  Control(String s,float[] b){label=s;bounds=b;}
 }
 static void clear(Map<String,float[]> out){out.put("home",new float[]{0});out.remove("main");out.remove("close");}
 static void apply(PhaseEngine e,String context,List<Control> controls,Map<String,float[]> out,int w,int h){out.put("legacy",new float[]{1});}
}
final class KaitakuActionEvidence {
 static void put(Map<String,float[]> out,String key,float[] bounds){out.put(key,new float[]{1});if(bounds!=null)out.put(key+"-bounds",bounds.clone());}
}
