package jp.tapmarker;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
public final class VirtualCopyExecutorFactoryTest {
 static int checks;
 static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
 public static void main(String[] args)throws Exception{
  AtomicInteger names=new AtomicInteger();
  ExecutorService pool=VirtualCopyExecutorFactory.create(r->new Thread(r,"copy-test-"+names.incrementAndGet()));
  CountDownLatch blockFirst=new CountDownLatch(1),enteredFirst=new CountDownLatch(1);
  Future<?> blocked=pool.submit(()->{enteredFirst.countDown();try{blockFirst.await(3,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}});
  check(enteredFirst.await(1,TimeUnit.SECONDS),"first screen entered copy");
  Future<Integer> peer=pool.submit(()->42);
  check(peer.get(1,TimeUnit.SECONDS)==42,"second screen completes while first copy blocked");
  check(!blocked.isDone(),"first screen remains independently blocked");
  CountDownLatch blockSecond=new CountDownLatch(1),enteredSecond=new CountDownLatch(1);
  Future<?> second=pool.submit(()->{enteredSecond.countDown();try{blockSecond.await(3,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}});
  check(enteredSecond.await(1,TimeUnit.SECONDS),"second copy worker available");
  Future<?> queued1=pool.submit(()->{});
  Future<?> queued2=pool.submit(()->{});
  check(!queued1.isDone()&&!queued2.isDone(),"bounded queue stores only two callbacks");
  boolean rejected=false;
  try{pool.submit(()->{});}catch(RejectedExecutionException expected){rejected=true;}
  check(rejected,"third queued callback rejected instead of growing memory");
  blockSecond.countDown();blockFirst.countDown();
  queued1.get(2,TimeUnit.SECONDS);queued2.get(2,TimeUnit.SECONDS);blocked.get(2,TimeUnit.SECONDS);second.get(2,TimeUnit.SECONDS);
  pool.shutdown();check(pool.awaitTermination(2,TimeUnit.SECONDS),"copy workers shut down");
  check(names.get()==2,"only two worker threads created");
  System.out.println("VirtualCopyExecutorFactoryTest: "+checks+" checks passed");
 }
}
