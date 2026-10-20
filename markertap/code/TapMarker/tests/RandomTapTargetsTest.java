package jp.tapmarker;
import java.util.*;

/** Seeded tests of the actual per-press selection, not a continuous jitter model. */
public final class RandomTapTargetsTest {
 static int checks;static void check(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
 static final int[] BOUNDS={0,0,540,1200};
 static Tapper.Command command(float[] points,long expiry,boolean random){return new Tapper.Command("game",0,"main",BOUNDS,points,1,1,expiry,true,0,random);}
 static String key(float[] point){return point[0]+","+point[1];}
 static final class Source extends Random {int calls;Source(long seed){super(seed);}public int nextInt(int bound){calls++;return super.nextInt(bound);}}
 public static void main(String[] args){
  float[] points=DiscreteTapTargets.points(new float[]{270,900},BOUNDS,18);Set<String> allowed=new HashSet<>();for(int i=0;i<5;i++)allowed.add(key(new float[]{points[i*2],points[i*2+1]}));
  ArrayList<String> previous=null;
  for(long seed:new long[]{52,182,20261015}){
   Source random=new Source(seed);TapperTest.Device device=new TapperTest.Device();Tapper tapper=new Tapper(device,device,device,device,random);tapper.update(command(points,5000,true));device.until(250);tapper.pause("CM");long sent=tapper.sent();device.until(300);
   Set<String> visited=new HashSet<>();ArrayList<String> sequence=new ArrayList<>();boolean differsFromOrder=false,repeatedNeighbor=false;
   for(int i=0;i<device.points.size();i++){String p=key(device.points.get(i));check(allowed.contains(p),"every random press belongs to the finite five-point set");visited.add(p);sequence.add(p);if(!p.equals(key(new float[]{points[i%5*2],points[i%5*2+1]})))differsFromOrder=true;if(i>0&&p.equals(key(device.points.get(i-1))))repeatedNeighbor=true;}
   check(visited.equals(allowed)&&differsFromOrder,"random execution visits all points without enforcing their stored order");check(repeatedNeighbor,"each press is an independent random draw, with repeated locations allowed");
   check(device.maxPending==1&&random.calls==device.starts.size(),"one random draw per serial send, with no in-flight point changes");check(tapper.sent()==sent,"CM pause stops further random contacts");
   if(previous!=null)check(!sequence.equals(previous),"independent random sources do not force the same cycle");previous=sequence;
  }
  TapperTest.Device busy=new TapperTest.Device();busy.ready=false;Source random=new Source(52);Tapper blocked=new Tapper(busy,busy,busy,busy,random);blocked.update(command(points,5000,true));busy.until(30);check(random.calls==0&&busy.points.isEmpty(),"waiting for input does not repeatedly reroll or move a contact");busy.ready=true;busy.until(40);check(random.calls==busy.points.size(),"random draw happens only when sending a new press");
  Tapper.Command fixed=command(points,5000,false),spread=command(points,5000,true);check(!fixed.sameRoute(spread),"changing selection mode invalidates the prior command route");
  TapperTest.Device flight=new TapperTest.Device();flight.extraLatency=49;Tapper active=new Tapper(flight,flight,flight,flight,new Random(52));active.update(spread);flight.until(10);float[] held=flight.points.get(0).clone();float[] replacement=DiscreteTapTargets.points(new float[]{100,200},BOUNDS,18);active.update(command(replacement,6000,true));check(Arrays.equals(held,flight.points.get(0)),"new random target set cannot alter the contact that is already down");flight.until(50);Set<String> replacementSet=new HashSet<>();for(int i=0;i<5;i++)replacementSet.add(key(new float[]{replacement[i*2],replacement[i*2+1]}));check(replacementSet.contains(key(flight.points.get(1)))&&flight.maxPending==1,"next random location is selected from the refreshed set only after release");
  System.out.println("RandomTapTargetsTest: "+checks+" checks passed; finite independent random locations, no sequential cycle, immutable press, bounded window and CM pause; no physical-device claim");
 }
}
