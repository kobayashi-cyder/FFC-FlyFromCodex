package jp.tapmarker;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
public final class FrameDeadlineTest {
 static int checks;
 static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 static FrameCircuit.Source source(AtomicInteger released){return workspace->new FrameCircuit.Input(64,64,1000,(x,y)->7,released::incrementAndGet);}
 static void analysisDeadline(){
  AtomicLong clock=new AtomicLong(1000);AtomicInteger reads=new AtomicInteger(),released=new AtomicInteger(),success=new AtomicInteger(),failed=new AtomicInteger();FrameCircuit c=new FrameCircuit(clock::get);
  c.run(new FrameCircuit.Request(()->true,workspace->new FrameCircuit.Input(64,64,1000,(x,y)->{if(reads.incrementAndGet()==2048)clock.set(2501);return 7;},released::incrementAndGet),frame->{RegionSampler.Pixels p=frame.samplingView();for(int i=0;i<200000;i++)p.get(i%64,i/64%64);return success::incrementAndGet;},Runnable::run,error->failed.incrementAndGet()));
  check(success.get()==0&&failed.get()==1,"expired pixel work must fail, never publish success");check(reads.get()<=3072,"expired sampling is bounded");check(released.get()==1,"retained image released exactly once");
 }
 static void computationDeadline(){
  AtomicLong clock=new AtomicLong(1000);AtomicInteger released=new AtomicInteger(),success=new AtomicInteger(),failed=new AtomicInteger();FrameCircuit c=new FrameCircuit(clock::get);
  c.run(new FrameCircuit.Request(()->true,source(released),frame->{clock.set(2501);return success::incrementAndGet;},Runnable::run,error->failed.incrementAndGet()));check(success.get()==0&&failed.get()==1,"post-computation freshness check");check(released.get()==1,"expired computation releases image");
 }
 static void deliveryDeadline(){
  for(long at:new long[]{2501,999}){AtomicLong clock=new AtomicLong(1000);AtomicInteger released=new AtomicInteger(),success=new AtomicInteger(),failed=new AtomicInteger();List<Runnable> ui=new ArrayList<>();FrameCircuit c=new FrameCircuit(clock::get);
   c.run(new FrameCircuit.Request(()->true,source(released),frame->success::incrementAndGet,ui::add,error->failed.incrementAndGet()));check(ui.size()==1&&released.get()==1,"queued result owns no image");clock.set(at);ui.remove(0).run();check(success.get()==0&&failed.get()==1,"expired delivery rejected");}
 }
 static void validBoundaryAndCancellation(){
  AtomicLong clock=new AtomicLong(1000);AtomicBoolean active=new AtomicBoolean(true);AtomicInteger released=new AtomicInteger(),success=new AtomicInteger(),failed=new AtomicInteger();List<Runnable> ui=new ArrayList<>();FrameCircuit c=new FrameCircuit(clock::get);
  c.run(new FrameCircuit.Request(active::get,source(released),frame->success::incrementAndGet,ui::add,error->failed.incrementAndGet()));clock.set(2500);ui.remove(0).run();check(success.get()==1&&failed.get()==0,"1500ms boundary unchanged");clock.set(1000);
  c.run(new FrameCircuit.Request(active::get,source(released),frame->success::incrementAndGet,ui::add,error->failed.incrementAndGet()));active.set(false);clock.set(5000);ui.remove(0).run();check(success.get()==1&&failed.get()==0&&released.get()==2,"superseded result suppressed");
 }
 static void nonblockingTrim()throws Exception{
  AtomicLong clock=new AtomicLong(1000);FrameCircuit first=new FrameCircuit(clock::get),second=new FrameCircuit(clock::get);ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);AtomicInteger done=new AtomicInteger();
  try{Future<?> stuck=pool.submit(()->first.run(new FrameCircuit.Request(()->true,workspace->{workspace.array(4096);return new FrameCircuit.Input(64,64,1000);},frame->{entered.countDown();try{if(!release.await(3,TimeUnit.SECONDS))throw new AssertionError("trim blocked");}catch(InterruptedException e){throw new RuntimeException(e);}return ()->{};},Runnable::run,error->{throw new AssertionError(error);} )));
   check(entered.await(2,TimeUnit.SECONDS),"first display owns lease");pool.submit(()->{first.requestTrim();second.requestTrim();}).get(1,TimeUnit.SECONDS);
   pool.submit(()->second.run(new FrameCircuit.Request(()->true,workspace->new FrameCircuit.Input(1,1,1000,(x,y)->1),frame->done::incrementAndGet,Runnable::run,error->{throw new AssertionError(error);}))).get(1,TimeUnit.SECONDS);check(done.get()==1,"healthy lane survives memory trim");release.countDown();stuck.get(1,TimeUnit.SECONDS);check(first.retainedBytes==0,"busy workspace trimmed after lease");
   first.run(new FrameCircuit.Request(()->true,workspace->{workspace.array(4096);return new FrameCircuit.Input(64,64,1000);},frame->()->{},Runnable::run,error->{throw new AssertionError(error);}));check(first.retainedBytes>0,"fixture retains idle workspace");first.requestTrim();check(first.retainedBytes==0,"idle workspace trimmed immediately");
  }finally{release.countDown();pool.shutdownNow();}
 }
 public static void main(String[] args)throws Exception{analysisDeadline();computationDeadline();deliveryDeadline();validBoundaryAndCancellation();nonblockingTrim();System.out.println("FrameDeadlineTest: "+checks+" passed");}
}
