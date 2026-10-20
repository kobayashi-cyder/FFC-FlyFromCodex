package jp.tapmarker;
import java.util.concurrent.*;
public class DisplayLaneTest {
 static int checks;
 static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 public static void main(String[] args)throws Exception{
  DisplayLane first=new DisplayLane(),second=new DisplayLane();
  ExecutorService workers=Executors.newFixedThreadPool(2);
  CountDownLatch started=new CountDownLatch(1),release=new CountDownLatch(1);
  try{
   Future<Long> blocked=workers.submit(()->{long token=first.enter();started.countDown();if(!release.await(5,TimeUnit.SECONDS))throw new AssertionError("test timeout");first.complete(token);return token;});
   check(started.await(5,TimeUnit.SECONDS),"first display begins");
   check(first.enter()<0,"one display cannot overlap its own operations");
   Future<Boolean> other=workers.submit(()->{long token=second.enter();boolean accepted=token>=0&&second.valid(token);second.complete(token);return accepted;});
   check(other.get(5,TimeUnit.SECONDS),"second display completes while first display is blocked");
   check(!blocked.isDone(),"no global serialization of input lanes");
   release.countDown();long old=blocked.get(5,TimeUnit.SECONDS);long next=first.enter();
   first.complete(old);check(first.valid(next),"late duplicate completion must not unlock the following tap");
   first.cancel();check(!first.valid(next),"stop invalidates queued input");
   long restarted=first.enter();first.complete(next);check(first.valid(restarted),"stale completion after stop cannot unlock a restarted operation");
   first.complete(restarted);check(first.enter()>=0,"current completion frees only its own lane");
  }finally{release.countDown();workers.shutdownNow();}
  System.out.println("DisplayLaneTest: "+checks+" checks passed; Android input dispatch not tested");
 }
}
