package jp.tapmarker;
public final class MultiWindowLayout {
 public static int[] cell(int width,int height,int count,int index){if(width<1||height<1||count<1||index<0||index>=count)throw new IllegalArgumentException("invalid layout");int columns=count<=2?1:(int)Math.ceil(Math.sqrt(count*width/(double)height));columns=Math.max(1,Math.min(count,columns));int rows=(count+columns-1)/columns;int x=index%columns,y=index/columns;return new int[]{x*width/columns,y*height/rows,(x+1)*width/columns,(y+1)*height/rows};}
 public static boolean splitLabel(String label){return "分割画面".equals(label)||"画面を分割".equals(label)||"分割表示".equals(label)||"分割画面にする".equals(label)||"Split screen".equalsIgnoreCase(label)||"Split top".equalsIgnoreCase(label)||"Split left".equalsIgnoreCase(label);}
}
