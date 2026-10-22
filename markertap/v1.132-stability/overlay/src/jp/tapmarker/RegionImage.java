package jp.tapmarker;

import android.graphics.*;
import android.media.Image;
import java.nio.ByteBuffer;

/** Owns a retained image; exact point caches live only for one analysis. */
final class RegionImage implements AutoCloseable {
 final int width,height;final long capturedAt;final TilePixels.Source source;final RegionSampler.Pixels points;
 private final FrameResource resource;
 private RegionImage(int width,int height,long at,TilePixels.Source source,RegionSampler.Pixels points,Runnable dispose){this.width=width;this.height=height;capturedAt=at;this.source=source;this.points=points;resource=new FrameResource(dispose);}
 static RegionImage fromImage(Image image,int width,int height,long at,FrameResource readerLifetime){
  if(!FrameRetirement.permitsNewFrame())throw new IllegalStateException("Native frame retirement backlog");
  if(image.getWidth()!=width||image.getHeight()!=height)throw new IllegalArgumentException("display/image geometry changed");
  Image.Plane plane=image.getPlanes()[0];
  RgbaRegionSource source=new RgbaRegionSource(plane.getBuffer(),width,height,plane.getRowStride(),plane.getPixelStride(),(packed,w,h,out,stride)->{
   if(!FrameRetirement.permitsNewFrame())throw new IllegalStateException("Native frame retirement backlog");
   Bitmap converted=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
   try{converted.copyPixelsFromBuffer(ByteBuffer.wrap(packed));converted.getPixels(out,0,stride,0,0,w,h);}finally{FrameRetirement.retire(converted::recycle);}
  });
  readerLifetime.retain();
  try{return new RegionImage(width,height,at,source,source,()->FrameRetirement.retire(()->{try{image.close();}finally{readerLifetime.release();}}));}
  catch(RuntimeException|OutOfMemoryError error){readerLifetime.release();throw error;}
 }
 static RegionImage fromHardware(Bitmap image,long at){
  if(image==null)throw new IllegalArgumentException("hardware image");
  if(!FrameRetirement.permitsNewFrame())throw new IllegalStateException("Native frame retirement backlog");
  HardwarePixelSource source=new HardwarePixelSource(image);
  return new RegionImage(image.getWidth(),image.getHeight(),at,source,source,()->FrameRetirement.retire(source::close));
 }
 RegionImage retain(){resource.retain();return this;}
 public void close(){resource.release();}
 private void check(){resource.check();}
 static int pointCacheCapacity(long heapBytes){return heapBytes>=256L*1024*1024?1024:heapBytes>=128L*1024*1024?512:256;}
 RegionSampler.Pixels tiles(RegionPlan.Builder plan,long[] copied){
  check();
  if(points instanceof RgbaRegionSource)return new PointPixels(width,height,(x,y)->{check();int value=points.get(x,y);if(copied!=null)copied[0]++;return value;},pointCacheCapacity(Runtime.getRuntime().maxMemory()),(x,y,w,h)->{if(plan!=null)plan.add(x,y,w,h);});
  return new TilePixels(width,height,(x,y,w,h,out,stride)->{check();source.read(x,y,w,h,out,stride);if(plan!=null)plan.add(x,y,w,h);if(copied!=null)copied[0]+=(long)w*h;},64);
 }
 RegionSampler.Pixels points(){check();return (x,y)->{check();return points.get(x,y);};}
}
