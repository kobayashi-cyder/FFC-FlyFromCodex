package jp.tapmarker;
import java.util.LinkedHashMap;import java.util.Map;

/** Exact, bounded tile cache. A bulk read replaces thousands of JNI pixel reads. */
public final class TilePixels implements RegionSampler.Pixels {
 public interface Source {void read(int x,int y,int width,int height,int[] pixels,int stride);}
 static final int SIDE=32;
 final int width,height,capacity;final Source source;
 final LinkedHashMap<Long,int[]> tiles=new LinkedHashMap<Long,int[]>(16,.75f,true);
 public long reads;
 private long lastKey=Long.MIN_VALUE;private int[] lastValues;
 public TilePixels(int width,int height,Source source,int capacity){
  if(width<=0||height<=0||source==null||capacity<1)throw new IllegalArgumentException("invalid tile source");
  this.width=width;this.height=height;this.source=source;this.capacity=capacity;
 }
 public int get(int x,int y){
  if(x<0||y<0||x>=width||y>=height)throw new IndexOutOfBoundsException();
  int tx=x/SIDE,ty=y/SIDE;long key=((long)ty<<32)|(tx&0xffffffffL);
  if(key==lastKey)return lastValues[(y%SIDE)*SIDE+x%SIDE];
  int[] values=tiles.get(key);
  if(values==null){
   if(tiles.size()>=capacity){Map.Entry<Long,int[]> oldest=tiles.entrySet().iterator().next();values=oldest.getValue();tiles.remove(oldest.getKey());}
   else values=new int[SIDE*SIDE];
   int left=tx*SIDE,top=ty*SIDE;source.read(left,top,Math.min(SIDE,width-left),Math.min(SIDE,height-top),values,SIDE);reads++;tiles.put(key,values);
  }
  lastKey=key;lastValues=values;return values[(y%SIDE)*SIDE+x%SIDE];
 }
}
