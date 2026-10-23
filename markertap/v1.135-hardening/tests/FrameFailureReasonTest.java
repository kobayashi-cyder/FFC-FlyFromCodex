package jp.tapmarker;
import java.util.concurrent.atomic.AtomicInteger;
public final class FrameFailureReasonTest {
 public static void main(String[] args){
  FrameCircuit circuit=new FrameCircuit(()->1000);
  AtomicInteger failed=new AtomicInteger(),success=new AtomicInteger();
  circuit.run(new FrameCircuit.Request(()->true,space->null,frame->()->success.incrementAndGet(),Runnable::run,error->{
   if(error==null||!String.valueOf(error.getMessage()).contains("no frame"))
    throw new AssertionError("null capture must have classified failure");
   failed.incrementAndGet();
  }));
  if(failed.get()!=1||success.get()!=0)throw new AssertionError("missing image published success");
  System.out.println("FrameFailureReasonTest: passed");
 }
}
