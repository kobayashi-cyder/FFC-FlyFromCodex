package jp.tapmarker;
import java.util.concurrent.*;

/** At most two screenshot-copy workers and two queued callbacks.
 * Unlike a single-thread executor, one blocked hardware-buffer copy cannot
 * indefinitely prevent the other virtual screen from publishing a frame.
 * Rejection is handled by VirtualWindows (buffer closed, request failed).
 * This never executes a screenshot copy on the Android main thread.
 */
final class VirtualCopyExecutorFactory {
 static ExecutorService create(ThreadFactory factory){
  if(factory==null)throw new IllegalArgumentException("thread factory required");
  return new ThreadPoolExecutor(2,2,0L,TimeUnit.MILLISECONDS,
    new ArrayBlockingQueue<Runnable>(2),factory,new ThreadPoolExecutor.AbortPolicy());
 }
 private VirtualCopyExecutorFactory(){}
}
