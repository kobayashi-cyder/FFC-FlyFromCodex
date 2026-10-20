package jp.tapmarker;

/** Four-display timing simulation; exercises the policies used by the Android adapter. */
public final class VirtualLoadPolicyTest {
 static int checks;
 static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 static void limits(){
  for(int bad:new int[]{Integer.MIN_VALUE,-1,0,1,5,Integer.MAX_VALUE}){
   check(VirtualLoadPolicy.limit(bad)==2,"invalid saved settings retain the safe two-display default");
   check(!VirtualLoadPolicy.allows(3,bad),"invalid settings cannot silently enable extra displays");
  }
  for(int limit=2;limit<=4;limit++)for(int selected=0;selected<=6;selected++)
   check(VirtualLoadPolicy.allows(selected,limit)==(selected>0&&selected<=limit),"selection must fit the saved limit without truncating the target list");
 }
 static void fourDisplays(PerformancePolicy.Profile p,int count){
  long interval=VirtualLoadPolicy.decodeInterval(p,count,true,true);
  check(interval==VirtualLoadPolicy.decodeInterval(p,count,true,false),"hiding previews cannot pause game frame acquisition or ads");
  long[] due=new long[count],last=new long[count];int[] received=new int[count];int maxBatch=0;
  for(long now=10000;now<15000;now+=16){
   int batch=0;
   for(int lane=0;lane<count;lane++){
    if(now<10000+VirtualLoadPolicy.creationDelay(p,lane,count)||now<due[lane])continue;
    if(last[lane]>0)check(now-last[lane]<=interval+16,"a responsive display stays fresh despite the other displays");
    due[lane]=VirtualLoadPolicy.nextDecodeAt(now,interval,lane,count);last[lane]=now;received[lane]++;batch++;
   }
   if(now>12500)maxBatch=Math.max(maxBatch,batch);
  }
  check(maxBatch<=1,"steady-state frame copies occupy different callback ticks");
  for(int countForLane:received)check(countForLane>=8,"every selected display continues receiving frames");
  for(int lane=0;lane<count;lane++){
   long offset=VirtualLoadPolicy.scanOffset(lane,count,p.scanMs);
   check(offset>=0&&offset<p.scanMs,"initial analysis requests are spread across one scan period");
   if(lane>0)check(offset>VirtualLoadPolicy.scanOffset(lane-1,count,p.scanMs),"distinct apps do not all enqueue their first analysis together");
  }
  long late=50000,next=VirtualLoadPolicy.nextDecodeAt(late,interval,count-1,count);
  check(next>late&&next<=late+interval,"a stalled callback skips old work instead of running a backlog");
 }
 static void lifecycle(){
  long now=20000,active=VirtualLoadPolicy.decodeInterval(PerformancePolicy.NORMAL,4,true,true);
  check(VirtualLoadPolicy.decodeInterval(PerformancePolicy.NORMAL,4,false,true)==1000,"stopped hidden previews reduce copying");
  check(active<750,"active displays retain the existing stale-frame recovery threshold");
  check(VirtualLoadPolicy.creationDelay(PerformancePolicy.NORMAL,3,4)>=900,"four app launches do not share one startup burst");
  check(VirtualLoadPolicy.creationDelay(PerformancePolicy.F52A,3,4)==1950,"existing F-52A launch pacing is retained");
  check(VirtualLoadPolicy.nextDecodeAt(now,active,0,4)>now,"resuming cannot request an immediate catch-up loop");
  VirtualFrameQueue queue=new VirtualFrameQueue();
  for(int lane=0;lane<4;lane++)queue.offer(100+lane,2,540,1212,0,now);
  for(int lane=0;lane<4;lane++){
   VirtualFrameQueue.Request r=queue.poll(now+lane*400);
   check(r!=null&&r.display==100+lane,"four static displays retain fair serialized fallback captures");
   check(queue.poll(now+lane*400+1)==null,"screenshots never overlap across app boundaries");
   check(!queue.accepts(r,100+(lane+1)%4,2,0,now+lane*400+50),"a capture cannot update another app");
   queue.complete(r,now+lane*400+50,true);
  }
  check(!queue.hasWork(),"all four fallback requests complete without starvation");
 }
 public static void main(String[] args){
  limits();for(PerformancePolicy.Profile p:new PerformancePolicy.Profile[]{PerformancePolicy.ECO,PerformancePolicy.NORMAL,PerformancePolicy.QUICK,PerformancePolicy.F52A})for(int count=2;count<=4;count++)fourDisplays(p,count);lifecycle();
  System.out.println("VirtualLoadPolicyTest: "+checks+" checks passed; 2/3/4 selections, phased captures, delayed callbacks and four-display recovery; Android throughput and RAM not measured");
 }
}
