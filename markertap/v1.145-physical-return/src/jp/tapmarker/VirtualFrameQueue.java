package jp.tapmarker;
import java.util.*;

/**
 * Two independent screenshot lanes, one per virtual display. The previous
 * global "running" request prevented the second display from taking a frame
 * while the first was stuck in a callback (up to 2500 ms).
 *
 * Android screenshot calls still have a global 400 ms spacing and at most
 * two outstanding requests. No request/result can cross a display or run
 * generation. All methods are called by VirtualWindows' main Handler, but
 * synchronized entry points make late callback handling deterministic.
 */
final class VirtualFrameQueue {
 static final long STALE_MS=750,INTERVAL_MS=400,TIMEOUT_MS=2500;
 static final int MAX_IN_FLIGHT=2;
 static final class Request {
  final int display,generation,width,height;long startedAt;
  Request(int id,int gen,int w,int h){display=id;generation=gen;width=w;height=h;}
  String key(){return generation+":"+display;}
 }
 final ArrayDeque<Request> waiting=new ArrayDeque<>();
 final Map<String,Long> retryAt=new HashMap<>();
 final LinkedHashMap<String,Request> active=new LinkedHashMap<>();
 // Preserve the baseline field for any existing binary references.
 Request running;
 long nextAt;
 static boolean stale(long frameAt,long now){return frameAt<=0||now-frameAt>=STALE_MS;}
 synchronized boolean offer(int display,int generation,int width,int height,long frameAt,long now){
  if(display<=0||width<=0||height<=0)throw new IllegalArgumentException("有効な仮想画面だけ再取得できます");
  if(!stale(frameAt,now))return false;
  String key=generation+":"+display;
  Long retry=retryAt.get(key);if(retry!=null&&now<retry)return false;
  if(active.containsKey(key))return false;
  for(Request queued:waiting)if(queued.key().equals(key))return false;
  waiting.addLast(new Request(display,generation,width,height));return true;
 }
 synchronized Request poll(long now){
  if(active.size()>=MAX_IN_FLIGHT||waiting.isEmpty()||now<nextAt)return null;
  Request request=waiting.removeFirst();request.startedAt=now;
  active.put(request.key(),request);running=active.values().iterator().next();
  nextAt=now+INTERVAL_MS;
  return request;
 }
 synchronized boolean current(Request request){return request!=null&&active.get(request.key())==request;}
 synchronized boolean accepts(Request request,int display,int generation,long frameAt,long now){
  return current(request)&&request.display==display&&display>0&&request.generation==generation
   &&request.startedAt>frameAt&&now>=request.startedAt&&now-request.startedAt<=1500;
 }
 synchronized boolean complete(Request request,long now,boolean success){
  if(!current(request))return false;
  active.remove(request.key());retryAt.put(request.key(),now+(success?500:1500));
  running=active.isEmpty()?null:active.values().iterator().next();
  return true;
 }
 synchronized Request expire(long now){
  for(Request request:new ArrayList<>(active.values())){
   if(now-request.startedAt>=TIMEOUT_MS){complete(request,now,false);return request;}
  }
  return null;
 }
 synchronized boolean hasWork(){return !active.isEmpty()||!waiting.isEmpty();}
 synchronized void clear(){waiting.clear();active.clear();running=null;retryAt.clear();nextAt=0;}
 synchronized void remove(int display,int generation){
  String key=generation+":"+display;
  waiting.removeIf(r->r.key().equals(key));active.remove(key);retryAt.remove(key);
  running=active.isEmpty()?null:active.values().iterator().next();
 }
}
