package jp.tapmarker;

import java.util.*;

/**
 * CIFA-owned dense scorer, entirely independent of BANC circuit data.
 * A fixed fully-connected 8 -> 16 -> route readout evaluates grounded
 * candidates. Success/failure only modulates bounded readout gains AFTER
 * existing post-action evidence confirms the result.
 *
 * This is NOT a recognition network: the enclosing CifaAutonomy validates
 * target package, safe named UI, history, two evidence frames and freshness.
 */
public final class CifaDenseSelector {
 public static final String SOURCE="CIFA: dense-evidence-selection-v1";
 static final int INPUTS=8,HIDDEN=16,ROUTES=4;
 private static final double[][] L1=new double[HIDDEN][INPUTS];
 private static final double[][] L2=new double[ROUTES][HIDDEN];
 static {
  // Deterministic nonzero dense parameters; no stochastic runtime output.
  // Different readouts permit later weight-space programming per route.
  for(int h=0;h<HIDDEN;h++)for(int j=0;j<INPUTS;j++)
   L1[h][j]=.17*Math.sin((h+1)*.71+(j+1)*1.37);
  for(int r=0;r<ROUTES;r++)for(int h=0;h<HIDDEN;h++)
   L2[r][h]=.13*Math.cos((r+1)*.93+(h+1)*.77);
 }
 public static final class Candidate {
  public final String id;
  public final int route;
  public final double excitation,confidence;
  public Candidate(String id,int route,double excitation,double confidence){
   this.id=id;this.route=route;this.excitation=excitation;this.confidence=confidence;
  }
 }
 public static final class Selection {
  public final Candidate candidate;
  public final double activation,signedOutput;
  public final double[] credit;
  Selection(Candidate c,double value,double[] credit){
   candidate=c;activation=value;signedOutput=value;this.credit=credit;
  }
 }
 private final double[] gains=new double[HIDDEN];
 private final boolean[] enabled=new boolean[HIDDEN];
 private Selection last;
 public long propagations;
 public CifaDenseSelector(){Arrays.fill(gains,1);Arrays.fill(enabled,true);}
 private static double bound(double x,double lo,double hi){return Math.max(lo,Math.min(hi,x));}
 Selection propagate(Candidate c,boolean trace){
  if(c==null||c.id==null||c.id.isEmpty()||c.route<0||c.route>=ROUTES
     ||!Double.isFinite(c.excitation)||!Double.isFinite(c.confidence)
     ||c.excitation<=0||c.confidence<=0)return null;
  final double excitation=bound(c.excitation,0,1.5),confidence=bound(c.confidence,0,1);
  double[] x={excitation,confidence,excitation*confidence,1,
    c.route==1?1:0,c.route==2?1:0,c.route==0?1:0,c.route==3?1:0};
  double result=0,active=0;
  double[] credit=new double[HIDDEN];
  propagations++;
  for(int h=0;h<HIDDEN;h++){
   if(!enabled[h])continue;
   double sum=0;
   for(int j=0;j<INPUTS;j++)sum+=L1[h][j]*x[j];
   double value=Math.tanh(sum);
   double contribution=L2[c.route][h]*value*gains[h];
   result+=contribution;active++;
   if(trace)credit[h]=Math.abs(contribution);
  }
  if(active==0)return null;
  // Grounded evidence dominates bounded dense tie-breaking.
  double score=bound(.16+.30*confidence+.20*excitation+.09*Math.tanh(result),.00001,.99999);
  if(!Double.isFinite(score))return null;
  return new Selection(c,score,credit);
 }
 public Selection select(List<Candidate> candidates){
  if(candidates==null||candidates.isEmpty()){last=null;return null;}
  Selection winner=null;
  for(Candidate c:candidates){
   Selection x=propagate(c,false);
   if(x==null)continue;
   if(winner==null||x.activation>winner.activation||
     x.activation==winner.activation&&x.candidate.id.compareTo(winner.candidate.id)<0)winner=x;
  }
  last=winner==null?null:propagate(winner.candidate,true);
  return last;
 }
 /** Reinforce only the previously selected, evidence-bound action. */
 public boolean reinforce(String id,double reward){
  if(last==null||!last.candidate.id.equals(id)||!Double.isFinite(reward))return false;
  double signed=bound(reward,-1,1);
  for(int i=0;i<HIDDEN;i++)if(enabled[i]&&last.credit[i]>1e-12)
   gains[i]=bound(gains[i]+.020*signed*bound(last.credit[i],0,1),.75,1.25);
  last=null;return true;
 }
 public void discardCredit(){last=null;}
 public double[] gains(){return gains.clone();}
 public void restore(double[] values){
  if(values==null||values.length!=HIDDEN)return;
  for(int i=0;i<HIDDEN;i++)if(Double.isFinite(values[i])&&values[i]>=.75&&values[i]<=1.25)gains[i]=values[i];
 }
 public void setConnected(int hidden,boolean value){
  if(hidden<0||hidden>=HIDDEN)throw new IllegalArgumentException("hidden index");
  enabled[hidden]=value;last=null;
 }
 public void disconnectAll(){Arrays.fill(enabled,false);last=null;}
}
