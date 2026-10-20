package jp.tapmarker;

import java.util.Arrays;

/** Cancellable main-input ticket; phase changes and resized windows invalidate queued taps. */
public final class ContinuousTapSession {
 private volatile long version;private long epoch;private String state="";private int[] bounds;private float[] point;private volatile boolean active;
 public boolean active(){return active;}
 public long ticket(){return version;}
 public long arm(PhaseEngine engine,int[] window,float[] location){
  if(window==null||window.length!=4||location==null||location.length!=2||!Float.isFinite(location[0])||!Float.isFinite(location[1])||location[0]<window[0]||location[0]>=window[2]||location[1]<window[1]||location[1]>=window[3])throw new IllegalArgumentException("invalid main input");
  if(!active||!state.equals(engine.current)||epoch!=engine.epoch||!Arrays.equals(bounds,window))version++;
  state=engine.current;epoch=engine.epoch;bounds=window.clone();point=DiscreteTapPosition.point(location[0],location[1],window);active=true;return version;
 }
 public boolean current(PhaseEngine engine,long ticket){return active&&ticket==version&&epoch==engine.epoch&&state.equals(engine.current);}
 public boolean window(int[] window){return active&&Arrays.equals(bounds,window);}
 public float[] point(){return point==null?null:point.clone();}
 public int[] bounds(){return bounds==null?null:bounds.clone();}
 public void pause(){if(active)version++;active=false;}
}
