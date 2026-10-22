package jp.tapmarker;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

public final class DiagnosticCaptureAnrTest {
 static int checks;
 static void check(boolean value,String reason){checks++;if(!value)throw new AssertionError(reason);}
 static void capture(DiagnosticJournal j,RegionSampler.Pixels p){DiagnosticFrameCapture.frame(j,"farm",19,7,4,4,p,1234,"before");}
 static void blockedCapture(int mutation)throws Exception {
  DiagnosticJournal j=new DiagnosticJournal();CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
  AtomicReference<Throwable> failure=new AtomicReference<>();
  Thread reader=new Thread(()->{try{capture(j,(x,y)->{
   check(!Thread.holdsLock(j),"pixel read under journal monitor");
   entered.countDown();try{release.await();}catch(InterruptedException e){throw new IllegalStateException(e);}return x+y;
  });}catch(Throwable e){failure.set(e);}});
  reader.start();ExecutorService control=Executors.newSingleThreadExecutor();
  try {
   check(entered.await(2,TimeUnit.SECONDS),"readback did not start");
   Future<?> manual=control.submit(()->{synchronized(j){
    if(mutation==0)j.manual();
    if(mutation==1)j.lane("farm",20,8);
    if(mutation==2)j.closed=true;
    if(mutation==3)j.lane("farm",19,7).action++;
   }});
   manual.get(750,TimeUnit.MILLISECONDS);check(true,"touch remains responsive while native readback is blocked");
  } finally {release.countDown();reader.join(2000);control.shutdownNow();j.worker.shutdown();j.worker.awaitTermination(2,TimeUnit.SECONDS);}
  if(failure.get()!=null)throw new AssertionError(failure.get());
  check(!reader.isAlive(),"capture did not return");check(j.encoded.get()==0,"stale metadata published");
  check(j.rawCopies.availablePermits()==4,"stale capture leaked permit");
 }
 public static void main(String[] args)throws Exception {
  for(int i=0;i<4;i++)blockedCapture(i);
  for(int[] geometry:new int[][]{{37,83,19,42},{1080,2424,540,1212},{7,3,7,3}}) {
   int w=geometry[0],h=geometry[1],tw=geometry[2],th=geometry[3];int[] pixels=new int[w*h];
   for(int i=0;i<pixels.length;i++)pixels[i]=i*1297;
   check(java.util.Arrays.equals(FramePixels.thumbnail(pixels,w,h,tw,th),DiagnosticFrameCapture.thumbnail((x,y)->pixels[y*w+x],w,h,tw,th)),"thumbnail differs from existing center sampling");
  }
  DiagnosticJournal j=new DiagnosticJournal();
  capture(j,(x,y)->{check(!Thread.holdsLock(j),"thumbnail lock");return 0xff000000|y*4+x;});
  j.worker.shutdown();check(j.worker.awaitTermination(2,TimeUnit.SECONDS),"encoder stuck");
  check(j.encoded.get()==1&&j.encodedPixels[15]==0xff00000f,"successful image changed");
  check(j.rawCopies.availablePermits()==4,"encoder permit not restored");
  capture(j,(x,y)->1);check(j.rawCopies.availablePermits()==4&&j.dropped==1,"rejected submission leaked permit");
  capture(j,(x,y)->{throw new IllegalStateException("read failure");});
  capture(j,(x,y)->{throw new OutOfMemoryError("memory pressure");});
  check(j.rawCopies.availablePermits()==4&&j.dropped==3,"failure leaked permit");
  j.rawCopies.acquire(4);capture(j,(x,y)->{throw new AssertionError("capture admitted over limit");});
  check(j.dropped==4&&j.rawCopies.availablePermits()==0,"capture capacity ignored");j.rawCopies.release(4);
  j.closed=true;capture(j,(x,y)->{throw new AssertionError("closed capture read pixels");});
  check(j.rawCopies.availablePermits()==4,"closed capture leaked permit");
  System.out.println("DiagnosticCaptureAnrTest: "+checks+" checks passed, blocked readback cannot block touch");
 }
}
