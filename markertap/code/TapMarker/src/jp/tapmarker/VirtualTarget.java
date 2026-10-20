package jp.tapmarker;
public final class VirtualTarget {
 public static float[] map(int displayId,int width,int height,float x,float y){if(displayId<=0||width<=0||height<=0||Float.isNaN(x)||Float.isNaN(y)||x<0||x>1||y<0||y>1)throw new IllegalArgumentException("invalid virtual target");return new float[]{Math.min(width-1,x*width),Math.min(height-1,y*height)};}
}
