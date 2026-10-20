package jp.tapmarker;
import java.nio.ByteBuffer;
/** Pack only visible RGBA pixels, including buffers without padding on the final row. */
final class FramePixels {
 static final class Raster implements RegionSampler.Pixels {
  final int width,height;final int[] pixels;final long capturedAt;
  Raster(int w,int h,int[] pixels,long at){width=w;height=h;this.pixels=pixels;capturedAt=at;}
  public int get(int x,int y){return pixels[y*width+x];}
 }
 /** ImageReader RGBA to Android ARGB, without another full-size byte/Bitmap copy. */
 static int[] argb(ByteBuffer input,int width,int height,int rowStride,int pixelStride,int[] reuse){
  if(width<=0||height<=0||pixelStride<4||rowStride<(long)width*pixelStride)throw new IllegalArgumentException("invalid frame layout");
  long needed=(long)(height-1)*rowStride+(long)(width-1)*pixelStride+4,pixels=(long)width*height;
  if(needed>input.remaining()||pixels>Integer.MAX_VALUE)throw new IllegalArgumentException("short frame buffer");
  int[] output=reuse!=null&&reuse.length==pixels?reuse:new int[(int)pixels];
  if(pixelStride==4&&rowStride%4==0){
   java.nio.IntBuffer view=input.slice().order(java.nio.ByteOrder.LITTLE_ENDIAN).asIntBuffer();
   for(int y=0;y<height;y++){view.position(y*(rowStride/4));view.get(output,y*width,width);}
   for(int i=0;i<output.length;i++){int rgba=output[i];output[i]=(rgba&0xff00ff00)|((rgba&255)<<16)|((rgba>>>16)&255);}
  }else{
   int base=input.position();for(int y=0;y<height;y++)for(int x=0;x<width;x++){int offset=base+y*rowStride+x*pixelStride;output[y*width+x]=((input.get(offset+3)&255)<<24)|((input.get(offset)&255)<<16)|((input.get(offset+1)&255)<<8)|(input.get(offset+2)&255);}
  }
  return output;
 }
 static int[] thumbnail(int[] source,int width,int height,int targetWidth,int targetHeight){
  int[] result=new int[targetWidth*targetHeight];
  for(int y=0;y<targetHeight;y++){int sy=Math.min(height-1,(int)((y+.5)*height/targetHeight));for(int x=0;x<targetWidth;x++){int sx=Math.min(width-1,(int)((x+.5)*width/targetWidth));result[y*targetWidth+x]=source[sy*width+sx];}}
  return result;
 }
 static int[] thumbnail(RegionSampler.Pixels source,int width,int height,int targetWidth,int targetHeight){
  int[] result=new int[targetWidth*targetHeight];
  for(int y=0;y<targetHeight;y++){int sy=Math.min(height-1,(int)((y+.5)*height/targetHeight));for(int x=0;x<targetWidth;x++){int sx=Math.min(width-1,(int)((x+.5)*width/targetWidth));result[y*targetWidth+x]=source.get(sx,sy);}}return result;
 }
 static boolean needsBitmapUnpremultiply(int[] pixels){
  for(int color:pixels)if((color>>>24)!=255&&color!=0)return true;
  return false;
 }
 static byte[] pack(ByteBuffer input,int width,int height,int rowStride,int pixelStride,byte[] reuse){
  if(width<=0||height<=0||pixelStride<4||rowStride<(long)width*pixelStride)throw new IllegalArgumentException("invalid frame layout");
  int base=input.position();long needed=(long)(height-1)*rowStride+(long)(width-1)*pixelStride+4;
  long bytes=(long)width*height*4;if(needed>input.remaining()||bytes>Integer.MAX_VALUE)throw new IllegalArgumentException("short frame buffer");
  byte[] output=reuse!=null&&reuse.length==(int)bytes?reuse:new byte[(int)bytes];ByteBuffer view=input.duplicate();
  for(int y=0;y<height;y++){int offset=y*width*4;if(pixelStride==4){view.position(base+y*rowStride);view.get(output,offset,width*4);}else for(int x=0;x<width;x++)for(int c=0;c<4;c++)output[offset+x*4+c]=view.get(base+y*rowStride+x*pixelStride+c);}
  return output;
 }
}
