package jp.tapmarker;
import java.util.*;

/** A finite set of separate contacts around each main-play marker. Never a path. */
public final class DiscreteTapTargets {
 private static final int[][] OFFSETS={{0,0},{-1,0},{1,0},{0,-1},{0,1}};
 public static float[] points(float[] centers,int[] bounds,int step){
  if(centers==null||centers.length==0||centers.length%2!=0||bounds==null||bounds.length!=4||bounds[0]>=bounds[2]||bounds[1]>=bounds[3]||step<0)throw new IllegalArgumentException("invalid discrete targets");
  for(int i=0;i<centers.length;i+=2)if(!Float.isFinite(centers[i])||!Float.isFinite(centers[i+1])||centers[i]<bounds[0]||centers[i]>=bounds[2]||centers[i+1]<bounds[1]||centers[i+1]>=bounds[3])throw new IllegalArgumentException("marker outside target");
  float[] fixed=DiscreteTapPosition.points(centers,bounds);if(step==0)return fixed;
  ArrayList<Float> result=new ArrayList<>();ArrayList<Set<Long>> used=new ArrayList<>();for(int i=0;i<fixed.length/2;i++)used.add(new HashSet<>());
  // Interleave markers first, then change the offset. Each marker remains equally
  // represented when all five points fit; clipped duplicate points are omitted.
  for(int[] offset:OFFSETS)for(int i=0;i<fixed.length;i+=2){
   float x=DiscreteTapPosition.coordinate(fixed[i]+offset[0]*(float)step,bounds[0],bounds[2]),y=DiscreteTapPosition.coordinate(fixed[i+1]+offset[1]*(float)step,bounds[1],bounds[3]);
   long key=((long)(int)x<<32)^((int)y&0xffffffffL);if(used.get(i/2).add(key)){result.add(x);result.add(y);}
  }
  float[] out=new float[result.size()];for(int i=0;i<out.length;i++)out[i]=result.get(i);return out;
 }
 private DiscreteTapTargets(){}
}
