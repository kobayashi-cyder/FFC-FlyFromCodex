package jp.tapmarker;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.zip.InflaterInputStream;

/**
 * Foreground screenshot supplied in discussion, 1080x2424, upper-left 310x54
 * digit pixels. Bit-packed foreground mask rather than redistributing the
 * original image, preserving its authentic rendered number shapes.
 */
public final class KurashiruPointHudRecordedTest {
  private static final String FOREGROUND_ZLIB="eNqFlU1u2zAQhcmyAHfltouivEKXWRRlj9Ij5ABFxBvkSrpBrqBddi2BbhiA4PS9oX7sIFYcI5bsEd83b4ZDY269nIiUr794+d3cfoWCuJfMy/uTsDhPIn/0cjkJS9mLVJWfT8I6IkXX8SdRtuGfKNqnkzBfENr18u4sUQB5RTtNNEEvKpqdBygTcc1EaWYaNCMDM+U1g9CM13RiRUiF81sG0LPjBhlIQdYzv1msyBKk6fML3xvaHVaa8SuWDkIzc8Jjgyvi2bCsNUiCAC49daB1IzKvXNOaBaizhUZ67uyGikeApkY5iLU1C6D99FJsJ0FgGmEJo3gQc2VEc03ozL6SQH1I+NssCwcaXQIqbLMUQxHbUfS8oxX7D5EGGfjnSheo9DtlaChWW4vmH4trsN9EVgW/P2X7IhnGEMurN+aH+ZzmUALu7lEVq5WCPUyXWHHkgwcE9eQdG8YXq/62QHPycE7rDj3RO3qiPW3kqa5oth17xzW9IxrLANC40LUDjf4ATe+Ahk90SFhSpmsHmrlGw2eNCOqba9O+rYkmK1pX97utbtxco7kVTevXXfU7Wr1ECysak3BAizvacqBpQRVt0sZI+WFHywfalPtAUxKguR2tH2jsNRiB9SljO1Vx5V+huZYWqGJ9yNzFGspENGLFcqBxG3Suzze2QUJjoT+JNc0HGqtTuT6UtaDoeWzErmvsaJxLQIEGlKPuZZQ/0yPXDjT2+KSpC0fZHP6i0brOiiMDBhQYAQ2awCZ7XIgu5tLcsk4BCweGlf2jfkW96XqMxnwx4IcQrbByPcQeLufeEOKO9e16vpZXcw9r9jHFzib3FpFuHwQrms7EMTLtmyN5GQfVJnzjpBposZltU7nyVthIWqfl2FQpnxyO2Wyb6iTb2PeADydnhmq6s3NR66kT2ud3wpLW8ss7UXacst/eCYty2T//Aa37sdI=";
  static byte[] unpack() throws Exception{
    byte[] zipped=Base64.getDecoder().decode(FOREGROUND_ZLIB);
    InflaterInputStream input=new InflaterInputStream(new ByteArrayInputStream(zipped));
    ByteArrayOutputStream result=new ByteArrayOutputStream();
    byte[] buffer=new byte[4096];int n;
    while((n=input.read(buffer))!=-1)result.write(buffer,0,n);
    byte[] data=result.toByteArray();
    if(data.length!=2093)throw new AssertionError("corrupt saved ROI mask");
    return data;
  }
  public static void main(String[] args)throws Exception{
    final byte[] data=unpack();
    final int W=1080,H=2424;
    final int left=(int)(W*.105f),top=(int)(H*.089f),width=(int)(W*.392f)-left,
              height=(int)(H*.111f)-top;
    final int[] inspections={0};
    KurashiruPointHud.Pixels pixels=(x,y)->{
      inspections[0]++;
      if(x<left||x>=left+width||y<top||y>=top+height)return 0xffffffff;
      int i=(y-top)*width+x-left;
      boolean black=((data[i>>>3]>>(7-(i&7)))&1)!=0;
      return black?0xff000000:0xffffffff;
    };
    long pt=KurashiruPointHud.read(pixels,W,H);
    if(pt!=10175)throw new AssertionError("real screenshot ROI expected 10175pt, got "+pt);
    if(inspections[0]>width*height+100)throw new AssertionError("unbounded region scanning");
    System.out.println("KurashiruPointHudRecordedTest: authentic 10,175pt screenshot passed ("+inspections[0]+" pixels)");
  }
}
