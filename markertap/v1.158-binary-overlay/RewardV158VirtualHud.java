package jp.tapmarker;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Limited read-only virtual frame point proof; never calls full-size image(). */
final class RewardV158VirtualHud {
  private static final ExecutorService WORKER=
      Executors.newSingleThreadExecutor(r->{
        Thread t=new Thread(r,"MarkerTap-158-virtual-hud");
        t.setDaemon(true);return t;
      });
  private static final Map<Object,Long> LAST_FRAMES=
      Collections.synchronizedMap(new WeakHashMap<Object,Long>());

  private static Object field(Object o,String name)throws Exception{
    for(Class<?> c=o.getClass();c!=null;c=c.getSuperclass()){
      try{
        Field f=c.getDeclaredField(name);f.setAccessible(true);
        return f.get(o);
      }catch(NoSuchFieldException missing){}
    }
    throw new NoSuchFieldException(name);
  }
  private static Object call(Object o,String name,Object... args)throws Exception{
    for(Class<?> c=o.getClass();c!=null;c=c.getSuperclass()){
      for(Method m:c.getDeclaredMethods()){
        if(m.getName().equals(name)&&m.getParameterTypes().length==args.length){
          m.setAccessible(true);return m.invoke(o,args);
        }
      }
    }
    throw new NoSuchMethodException(name);
  }
  /**
   * Read exactly the HUD crop, not a full-screen Bitmap; bounded memory.
   * The original RegionImage retains/retires its GPU buffer under the
   * existing VirtualWindows resource lease.
   */
  static long readHud(Object source,int width,int height)throws Exception {
    if(source==null||width<300||height<560)return -1;
    int x0=(int)(width*.105f),x1=(int)(width*.392f);
    int y0=(int)(height*.089f),y1=(int)(height*.111f);
    int w=x1-x0,h=y1-y0;
    if(w<60||h<12||w>1600||h>400)return -1;
    int[] colors=new int[w*h];
    call(source,"read",x0,y0,w,h,colors,w);
    return KurashiruPointHud.read((x,y)->{
      if(x<x0||x>=x1||y<y0||y>=y1)
        throw new IllegalArgumentException("Out-of-ROI HUD read");
      return colors[(y-y0)*w+(x-x0)];
    },width,height);
  }
  static void capture(Object lane,Object windows,Object window,String pkg){
    if(lane==null||windows==null||window==null||pkg==null)return;
    final Object image;
    final int width,height,id;
    final long capturedAt;
    try{
      if(Boolean.TRUE.equals(field(window,"closed")))return;
      if(!pkg.equals(field(window,"pkg")))return;
      int currentId=((Number)field(window,"id")).intValue();
      long at=((Number)field(window,"frameAt")).longValue();
      long now=android.os.SystemClock.uptimeMillis();
      if(currentId<=0||at<=0||now<at||now-at>=1500)return;
      synchronized(LAST_FRAMES){
        Long previous=LAST_FRAMES.get(lane);
        if(previous!=null&&previous>=at)return;
        LAST_FRAMES.put(lane,at);
      }
      Object lock=field(window,"frameLock");
      synchronized(lock){
        if(Boolean.TRUE.equals(field(window,"closed")))return;
        Object candidate=field(window,"latest");
        if(candidate==null||((Number)field(window,"frameAt")).longValue()!=at)return;
        image=call(candidate,"retain");
        width=((Number)field(window,"width")).intValue();
        height=((Number)field(window,"height")).intValue();
        id=currentId;capturedAt=at;
      }
    }catch(Exception|LinkageError rejected){return;}
    try{
      WORKER.execute(()->{
        try{
          long now=android.os.SystemClock.uptimeMillis();
          if(now<capturedAt||now-capturedAt>=1500)return;
          Object src=field(image,"source");
          long amount=readHud(src,width,height);
          long done=android.os.SystemClock.uptimeMillis();
          if(amount<0||done-capturedAt>=1500)return;
          if(Boolean.TRUE.equals(field(window,"closed"))
              ||((Number)field(window,"id")).intValue()!=id)return;
          RewardV158Observer.balance(lane,pkg,amount,capturedAt,done);
        }catch(Exception|LinkageError ignored){
          // No verified virtual HUD means no credited reward.
        }finally{retire(windows,image);}
      });
    }catch(RuntimeException saturated){retire(windows,image);}
  }
  private static void retire(Object windows,Object image){
    try{call(windows,"retireFrameAsync",image);}
    catch(Exception|LinkageError unavailable){
      try{if(image instanceof AutoCloseable)((AutoCloseable)image).close();}
      catch(Exception ignored){}
    }
  }
  private RewardV158VirtualHud(){}
}
