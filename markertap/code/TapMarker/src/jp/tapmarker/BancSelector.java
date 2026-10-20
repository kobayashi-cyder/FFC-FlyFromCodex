package jp.tapmarker;

import java.util.*;

/** Native port of BANC888 v7.12 ConnectomeSelector. Task bindings are engineered. */
public final class BancSelector {
 public static final String SOURCE="htem/BANC-project / figures/vignettes/network_fig3g_edges.csv + network_fig3g_neurons.csv";
 public static final String[] NODES={"AN27X017","DNp65","MNad21","m_NSC_DILP","oxygen_sensory","aorta_sensory"};
 private static final int[] FROM={0,0,0,0,1,1,1,2,2,5,5,3,4,4,4,4};
 private static final int[] TO={1,2,3,4,0,2,3,0,4,0,3,1,0,1,2,3};
 private static final int[] COUNT={329,27,989,11,191,618,52,6,3,7,1062,3,769,12,28,3};
 private static final double[] SIGN={.28,1,.28,.28,-1,-1,-1,.18,.18,.28,.28,.18,.28,.28,.28,1};
 // Same four source/readout pairs as O1P_SHORTCUTS, irrespective of node ordering.
 private static final int[] ROUTE_SOURCE={1,1,0,0},ROUTE_TARGET={2,0,1,3};
 public static final class Candidate {
  public final String id;public final int route;public final double excitation,confidence;
  public Candidate(String id,int route,double excitation,double confidence){this.id=id;this.route=route;this.excitation=excitation;this.confidence=confidence;}
 }
 public static final class Selection {
  public final Candidate candidate;public final double activation,signedOutput;public final double[] credit;
  Selection(Candidate c,double value,double[] credit){candidate=c;signedOutput=value;activation=Math.abs(value);this.credit=credit;}
 }
 private final double[] weights=new double[16],gains=new double[16];
 private final boolean[] connected=new boolean[16];
 private Selection last;public long propagations;
 public BancSelector(){double[] totals=new double[6];for(int i=0;i<16;i++)totals[FROM[i]]+=Math.sqrt(COUNT[i]);for(int i=0;i<16;i++){weights[i]=SIGN[i]*Math.sqrt(COUNT[i])/totals[FROM[i]];gains[i]=1;connected[i]=true;}}
 Selection propagate(Candidate c,boolean trace){
  if(c==null||c.route<0||c.route>=4||!Double.isFinite(c.excitation)||!Double.isFinite(c.confidence))return null;
  double drive=Math.min(1.2,c.excitation*c.confidence);if(drive<=0)return null;
  double[] activity=new double[6],next=new double[6],credit=new double[16];propagations++;
  for(int tick=0;tick<6;tick++){
   for(int i=0;i<6;i++)next[i]=activity[i]*.35;
   next[ROUTE_SOURCE[c.route]]+=drive;
   for(int i=0;i<16;i++)if(connected[i]){double flux=activity[FROM[i]]*weights[i]*gains[i];next[TO[i]]+=flux;if(trace)credit[i]+=Math.abs(flux);}
   for(int i=0;i<6;i++)next[i]=Math.tanh(next[i]);
   double[] swap=activity;activity=next;next=swap;
  }
  return new Selection(c,activity[ROUTE_TARGET[c.route]],credit);
 }
 public Selection select(List<Candidate> candidates){
  Selection winner=null;
  for(Candidate c:candidates){Selection s=propagate(c,false);if(s==null||!Double.isFinite(s.activation)||s.activation<1e-6)continue;if(winner==null||s.activation>winner.activation||s.activation==winner.activation&&c.id.compareTo(winner.candidate.id)<0)winner=s;}
  last=winner==null?null:propagate(winner.candidate,true);return last;
 }
 public boolean reinforce(String id,double reward){
  if(last==null||!last.candidate.id.equals(id)||!Double.isFinite(reward))return false;
  boolean[] cone=new boolean[6];cone[ROUTE_TARGET[last.candidate.route]]=true;
  for(int pass=0;pass<6;pass++)for(int i=0;i<16;i++)if(connected[i]&&cone[TO[i]])cone[FROM[i]]=true;
  for(int i=0;i<16;i++)if(connected[i]&&cone[FROM[i]]&&cone[TO[i]]&&last.credit[i]>=1e-9)gains[i]=Math.max(.75,Math.min(1.25,gains[i]+.025*Math.max(-1,Math.min(1,reward))*Math.min(1,last.credit[i])));
  last=null;return true;
 }
 public void discardCredit(){last=null;}
 public double[] gains(){return gains.clone();}
 public void restore(double[] factors){if(factors==null||factors.length!=16)return;for(int i=0;i<16;i++)if(Double.isFinite(factors[i])&&factors[i]>=.75&&factors[i]<=1.25)gains[i]=factors[i];}
 public void setConnected(int edge,boolean value){connected[edge]=value;last=null;}
 public void disconnectAll(){Arrays.fill(connected,false);last=null;}
}
