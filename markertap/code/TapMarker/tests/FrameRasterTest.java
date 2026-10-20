package jp.tapmarker;
import java.nio.*;import java.util.*;
public class FrameRasterTest {
 static int checks;static void check(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
 public static void main(String[] args){
  Random r=new Random(165);
  for(int trial=0;trial<300;trial++){
   int w=1+r.nextInt(33),h=1+r.nextInt(40),ps=trial%3==0?4:4+r.nextInt(5),rs=w*ps+r.nextInt(20),base=r.nextInt(9),len=(h-1)*rs+(w-1)*ps+4;
   ByteBuffer plane=trial%2==0?ByteBuffer.allocate(base+len):ByteBuffer.allocateDirect(base+len);
   int[] expected=new int[w*h];
   for(int y=0;y<h;y++)for(int x=0;x<w;x++){int color=r.nextInt();expected[y*w+x]=color;int offset=base+y*rs+x*ps;plane.put(offset,(byte)(color>>>16));plane.put(offset+1,(byte)(color>>>8));plane.put(offset+2,(byte)color);plane.put(offset+3,(byte)(color>>>24));}
   plane.position(base);ByteOrder order=plane.order();int[] reuse=new int[w*h];int[] result=FramePixels.argb(plane,w,h,rs,ps,reuse);
   check(Arrays.equals(result,expected),"exact RGBA channels across non-zero offsets, direct buffers and padded rows");
   check(result==reuse,"repeated frames reuse the same allocation");check(plane.position()==base&&plane.order()==order,"reader buffer position and byte order are untouched");
   FramePixels.Raster frame=new FramePixels.Raster(w,h,result,123);for(int y=0;y<h;y++)for(int x=0;x<w;x++)check(frame.get(x,y)==expected[y*w+x],"snapshot sampling uses full-resolution coordinates");
   plane.limit(plane.limit()-1);boolean rejected=false;try{FramePixels.argb(plane,w,h,rs,ps,reuse);}catch(IllegalArgumentException truncated){rejected=true;}check(rejected,"a truncated frame cannot authorize input");
  }
  int[] original={1,2,3,4};check(Arrays.equals(FramePixels.thumbnail(original,2,2,2,2),original),"unscaled preview has identical pixels");
  check(!FramePixels.needsBitmapUnpremultiply(new int[]{0xff123456,0}),"opaque pixels and transparent black can take the direct path");
  check(FramePixels.needsBitmapUnpremultiply(new int[]{0x80102030}),"semi-transparent pixels use the original Bitmap conversion semantics");
  check(FramePixels.needsBitmapUnpremultiply(new int[]{0x00010203}),"transparent dirty RGB cannot become false recognition evidence");
  System.out.println("FrameRasterTest: "+checks+" checks passed");
 }
}
