package jp.tapmarker;
import java.nio.ByteBuffer;
/** Reads only a requested native rectangle, respecting plane position and both strides. */
final class RgbaRegionSource implements TilePixels.Source,RegionSampler.Pixels {
 interface Alpha {void convert(byte[] rgba,int width,int height,int[] output,int stride);}
 final int width,height,rowStride,pixelStride;final ByteBuffer data;final Alpha alpha;
 long copiedPixels;
 RgbaRegionSource(ByteBuffer input,int w,int h,int row,int pixel,Alpha alpha){
  if(input==null||w<=0||h<=0||pixel<4||row<(long)w*pixel)throw new IllegalArgumentException("invalid RGBA layout");
  long end=(long)(h-1)*row+(long)(w-1)*pixel+4;
  if(end>input.remaining())throw new IllegalArgumentException("short RGBA plane");
  width=w;height=h;rowStride=row;pixelStride=pixel;data=input.slice().asReadOnlyBuffer();this.alpha=alpha;
 }
 int raw(int x,int y){int p=y*rowStride+x*pixelStride;return (data.get(p+3)&255)<<24|(data.get(p)&255)<<16|(data.get(p+1)&255)<<8|(data.get(p+2)&255);}
 public int get(int x,int y){
  if(x<0||y<0||x>=width||y>=height)throw new IndexOutOfBoundsException();
  int color=raw(x,y);copiedPixels++;
  if((color>>>24)==255||color==0)return color;
  int[] one=new int[1];convert(x,y,1,1,one,1);return one[0];
 }
 public void read(int x,int y,int w,int h,int[] output,int stride){
  if(x<0||y<0||w<=0||h<=0||(long)x+w>width||(long)y+h>height||stride<w||output==null||output.length<(long)(h-1)*stride+w)throw new IllegalArgumentException("invalid RGBA crop");
  boolean translucent=false;
  for(int j=0;j<h;j++)for(int i=0;i<w;i++){int color=raw(x+i,y+j);output[j*stride+i]=color;if((color>>>24)!=255&&color!=0)translucent=true;}
  copiedPixels+=(long)w*h;if(translucent)convert(x,y,w,h,output,stride);
 }
 private void convert(int x,int y,int w,int h,int[] output,int stride){
  if(alpha==null)throw new IllegalStateException("alpha conversion required");
  byte[] packed=new byte[w*h*4];
  for(int j=0;j<h;j++)for(int i=0;i<w;i++){int from=(y+j)*rowStride+(x+i)*pixelStride,to=(j*w+i)*4;for(int c=0;c<4;c++)packed[to+c]=data.get(from+c);}
  alpha.convert(packed,w,h,output,stride);
 }
}
