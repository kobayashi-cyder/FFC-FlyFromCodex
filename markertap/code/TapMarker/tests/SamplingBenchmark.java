package jp.tapmarker;
import java.util.*;

/** Host timing only; baseline/new runs use identical held-out frames and scan schedules. */
public final class SamplingBenchmark {
 static long digest(Map<String,float[]> map){long result=0;for(String key:new TreeSet<>(map.keySet()))result=result*31+key.hashCode()*31L+Arrays.hashCode(map.get(key));return result;}
 public static void main(String[] args)throws Exception{
  Video6687ReplayTest.load();VirtualPresetProfileTest.Image image=Video6687ReplayTest.images.get("10");long[] timings=new long[4];long lastReads=0,lastDigest=0;
  for(int round=0;round<5;round++){
   Video6687ReplayTest.Run run=new Video6687ReplayTest.Run();run.e.enter(CoinPreset.P+"main",0);long[] reads={0};long result=0;long start=System.nanoTime();
   for(int frame=0;frame<6;frame++){
    TilePixels tile=new TilePixels(image.width,image.height,(x,y,w,h,p,stride)->{for(int dy=0;dy<h;dy++)for(int dx=0;dx<w;dx++)p[dy*stride+dx]=image.get(x+dx,y+dy);},64);
    RegionSampler.Pixels counted=(x,y)->{reads[0]++;return tile.get(x,y);};Map<String,float[]> samples=run.layout.sampleRelative(run.e.activeRegions(),image.width,image.height,counted,1000+frame*400);
    result=result*31+digest(samples);
   }
   long elapsed=System.nanoTime()-start;if(round>0)timings[round-1]=elapsed;lastReads=reads[0];lastDigest=result;
  }
  Arrays.sort(timings);System.out.println("{\"frames\":6,\"warmup_rounds\":1,\"measured_rounds\":4,\"median_ms\":"+((timings[1]+timings[2])/2000000d)+",\"pixel_requests\":"+lastReads+",\"evidence_digest\":"+lastDigest+"}");
 }
}
