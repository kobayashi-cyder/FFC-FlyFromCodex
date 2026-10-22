package jp.tapmarker;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class OwnerDeathCleanupTest {
 static int checks;
 static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
 static final class Fake implements OwnerDeathCleanup.Host {
  Runnable deadline;long delay;boolean fail,block;
  final AtomicInteger stopped=new AtomicInteger();
  final CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
  public void scheduleHardStop(Runnable stop,long millis){deadline=stop;delay=millis;}
  public void cleanup(){
   check(deadline!=null,"deadline armed before native cleanup");
   entered.countDown();
   if(fail)throw new IllegalStateException("native release failed");
   if(block)try{release.await(2,TimeUnit.SECONDS);}catch(InterruptedException ex){Thread.currentThread().interrupt();}
  }
  public void terminate(){stopped.incrementAndGet();}
 }
 public static void main(String[] args)throws Exception {
  Fake normal=new Fake();OwnerDeathCleanup.run(normal);
  check(normal.stopped.get()==1,"completed cleanup stops the backend");
  check(normal.delay==5000,"hard-stop deadline is five seconds");
  Fake failure=new Fake();failure.fail=true;
  try{OwnerDeathCleanup.run(failure);throw new AssertionError("failure missing");}catch(IllegalStateException expected){}
  check(failure.stopped.get()==1,"cleanup exception still stops the backend");
  Fake blocked=new Fake();blocked.block=true;
  Thread worker=new Thread(()->OwnerDeathCleanup.run(blocked));worker.start();
  check(blocked.entered.await(1,TimeUnit.SECONDS),"native cleanup is blocked");
  blocked.deadline.run();
  check(blocked.stopped.get()==1&&worker.isAlive(),"independent deadline can stop a blocked backend");
  blocked.release.countDown();worker.join(1000);
  check(!worker.isAlive(),"test cleanup worker ends");
  System.out.println("OwnerDeathCleanupTest: "+checks+" checks passed");
 }
}
