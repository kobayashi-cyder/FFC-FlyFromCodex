package jp.tapmarker;
import java.util.*;

/** The finite spatial set runs through the shipped tapper; button contacts stay exact. */
public final class DiscreteTapTargetsTest {
 static int checks;static void check(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
 static String key(float[] p){return p[0]+","+p[1];}
 static Set<String> set(float[] p){Set<String> out=new HashSet<>();for(int i=0;i<p.length;i+=2)out.add(p[i]+","+p[i+1]);return out;}
 public static void main(String[] args){
  int[] bounds={0,0,540,1200};float[] centers={270.2f,900.1f};float[] targets=DiscreteTapTargets.points(centers,bounds,18);
  check(Arrays.equals(targets,new float[]{270,900,252,900,288,900,270,882,270,918}),"main marker has exactly center/left/right/up/down, not continuously sampled coordinates");
  check(Arrays.equals(centers,new float[]{270.2f,900.1f}),"saved marker coordinates are not modified");
  check(Arrays.equals(DiscreteTapTargets.points(centers,bounds,0),new float[]{270,900}),"fixed-center option retains a single integer point");
  check(Arrays.equals(DiscreteTapTargets.points(new float[]{100,200,400,800},bounds,9),new float[]{100,200,400,800,91,200,391,800,109,200,409,800,100,191,400,791,100,209,400,809}),"multiple markers are interleaved before advancing to the next discrete offset");
  float[] corner=DiscreteTapTargets.points(new float[]{0,0},bounds,18);check(Arrays.equals(corner,new float[]{0,0,18,0,0,18}),"edge clipping removes duplicate contacts and never leaves the target window");
  int[] moved={30,40,570,1240};float[] local=DiscreteTapTargets.points(new float[]{300,940},moved,18);for(int i=0;i<local.length;i+=2)check(local[i]==targets[i]+30&&local[i+1]==targets[i+1]+40,"window translation is applied once to every finite contact");
  for(int step:new int[]{1,6,9,18,36}){
   float[] p=DiscreteTapTargets.points(new float[]{539.9f,1199.9f},bounds,step);check(set(p).size()==p.length/2,"clipped points are unique for each marker");for(int i=0;i<p.length;i+=2)check(p[i]>=0&&p[i]<540&&p[i+1]>=0&&p[i+1]<1200,"integer contact stays inside right/bottom bounds");
  }
  TapperTest.Device device=new TapperTest.Device();device.extraLatency=17;Tapper tapper=device.tapper();Tapper.Command command=new Tapper.Command("game",0,"play",bounds,targets,17,1,3000,true);tapper.update(command);device.until(400);
  Set<String> allowed=set(targets),visited=new HashSet<>();for(float[] point:device.points)visited.add(key(point));check(allowed.size()==5&&visited.equals(allowed),"actual executor visits all five discrete locations");
  for(float[] point:device.points)check(allowed.contains(key(point)),"executor produces no coordinates along the connecting lines");
  check(device.maxPending==1,"a new location is pressed only after the old location is released");
  for(int i=0;i<device.points.size();i++)check(key(device.points.get(i)).equals(key(command.point(i))),"location follows the stable finite sequence across repeated taps");
  long sent=tapper.sent();tapper.pause("CM");device.until(1000);check(tapper.sent()==sent,"CM pause stops future spatial contacts");
  Tapper.Command button=new Tapper.Command("game",0,"close",bounds,new float[]{20.4f,30.7f},60,1,3000,false);check(button.count()==1&&Arrays.equals(button.point(0),new float[]{20,31}),"one-shot Close/cancel/upgrade input has no added surrounding targets");
  for(float[] invalid:new float[][]{{-1,50},{540,50},{Float.NaN,50},{Float.POSITIVE_INFINITY,50}}){boolean denied=false;try{DiscreteTapTargets.points(invalid,bounds,18);}catch(IllegalArgumentException expected){denied=true;}check(denied,"an invalid anchor is refused before clipping can conceal it");}
  System.out.println("DiscreteTapTargetsTest: "+checks+" checks passed; finite five-point locations, scaled spacing, window bounds, serial contacts, CM stop and exact button contact; physical injection untested");
 }
}
