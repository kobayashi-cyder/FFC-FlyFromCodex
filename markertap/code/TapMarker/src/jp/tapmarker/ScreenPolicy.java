package jp.tapmarker;
import java.util.*;
public final class ScreenPolicy {
 public static class Example {String pkg;int width,height,action;float x,y,x2,y2;long duration;float[] raw,embedding;String regionKey="";String[] markerIds=new String[0];}
 public static int select(List<? extends Example> items,String pkg,int w,int h,float[] raw,float[] embed){return select(items,pkg,w,h,raw,embed,"");}
 public static int select(List<? extends Example> items,String pkg,int w,int h,float[] raw,float[] embed,String regionKey){int best=-1;float bestDistance=Float.POSITIVE_INFINITY;for(int i=0;i<items.size();i++){Example r=items.get(i);if(!r.regionKey.equals(regionKey)||!r.pkg.equals(pkg)||r.width!=w||r.height!=h||FlyCircuit.distance(r.raw,raw)>.006f)continue;float d=FlyCircuit.distance(r.embedding,embed);if(d<bestDistance){bestDistance=d;best=i;}}if(best<0)return -1;Example winner=items.get(best);for(int i=0;i<items.size();i++){if(i==best)continue;Example r=items.get(i);if(!r.regionKey.equals(regionKey)||!r.pkg.equals(pkg)||r.width!=w||r.height!=h||FlyCircuit.distance(r.raw,raw)>.006f)continue;boolean different=(r.action==4&&!Arrays.equals(r.markerIds,winner.markerIds))||r.action!=winner.action||Math.hypot(r.x-winner.x,r.y-winner.y)>24||Math.hypot(r.x2-winner.x2,r.y2-winner.y2)>24;if(different&&FlyCircuit.distance(r.embedding,embed)<=bestDistance*1.2f+1e-8f)return -1;}return best;}
}
