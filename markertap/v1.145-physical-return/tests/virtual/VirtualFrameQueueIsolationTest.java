package jp.tapmarker;
public final class VirtualFrameQueueIsolationTest {
 static int checks;
 static void check(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
 public static void main(String[] args){
  VirtualFrameQueue q=new VirtualFrameQueue();
  check(q.offer(50,1,720,1280,0,1000),"first lane queued");
  check(q.offer(51,1,720,1280,0,1000),"second lane queued");
  VirtualFrameQueue.Request a=q.poll(1000);
  check(a!=null&&a.display==50,"first sent");
  check(q.poll(1200)==null,"global Android capture spacing kept");
  VirtualFrameQueue.Request b=q.poll(1400);
  check(b!=null&&b.display==51,"second sent while first callback stalled");
  check(q.current(a)&&q.current(b),"each lane independently in flight");
  check(!q.offer(50,1,720,1280,0,1500),"duplicate first lane suppressed");
  check(!q.offer(51,1,720,1280,0,1500),"duplicate second lane suppressed");
  check(q.accepts(b,51,1,0,1700),"correct display published");
  check(!q.accepts(b,50,1,0,1700),"cross-display publication denied");
  check(!q.accepts(b,51,2,0,1700),"cross-generation publication denied");
  check(q.complete(b,1700,true),"second completes first");
  check(q.current(a),"stalled first stays independent");
  check(!q.current(b),"completed request invalidated");
  check(q.expire(3501)==a,"only stalled first times out");
  check(!q.current(a)&&!q.hasWork(),"expired callback cannot publish");
  check(!q.complete(a,3600,true),"late completion cannot corrupt new frame");
  check(q.offer(51,1,720,1280,0,3800),"second lane can continue");
  VirtualFrameQueue.Request c=q.poll(3800);
  check(c!=null&&c.display==51,"unaffected lane restarted");
  q.remove(50,1);
  check(q.current(c),"removing first display preserves second");
  q.clear();check(!q.hasWork(),"stop cancels all pending work");
  check(q.offer(50,2,720,1280,0,5000),"fresh generation accepted");
  check(!q.offer(50,2,720,1280,0,5000),"duplicate queue entry refused");
  q.clear();
  check(q.offer(60,3,720,1280,0,8000),"first of three queued");
  check(q.offer(61,3,720,1280,0,8000),"second of three queued");
  check(q.offer(62,3,720,1280,0,8000),"third queued for capacity test");
  check(q.poll(8000)!=null,"slot 1 accepted");
  check(q.poll(8400)!=null,"slot 2 accepted");
  check(q.poll(8800)==null,"no third concurrent buffer");
  check(q.hasWork(),"third stays queued");
  System.out.println("VirtualFrameQueueIsolationTest: "+checks+" checks passed");
 }
}
