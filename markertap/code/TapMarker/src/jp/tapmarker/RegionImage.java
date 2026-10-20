package jp.tapmarker;
import android.graphics.*;import android.media.Image;import java.nio.ByteBuffer;
/** Owned by a Window under frameLock, including every synchronous pixel consumer. */
final class RegionImage implements AutoCloseable {
 final int width,height;final long capturedAt;final TilePixels.Source source;final RegionSampler.Pixels points;private final Runnable release;
 private boolean closed;
 private RegionImage(int w,int h,long at,TilePixels.Source source,RegionSampler.Pixels points,Runnable release){width=w;height=h;capturedAt=at;this.source=source;this.points=points;this.release=release;}
 static RegionImage fromImage(Image image,int width,int height,long at){
  if(image.getWidth()!=width||image.getHeight()!=height)throw new IllegalArgumentException("display/image geometry changed");
  Image.Plane plane=image.getPlanes()[0];
  RgbaRegionSource source=new RgbaRegionSource(plane.getBuffer(),width,height,plane.getRowStride(),plane.getPixelStride(),(rgba,w,h,out,stride)->{
   Bitmap compatible=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
   try{compatible.copyPixelsFromBuffer(ByteBuffer.wrap(rgba));compatible.getPixels(out,0,stride,0,0,w,h);}finally{compatible.recycle();}
  });
  return new RegionImage(width,height,at,source,source,image::close);
 }
 static RegionImage fromHardware(Bitmap hardware,long at){
  if(hardware==null)throw new IllegalArgumentException("hardware image");
  TilePixels.Source source=(x,y,w,h,out,stride)->{
   // Bitmap.createBitmap(hardware,x,y,w,h) can copy the WHOLE hardware source
   // first. Record a cropped Picture instead, then read back only that crop.
   Picture picture=new Picture();Canvas canvas=picture.beginRecording(w,h);
   canvas.drawBitmap(hardware,new Rect(x,y,x+w,y+h),new Rect(0,0,w,h),null);picture.endRecording();
   Bitmap crop=Bitmap.createBitmap(picture,w,h,Bitmap.Config.ARGB_8888);
   try{crop.getPixels(out,0,stride,0,0,w,h);}finally{crop.recycle();}
  };
  int w=hardware.getWidth(),h=hardware.getHeight();
  return new RegionImage(w,h,at,source,new TilePixels(w,h,source,64),hardware::recycle);
 }
 RegionSampler.Pixels tiles(RegionPlan.Builder dependencies,long[] copied){
  check();return new TilePixels(width,height,(x,y,w,h,out,stride)->{check();source.read(x,y,w,h,out,stride);if(dependencies!=null)dependencies.add(x,y,w,h);if(copied!=null)copied[0]+=(long)w*h;},64);
 }
 RegionSampler.Pixels points(){check();return (x,y)->{check();return points.get(x,y);};}
 private void check(){if(closed)throw new IllegalStateException("image is closed");}
 public void close(){if(!closed){closed=true;release.run();}}
}
