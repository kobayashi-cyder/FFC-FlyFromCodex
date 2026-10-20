package jp.tapmarker;
public final class WindowCoordinates {
 public static float[] point(int left,int top,int width,int height,float x,float y){if(width<=0||height<=0||x<0||x>1||y<0||y>1)throw new IllegalArgumentException("invalid window point");return new float[]{left+Math.min(width-1,x*width),top+Math.min(height-1,y*height)};}
 public static boolean same(int[] a,int[] b){return java.util.Arrays.equals(a,b);}
}
