package jp.tapmarker;
import java.util.*;import java.util.concurrent.atomic.AtomicInteger;
public final class PointPixelsTest {
 static int checks;static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 public static void main(String[] args){
  int w=1080,h=2424;AtomicInteger reads=new AtomicInteger();PointPixels pixels=new PointPixels(w,h,(x,y)->{reads.incrementAndGet();return 0xff000000|x<<12|y;},256,null);
  check(pixels.get(2,3)==(0xff000000|2<<12|3)&&reads.get()==1,"first point does not copy the other 1023 tile pixels");
  for(int n=0;n<100;n++)check(pixels.get(2,3)==(0xff000000|2<<12|3),"identical native coordinate reused");
  check(reads.get()==1&&pixels.reads==1&&pixels.hits==100,"repeated points have no additional native conversion");
  check(pixels.get(4,5)==(0xff000000|4<<12|5)&&reads.get()==2,"second coordinate converts just one more pixel");
  Random random=new Random(119);
  for(int i=0;i<50000;i++){int x=random.nextInt(w),y=random.nextInt(h);check(pixels.get(x,y)==(0xff000000|x<<12|y),"evicted and retained tiles preserve exact native coordinates");}
  check(pixels.tiles.size()<=256,"retained tile arrays bounded independently of native frame size");
  AtomicInteger attempts=new AtomicInteger();PointPixels failed=new PointPixels(64,64,(x,y)->{if(attempts.incrementAndGet()==1)throw new IllegalStateException("unavailable pixel");return 0xff123456;},1,null);
  boolean refused=false;try{failed.get(1,1);}catch(IllegalStateException expected){refused=true;}check(refused,"missing pixel propagates instead of becoming zero evidence");
  check(failed.get(1,1)==0xff123456&&attempts.get()==2,"failed read is never cached as a valid pixel");
  failed.get(40,40);check(failed.get(1,1)==0xff123456&&failed.tiles.size()==1,"reused storage cannot return another tile's stale color");
  RegionPlan.Builder plan=new RegionPlan.Builder(65,67,"edges");PointPixels edges=new PointPixels(65,67,(x,y)->0xff000000,2,plan::add);
  edges.get(64,66);check(plan.build().coveredPixels()==3,"observer reports exact clipped edge dependency");
  for(int[] xy:new int[][]{{-1,0},{0,-1},{65,0},{0,67}}){refused=false;try{edges.get(xy[0],xy[1]);}catch(IndexOutOfBoundsException expected){refused=true;}check(refused,"source bounds are enforced");}
  System.out.println("PointPixelsTest: "+checks+" checks passed; sparse native reads, bounded cache, eviction, exceptions and exact dependency tiles");
 }
}
