package jp.tapmarker;
import java.util.*;
/** Bounded native-coordinate cache. A tile allocates storage, not a bulk image read. */
final class PointPixels implements RegionSampler.Pixels {
 interface Observer {void tile(int x,int y,int width,int height);}
 static final int SIDE=32;
 static final class Tile {final int[] values=new int[SIDE*SIDE];final BitSet present=new BitSet(SIDE*SIDE);}
 final int width,height,capacity;final RegionSampler.Pixels source;final Observer observer;
 final LinkedHashMap<Long,Tile> tiles=new LinkedHashMap<Long,Tile>(16,.75f,true);
 long reads,hits;private long lastKey=Long.MIN_VALUE;private Tile last;
 PointPixels(int width,int height,RegionSampler.Pixels source,int capacity,Observer observer){if(width<=0||height<=0||source==null||capacity<1)throw new IllegalArgumentException("point source");this.width=width;this.height=height;this.source=source;this.capacity=capacity;this.observer=observer;}
 public int get(int x,int y){
  if(x<0||y<0||x>=width||y>=height)throw new IndexOutOfBoundsException();
  int tx=x/SIDE,ty=y/SIDE;long key=((long)ty<<32)|(tx&0xffffffffL);Tile tile;
  if(key==lastKey)tile=last;
  else{tile=tiles.get(key);if(tile==null){
    lastKey=Long.MIN_VALUE;last=null;
    if(tiles.size()>=capacity){Map.Entry<Long,Tile> oldest=tiles.entrySet().iterator().next();tile=oldest.getValue();tiles.remove(oldest.getKey());tile.present.clear();}else tile=new Tile();
    tiles.put(key,tile);if(observer!=null){int left=tx*SIDE,top=ty*SIDE;observer.tile(left,top,Math.min(SIDE,width-left),Math.min(SIDE,height-top));}
   }
   lastKey=key;last=tile;
  }
  int index=(y%SIDE)*SIDE+x%SIDE;if(tile.present.get(index)){hits++;return tile.values[index];}
  int color=source.get(x,y);tile.values[index]=color;tile.present.set(index);reads++;return color;
 }
}
