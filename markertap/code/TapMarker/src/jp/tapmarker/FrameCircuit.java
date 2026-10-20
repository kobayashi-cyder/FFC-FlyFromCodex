package jp.tapmarker;

import java.util.function.*;

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
  final int width,height;final long capturedAt;
  Input(int width,int height,long at){this.width=width;this.height=height;capturedAt=at;}
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
  // Access is confined to the synchronous ANALYZE callback. The publication
  // crosses threads only after close(), via the adapter's dispatcher.
  final int width,height;final long capturedAt;private int[] pixels;
  Frame(Input input,int[] pixels){width=input.width;height=input.height;capturedAt=input.capturedAt;this.pixels=pixels;}
  public int get(int x,int y){int[] current=pixels;if(current==null)throw new IllegalStateException("pixel lease has ended");return current[y*width+x];}
  void checkLease(){if(pixels==null)throw new IllegalStateException("pixel lease has ended");}
  // FrameSampleCache rechecks the lease at every cache entry, then samples this
  // final-array view synchronously. No repeated lease check for every subpixel.
  RegionSampler.Pixels samplingView(){checkLease();return new FramePixels.Raster(width,height,pixels,capturedAt);}
  void close(){pixels=null;}
 }
 static final class Request {
  final BooleanSupplier valid;final Source source;final Function<Frame,Runnable> analyze;final Dispatcher dispatcher;final Consumer<Throwable> failed;
  Request(BooleanSupplier valid,Source source,Function<Frame,Runnable> analyze,Dispatcher dispatcher,Consumer<Throwable> failed){this.valid=valid;this.source=source;this.analyze=analyze;this.dispatcher=dispatcher;this.failed=failed;}
 }
 final Workspace workspace=new Workspace();final Clock clock;
 volatile long completed,cancelled,failures,retainedBytes,analyzeNanos;volatile int lastTrace;volatile boolean trimRequested;
 FrameCircuit(Clock clock){if(clock==null)throw new IllegalArgumentException("clock");this.clock=clock;}
 /** One worker invokes the graph. Synchronization also enforces the lease if a caller errs. */
 synchronized void run(Request request){
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
       if(signal){long size=(long)input.width*input.height;signal=input.width>0&&input.height>0&&size<=Integer.MAX_VALUE&&workspace.pixels!=null&&workspace.pixels.length>=size;if(!signal)throw new IllegalArgumentException("incomplete frame");}
       break;
      case FRESH:{long now=clock.now();signal=input.capturedAt>0&&now>=input.capturedAt&&now-input.capturedAt<=1500;if(!signal)failure=new IllegalStateException("stale input frame");break;}
      case ANALYZE:
       frame=new Frame(input,workspace.pixels);long started=System.nanoTime();
       try{publication=request.analyze.apply(frame);}finally{analyzeNanos+=System.nanoTime()-started;frame.close();}
       signal=publication!=null;break;
      case RECHECK:signal=request.valid.getAsBoolean()&&!Thread.currentThread().isInterrupted();break;
      case PUBLISH:{final Runnable result=publication;request.dispatcher.post(()->{if(request.valid.getAsBoolean())result.run();});completed++;break;}
      case CANCEL:{cancelled++;final Throwable reason=failure;request.dispatcher.post(()->{if(request.valid.getAsBoolean())request.failed.accept(reason);});break;}
      case RELEASE:if(frame!=null)frame.close();break;
      default:throw new IllegalStateException("unknown processing node");
     }
    }catch(RuntimeException|OutOfMemoryError error){failure=error;failures++;signal=false;if(node==CANCEL||node==RELEASE)break;}
    node=signal?YES[node]:NO[node];
   }
  }finally{if(frame!=null)frame.close();if(trimRequested){workspace.clear();trimRequested=false;}lastTrace=trace;retainedBytes=workspace.retainedBytes();}
 }
 void requestTrim(){trimRequested=true;}
 synchronized void trim(){workspace.clear();retainedBytes=0;trimRequested=false;}
 static boolean validTopology(){
  if(YES.length!=NAMES.length||NO.length!=NAMES.length)return false;
  for(int[] edges:new int[][]{YES,NO})for(int target:edges)if(target<END||target>=NAMES.length)return false;
  return true;
 }
}
