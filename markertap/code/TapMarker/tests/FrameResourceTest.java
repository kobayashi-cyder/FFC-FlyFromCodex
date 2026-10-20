package jp.tapmarker;
import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.*;
public final class FrameResourceTest {
 static int checks;static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 static void replacement(){
  AtomicInteger imagesClosed=new AtomicInteger(),readersClosed=new AtomicInteger();FrameResource reader=new FrameResource(readersClosed::incrementAndGet);
  reader.retain();FrameResource first=new FrameResource(()->{imagesClosed.incrementAndGet();reader.release();});
  first.retain(); // synchronous analysis has borrowed the prior latest frame
  reader.retain();FrameResource next=new FrameResource(()->{imagesClosed.incrementAndGet();reader.release();});
  first.release(); // latest swaps immediately; analysis still sees the immutable first image
  first.check();check(imagesClosed.get()==0,"capture may replace latest image while prior analysis remains alive");
  reader.release();next.release(); // stop/rebuild retires reader and the new latest image
  first.check();check(imagesClosed.get()==1&&readersClosed.get()==0,"stopped reader stays alive until prior analysis ends");
  first.release();check(imagesClosed.get()==2&&readersClosed.get()==1,"last old-frame lease closes its image and retired reader exactly once");
  boolean refused=false;try{first.retain();}catch(IllegalStateException expected){refused=true;}check(refused,"closed frame cannot be borrowed again");
  refused=false;try{reader.check();}catch(IllegalStateException expected){refused=true;}check(refused,"retired and drained reader cannot serve pixels");
 }
 static void graph(){
  for(int scenario=0;scenario<6;scenario++){
   final int mode=scenario;AtomicInteger disposed=new AtomicInteger(),releases=new AtomicInteger(),success=new AtomicInteger(),failures=new AtomicInteger();AtomicBoolean current=new AtomicBoolean(true);
   FrameResource image=new FrameResource(disposed::incrementAndGet);FrameCircuit circuit=new FrameCircuit(()->mode==0?3000:1000);List<Runnable> ui=new ArrayList<>();
   circuit.run(new FrameCircuit.Request(current::get,workspace->{image.retain();return new FrameCircuit.Input(1080,2424,mode==0?1:1000,(x,y)->{image.check();return 0xff123456;},()->{releases.incrementAndGet();image.release();});},frame->{
    check(frame.get(20,20)==0xff123456,"borrowed image remains readable during recognition");
    if(mode==1)throw new IllegalArgumentException("recognizer failed");
    if(mode==2)throw new OutOfMemoryError("simulated memory pressure");
    if(mode==3)current.set(false);
    if(mode==4)return null;
    return success::incrementAndGet;
   },ui::add,error->failures.incrementAndGet()));
   image.release();check(disposed.get()==1&&releases.get()==1,"success, cancellation, null output and errors all release their exact source lease");
   for(Runnable task:ui)task.run();
   check((mode==5)==(success.get()==1),"only fresh valid recognition publishes");
  }
  AtomicInteger releases=new AtomicInteger();FrameCircuit.Input input=new FrameCircuit.Input(1,1,1000,(x,y)->0,releases::incrementAndGet);input.close();input.close();check(releases.get()==1,"graph release is idempotent");
  AtomicInteger touched=new AtomicInteger();FrameCircuit stopped=new FrameCircuit(()->1000);stopped.run(new FrameCircuit.Request(()->false,workspace->{touched.incrementAndGet();return null;},frame->{throw new AssertionError();},Runnable::run,error->{throw new AssertionError();}));check(touched.get()==0,"already cancelled queued request never acquires an image");
 }
 static void concurrent()throws Exception{
  for(int trial=0;trial<50;trial++){
   AtomicInteger disposed=new AtomicInteger();FrameResource resource=new FrameResource(disposed::incrementAndGet);resource.retain();CountDownLatch start=new CountDownLatch(1);
   Thread thread=new Thread(()->{try{start.await();}catch(InterruptedException e){throw new RuntimeException(e);}resource.release();});
   thread.start();start.countDown();resource.release();thread.join(3000);check(!thread.isAlive()&&disposed.get()==1&&resource.references()==0,"UI stop and analysis completion race disposes exactly once");
  }
 }
 public static void main(String[] args)throws Exception{replacement();graph();concurrent();System.out.println("FrameResourceTest: "+checks+" checks passed; image replacement, deferred reader retirement, native source release on all graph exits and concurrent stop; Android buffers not exercised");}
}
