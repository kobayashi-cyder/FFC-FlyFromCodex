package jp.tapmarker;

import java.util.LinkedHashMap;
import java.util.Map;

/** Exact samples of one immutable image; never retained across frames or windows. */
public final class FrameSampleCache implements RegionSampler.Pixels {
 private final int width,height,capacity;
 private final RegionSampler.Pixels source;
 private final FrameCircuit.Frame lease;
 private final LinkedHashMap<Key,float[]> samples=new LinkedHashMap<Key,float[]>(16,.75f,true);
 public long hits,misses;
 public FrameSampleCache(int width,int height,RegionSampler.Pixels source,int capacity){
  this(width,height,source,capacity,null);
 }
 private FrameSampleCache(int width,int height,RegionSampler.Pixels source,int capacity,FrameCircuit.Frame lease){
  if(width<=0||height<=0||source==null||capacity<1)throw new IllegalArgumentException("invalid frame cache");
  this.width=width;this.height=height;this.source=source;this.capacity=capacity;this.lease=lease;
 }
 static RegionSampler.Pixels wrap(int width,int height,RegionSampler.Pixels pixels){
  if(pixels instanceof FrameSampleCache){FrameSampleCache c=(FrameSampleCache)pixels;if(c.width==width&&c.height==height)return pixels;}
  if(pixels instanceof FrameCircuit.Frame){FrameCircuit.Frame frame=(FrameCircuit.Frame)pixels;return new FrameSampleCache(width,height,frame.samplingView(),384,frame);}
  return new FrameSampleCache(width,height,pixels,384);
 }
 public int get(int x,int y){if(lease!=null)lease.checkLease();return source.get(x,y);}
 float[] sample(int w,int h,float[] region){
  if(lease!=null)lease.checkLease();
  if(w!=width||h!=height)return RegionSampler.sample(w,h,region,source);
  int[] bounds=RegionMath.bounds(region,w,h);Key key=new Key(bounds);float[] found=samples.get(key);
  if(found!=null){hits++;return found.clone();}
  misses++;float[] raw=RegionSampler.sampleBounds(bounds,source);
  if(samples.size()>=capacity){Map.Entry<Key,float[]> oldest=samples.entrySet().iterator().next();samples.remove(oldest.getKey());}
  samples.put(key,raw.clone());return raw;
 }
 public int size(){return samples.size();}
 private static final class Key {
  final int left,top,right,bottom;
  Key(int[] b){left=b[0];top=b[1];right=b[2];bottom=b[3];}
  public int hashCode(){return ((left*31+top)*31+right)*31+bottom;}
  public boolean equals(Object value){if(!(value instanceof Key))return false;Key k=(Key)value;return left==k.left&&top==k.top&&right==k.right&&bottom==k.bottom;}
 }
}
