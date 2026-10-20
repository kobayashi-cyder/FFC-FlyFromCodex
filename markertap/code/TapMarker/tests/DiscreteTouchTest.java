package jp.tapmarker;
import java.util.*;

public final class DiscreteTouchTest {
 static int checks;static void check(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
 static final class Host implements DiscreteTouch.Clock,DiscreteTouch.Sink {
  long now,stopAt=Long.MAX_VALUE,downLatency;boolean downAccepted=true,upAccepted=true,throwSleep;final ArrayList<String> events=new ArrayList<>();
  public long now(){return now;}public void sleep(long ms){if(throwSleep)throw new IllegalStateException("clock failed");check(ms>0&&ms<=8,"pause is checked in small slices");now+=ms;}
  public boolean send(long down,int action,float x,float y){events.add(action+":"+down+":"+now+":"+x+":"+y);if(action==0){now+=downLatency;return downAccepted;}return upAccepted;}
  boolean tap(long duration)throws Exception{return new DiscreteTouch(this,this).tap(30,60,duration,()->now<stopAt);}
 }
 public static void main(String[] args)throws Exception{
  Host h=new Host();check(h.tap(17),"stationary contact completes");check(h.events.equals(Arrays.asList("0:0:0:30.0:60.0","1:0:17:30.0:60.0")),"DOWN and UP use identical coordinates/downTime and contain no MOVE");
  h=new Host();h.downLatency=35;check(h.tap(17)&&h.now==52,"requested hold starts after DOWN acknowledgement, including slow old-Android task checks");
  h=new Host();h.stopAt=9;check(!h.tap(60),"stop during press is not counted as a completed click");check(h.events.get(1).startsWith("3:0:16:"),"stop sends CANCEL with the same contact rather than a click-producing UP");
  h=new Host();h.stopAt=0;check(!h.tap(17)&&h.events.isEmpty(),"revoked input is never pressed");
  h=new Host();h.downAccepted=false;check(!h.tap(17)&&h.events.size()==1&&h.now==0,"rejected DOWN causes no sleep or orphan UP");
  h=new Host();h.upAccepted=false;check(!h.tap(17)&&h.events.size()==2,"rejected release is propagated to bounded retry");
  h=new Host();h.throwSleep=true;boolean failed=false;try{h.tap(17);}catch(IllegalStateException expected){failed=true;}check(failed&&h.events.size()==2&&h.events.get(1).startsWith("3:"),"exception after DOWN still attempts CANCEL");
  h=new Host();check(h.tap(1)&&h.now==1,"explicit shortest input preserves a one-millisecond contact");
  for(long invalid:new long[]{0,-1,1001}){boolean rejected=false;try{new Host().tap(invalid);}catch(IllegalArgumentException expected){rejected=true;}check(rejected,"invalid duration refused before DOWN");}
  System.out.println("DiscreteTouchTest: "+checks+" checks passed; real virtual contact sequencing with synthetic clock/sink, cancellation and slow acknowledgement; physical injection untested");
 }
}
