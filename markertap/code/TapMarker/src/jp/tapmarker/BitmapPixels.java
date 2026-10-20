package jp.tapmarker;
import android.graphics.Bitmap;
final class BitmapPixels {
 static RegionSampler.Pixels of(Bitmap image){return FrameSampleCache.wrap(image.getWidth(),image.getHeight(),new TilePixels(image.getWidth(),image.getHeight(),(x,y,w,h,p,stride)->image.getPixels(p,0,stride,x,y,w,h),64));}
 private BitmapPixels(){}
}
