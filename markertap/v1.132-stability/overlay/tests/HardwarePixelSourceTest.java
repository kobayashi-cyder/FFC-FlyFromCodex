package jp.tapmarker;
import android.graphics.Bitmap;
import java.util.Arrays;
import java.util.concurrent.*;

public final class HardwarePixelSourceTest {
 static int checks;
 static void check(boolean value,String reason){checks++;if(!value)throw new AssertionError(reason);}
 public static void main(String[] args)throws Exception {
  Bitmap b=new Bitmap(8,6,Bitmap.Config.HARDWARE);HardwarePixelSource source=new HardwarePixelSource(b);
  b.entered=new CountDownLatch(1);b.proceed=new CountDownLatch(1);
  ExecutorService pool=Executors.newFixedThreadPool(2);
  Future<Integer> a=pool.submit(()->source.get(7,5));
  check(b.entered.await(2,TimeUnit.SECONDS),"hardware readback did not start");
  Future<Integer> c=pool.submit(()->source.get(0,0));b.proceed.countDown();
  check(a.get(2,TimeUnit.SECONDS)==(0xff000000|(5<<12)|7),"exact corner pixel lost");
  check(c.get(2,TimeUnit.SECONDS)==0xff000000,"concurrent point changed");pool.shutdownNow();
  check(b.copies.get()==1,"readback repeated per reader");
  int[] out=new int[10];Arrays.fill(out,42);source.read(2,1,3,2,out,5);
  check(out[0]==(0xff001000|2)&&out[7]==(0xff002000|4)&&out[3]==42&&out[9]==42,"crop/stride changed pixels");
  for(int i=0;i<2000;i++)source.get(i%8,i%6);
  check(b.copies.get()==1&&b.lastCopy.width==8&&b.lastCopy.height==6,"frame resized or recaptured per tile");
  source.close();source.close();check(b.recycles.get()==1&&b.lastCopy.recycles.get()==1,"buffers not released exactly once");
  try{source.get(0,0);throw new AssertionError("closed frame readable");}catch(IllegalStateException expected){checks++;}
  Bitmap unread=new Bitmap(8,6,Bitmap.Config.HARDWARE);new HardwarePixelSource(unread).close();
  check(unread.copies.get()==0&&unread.recycles.get()==1,"unused frame allocated readback");
  Bitmap cpu=new Bitmap(8,6,Bitmap.Config.ARGB_8888);HardwarePixelSource direct=new HardwarePixelSource(cpu);
  check(direct.get(3,2)==(0xff002000|3)&&cpu.copies.get()==0,"software frame recopied");direct.close();check(cpu.recycles.get()==1,"software input double freed");
  Bitmap bad=new Bitmap(8,6,Bitmap.Config.HARDWARE);bad.nullCopy=true;HardwarePixelSource retry=new HardwarePixelSource(bad);
  try{retry.get(0,0);throw new AssertionError("null copy accepted");}catch(IllegalStateException expected){checks++;}
  bad.nullCopy=false;check(retry.get(0,0)==0xff000000,"failed readback poisoned frame");retry.close();
  Bitmap denied=new Bitmap(8,6,Bitmap.Config.HARDWARE);HardwarePixelSource gated=new HardwarePixelSource(denied);FrameRetirement.permitted=false;
  try{gated.get(0,0);throw new AssertionError("retirement limit ignored");}catch(IllegalStateException expected){checks++;}finally{FrameRetirement.permitted=true;gated.close();}
  check(denied.copies.get()==0,"readback started despite retirement pressure");
  System.out.println("HardwarePixelSourceTest: "+checks+" checks passed, 2000 reads share one exact readback");
 }
}
