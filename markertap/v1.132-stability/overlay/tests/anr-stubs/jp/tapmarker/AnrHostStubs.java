package jp.tapmarker;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

final class FrameRetirement {static boolean permitted=true;static boolean permitsNewFrame(){return permitted;}}
final class DiagnosticJournal {
 volatile boolean closed;long dropped;
 final Map<String,Lane> lanes=new HashMap<>();
 final Semaphore rawCopies=new Semaphore(4);
 final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<Runnable>(2),r->{Thread t=new Thread(r);t.setDaemon(true);return t;});
 final AtomicInteger encoded=new AtomicInteger();volatile int[] encodedPixels;
 static final class Selection {}
 static final class Lane {
  final int display,run;final DiagnosticCapturePolicy policy=new DiagnosticCapturePolicy();
  Selection selection=new Selection();String observation="observed";long action;
  Lane(int display,int run){this.display=display;this.run=run;}
 }
 synchronized Lane lane(String app,int display,int run){Lane a=lanes.get(app);if(a==null||a.display!=display||a.run!=run){a=new Lane(display,run);lanes.put(app,a);}return a;}
 synchronized void manual(){lane("farm",19,7).selection=new Selection();}
}
final class DiagnosticJournal$$ExternalSyntheticLambda0 implements Runnable {
 final DiagnosticJournal journal;final int[] pixels;
 DiagnosticJournal$$ExternalSyntheticLambda0(DiagnosticJournal journal,int[] size,int[] pixels,
  DiagnosticJournal.Selection selection,int width,int height,String app,int display,int run,
  long capturedAt,String kind,String observation,DiagnosticJournal.Lane lane,long action){this.journal=journal;this.pixels=pixels;}
 public void run(){try{journal.encodedPixels=pixels;journal.encoded.incrementAndGet();}finally{journal.rawCopies.release();}}
}
