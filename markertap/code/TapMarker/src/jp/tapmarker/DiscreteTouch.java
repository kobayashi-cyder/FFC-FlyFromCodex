package jp.tapmarker;
import java.util.function.BooleanSupplier;

/** A stationary contact, followed by UP or CANCEL even if stopped during a press. */
public final class DiscreteTouch {
 public static final int DOWN=0,UP=1,CANCEL=3;
 public interface Clock {long now();void sleep(long millis);}
 public interface Sink {boolean send(long down,int action,float x,float y)throws Exception;}
 private final Clock clock;private final Sink sink;
 public DiscreteTouch(Clock clock,Sink sink){this.clock=clock;this.sink=sink;}
 public boolean tap(float x,float y,long duration,BooleanSupplier current)throws Exception{
  if(!Float.isFinite(x)||!Float.isFinite(y)||duration<1||duration>1000)throw new IllegalArgumentException("invalid contact");
  if(!current.getAsBoolean())return false;
  long down=clock.now();boolean pressed=false,complete=false,released=false;
  try{
   pressed=sink.send(down,DOWN,x,y);if(!pressed)return false;long heldAt=clock.now();
   while(clock.now()-heldAt<duration){if(!current.getAsBoolean())return false;clock.sleep(Math.min(8,Math.max(1,duration-(clock.now()-heldAt))));}
   if(!current.getAsBoolean())return false;complete=true;
  }finally{if(pressed)released=sink.send(down,complete?UP:CANCEL,x,y);}
  return complete&&released;
 }
}
