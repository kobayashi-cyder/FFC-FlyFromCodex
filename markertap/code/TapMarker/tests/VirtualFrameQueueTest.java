package jp.tapmarker;
public class VirtualFrameQueueTest {
 static int checks;
 static void check(boolean value,String text){checks++;if(!value)throw new AssertionError(text);}
 public static void main(String[] args){
  VirtualFrameQueue queue=new VirtualFrameQueue();long now=200000;
  check(queue.offer(162,7,720,1280,now-175453,now),"175453ms-old shopping image requests a real capture instead of waiting forever");
  check(!queue.offer(162,7,720,1280,now-175453,now),"one pending request per display");
  check(!queue.offer(161,7,720,1280,now-94,now),"fresh farm frame stays on normal ImageReader path");
  boolean denied=false;try{queue.offer(0,7,720,1280,0,now);}catch(IllegalArgumentException expected){denied=true;}check(denied,"physical display cannot be a fallback target");
  VirtualFrameQueue.Request first=queue.poll(now);check(first.display==162&&first.generation==7&&first.startedAt==now,"actual positive display and generation retained");
  check(queue.accepts(first,162,7,now-175453,now+100),"actual callback may replace stale pixels");
  check(!queue.accepts(first,0,7,0,now+100)&&!queue.accepts(first,161,7,0,now+100)&&!queue.accepts(first,162,8,0,now+100),"foreign, physical and replacement displays cannot receive pixels");
  check(!queue.accepts(first,162,7,now+50,now+100),"late screenshot cannot overwrite a newer ImageReader frame");
  check(!queue.accepts(first,162,7,0,now+1501),"slow callback cannot be stamped as fresh");
  queue.offer(161,7,720,1280,1,now);check(queue.poll(now+200)==null,"captures cannot overlap");
  check(queue.complete(first,now+100,true),"successful callback releases the request");
  check(!queue.accepts(first,162,7,0,now+101)&&!queue.complete(first,now+101,true),"duplicate callback cannot mutate or complete another request");
  check(queue.poll(now+399)==null,"global API request interval is enforced");
  check(!VirtualFrameQueue.stale(now+350,now+400),"native recovery during a queued capture prevents a needless app restore");
  VirtualFrameQueue.Request second=queue.poll(now+400);check(second.display==161,"next display gets the capture slot fairly");
  check(queue.expire(now+2899)==null&&queue.expire(now+2900)==second,"missing callback has a bounded timeout");
  check(!queue.current(second)&&!queue.accepts(second,161,7,0,now+2901),"timed-out result cannot refresh old pixels");
  check(!queue.offer(161,7,720,1280,0,now+3000),"failed capture backs off instead of flooding the API");
  check(queue.offer(161,7,720,1280,0,now+4400),"failed display can retry after its cooldown");
  queue.remove(161,7);check(!queue.hasWork(),"closing one display removes its queued capture");
  for(int id=159;id<=162;id++)queue.offer(id,8,720,1280,1,now+5000);
  for(int i=0;i<4;i++){VirtualFrameQueue.Request request=queue.poll(now+5000+i*400);check(request!=null&&request.display==159+i,"all four static displays get independent captures");queue.complete(request,now+5100+i*400,true);}
  queue.offer(162,8,720,1280,1,now+8000);VirtualFrameQueue.Request old=queue.poll(now+8000);queue.clear();
  check(!queue.hasWork()&&!queue.accepts(old,162,8,0,now+8100),"stop discards pending requests and late callbacks");
  queue.offer(162,9,720,1280,1,now+8100);check(queue.poll(now+8100)==null,"stop and restart retain the shared API rate limit");
  VirtualFrameQueue.Request fresh=queue.poll(now+8400);check(fresh!=null&&fresh.generation==9,"new generation can capture without accepting prior callbacks");
  System.out.println("VirtualFrameQueueTest: "+checks+" checks passed; real screenshot queue policy, Android capture callbacks not exercised");
 }
}
