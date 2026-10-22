package android.graphics;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
public final class Bitmap {
 public enum Config {ARGB_8888,HARDWARE}
 public final int width,height;public final Config config;
 public final AtomicInteger copies=new AtomicInteger(),recycles=new AtomicInteger();
 public volatile Bitmap lastCopy;public volatile boolean nullCopy;
 public CountDownLatch entered,proceed;
 public Bitmap(int w,int h,Config c){width=w;height=h;config=c;}
 public Config getConfig(){return config;}
 public Bitmap copy(Config c,boolean mutable){
  copies.incrementAndGet();
  if(entered!=null){entered.countDown();try{proceed.await();}catch(InterruptedException e){throw new IllegalStateException(e);}}
  if(nullCopy)return null;
  lastCopy=new Bitmap(width,height,c);return lastCopy;
 }
 public int getPixel(int x,int y){
  if(config==Config.HARDWARE||recycles.get()>0)throw new IllegalStateException("unreadable bitmap");
  if(x<0||x>=width||y<0||y>=height)throw new IllegalArgumentException("bounds");
  return 0xff000000|(y<<12)|x;
 }
 public void getPixels(int[] out,int offset,int stride,int x,int y,int w,int h){
  for(int row=0;row<h;row++)for(int col=0;col<w;col++)out[offset+row*stride+col]=getPixel(x+col,y+row);
 }
 public void recycle(){if(recycles.incrementAndGet()!=1)throw new AssertionError("double recycle");}
}
