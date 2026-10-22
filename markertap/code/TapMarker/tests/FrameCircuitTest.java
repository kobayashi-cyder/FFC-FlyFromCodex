package jp.tapmarker;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public final class FrameCircuitTest {
 static int checks;
 static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 static FrameCircuit.Source source(int width,int height,int value,long at){return workspace->{Arrays.fill(workspace.array(width*height),0,width*height,value);return new FrameCircuit.Input(width,height,at);};}
 static FrameCircuit.Request request(FrameCircuit.Source source,java.util.function.BooleanSupplier valid,java.util.function.Function<FrameCircuit.Frame,Runnable> analyze,FrameCircuit.Dispatcher dispatch,java.util.function.Consumer<Throwable> failed){return new FrameCircuit.Request(valid,source,analyze,dispatch,failed);}
 static void normal(){
  FrameCircuit circuit=new FrameCircuit(()->1000);AtomicInteger results=new AtomicInteger();FrameCircuit.Frame[] leaked=new FrameCircuit.Frame[1];FrameSampleCache[] cache=new FrameSampleCache[1];List<Runnable> published=new ArrayList<>();
  circuit.run(request(source(40,60,0xff123456,1000),()->true,frame->{leaked[0]=frame;cache[0]=(FrameSampleCache)FrameSampleCache.wrap(40,60,frame);check(cache[0].get(0,0)==0xff123456&&frame.get(39,59)==0xff123456,"leased frame has exact copied pixels");RegionSampler.sample(40,60,new float[]{0,0,1,1},cache[0]);int value=frame.get(20,30);return ()->{check(value==0xff123456,"publication retains a result, not mutable pixels");results.incrementAndGet();};},published::add,failure->{throw new AssertionError(failure);}));
  check(results.get()==0&&published.size()==1,"result publication remains asynchronous");
  boolean revoked=false;try{leaked[0].get(0,0);}catch(IllegalStateException expected){revoked=true;}check(revoked,"pixel view is revoked before asynchronous publication");
  revoked=false;try{cache[0].get(0,0);}catch(IllegalStateException expected){revoked=true;}check(revoked,"fast cache direct reads cannot outlive the lease");
  revoked=false;try{RegionSampler.sample(40,60,new float[]{0,0,1,1},cache[0]);}catch(IllegalStateException expected){revoked=true;}check(revoked,"cached feature reads cannot outlive the lease");
  published.remove(0).run();check(results.get()==1,"valid publication arrives once");
  long retained=circuit.retainedBytes;
  for(int lane=0;lane<4;lane++){final int color=0xff000000|lane;circuit.run(request(source(40,60,color,1000),()->true,frame->{int value=frame.get(39,59);return ()->{check(value==color,"another lane cannot contaminate the copied result");};},Runnable::run,failure->{throw new AssertionError(failure);}));}
  check(circuit.retainedBytes==retained&&circuit.workspace.allocations==1,"four virtual lanes reuse one workspace after warmup");
  check(circuit.completed==5&&FrameCircuit.validTopology(),"new connection graph finishes all successful requests");
  check((circuit.lastTrace&(1<<FrameCircuit.CANCEL))==0&&(circuit.lastTrace&(1<<FrameCircuit.RELEASE))!=0,"success follows its release connection");
  circuit.trim();check(circuit.retainedBytes==0,"closing the session releases the shared workspace");
  circuit.run(request(source(40,60,2,1000),()->true,frame->{circuit.requestTrim();return ()->{};},Runnable::run,failure->{throw new AssertionError(failure);}));check(circuit.retainedBytes==0,"memory-pressure trim is honored at graph completion without another queued task");
 }
 static void cancellations(){
  FrameCircuit circuit=new FrameCircuit(()->1000);AtomicInteger version=new AtomicInteger(1),success=new AtomicInteger(),failed=new AtomicInteger();List<Runnable> ui=new ArrayList<>();
  circuit.run(request(source(20,30,1,1000),()->version.get()==1,frame->()->success.incrementAndGet(),ui::add,failure->failed.incrementAndGet()));
  version.incrementAndGet();ui.remove(0).run();check(success.get()==0,"stopped or replaced run cannot publish a queued old result");
  circuit.run(request(source(20,30,2,1000),()->version.get()==2,frame->{version.incrementAndGet();return ()->success.incrementAndGet();},ui::add,failure->failed.incrementAndGet()));
  check(ui.isEmpty()&&success.get()==0,"cancellation during recognition is checked before publication");
  long completed=circuit.completed;circuit.run(request(source(20,30,3,1000),()->false,frame->{throw new AssertionError("cancelled request analyzed");},ui::add,failure->failed.incrementAndGet()));
  check(circuit.completed==completed&&ui.isEmpty(),"cancelled queued request never touches another screen");
  circuit.run(request(source(20,30,4,1001),()->true,frame->{throw new AssertionError("future frame analyzed");},Runnable::run,failure->failed.incrementAndGet()));
  circuit.run(request(source(20,30,4,1),()->true,frame->()->success.incrementAndGet(),Runnable::run,failure->failed.incrementAndGet()));
  check(failed.get()==1&&success.get()==1,"future frame is rejected but a fresh timestamp is accepted");
  FrameCircuit stale=new FrameCircuit(()->3000);stale.run(request(source(20,30,4,1000),()->true,frame->{throw new AssertionError("stale frame analyzed");},Runnable::run,failure->failed.incrementAndGet()));
  check(failed.get()==2,"stale frame reports failure for bounded retry");
 }
 static void failures(){
  FrameCircuit circuit=new FrameCircuit(()->1000);AtomicInteger failed=new AtomicInteger(),passed=new AtomicInteger();FrameCircuit.Frame[] prior=new FrameCircuit.Frame[1];
  circuit.run(request(workspace->null,()->true,frame->{throw new AssertionError("missing frame analyzed");},Runnable::run,failure->failed.incrementAndGet()));
  circuit.run(request(source(20,30,1,1000),()->true,frame->{prior[0]=frame;throw new IllegalArgumentException("recognition failed");},Runnable::run,failure->failed.incrementAndGet()));
  boolean revoked=false;try{prior[0].get(0,0);}catch(IllegalStateException expected){revoked=true;}check(revoked,"failed analysis also revokes the pixel lease");
  circuit.run(request(source(20,30,1,1000),()->true,frame->{throw new OutOfMemoryError("simulated allocation rejection");},Runnable::run,failure->{check(failure instanceof OutOfMemoryError,"memory-pressure signal survives the graph");failed.incrementAndGet();}));
  circuit.run(request(source(20,30,8,1000),()->true,frame->{int value=frame.get(0,0);return ()->{if(value==8)passed.incrementAndGet();};},Runnable::run,failure->failed.incrementAndGet()));
  check(failed.get()==3&&passed.get()==1,"missing, failed and memory-pressure cases cannot poison a later window");
 }
 static void concurrent()throws Exception{
  FrameCircuit circuit=new FrameCircuit(()->1000);ExecutorService workers=Executors.newFixedThreadPool(2);AtomicInteger inFlight=new AtomicInteger(),max=new AtomicInteger(),done=new AtomicInteger();CountDownLatch start=new CountDownLatch(1);List<Future<?>> jobs=new ArrayList<>();
  for(int i=0;i<20;i++){final int color=i;jobs.add(workers.submit(()->{try{start.await();}catch(InterruptedException e){throw new RuntimeException(e);}circuit.run(request(source(64,96,color,1000),()->true,frame->{int concurrent=inFlight.incrementAndGet();max.accumulateAndGet(concurrent,Math::max);for(int p=0;p<6000;p++)if(frame.get(p%64,p/64)!=color)throw new AssertionError("cross-window pixel contamination");inFlight.decrementAndGet();return ()->done.incrementAndGet();},Runnable::run,failure->{throw new AssertionError(failure);}));}));}
  start.countDown();for(Future<?> job:jobs)job.get(10,TimeUnit.SECONDS);workers.shutdown();check(done.get()==20&&max.get()==1,"concurrent callers cannot hold two mutable workspace leases");check(circuit.workspace.allocations==1,"concurrent lanes retain one image allocation");
 }

 static void independentWindows()throws Exception{
  FrameCircuit first=new FrameCircuit(()->1000),second=new FrameCircuit(()->1000);
  ExecutorService pool=Executors.newFixedThreadPool(2);
  CountDownLatch bothEntered=new CountDownLatch(2),release=new CountDownLatch(1);
  AtomicInteger done=new AtomicInteger();
  List<Future<?>> jobs=new ArrayList<>();
  for(int i=0;i<2;i++){
   final int color=0xff123456+i;
   final FrameCircuit circuit=i==0?first:second;
   jobs.add(pool.submit(()->circuit.run(request(source(20,20,color,1000),()->true,frame->{
    check(frame.get(0,0)==color,"isolated window retains correct image");
    bothEntered.countDown();
    try{if(!release.await(3,TimeUnit.SECONDS))throw new IllegalStateException("parallel test timed out");}
    catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}
    return ()->done.incrementAndGet();
   },Runnable::run,failure->{throw new AssertionError(failure);}))));
  }
  try{check(bothEntered.await(2,TimeUnit.SECONDS),"two window circuits must analyze simultaneously, not serialize");}
  finally{release.countDown();}
  try{for(Future<?> job:jobs)job.get(3,TimeUnit.SECONDS);}
  finally{pool.shutdownNow();}
  check(done.get()==2&&first.completed==1&&second.completed==1,"both windows publish independently");
  check(first.workspace.allocations==1&&second.workspace.allocations==1,"each window has its own bounded workspace");
 }
 public static void main(String[] args)throws Exception{normal();cancellations();failures();concurrent();independentWindows();System.out.println("FrameCircuitTest: "+checks+" checks passed; new graph execution, result-only publication, cancellation, stale images, exceptions and concurrent leases; no Android execution");}
}
