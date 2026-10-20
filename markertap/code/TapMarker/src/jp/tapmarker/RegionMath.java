package jp.tapmarker;
public final class RegionMath {
 public static int[] bounds(float[] r,int w,int h){if(r==null||r.length!=4)throw new IllegalArgumentException("monitor region required");int left=Math.max(0,Math.min(w-1,(int)(r[0]*w))),top=Math.max(0,Math.min(h-1,(int)(r[1]*h)));int right=Math.max(left+1,Math.min(w,(int)(r[2]*w))),bottom=Math.max(top+1,Math.min(h,(int)(r[3]*h)));return new int[]{left,top,right,bottom};}
 public static float[] center(float[] r,int w,int h){int[] b=bounds(r,w,h);return new float[]{(b[0]+b[2])/2f,(b[1]+b[3])/2f};}
 public static String key(float[] r){if(r==null)return "";return Math.round(r[0]*10000)+","+Math.round(r[1]*10000)+","+Math.round(r[2]*10000)+","+Math.round(r[3]*10000);}
}
