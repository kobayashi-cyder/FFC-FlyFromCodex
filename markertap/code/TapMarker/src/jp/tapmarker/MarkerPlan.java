package jp.tapmarker;
import java.util.*;
public final class MarkerPlan {
 public static class Point {public final float x,y;public Point(float x,float y){this.x=x;this.y=y;}}
 public static Point[] resolve(String[] ids,Map<String,Point> live){if(ids==null||ids.length==0)return null;Point[] result=new Point[ids.length];for(int i=0;i<ids.length;i++){result[i]=live.get(ids[i]);if(result[i]==null)return null;}return result;}
}
