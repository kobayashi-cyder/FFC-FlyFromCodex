package jp.tapmarker;

import java.util.function.*;
import java.util.concurrent.locks.ReentrantLock;

/** A new, hand-wired processing graph. No biological/BANC topology is imported.
 * Its single workspace is leased only while ANALYZE runs; publications contain
 * results and metadata, never a live pixel view. The Android adapter supplies IO.
 */
final class FrameCircuit {
 static final int VALIDATE=0,COPY=1,FRESH=2,ANALYZE=3,RECHECK=4,PUBLISH=5,CANCEL=6,RELEASE=7,END=-1;
 static final String[] NAMES={"validate-request","copy-current-frame","check-freshness","analyze","recheck-request","publish-results","cancel","release-view"};
 // Sparse outgoing connections indexed by the current node and its signal.
 static final int[] YES={COPY,FRESH,ANALYZE,RECHECK,PUBLISH,RELEASE,RELEASE,END};
 static final int[] NO ={RELEASE,CANCEL,CANCEL,CANCEL,RELEASE,CANCEL,RELEASE,END};
 interface Source {Input copy(Workspace workspace);}
 interface Clock {long now();}
 interface Dispatcher {void post(Runnable task);}
 static final class Input {
  final int width,height;final long capturedAt;final RegionSampler.Pixels source;private Runnable release;
  Input(int width,int height,long at){this(width,height,at,null,null);}
  Input(int width,int height,long at,RegionSampler.Pixels source){this(width,height,at,source,null);}
  Input(int width,int height,long at,RegionSampler.Pixels source,Runnable release){this.width=width;this.height=height;capturedAt=at;this.source=source;this.release=release;}
  void close(){Runnable done=release;release=null;if(done!=null)done.run();}
 }
 static final class Workspace {
  private int[] pixels;long allocations,allocatedBytes;
  int[] array(int length){
   if(length<=0)throw new IllegalArgumentException("empty frame");
   if(pixels==null||pixels.length<length){pixels=new int[length];allocations++;allocatedBytes+=(long)length*4;}
   return pixels;
  }
  long retainedBytes(){return pixels==null?0:(long)pixels.length*4;}
  void clear(){pixels=null;}
 }
 static final class Frame implements RegionSampler.Pixels {
  final int width,height;final long capturedAt;private RegionSampler.Pixels pixels;private BooleanSupplier valid;private Clock clock;private int reads;
  Frame(Input input,int[] array){width=input.width;height=input.height;capturedAt=input.capturedAt;pixels=input.source==null?new FramePixels.Raster(width,height,array,capturedAt):input.source;}
  public int get(int x,int y){checkLease();return pixels.get(x,y);}
  void checkLease(){if(pixels==null)throw new IllegalStateException("pixel lease has ended");if(Thread.currentThread().isInterrupted()||valid!=null&&!valid.getAsBoolean())throw new java.util.concurrent.CancellationException("analysis superseded");if(clock!=null&&!AnalysisBudget.fresh(capturedAt,clock.now()))throw new java.util.concurrent.CancellationException("frame freshness deadline exceeded");}
  RegionSampler.Pixels samplingView(){checkLease();return (x,y)->{RegionSampler.Pixels source=pixels;if(source==null)throw new IllegalStateException("pixel lease has ended");if((++reads&1023)==0)checkLease();return source.get(x,y);};}
  void close(){pixels=null;valid=null;clock=null;}
 }
 static final class Request {
  final BooleanSupplier valid;final Source source;final Function<Frame,Runnable> analyze;final Dispatcher dispatcher;final Consumer<Throwable> failed;
  Request(BooleanSupplier valid,Source source,Function<Frame,Runnable> analyze,Dispatcher dispatcher,Consumer<Throwable> failed){this.valid=valid;this.source=source;this.analyze=analyze;this.dispatcher=dispatcher;this.failed=failed;}
 }
 final Workspace workspace=new Workspace();final Clock clock;private final ReentrantLock lifecycle=new ReentrantLock();
 volatile long completed,cancelled,failures,retainedBytes,analyzeNanos;volatile int lastTrace;volatile boolean trimRequested;
 FrameCircuit(Clock clock){if(clock==null)throw new IllegalArgumentException("clock");this.clock=clock;}
 /** One worker owns the lease. Memory pressure must never wait for that worker. */
 void run(Request request){
  lifecycle.lock();
  try{
  Input input=null;Frame frame=null;Runnable publication=null;Throwable failure=null;int node=VALIDATE,steps=0,trace=0;
  try{
   while(node!=END){
    if(++steps>16)throw new IllegalStateException("processing graph did not terminate");
    trace|=1<<node;boolean signal=true;
    try{
     switch(node){
      case VALIDATE:signal=request.valid.getAsBoolean()&&!Thread.currentThread().isInterrupted();break;
      case COPY:
       input=request.source.copy(workspace);signal=input!=null;
       if(signal){long size=(long)input.width*input.height;signal=input.width>0&&input.height>0&&size<=Integer.MAX_VALUE&&(input.source!=null||workspace.pixels!=null&&workspace.pixels.length>=size);if(!signal)throw new IllegalArgumentException("incomplete frame");}
       break;
      case FRESH:{signal=AnalysisBudget.fresh(input.capturedAt,clock.now());if(!signal)failure=new IllegalStateException("stale input frame");break;}
      case ANALYZE:
       frame=new Frame(input,workspace.pixels);frame.valid=request.valid;frame.clock=clock;long started=System.nanoTime();
       try{publication=request.analyze.apply(frame);}finally{analyzeNanos+=System.nanoTime()-started;frame.close();}
       signal=publication!=null;break;
      case RECHECK:signal=request.valid.getAsBoolean()&&!Thread.currentThread().isInterrupted();if(signal&&!AnalysisBudget.fresh(input.capturedAt,clock.now())){failure=new IllegalStateException("analysis exceeded frame freshness deadline");node=CANCEL;continue;}break;
      case PUBLISH:{final Runnable result=publication;final long capturedAt=input.capturedAt;request.dispatcher.post(()->{if(!request.valid.getAsBoolean())return;if(!AnalysisBudget.fresh(capturedAt,clock.now())){request.failed.accept(new IllegalStateException("result expired before delivery"));return;}result.run();});completed++;break;}
      case CANCEL:{cancelled++;final Throwable reason=failure;request.dispatcher.post(()->{if(request.valid.getAsBoolean())request.failed.accept(reason);});break;}
      case RELEASE:if(frame!=null)frame.close();break;
      default:throw new IllegalStateException("unknown processing node");
     }
    }catch(RuntimeException|OutOfMemoryError error){failure=error;failures++;signal=false;if(node==CANCEL||node==RELEASE)break;}
    node=signal?YES[node]:NO[node];
   }
  }finally{if(frame!=null)frame.close();try{if(input!=null)input.close();}finally{if(trimRequested){workspace.clear();trimRequested=false;}lastTrace=trace;retainedBytes=workspace.retainedBytes();}}
  }finally{lifecycle.unlock();}
 }
 void requestTrim(){
  trimRequested=true;
  // A reentrant call during ANALYZE only marks the workspace for retirement.
  if(lifecycle.isHeldByCurrentThread()||!lifecycle.tryLock())return;
  try{workspace.clear();retainedBytes=0;trimRequested=false;}finally{lifecycle.unlock();}
 }
 void trim(){requestTrim();}
 static boolean validTopology(){
  if(YES.length!=NAMES.length||NO.length!=NAMES.length)return false;
  for(int[] edges:new int[][]{YES,NO})for(int target:edges)if(target<END||target>=NAMES.length)return false;
  return true;
 }
}
