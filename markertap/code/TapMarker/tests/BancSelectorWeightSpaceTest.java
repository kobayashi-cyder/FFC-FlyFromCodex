package jp.tapmarker;
import java.lang.reflect.Field;
import java.util.*;
/** Compares optimized scores against an independent copy of the previous 6-tick algebra.
 * All comparisons are exact Double.doubleToLongBits, including credit traces.
 */
public final class BancSelectorWeightSpaceTest {
 static int[] ints(String name)throws Exception {
  Field f=BancSelector.class.getDeclaredField(name);f.setAccessible(true);return ((int[])f.get(null)).clone();
 }
 static double[] doubles(BancSelector s,String name)throws Exception {
  Field f=BancSelector.class.getDeclaredField(name);f.setAccessible(true);return ((double[])f.get(s)).clone();
 }
 static boolean[] connected(BancSelector s)throws Exception {
  Field f=BancSelector.class.getDeclaredField("connected");f.setAccessible(true);return ((boolean[])f.get(s)).clone();
 }
 static void eq(double a,double b,String name){
  if(Double.doubleToLongBits(a)!=Double.doubleToLongBits(b))
   throw new AssertionError(name+" legacy="+a+" optimized="+b);
 }
 static class Result {double output;double[] credit;Result(double a,double[] b){output=a;credit=b;}}
 static Result legacy(BancSelector s,BancSelector.Candidate c,boolean trace)throws Exception {
  int[] from=ints("FROM"),to=ints("TO"),routeSource=ints("ROUTE_SOURCE"),routeTarget=ints("ROUTE_TARGET");
  double[] w=doubles(s,"weights"),g=doubles(s,"gains");boolean[] connected=connected(s);
  double drive=Math.min(1.2,c.excitation*c.confidence);
  if(drive<=0)return null;
  double[] activity=new double[6],next=new double[6],credit=new double[16];
  for(int tick=0;tick<6;tick++){
   for(int i=0;i<6;i++)next[i]=activity[i]*.35;
   next[routeSource[c.route]]+=drive;
   for(int i=0;i<16;i++)if(connected[i]){
    double flux=activity[from[i]]*w[i]*g[i];next[to[i]]+=flux;
    if(trace)credit[i]+=Math.abs(flux);
   }
   for(int i=0;i<6;i++)next[i]=Math.tanh(next[i]);
   double[] swap=activity;activity=next;next=swap;
  }
  return new Result(activity[routeTarget[c.route]],credit);
 }
 static void yes(boolean b,String msg){if(!b)throw new AssertionError(msg);}
 public static void main(String[] args)throws Exception {
  Random rnd=new Random(20261104L);
  int assertions=0;
  for(int caseNo=0;caseNo<256;caseNo++){
   BancSelector s=new BancSelector();
   double[] factors=new double[16];
   for(int i=0;i<16;i++)factors[i]=.75+rnd.nextDouble()*.5;
   s.restore(factors);
   for(int i=0;i<16;i++)if(rnd.nextInt(5)==0)s.setConnected(i,false);
   for(int route=0;route<4;route++){
    BancSelector.Candidate c=new BancSelector.Candidate("case-"+caseNo+"-route-"+route,
      route,.02+rnd.nextDouble()*1.3,.01+rnd.nextDouble()*1.0);
    Result baseline=legacy(s,c,false);
    BancSelector.Selection actual=s.propagate(c,false);
    yes(actual!=null,"unexpected null");assertions++;
    eq(baseline.output,actual.signedOutput,"route "+route+" case "+caseNo);assertions++;
    Result fullBaseline=legacy(s,c,true);
    BancSelector.Selection traced=s.propagate(c,true);
    eq(fullBaseline.output,traced.signedOutput,"trace route "+route);assertions++;
    for(int i=0;i<16;i++){eq(fullBaseline.credit[i],traced.credit[i],"credit "+i);assertions++;}
   }
  }
  Field field=BancSelector.class.getDeclaredField("OUTPUT_CONE");field.setAccessible(true);
  boolean[][][] cone=(boolean[][][])field.get(null);
  int[] to=ints("TO");
  for(int route=0;route<4;route++){
   int edges=0,nodes=0;
   for(int tick=0;tick<6;tick++){
    for(int j=0;j<6;j++)if(cone[route][tick][j])nodes++;
    for(int e=0;e<16;e++)if(cone[route][tick][to[e]])edges++;
   }
   yes(edges<96&&nodes<36,"output cone is not smaller for route "+route);assertions++;
   System.out.println("route "+route+": weighted edge evals "+edges+"/96, node evals "+nodes+"/36");
  }
  System.out.println("PASS: "+assertions+" exact equality/invariant checks, 256 randomized gain/connectivity scenarios across four routes");
 }
}
