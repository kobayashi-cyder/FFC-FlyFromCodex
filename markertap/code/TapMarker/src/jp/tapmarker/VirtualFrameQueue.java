package jp.tapmarker;
import java.util.*;

/** Serializes real screenshots across displays; cached pixels never become fresh by waiting. */
final class VirtualFrameQueue {
 static final long STALE_MS=750,INTERVAL_MS=400,TIMEOUT_MS=2500;
 static final class Request {final int display,generation,width,height;long startedAt;Request(int id,int gen,int w,int h){display=id;generation=gen;width=w;height=h;}String key(){return generation+":"+display;}}
 final ArrayDeque<Request> waiting=new ArrayDeque<>();final Map<String,Long> retryAt=new HashMap<>();Request running;long nextAt;
 static boolean stale(long frameAt,long now){return frameAt<=0||now-frameAt>=STALE_MS;}
 boolean offer(int display,int generation,int width,int height,long frameAt,long now){
  if(display<=0||width<=0||height<=0)throw new IllegalArgumentException("有効な仮想画面だけ再取得できます");
  if(!stale(frameAt,now))return false;
  Request request=new Request(display,generation,width,height);Long retry=retryAt.get(request.key());if(retry!=null&&now<retry)return false;
  if(running!=null&&running.key().equals(request.key()))return false;for(Request queued:waiting)if(queued.key().equals(request.key()))return false;
  waiting.addLast(request);return true;
 }
 Request poll(long now){if(running!=null||waiting.isEmpty()||now<nextAt)return null;running=waiting.removeFirst();running.startedAt=now;nextAt=now+INTERVAL_MS;return running;}
 boolean current(Request request){return running==request;}
 boolean accepts(Request request,int display,int generation,long frameAt,long now){return current(request)&&request.display==display&&display>0&&request.generation==generation&&request.startedAt>frameAt&&now-request.startedAt<=1500;}
 boolean complete(Request request,long now,boolean success){if(!current(request))return false;retryAt.put(request.key(),now+(success?500:1500));running=null;return true;}
 Request expire(long now){Request old=running;if(old==null||now-old.startedAt<TIMEOUT_MS)return null;complete(old,now,false);return old;}
 boolean hasWork(){return running!=null||!waiting.isEmpty();}
 void clear(){waiting.clear();running=null;retryAt.clear();}
 void remove(int display,int generation){waiting.removeIf(r->r.display==display&&r.generation==generation);if(running!=null&&running.display==display&&running.generation==generation)running=null;retryAt.remove(generation+":"+display);}
}
