package jp.tapmarker;
import android.graphics.Bitmap;

/** One exact software readback per retained frame, shared by all background readers. */
final class HardwarePixelSource implements TilePixels.Source, RegionSampler.Pixels, AutoCloseable {
 private final Bitmap hardware;
 private volatile Bitmap software;
 private volatile boolean closed;
 HardwarePixelSource(Bitmap image) {
  if(image==null)throw new IllegalArgumentException("hardware image");
  hardware=image;
 }
 private Bitmap readable() {
  if(closed)throw new IllegalStateException("frame released");
  Bitmap ready=software;
  if(ready!=null)return ready;
  synchronized(this) {
   if(closed)throw new IllegalStateException("frame released");
   if(software==null) {
    if(!FrameRetirement.permitsNewFrame())throw new IllegalStateException("Native frame retirement backlog");
    Bitmap copy=hardware.getConfig()==Bitmap.Config.HARDWARE?hardware.copy(Bitmap.Config.ARGB_8888,false):hardware;
    if(copy==null)throw new IllegalStateException("hardware readback failed");
    software=copy;
   }
   return software;
  }
 }
 @Override public int get(int x,int y) {return readable().getPixel(x,y);}
 @Override public void read(int x,int y,int width,int height,int[] out,int stride) {
  readable().getPixels(out,0,stride,x,y,width,height);
 }
 // Called only by the off-UI retirement worker after the last RegionImage lease ends.
 @Override public synchronized void close() {
  if(closed)return;
  closed=true;
  Bitmap copy=software;software=null;
  try {if(copy!=null&&copy!=hardware)copy.recycle();}
  finally {hardware.recycle();}
 }
}
