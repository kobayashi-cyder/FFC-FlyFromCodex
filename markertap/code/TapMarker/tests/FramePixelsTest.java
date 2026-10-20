package jp.tapmarker;
import java.nio.ByteBuffer;import java.util.Arrays;import java.util.Random;
public class FramePixelsTest {
 static int checks;
 static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 public static void main(String[] args){
  Random random=new Random(154);
  for(int trial=0;trial<200;trial++){
   int width=1+random.nextInt(20),height=1+random.nextInt(20),stride=4+random.nextInt(5),row=width*stride+random.nextInt(15),base=random.nextInt(8);
   int length=(height-1)*row+(width-1)*stride+4;ByteBuffer image=trial%2==0?ByteBuffer.allocate(base+length):ByteBuffer.allocateDirect(base+length);
   byte[] expected=new byte[width*height*4];random.nextBytes(expected);
   for(int y=0;y<height;y++)for(int x=0;x<width;x++)for(int c=0;c<4;c++)image.put(base+y*row+x*stride+c,expected[(y*width+x)*4+c]);
   image.position(base);byte[] reuse=new byte[expected.length];Arrays.fill(reuse,(byte)123);
   byte[] packed=FramePixels.pack(image,width,height,row,stride,reuse);
   check(Arrays.equals(packed,expected),"visible RGBA bytes preserved despite row/pixel padding");
   check(image.position()==base,"buffer ownership and position remain unchanged");
   check(packed==reuse,"frame buffer reused to bound allocation during continuous rendering");
   image.limit(image.limit()-1);boolean rejected=false;try{FramePixels.pack(image,width,height,row,stride,null);}catch(IllegalArgumentException shortFrame){rejected=true;}
   check(rejected,"truncated final pixel is rejected rather than becoming a stale frame");
  }
  ByteBuffer lastRow=ByteBuffer.wrap(new byte[]{1,2,3,4,5,6,7,8});
  check(FramePixels.pack(lastRow,2,1,16,4,null).length==8,"final row needs no trailing row padding");
  System.out.println("FramePixelsTest: "+checks+" checks passed; ImageReader device formats not tested");
 }
}
