package jp.tapmarker;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Gently changes only the NEXT lane index in a sequential SplitPilot.
 * Everything that sends touches, returns from ads, and minimizes the panel
 * remains the original authenticated v1.157 bytecode.
 */
final class RewardV158Scheduler {
  private static final Map<Object,RewardLanePlanner> PLANNERS=
      Collections.synchronizedMap(new WeakHashMap<Object,RewardLanePlanner>());

  private static Object get(Object obj,String name)throws Exception{
    for(Class<?> c=obj.getClass();c!=null;c=c.getSuperclass()){
      try{Field f=c.getDeclaredField(name);f.setAccessible(true);return f.get(obj);}
      catch(NoSuchFieldException ignored){}
    }
    throw new NoSuchFieldException(name);
  }
  private static void set(Object obj,String name,int val)throws Exception{
    for(Class<?> c=obj.getClass();c!=null;c=c.getSuperclass()){
      try{Field f=c.getDeclaredField(name);f.setAccessible(true);f.setInt(obj,val);return;}
      catch(NoSuchFieldException ignored){}
    }
    throw new NoSuchFieldException(name);
  }
  private static boolean flag(Object o,String key)throws Exception{
    return Boolean.TRUE.equals(get(o,key));
  }
  private static boolean manualBlocked(Object slot,long now) {
    try{
      Object manual=get(slot,"manual");
      for(Method m:manual.getClass().getDeclaredMethods())
        if("holding".equals(m.getName())&&m.getParameterTypes().length==1){
          m.setAccessible(true);
          return Boolean.TRUE.equals(m.invoke(manual,now));
        }
    }catch(Exception blocked){return true;}
    return true; // unknown manual control: never schedule around it
  }
  static void afterSplit(Object split,long now) {
    try{
      if(split==null||!flag(split,"active")||flag(split,"parallelVirtual"))return;
      Object[] slots=(Object[])get(split,"slots");
      if(slots==null||slots.length<2||slots.length>2)return;
      boolean[] eligible=new boolean[slots.length];
      boolean[] verifying=new boolean[slots.length];
      long[] cycles=new long[slots.length];
      double[] objectives=new double[slots.length];
      boolean knownReward=false;
      for(int i=0;i<slots.length;i++){
        Object slot=slots[i];
        eligible[i]=slot!=null&&!flag(slot,"stopped") &&
            !manualBlocked(slot,now);
        if(!eligible[i])continue;
        verifying[i]=RewardV158Observer.verifying(slot);
        cycles[i]=RewardV158Observer.cycles(slot);
        objectives[i]=RewardV158Observer.score(slot);
        knownReward|=cycles[i]>0||verifying[i];
      }
      // Not enough actual evidence? Retain v1.157's normal index behavior.
      if(!knownReward)return;
      RewardLanePlanner planner;
      synchronized(PLANNERS){
        planner=PLANNERS.get(split);
        if(planner==null){planner=new RewardLanePlanner();PLANNERS.put(split,planner);}
      }
      int selection=planner.select(eligible,verifying,cycles,objectives);
      if(selection>=0)set(split,"index",selection);
    }catch(Exception|LinkageError absent){
      // Scheduling policy is optional: old round-robin is the safe fallback.
    }
  }
  private RewardV158Scheduler(){}
}
