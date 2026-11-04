package jp.tapmarker;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * Non-blocking, single-outstanding accessibility probe.
 * UI/screenshot analysis threads never wait on an accessibility Binder call.
 * A timed-out task is cancelled and its result cannot authorize an action.
 */
final class BoundedProbeCache<T> {
  static final class Result<T> {
    final T value;
    final long completedAt;
    Result(T value,long completedAt){this.value=value;this.completedAt=completedAt;}
  }

  private final LongSupplier clock;
  private final long ttlMs,timeoutMs;
  private final ExecutorService worker=Executors.newSingleThreadExecutor(new ThreadFactory(){
    public Thread newThread(Runnable r){
      Thread t=new Thread(r,"MarkerTap-kaitaku-ui-probe");
      t.setDaemon(true);return t;
    }
  });
  private String currentKey="";
  private Future<T> current;
  private long startedAt=-1;
  private Result<T> ready;

  BoundedProbeCache(LongSupplier clock,long ttlMs,long timeoutMs){
    if(clock==null||ttlMs<=0||timeoutMs<=0)
      throw new IllegalArgumentException("invalid bounded probe cache");
    this.clock=clock;this.ttlMs=ttlMs;this.timeoutMs=timeoutMs;
  }

  synchronized Result<T> get(String key,int token,int display,int width,int height,
                              Callable<T> probe){
    if(key==null||probe==null||width<=0||height<=0)return null;
    long now=clock.getAsLong();
    String scope=token+":"+display+":"+width+":"+height+":"+key;
    if(!scope.equals(currentKey)){
      cancel();
      currentKey=scope;
    }
    if(ready!=null){
      if(now>=ready.completedAt&&now-ready.completedAt<=ttlMs)return ready;
      ready=null;
    }
    if(current!=null){
      if(now<startedAt||now-startedAt>timeoutMs){
        cancel();return null;
      }
      if(!current.isDone())return null;
      Future<T> future=current;current=null;
      try {
        T value=future.get(0,TimeUnit.MILLISECONDS);
        if(value!=null)ready=new Result<>(value,now);
      }catch(Exception denied){ready=null;}
      return ready;
    }
    startedAt=now;
    try{current=worker.submit(probe);}
    catch(RuntimeException unavailable){current=null;}
    return null;
  }

  synchronized void clear(){
    cancel();currentKey="";
  }
  private void cancel(){
    if(current!=null){current.cancel(true);current=null;}
    ready=null;startedAt=-1;
  }
}
