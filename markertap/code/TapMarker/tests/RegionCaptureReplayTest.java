package jp.tapmarker;
import java.nio.*;import java.util.*;
/** Existing recorded pixels through padded native RGBA crops, including helper search. */
public final class RegionCaptureReplayTest {
 static int checks;
 static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 public static void main(String[] args)throws Exception{
  Video6774ReplayTest.load();int frames=0;long copied=0,total=0;
  for(Map.Entry<String,VirtualPresetProfileTest.Image> row:Video6774ReplayTest.frames.entrySet()){
   String game=row.getKey().split(":")[0];VirtualPresetProfileTest.Image image=row.getValue();int w=image.width,h=image.height,stride=w*4+12;
   ByteBuffer bytes=ByteBuffer.allocate(5+(h-1)*stride+w*4);
   for(int y=0;y<h;y++)for(int x=0;x<w;x++){int c=image.get(x,y),p=5+y*stride+x*4;bytes.put(p,(byte)(c>>16));bytes.put(p+1,(byte)(c>>8));bytes.put(p+2,(byte)c);bytes.put(p+3,(byte)255);}bytes.position(5);
   RgbaRegionSource rgba=new RgbaRegionSource(bytes,w,h,stride,4,null);
   Video6772ReplayTest.Run original=new Video6772ReplayTest.Run(game,"verify"),region=new Video6772ReplayTest.Run(game,"verify");
   for(long at:new long[]{1000,1350}){
    PointPixels tiled=new PointPixels(w,h,rgba,256,null);
    PhaseEngine.Watch a=original.observe(w,h,image,at),b=region.observe(w,h,tiled,at);
    check(Objects.equals(a==null?null:a.id,b==null?null:b.id),"recorded action identical through region source: "+row.getKey());
    check(original.samples.keySet().equals(region.samples.keySet()),"all detector/guard sample keys preserved: "+row.getKey());
    for(String key:original.samples.keySet())check(Arrays.equals(original.samples.get(key),region.samples.get(key)),"recorded sample/target exact: "+row.getKey()+"/"+key);
    if(a!=null&&a.action==PhaseEngine.WAIT){original.e.finish(a,at);region.e.finish(b,at);}
   }
   copied+=rgba.copiedPixels;total+=(long)w*h;frames++;
  }
  check(copied<=total*2,"recorded source conversions stay within two native frame passes, including fresh caches for both observations");
  System.out.println("RegionCaptureReplayTest: "+checks+" checks; "+frames+" recorded frames; full and ROI detector results/coordinates identical; source point reads="+copied+", native frame pixels="+total+"; no device throughput claim");
 }
}
